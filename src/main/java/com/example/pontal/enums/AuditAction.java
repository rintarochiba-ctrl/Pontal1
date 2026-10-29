package com.example.pontal.enums;

//監査ログに記録する操作の種類
public enum AuditAction {
    DOWNLOAD, //給与明細ダウンロード
    CREATE,   //給与明細アップロード
    DELETE    //給与明細削除
}
