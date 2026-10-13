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

-- 給与明細PDF本体テーブル（1件ずつ同期アップロードする仕様のため、バッチ管理テーブルは無し）
CREATE TABLE IF NOT EXISTS salary_slip (
    id              BIGSERIAL PRIMARY KEY,
    employee_id     BIGINT NOT NULL REFERENCES employee(id),
    pay_month       VARCHAR(7) NOT NULL,
    file_path       VARCHAR(255) NOT NULL,
    uploaded_by     BIGINT NOT NULL REFERENCES employee(id),
    uploaded_at     TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (employee_id, pay_month)
);

-- 監査ログ本体テーブル（給与明細のダウンロード・登録・削除と、監査ログのダウンロードを記録する）
CREATE TABLE IF NOT EXISTS audit_log (
    id                  BIGSERIAL PRIMARY KEY,
    actor_employee_id   BIGINT NOT NULL REFERENCES employee(id),   -- 操作した人
    target_employee_id  BIGINT NOT NULL REFERENCES employee(id),   -- 操作された対象の社員(監査ログのダウンロードは操作者と同じ値)
    resource_type       VARCHAR(30) NOT NULL CHECK (resource_type IN ('SALARY_SLIP', 'AUDIT_LOG_EXPORT')),
    resource_id         BIGINT NOT NULL,                           -- 給与明細ID または エクスポートバッチID(FK制約なしの緩い参照)
    action              VARCHAR(20) NOT NULL CHECK (action IN ('DOWNLOAD', 'CREATE', 'DELETE')),
    occurred_at         TIMESTAMP NOT NULL DEFAULT now()
);

-- 週次バッチが「期間を指定して」ログを集めるため、発生日時で検索しやすくしておく
CREATE INDEX IF NOT EXISTS idx_audit_log_occurred_at ON audit_log (occurred_at);

-- 監査ログの週次エクスポートバッチ管理テーブル（1週間(月曜〜日曜)分で1行。外部キーなし）
CREATE TABLE IF NOT EXISTS audit_log_export_batch (
    id            BIGSERIAL PRIMARY KEY,
    period_start  DATE NOT NULL UNIQUE,                            -- 週の開始日(月曜)。同じ週のバッチが重複しないようUNIQUE
    period_end    DATE NOT NULL,                                   -- 週の終了日(日曜)
    status        VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    record_count  INT,                                             -- 対象のログ件数(0件の週は0)
    file_path     VARCHAR(255),                                    -- S3オブジェクトキー(完了後にセット)
    error_message VARCHAR(500),                                    -- FAILEDのときの原因(失敗したときだけセット)
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    completed_at  TIMESTAMP
);
