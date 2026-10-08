package com.example.pontal.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

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
import com.example.pontal.dto.SalarySlipSummary;
import com.example.pontal.dto.SalarySlipUploadHistory;

//SalarySlipMapperのSQLのテスト。DockerでPostgreSQLを起動し、schema.sql/data.sqlを流して実際にSQLを実行する
//前提の初期データ(data.sql): 社員は id=1 山田 / 2 佐藤 / 3 鈴木 / 4 権限なしテスト(給与明細は0件)
//@MybatisTestはテストごとにトランザクションを張り、終了時にロールバックするので、テスト同士は影響しない
@MybatisTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
@Import(MyBatisConfig.class)
class SalarySlipMapperTest {

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
    private SalarySlipMapper salarySlipMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;//アップロード日時を、テストで決めた値にするために使う

    //明細を1件登録して、そのidを返す(アップロード者は山田=1)
    private Long insertSlip(Long employeeId, String payMonth) {
        salarySlipMapper.insert(employeeId, payMonth,
                "salary-slips/employee-" + employeeId + "/" + payMonth.replace("-", "/") + ".pdf", 1L);
        return salarySlipMapper.findIdByEmployeeAndMonth(employeeId, payMonth);
    }

    //アップロード日時を、指定した値に書き換える
    //(同じトランザクション内ではnow()が同じ値になるため、並び順のテストでは日時を明示する)
    private void setUploadedAt(Long slipId, LocalDateTime at) {
        jdbcTemplate.update("UPDATE salary_slip SET uploaded_at = ? WHERE id = ?", at, slipId);
    }

    // ---------- insert / findIdByEmployeeAndMonth / countByEmployeeAndMonth ----------

    @Test
    void insert_savesSlip_andCanBeFoundByEmployeeAndMonth() {
        assertThat(salarySlipMapper.countByEmployeeAndMonth(4L, "2026-10")).isEqualTo(0);

        Long id = insertSlip(4L, "2026-10");

        assertThat(id).isNotNull();
        assertThat(salarySlipMapper.countByEmployeeAndMonth(4L, "2026-10")).isEqualTo(1);
        assertThat(salarySlipMapper.countByEmployeeAndMonth(4L, "2026-09")).isEqualTo(0);//別の月は数えない
        assertThat(salarySlipMapper.countByEmployeeAndMonth(1L, "2026-10")).isEqualTo(0);//別の社員は数えない
    }

