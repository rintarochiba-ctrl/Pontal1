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

-- お知らせテーブル(社内のお知らせ。宛先は、全員・部署・個人のどれか1つ)
CREATE TABLE IF NOT EXISTS announcement (
    id                   BIGSERIAL PRIMARY KEY,
    title                VARCHAR(100) NOT NULL,
    body                 VARCHAR(2000) NOT NULL,
    -- 種類(技術・社内情報・対応依頼など)。選択肢は、まだ増える可能性があるので、DBのCHECK制約にはせず、Serviceで確認する
    category             VARCHAR(20) NOT NULL,
    is_important         BOOLEAN NOT NULL DEFAULT FALSE,
    sender_employee_id   BIGINT NOT NULL REFERENCES employee(id),
    -- 投稿した時点の部署(コピーして保存する。あとで異動しても、お知らせの差出人は変わらない)
    sender_department    VARCHAR(50) NOT NULL,
    target_type          VARCHAR(10) NOT NULL,
    target_department    VARCHAR(50),
    target_employee_id   BIGINT REFERENCES employee(id),
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP NOT NULL DEFAULT now(),
    is_delete            BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT announcement_target_type_check
        CHECK (target_type IN ('ALL', 'DEPARTMENT', 'EMPLOYEE')),
    -- 宛先の種類に合う列だけを入れて、ほかはNULLにする(全員向けは、部署も個人も空)
    CONSTRAINT announcement_target_consistency_check CHECK (
        (target_type = 'ALL'        AND target_department IS NULL     AND target_employee_id IS NULL)
     OR (target_type = 'DEPARTMENT' AND target_department IS NOT NULL AND target_employee_id IS NULL)
     OR (target_type = 'EMPLOYEE'   AND target_employee_id IS NOT NULL AND target_department IS NULL)
    )
);

-- 一覧(削除されていない・宛先の種類・新しい順)と、部署宛て・個人宛ての絞り込みを速くする
CREATE INDEX IF NOT EXISTS idx_announcement_list
    ON announcement (is_delete, target_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_announcement_target_department
    ON announcement (target_department);
CREATE INDEX IF NOT EXISTS idx_announcement_target_employee
    ON announcement (target_employee_id);
