package com.example.pontal.dto;

//監査ログのエクスポート(CSV)の1行分。名前つきで取り出す
public class AuditLogExportRow {
    private java.time.LocalDateTime occurredAt;
    private Long actorEmployeeId;
    private String actorName;
    private Long targetEmployeeId;
    private String targetName;
    private String resourceType;
    private Long resourceId;
    private String action;

    public java.time.LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(java.time.LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    public Long getActorEmployeeId() { return actorEmployeeId; }
    public void setActorEmployeeId(Long actorEmployeeId) { this.actorEmployeeId = actorEmployeeId; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public Long getTargetEmployeeId() { return targetEmployeeId; }
    public void setTargetEmployeeId(Long targetEmployeeId) { this.targetEmployeeId = targetEmployeeId; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
