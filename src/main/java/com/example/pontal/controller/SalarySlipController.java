package com.example.pontal.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import com.example.pontal.dto.SalarySlipSummary;
import com.example.pontal.dto.SalarySlipUploadResult;
import com.example.pontal.service.SalarySlipService;

//給与関係のAPIを提供するコントローラー
@RestController
public class SalarySlipController {

    private final SalarySlipService salarySlipService;

    public SalarySlipController(SalarySlipService salarySlipService) {
        this.salarySlipService = salarySlipService;
    }
    //給与明細アップロードAPI
    @PostMapping("/api/salary-slips")
    @ResponseStatus(HttpStatus.CREATED)//201作成成功を返す
    public SalarySlipUploadResult upload(
            //RequestParamでリクエストパラメータを受け取る
            @RequestParam Long employeeId,
            @RequestParam String payMonth,
            @RequestParam(defaultValue = "false") boolean overwrite,
            @RequestParam MultipartFile file) throws IOException {

        // ログインユーザー(HR管理者)のID 今は仮の値
        Long uploadedBy = 1L;

        return salarySlipService.upload(employeeId, payMonth, overwrite, uploadedBy, file);
    }

    //給与明細一覧取得API
    @GetMapping("/api/employees/{employeeId}/salary-slips")
    public List<SalarySlipSummary> list(@PathVariable Long employeeId) {
        return salarySlipService.listByEmployee(employeeId);
    }

    //給与明細PDFダウンロードAPI
    @GetMapping("/api/salary-slips/{slipId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long slipId) {
        byte[] content = salarySlipService.download(slipId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }


    //給与明細削除API
    @DeleteMapping("/api/salary-slips/{slipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long slipId) {
        salarySlipService.delete(slipId);
    }
}
