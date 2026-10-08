package com.example.pontal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.pontal.dto.LoginEmployee;
import com.example.pontal.dto.SalarySlipSummary;
import com.example.pontal.dto.SalarySlipUploadHistory;
import com.example.pontal.dto.SalarySlipUploadResult;
import com.example.pontal.exception.ConflictException;
import com.example.pontal.exception.ForbiddenException;
import com.example.pontal.exception.NotFoundException;
import com.example.pontal.exception.ValidationException;
import com.example.pontal.mapper.SalarySlipMapper;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

//SalarySlipServiceの単体テスト。Mapper・S3・EmployeeServiceはモックに差し替え、DBやAWSには繋がない
//特に「権限が無いときは、DBにもS3にも触れずに403で止まる」ことを確認する
@ExtendWith(MockitoExtension.class)
class SalarySlipServiceTest {

    @Mock
    private SalarySlipMapper salarySlipMapper;

    @Mock
    private S3Client s3Client;

    @Mock
    private EmployeeService employeeService;//ログイン社員の特定は、社員側のテストで確認済みなのでモックにする

    @InjectMocks
    private SalarySlipService salarySlipService;

    @BeforeEach
    void setUp() {
        //@Valueで注入されるバケット名は、Spring無しでは入らないため直接セットする
        ReflectionTestUtils.setField(salarySlipService, "bucketName", "test-bucket");
    }

    //テスト用のログイン社員を作る
    private LoginEmployee login(Long id, boolean isHrAdmin) {
        LoginEmployee e = new LoginEmployee();
        e.setEmployeeId(id);
        e.setName("テスト");
        e.setIsSystemAdmin(false);
        e.setIsHrAdmin(isHrAdmin);
        return e;
    }

    //subが "sub-hr" の人は、HR管理者(id=1)としてログインしている状態にする
    private void loginAsHr() {
        when(employeeService.getLoginEmployee("sub-hr")).thenReturn(login(1L, true));
    }

