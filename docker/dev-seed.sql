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

-- お知らせのダミーデータ(全員向け・部署宛て・個人宛て、重要・通常、いろいろな種類)
-- 差出人・宛先の社員は、メールアドレスで探す。日時は、いまから見た「何日前」で入れる
INSERT INTO announcement
    (title, body, category, is_important, sender_employee_id, sender_department,
     target_type, target_department, target_employee_id, created_at, updated_at)
VALUES
    ('【重要】システムメンテナンスのお知らせ',
     E'10月末の夜間に、社内システムのメンテナンスを行います。\n作業中は、一部の機能がご利用いただけません。',
     '社内情報', TRUE, (SELECT id FROM employee WHERE email = 'test@example.com'), '開発部',
     'ALL', NULL, NULL, now() - interval '1 day', now() - interval '1 day'),
    ('社内勉強会のご案内',
     E'来週、Spring Bootの勉強会を開きます。\n興味のある方は、どなたでもご参加ください。',
     '技術', FALSE, (SELECT id FROM employee WHERE email = 'test@example.com'), '開発部',
     'ALL', NULL, NULL, now() - interval '3 days', now() - interval '3 days'),
    ('開発部:コードレビューのルール更新',
     E'レビューの依頼は、PRの説明に、確認してほしい点を書いてください。',
     '技術', FALSE, (SELECT id FROM employee WHERE email = 'test@example.com'), '開発部',
     'DEPARTMENT', '開発部', NULL, now() - interval '2 days', now() - interval '2 days'),
    ('人事部:年末調整の書類提出について',
     E'今月末までに、書類をご提出ください。',
     '対応依頼', TRUE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'DEPARTMENT', '人事部', NULL, now() - interval '4 days', now() - interval '4 days'),
    ('山田さん宛て:経費精算の確認',
     E'先月分の経費精算に、確認したい点があります。\nお手すきのときに、ご連絡ください。',
     '対応依頼', FALSE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'EMPLOYEE', NULL, (SELECT id FROM employee WHERE email = 'test@example.com'), now() - interval '5 hours', now() - interval '5 hours'),
    ('鈴木さん宛て:研修資料のお知らせ',
     E'新人研修の資料を、共有フォルダに置きました。',
     '社内情報', FALSE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'EMPLOYEE', NULL, (SELECT id FROM employee WHERE email = 'suzuki.ichiro@example.com'), now() - interval '6 days', now() - interval '6 days');
