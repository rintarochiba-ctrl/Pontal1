package com.example.pontal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.pontal.dto.AuditLogExportBatch;
import com.example.pontal.dto.AuditLogExportRow;
import com.example.pontal.mapper.AuditLogExportBatchMapper;
import com.example.pontal.mapper.AuditLogMapper;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

//AuditLogExportServiceの単体テスト。Mapper・S3はモックに差し替え、DBやAWSには繋がない
@ExtendWith(MockitoExtension.class)
class AuditLogExportServiceTest {

    //2026-10-05は月曜日。この週は 10/5(月)〜10/11(日)
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 11);

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private AuditLogExportBatchMapper batchMapper;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private AuditLogExportService exportService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(exportService, "bucketName", "test-bucket");
    }

    private AuditLogExportBatch batch(Long id, String status) {
        AuditLogExportBatch b = new AuditLogExportBatch();
        b.setId(id);
        b.setPeriodStart(MONDAY);
        b.setPeriodEnd(SUNDAY);
        b.setStatus(status);
        return b;
    }

    private AuditLogExportRow row() {
        AuditLogExportRow r = new AuditLogExportRow();
        r.setOccurredAt(LocalDateTime.of(2026, 10, 6, 10, 0, 0));
        r.setActorEmployeeId(1L);
        r.setActorName("山田太郎");
        r.setTargetEmployeeId(4L);
        r.setTargetName("権限なしテスト");
        r.setResourceType("SALARY_SLIP");
        r.setResourceId(5L);
        r.setAction("DOWNLOAD");
        return r;
    }

    // ---------- 入力のチェック ----------

    @Test
    void exportWeek_throwsIllegalArgument_whenNotMonday() {
        LocalDate tuesday = MONDAY.plusDays(1);

        assertThatThrownBy(() -> exportService.exportWeek(tuesday))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(batchMapper, auditLogMapper, s3Client);//何も始めない
    }

    // ---------- 新しい週(成功) ----------

    @Test
    void exportWeek_newWeek_savesCsvToS3AndMarksCompleted() {
        when(batchMapper.findByPeriodStart(MONDAY)).thenReturn(null);
        when(batchMapper.insertProcessing(MONDAY, SUNDAY)).thenReturn(7L);
        when(auditLogMapper.selectForExport(any(), any())).thenReturn(List.of(row(), row()));

        exportService.exportWeek(MONDAY);

        //ログを集める期間: 月曜0時以上、翌週の月曜0時未満
        verify(auditLogMapper).selectForExport(LocalDateTime.of(2026, 10, 5, 0, 0), LocalDateTime.of(2026, 10, 12, 0, 0));
        //S3に、週の開始日の年月のフォルダで、保存する
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("audit-logs/2026/10/2026-10-05_2026-10-11.csv");
        //CSVとして保存する(ダウンロードしたときに、ブラウザがCSVだと分かるように)
        assertThat(captor.getValue().contentType()).isEqualTo("text/csv; charset=utf-8");
        //完了にして、件数とS3のパスを記録する
        verify(batchMapper).markCompleted(7L, 2, "audit-logs/2026/10/2026-10-05_2026-10-11.csv");
        verify(batchMapper, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void exportWeek_savesToS3BeforeMarkingCompleted() {
        when(batchMapper.findByPeriodStart(MONDAY)).thenReturn(null);
        when(batchMapper.insertProcessing(MONDAY, SUNDAY)).thenReturn(7L);
        when(auditLogMapper.selectForExport(any(), any())).thenReturn(List.of());

        exportService.exportWeek(MONDAY);

        //S3に保存できてから完了にする(順番が逆だと、保存に失敗しても完了と記録されてしまう)
        InOrder order = Mockito.inOrder(s3Client, batchMapper);
        order.verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        order.verify(batchMapper).markCompleted(anyLong(), anyInt(), anyString());
    }

    @Test
    void exportWeek_weekWithNoLogs_stillCreatesFileAndCompletesWithZero() {
        when(batchMapper.findByPeriodStart(MONDAY)).thenReturn(null);
        when(batchMapper.insertProcessing(MONDAY, SUNDAY)).thenReturn(7L);
        when(auditLogMapper.selectForExport(any(), any())).thenReturn(List.of());

        exportService.exportWeek(MONDAY);

        //ログが0件の週も、ファイルを作り、件数0で完了にする
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(batchMapper).markCompleted(7L, 0, "audit-logs/2026/10/2026-10-05_2026-10-11.csv");
    }

    @Test
    void exportWeek_weekStartingInPreviousMonth_usesStartDateMonthInKey() {
        //2026-09-28(月)〜10-04(日)の週は、開始日の月(9月)のフォルダに入る
        LocalDate monday = LocalDate.of(2026, 9, 28);
        when(batchMapper.findByPeriodStart(monday)).thenReturn(null);
        when(batchMapper.insertProcessing(monday, LocalDate.of(2026, 10, 4))).thenReturn(3L);
        when(auditLogMapper.selectForExport(any(), any())).thenReturn(List.of());

        exportService.exportWeek(monday);

        verify(batchMapper).markCompleted(3L, 0, "audit-logs/2026/09/2026-09-28_2026-10-04.csv");
    }

    // ---------- すでにある週 ----------

    @Test
    void exportWeek_doesNothing_whenAlreadyCompleted() {
        when(batchMapper.findByPeriodStart(MONDAY)).thenReturn(batch(7L, "COMPLETED"));

        exportService.exportWeek(MONDAY);

        verify(batchMapper, never()).insertProcessing(any(), any());
        verify(batchMapper, never()).resetToProcessing(anyLong());
        verifyNoInteractions(auditLogMapper, s3Client);//完了済みの週は、もう一度作り直さない
    }

    @Test
    void exportWeek_retriesWithSameRow_whenPreviousRunFailed() {
        when(batchMapper.findByPeriodStart(MONDAY)).thenReturn(batch(7L, "FAILED"));
        when(auditLogMapper.selectForExport(any(), any())).thenReturn(List.of(row()));

        exportService.exportWeek(MONDAY);

        verify(batchMapper).resetToProcessing(7L);//同じ行を処理中に戻す
        verify(batchMapper, never()).insertProcessing(any(), any());//新しい行は作らない
        verify(batchMapper).markCompleted(7L, 1, "audit-logs/2026/10/2026-10-05_2026-10-11.csv");
    }

    @Test
    void exportWeek_retriesWithSameRow_whenPreviousRunWasInterrupted() {
        //途中でアプリが止まって、PROCESSINGのまま残っていた場合
        when(batchMapper.findByPeriodStart(MONDAY)).thenReturn(batch(7L, "PROCESSING"));
        when(auditLogMapper.selectForExport(any(), any())).thenReturn(List.of());

        exportService.exportWeek(MONDAY);

        verify(batchMapper).resetToProcessing(7L);
        verify(batchMapper).markCompleted(7L, 0, "audit-logs/2026/10/2026-10-05_2026-10-11.csv");
    }

    @Test
    void exportWeek_doesNothing_whenAnotherRunCreatedTheRowFirst() {
        when(batchMapper.findByPeriodStart(MONDAY)).thenReturn(null);
        //同時に動いた別の実行が、先に同じ週の行を作った場合(UNIQUE違反)
        when(batchMapper.insertProcessing(MONDAY, SUNDAY)).thenThrow(new DuplicateKeyException("duplicate"));

        exportService.exportWeek(MONDAY);

        verifyNoInteractions(auditLogMapper, s3Client);
    }

    // ---------- 失敗 ----------

    @Test
    void exportWeek_marksFailedAndRethrows_whenS3SaveFails() {
        when(batchMapper.findByPeriodStart(MONDAY)).thenReturn(null);
        when(batchMapper.insertProcessing(MONDAY, SUNDAY)).thenReturn(7L);
        when(auditLogMapper.selectForExport(any(), any())).thenReturn(List.of(row()));
        doThrow(new RuntimeException("S3 error")).when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        assertThatThrownBy(() -> exportService.exportWeek(MONDAY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("S3 error");//元のエラーが、そのまま外に出る
        //失敗にして、原因を記録する。完了にはしない
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(batchMapper).markFailed(Mockito.eq(7L), message.capture());
        assertThat(message.getValue()).contains("S3 error");
        verify(batchMapper, never()).markCompleted(anyLong(), anyInt(), anyString());
    }

    @Test
    void exportWeek_marksFailed_whenCollectingLogsFails() {
        when(batchMapper.findByPeriodStart(MONDAY)).thenReturn(null);
        when(batchMapper.insertProcessing(MONDAY, SUNDAY)).thenReturn(7L);
        when(auditLogMapper.selectForExport(any(), any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> exportService.exportWeek(MONDAY)).hasMessage("DB error");
        verify(batchMapper).markFailed(Mockito.eq(7L), anyString());
        verifyNoInteractions(s3Client);//ログが集められなかったら、S3には何も保存しない
    }

    @Test
    void exportWeek_keepsOriginalError_whenMarkingFailedAlsoFails() {
        when(batchMapper.findByPeriodStart(MONDAY)).thenReturn(null);
        when(batchMapper.insertProcessing(MONDAY, SUNDAY)).thenReturn(7L);
        when(auditLogMapper.selectForExport(any(), any())).thenThrow(new RuntimeException("DB error"));
        when(batchMapper.markFailed(anyLong(), anyString())).thenThrow(new RuntimeException("mark error"));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> exportService.exportWeek(MONDAY));

        //外に出るのは、もともとのエラー。記録の失敗は、付け足されて残る
        assertThat(thrown).hasMessage("DB error");
        assertThat(thrown.getSuppressed()).hasSize(1);
    }

    @Test
    void exportWeek_truncatesLongErrorMessageTo500Characters() {
        when(batchMapper.findByPeriodStart(MONDAY)).thenReturn(null);
        when(batchMapper.insertProcessing(MONDAY, SUNDAY)).thenReturn(7L);
        when(auditLogMapper.selectForExport(any(), any())).thenThrow(new RuntimeException("x".repeat(1000)));

        assertThatThrownBy(() -> exportService.exportWeek(MONDAY)).isInstanceOf(RuntimeException.class);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(batchMapper).markFailed(Mockito.eq(7L), message.capture());
        assertThat(message.getValue()).hasSize(500);//管理表の列(VARCHAR(500))に入る長さ
    }
}
