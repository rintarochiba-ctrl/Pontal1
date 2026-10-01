package com.example.pontal.config;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cognitoが発行したアクセストークン(JWT)の検証のみを行う認証基盤。
 * JwtDecoderはJWK SetをリクエストされたタイミングでLazy取得する構成にしており、
 * 起動時にCognitoへの疎通が無くてもアプリ自体は起動できる（Cognito側の一時的な
 * 障害がアプリ全体の起動不能に波及しないようにするため）。
 * isSystemAdmin/isHrAdminに基づく権限(GrantedAuthority)への変換は、
 * Employeeテーブル参照が実装される社員関連チケットで対応する。
 */

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // JacksonのObjectMapper。JavaのMapをJSON文字列に変換するために使う
    private final ObjectMapper objectMapper = new ObjectMapper();

    //Cognitoユーザープールのリージョン
    @Value("${cognito.region}")
    private String cognitoRegion;

    //CognitoユーザープールID
    @Value("${cognito.user-pool-id}")
    private String cognitoUserPoolId;

    @Bean //SpringBoot起動時に読み込む設定
    // アプリ全体の認証・認可ルールをまとめて定義する（CSRF、セッション管理、
    // どのリクエストに認証が必要か、JWT検証方式、エラー時の処理など）
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) //CSRF対策(トークン検証:default ON)をOFF
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) //ログイン状態は維持しない
                .authorizeHttpRequests(auth -> auth
                        // 既存のCorsFilterはSecurityのフィルタチェーンより後に評価され得るため、
                        // プリフライトがここで弾かれないよう明示的に許可する
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() //OPTIONSのみ認証免除
                        .anyRequest().authenticated()) //それ以外のパス・メソッドはログイン(JWT)必須にする
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder()))) //検証ロジックにjwtDecoderを使用
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::handleUnauthorized) //未ログインで弾かれた際の処理
                        .accessDeniedHandler(this::handleForbidden)); //ログイン済で権限不足で弾かれた際の処理

        return http.build(); //ここまでの設定を1つのルールとして組み立てて返す
    }

    @Bean //SpringBoot起動時に読み込む設定
    //Cognitoが発行したJWTを検証する仕組みを1つ作る
    public JwtDecoder jwtDecoder() {
        String issuer = "https://cognito-idp.%s.amazonaws.com/%s".formatted(cognitoRegion, cognitoUserPoolId);//ユーザープールのURL組み立て
        String jwkSetUri = issuer + "/.well-known/jwks.json";//JWTの署名検証するための公開鍵一覧の置き場所

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();//公開鍵はアプリ起動時ではなくトークン付きリクエストが来た時に取得しに行く
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));//有効期限チェック,issuserチェック
        return decoder;
    }
    //未ログインで弾かれた際の処理
    private void handleUnauthorized(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED, "authentication required");
    }
    //ログイン済みで権限不足で弾かれた際の処理
    private void handleForbidden(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        writeError(response, HttpStatus.FORBIDDEN, "access denied");
    }
    //上記2つの処理でControllerレスポンスの代替
    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("result", false, "message", message)));
    }
}
