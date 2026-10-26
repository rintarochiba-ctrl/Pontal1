package com.example.pontal.dto;

import java.time.LocalDateTime;

//お知らせ一覧の1件分 GET /api/announcements の items で使用(本文は含めない)
public class AnnouncementItem {
    private Long id;
    private String title;
    private String category;
    private boolean isImportant;
    private String senderDepartment;
    private String targetType;
    //宛先の表示名(ALL=「全員」、DEPARTMENT=部署名、EMPLOYEE=宛先の社員名)
    private String targetLabel;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean getIsImportant() { return isImportant; }
    public void setIsImportant(boolean isImportant) { this.isImportant = isImportant; }

    public String getSenderDepartment() { return senderDepartment; }
    public void setSenderDepartment(String senderDepartment) { this.senderDepartment = senderDepartment; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetLabel() { return targetLabel; }
    public void setTargetLabel(String targetLabel) { this.targetLabel = targetLabel; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
