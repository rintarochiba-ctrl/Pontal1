package com.example.pontal.dto;

//給与明細１件分の要約情報(id,対象年月) 給与明細一覧取得APIのレスポンスで使用
public class SalarySlipSummary {
    private Long id;
    private String payMonth;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPayMonth() { return payMonth; }
    public void setPayMonth(String payMonth) { this.payMonth = payMonth; }
}
