package com.example.pontal.dto;

// アップロード履歴一覧の1件分(誰が・いつ・誰の分をアップロードしたか)
public class SalarySlipUploadHistory {
    private Long slipId;
    private Long employeeId;
    private String payMonth;
    private Long uploadedBy;
    private java.time.LocalDateTime uploadedAt;

    public Long getSlipId() { return slipId; }
    public void setSlipId(Long slipId) { this.slipId = slipId; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getPayMonth() { return payMonth; }
    public void setPayMonth(String payMonth) { this.payMonth = payMonth; }

    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }

    public java.time.LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(java.time.LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
