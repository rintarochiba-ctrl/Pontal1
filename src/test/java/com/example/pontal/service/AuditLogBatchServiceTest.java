package com.example.pontal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.pontal.dto.AuditLogBatchSummary;
import com.example.pontal.dto.AuditLogExportBatch;
import com.example.pontal.dto.LoginEmployee;
import com.example.pontal.exception.ForbiddenException;
import com.example.pontal.exception.UnauthorizedException;
import com.example.pontal.exception.ValidationException;
import com.example.pontal.mapper.AuditLogExportBatchMapper;

//AuditLogBatchServiceの単体テスト。Mapper・EmployeeServiceはモックに差し替え、DBには繋がない
@ExtendWith(MockitoExtension.class)
class AuditLogBatchServiceTest {

    @Mock
    private AuditLogExportBatchMapper batchMapper;

    @Mock
    private EmployeeService employeeService;

    @InjectMocks
    private AuditLogBatchService batchService;

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
}
