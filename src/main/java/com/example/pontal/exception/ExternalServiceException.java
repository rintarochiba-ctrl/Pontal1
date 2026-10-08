package com.example.pontal.exception;

//外部サービス(Cognitoなど)の操作に失敗したことを表す例外。DBの更新は済んでいる場合があり、管理者に知らせる必要があるときに使う
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }
}
