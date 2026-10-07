package com.example.pontal.service;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.pontal.dto.LoginEmployee;
import com.example.pontal.dto.SalarySlipSummary;
import com.example.pontal.dto.SalarySlipUploadHistory;
import com.example.pontal.dto.SalarySlipUploadResult;
import com.example.pontal.exception.ConflictException;
import com.example.pontal.exception.ForbiddenException;
import com.example.pontal.exception.NotFoundException;
import com.example.pontal.mapper.SalarySlipMapper;
import com.example.pontal.exception.ValidationException;

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
    private final EmployeeService employeeService;

    // application.propertiesのaws.s3.bucketの値を注入(.envのAWS_S3_BUCKETが実体)
    @Value("${aws.s3.bucket}")
    private String bucketName;

    //コンストラクタでDIにより部品を受け取る
    public SalarySlipService(SalarySlipMapper salarySlipMapper, S3Client s3Client,
        EmployeeService employeeService){
        this.salarySlipMapper = salarySlipMapper;
        this.s3Client = s3Client;
        this.employeeService = employeeService;
    }

    //給与明細アップロード処理(HR管理者のみ)
    public SalarySlipUploadResult upload(String cognitoSub, Long employeeId, String payMonth,
        boolean overwrite,MultipartFile file) throws IOException {
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);
        requireHrAdmin(login);
        Long uploadedBy = login.getEmployeeId();//アップロードした社員のID(ログイン中)
        // idと年月で給与明細存在するかチェック(boolean)
        boolean exists = salarySlipMapper.countByEmployeeAndMonth(employeeId, payMonth) > 0;
        // 存在する場合はコンフリクトエラーを返す
        if (exists && !overwrite) {
            throw new ConflictException("既に" + payMonth + "分の給与明細が登録されています");
        }

        String filePath = "salary-slips/" + payMonth + "/employee-" + employeeId + ".pdf";
        //AWS SDKのPutObjectRequestクラスはS3への保存リクエスト作成に使用
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName) //保存先バケット
                .key(filePath)      //バケットのどの場所に保存するか
                .build();           //保存リクエストの完成
        //保存リクエストと中身(PDF)を渡してS3に保存する処理
        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        // S3のどこに保存したかをDBに記録する。既存なら上書き更新、無ければ新規登録
        if (exists) {
            salarySlipMapper.updateFilePath(employeeId, payMonth, filePath, uploadedBy);
        } else {
            salarySlipMapper.insert(employeeId, payMonth, filePath, uploadedBy);
        }

        // 登録/更新した行のIDを取得してレスポンスを組み立てる
        Long id = salarySlipMapper.findIdByEmployeeAndMonth(employeeId, payMonth);
        // 登録/更新した結果を返す(明細ID,明細の対象社員ID,対象年月)
        SalarySlipUploadResult result = new SalarySlipUploadResult();
        result.setId(id);
        result.setEmployeeId(employeeId);
        result.setPayMonth(payMonth);
        return result;
    }

    // 特定社員の給与明細一覧を返すだけ(自分またはHR管理者)
    public List<SalarySlipSummary> listByEmployee(String cognitoSub, Long employeeId) {
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);
        requireSelfOrHrAdmin(login, employeeId);
        return salarySlipMapper.selectByEmployeeId(employeeId);
    }

    // 給与明細のPDFを取得する処理(自分またはHR管理者)
    public byte[] download(String cognitoSub, Long slipId) {
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);

        //明細IDに紐づく社員IDを取得
        Long ownerEmployeeId = salarySlipMapper.findEmployeeIdById(slipId);
        if (ownerEmployeeId == null) {
            throw new NotFoundException("給与明細が見つかりません");
        }
        requireSelfOrHrAdmin(login, ownerEmployeeId);
        //存在する場合ファイルパスを取得
        String filePath = salarySlipMapper.findFilePathById(slipId);

        if (filePath == null) {
            throw new NotFoundException("給与明細が見つかりません");
        }
        //GetObjectRequestクラスでS3への取得リクエストを作成
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();
        //ファイルはバイト列で取得
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
        return response.asByteArray();
    }

    //特定社員の給与明細を削除する処理(HR管理者)
    public void delete(String cognitoSub, Long slipId) {
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);
        requireHrAdmin(login);

        String filePath = salarySlipMapper.findFilePathById(slipId);

        // file_pathが無い=該当する給与明細が存在しない
        if (filePath == null) {
            throw new NotFoundException("給与明細が見つかりません");
        }
        //DeleteObjectRequestクラスでS3への削除リクエストを作成
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();
        //S3から削除実行
        s3Client.deleteObject(request);
        //DBから対象明細を削除
        salarySlipMapper.deleteById(slipId);
    }

    //アップロード履歴一覧を返す(HR管理者のみ、新しい順)
    public List<SalarySlipUploadHistory> listHistory(String cognitoSub, int page, int size) {
        LoginEmployee login = employeeService.getLoginEmployee(cognitoSub);
        requireHrAdmin(login);

        //バリデーション規約: page=0以上、size=1〜100
        if (page < 0) {
            throw new ValidationException("pageは0以上で指定してください");
        }
        if (size < 1 || size > 100) {
            throw new ValidationException("sizeは1〜100で指定してください");
        }

        long offset = (long) page * size;//何件目から取るか
        return salarySlipMapper.selectHistory(size, offset);
    }

    //HR管理者でなければ403権限エラーを返すメソッド
    private void requireHrAdmin(LoginEmployee login) {
        if (!login.getIsHrAdmin()) {
            throw new ForbiddenException("給与明細を管理する権限がありません");
        }
    }

    //明細持ち主本人またはHR管理者でなければ403権限エラーを返すメソッド
    private void requireSelfOrHrAdmin(LoginEmployee login, Long ownerEmployeeId) {
        boolean isSelf = login.getEmployeeId().equals(ownerEmployeeId);
        if (!isSelf && !login.getIsHrAdmin()) {
            throw new ForbiddenException("この給与明細を閲覧する権限がありません");
        }
    }
}
