package com.example.pontal.dto;

//ログイン中の社員の情報 GET /api/me のレスポンスと、権限判定で使用
public class LoginEmployee {
    private Long employeeId;
    private String name;
    private boolean isSystemAdmin;
    private boolean isHrAdmin;

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean getIsSystemAdmin() { return isSystemAdmin; }
    public void setIsSystemAdmin(boolean isSystemAdmin) { this.isSystemAdmin = isSystemAdmin; }

    public boolean getIsHrAdmin() { return isHrAdmin; }
    public void setIsHrAdmin(boolean isHrAdmin) { this.isHrAdmin = isHrAdmin; }
}
