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
}
