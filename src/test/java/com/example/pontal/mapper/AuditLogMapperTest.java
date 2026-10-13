package com.example.pontal.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.pontal.config.MyBatisConfig;
import com.example.pontal.dto.AuditLogExportRow;
import com.example.pontal.enums.AuditAction;
import com.example.pontal.enums.AuditResourceType;

//AuditLogMapperのSQLのテスト。DockerでPostgreSQLを起動し、schema.sql/data.sqlを流して実際にSQLを実行する
//前提の初期データ(data.sql): 社員は id=1 山田 / 2 佐藤 / 3 鈴木 / 4 権限なしテスト
//Mapperには「記録する」SQLしかないため、入った内容の確認はJdbcTemplateでaudit_logを直接読んで行う
@MybatisTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
@Import(MyBatisConfig.class)
class AuditLogMapperTest {

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
    private AuditLogMapper auditLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<Map<String, Object>> allLogs() {
        return jdbcTemplate.queryForList("SELECT * FROM audit_log ORDER BY id");
    }

    @Test
    void insert_savesAllColumns_andEnumsAreStoredAsNames() {
        auditLogMapper.insert(1L, 4L, AuditResourceType.SALARY_SLIP, 5L, AuditAction.DOWNLOAD);

        List<Map<String, Object>> logs = allLogs();
        assertThat(logs).hasSize(1);
        Map<String, Object> log = logs.get(0);
        assertThat(log.get("actor_employee_id")).isEqualTo(1L);
        assertThat(log.get("target_employee_id")).isEqualTo(4L);
        assertThat(log.get("resource_type")).isEqualTo("SALARY_SLIP");//enumは名前の文字列で保存される
        assertThat(log.get("resource_id")).isEqualTo(5L);
        assertThat(log.get("action")).isEqualTo("DOWNLOAD");
    }

    @Test
    void insert_setsIdAutomatically_andOccurredAtToNow() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(1);

        auditLogMapper.insert(1L, 4L, AuditResourceType.SALARY_SLIP, 5L, AuditAction.CREATE);
        auditLogMapper.insert(1L, 4L, AuditResourceType.SALARY_SLIP, 6L, AuditAction.DELETE);