    //subが "sub-member" の人は、一般社員(id=4)としてログインしている状態にする
    private void loginAsMember() {
        when(employeeService.getLoginEmployee("sub-member")).thenReturn(login(4L, false));
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "slip.pdf", "application/pdf", "pdf-content".getBytes());
    }

    // ---------- upload (API011) ----------

    @Test
    void upload_throws403_whenNotHrAdmin_andTouchesNothing() {
        loginAsMember();

        assertThatThrownBy(() -> salarySlipService.upload("sub-member", 4L, "2026-10", false, pdf()))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(salarySlipMapper);//DBにも
        verifyNoInteractions(s3Client);//S3にも触れない
    }

    // payMonthはS3のパスの一部になるので、YYYY-MM(月は01〜12)以外は400で止める
    @ParameterizedTest
    @ValueSource(strings = { "2026-13", "2026-00", "2026-1", "26-10", "202610", "2026/10", "abc", "../x", "2026-10 ", " ", "" })
    void upload_throws400_whenPayMonthFormatIsInvalid(String invalidPayMonth) {
        loginAsHr();

        assertThatThrownBy(() -> salarySlipService.upload("sub-hr", 4L, invalidPayMonth, false, pdf()))
                .isInstanceOf(ValidationException.class);
        //不正な値は、DBにもS3にも届かない
        verifyNoInteractions(salarySlipMapper);
        verifyNoInteractions(s3Client);
    }

    @Test
    void upload_throws403_beforeValidatingPayMonth() {
        loginAsMember();

        //権限が無い人には、入力の不備(400)ではなく、先に403を返す
        assertThatThrownBy(() -> salarySlipService.upload("sub-member", 4L, "abc", false, pdf()))
                .isInstanceOf(ForbiddenException.class);
    }

    // ファイルは、中身があり、拡張子が.pdfのものだけを受け付ける。それ以外は400で止める
    @ParameterizedTest
    @ValueSource(strings = { "slip.txt", "slip.docx", "slip.pdf.exe", "slip", "pdf", "" })
    void upload_throws400_whenFileIsNotPdf(String fileName) {
        loginAsHr();
        MockMultipartFile notPdf = new MockMultipartFile("file", fileName, "application/octet-stream", "content".getBytes());

        assertThatThrownBy(() -> salarySlipService.upload("sub-hr", 4L, "2026-10", false, notPdf))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(salarySlipMapper);
        verifyNoInteractions(s3Client);//PDF以外はS3に保存しない
    }

    @Test
    void upload_throws400_whenFileIsEmpty() {
        loginAsHr();
        MockMultipartFile empty = new MockMultipartFile("file", "slip.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> salarySlipService.upload("sub-hr", 4L, "2026-10", false, empty))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    void upload_acceptsUpperCaseExtension() throws Exception {
        loginAsHr();
        when(salarySlipMapper.countByEmployeeAndMonth(4L, "2026-10")).thenReturn(0);
        when(salarySlipMapper.findIdByEmployeeAndMonth(4L, "2026-10")).thenReturn(1L);
        MockMultipartFile upper = new MockMultipartFile("file", "SLIP.PDF", "application/pdf", "pdf".getBytes());

        salarySlipService.upload("sub-hr", 4L, "2026-10", false, upper);

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));//.PDFも受け付ける
    }

    @Test
    void upload_throws403_beforeValidatingFile() {
        loginAsMember();
        MockMultipartFile notPdf = new MockMultipartFile("file", "slip.txt", "text/plain", "content".getBytes());

        //権限が無い人には、ファイルの不備(400)ではなく、先に403を返す
        assertThatThrownBy(() -> salarySlipService.upload("sub-member", 4L, "2026-10", false, notPdf))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void upload_throws404_whenTargetEmployeeDoesNotExist() {
        loginAsHr();
        //アップロード先の社員が、存在しない(または論理削除済み)場合
        when(employeeService.getDetail(999L)).thenThrow(new NotFoundException("社員が見つかりません"));

        assertThatThrownBy(() -> salarySlipService.upload("sub-hr", 999L, "2026-10", false, pdf()))
                .isInstanceOf(NotFoundException.class);
        //存在しない社員の分は、DBにもS3にも保存しない
        verifyNoInteractions(salarySlipMapper);
        verifyNoInteractions(s3Client);
    }

    // 年月から、社員別のS3のパスが作られる(salary-slips/employee-{社員ID}/{年}/{月}.pdf)
    @ParameterizedTest
    @CsvSource({
            "2026-01, salary-slips/employee-4/2026/01.pdf",
            "2026-10, salary-slips/employee-4/2026/10.pdf",
            "2026-12, salary-slips/employee-4/2026/12.pdf" })
    void upload_savesToPerEmployeePath(String payMonth, String expectedKey) throws Exception {
        loginAsHr();
        when(salarySlipMapper.countByEmployeeAndMonth(4L, payMonth)).thenReturn(0);
        when(salarySlipMapper.findIdByEmployeeAndMonth(4L, payMonth)).thenReturn(1L);

        salarySlipService.upload("sub-hr", 4L, payMonth, false, pdf());

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().key()).isEqualTo(expectedKey);//S3に保存するキー
        verify(salarySlipMapper).insert(4L, payMonth, expectedKey, 1L);//DBに記録するfile_pathも同じ
    }

    @Test
    void upload_doesNotUseOriginalFileName_forS3Key() throws Exception {
        loginAsHr();
        when(salarySlipMapper.countByEmployeeAndMonth(4L, "2026-10")).thenReturn(0);
        when(salarySlipMapper.findIdByEmployeeAndMonth(4L, "2026-10")).thenReturn(1L);
        //アップロードする人の手元のファイル名が何でも、保存名はサーバーが決める
        MockMultipartFile file = new MockMultipartFile("file", "給与明細_最終版(1).pdf", "application/pdf", "pdf".getBytes());

        salarySlipService.upload("sub-hr", 4L, "2026-10", false, file);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().key()).isEqualTo("salary-slips/employee-4/2026/10.pdf");
    }

    @Test
    void upload_throws409_whenAlreadyExistsAndNoOverwrite() {
        loginAsHr();
        when(salarySlipMapper.countByEmployeeAndMonth(4L, "2026-10")).thenReturn(1);

        assertThatThrownBy(() -> salarySlipService.upload("sub-hr", 4L, "2026-10", false, pdf()))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(s3Client);//409で止まるときは、S3に何も書かない
    }

    @Test
    void upload_insertsNewSlip_withLoginEmployeeAsUploader() throws Exception {
        loginAsHr();
        when(salarySlipMapper.countByEmployeeAndMonth(4L, "2026-10")).thenReturn(0);
        when(salarySlipMapper.findIdByEmployeeAndMonth(4L, "2026-10")).thenReturn(7L);

        SalarySlipUploadResult result = salarySlipService.upload("sub-hr", 4L, "2026-10", false, pdf());

        //S3に、決まったキーで保存している
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("salary-slips/employee-4/2026/10.pdf");
        //アップロードした人は、仮の値ではなく、ログイン中のHR管理者(id=1)
        verify(salarySlipMapper).insert(4L, "2026-10", "salary-slips/employee-4/2026/10.pdf", 1L);
        verify(salarySlipMapper, never()).updateFilePath(anyLong(), anyString(), anyString(), anyLong());
        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getEmployeeId()).isEqualTo(4L);
        assertThat(result.getPayMonth()).isEqualTo("2026-10");
    }

    @Test
    void upload_overwritesExistingSlip_whenOverwriteIsTrue() throws Exception {
        loginAsHr();
        when(salarySlipMapper.countByEmployeeAndMonth(4L, "2026-10")).thenReturn(1);
        when(salarySlipMapper.findIdByEmployeeAndMonth(4L, "2026-10")).thenReturn(7L);

        salarySlipService.upload("sub-hr", 4L, "2026-10", true, pdf());

        verify(salarySlipMapper).updateFilePath(4L, "2026-10", "salary-slips/employee-4/2026/10.pdf", 1L);
        verify(salarySlipMapper, never()).insert(anyLong(), anyString(), anyString(), anyLong());
    }

    // ---------- listByEmployee (API009) ----------

    @Test
    void list_returnsOwnSlips_forMember() {
        loginAsMember();
        List<SalarySlipSummary> slips = List.of(new SalarySlipSummary());
        when(salarySlipMapper.selectByEmployeeId(4L)).thenReturn(slips);

        assertThat(salarySlipService.listByEmployee("sub-member", 4L)).isSameAs(slips);
    }

    @Test
    void list_throws403_whenMemberRequestsOthers_andDoesNotQuery() {
        loginAsMember();

        assertThatThrownBy(() -> salarySlipService.listByEmployee("sub-member", 1L))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(salarySlipMapper);
    }

    @Test
    void list_returnsOthersSlips_forHrAdmin() {
        loginAsHr();
        List<SalarySlipSummary> slips = List.of(new SalarySlipSummary());
        when(salarySlipMapper.selectByEmployeeId(4L)).thenReturn(slips);

        assertThat(salarySlipService.listByEmployee("sub-hr", 4L)).isSameAs(slips);
    }

    // ---------- download (API010) ----------

    @Test
    void download_throws404_whenSlipDoesNotExist() {
        loginAsMember();
        when(salarySlipMapper.findEmployeeIdById(99L)).thenReturn(null);

        assertThatThrownBy(() -> salarySlipService.download("sub-member", 99L))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    void download_throws403_whenSlipBelongsToSomeoneElse_andDoesNotReadS3() {
        loginAsMember();//id=4
        when(salarySlipMapper.findEmployeeIdById(5L)).thenReturn(1L);//持ち主は社員1

        assertThatThrownBy(() -> salarySlipService.download("sub-member", 5L))
                .isInstanceOf(ForbiddenException.class);
        verify(salarySlipMapper, never()).findFilePathById(anyLong());//ファイルの場所も調べない
        verifyNoInteractions(s3Client);//S3にも触れない
    }

    @Test
    void download_returnsPdfBytes_forOwner() {
        loginAsMember();//id=4
        when(salarySlipMapper.findEmployeeIdById(5L)).thenReturn(4L);
        when(salarySlipMapper.findFilePathById(5L)).thenReturn("salary-slips/employee-4/2026/10.pdf");
        byte[] bytes = "pdf-content".getBytes();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes));

        byte[] result = salarySlipService.download("sub-member", 5L);

        assertThat(result).isEqualTo(bytes);
        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("salary-slips/employee-4/2026/10.pdf");
    }

    @Test
    void download_returnsPdfBytes_forHrAdmin_evenIfNotOwner() {
        loginAsHr();//id=1
        when(salarySlipMapper.findEmployeeIdById(5L)).thenReturn(4L);
        when(salarySlipMapper.findFilePathById(5L)).thenReturn("salary-slips/employee-4/2026/10.pdf");
        byte[] bytes = "pdf-content".getBytes();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes));

        assertThat(salarySlipService.download("sub-hr", 5L)).isEqualTo(bytes);
    }

    @Test
    void download_throws404_whenFilePathIsMissing() {
        loginAsMember();
        when(salarySlipMapper.findEmployeeIdById(5L)).thenReturn(4L);
        when(salarySlipMapper.findFilePathById(5L)).thenReturn(null);

        assertThatThrownBy(() -> salarySlipService.download("sub-member", 5L))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(s3Client);
    }

    // ---------- delete (API012) ----------

    @Test
    void delete_throws403_whenNotHrAdmin_evenIfSlipDoesNotExist() {
        loginAsMember();

        assertThatThrownBy(() -> salarySlipService.delete("sub-member", 99L))
                .isInstanceOf(ForbiddenException.class);
        //権限チェックが先なので、DBも調べない(その明細が存在するかを教えない)
        verifyNoInteractions(salarySlipMapper);
        verifyNoInteractions(s3Client);
    }

    @Test
    void delete_throws404_whenSlipDoesNotExist() {
        loginAsHr();
        when(salarySlipMapper.findFilePathById(99L)).thenReturn(null);

        assertThatThrownBy(() -> salarySlipService.delete("sub-hr", 99L))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(s3Client);
        verify(salarySlipMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_removesS3ObjectThenDbRow() {
        loginAsHr();
        when(salarySlipMapper.findFilePathById(5L)).thenReturn("salary-slips/employee-4/2026/10.pdf");

        salarySlipService.delete("sub-hr", 5L);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        InOrder order = inOrder(s3Client, salarySlipMapper);
        order.verify(s3Client).deleteObject(captor.capture());//先にS3のファイルを消し
        order.verify(salarySlipMapper).deleteById(5L);//そのあとDBの行を消す
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("salary-slips/employee-4/2026/10.pdf");
    }

    // ---------- listHistory (API013) ----------

    @Test
    void listHistory_throws403_whenNotHrAdmin_andDoesNotQuery() {
        loginAsMember();

        assertThatThrownBy(() -> salarySlipService.listHistory("sub-member", 0, 20))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(salarySlipMapper);
    }

    @Test
    void listHistory_throws403_beforeValidatingParameters() {
        loginAsMember();

        //権限が無い人には、入力の不備(400)ではなく、先に403を返す
        assertThatThrownBy(() -> salarySlipService.listHistory("sub-member", -1, 0))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listHistory_throws400_whenPageIsNegative() {
        loginAsHr();

        assertThatThrownBy(() -> salarySlipService.listHistory("sub-hr", -1, 20))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(salarySlipMapper);
    }

    @Test
    void listHistory_throws400_whenSizeIsZero() {
        loginAsHr();

        assertThatThrownBy(() -> salarySlipService.listHistory("sub-hr", 0, 0))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void listHistory_throws400_whenSizeIsOver100() {
        loginAsHr();

        assertThatThrownBy(() -> salarySlipService.listHistory("sub-hr", 0, 101))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void listHistory_passesSizeAndOffsetToMapper() {
        loginAsHr();
        List<SalarySlipUploadHistory> history = List.of(new SalarySlipUploadHistory());
        when(salarySlipMapper.selectHistory(2, 4L)).thenReturn(history);

        //page=2,size=2 → 4件目から取る(offset=4)
        assertThat(salarySlipService.listHistory("sub-hr", 2, 2)).isSameAs(history);
    }
}
