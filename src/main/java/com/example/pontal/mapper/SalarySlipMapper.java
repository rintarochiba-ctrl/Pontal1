package com.example.pontal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.example.pontal.dto.SalarySlipSummary;
import com.example.pontal.dto.SalarySlipUploadHistory;

//給与明細のDBアクセスMapper
public interface SalarySlipMapper {

    //アップロード時の重複チェックで使用、該当するrow数を返す
    int countByEmployeeAndMonth(@Param("employeeId") Long employeeId, @Param("payMonth") String payMonth);

    //一覧取得で使用、特定社員のidとpayMonthだけを一覧で返す
    List<SalarySlipSummary> selectByEmployeeId(@Param("employeeId") Long employeeId);

    //ダウンロードと削除に使用、S3のファイルパスを取得、なければnullを返す
    String findFilePathById(@Param("slipId") Long slipId);

    //削除時に使用、DBから該当するrowを削除する
    int deleteById(@Param("slipId") Long slipId);

    //アップロード時に使用、DBに新規登録する
    void insert(@Param("employeeId") Long employeeId, @Param("payMonth") String payMonth,
            @Param("filePath") String filePath, @Param("uploadedBy") Long uploadedBy);

    //アップロード時に使用(既存データを上書きする場合)
    void updateFilePath(@Param("employeeId") Long employeeId, @Param("payMonth") String payMonth,
                        @Param("filePath") String filePath, @Param("uploadedBy") Long uploadedBy);

    //アップロード時に使用、登録・更新後にレスポンスにIDを含める
    Long findIdByEmployeeAndMonth(@Param("employeeId") Long employeeId, @Param("payMonth") String payMonth);

    //ダウンロードの権限チェックで使用、明細の社員IDを取得、なければnullを返す
    Long findEmployeeIdById(@Param("slipId") Long slipId);

    //アップロード履歴一覧で使用、新しい順。offsetはスキップする件数
    List<SalarySlipUploadHistory> selectHistory(@Param("size") int size, @Param("offset") long offset);
}
