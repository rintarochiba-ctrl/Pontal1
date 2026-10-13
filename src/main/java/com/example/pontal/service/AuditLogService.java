package com.example.pontal.service;

import org.springframework.stereotype.Service;

import com.example.pontal.enums.AuditAction;
import com.example.pontal.enums.AuditResourceType;
import com.example.pontal.mapper.AuditLogMapper;

//監査ログの記録を行うService(給与明細の取得・登録・削除や監査ログのダウンロード時に記録する)
@Service
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    //監査ログを1行記録する
    // 引数1:操作した社員のID,引数2:操作の対象社員ID,引数3:リソースの種類(給与明細orログ取得),引数4:リソースID,引数5:操作の種類
    public void record(Long actorEmployeeId, Long targetEmployeeId,
            AuditResourceType resourceType, Long resourceId, AuditAction action) {
        auditLogMapper.insert(actorEmployeeId, targetEmployeeId, resourceType, resourceId, action);
    }
}
