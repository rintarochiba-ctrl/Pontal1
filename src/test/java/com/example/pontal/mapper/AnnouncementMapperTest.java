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
import com.example.pontal.dto.Announcement;

//AnnouncementMapperのSQLのテスト。DockerでPostgreSQLを起動し、schema.sql/data.sqlを流して実際にSQLを実行する
//前提の初期データ(data.sql): id=1 山田(開発部,システム管理者) / 2 佐藤(人事部,HR) / 3 鈴木(営業部) / 4 権限なしテスト(開発部)
//お知らせは、テストの中で、AnnouncementMapper.insert で作る(日時を変えたいときは、JdbcTemplateで書き換える)
@MybatisTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
@Import(MyBatisConfig.class)
class AnnouncementMapperTest {

    private static final Long YAMADA = 1L;
    private static final Long SATO = 2L;
    private static final Long SUZUKI = 3L;
    private static final Long NO_ROLE = 4L;

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
    private AnnouncementMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------- テストデータを作る部品 ----------

    //全員向けのお知らせ(差出人は山田・開発部)。ほかの宛先にしたいときは、target...で上書きする
    private Announcement news(String title) {
        Announcement a = new Announcement();
        a.setTitle(title);
        a.setBody("本文:" + title);
        a.setCategory("社内情報");
        a.setIsImportant(false);
        a.setSenderEmployeeId(YAMADA);
        a.setSenderDepartment("開発部");
        a.setTargetType("ALL");
        return a;
    }

    private Announcement toDepartment(String title, String department) {
        Announcement a = news(title);
        a.setTargetType("DEPARTMENT");
        a.setTargetDepartment(department);
        return a;
    }

    private Announcement toEmployee(String title, Long employeeId) {
        Announcement a = news(title);
        a.setTargetType("EMPLOYEE");
        a.setTargetEmployeeId(employeeId);
        return a;
    }

    //作成して、日時を指定した値にする(INSERTだけだと、いまの日時になってしまうため)
    private Long insertAt(Announcement a, LocalDateTime at) {
        Long id = mapper.insert(a);
        jdbcTemplate.update("UPDATE announcement SET created_at = ?, updated_at = ? WHERE id = ?", at, at, id);
        return id;
    }

    private List<String> titles(List<Announcement> list) {
        return list.stream().map(Announcement::getTitle).toList();
    }

    //一覧を取る(視点の人と、範囲を指定)。大きめのsizeで、全部取る
    private List<Announcement> list(String scope, Long viewerId, String viewerDepartment, String category) {
        return mapper.selectVisible(scope, viewerId, viewerDepartment, category, 100, 0);
    }

    //見られるかどうかを確かめるための、6件のお知らせ(全員1・部署3・個人2)
    //  A 全員向け / B 開発部宛て / C 人事部宛て / F 営業部宛て / D 権限なしテスト宛て / E 佐藤宛て
    private void insertVisibilitySamples() {
        mapper.insert(news("A 全員向け"));
        mapper.insert(toDepartment("B 開発部宛て", "開発部"));
        mapper.insert(toDepartment("C 人事部宛て", "人事部"));
        mapper.insert(toDepartment("F 営業部宛て", "営業部"));
        mapper.insert(toEmployee("D 権限なしテスト宛て", NO_ROLE));
        mapper.insert(toEmployee("E 佐藤宛て", SATO));
    }

    // ---------- insert ----------

    @Test
    void insert_returnsId_andSavesAllColumns() {
        Announcement a = news("メンテナンスのお知らせ");
        a.setBody("夜間にメンテナンスを行います");
        a.setCategory("技術");
        a.setIsImportant(true);

        Long id = mapper.insert(a);

        assertThat(id).isNotNull();
        Announcement saved = mapper.findById(id);
        assertThat(saved.getTitle()).isEqualTo("メンテナンスのお知らせ");
        assertThat(saved.getBody()).isEqualTo("夜間にメンテナンスを行います");
        assertThat(saved.getCategory()).isEqualTo("技術");
        assertThat(saved.getIsImportant()).isTrue();
        assertThat(saved.getSenderEmployeeId()).isEqualTo(YAMADA);
        assertThat(saved.getSenderDepartment()).isEqualTo("開発部");
        assertThat(saved.getTargetType()).isEqualTo("ALL");
        assertThat(saved.getTargetDepartment()).isNull();
        assertThat(saved.getTargetEmployeeId()).isNull();
    }

    @Test
    void insert_setsCreatedAtAndUpdatedAt_andIsNotDeleted() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(1);

        Long id = mapper.insert(news("日時のテスト"));

