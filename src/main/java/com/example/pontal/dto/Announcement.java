package com.example.pontal.dto;

import java.time.LocalDateTime;

//お知らせ1件(announcementテーブルの1行)。一覧・詳細・登録・編集で使う
//  ・一覧では body が入らない(本文は、一覧には出さないため)
//  ・targetEmployeeName は、読み取り専用(個人宛てのとき、宛先の社員の名前)。登録・編集では使わない
public class Announcement {
    private Long id;
    private String title;
    private String body;
    private String category;
    private boolean isImportant;
    private Long senderEmployeeId;
    private String senderDepartment;
    private String targetType;//ALL / DEPARTMENT / EMPLOYEE
    private String targetDepartment;
    private Long targetEmployeeId;
    private String targetEmployeeName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean getIsImportant() { return isImportant; }
    public void setIsImportant(boolean isImportant) { this.isImportant = isImportant; }

    public Long getSenderEmployeeId() { return senderEmployeeId; }
    public void setSenderEmployeeId(Long senderEmployeeId) { this.senderEmployeeId = senderEmployeeId; }

    public String getSenderDepartment() { return senderDepartment; }
    public void setSenderDepartment(String senderDepartment) { this.senderDepartment = senderDepartment; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetDepartment() { return targetDepartment; }
    public void setTargetDepartment(String targetDepartment) { this.targetDepartment = targetDepartment; }

    public Long getTargetEmployeeId() { return targetEmployeeId; }
    public void setTargetEmployeeId(Long targetEmployeeId) { this.targetEmployeeId = targetEmployeeId; }

    public String getTargetEmployeeName() { return targetEmployeeName; }
    public void setTargetEmployeeName(String targetEmployeeName) { this.targetEmployeeName = targetEmployeeName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
