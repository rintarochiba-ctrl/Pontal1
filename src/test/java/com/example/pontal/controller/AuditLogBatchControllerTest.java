package com.example.pontal.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.example.pontal.dto.AuditLogBatchSummary;
import com.example.pontal.exception.ForbiddenException;
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
}
