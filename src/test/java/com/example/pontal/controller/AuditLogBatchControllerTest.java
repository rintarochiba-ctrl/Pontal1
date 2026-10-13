package com.example.pontal.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.example.pontal.config.SecurityConfig;
import com.example.pontal.dto.AuditLogBatchFile;
import com.example.pontal.dto.AuditLogBatchSummary;
import com.example.pontal.exception.ConflictException;
import com.example.pontal.exception.ExternalServiceException;
import com.example.pontal.exception.ForbiddenException;
import com.example.pontal.exception.NotFoundException;
import com.example.pontal.exception.ValidationException;
import com.example.pontal.service.AuditLogBatchService;

//AuditLogBatchControllerのテスト。Controllerと認証・例外変換だけを起動し、Serviceはモックにする
//「ログイン中の人のsubと、year・monthがServiceに渡ること」「Serviceの例外が正しいステータスになること」を確認する
@WebMvcTest(controllers = AuditLogBatchController.class, properties = {
        "cognito.region=ap-southeast-2",
        "cognito.user-pool-id=test-pool" })
@Import(SecurityConfig.class)
class AuditLogBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogBatchService auditLogBatchService;

    //「Cognitoで認証済みで、subは sub-user」というリクエストを再現する
    private RequestPostProcessor user() {
        return jwt().jwt(j -> j.subject("sub-user"));
    }

    // ---------- GET /api/audit-log-batches ----------

    @Test
    void list_returnsJsonArray_andPassesSubYearMonthToService() throws Exception {
        AuditLogBatchSummary s = new AuditLogBatchSummary();
        s.setBatchId(7L);
        s.setPeriodStart(LocalDate.of(2026, 10, 5));
        s.setPeriodEnd(LocalDate.of(2026, 10, 11));
        s.setStatus("COMPLETED");
        s.setRecordCount(3);
        when(auditLogBatchService.list("sub-user", 2026, 10)).thenReturn(List.of(s));

        mockMvc.perform(get("/api/audit-log-batches").with(user())
                .param("year", "2026").param("month", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].batchId").value(7))
                .andExpect(jsonPath("$[0].periodStart").value("2026-10-05"))
                .andExpect(jsonPath("$[0].periodEnd").value("2026-10-11"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].recordCount").value(3));
        verify(auditLogBatchService).list("sub-user", 2026, 10);
    }

    @Test
    void list_returnsEmptyArray_whenNoBatches() throws Exception {
        when(auditLogBatchService.list("sub-user", 2026, 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/audit-log-batches").with(user())
                .param("year", "2026").param("month", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void list_returns403_whenServiceRejects() throws Exception {
        when(auditLogBatchService.list(anyString(), anyInt(), anyInt()))
                .thenThrow(new ForbiddenException("監査ログを閲覧する権限がありません"));

        mockMvc.perform(get("/api/audit-log-batches").with(user())
                .param("year", "2026").param("month", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_returns400_whenServiceRejectsParameters() throws Exception {
        when(auditLogBatchService.list(anyString(), anyInt(), anyInt()))
                .thenThrow(new ValidationException("monthは1〜12で指定してください"));

        mockMvc.perform(get("/api/audit-log-batches").with(user())
                .param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("monthは1〜12で指定してください"));
    }

    @Test
    void list_returns400_whenYearIsMissing() throws Exception {
        mockMvc.perform(get("/api/audit-log-batches").with(user()).param("month", "10"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auditLogBatchService);
    }

    @Test
    void list_returns400_whenMonthIsMissing() throws Exception {
        mockMvc.perform(get("/api/audit-log-batches").with(user()).param("year", "2026"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auditLogBatchService);
    }

    @Test
    void list_returns400_whenYearIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/audit-log-batches").with(user())
                .param("year", "abc").param("month", "10"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auditLogBatchService);
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/audit-log-batches").param("year", "2026").param("month", "10"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auditLogBatchService);
    }

    // ---------- GET /api/audit-log-batches/{batchId}/download ----------

    @Test
    void download_returnsCsvFile_withContentTypeAndAttachmentHeaders() throws Exception {
        byte[] csv = "日時,操作\r\n".getBytes(StandardCharsets.UTF_8);
        when(auditLogBatchService.download("sub-user", 4L))
                .thenReturn(new AuditLogBatchFile("audit-log_2026-10-05_2026-10-11.csv", csv));

        mockMvc.perform(get("/api/audit-log-batches/4/download").with(user()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                //ブラウザに「保存するファイル」として扱わせ、ファイル名も伝える
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"audit-log_2026-10-05_2026-10-11.csv\""))
                .andExpect(content().bytes(csv));
        verify(auditLogBatchService).download("sub-user", 4L);
    }

    @Test
    void download_returns403_whenServiceRejects() throws Exception {
        when(auditLogBatchService.download(anyString(), anyLong()))
                .thenThrow(new ForbiddenException("監査ログを閲覧する権限がありません"));

        mockMvc.perform(get("/api/audit-log-batches/4/download").with(user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void download_returns404_whenBatchNotFound() throws Exception {
        when(auditLogBatchService.download(anyString(), anyLong()))
                .thenThrow(new NotFoundException("監査ログのエクスポートが見つかりません"));

        mockMvc.perform(get("/api/audit-log-batches/99/download").with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("監査ログのエクスポートが見つかりません"));
    }

    @Test
    void download_returns409_whenBatchIsNotCompleted() throws Exception {
        when(auditLogBatchService.download(anyString(), anyLong()))
                .thenThrow(new ConflictException("このエクスポートはダウンロードできません(状態: FAILED)"));

        mockMvc.perform(get("/api/audit-log-batches/4/download").with(user()))
                .andExpect(status().isConflict());
    }

    @Test
    void download_returns500_whenFileCannotBeReadFromS3() throws Exception {
        when(auditLogBatchService.download(anyString(), anyLong()))
                .thenThrow(new ExternalServiceException("監査ログのファイルを取得できませんでした"));

        mockMvc.perform(get("/api/audit-log-batches/4/download").with(user()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("監査ログのファイルを取得できませんでした"));
    }

    @Test
    void download_returns400_whenBatchIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/audit-log-batches/abc/download").with(user()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auditLogBatchService);
    }

    @Test
    void download_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/audit-log-batches/4/download"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auditLogBatchService);
    }
}
