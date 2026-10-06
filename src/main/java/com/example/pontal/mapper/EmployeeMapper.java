package com.example.pontal.mapper;

import org.apache.ibatis.annotations.Param;

import com.example.pontal.dto.EmployeeCreateRequest;
import com.example.pontal.dto.EmployeeDetail;
import com.example.pontal.dto.LoginEmployee;
import com.example.pontal.dto.EmployeeSummary;
import com.example.pontal.dto.EmployeeUpdateRequest;
import java.util.List;

//社員テーブルのDBアクセスMapper
public interface EmployeeMapper {

    //JWTのsubからログイン社員を取得、論理削除済み・未登録ならnullを返す
    LoginEmployee findByCognitoSub(@Param("cognitoSub") String cognitoSub);

    //社員詳細取得で使用、論理削除済み・存在しないidならnullを返す
    EmployeeDetail findDetailById(@Param("id") Long id);

    //社員一覧取得で使用、keywordがnull/空なら全件が対象。offsetはスキップする件数
    List<EmployeeSummary> selectByKeyword(@Param("keyword") String keyword,
            @Param("size") int size, @Param("offset") long offset);

    //社員一覧の総件数(ページング計算用)、検索条件はselectByKeywordと同じ
    int countByKeyword(@Param("keyword") String keyword);

    //社員編集で使用、更新した行数を返す(0なら存在しない/論理削除済み)
    int update(@Param("id") Long id, @Param("req") EmployeeUpdateRequest req);

    //社員登録の重複チェックで使用、論理削除済みも含めて数える(emailはテーブル全体でUNIQUEのため)
    int countByEmail(@Param("email") String email);

    //社員登録で使用、登録した行のidを返す
    Long insert(@Param("req") EmployeeCreateRequest req, @Param("cognitoSub") String cognitoSub);

    //社員削除後のCognito無効化で使用、論理削除済みでも取得する(削除直後にsubを引くため)。存在しなければnull
    String findCognitoSubById(@Param("id") Long id);

    //社員削除で使用、論理削除(is_delete=true)して更新した行数を返す(0なら存在しない/削除済み)
    int softDeleteById(@Param("id") Long id);

}