        Announcement saved = mapper.findById(id);
        assertThat(saved.getCreatedAt()).isAfter(before);
        assertThat(saved.getUpdatedAt()).isAfter(before);
        Boolean deleted = jdbcTemplate.queryForObject("SELECT is_delete FROM announcement WHERE id = ?", Boolean.class, id);
        assertThat(deleted).isFalse();
    }

    @Test
    void insert_calledTwice_createsTwoRows() {
        //INSERT ... RETURNING を select で書いているので、2回目が、MyBatisのキャッシュで、実行されない問題がないこと
        Long first = mapper.insert(news("1回目"));
        Long second = mapper.insert(news("2回目"));

        assertThat(second).isNotEqualTo(first);
        assertThat(titles(list("manage", YAMADA, "開発部", null))).containsExactlyInAnyOrder("1回目", "2回目");
    }

    @Test
    void insert_savesDepartmentTarget() {
        Long id = mapper.insert(toDepartment("部署宛て", "人事部"));

        Announcement saved = mapper.findById(id);
        assertThat(saved.getTargetType()).isEqualTo("DEPARTMENT");
        assertThat(saved.getTargetDepartment()).isEqualTo("人事部");
        assertThat(saved.getTargetEmployeeId()).isNull();
    }

    @Test
    void insert_savesEmployeeTarget_andFindByIdReturnsTargetName() {
        Long id = mapper.insert(toEmployee("個人宛て", SATO));

        Announcement saved = mapper.findById(id);
        assertThat(saved.getTargetType()).isEqualTo("EMPLOYEE");
        assertThat(saved.getTargetEmployeeId()).isEqualTo(SATO);
        assertThat(saved.getTargetEmployeeName()).isEqualTo("佐藤花子");
        assertThat(saved.getTargetDepartment()).isNull();
    }

    @Test
    void insert_acceptsMaxLengthTitleAndBody() {
        Announcement a = news("あ".repeat(100));
        a.setBody("い".repeat(2000));

        Announcement saved = mapper.findById(mapper.insert(a));

        assertThat(saved.getTitle()).hasSize(100);
        assertThat(saved.getBody()).hasSize(2000);
    }

    // ---------- insert: 制約(DBが、おかしな値を弾く) ----------

    @Test
    void insert_fails_whenTitleIsTooLong() {
        assertThatThrownBy(() -> mapper.insert(news("あ".repeat(101))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insert_fails_whenBodyIsTooLong() {
        Announcement a = news("長い本文");
        a.setBody("い".repeat(2001));

        assertThatThrownBy(() -> mapper.insert(a)).isInstanceOf(DataIntegrityViolationException.class);
    }

    //注意: DBエラーが起きると、そのテストのトランザクションは中断される。そのため、1つのテストで失敗させるのは1回だけにする
    private void assertInsertFails(Announcement a) {
        assertThatThrownBy(() -> mapper.insert(a)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insert_fails_whenTitleIsMissing() {
        Announcement a = news("x");
        a.setTitle(null);
        assertInsertFails(a);
    }

    @Test
    void insert_fails_whenCategoryIsMissing() {
        Announcement a = news("x");
        a.setCategory(null);
        assertInsertFails(a);
    }

    @Test
    void insert_fails_whenTargetTypeIsUnknown() {
        Announcement a = news("x");
        a.setTargetType("EVERYONE");

        assertThatThrownBy(() -> mapper.insert(a)).isInstanceOf(DataIntegrityViolationException.class);
    }

    //全員向けなのに、部署や個人が入っている(宛先の矛盾)
    @Test
    void insert_fails_whenAllTargetHasDepartment() {
        Announcement a = news("x");
        a.setTargetDepartment("開発部");
        assertInsertFails(a);
    }

    @Test
    void insert_fails_whenAllTargetHasEmployee() {
        Announcement a = news("x");
        a.setTargetEmployeeId(SATO);
        assertInsertFails(a);
    }

    @Test
    void insert_fails_whenDepartmentTargetHasNoDepartment() {
        assertInsertFails(toDepartment("x", null));
    }

    @Test
    void insert_fails_whenDepartmentTargetHasEmployee() {
        Announcement a = toDepartment("x", "開発部");
        a.setTargetEmployeeId(SATO);
        assertInsertFails(a);
    }

    @Test
    void insert_fails_whenEmployeeTargetHasNoEmployee() {
        assertInsertFails(toEmployee("x", null));
    }

    @Test
    void insert_fails_whenEmployeeTargetHasDepartment() {
        Announcement a = toEmployee("x", SATO);
        a.setTargetDepartment("開発部");
        assertInsertFails(a);
    }

    @Test
    void insert_fails_whenSenderDoesNotExist() {
        Announcement a = news("x");
        a.setSenderEmployeeId(999L);
        assertInsertFails(a);
    }

    @Test
    void insert_fails_whenTargetEmployeeDoesNotExist() {
        assertInsertFails(toEmployee("x", 999L));
    }

    // ---------- findById ----------

    @Test
    void findById_returnsBodyAndSender() {
        Long id = mapper.insert(news("詳細"));

        Announcement found = mapper.findById(id);

        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getBody()).isEqualTo("本文:詳細");
        assertThat(found.getSenderEmployeeId()).isEqualTo(YAMADA);
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void findById_returnsNull_whenNotFoundOrDeleted() {
        Long id = mapper.insert(news("消す"));
        mapper.softDeleteById(id);

        assertThat(mapper.findById(id)).isNull();
        assertThat(mapper.findById(99999L)).isNull();
    }

    // ---------- selectVisible / countVisible: 見られる範囲(scope) ----------

    @Test
    void scopeAll_showsEveryoneAndOwnPersonalAndOwnDepartment() {
        insertVisibilitySamples();

        //山田(開発部): 全員向け + 開発部宛て。人事部宛て・営業部宛て・他人宛ては、見えない
        assertThat(titles(list("all", YAMADA, "開発部", null))).containsExactlyInAnyOrder("A 全員向け", "B 開発部宛て");
        //権限なしテスト(開発部): 上の2つ + 自分宛て
        assertThat(titles(list("all", NO_ROLE, "開発部", null)))
                .containsExactlyInAnyOrder("A 全員向け", "B 開発部宛て", "D 権限なしテスト宛て");
        //佐藤(人事部)
        assertThat(titles(list("all", SATO, "人事部", null)))
                .containsExactlyInAnyOrder("A 全員向け", "C 人事部宛て", "E 佐藤宛て");
        //鈴木(営業部)
        assertThat(titles(list("all", SUZUKI, "営業部", null))).containsExactlyInAnyOrder("A 全員向け", "F 営業部宛て");
    }

    @Test
    void scopeMine_excludesEveryoneNotices() {
        insertVisibilitySamples();

        assertThat(titles(list("mine", NO_ROLE, "開発部", null))).containsExactlyInAnyOrder("B 開発部宛て", "D 権限なしテスト宛て");
        assertThat(titles(list("mine", SUZUKI, "営業部", null))).containsExactly("F 営業部宛て");
    }

    @Test
    void scopeEveryone_showsOnlyEveryoneNotices() {
        insertVisibilitySamples();

        assertThat(titles(list("everyone", NO_ROLE, "開発部", null))).containsExactly("A 全員向け");
    }

    @Test
    void scopeManage_showsEverythingRegardlessOfViewer() {
        insertVisibilitySamples();

        //視点の人(存在しないid・部署なし)に関わらず、宛先を問わず全件
        assertThat(list("manage", 99999L, null, null)).hasSize(6);
        assertThat(mapper.countVisible("manage", 99999L, null, null)).isEqualTo(6);
    }

    @Test
    void unknownScope_behavesLikeAll() {
        insertVisibilitySamples();

        assertThat(titles(list("whatever", NO_ROLE, "開発部", null)))
                .containsExactlyInAnyOrder("A 全員向け", "B 開発部宛て", "D 権限なしテスト宛て");
    }

    @Test
    void viewerWithoutDepartment_doesNotMatchDepartmentNotices() {
        insertVisibilitySamples();

        //部署が未設定の人には、部署宛てのお知らせは、見えない(全員向けと、自分宛てだけ)
        assertThat(titles(list("all", NO_ROLE, null, null))).containsExactlyInAnyOrder("A 全員向け", "D 権限なしテスト宛て");
    }

    @Test
    void personalNotice_isNotVisibleToOtherEmployees() {
        Long id = mapper.insert(toEmployee("山田さん宛て", YAMADA));

        assertThat(titles(list("all", SATO, "人事部", null))).isEmpty();
        assertThat(titles(list("all", YAMADA, "開発部", null))).containsExactly("山田さん宛て");
        assertThat(id).isNotNull();
    }

    // ---------- selectVisible: 種類の絞り込み・並び順・ページ ----------

    @Test
    void category_filtersByCategory() {
        Announcement tech = news("技術の話");
        tech.setCategory("技術");
        mapper.insert(tech);
        mapper.insert(news("社内の話"));

        assertThat(titles(list("all", YAMADA, "開発部", "技術"))).containsExactly("技術の話");
        assertThat(mapper.countVisible("all", YAMADA, "開発部", "技術")).isEqualTo(1);
        //nullや空文字なら、絞り込まない
        assertThat(list("all", YAMADA, "開発部", null)).hasSize(2);
        assertThat(list("all", YAMADA, "開発部", "")).hasSize(2);
    }

    @Test
    void order_importantFirst_thenNewestFirst() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 12, 0, 0);
        Announcement oldImportant = news("古い重要");
        oldImportant.setIsImportant(true);
        Announcement newImportant = news("新しい重要");
        newImportant.setIsImportant(true);
        insertAt(oldImportant, now.minusDays(3));
        insertAt(news("新しい通常"), now.minusHours(1));
        insertAt(newImportant, now.minusHours(2));
        insertAt(news("少し前の通常"), now.minusDays(1));

        //重要が先(その中は新しい順)、次に通常(新しい順)
        assertThat(titles(list("all", YAMADA, "開発部", null)))
                .containsExactly("新しい重要", "古い重要", "新しい通常", "少し前の通常");
    }

    @Test
    void order_sameTime_newerIdFirst() {
        LocalDateTime at = LocalDateTime.of(2026, 10, 20, 12, 0, 0);
        insertAt(news("先に作った"), at);
        insertAt(news("あとに作った"), at);

        assertThat(titles(list("all", YAMADA, "開発部", null))).containsExactly("あとに作った", "先に作った");
    }

    @Test
    void paging_splitsBySizeAndOffset_andCountIsTheTotal() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 9, 0, 0);
        for (int i = 1; i <= 12; i++) {
            insertAt(news("お知らせ" + i), base.plusHours(i));
        }

        List<Announcement> page0 = mapper.selectVisible("all", YAMADA, "開発部", null, 5, 0);
        List<Announcement> page1 = mapper.selectVisible("all", YAMADA, "開発部", null, 5, 5);
        List<Announcement> page2 = mapper.selectVisible("all", YAMADA, "開発部", null, 5, 10);

        assertThat(page0).hasSize(5);
        assertThat(page1).hasSize(5);
        assertThat(page2).hasSize(2);
        assertThat(titles(page0).get(0)).isEqualTo("お知らせ12"); //新しい順
        assertThat(titles(page2)).containsExactly("お知らせ2", "お知らせ1");
        assertThat(mapper.countVisible("all", YAMADA, "開発部", null)).isEqualTo(12);
        //範囲外のページは、空
        assertThat(mapper.selectVisible("all", YAMADA, "開発部", null, 5, 100)).isEmpty();
    }

    @Test
    void list_hasNoBody_butHasTargetEmployeeName() {
        mapper.insert(news("全員向け"));
        mapper.insert(toEmployee("佐藤宛て", SATO));

        List<Announcement> result = list("manage", YAMADA, "開発部", null);

        assertThat(result).allSatisfy(a -> assertThat(a.getBody()).isNull()); //本文は、一覧に含めない
        assertThat(result).filteredOn(a -> a.getTitle().equals("佐藤宛て"))
                .singleElement().satisfies(a -> assertThat(a.getTargetEmployeeName()).isEqualTo("佐藤花子"));
        assertThat(result).filteredOn(a -> a.getTitle().equals("全員向け"))
                .singleElement().satisfies(a -> assertThat(a.getTargetEmployeeName()).isNull());
    }

    @Test
    void list_isEmpty_whenNoNotices() {
        assertThat(list("manage", YAMADA, "開発部", null)).isEmpty();
        assertThat(mapper.countVisible("manage", YAMADA, "開発部", null)).isZero();
    }

    // ---------- findVisibleById ----------

    @Test
    void findVisibleById_returnsBody_forPeopleWhoCanSeeIt() {
        Long id = mapper.insert(toDepartment("開発部だけ", "開発部"));

        Announcement found = mapper.findVisibleById(id, "all", NO_ROLE, "開発部");

        assertThat(found.getTitle()).isEqualTo("開発部だけ");
        assertThat(found.getBody()).isEqualTo("本文:開発部だけ");
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void findVisibleById_returnsNull_forPeopleWhoCannotSeeIt() {
        Long department = mapper.insert(toDepartment("開発部だけ", "開発部"));
        Long personal = mapper.insert(toEmployee("山田さんだけ", YAMADA));

        //他の部署の人、他人宛ては、見えない(存在を教えないため、「ない」と同じ結果)
        assertThat(mapper.findVisibleById(department, "all", SATO, "人事部")).isNull();
        assertThat(mapper.findVisibleById(personal, "all", SATO, "人事部")).isNull();
        assertThat(mapper.findVisibleById(personal, "all", NO_ROLE, "開発部")).isNull(); //同じ部署でも、個人宛ては別
    }

    @Test
    void findVisibleById_manageScope_canSeeEverything() {
        Long personal = mapper.insert(toEmployee("山田さんだけ", YAMADA));

        assertThat(mapper.findVisibleById(personal, "manage", SATO, "人事部")).isNotNull();
    }

    @Test
    void findVisibleById_returnsNull_whenDeletedOrUnknown() {
        Long id = mapper.insert(news("消す"));
        mapper.softDeleteById(id);

        //管理者(manage)でも、削除済みは見えない
        assertThat(mapper.findVisibleById(id, "manage", YAMADA, "開発部")).isNull();
        assertThat(mapper.findVisibleById(99999L, "manage", YAMADA, "開発部")).isNull();
    }

    // ---------- update ----------

    @Test
    void update_changesFields_butKeepsSenderAndCreatedAt() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 1, 9, 0, 0);
        Long id = insertAt(news("元のタイトル"), created);
        Announcement edit = news("新しいタイトル");
        edit.setBody("新しい本文");
        edit.setCategory("対応依頼");
        edit.setIsImportant(true);
        edit.setSenderEmployeeId(SATO);//差出人は、変えられない(無視される)
        edit.setSenderDepartment("人事部");

        int updated = mapper.update(id, edit);

        assertThat(updated).isEqualTo(1);
        Announcement saved = mapper.findById(id);
        assertThat(saved.getTitle()).isEqualTo("新しいタイトル");
        assertThat(saved.getBody()).isEqualTo("新しい本文");
        assertThat(saved.getCategory()).isEqualTo("対応依頼");
        assertThat(saved.getIsImportant()).isTrue();
        assertThat(saved.getSenderEmployeeId()).isEqualTo(YAMADA);
        assertThat(saved.getSenderDepartment()).isEqualTo("開発部");
        assertThat(saved.getCreatedAt()).isEqualTo(created);
        assertThat(saved.getUpdatedAt()).isAfter(created);
    }

    @Test
    void update_canChangeTarget_fromAllToDepartmentToEmployee() {
        Long id = mapper.insert(news("宛先を変える"));

        mapper.update(id, toDepartment("宛先を変える", "人事部"));
        Announcement department = mapper.findById(id);
        assertThat(department.getTargetType()).isEqualTo("DEPARTMENT");
        assertThat(department.getTargetDepartment()).isEqualTo("人事部");

        mapper.update(id, toEmployee("宛先を変える", SATO));
        Announcement employee = mapper.findById(id);
        assertThat(employee.getTargetType()).isEqualTo("EMPLOYEE");
        assertThat(employee.getTargetEmployeeId()).isEqualTo(SATO);
        assertThat(employee.getTargetDepartment()).isNull(); //前の宛先(部署)は、消える

        mapper.update(id, news("宛先を変える"));
        Announcement all = mapper.findById(id);
        assertThat(all.getTargetType()).isEqualTo("ALL");
        assertThat(all.getTargetEmployeeId()).isNull();
    }

    @Test
    void update_returnsZero_whenNotFoundOrDeleted() {
        Long id = mapper.insert(news("消す"));
        mapper.softDeleteById(id);

        assertThat(mapper.update(id, news("更新"))).isZero();
        assertThat(mapper.update(99999L, news("更新"))).isZero();
    }

    @Test
    void update_fails_whenTargetIsInconsistent() {
        Long id = mapper.insert(news("矛盾"));
        Announcement broken = news("矛盾");
        broken.setTargetType("DEPARTMENT");//部署の指定がない

        assertThatThrownBy(() -> mapper.update(id, broken)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- softDeleteById ----------

    @Test
    void softDelete_hidesFromEverything_butKeepsTheRow() {
        Long id = mapper.insert(news("削除する"));

        int deleted = mapper.softDeleteById(id);

        assertThat(deleted).isEqualTo(1);
        assertThat(mapper.findById(id)).isNull();
        assertThat(list("manage", YAMADA, "開発部", null)).isEmpty();
        assertThat(mapper.countVisible("manage", YAMADA, "開発部", null)).isZero();
        //行そのものは、残っている(論理削除)
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM announcement WHERE id = ? AND is_delete = TRUE", Integer.class, id);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void softDelete_returnsZero_whenAlreadyDeletedOrUnknown() {
        Long id = mapper.insert(news("削除する"));
        mapper.softDeleteById(id);

        assertThat(mapper.softDeleteById(id)).isZero();
        assertThat(mapper.softDeleteById(99999L)).isZero();
    }
}
