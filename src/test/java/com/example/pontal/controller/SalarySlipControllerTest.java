package com.example.pontal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.example.pontal.config.SecurityConfig;
import com.example.pontal.dto.SalarySlipSummary;
import com.example.pontal.dto.SalarySlipUploadHistory;
import com.example.pontal.dto.SalarySlipUploadResult;
import com.example.pontal.exception.ConflictException;
import com.example.pontal.exception.ForbiddenException;
import com.example.pontal.exception.NotFoundException;
import com.example.pontal.exception.ValidationException;
import com.example.pontal.service.SalarySlipService;

//SalarySlipControllerのテスト。Controllerと認証・例外変換だけを起動し、Serviceはモックにする
//「ログイン中の人のsubがServiceに渡ること」「Serviceの例外が正しいステータスになること」を確認する
@WebMvcTest(controllers = SalarySlipController.class, properties = {
        "cognito.region=ap-southeast-2",
        "cognito.user-pool-id=test-pool" })
@Import(SecurityConfig.class)
class SalarySlipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SalarySlipService salarySlipService;

    //「Cognitoで認証済みで、subは sub-user」というリクエストを再現する
    private RequestPostProcessor user() {
        return jwt().jwt(j -> j.subject("sub-user"));
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "slip.pdf", "application/pdf", "pdf-content".getBytes());
    }

    // ---------- 認証(全API共通) ----------

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/employees/4/salary-slips"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(salarySlipService);
    }

    // ---------- POST /api/salary-slips ----------

    @Test
    void upload_returns201_andPassesSubAndParametersToService() throws Exception {
        SalarySlipUploadResult result = new SalarySlipUploadResult();
        result.setId(7L);
        result.setEmployeeId(4L);
        result.setPayMonth("2026-10");
        when(salarySlipService.upload(eq("sub-user"), eq(4L), eq("2026-10"), eq(false), any())).thenReturn(result);

        mockMvc.perform(multipart("/api/salary-slips").file(pdf()).with(user())
                .param("employeeId", "4").param("payMonth", "2026-10"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.employeeId").value(4))
                .andExpect(jsonPath("$.payMonth").value("2026-10"));
        //overwriteを省略するとfalse(上書きしない)になる
        verify(salarySlipService).upload(eq("sub-user"), eq(4L), eq("2026-10"), eq(false), any());
    }

    @Test
    void upload_passesOverwriteFlag() throws Exception {
        when(salarySlipService.upload(anyString(), anyLong(), anyString(), anyBoolean(), any()))
                .thenReturn(new SalarySlipUploadResult());

        mockMvc.perform(multipart("/api/salary-slips").file(pdf()).with(user())
                .param("employeeId", "4").param("payMonth", "2026-10").param("overwrite", "true"))
                .andExpect(status().isCreated());
        verify(salarySlipService).upload(eq("sub-user"), eq(4L), eq("2026-10"), eq(true), any());
    }

    @Test
    void upload_returns400_whenFileIsMissing() throws Exception {
        mockMvc.perform(multipart("/api/salary-slips").with(user())
                .param("employeeId", "4").param("payMonth", "2026-10"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(salarySlipService);
    }

    @Test
    void upload_returns403_whenServiceRejects() throws Exception {
        when(salarySlipService.upload(anyString(), anyLong(), anyString(), anyBoolean(), any()))
                .thenThrow(new ForbiddenException("給与明細を管理する権限がありません"));

        mockMvc.perform(multipart("/api/salary-slips").file(pdf()).with(user())
                .param("employeeId", "4").param("payMonth", "2026-10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("給与明細を管理する権限がありません"));
    }

    @Test
    void upload_returns409_whenAlreadyExists() throws Exception {
        when(salarySlipService.upload(anyString(), anyLong(), anyString(), anyBoolean(), any()))
                .thenThrow(new ConflictException("既に2026-10分の給与明細が登録されています"));

        mockMvc.perform(multipart("/api/salary-slips").file(pdf()).with(user())
                .param("employeeId", "4").param("payMonth", "2026-10"))
                .andExpect(status().isConflict());
    }

    @Test
    void upload_returns404_whenTargetEmployeeDoesNotExist() throws Exception {
        when(salarySlipService.upload(anyString(), anyLong(), anyString(), anyBoolean(), any()))
                .thenThrow(new NotFoundException("社員が見つかりません"));

        mockMvc.perform(multipart("/api/salary-slips").file(pdf()).with(user())
                .param("employeeId", "999").param("payMonth", "2026-10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("社員が見つかりません"));
    }

    // DBの制約違反は、原因に合わせたメッセージで返す(以前は常に「value already in use」だった)
    @Test
    void upload_returns409WithMessage_whenDatabaseReportsDuplicate() throws Exception {
        when(salarySlipService.upload(anyString(), anyLong(), anyString(), anyBoolean(), any()))
                .thenThrow(new DuplicateKeyException("duplicate key"));

        mockMvc.perform(multipart("/api/salary-slips").file(pdf()).with(user())
                .param("employeeId", "4").param("payMonth", "2026-10"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("既に登録されている値と重複しています"));
    }

    @Test
    void upload_returns400WithMessage_whenDatabaseRejectsTheInput() throws Exception {
        //重複以外の制約違反(必須項目の未入力、存在しない社員の指定など)
        when(salarySlipService.upload(anyString(), anyLong(), anyString(), anyBoolean(), any()))
                .thenThrow(new DataIntegrityViolationException("foreign key violation"));

        mockMvc.perform(multipart("/api/salary-slips").file(pdf()).with(user())
                .param("employeeId", "4").param("payMonth", "2026-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("入力内容がデータベースの制約に合いません"));
    }

    // ---------- GET /api/employees/{employeeId}/salary-slips ----------

    @Test
    void list_returnsSlips_andPassesSubAndEmployeeId() throws Exception {
        SalarySlipSummary s = new SalarySlipSummary();
        s.setId(1L);
        s.setPayMonth("2026-10");
        when(salarySlipService.listByEmployee("sub-user", 4L)).thenReturn(List.of(s));

        mockMvc.perform(get("/api/employees/4/salary-slips").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].payMonth").value("2026-10"));
    }

    @Test
    void list_returns403_whenServiceRejects() throws Exception {
        when(salarySlipService.listByEmployee(anyString(), anyLong()))
                .thenThrow(new ForbiddenException("この給与明細を閲覧する権限がありません"));

        mockMvc.perform(get("/api/employees/1/salary-slips").with(user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_returns400_whenEmployeeIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/employees/abc/salary-slips").with(user()))
                .andExpect(status().isBadRequest());
    }

    // ---------- GET /api/salary-slips/{slipId}/download ----------

    @Test
    void download_returnsPdfBytes() throws Exception {
        byte[] bytes = "pdf-content".getBytes();
        when(salarySlipService.download("sub-user", 5L)).thenReturn(bytes);

        mockMvc.perform(get("/api/salary-slips/5/download").with(user()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void download_returns403_whenServiceRejects() throws Exception {
        when(salarySlipService.download(anyString(), anyLong()))
                .thenThrow(new ForbiddenException("この給与明細を閲覧する権限がありません"));

        mockMvc.perform(get("/api/salary-slips/5/download").with(user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void download_returns404_whenSlipNotFound() throws Exception {
        when(salarySlipService.download(anyString(), anyLong()))
                .thenThrow(new NotFoundException("給与明細が見つかりません"));

        mockMvc.perform(get("/api/salary-slips/99/download").with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("給与明細が見つかりません"));
    }

    // ---------- DELETE /api/salary-slips/{slipId} ----------

    @Test
    void delete_returns204_andPassesSubAndSlipId() throws Exception {
        mockMvc.perform(delete("/api/salary-slips/5").with(user()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(salarySlipService).delete("sub-user", 5L);
    }

    @Test
    void delete_withoutToken_returns401_andDoesNotCallService() throws Exception {
        mockMvc.perform(delete("/api/salary-slips/5"))
                .andExpect(status().isUnauthorized());
        verify(salarySlipService, never()).delete(anyString(), anyLong());
    }

    @Test
    void delete_returns403_whenServiceRejects() throws Exception {
        doThrow(new ForbiddenException("給与明細を管理する権限がありません"))
                .when(salarySlipService).delete("sub-user", 5L);

        mockMvc.perform(delete("/api/salary-slips/5").with(user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_returns404_whenSlipNotFound() throws Exception {
        doThrow(new NotFoundException("給与明細が見つかりません"))
                .when(salarySlipService).delete("sub-user", 99L);

        mockMvc.perform(delete("/api/salary-slips/99").with(user()))
                .andExpect(status().isNotFound());
    }

    // ---------- GET /api/salary-slips/history ----------

    @Test
    void history_returnsList_withDefaultPageAndSize() throws Exception {
        SalarySlipUploadHistory h = new SalarySlipUploadHistory();
        h.setSlipId(3L);
        h.setEmployeeId(4L);
        h.setPayMonth("2026-10");
        h.setUploadedBy(1L);
        h.setUploadedAt(LocalDateTime.of(2026, 10, 6, 15, 30, 0));
        when(salarySlipService.listHistory("sub-user", 0, 20)).thenReturn(List.of(h));

        mockMvc.perform(get("/api/salary-slips/history").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slipId").value(3))
                .andExpect(jsonPath("$[0].employeeId").value(4))
                .andExpect(jsonPath("$[0].payMonth").value("2026-10"))
                .andExpect(jsonPath("$[0].uploadedBy").value(1))
                .andExpect(jsonPath("$[0].uploadedAt").value("2026-10-06T15:30:00"));
        verify(salarySlipService).listHistory("sub-user", 0, 20);//未指定ならpage=0,size=20
    }

    @Test
    void history_passesQueryParametersToService() throws Exception {
        when(salarySlipService.listHistory("sub-user", 2, 5)).thenReturn(List.of());

        mockMvc.perform(get("/api/salary-slips/history").with(user())
                .param("page", "2").param("size", "5"))
                .andExpect(status().isOk());
        verify(salarySlipService).listHistory("sub-user", 2, 5);
    }

    @Test
    void history_returns403_whenServiceRejects() throws Exception {
        when(salarySlipService.listHistory(anyString(), anyInt(), anyInt()))
                .thenThrow(new ForbiddenException("給与明細を管理する権限がありません"));

        mockMvc.perform(get("/api/salary-slips/history").with(user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void history_returns400_whenServiceRejectsParameters() throws Exception {
        when(salarySlipService.listHistory(anyString(), anyInt(), anyInt()))
                .thenThrow(new ValidationException("sizeは1〜100で指定してください"));

        mockMvc.perform(get("/api/salary-slips/history").with(user()).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sizeは1〜100で指定してください"));
    }

    @Test
    void history_returns400_whenPageIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/salary-slips/history").with(user()).param("page", "abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(salarySlipService);
    }

    @Test
    void history_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/salary-slips/history"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(salarySlipService);
    }
}
