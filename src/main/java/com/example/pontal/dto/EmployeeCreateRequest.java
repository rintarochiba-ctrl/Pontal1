package com.example.pontal.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

//社員登録APIのリクエスト POST /api/employees で使用
public class EmployeeCreateRequest {
    @NotBlank
    @Size(max = 50)
    private String name;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    //Cognitoのユーザー作成に使う初期(仮)パスワード、Pontal側のDBには保存しない
    @NotBlank
    private String initialPassword;

    @NotNull //DBのjoin_dateがNOT NULLのため必須
    private LocalDate joinDate;

    @NotBlank
    @Size(max = 50)
    private String department;

    @Size(max = 50)
    private String position;

    //未指定ならfalse(権限なし)になる
    private boolean isSystemAdmin;
    private boolean isHrAdmin;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getInitialPassword() { return initialPassword; }
    public void setInitialPassword(String initialPassword) { this.initialPassword = initialPassword; }

    public LocalDate getJoinDate() { return joinDate; }
    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public boolean getIsSystemAdmin() { return isSystemAdmin; }
    public void setIsSystemAdmin(boolean isSystemAdmin) { this.isSystemAdmin = isSystemAdmin; }

    public boolean getIsHrAdmin() { return isHrAdmin; }
    public void setIsHrAdmin(boolean isHrAdmin) { this.isHrAdmin = isHrAdmin; }
}
