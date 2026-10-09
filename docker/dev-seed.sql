-- 開発用のダミーデータ(Postmanなどで動作確認するためのもの)
-- docker-compose.ymlで /docker-entrypoint-initdb.d/ に置いてあり、
-- 「空のDBが初めて作られるとき」に1回だけ、schema.sqlの後に実行される。アプリの再起動では実行されない。
-- 山田太郎は、実在するCognitoユーザー(ログインできる)のsubと紐づけてある。
-- やり直したいとき: docker compose down -v (DBのデータも消えて、次の起動で最初から作り直される)

INSERT INTO employee
    (name, email, cognito_sub, department, position, join_date, gender, age, birthplace, is_system_admin, is_hr_admin, bio, hobby)
VALUES
    ('山田太郎', 'test@example.com', '29cec488-b011-70d2-5648-27e12d2e21c7', '開発部', 'エンジニア', '2023-04-01', '男性', 28, '東京都', TRUE, FALSE, 'よろしくお願いします。', '読書'),
    ('佐藤花子', 'sato.hanako@example.com', 'a1b2c3d4-0000-4000-8000-000000000001', '人事部', '課長', '2019-07-15', '女性', 35, '大阪府', FALSE, TRUE, '人事部で働いています。', 'ヨガ'),
    ('鈴木一郎', 'suzuki.ichiro@example.com', 'a1b2c3d4-0000-4000-8000-000000000002', '営業部', '主任', '2021-10-01', '男性', 30, '愛知県', FALSE, FALSE, 'よろしくお願いします！', 'サッカー観戦'),
    ('権限なしテスト', 'api006-test@example.com', '29ce5458-0061-70ce-c27d-97f91a18ca66', '開発部', 'エンジニア', '2026-10-01', NULL, NULL, NULL, FALSE, FALSE, NULL, NULL);
