# Pontal

社内ポータルシステムのバックエンド API です
社員情報・給与明細・監査ログを扱い 認証は AWS Cognito で行います

## 技術

| 区分 | 内容 |
|---|---|
| 言語・FW | Java 17 / Spring Boot 3.2 |
| DB | PostgreSQL 16 / MyBatis |
| 認証 | AWS Cognito (JWT を検証) |
| ファイル保存 | AWS S3 (給与明細 PDF・監査ログ) |
| テスト | JUnit 5 / Mockito / Testcontainers |

## 必要なもの

- Docker Desktop (起動しておく)
- JDK 17 以上 と Maven (テストをローカルで実行する場合)
- AWS の認証情報 (Cognito と S3 を使うため)

## 初回セットアップ

### 1. `.env` を作る

プロジェクトの直下に `.env` を作って 次の値を書きます (`.env` は Git の管理外です)

```
COGNITO_REGION=ap-southeast-2
COGNITO_USER_POOL_ID=<ユーザープールID>
AWS_S3_BUCKET=<バケット名>
AWS_S3_REGION=<S3のリージョン>
AWS_ACCESS_KEY_ID=<アクセスキーID>
AWS_SECRET_ACCESS_KEY=<シークレットアクセスキー>
```

- Cognito の `AdminCreateUser` `AdminDisableUser` `AdminDeleteUser` と S3 の読み書きができる権限を持つ IAM ユーザーのキーを使います
- 値は管理者から受け取ってください

### 2. 起動する

```bash
docker compose up -d
```

| サービス | URL / ポート |
|---|---|
| API (app) | http://localhost:8092 |
| PostgreSQL | localhost:5433 (ユーザー `postgres` / DB `pontal`) |
| Swagger Editor (API 仕様) | http://localhost:8093 |

コードを変えたら アプリだけ作り直します

```bash
docker compose up -d --build app
```

## DB の仕組み

アプリは起動時に SQL を実行しません (再起動でデータが消えないようにするため)

| 場面 | テーブル作成 | ダミーデータ |
|---|---|---|
| 開発用 DB (Docker) | **空の DB が初めて作られるときだけ** 1 回 (`src/main/resources/schema/schema.sql`) | 同じとき 1 回 (`docker/dev-seed.sql`) |
| SQL のテスト | テスト実行時に毎回 (`schema.sql`) | `src/test/resources/data.sql` |

- 開発用 DB はテーブルや列を変えても 既存の DB には反映されません
- やり直したいときは DB のデータごと消して作り直します

```bash
docker compose down -v   # DB のデータも消して停止する
docker compose up -d     # 空の DB が作られ 初期化スクリプトが走る
```

- DB の中身を見るとき

```bash
docker exec -it pontal1_postgres psql -U postgres -d pontal
```

## 動作確認 (Postman / curl)

API は Cognito のアクセストークンが必要です (`Authorization: Bearer <トークン>`)

### トークンの取り方

```bash
curl -X POST https://cognito-idp.<リージョン>.amazonaws.com/ \
  -H "Content-Type: application/x-amz-json-1.1" \
  -H "X-Amz-Target: AWSCognitoIdentityProviderService.InitiateAuth" \
  -d '{"AuthFlow":"USER_PASSWORD_AUTH","ClientId":"<アプリクライアントID>","AuthParameters":{"USERNAME":"<ユーザー名>","PASSWORD":"<パスワード>"}}'
```

返ってきた `AuthenticationResult.AccessToken` を使います (有効期限は 1 時間)

### ダミーのユーザー

| 社員 | メール | 権限 |
|---|---|---|
| 山田太郎 | test@example.com | システム管理者 (Cognito に実在するユーザー) |
| 権限なしテスト | api006-test@example.com | なし (Cognito に実在するユーザー) |
| 佐藤花子 | sato.hanako@example.com | HR 管理者 (Cognito に未登録 一覧や詳細に出るだけ) |
| 鈴木一郎 | suzuki.ichiro@example.com | なし (Cognito に未登録) |

ログインに使うパスワードは管理者に確認してください

### ログの見方

```bash
docker logs pontal1_app --tail 100
```

## テスト

```bash
mvn test                              # 全部
mvn test -Dtest=EmployeeServiceTest   # 1 クラスだけ
mvn clean test                        # 古いビルドの影響を消してやり直す
```

| 種類 | 内容 |
|---|---|
| Service (Mockito) | 権限・順番・失敗時の動き |
| Controller (`@WebMvcTest`) | ステータスコード・入力チェック・JSON の形 |
| SQL (`@MybatisTest` + Testcontainers) | **Docker が必要** 本物の PostgreSQL で SQL を確認 |

## API 仕様

- `openapi/openapi.yaml` (Swagger Editor `http://localhost:8093` で表示)
- 詳細な設計 (機能一覧・API 設計・DB 設計) は Notion の「java 最終課題 (Pontal)」

## ディレクトリ

| パス | 内容 |
|---|---|
| `src/main/java/com/example/pontal/controller` | API の入口 |
| `.../service` | 業務ルール (権限チェックなど) |
| `.../mapper` と `src/main/resources/mapper` | DB アクセス (Mapper と SQL) |
| `.../dto` | リクエスト・レスポンスのデータの入れ物 |
| `.../exception` | 例外と エラーレスポンスへの変換 |
| `.../config` | Spring の設定 (認証・CORS・Cognito・MyBatis) |
| `src/main/resources/schema` | テーブル定義 (`schema.sql`) |
| `docker/` | 開発用ダミーデータ |
| `docs/` | ER 図 |
