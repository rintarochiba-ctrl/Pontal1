package com.example.pontal.service;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.pontal.dto.AuditLogBatchFile;
import com.example.pontal.dto.AuditLogBatchSummary;
import com.example.pontal.dto.AuditLogExportBatch;
import com.example.pontal.dto.LoginEmployee;
import com.example.pontal.enums.AuditAction;
import com.example.pontal.enums.AuditResourceType;
import com.example.pontal.exception.ConflictException;
import com.example.pontal.exception.ExternalServiceException;
import com.example.pontal.exception.NotFoundException;
import com.example.pontal.exception.ForbiddenException;
import com.example.pontal.exception.ValidationException;
import com.example.pontal.mapper.AuditLogExportBatchMapper;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Service
public class AuditLogBatchService {
    private static final Logger log = LoggerFactory.getLogger(AuditLogBatchService.class);

    private final AuditLogExportBatchMapper batchMapper;
    private final EmployeeService employeeService;
    private final AuditLogService auditLogService;
    private final S3Client s3Client;

    //application.propertiesのaws.s3.bucketの値(.envのAWS_S3_BUCKETが実体)
    @Value("${aws.s3.bucket}")
    private String bucketName;

    public AuditLogBatchService(AuditLogExportBatchMapper batchMapper, EmployeeService employeeService,
            AuditLogService auditLogService, S3Client s3Client) {
        this.batchMapper = batchMapper;
        this.employeeService = employeeService;
        this.auditLogService = auditLogService;
        this.s3Client = s3Client;
    }

    //指定した年月のエクスポート一覧を返す(システム管理者のみ、週の開始日の古い順)
    public List<AuditLogBatchSummary> list(String cognitoSub, int year, int month) {
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);
        requireSystemAdmin(login);

        //バリデーション規約: year=4桁の整数、month=1〜12
        if (year < 1000 || year > 9999) {
            throw new ValidationException("yearは4桁の整数で指定してください");
        }
        if (month < 1 || month > 12) {
            throw new ValidationException("monthは1〜12で指定してください");
        }

        //週の開始日(月曜)が、その月の1日以上、翌月の1日未満のもの
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.plusMonths(1);
        return batchMapper.selectByPeriodStartBetween(from, to).stream()
                .map(this::toSummary)
                .toList();
    }

    //エクスポートされたCSVを取得する(システム管理者のみ)。成功したら、ダウンロードしたことを監査ログに記録する
    public AuditLogBatchFile download(String cognitoSub, Long batchId) {
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);
        requireSystemAdmin(login);

        AuditLogExportBatch batch = batchMapper.findById(batchId);
        if (batch == null) {
            throw new NotFoundException("監査ログのエクスポートが見つかりません");
        }
        //処理中・失敗のバッチは、まだファイルがない(または不完全)ので、ダウンロードさせない
        if (!"COMPLETED".equals(batch.getStatus())) {
            throw new ConflictException("このエクスポートはダウンロードできません(状態: " + batch.getStatus() + ")");
        }

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(batch.getFilePath())
                .build();
        byte[] content;
        try {
            content = s3Client.getObjectAsBytes(request).asByteArray();
        } catch (SdkException e) {
            //DBは完了なのにS3から取れない=記録とファイルの食い違い。管理者が調べられるようログに残す
            log.error("監査ログのファイルをS3から取得できませんでした batchId={} key={}", batchId, batch.getFilePath(), e);
            throw new ExternalServiceException("監査ログのファイルを取得できませんでした");
        }

        //取得に成功してから記録する。対象の社員はいないので、対象は実行者と同じ
        auditLogService.record(login.getEmployeeId(), login.getEmployeeId(),
                AuditResourceType.AUDIT_LOG_EXPORT, batchId, AuditAction.DOWNLOAD);

        String fileName = "audit-log_" + batch.getPeriodStart() + "_" + batch.getPeriodEnd() + ".csv";
        return new AuditLogBatchFile(fileName, content);
    }

    //DBの1行から、返す項目だけを取り出す
    private AuditLogBatchSummary toSummary(AuditLogExportBatch batch) {
        AuditLogBatchSummary summary = new AuditLogBatchSummary();
        summary.setBatchId(batch.getId());
        summary.setPeriodStart(batch.getPeriodStart());
        summary.setPeriodEnd(batch.getPeriodEnd());
        summary.setStatus(batch.getStatus());
        summary.setRecordCount(batch.getRecordCount());
        return summary;
    }

    //システム管理者でなければ403権限エラーを返すメソッド
    private void requireSystemAdmin(LoginEmployee login) {
        if (!login.getIsSystemAdmin()) {
            throw new ForbiddenException("監査ログを閲覧する権限がありません");
        }
    }
}
