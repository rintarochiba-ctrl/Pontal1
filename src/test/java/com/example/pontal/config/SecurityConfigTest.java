package com.example.pontal.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)//本物のHTTPサーバーを起動してテストする設定
class SecurityConfigTest {

    @Autowired
    private TestRestTemplate restTemplate;//HTTPリクエストを送るクライアント
    //トークン無しでアクセスすると401,SecurityConfigで決めたオブジェクトの形になっているかを確認
    @Test
    void unauthenticatedRequestReturns401WithErrorBody() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/anything", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"result\":false");
    }
    //CORSのOPTIONSリクエストが401にならないことを確認
    @Test
    void optionsPreflightIsNotBlockedByAuthentication() {
        ResponseEntity<String> response =
                restTemplate.exchange("/api/anything", HttpMethod.OPTIONS, null, String.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
