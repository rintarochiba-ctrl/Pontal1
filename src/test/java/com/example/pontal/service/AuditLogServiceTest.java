package com.example.pontal.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.pontal.enums.AuditAction;
import com.example.pontal.enums.AuditResourceType;
import com.example.pontal.mapper.AuditLogMapper;

//AuditLogServiceの単体テスト。Mapperはモックに差し替え、DBには繋がない
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void record_passesAllArgumentsToMapper() {
        //操作した人=1、対象=4、給与明細の5番、ダウンロード
        auditLogService.record(1L, 4L, AuditResourceType.SALARY_SLIP, 5L, AuditAction.DOWNLOAD);

        //引数の順番が入れ替わっていないことも確認する
        verify(auditLogMapper).insert(1L, 4L, AuditResourceType.SALARY_SLIP, 5L, AuditAction.DOWNLOAD);
    }

    @Test
    void record_worksForAuditLogExportResource() {
        //監査ログのエクスポートファイルのダウンロードは、対象社員=操作した人と同じ値で記録する
        auditLogService.record(2L, 2L, AuditResourceType.AUDIT_LOG_EXPORT, 10L, AuditAction.DOWNLOAD);

        verify(auditLogMapper).insert(2L, 2L, AuditResourceType.AUDIT_LOG_EXPORT, 10L, AuditAction.DOWNLOAD);
    }
}
