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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.pontal.config.MyBatisConfig;
import com.example.pontal.dto.EmployeeCreateRequest;
import com.example.pontal.dto.EmployeeDetail;
import com.example.pontal.dto.EmployeeSummary;
import com.example.pontal.dto.EmployeeUpdateRequest;
import com.example.pontal.dto.LoginEmployee;

//EmployeeMapperのSQLのテスト。DockerでPostgreSQLを起動し、schema.sql/data.sqlを流して実際にSQLを実行する
//前提の初期データ(data.sql): id=1 山田(開発部,管理者) / 2 佐藤(人事部,HR) / 3 鈴木(営業部) / 4 権限なしテスト(開発部)
//@MybatisTestはテストごとにトランザクションを張り、終了時にロールバックするので、テスト同士は影響しない
@MybatisTest
@AutoConfigureTestDatabase(replace = Replace.NONE)//組み込みDBに差し替えず、下で起動する本物のPostgreSQLを使う
@Testcontainers
@Import(MyBatisConfig.class)//@MapperScanの設定を読み込む(スライステストでは自動では読まれない)
class EmployeeMapperTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    //起動したコンテナの接続先を、アプリのDB設定に差し込む
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        //アプリ本体はSQLを自動実行しない設定なので、このテストでだけ、schema.sqlとdata.sqlを流す
        registry.add("spring.sql.init.mode", () -> "always");
    }

    @Autowired
    private EmployeeMapper employeeMapper;

    // ---------- findByCognitoSub ----------

    @Test
    void findByCognitoSub_returnsLoginEmployeeWithRoles() {
        LoginEmployee e = employeeMapper.findByCognitoSub("29cec488-b011-70d2-5648-27e12d2e21c7");

        assertThat(e.getEmployeeId()).isEqualTo(1L);
        assertThat(e.getName()).isEqualTo("山田太郎");
        assertThat(e.getIsSystemAdmin()).isTrue();
        assertThat(e.getIsHrAdmin()).isFalse();
    }

    @Test
    void findByCognitoSub_returnsNull_whenUnknown() {
        assertThat(employeeMapper.findByCognitoSub("unknown-sub")).isNull();
    }

    @Test
    void findByCognitoSub_returnsNull_whenSoftDeleted() {
        employeeMapper.softDeleteById(3L);

        assertThat(employeeMapper.findByCognitoSub("a1b2c3d4-0000-4000-8000-000000000002")).isNull();
    }

    // ---------- findDetailById ----------

    @Test
    void findDetailById_mapsAllColumns() {
        EmployeeDetail d = employeeMapper.findDetailById(2L);

        assertThat(d.getName()).isEqualTo("佐藤花子");
        assertThat(d.getEmail()).isEqualTo("sato.hanako@example.com");
        assertThat(d.getDepartment()).isEqualTo("人事部");
        assertThat(d.getJoinDate()).isEqualTo(LocalDate.of(2019, 7, 15));
        assertThat(d.getAge()).isEqualTo(35);
        assertThat(d.getHobby()).isEqualTo("ヨガ");
        assertThat(d.getIsHrAdmin()).isTrue();//is_hr_admin → isHrAdminの対応
        assertThat(d.getIsSystemAdmin()).isFalse();
    }

    @Test
    void findDetailById_returnsNull_whenNotFound() {
        assertThat(employeeMapper.findDetailById(999L)).isNull();
    }

    @Test
    void findDetailById_returnsNull_whenSoftDeleted() {
        employeeMapper.softDeleteById(3L);

        assertThat(employeeMapper.findDetailById(3L)).isNull();
    }

    // ---------- selectByKeyword / countByKeyword ----------

    @Test
    void selectByKeyword_withoutKeyword_returnsAllOrderedById() {
        List<EmployeeSummary> list = employeeMapper.selectByKeyword(null, 10, 0);

        assertThat(list).extracting(EmployeeSummary::getId).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void selectByKeyword_emptyKeyword_isTreatedAsNoFilter() {
        assertThat(employeeMapper.selectByKeyword("", 10, 0)).hasSize(4);
    }

    @Test
    void selectByKeyword_matchesDepartment() {
        List<EmployeeSummary> list = employeeMapper.selectByKeyword("人事", 10, 0);

        assertThat(list).extracting(EmployeeSummary::getName).containsExactly("佐藤花子");
    }

    @Test
    void selectByKeyword_matchesNameAndEmail_caseInsensitive() {
        //ILIKEなので大文字小文字を区別しない(メールで検索)
        assertThat(employeeMapper.selectByKeyword("SATO.HANAKO", 10, 0))
                .extracting(EmployeeSummary::getId).containsExactly(2L);
        //名前で検索
        assertThat(employeeMapper.selectByKeyword("山田", 10, 0))
                .extracting(EmployeeSummary::getId).containsExactly(1L);
    }

    @Test
    void selectByKeyword_appliesLimitAndOffset() {
        List<EmployeeSummary> secondPage = employeeMapper.selectByKeyword(null, 2, 2);

        assertThat(secondPage).extracting(EmployeeSummary::getId).containsExactly(3L, 4L);
    }

    @Test
    void selectByKeyword_excludesSoftDeleted() {
        employeeMapper.softDeleteById(3L);

        assertThat(employeeMapper.selectByKeyword(null, 10, 0))
                .extracting(EmployeeSummary::getId).containsExactly(1L, 2L, 4L);
    }

    @Test
    void countByKeyword_countsSameConditionAsSelect() {
        assertThat(employeeMapper.countByKeyword(null)).isEqualTo(4);
        assertThat(employeeMapper.countByKeyword("開発")).isEqualTo(2);//山田と権限なしテスト
        assertThat(employeeMapper.countByKeyword("zzz")).isEqualTo(0);

        employeeMapper.softDeleteById(3L);
        assertThat(employeeMapper.countByKeyword(null)).isEqualTo(3);
    }

    // ---------- countByEmail ----------

    @Test
    void countByEmail_countsExistingAndUnknown() {
        assertThat(employeeMapper.countByEmail("test@example.com")).isEqualTo(1);
        assertThat(employeeMapper.countByEmail("nobody@example.com")).isEqualTo(0);
    }

    @Test
    void countByEmail_includesSoftDeleted() {
        //論理削除済みでもemailのUNIQUE制約は残るので、重複としてカウントされる
        employeeMapper.softDeleteById(3L);

        assertThat(employeeMapper.countByEmail("suzuki.ichiro@example.com")).isEqualTo(1);
    }

    // ---------- insert ----------

    private EmployeeCreateRequest newEmployee(String email) {
        EmployeeCreateRequest r = new EmployeeCreateRequest();
        r.setName("新入社員");
        r.setEmail(email);
        r.setInitialPassword("ignored");//DBには保存されない項目
        r.setJoinDate(LocalDate.of(2026, 10, 1));
        r.setDepartment("開発部");
        r.setPosition("エンジニア");
        r.setIsHrAdmin(true);
        return r;
    }

    @Test
    void insert_returnsGeneratedId_andSavesRow() {
        Long id = employeeMapper.insert(newEmployee("new@example.com"), "new-sub");

        assertThat(id).isGreaterThan(4L);//data.sqlの4件の次の番号
        EmployeeDetail saved = employeeMapper.findDetailById(id);
        assertThat(saved.getName()).isEqualTo("新入社員");
        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getIsHrAdmin()).isTrue();
        assertThat(saved.getIsSystemAdmin()).isFalse();//省略したフラグはfalse
        assertThat(employeeMapper.findByCognitoSub("new-sub").getEmployeeId()).isEqualTo(id);
    }

    @Test
    void insert_fails_whenEmailAlreadyExists() {
        assertThatThrownBy(() -> employeeMapper.insert(newEmployee("test@example.com"), "another-sub"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insert_fails_whenCognitoSubAlreadyExists() {
        assertThatThrownBy(() -> employeeMapper.insert(newEmployee("other@example.com"),
                "29cec488-b011-70d2-5648-27e12d2e21c7"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- update ----------

    private EmployeeUpdateRequest updateRequest() {
        EmployeeUpdateRequest r = new EmployeeUpdateRequest();
        r.setName("鈴木次郎");
        r.setGender("男性");
        r.setAge(31);
        r.setBirthplace("愛知県");
        r.setJoinDate(LocalDate.of(2021, 10, 1));
        r.setDepartment("営業部");
        r.setPosition("課長");
        r.setImage("employees/3.png");
        r.setBio("更新テスト");
        r.setHobby("釣り");
        r.setSelfQa("Q&A");
        r.setIsSystemAdmin(false);
        r.setIsHrAdmin(true);
        return r;
    }

    @Test
    void update_changesAllFields_andReturnsOne() {
        int updated = employeeMapper.update(3L, updateRequest());

        assertThat(updated).isEqualTo(1);
        EmployeeDetail d = employeeMapper.findDetailById(3L);
        assertThat(d.getName()).isEqualTo("鈴木次郎");
        assertThat(d.getAge()).isEqualTo(31);
        assertThat(d.getPosition()).isEqualTo("課長");
        assertThat(d.getImageUrl()).isEqualTo("employees/3.png");//リクエストのimage → image_url列
        assertThat(d.getSelfQa()).isEqualTo("Q&A");
        assertThat(d.getIsHrAdmin()).isTrue();
        assertThat(d.getEmail()).isEqualTo("suzuki.ichiro@example.com");//更新対象外の列は変わらない
    }

    @Test
    void update_returnsZero_whenNotFound() {
        assertThat(employeeMapper.update(999L, updateRequest())).isEqualTo(0);
    }

    @Test
    void update_returnsZero_whenSoftDeleted() {
        employeeMapper.softDeleteById(3L);

        assertThat(employeeMapper.update(3L, updateRequest())).isEqualTo(0);
    }

    // ---------- softDeleteById ----------

    @Test
    void softDeleteById_returnsOneThenZero_andHidesEmployee() {
        assertThat(employeeMapper.softDeleteById(3L)).isEqualTo(1);
        assertThat(employeeMapper.softDeleteById(3L)).isEqualTo(0);//2回目は対象なし
        assertThat(employeeMapper.findDetailById(3L)).isNull();
    }

    // ---------- findCognitoSubById ----------

    @Test
    void findCognitoSubById_returnsSub_evenWhenSoftDeleted() {
        assertThat(employeeMapper.findCognitoSubById(1L)).isEqualTo("29cec488-b011-70d2-5648-27e12d2e21c7");

        //削除直後にCognitoを無効化するため、論理削除済みでも取得できる
        employeeMapper.softDeleteById(1L);
        assertThat(employeeMapper.findCognitoSubById(1L)).isEqualTo("29cec488-b011-70d2-5648-27e12d2e21c7");
    }

    @Test
    void findCognitoSubById_returnsNull_whenNotFound() {
        assertThat(employeeMapper.findCognitoSubById(999L)).isNull();
    }

    @Test
    void softDeleteById_returnsZero_whenNotFound() {
        assertThat(employeeMapper.softDeleteById(999L)).isEqualTo(0);
    }
}
