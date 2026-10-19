package com.example.pontal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

//Cognito(ユーザー作成・削除などのAdmin API)を呼ぶためのクライアント設定
@Configuration
public class CognitoConfig {

    //Cognitoユーザープールのリージョン
    @Value("${cognito.region}")
    private String cognitoRegion;

    @Bean
    //認証情報は指定しない(.envのAWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEYが環境変数として自動で使われる)
    public CognitoIdentityProviderClient cognitoClient() {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(cognitoRegion))
                .build();
    }
}
