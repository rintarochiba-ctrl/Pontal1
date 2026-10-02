package com.example.pontal.service;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.pontal.dto.SalarySlipSummary;
import com.example.pontal.dto.SalarySlipUploadResult;
import com.example.pontal.exception.ConflictException;
import com.example.pontal.exception.NotFoundException;
import com.example.pontal.mapper.SalarySlipMapper;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class SalarySlipService {

    private final SalarySlipMapper salarySlipMapper;
    private final S3Client s3Client;

    // application.propertiesのaws.s3.bucketの値を注入(.envのAWS_S3_BUCKETが実体)
    @Value("${aws.s3.bucket}")
    private String bucketName;

    public SalarySlipService(SalarySlipMapper salarySlipMapper, S3Client s3Client) {
        this.salarySlipMapper = salarySlipMapper;
        this.s3Client = s3Client;
    }

    //給与明細アップロード処理(HR管理者)
    public SalarySlipUploadResult upload(Long employeeId, String payMonth, boolean overwrite,
        Long uploadedBy, MultipartFile file) throws IOException {
        boolean exists = salarySlipMapper.countByEmployeeAndMonth(employeeId, payMonth) > 0;

        if (exists && !overwrite) {
            throw new ConflictException("既に" + payMonth + "分の給与明細が登録されています");
        }

        String filePath = "salary-slips/" + payMonth + "/employee-" + employeeId + ".pdf";

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        // 既存なら上書き更新、無ければ新規登録
        if (exists) {
            salarySlipMapper.updateFilePath(employeeId, payMonth, filePath, uploadedBy);
        } else {
            salarySlipMapper.insert(employeeId, payMonth, filePath, uploadedBy);
        }

        // 登録/更新した行のIDを取得してレスポンスを組み立てる
        Long id = salarySlipMapper.findIdByEmployeeAndMonth(employeeId, payMonth);

        SalarySlipUploadResult result = new SalarySlipUploadResult();
        result.setId(id);
        result.setEmployeeId(employeeId);
        result.setPayMonth(payMonth);
        return result;
    }

    // 特定社員の給与明細一覧を返すだけ(自分またはHR管理者)
    public List<SalarySlipSummary> listByEmployee(Long employeeId) {
        return salarySlipMapper.selectByEmployeeId(employeeId);
    }

    // 特定社員の給与明細一覧からPDFボタンを押した時の処理(自分またはHR管理者)
    public byte[] download(Long slipId) {
        String filePath = salarySlipMapper.findFilePathById(slipId);

        if (filePath == null) {
            throw new NotFoundException("給与明細が見つかりません");
        }

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();

        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
        return response.asByteArray();
    }

    //特定社員の給与明細を削除する処理(HR管理者)
    public void delete(Long slipId) {
        String filePath = salarySlipMapper.findFilePathById(slipId);

        // file_pathが無い=該当する給与明細が存在しない
        if (filePath == null) {
            throw new NotFoundException("給与明細が見つかりません");
        }

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();
        s3Client.deleteObject(request);

        salarySlipMapper.deleteById(slipId);
    }
}
