package com.example.pontal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.pontal.dto.AuditLogBatchFile;
import com.example.pontal.dto.AuditLogBatchSummary;
import com.example.pontal.dto.AuditLogExportBatch;
import com.example.pontal.dto.LoginEmployee;
import com.example.pontal.enums.AuditAction;
import com.example.pontal.enums.AuditResourceType;
import com.example.pontal.exception.ConflictException;
import com.example.pontal.exception.ExternalServiceException;
import com.example.pontal.exception.ForbiddenException;
import com.example.pontal.exception.NotFoundException;
import com.example.pontal.exception.UnauthorizedException;
import com.example.pontal.exception.ValidationException;
import com.example.pontal.mapper.AuditLogExportBatchMapper;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

//AuditLogBatchServiceの単体テスト。Mapper・EmployeeService・S3・監査ログはモックに差し替え、DBやAWSには繋がない
@ExtendWith(MockitoExtension.class)
class AuditLogBatchServiceTest {

    @Mock
    private AuditLogExportBatchMapper batchMapper;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private AuditLogBatchService batchService;

    @BeforeEach
    void setUp() {
        //@Valueで入る値は、モックのテストでは入らないので、直接入れる
        ReflectionTestUtils.setField(batchService, "bucketName", "test-bucket");
    }

    private LoginEmployee login(Long id, boolean isSystemAdmin, boolean isHrAdmin) {
        LoginEmployee e = new LoginEmployee();
        e.setEmployeeId(id);
        e.setName("テスト");
        e.setIsSystemAdmin(isSystemAdmin);
        e.setIsHrAdmin(isHrAdmin);
        return e;
    }

    private void loginAsSystemAdmin() {
        when(employeeService.getLoginEmployee("sub-admin")).thenReturn(login(1L, true, false));
    }

    //HR管理者(システム管理者ではない)
    private void loginAsHrOnly() {
        when(employeeService.getLoginEmployee("sub-hr")).thenReturn(login(2L, false, true));
    }

    private AuditLogExportBatch batch(Long id, LocalDate start, String status, Integer count) {
        AuditLogExportBatch b = new AuditLogExportBatch();
        b.setId(id);
        b.setPeriodStart(start);
        b.setPeriodEnd(start.plusDays(6));
        b.setStatus(status);
        b.setRecordCount(count);
        b.setFilePath("audit-logs/2026/10/secret-path.csv");//返さない項目
        b.setErrorMessage("内部のエラー内容");//返さない項目
        return b;
    }

    // ---------- 正常系 ----------

    @Test
    void list_returnsSummaries_inTheOrderFromMapper() {
        loginAsSystemAdmin();
        when(batchMapper.selectByPeriodStartBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
                .thenReturn(List.of(
                        batch(1L, LocalDate.of(2026, 10, 5), "COMPLETED", 3),
                        batch(2L, LocalDate.of(2026, 10, 12), "FAILED", null)));

        List<AuditLogBatchSummary> result = batchService.list("sub-admin", 2026, 10);

        assertThat(result).hasSize(2);
        AuditLogBatchSummary first = result.get(0);
        assertThat(first.getBatchId()).isEqualTo(1L);//DBのidが、batchIdとして返る
        assertThat(first.getPeriodStart()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(first.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(first.getStatus()).isEqualTo("COMPLETED");
        assertThat(first.getRecordCount()).isEqualTo(3);
        //失敗した週は、件数がnullのまま
        assertThat(result.get(1).getStatus()).isEqualTo("FAILED");
        assertThat(result.get(1).getRecordCount()).isNull();
    }

    @Test
    void list_passesFirstDayOfMonthAndFirstDayOfNextMonthToMapper() {
        loginAsSystemAdmin();
        when(batchMapper.selectByPeriodStartBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
                .thenReturn(List.of());

        batchService.list("sub-admin", 2026, 10);

        //10月 = 10/1以上、11/1未満(週の開始日で絞る)
        verify(batchMapper).selectByPeriodStartBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1));
    }

    @Test
    void list_december_endsAtJanuaryFirstOfNextYear() {
        loginAsSystemAdmin();
        when(batchMapper.selectByPeriodStartBetween(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1)))
                .thenReturn(List.of());

        batchService.list("sub-admin", 2026, 12);

        //12月の次は、翌年の1月
        verify(batchMapper).selectByPeriodStartBetween(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1));
    }

