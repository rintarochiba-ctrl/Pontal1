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
    -- ===== 全員向け(開発部・人事部から) =====
    ('【重要】システムメンテナンスのお知らせ(10/31 夜間)',
     E'下記のとおり、社内システムのメンテナンスを実施します。\n\n■日時\n10月31日(土) 22:00 〜 翌1:00(予定)\n\n■影響\n・社内ポータル(Pontal)\n・勤怠システム\n作業中は、ログインできません。\n\n■お願い\n・入力途中のデータは、前日までに保存してください。\n・作業が延びた場合は、このお知らせでお伝えします。\n\n不明な点は、開発部(内線 1234)までご連絡ください。',
     '社内情報', TRUE, (SELECT id FROM employee WHERE email = 'test@example.com'), '開発部',
     'ALL', NULL, NULL, now() - interval '1 day', now() - interval '1 day'),
    ('【重要】年末調整の書類提出のお願い',
     E'今年の年末調整の書類を、下記の期限までにご提出ください。\n\n■提出期限\n11月13日(金) 17:00\n\n■提出するもの\n1. 給与所得者の扶養控除等(異動)申告書\n2. 給与所得者の保険料控除申告書\n3. 保険料控除証明書(該当する方のみ)\n\n■提出方法\n人事部の回収ボックス(3階)に入れるか、PDFにして人事部あてにメールでお送りください。\n\n期限を過ぎると、年内の還付が間に合わない場合があります。ご注意ください。',
     '対応依頼', TRUE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'ALL', NULL, NULL, now() - interval '2 days', now() - interval '2 days'),
    ('社内勉強会「Spring Boot入門」開催のご案内',
     E'新入社員向けに、Spring Bootの勉強会を開きます。他の部署の方も、どなたでもご参加いただけます。\n\n■日時\n11月5日(木) 17:30 〜 18:30\n\n■場所\n大会議室(オンライン参加も可能です)\n\n■内容\n・Spring Bootとは\n・簡単なAPIを作ってみる\n・質疑応答\n\n■申し込み\n開発部の山田まで、メールでご連絡ください。当日の参加も歓迎です。',
     '技術', FALSE, (SELECT id FROM employee WHERE email = 'test@example.com'), '開発部',
     'ALL', NULL, NULL, now() - interval '3 days', now() - interval '3 days'),
    ('健康診断のご案内(11月)',
     E'定期健康診断を、下記の期間に実施します。\n\n■期間\n11月9日(月) 〜 11月20日(金)\n\n■予約\n人事部が配った案内の、QRコードから予約してください。希望の日程は、先着順です。\n\n■注意\n・前日の21時以降は、食事をとらないでください(水とお茶は大丈夫です)。\n・当日は、保険証と問診票をお持ちください。\n・業務の都合で受診できない方は、早めに人事部へご相談ください。',
     '社内情報', FALSE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'ALL', NULL, NULL, now() - interval '4 days', now() - interval '4 days'),
    ('11月の全社会議について',
     E'毎月恒例の全社会議を、11月も開催します。\n\n日時: 11月2日(月) 10:00 〜 11:00\n場所: 大会議室 / オンライン(リンクは、前日にメールでお送りします)\n\n議題\n・10月の業績報告\n・新しい社内ポータルの紹介\n・年末までのスケジュール\n\n出席できない方は、後日、録画を共有しますので、ご安心ください。',
     '社内情報', FALSE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'ALL', NULL, NULL, now() - interval '5 days', now() - interval '5 days'),
    ('情報セキュリティ研修(eラーニング)の受講のお願い',
     E'全社員を対象に、情報セキュリティ研修を実施します。\n\n■受講期限\n11月27日(金)\n\n■所要時間\n約30分(途中で中断しても、続きから再開できます)\n\n■内容\n・フィッシングメールの見分け方\n・パスワードの管理\n・個人情報の取り扱い\n\n受講が終わったら、最後の画面のスクリーンショットを、開発部に送ってください。',
     '対応依頼', FALSE, (SELECT id FROM employee WHERE email = 'test@example.com'), '開発部',
     'ALL', NULL, NULL, now() - interval '6 days', now() - interval '6 days'),
    ('【重要】社内Wi-Fiのパスワードを変更します',
     E'セキュリティ強化のため、社内Wi-Fiのパスワードを変更します。\n\n■変更日\n11月2日(月) 9:00\n\n■新しいパスワード\n各フロアの掲示板に、貼り出します(このお知らせには、書きません)。\n\n■お願い\n・変更後、Wi-Fiにつながらなくなったら、端末の「ネットワーク設定」で、古い接続情報を削除して、つなぎ直してください。\n・ゲストの方には、受付で、ゲスト用の接続情報をお渡しします。',
     '社内情報', TRUE, (SELECT id FROM employee WHERE email = 'test@example.com'), '開発部',
     'ALL', NULL, NULL, now() - interval '7 days', now() - interval '7 days'),
    ('年末年始の休業日について',
     E'年末年始の休業日は、次のとおりです。\n\n12月29日(火) 〜 1月3日(日)\n\n・仕事納めは、12月28日(月)です。\n・仕事始めは、1月4日(月)です。\n・休業中の緊急の連絡は、人事部の緊急連絡先まで、お願いします。',
     '社内情報', FALSE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'ALL', NULL, NULL, now() - interval '8 days', now() - interval '8 days'),
    ('新しい社員証への切り替えについて',
     E'セキュリティの強化に合わせて、社員証をICカードに切り替えます。\n\n■配布\n11月16日(月)から、人事部で、順番にお渡しします。\n\n■お願い\n・古い社員証は、新しいカードと引き換えに、お返しください。\n・紛失した場合は、すぐに、人事部まで届け出てください。',
     '対応依頼', FALSE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'ALL', NULL, NULL, now() - interval '9 days', now() - interval '9 days'),
    ('勤怠システムの打刻画面が変わります',
     E'11月1日(日)から、勤怠システムの打刻画面を、新しくします。\n\n・ボタンの位置が、変わります。\n・スマートフォンからも、打刻できるようになります。\n\n使い方の動画を、共有フォルダに置きました。',
     '社内情報', FALSE, (SELECT id FROM employee WHERE email = 'test@example.com'), '開発部',
     'ALL', NULL, NULL, now() - interval '10 days', now() - interval '10 days'),
    ('社内ポータル「Pontal」をリリースしました',
     E'社内ポータル「Pontal」の運用を、始めました。\n\n■できること\n・社員一覧の検索と、プロフィールの確認\n・自分の給与明細の確認とダウンロード\n・このお知らせの確認\n\n■ログイン\n配布したメールアドレスと、初期パスワードでログインしてください。初回は、新しいパスワードの設定が必要です。\n\n不具合やご要望は、開発部までお知らせください。',
     '社内情報', FALSE, (SELECT id FROM employee WHERE email = 'test@example.com'), '開発部',
     'ALL', NULL, NULL, now() - interval '12 days', now() - interval '12 days'),
    ('忘年会の出欠確認(12月18日)',
     E'今年の忘年会を、12月18日(金) 19:00から開催します。\n\n■会場\n会社近くの居酒屋(詳細は、出席の方に、あらためてお知らせします)\n\n■出欠\n11月10日(火)までに、人事部に、メールでご連絡ください。\n\n■会費\n3,000円(当日、集めます)\n\nご家庭の事情などで、参加できない方も、お気軽にご連絡ください。',
     '対応依頼', FALSE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'ALL', NULL, NULL, now() - interval '14 days', now() - interval '14 days'),
    ('【お願い】共有フォルダの整理と、古いファイルの削除について(11月末まで)',
     E'共有フォルダの容量が、残りわずかになっています。\n\n11月末までに、次のとおり、整理をお願いします。\n・1年以上使っていないファイルは、削除してください。\n・必要なファイルは、各部署のアーカイブフォルダに、移してください。\n\n11月30日(月)の夜に、古いファイルを、自動で削除します。',
     '対応依頼', FALSE, (SELECT id FROM employee WHERE email = 'test@example.com'), '開発部',
     'ALL', NULL, NULL, now() - interval '16 days', now() - interval '16 days'),

    -- ===== 部署宛て =====
    ('開発部:コードレビューのルール更新',
     E'コードレビューのルールを、次のように更新しました。\n\n1. レビューの依頼は、PRの説明に、確認してほしい点を書く\n2. レビューする人は、24時間以内に、最初のコメントを返す\n3. 変更が大きいときは、PRを分ける(目安: 500行まで)\n\nこれまでのPRにも、少しずつ、適用してください。',
     '技術', FALSE, (SELECT id FROM employee WHERE email = 'test@example.com'), '開発部',
     'DEPARTMENT', '開発部', NULL, now() - interval '2 days', now() - interval '2 days'),
    ('開発部:本番リリースの手順書を更新しました',
     E'本番リリースの手順書を更新しました。主な変更点です。\n\n・リリース前の、チェックリストを追加\n・ロールバックの手順を、具体的に記載\n・リリースは、原則、火曜と木曜の午前\n\n次回のリリースから、新しい手順書で、お願いします。',
     '技術', FALSE, (SELECT id FROM employee WHERE email = 'test@example.com'), '開発部',
     'DEPARTMENT', '開発部', NULL, now() - interval '6 days', now() - interval '6 days'),
    ('営業部:経費精算の締め日が変わります',
     E'経費精算の締め日が、11月から変わります。\n\n・これまで: 翌月5日\n・11月から: 当月25日\n\n営業部は、立替が多いため、特にご注意ください。締め日を過ぎると、翌月の精算になります。\n\nご不明な点は、人事部の佐藤まで。',
     '対応依頼', TRUE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'DEPARTMENT', '営業部', NULL, now() - interval '3 days', now() - interval '3 days'),
    ('人事部:評価面談のスケジュール',
     E'下半期の評価面談の日程を、共有しました。\n\n11月16日(月) 〜 11月20日(金)の間で、希望の時間帯を、共有カレンダーに入れてください。\n\n面談シートは、前日までに、提出をお願いします。',
     '対応依頼', FALSE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'DEPARTMENT', '人事部', NULL, now() - interval '4 days', now() - interval '4 days'),

    -- ===== 個人宛て =====
    ('山田さん宛て:経費精算の確認',
     E'先月分の経費精算に、確認したい点があります。\n\n10月12日のタクシー代について、領収書の日付と、申請の日付が異なっています。\nお手すきのときに、人事部まで、ご連絡ください。',
     '対応依頼', FALSE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'EMPLOYEE', NULL, (SELECT id FROM employee WHERE email = 'test@example.com'), now() - interval '5 hours', now() - interval '5 hours'),
    ('鈴木さん宛て:新人研修の講師のお願い',
     E'新入社員研修で、営業部の仕事について、30分ほどお話しいただけませんか。\n\n■日時\n11月12日(木) 14:00 〜 14:30\n\n■内容\n営業の1日の流れと、お客様との接し方の基本\n\nご都合を、人事部の佐藤まで、お知らせください。',
     '対応依頼', TRUE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'EMPLOYEE', NULL, (SELECT id FROM employee WHERE email = 'suzuki.ichiro@example.com'), now() - interval '1 day', now() - interval '1 day'),
    ('鈴木さん宛て:研修資料のお知らせ',
     E'新人研修の資料を、共有フォルダに置きました。\n\n置き場所: 共有フォルダ > 人事部 > 新人研修 > 2026年度\n当日までに、一度、目を通しておいてください。',
     '社内情報', FALSE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'EMPLOYEE', NULL, (SELECT id FROM employee WHERE email = 'suzuki.ichiro@example.com'), now() - interval '6 days', now() - interval '6 days'),
    ('権限なしテストさん宛て:入社手続きの書類について',
     E'ご入社、おめでとうございます。\n\n入社手続きに必要な書類を、メールでお送りしました。ご確認のうえ、今週中にご提出ください。\n\n不明な点は、人事部まで、お気軽にご連絡ください。',
     '社内情報', FALSE, (SELECT id FROM employee WHERE email = 'sato.hanako@example.com'), '人事部',
     'EMPLOYEE', NULL, (SELECT id FROM employee WHERE email = 'api006-test@example.com'), now() - interval '2 days', now() - interval '2 days');
