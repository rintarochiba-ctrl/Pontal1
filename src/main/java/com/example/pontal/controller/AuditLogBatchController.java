package com.example.pontal.controller;

import java.util.List;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.pontal.dto.AuditLogBatchFile;
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

    @GetMapping("/api/audit-log-batches/{batchId}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable Long batchId){
        AuditLogBatchFile file = auditLogBatchService.download(jwt.getSubject(), batchId);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)) //CSV(UTF-8)
                //ブラウザに「保存するファイル」として扱わせ、ファイル名も伝える
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

}