    @Test
    void insert_fails_whenSameEmployeeAndMonthAlreadyExists() {
        insertSlip(4L, "2026-10");

        //(employee_id, pay_month)のUNIQUE制約
        assertThatThrownBy(() -> salarySlipMapper.insert(4L, "2026-10", "other.pdf", 1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insert_fails_whenEmployeeDoesNotExist() {
        //employee_idの外部キー制約
        assertThatThrownBy(() -> salarySlipMapper.insert(999L, "2026-10", "x.pdf", 1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- selectByEmployeeId ----------

    @Test
    void selectByEmployeeId_returnsOnlyThatEmployee_newestMonthFirst() {
        insertSlip(4L, "2026-08");
        insertSlip(4L, "2026-10");
        insertSlip(4L, "2026-09");
        insertSlip(1L, "2026-10");//別の社員の明細は含まれない

        List<SalarySlipSummary> list = salarySlipMapper.selectByEmployeeId(4L);

        assertThat(list).extracting(SalarySlipSummary::getPayMonth)
                .containsExactly("2026-10", "2026-09", "2026-08");
        assertThat(list.get(0).getId()).isNotNull();
    }

    @Test
    void selectByEmployeeId_returnsEmptyList_whenNoSlips() {
        assertThat(salarySlipMapper.selectByEmployeeId(4L)).isEmpty();
    }

    // ---------- findFilePathById / findEmployeeIdById ----------

    @Test
    void findFilePathById_returnsPath_orNullWhenNotFound() {
        Long id = insertSlip(4L, "2026-10");

        assertThat(salarySlipMapper.findFilePathById(id)).isEqualTo("salary-slips/employee-4/2026/10.pdf");
        assertThat(salarySlipMapper.findFilePathById(999L)).isNull();
    }

    @Test
    void findEmployeeIdById_returnsOwner_orNullWhenNotFound() {
        Long id = insertSlip(4L, "2026-10");

        assertThat(salarySlipMapper.findEmployeeIdById(id)).isEqualTo(4L);
        assertThat(salarySlipMapper.findEmployeeIdById(999L)).isNull();
    }

    // ---------- updateFilePath ----------

    @Test
    void updateFilePath_overwritesPathUploaderAndTime_withoutAddingRows() {
        Long id = insertSlip(4L, "2026-10");
        setUploadedAt(id, LocalDateTime.of(2026, 1, 1, 0, 0));//古い日時にしておく

        salarySlipMapper.updateFilePath(4L, "2026-10", "salary-slips/new.pdf", 2L);

        assertThat(salarySlipMapper.countByEmployeeAndMonth(4L, "2026-10")).isEqualTo(1);//行は増えない
        assertThat(salarySlipMapper.findFilePathById(id)).isEqualTo("salary-slips/new.pdf");
        SalarySlipUploadHistory h = salarySlipMapper.selectHistory(10, 0).get(0);
        assertThat(h.getUploadedBy()).isEqualTo(2L);//アップロード者が更新される
        assertThat(h.getUploadedAt()).isAfter(LocalDateTime.of(2026, 1, 2, 0, 0));//日時も更新される
    }

    // ---------- deleteById ----------

    @Test
    void deleteById_returnsOneThenZero_andRemovesRow() {
        Long id = insertSlip(4L, "2026-10");

        assertThat(salarySlipMapper.deleteById(id)).isEqualTo(1);
        assertThat(salarySlipMapper.deleteById(id)).isEqualTo(0);//2回目は対象なし
        assertThat(salarySlipMapper.findFilePathById(id)).isNull();
    }

    // ---------- selectHistory (API013) ----------

    @Test
    void selectHistory_mapsAllColumns() {
        Long id = insertSlip(4L, "2026-10");
        setUploadedAt(id, LocalDateTime.of(2026, 10, 6, 15, 30, 0));

        SalarySlipUploadHistory h = salarySlipMapper.selectHistory(10, 0).get(0);

        assertThat(h.getSlipId()).isEqualTo(id);//id AS slip_id の対応
        assertThat(h.getEmployeeId()).isEqualTo(4L);
        assertThat(h.getPayMonth()).isEqualTo("2026-10");
        assertThat(h.getUploadedBy()).isEqualTo(1L);
        assertThat(h.getUploadedAt()).isEqualTo(LocalDateTime.of(2026, 10, 6, 15, 30, 0));
    }

    @Test
    void selectHistory_ordersByUploadedAtDescending() {
        Long oldest = insertSlip(4L, "2026-08");
        Long middle = insertSlip(4L, "2026-09");
        Long newest = insertSlip(1L, "2026-09");
        //登録した順とは違う並びになるよう、日時を明示する
        setUploadedAt(oldest, LocalDateTime.of(2026, 8, 31, 10, 0));
        setUploadedAt(middle, LocalDateTime.of(2026, 9, 30, 10, 0));
        setUploadedAt(newest, LocalDateTime.of(2026, 9, 30, 11, 0));

        List<SalarySlipUploadHistory> history = salarySlipMapper.selectHistory(10, 0);

        assertThat(history).extracting(SalarySlipUploadHistory::getSlipId)
                .containsExactly(newest, middle, oldest);
    }

    @Test
    void selectHistory_ordersByIdDescending_whenUploadedAtIsSame() {
        //同じトランザクション内ではnow()が同じ値になるので、日時が同じ場合の並びを確認できる
        Long first = insertSlip(4L, "2026-08");
        Long second = insertSlip(4L, "2026-09");
        Long third = insertSlip(4L, "2026-10");

        assertThat(salarySlipMapper.selectHistory(10, 0)).extracting(SalarySlipUploadHistory::getSlipId)
                .containsExactly(third, second, first);
    }

    @Test
    void selectHistory_appliesLimitAndOffset() {
        Long a = insertSlip(4L, "2026-08");
        Long b = insertSlip(4L, "2026-09");
        Long c = insertSlip(4L, "2026-10");
        setUploadedAt(a, LocalDateTime.of(2026, 8, 31, 10, 0));
        setUploadedAt(b, LocalDateTime.of(2026, 9, 30, 10, 0));
        setUploadedAt(c, LocalDateTime.of(2026, 10, 31, 10, 0));

        assertThat(salarySlipMapper.selectHistory(1, 0)).extracting(SalarySlipUploadHistory::getSlipId)
                .containsExactly(c);
        assertThat(salarySlipMapper.selectHistory(1, 1)).extracting(SalarySlipUploadHistory::getSlipId)
                .containsExactly(b);
        assertThat(salarySlipMapper.selectHistory(2, 2)).extracting(SalarySlipUploadHistory::getSlipId)
                .containsExactly(a);
        assertThat(salarySlipMapper.selectHistory(10, 99)).isEmpty();//範囲外のページ
    }

    @Test
    void selectHistory_returnsEmptyList_whenNoSlips() {
        assertThat(salarySlipMapper.selectHistory(10, 0)).isEmpty();
    }
}
