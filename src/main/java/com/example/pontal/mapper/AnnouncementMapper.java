package com.example.pontal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.example.pontal.dto.Announcement;

//お知らせテーブルのDBアクセスMapper
//「誰に見えるか」の決まりは、ここ(SQL)に、1か所だけ置く。一覧と詳細で、同じ決まりを使うため
//scope(見られる範囲)は、次のどれか
//  all      自分が見られるもの全部(全員向け + 自分宛ての個人向け + 自分の部署宛て)
//  mine     自分宛ての個人向け + 自分の部署宛て
//  everyone 全員向けだけ
//  manage   宛先を問わず全件(システム管理者・HR管理者向け。この権限の確認は、Serviceで行う)
public interface AnnouncementMapper {

    //お知らせ一覧(本文は含めない)。重要が先、次に新しい順(同時刻は、idの大きい順)。論理削除済みは除く
    //viewerDepartment は、部署が未設定の人ならnull(その場合、部署宛てのお知らせには、一致しない)
    List<Announcement> selectVisible(@Param("scope") String scope,
            @Param("viewerId") Long viewerId,
            @Param("viewerDepartment") String viewerDepartment,
            @Param("category") String category,
            @Param("size") int size,
            @Param("offset") long offset);

    //お知らせ一覧の総件数(ページング計算用)。条件は、selectVisibleと同じ
    int countVisible(@Param("scope") String scope,
            @Param("viewerId") Long viewerId,
            @Param("viewerDepartment") String viewerDepartment,
            @Param("category") String category);

    //お知らせ詳細(本文つき)。「見られる人」だけが取れる。見られない・存在しない・論理削除済みは、どれもnull
    Announcement findVisibleById(@Param("id") Long id,
            @Param("scope") String scope,
            @Param("viewerId") Long viewerId,
            @Param("viewerDepartment") String viewerDepartment);

    //お知らせを1件取得(宛先を問わない)。編集・削除の前の、存在確認に使う。論理削除済み・存在しないならnull
    Announcement findById(@Param("id") Long id);

    //お知らせ作成。登録した行のidを返す(title, body, category, isImportant, 差出人, 宛先を使う)
    Long insert(@Param("a") Announcement announcement);

    //お知らせ編集。更新した行数を返す(0なら存在しない/論理削除済み)
    //差出人と作成日時は、変えない。宛先も、変えられる
    int update(@Param("id") Long id, @Param("a") Announcement announcement);

    //お知らせ削除(論理削除)。更新した行数を返す(0なら存在しない/削除済み)
    int softDeleteById(@Param("id") Long id);
}
