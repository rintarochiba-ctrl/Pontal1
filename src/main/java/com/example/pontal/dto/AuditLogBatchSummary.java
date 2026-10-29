package com.example.pontal.dto;

//監査ログのエクスポート一覧の1件分(API016のレスポンス)。S3のパスや失敗の詳細は返さない
public class AuditLogBatchSummary {
    private Long batchId;
    private java.time.LocalDate periodStart;
    private java.time.LocalDate periodEnd;
    private String status;
    private Integer recordCount;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public java.time.LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(java.time.LocalDate periodStart) { this.periodStart = periodStart; }

    public java.time.LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(java.time.LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRecordCount() { return recordCount; }
    public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }
}