    @Test
    void list_returnsEmptyList_whenNoBatchesInMonth() {
        loginAsSystemAdmin();
        when(batchMapper.selectByPeriodStartBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
                .thenReturn(List.of());

        assertThat(batchService.list("sub-admin", 2026, 10)).isEmpty();
    }

    // ---------- 権限 ----------

    @Test
    void list_throwsForbidden_whenHrAdminButNotSystemAdmin() {
        loginAsHrOnly();

        assertThatThrownBy(() -> batchService.list("sub-hr", 2026, 10))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(batchMapper);//DBには触らない
    }

    @Test
    void list_checksPermissionBeforeValidation() {
        loginAsHrOnly();

        //権限がない人には、入力の誤り(400)ではなく、先に403を返す
        assertThatThrownBy(() -> batchService.list("sub-hr", 2026, 13))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(batchMapper);
    }

    @Test
    void list_propagatesUnauthorized_whenLoginEmployeeNotFound() {
        when(employeeService.getLoginEmployee("sub-unknown")).thenThrow(new UnauthorizedException("未登録"));

        assertThatThrownBy(() -> batchService.list("sub-unknown", 2026, 10))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(batchMapper);
    }

    // ---------- 入力チェック ----------

    @ParameterizedTest
    @ValueSource(ints = { 0, 13, -1, 100 })
    void list_throwsValidation_whenMonthOutOfRange(int month) {
        loginAsSystemAdmin();

        assertThatThrownBy(() -> batchService.list("sub-admin", 2026, month))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(batchMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 999, 10000, -2026 })
    void list_throwsValidation_whenYearIsNotFourDigits(int year) {
        loginAsSystemAdmin();

        assertThatThrownBy(() -> batchService.list("sub-admin", year, 10))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(batchMapper);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 12 })
    void list_acceptsBoundaryMonths(int month) {
        loginAsSystemAdmin();
        when(batchMapper.selectByPeriodStartBetween(any(), any()))
                .thenReturn(List.of());

        assertThat(batchService.list("sub-admin", 2026, month)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = { 1000, 9999 })
    void list_acceptsBoundaryYears(int year) {
        loginAsSystemAdmin();
        when(batchMapper.selectByPeriodStartBetween(any(), any()))
                .thenReturn(List.of());

        assertThat(batchService.list("sub-admin", year, 6)).isEmpty();
    }

    // ---------- download(正常系) ----------

    //S3から、指定した中身が返ってくる状態にする
    private void s3Returns(byte[] bytes) {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes));
    }

    @Test
    void download_returnsFileNameAndContent() {
        loginAsSystemAdmin();
        when(batchMapper.findById(4L)).thenReturn(batch(4L, LocalDate.of(2026, 10, 5), "COMPLETED", 3));
        byte[] csv = "csv-content".getBytes();
        s3Returns(csv);

        AuditLogBatchFile file = batchService.download("sub-admin", 4L);

        assertThat(file.fileName()).isEqualTo("audit-log_2026-10-05_2026-10-11.csv");
        assertThat(file.content()).isEqualTo(csv);
    }

    @Test
    void download_readsTheFileRecordedInDbFromTheConfiguredBucket() {
        loginAsSystemAdmin();
        when(batchMapper.findById(4L)).thenReturn(batch(4L, LocalDate.of(2026, 10, 5), "COMPLETED", 3));
        s3Returns("x".getBytes());

        batchService.download("sub-admin", 4L);

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("audit-logs/2026/10/secret-path.csv");//DBのfile_path
    }

    @Test
    void download_recordsAuditLog_withLoginEmployeeAsBothActorAndTarget() {
        loginAsSystemAdmin();
        when(batchMapper.findById(4L)).thenReturn(batch(4L, LocalDate.of(2026, 10, 5), "COMPLETED", 3));
        s3Returns("x".getBytes());

        batchService.download("sub-admin", 4L);

        //対象の社員はいないので、操作者と対象は同じ(ログイン中の社員=1)。リソースIDはバッチのID
        verify(auditLogService).record(1L, 1L, AuditResourceType.AUDIT_LOG_EXPORT, 4L, AuditAction.DOWNLOAD);
    }

    @Test
    void download_recordsAuditLogAfterReadingS3() {
        loginAsSystemAdmin();
        when(batchMapper.findById(4L)).thenReturn(batch(4L, LocalDate.of(2026, 10, 5), "COMPLETED", 3));
        s3Returns("x".getBytes());

        batchService.download("sub-admin", 4L);

        //取得に成功してから記録する(順番が逆だと、取得に失敗しても、ダウンロードしたと記録されてしまう)
        InOrder order = Mockito.inOrder(s3Client, auditLogService);
        order.verify(s3Client).getObjectAsBytes(any(GetObjectRequest.class));
        order.verify(auditLogService).record(any(), any(), any(), any(), any());
    }

    // ---------- download(権限・存在・状態) ----------

    @Test
    void download_throwsForbidden_whenHrAdminButNotSystemAdmin() {
        loginAsHrOnly();

        assertThatThrownBy(() -> batchService.download("sub-hr", 4L))
                .isInstanceOf(ForbiddenException.class);
        //権限がない人には、バッチがあるかどうかも調べない
        verifyNoInteractions(batchMapper, s3Client, auditLogService);
    }

    @Test
    void download_propagatesUnauthorized_whenLoginEmployeeNotFound() {
        when(employeeService.getLoginEmployee("sub-unknown")).thenThrow(new UnauthorizedException("未登録"));

        assertThatThrownBy(() -> batchService.download("sub-unknown", 4L))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(batchMapper, s3Client, auditLogService);
    }

    @Test
    void download_throwsNotFound_whenBatchDoesNotExist() {
        loginAsSystemAdmin();
        when(batchMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> batchService.download("sub-admin", 99L))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(s3Client, auditLogService);//S3にも行かず、記録もしない
    }

    @ParameterizedTest
    @ValueSource(strings = { "PROCESSING", "FAILED" })
    void download_throwsConflict_whenNotCompleted(String status) {
        loginAsSystemAdmin();
        when(batchMapper.findById(4L)).thenReturn(batch(4L, LocalDate.of(2026, 10, 5), status, null));

        assertThatThrownBy(() -> batchService.download("sub-admin", 4L))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(s3Client, auditLogService);
    }

    // ---------- download(S3から取れないとき) ----------

    @Test
    void download_throwsExternalService_andDoesNotRecord_whenFileIsMissingInS3() {
        loginAsSystemAdmin();
        when(batchMapper.findById(4L)).thenReturn(batch(4L, LocalDate.of(2026, 10, 5), "COMPLETED", 3));
        //DBは完了なのに、S3にファイルがない(記録とファイルの食い違い)
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

        assertThatThrownBy(() -> batchService.download("sub-admin", 4L))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("監査ログのファイルを取得できませんでした");
        verifyNoInteractions(auditLogService);//失敗したダウンロードは記録しない
    }

    @Test
    void download_throwsExternalService_andDoesNotRecord_whenS3IsUnreachable() {
        loginAsSystemAdmin();
        when(batchMapper.findById(4L)).thenReturn(batch(4L, LocalDate.of(2026, 10, 5), "COMPLETED", 3));
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(SdkClientException.create("接続できません"));

        assertThatThrownBy(() -> batchService.download("sub-admin", 4L))
                .isInstanceOf(ExternalServiceException.class);
        verifyNoInteractions(auditLogService);
    }
}
