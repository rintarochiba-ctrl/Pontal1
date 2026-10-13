package com.example.pontal.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.pontal.dto.AuditLogBatchSummary;
import com.example.pontal.dto.AuditLogExportBatch;
import com.example.pontal.dto.LoginEmployee;
import com.example.pontal.exception.ForbiddenException;
import com.example.pontal.exception.ValidationException;
import com.example.pontal.mapper.AuditLogExportBatchMapper;

@Service
public class AuditLogBatchService {

    private final AuditLogExportBatchMapper batchMapper;
    private final EmployeeService employeeService;

    public AuditLogBatchService(AuditLogExportBatchMapper batchMapper, EmployeeService employeeService) {
        this.batchMapper = batchMapper;
        this.employeeService = employeeService;
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