        List<Map<String, Object>> logs = allLogs();
        assertThat(logs).hasSize(2);
        //idは自動採番で、後から入れた行ほど大きい
        assertThat((Long) logs.get(1).get("id")).isGreaterThan((Long) logs.get(0).get("id"));
        //occurred_atは、INSERTに書かなくてもDBが現在日時を入れる(NULLではなく、ほぼ今の時刻)
        LocalDateTime occurredAt = ((Timestamp) logs.get(0).get("occurred_at")).toLocalDateTime();
        assertThat(occurredAt).isAfter(before);
        assertThat(occurredAt).isBefore(LocalDateTime.now().plusMinutes(1));
    }

    @Test
    void insert_acceptsAllActionsAndResourceTypes() {
        auditLogMapper.insert(1L, 4L, AuditResourceType.SALARY_SLIP, 1L, AuditAction.DOWNLOAD);
        auditLogMapper.insert(1L, 4L, AuditResourceType.SALARY_SLIP, 1L, AuditAction.CREATE);
        auditLogMapper.insert(1L, 4L, AuditResourceType.SALARY_SLIP, 1L, AuditAction.DELETE);
        auditLogMapper.insert(1L, 1L, AuditResourceType.AUDIT_LOG_EXPORT, 1L, AuditAction.DOWNLOAD);

        //DBのCHECK制約(許可された値だけ)に、enumの全ての値が合っていることの確認
        assertThat(allLogs()).hasSize(4);
    }

    @Test
    void insert_allowsResourceIdOfDeletedRow() {
        //resource_idは外部キーのない「緩い参照」。存在しない(削除済みの)明細のIDでも記録できる
        auditLogMapper.insert(1L, 4L, AuditResourceType.SALARY_SLIP, 99999L, AuditAction.DELETE);

        assertThat(allLogs()).hasSize(1);
    }

    @Test
    void insert_fails_whenActorDoesNotExist() {
        //actor_employee_idはemployeeへの外部キー
        assertThatThrownBy(() -> auditLogMapper.insert(999L, 4L, AuditResourceType.SALARY_SLIP, 5L, AuditAction.DOWNLOAD))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insert_fails_whenTargetDoesNotExist() {
        //target_employee_idもemployeeへの外部キー
        assertThatThrownBy(() -> auditLogMapper.insert(1L, 999L, AuditResourceType.SALARY_SLIP, 5L, AuditAction.DOWNLOAD))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insert_fails_whenRequiredValueIsNull() {
        //resource_idはNOT NULL
        assertThatThrownBy(() -> auditLogMapper.insert(1L, 4L, AuditResourceType.SALARY_SLIP, null, AuditAction.DOWNLOAD))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- selectForExport (週次エクスポート) ----------

    //ログを1件記録して、発生日時を、指定した値にする(INSERTだけだと、今の日時になってしまうため)
    private void insertLogAt(Long actorId, Long targetId, Long resourceId, LocalDateTime at) {
        auditLogMapper.insert(actorId, targetId, AuditResourceType.SALARY_SLIP, resourceId, AuditAction.DOWNLOAD);
        Long id = jdbcTemplate.queryForObject("SELECT max(id) FROM audit_log", Long.class);
        jdbcTemplate.update("UPDATE audit_log SET occurred_at = ? WHERE id = ?", at, id);
    }

    @Test
    void selectForExport_includesStartInclusive_andExcludesEndExclusive() {
        //集める期間は、2026-10-05 00:00 以上、2026-10-12 00:00 未満(1週間)
        insertLogAt(1L, 4L, 1L, LocalDateTime.of(2026, 10, 4, 23, 59, 59));//前の週 → 含まれない
        insertLogAt(1L, 4L, 2L, LocalDateTime.of(2026, 10, 5, 0, 0, 0));//月曜0時ちょうど → 含まれる
        insertLogAt(1L, 4L, 3L, LocalDateTime.of(2026, 10, 11, 23, 59, 59));//日曜の最後 → 含まれる
        insertLogAt(1L, 4L, 4L, LocalDateTime.of(2026, 10, 12, 0, 0, 0));//翌週の月曜0時ちょうど → 含まれない

        List<AuditLogExportRow> rows = auditLogMapper.selectForExport(
                LocalDateTime.of(2026, 10, 5, 0, 0), LocalDateTime.of(2026, 10, 12, 0, 0));

        assertThat(rows).extracting(AuditLogExportRow::getResourceId).containsExactly(2L, 3L);
    }

    @Test
    void selectForExport_ordersByOccurredAtOldestFirst() {
        //記録した順とは違う並びで入れて、発生日時の古い順に並ぶことを確認する
        insertLogAt(1L, 4L, 30L, LocalDateTime.of(2026, 10, 9, 12, 0, 0));
        insertLogAt(1L, 4L, 10L, LocalDateTime.of(2026, 10, 6, 9, 0, 0));
        insertLogAt(1L, 4L, 20L, LocalDateTime.of(2026, 10, 7, 15, 30, 0));

        List<AuditLogExportRow> rows = auditLogMapper.selectForExport(
                LocalDateTime.of(2026, 10, 5, 0, 0), LocalDateTime.of(2026, 10, 12, 0, 0));

        assertThat(rows).extracting(AuditLogExportRow::getResourceId).containsExactly(10L, 20L, 30L);
    }

    @Test
    void selectForExport_returnsNamesAndAllColumns() {
        insertLogAt(2L, 4L, 8L, LocalDateTime.of(2026, 10, 6, 10, 0, 0));

        AuditLogExportRow row = auditLogMapper.selectForExport(
                LocalDateTime.of(2026, 10, 5, 0, 0), LocalDateTime.of(2026, 10, 12, 0, 0)).get(0);

        assertThat(row.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 10, 6, 10, 0, 0));
        assertThat(row.getActorEmployeeId()).isEqualTo(2L);
        assertThat(row.getActorName()).isEqualTo("佐藤花子");//操作者の名前
        assertThat(row.getTargetEmployeeId()).isEqualTo(4L);
        assertThat(row.getTargetName()).isEqualTo("権限なしテスト");//対象社員の名前
        assertThat(row.getResourceType()).isEqualTo("SALARY_SLIP");
        assertThat(row.getResourceId()).isEqualTo(8L);
        assertThat(row.getAction()).isEqualTo("DOWNLOAD");
    }

    @Test
    void selectForExport_stillReturnsNames_forSoftDeletedEmployees() {
        insertLogAt(1L, 4L, 8L, LocalDateTime.of(2026, 10, 6, 10, 0, 0));
        //社員を論理削除(is_delete=true)しても、行は残るので、過去のログの名前は取れる
        jdbcTemplate.update("UPDATE employee SET is_delete = TRUE WHERE id = 4");

        List<AuditLogExportRow> rows = auditLogMapper.selectForExport(
                LocalDateTime.of(2026, 10, 5, 0, 0), LocalDateTime.of(2026, 10, 12, 0, 0));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTargetName()).isEqualTo("権限なしテスト");
    }

    @Test
    void selectForExport_returnsEmptyList_whenNoLogsInPeriod() {
        insertLogAt(1L, 4L, 1L, LocalDateTime.of(2026, 9, 1, 0, 0, 0));

        assertThat(auditLogMapper.selectForExport(
                LocalDateTime.of(2026, 10, 5, 0, 0), LocalDateTime.of(2026, 10, 12, 0, 0))).isEmpty();
    }
}
