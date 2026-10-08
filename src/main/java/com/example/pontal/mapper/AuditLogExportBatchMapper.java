package com.example.pontal.mapper;


import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.example.pontal.dto.AuditLogExportBatch;

//監査ログの週次エクスポートバッチ(audit_log_export_batchテーブル)のDBアクセスMapper
public interface AuditLogExportBatchMapper {

    //その週(週の開始日)のバッチを取得、なければnullを返す
    AuditLogExportBatch findByPeriodStart(@Param("periodStart") LocalDate periodStart);

    //バッチIDで取得(ダウンロードで使用)、なければnullを返す
    AuditLogExportBatch findById(@Param("id") Long id);

    //一覧取得で使用。週の開始日がfromDate以上、toDate未満のバッチを、開始日の古い順に返す
    List<AuditLogExportBatch> selectByPeriodStartBetween(@Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    //新しい週のバッチを、処理中(PROCESSING)で登録し、そのidを返す
    Long insertProcessing(@Param("periodStart") LocalDate periodStart, @Param("periodEnd") LocalDate periodEnd);

    //失敗(FAILED)した週をやり直すとき、同じ行を処理中に戻す(週の開始日がUNIQUEで、新しい行は作れないため)
    int resetToProcessing(@Param("id") Long id);

    //成功したとき、完了(COMPLETED)にして、件数とS3のパスを記録する
    int markCompleted(@Param("id") Long id, @Param("recordCount") int recordCount,
            @Param("filePath") String filePath);

    //失敗したとき、失敗(FAILED)にして、原因を記録する
    int markFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);
}
