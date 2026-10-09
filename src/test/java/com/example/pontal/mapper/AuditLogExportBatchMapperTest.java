package com.example.pontal.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.pontal.config.MyBatisConfig;
import com.example.pontal.dto.AuditLogExportBatch;

//AuditLogExportBatchMapperのSQLのテスト。DockerでPostgreSQLを起動し、schema.sqlを流して実際にSQLを実行する
//バッチ管理表(audit_log_export_batch)は、最初は空。週の開始日(period_start)はUNIQUE
@MybatisTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
@Import(MyBatisConfig.class)
class AuditLogExportBatchMapperTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 11);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        //アプリ本体はSQLを自動実行しない設定なので、このテストでだけ、schema.sqlとdata.sqlを流す
        registry.add("spring.sql.init.mode", () -> "always");
    }

    @Autowired
    private AuditLogExportBatchMapper batchMapper;

    // ---------- insertProcessing / find ----------

    @Test
    void insertProcessing_createsProcessingRow_andReturnsId() {
        Long id = batchMapper.insertProcessing(MONDAY, SUNDAY);

        assertThat(id).isNotNull();
        AuditLogExportBatch batch = batchMapper.findById(id);
        assertThat(batch.getPeriodStart()).isEqualTo(MONDAY);
        assertThat(batch.getPeriodEnd()).isEqualTo(SUNDAY);
        assertThat(batch.getStatus()).isEqualTo("PROCESSING");//DEFAULTで処理中になる
        assertThat(batch.getRecordCount()).isNull();//まだ件数は分からない
        assertThat(batch.getFilePath()).isNull();
        assertThat(batch.getErrorMessage()).isNull();
        assertThat(batch.getCreatedAt()).isNotNull();
        assertThat(batch.getCompletedAt()).isNull();
    }

    @Test
    void insertProcessing_throwsDuplicateKey_whenSameWeekAlreadyExists() {
        batchMapper.insertProcessing(MONDAY, SUNDAY);

        //period_startのUNIQUE制約。サービスが「同時に作られた」と判断するのは、この例外
        assertThatThrownBy(() -> batchMapper.insertProcessing(MONDAY, SUNDAY))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void findByPeriodStart_returnsRow_orNullWhenNotFound() {
        Long id = batchMapper.insertProcessing(MONDAY, SUNDAY);

        assertThat(batchMapper.findByPeriodStart(MONDAY).getId()).isEqualTo(id);
        assertThat(batchMapper.findByPeriodStart(MONDAY.plusWeeks(1))).isNull();
    }

    @Test
    void findById_returnsNull_whenNotFound() {
        assertThat(batchMapper.findById(99999L)).isNull();
    }

    // ---------- markCompleted ----------

    @Test
    void markCompleted_setsStatusCountPathAndCompletedAt() {
        Long id = batchMapper.insertProcessing(MONDAY, SUNDAY);

        int updated = batchMapper.markCompleted(id, 12, "audit-logs/2026/10/2026-10-05_2026-10-11.txt");

        assertThat(updated).isEqualTo(1);
        AuditLogExportBatch batch = batchMapper.findById(id);
        assertThat(batch.getStatus()).isEqualTo("COMPLETED");
        assertThat(batch.getRecordCount()).isEqualTo(12);
        assertThat(batch.getFilePath()).isEqualTo("audit-logs/2026/10/2026-10-05_2026-10-11.txt");
        assertThat(batch.getCompletedAt()).isNotNull();
        assertThat(batch.getErrorMessage()).isNull();
    }

    @Test
    void markCompleted_acceptsZeroRecords() {
        Long id = batchMapper.insertProcessing(MONDAY, SUNDAY);

        batchMapper.markCompleted(id, 0, "audit-logs/2026/10/x.txt");

        assertThat(batchMapper.findById(id).getRecordCount()).isEqualTo(0);//0件の週は、NULLではなく0
    }

    @Test
    void markCompleted_returnsZero_whenNotFound() {
        assertThat(batchMapper.markCompleted(99999L, 1, "x")).isEqualTo(0);
    }

    // ---------- markFailed ----------

    @Test
    void markFailed_setsStatusAndErrorMessage_withoutCountOrPath() {
        Long id = batchMapper.insertProcessing(MONDAY, SUNDAY);

        int updated = batchMapper.markFailed(id, "RuntimeException: S3 error");

        assertThat(updated).isEqualTo(1);
        AuditLogExportBatch batch = batchMapper.findById(id);
        assertThat(batch.getStatus()).isEqualTo("FAILED");
        assertThat(batch.getErrorMessage()).isEqualTo("RuntimeException: S3 error");
        assertThat(batch.getCompletedAt()).isNotNull();
        assertThat(batch.getRecordCount()).isNull();
        assertThat(batch.getFilePath()).isNull();
    }

    // ---------- resetToProcessing(失敗した週のやり直し) ----------

    @Test
    void resetToProcessing_clearsErrorAndCompletedAt_andKeepsSameRow() {
        Long id = batchMapper.insertProcessing(MONDAY, SUNDAY);
        batchMapper.markFailed(id, "error");

        int updated = batchMapper.resetToProcessing(id);

        assertThat(updated).isEqualTo(1);
        AuditLogExportBatch batch = batchMapper.findById(id);
        assertThat(batch.getStatus()).isEqualTo("PROCESSING");
        assertThat(batch.getErrorMessage()).isNull();//前回の原因は消える
        assertThat(batch.getCompletedAt()).isNull();
        //同じ行のまま(週の開始日がUNIQUEなので、新しい行は作れない)
        assertThat(batchMapper.findByPeriodStart(MONDAY).getId()).isEqualTo(id);
    }

    @Test
    void retryAfterFailure_canBeCompleted() {
        //失敗 → やり直し → 完了、という流れ全体
        Long id = batchMapper.insertProcessing(MONDAY, SUNDAY);
        batchMapper.markFailed(id, "error");
        batchMapper.resetToProcessing(id);
        batchMapper.markCompleted(id, 3, "audit-logs/x.txt");

        AuditLogExportBatch batch = batchMapper.findById(id);
        assertThat(batch.getStatus()).isEqualTo("COMPLETED");
        assertThat(batch.getRecordCount()).isEqualTo(3);
        assertThat(batch.getErrorMessage()).isNull();
    }

    // ---------- selectByPeriodStartBetween(月ごとの一覧) ----------

    @Test
    void selectByPeriodStartBetween_returnsWeeksStartingInMonth_oldestFirst() {
        //記録した順とは違う並びで入れる
        batchMapper.insertProcessing(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25));
        batchMapper.insertProcessing(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4));//9月に始まる週
        batchMapper.insertProcessing(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11));
        batchMapper.insertProcessing(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8));//11月に始まる週
        batchMapper.insertProcessing(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18));
        batchMapper.insertProcessing(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1));//10月に始まって11月に終わる週

        //10月に属する週 = 週の開始日が、10/1以上、11/1未満
        List<AuditLogExportBatch> october = batchMapper.selectByPeriodStartBetween(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1));

        assertThat(october).extracting(AuditLogExportBatch::getPeriodStart).containsExactly(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26));//10月は月曜が4回で、4件
    }

    @Test
    void selectByPeriodStartBetween_returnsEmptyList_whenNoWeeksInMonth() {
        assertThat(batchMapper.selectByPeriodStartBetween(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1))).isEmpty();
    }
}
