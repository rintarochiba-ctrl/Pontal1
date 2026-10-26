package com.example.pontal.dto;

import java.time.LocalDateTime;

//お知らせ詳細 GET /api/announcements/{id} などで使用(一覧の項目 + 本文・更新日時・操作可否)
public class AnnouncementDetail extends AnnouncementItem {
    private String body;
    private LocalDateTime updatedAt;
    //ログイン中の人が編集・削除できるか(画面のボタン表示用。実際の権限チェックはAPI側で行う)
    private boolean canEdit;
    private boolean canDelete;

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean getCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }

    public boolean getCanDelete() { return canDelete; }
    public void setCanDelete(boolean canDelete) { this.canDelete = canDelete; }
}
