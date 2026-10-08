package com.example.pontal.dto;

//監査ログの週次エクスポートバッチ(audit_log_export_batchの1行)
public class AuditLogExportBatch {
    private Long id;
    private java.time.LocalDate periodStart;
    private java.time.LocalDate periodEnd;
    private String status;
    private Integer recordCount;
    private String filePath;
    private String errorMessage;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.time.LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(java.time.LocalDate periodStart) { this.periodStart = periodStart; }

    public java.time.LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(java.time.LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRecordCount() { return recordCount; }
    public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }

    public java.time.LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(java.time.LocalDateTime completedAt) { this.completedAt = completedAt; }
}
