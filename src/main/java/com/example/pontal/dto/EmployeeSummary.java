package com.example.pontal.dto;
//社員1人のサマリークラス 一覧表示の一つ一つに使用
public class EmployeeSummary {
    private Long id;
    private String name;
    private String email;
    private String department;
    private String position;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
}
