package com.example.pontal.dto;

// 給与明細アップロードAPIの成功レスポンス
public class SalarySlipUploadResult {
    private Long id;
    private Long employeeId;
    private String payMonth;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getPayMonth() { return payMonth; }
    public void setPayMonth(String payMonth) { this.payMonth = payMonth; }
}
