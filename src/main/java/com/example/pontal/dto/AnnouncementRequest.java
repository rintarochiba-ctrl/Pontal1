package com.example.pontal.dto;

//お知らせ作成・編集のリクエスト POST /api/announcements、PUT /api/announcements/{id} で使用
//入力チェックは、権限チェックのあとに行う決まりのため、@Validではなく、Serviceで行う
public class AnnouncementRequest {
    private String title;
    private String body;
    private String category;
    //未指定ならfalse(重要ではない)
    private boolean isImportant;
    //ALL / DEPARTMENT / EMPLOYEE のどれか
    private String targetType;
    //targetTypeがDEPARTMENTのときだけ指定する
    private String targetDepartment;
    //targetTypeがEMPLOYEEのときだけ指定する
    private Long targetEmployeeId;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean getIsImportant() { return isImportant; }
    public void setIsImportant(boolean isImportant) { this.isImportant = isImportant; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetDepartment() { return targetDepartment; }
    public void setTargetDepartment(String targetDepartment) { this.targetDepartment = targetDepartment; }

    public Long getTargetEmployeeId() { return targetEmployeeId; }
    public void setTargetEmployeeId(Long targetEmployeeId) { this.targetEmployeeId = targetEmployeeId; }
}
