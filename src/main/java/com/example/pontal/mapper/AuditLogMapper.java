package com.example.pontal.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

import com.example.pontal.dto.AuditLogExportRow;
import com.example.pontal.enums.AuditAction;
import com.example.pontal.enums.AuditResourceType;

//監査ログ(audit_logテーブル)のDBアクセスMapper
public interface AuditLogMapper {

    //監査ログを1行記録する(発生日時はDB側のnow()が自動で入る)
    void insert(@Param("actorEmployeeId") Long actorEmployeeId,
            @Param("targetEmployeeId") Long targetEmployeeId,
            @Param("resourceType") AuditResourceType resourceType,
            @Param("resourceId") Long resourceId,
            @Param("action") AuditAction action);

    //週次エクスポートで使用。startAt以上、endAt未満のログを、発生日時の古い順に取得(名前つき)
    List<AuditLogExportRow> selectForExport(@Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

}
