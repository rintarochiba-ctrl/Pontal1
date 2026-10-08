package com.example.pontal.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import com.example.pontal.dto.SalarySlipUploadHistory;
import com.example.pontal.dto.SalarySlipSummary;
import com.example.pontal.dto.SalarySlipUploadResult;
import com.example.pontal.service.SalarySlipService;

//給与関係のAPIを提供するコントローラー
@RestController
public class SalarySlipController {
    // SalarySlipServiceをDI注入するためのフィールド
    private final SalarySlipService salarySlipService;
    // コンストラクタでDI注入
    public SalarySlipController(SalarySlipService salarySlipService) {
        this.salarySlipService = salarySlipService;
    }
    //給与明細アップロードAPI
    // POST /api/salary-slipsでアップロード成功後に201,既にあれば409結果を返す
    @PostMapping("/api/salary-slips")
    @ResponseStatus(HttpStatus.CREATED)
    public SalarySlipUploadResult upload(
            @AuthenticationPrincipal Jwt jwt, //SpringSecurityが渡す検証済jwtをオブジェクトとして格納
            //RequestParamでリクエストパラメータを受け取る(ファイルを送るリクエストはJSONではなくmultipart/form-data形式)
            @RequestParam Long employeeId,
            @RequestParam String payMonth,
            @RequestParam(defaultValue = "false") boolean overwrite,
            @RequestParam MultipartFile file) throws IOException {
        return salarySlipService.upload(jwt.getSubject(), employeeId, payMonth, overwrite, file);
    }

    //給与明細一覧取得API
    @GetMapping("/api/employees/{employeeId}/salary-slips")
    public List<SalarySlipSummary> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long employeeId) {
        return salarySlipService.listByEmployee(jwt.getSubject(), employeeId);
    }

    //給与明細PDFダウンロードAPI
    @GetMapping("/api/salary-slips/{slipId}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable Long slipId) { //ResponseEntityはレスポンス要素(status,header,body)を含む
        byte[] content = salarySlipService.download(jwt.getSubject(), slipId); //byte[]にはPDF1ファイル分のバイト列が入る

        return ResponseEntity.ok() //status 200
                .contentType(MediaType.APPLICATION_PDF) //header
                .body(content); //body PDFファイル
    }

    //給与明細削除API
    @DeleteMapping("/api/salary-slips/{slipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT) //status 204
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long slipId) {
        salarySlipService.delete(jwt.getSubject(), slipId);
    }

    //アップロード履歴一覧取得API(HR管理者のみ)
    @GetMapping("/api/salary-slips/history")
    public List<SalarySlipUploadHistory> history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page, //ページ番号
            @RequestParam(defaultValue = "20") int size) { //1ページあたりの件数
        return salarySlipService.listHistory(jwt.getSubject(), page, size);
    }
}
