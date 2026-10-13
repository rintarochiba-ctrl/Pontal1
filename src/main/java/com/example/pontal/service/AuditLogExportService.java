package com.example.pontal.service;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.example.pontal.dto.AuditLogExportBatch;
import com.example.pontal.dto.AuditLogExportRow;
import com.example.pontal.mapper.AuditLogExportBatchMapper;
import com.example.pontal.mapper.AuditLogMapper;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

//監査ログの週次エクスポート。1週間(月曜〜日曜)分のログをCSVにしてS3に保存し、バッチ管理表に結果を記録する
@Service
public class AuditLogExportService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogExportService.class);

    private final AuditLogMapper auditLogMapper;
    private final AuditLogExportBatchMapper batchMapper;
    private final S3Client s3Client;

    //application.propertiesのaws.s3.bucketの値(.envのAWS_S3_BUCKETが実体)
    @Value("${aws.s3.bucket}")
    private String bucketName;

    public AuditLogExportService(AuditLogMapper auditLogMapper, AuditLogExportBatchMapper batchMapper,
            S3Client s3Client) {
        this.auditLogMapper = auditLogMapper;
        this.batchMapper = batchMapper;
        this.s3Client = s3Client;
    }

    //指定した週(periodStartは月曜日)の監査ログをCSVにして、S3に保存する
    //すでに完了している週は何もしない。失敗や処理中のまま残っている週は、同じ行を使ってやり直す
    public void exportWeek(LocalDate periodStart) {
        if (periodStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("週の開始日は月曜日を指定してください: " + periodStart);
        }
        LocalDate periodEnd = periodStart.plusDays(6); //日曜日

        Long batchId = startBatch(periodStart, periodEnd);
        if (batchId == null) {
            return; //すでに完了している(または別の実行が先に始めた)
        }

        try {
            //月曜0時以上、翌週月曜0時未満のログを集める
            List<AuditLogExportRow> rows = auditLogMapper.selectForExport(
                    periodStart.atStartOfDay(), periodEnd.plusDays(1).atStartOfDay());

            String key = buildKey(periodStart, periodEnd);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName).key(key).contentType("text/csv; charset=utf-8").build();
            s3Client.putObject(request, RequestBody.fromString(AuditLogCsvFormatter.format(rows), StandardCharsets.UTF_8));

            //S3に保存できてから、完了にする(件数とS3のパスも記録)
            batchMapper.markCompleted(batchId, rows.size(), key);
        } catch (RuntimeException e) {
            log.error("監査ログのエクスポートに失敗しました periodStart={}", periodStart, e);
            try {
                batchMapper.markFailed(batchId, toMessage(e));
            } catch (RuntimeException markError) {
                e.addSuppressed(markError); //記録に失敗しても、もともとのエラーを隠さない
            }
            throw e;
        }
    }

    //バッチ管理表の行を用意して、処理を始めてよければそのidを返す(すでに完了していればnull)
    private Long startBatch(LocalDate periodStart, LocalDate periodEnd) {
        AuditLogExportBatch existing = batchMapper.findByPeriodStart(periodStart);

        if (existing == null) {
            try {
                return batchMapper.insertProcessing(periodStart, periodEnd);
            } catch (DuplicateKeyException e) {
                log.info("同じ週のバッチが同時に作られたため、何もしません periodStart={}", periodStart);
                return null;
            }
        }
        if ("COMPLETED".equals(existing.getStatus())) {
            log.info("すでに完了しているため、何もしません periodStart={}", periodStart);
            return null;
        }
        //FAILEDやPROCESSING(途中で止まった)のままの週は、同じ行を処理中に戻してやり直す
        batchMapper.resetToProcessing(existing.getId());
        return existing.getId();
    }

    //S3のキー。年と月は、週の開始日(月曜)のもの  例: audit-logs/2026/10/2026-10-05_2026-10-11.csv
    private String buildKey(LocalDate periodStart, LocalDate periodEnd) {
        return String.format("audit-logs/%d/%02d/%s_%s.csv",
                periodStart.getYear(), periodStart.getMonthValue(), periodStart, periodEnd);
    }

    //失敗の原因を、管理表の列(500文字まで)に入る長さにする
    private String toMessage(RuntimeException e) {
        String message = e.getClass().getSimpleName() + ": " + e.getMessage();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
