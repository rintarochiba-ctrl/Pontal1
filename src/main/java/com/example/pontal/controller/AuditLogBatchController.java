package com.example.pontal.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.pontal.dto.AuditLogBatchSummary;
import com.example.pontal.service.AuditLogBatchService;

//監査ログのエクスポートAPIを提供するコントローラー
@RestController
public class AuditLogBatchController {

    private final AuditLogBatchService auditLogBatchService;

    public AuditLogBatchController(AuditLogBatchService auditLogBatchService) {
        this.auditLogBatchService = auditLogBatchService;
    }

    //監査ログのエクスポート一覧取得API(システム管理者のみ)
    @GetMapping("/api/audit-log-batches")
    public List<AuditLogBatchSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam int year,
            @RequestParam int month) {
        return auditLogBatchService.list(jwt.getSubject(), year, month);
    }
}
