-- PonzPoint 「DBスキーマ」はテーブル設計図そのものを指す
-- どんなテーブルか,どんなカラム(列)があって型は何か,主キー・外部キー・NOT NULL・UNIQUEなどの制約

-- 社員テーブル
CREATE TABLE IF NOT EXISTS employee (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(50) NOT NULL,
    email            VARCHAR(255) NOT NULL UNIQUE,
    cognito_sub      VARCHAR(36) NOT NULL UNIQUE,
    department       VARCHAR(50),
    position         VARCHAR(50),
    join_date        DATE NOT NULL,
    gender           VARCHAR(10),
    age              SMALLINT,
    birthplace       VARCHAR(50),
    is_system_admin  BOOLEAN NOT NULL DEFAULT FALSE,
    is_hr_admin      BOOLEAN NOT NULL DEFAULT FALSE,
    image_url        VARCHAR(255),
    bio              TEXT,
    hobby            VARCHAR(255),
    self_qa          TEXT,
    is_delete        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);
