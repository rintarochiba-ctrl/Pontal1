package com.example.pontal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.pontal.dto.Announcement;
import com.example.pontal.dto.AnnouncementDetail;
import com.example.pontal.dto.AnnouncementPage;
import com.example.pontal.dto.AnnouncementRequest;
import com.example.pontal.dto.EmployeeDetail;
import com.example.pontal.dto.LoginEmployee;
import com.example.pontal.exception.ForbiddenException;
import com.example.pontal.exception.NotFoundException;
import com.example.pontal.exception.ValidationException;
import com.example.pontal.mapper.AnnouncementMapper;
import com.example.pontal.mapper.EmployeeMapper;

//AnnouncementServiceの単体テスト。MapperとEmployeeServiceはモックに差し替え、DBには繋がない
@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock
    private AnnouncementMapper announcementMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private EmployeeService employeeService;
    @InjectMocks
    private AnnouncementService service;

    //ログイン中の3人(sub → 社員は、loginAs で決める)
    private LoginEmployee sysAdmin;   //id=1 システム管理者(開発部)
    private LoginEmployee hrAdmin;    //id=2 HR管理者(人事部)
    private LoginEmployee general;    //id=3 一般社員(営業部)

    @BeforeEach
    void setUp() {
        sysAdmin = login(1L, true, false);
        hrAdmin = login(2L, false, true);
        general = login(3L, false, false);
        //Serviceは、自分の部署を employeeMapper.findDetailById で調べる
        lenient().when(employeeService.getLoginEmployee("sub-1")).thenReturn(sysAdmin);
        lenient().when(employeeService.getLoginEmployee("sub-2")).thenReturn(hrAdmin);
        lenient().when(employeeService.getLoginEmployee("sub-3")).thenReturn(general);
        lenient().when(employeeMapper.findDetailById(1L)).thenReturn(employee(1L, "開発部"));
        lenient().when(employeeMapper.findDetailById(2L)).thenReturn(employee(2L, "人事部"));
        lenient().when(employeeMapper.findDetailById(3L)).thenReturn(employee(3L, "営業部"));
    }

    // ---------- テストデータを作る部品 ----------

    private LoginEmployee login(Long id, boolean systemAdmin, boolean hrAdmin) {
        LoginEmployee l = new LoginEmployee();
        l.setEmployeeId(id);
        l.setIsSystemAdmin(systemAdmin);
        l.setIsHrAdmin(hrAdmin);
        return l;
    }

    private EmployeeDetail employee(Long id, String department) {
        EmployeeDetail e = new EmployeeDetail();
        e.setId(id);
        e.setDepartment(department);
        return e;
    }

    //DBから取れるお知らせ(全員向け)
    private Announcement saved(Long id, String type) {
        Announcement a = new Announcement();
        a.setId(id);
        a.setTitle("タイトル" + id);
        a.setBody("本文" + id);
        a.setCategory("社内情報");
        a.setIsImportant(true);
        a.setSenderEmployeeId(1L);
        a.setSenderDepartment("開発部");
        a.setTargetType(type);
        a.setCreatedAt(LocalDateTime.of(2026, 10, 20, 9, 0));
        a.setUpdatedAt(LocalDateTime.of(2026, 10, 21, 9, 0));
        return a;
    }

    //正しいリクエスト(全員向け)。テストごとに、変えたい項目だけ上書きする
    private AnnouncementRequest request() {
        AnnouncementRequest r = new AnnouncementRequest();
        r.setTitle("新しいお知らせ");
        r.setBody("本文です");
        r.setCategory("技術");
        r.setIsImportant(false);
        r.setTargetType("ALL");
        return r;
    }

    // ---------- getDepartments ----------

    @Test
    void getDepartments_returnsMapperResult() {
        when(employeeMapper.selectDepartments()).thenReturn(List.of("開発部", "人事部"));

        assertThat(service.getDepartments()).containsExactly("開発部", "人事部");
    }

    // ---------- list(問4:自分で書く) ----------

    @Test
    void list_firstPage_returnsItemsAndPaging() {
        Announcement all = saved(1L, "ALL");
        Announcement dept = saved(2L, "DEPARTMENT");
        dept.setTargetDepartment("営業部");
        //一般社員(営業部)が、scope=all で、1ページ目(10件ずつ)。全部で25件
        when(announcementMapper.selectVisible("all", 3L, "営業部", null, 10, 0)).thenReturn(List.of(all, dept));
        when(announcementMapper.countVisible("all", 3L, "営業部", null)).thenReturn(25);

        AnnouncementPage result = service.list("sub-3", "all", null, 0, 10);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getTargetLabel()).isEqualTo("全員");
        assertThat(result.getItems().get(1).getTargetLabel()).isEqualTo("営業部");
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("タイトル1");
        assertThat(result.getTotalCount()).isEqualTo(25);
        assertThat(result.getNext()).isEqualTo(1);
        assertThat(result.getPrev()).isNull();
    }

    @Test
    void list_middlePage_usesOffset_andHasNextAndPrev() {
        when(announcementMapper.selectVisible("all", 3L, "営業部", null, 10, 10)).thenReturn(List.of(saved(1L, "ALL")));
        when(announcementMapper.countVisible("all", 3L, "営業部", null)).thenReturn(25);

        AnnouncementPage result = service.list("sub-3", "all", null, 1, 10);

        assertThat(result.getNext()).isEqualTo(2);
        assertThat(result.getPrev()).isEqualTo(0);
    }

    @Test
    void list_lastPage_hasNoNext() {
        when(announcementMapper.selectVisible("all", 3L, "営業部", null, 10, 20)).thenReturn(List.of(saved(1L, "ALL")));
        when(announcementMapper.countVisible("all", 3L, "営業部", null)).thenReturn(25);

        AnnouncementPage result = service.list("sub-3", "all", null, 2, 10);

        assertThat(result.getNext()).isNull();
        assertThat(result.getPrev()).isEqualTo(1);
    }

    @Test
    void list_pageOutOfRange_returnsEmptyInsteadOfError() {
        when(announcementMapper.selectVisible("all", 3L, "営業部", null, 10, 50)).thenReturn(List.of());
        when(announcementMapper.countVisible("all", 3L, "営業部", null)).thenReturn(25);

        AnnouncementPage result = service.list("sub-3", "all", null, 5, 10);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalCount()).isEqualTo(25);
        assertThat(result.getNext()).isNull();
        assertThat(result.getPrev()).isEqualTo(2); //実在する最終ページ(2)までに丸める
    }

    @Test
    void list_noNotices_returnsEmptyPage() {
        when(announcementMapper.selectVisible("everyone", 3L, "営業部", null, 10, 0)).thenReturn(List.of());
        when(announcementMapper.countVisible("everyone", 3L, "営業部", null)).thenReturn(0);

        AnnouncementPage result = service.list("sub-3", "everyone", null, 0, 10);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalCount()).isZero();
        assertThat(result.getNext()).isNull();
        assertThat(result.getPrev()).isNull();
    }

    @Test
    void list_passesCategoryToMapper() {
        when(announcementMapper.selectVisible("mine", 3L, "営業部", "技術", 5, 0)).thenReturn(List.of());
        when(announcementMapper.countVisible("mine", 3L, "営業部", "技術")).thenReturn(0);

        assertThatCode(() -> service.list("sub-3", "mine", "技術", 0, 5)).doesNotThrowAnyException();
    }

    @Test
    void list_manageScope_isAllowedForSystemAdminAndHrAdmin() {
        when(announcementMapper.selectVisible(eq("manage"), anyLong(), any(), any(), eq(10), eq(0L))).thenReturn(List.of());
        when(announcementMapper.countVisible(eq("manage"), anyLong(), any(), any())).thenReturn(0);

        assertThatCode(() -> service.list("sub-1", "manage", null, 0, 10)).doesNotThrowAnyException();
        assertThatCode(() -> service.list("sub-2", "manage", null, 0, 10)).doesNotThrowAnyException();
    }

    @Test
    void list_manageScope_isForbiddenForGeneralEmployee() {
        assertThatThrownBy(() -> service.list("sub-3", "manage", null, 0, 10))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(announcementMapper);
    }

    @Test
    void list_throwsValidation_whenScopeIsUnknown() {
        assertThatThrownBy(() -> service.list("sub-3", "abc", null, 0, 10)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.list("sub-3", null, null, 0, 10)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(announcementMapper);
    }

    @Test
    void list_throwsValidation_whenCategoryIsUnknown() {
        assertThatThrownBy(() -> service.list("sub-3", "all", "不明な種類", 0, 10))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(announcementMapper);
    }

    @Test
    void list_throwsValidation_whenPageOrSizeIsOutOfRange() {
        assertThatThrownBy(() -> service.list("sub-3", "all", null, -1, 10)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.list("sub-3", "all", null, 0, 0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.list("sub-3", "all", null, 0, 101)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(announcementMapper);
    }

    // ---------- get ----------

    @Test
    void get_returnsDetail_forGeneralEmployee() {
        Announcement a = saved(5L, "ALL");
        when(announcementMapper.findVisibleById(5L, "all", 3L, "営業部")).thenReturn(a);

        AnnouncementDetail detail = service.get("sub-3", 5L);

        assertThat(detail.getId()).isEqualTo(5L);
        assertThat(detail.getBody()).isEqualTo("本文5");
        assertThat(detail.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 21, 9, 0));
        assertThat(detail.getTargetLabel()).isEqualTo("全員");
        assertThat(detail.getCanEdit()).isFalse();
        assertThat(detail.getCanDelete()).isFalse();
    }

    @Test
    void get_adminsUseManageScope_andCanEdit() {
        when(announcementMapper.findVisibleById(5L, "manage", 2L, "人事部")).thenReturn(saved(5L, "ALL"));
        when(announcementMapper.findVisibleById(5L, "manage", 1L, "開発部")).thenReturn(saved(5L, "ALL"));

        AnnouncementDetail byHr = service.get("sub-2", 5L);
        AnnouncementDetail bySys = service.get("sub-1", 5L);

        //HR管理者:編集はできるが、削除はできない / システム管理者:両方できる
        assertThat(byHr.getCanEdit()).isTrue();
        assertThat(byHr.getCanDelete()).isFalse();
        assertThat(bySys.getCanEdit()).isTrue();
        assertThat(bySys.getCanDelete()).isTrue();
    }

    @Test
    void get_targetLabel_isDepartmentNameOrEmployeeName() {
        Announcement dept = saved(6L, "DEPARTMENT");
        dept.setTargetDepartment("営業部");
        Announcement personal = saved(7L, "EMPLOYEE");
        personal.setTargetEmployeeId(3L);
        personal.setTargetEmployeeName("鈴木一郎");
        when(announcementMapper.findVisibleById(6L, "all", 3L, "営業部")).thenReturn(dept);
        when(announcementMapper.findVisibleById(7L, "all", 3L, "営業部")).thenReturn(personal);

        assertThat(service.get("sub-3", 6L).getTargetLabel()).isEqualTo("営業部");
        assertThat(service.get("sub-3", 7L).getTargetLabel()).isEqualTo("鈴木一郎");
    }

    @Test
    void get_returnsTargetDepartmentAndEmployeeId_forEditForm() {
        //編集画面の、フォームに最初から入れる宛先。表示名(targetLabel)とは別に、部署名・社員IDそのものを返す
        Announcement all = saved(5L, "ALL");
        Announcement dept = saved(6L, "DEPARTMENT");
        dept.setTargetDepartment("営業部");
        Announcement personal = saved(7L, "EMPLOYEE");
        personal.setTargetEmployeeId(3L);
        personal.setTargetEmployeeName("鈴木一郎");
        when(announcementMapper.findVisibleById(5L, "all", 3L, "営業部")).thenReturn(all);
        when(announcementMapper.findVisibleById(6L, "all", 3L, "営業部")).thenReturn(dept);
        when(announcementMapper.findVisibleById(7L, "all", 3L, "営業部")).thenReturn(personal);

        AnnouncementDetail forAll = service.get("sub-3", 5L);
        AnnouncementDetail forDept = service.get("sub-3", 6L);
        AnnouncementDetail forPersonal = service.get("sub-3", 7L);

        assertThat(forAll.getTargetDepartment()).isNull();
        assertThat(forAll.getTargetEmployeeId()).isNull();
        assertThat(forDept.getTargetDepartment()).isEqualTo("営業部");
        assertThat(forDept.getTargetEmployeeId()).isNull();
        assertThat(forPersonal.getTargetDepartment()).isNull();
        assertThat(forPersonal.getTargetEmployeeId()).isEqualTo(3L);
    }

    @Test
    void get_throwsNotFound_whenInvisibleOrMissing() {
        //見られない・存在しない・削除済みは、どれもMapperがnullを返す → 404
        assertThatThrownBy(() -> service.get("sub-3", 99L)).isInstanceOf(NotFoundException.class);
    }

    // ---------- create ----------

    @Test
    void create_savesWithSenderAndReturnsDetail() {
        when(announcementMapper.insert(any(Announcement.class))).thenReturn(10L);
        Announcement stored = saved(10L, "ALL");
        when(announcementMapper.findById(10L)).thenReturn(stored);

        AnnouncementDetail detail = service.create("sub-2", request());

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementMapper).insert(captor.capture());
        Announcement inserted = captor.getValue();
        assertThat(inserted.getTitle()).isEqualTo("新しいお知らせ");
        assertThat(inserted.getBody()).isEqualTo("本文です");
        assertThat(inserted.getCategory()).isEqualTo("技術");
        assertThat(inserted.getTargetType()).isEqualTo("ALL");
        //差出人=投稿した人、差出人の部署=投稿した人の部署(人事部)をコピー
        assertThat(inserted.getSenderEmployeeId()).isEqualTo(2L);
        assertThat(inserted.getSenderDepartment()).isEqualTo("人事部");
        assertThat(detail.getId()).isEqualTo(10L);
        assertThat(detail.getCanEdit()).isTrue();
    }

    @Test
    void create_toDepartment_checksDepartmentExists() {
        AnnouncementRequest r = request();
        r.setTargetType("DEPARTMENT");
        r.setTargetDepartment("営業部");
        when(employeeMapper.countByDepartment("営業部")).thenReturn(2);
        when(announcementMapper.insert(any(Announcement.class))).thenReturn(10L);
        when(announcementMapper.findById(10L)).thenReturn(saved(10L, "DEPARTMENT"));

        service.create("sub-1", r);

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementMapper).insert(captor.capture());
        assertThat(captor.getValue().getTargetDepartment()).isEqualTo("営業部");
        assertThat(captor.getValue().getTargetEmployeeId()).isNull();
    }

    @Test
    void create_toEmployee_checksEmployeeExists() {
        AnnouncementRequest r = request();
        r.setTargetType("EMPLOYEE");
        r.setTargetEmployeeId(3L);
        when(announcementMapper.insert(any(Announcement.class))).thenReturn(10L);
        when(announcementMapper.findById(10L)).thenReturn(saved(10L, "EMPLOYEE"));

        service.create("sub-1", r);

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementMapper).insert(captor.capture());
        assertThat(captor.getValue().getTargetEmployeeId()).isEqualTo(3L);
        assertThat(captor.getValue().getTargetDepartment()).isNull();
    }

    @Test
    void create_throwsForbidden_forGeneralEmployee_evenIfRequestIsInvalid() {
        AnnouncementRequest invalid = request();
        invalid.setTitle(null);

        //権限チェックが先:権限がない人には、入力の誤りも教えない(400ではなく403)
        assertThatThrownBy(() -> service.create("sub-3", invalid)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.create("sub-3", request())).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(announcementMapper);
    }

    @Test
    void create_throwsValidation_whenTitleOrBodyIsInvalid() {
        AnnouncementRequest noTitle = request();
        noTitle.setTitle("  ");
        AnnouncementRequest longTitle = request();
        longTitle.setTitle("あ".repeat(101));
        AnnouncementRequest noBody = request();
        noBody.setBody(null);
        AnnouncementRequest longBody = request();
        longBody.setBody("い".repeat(2001));

        for (AnnouncementRequest r : List.of(noTitle, longTitle, noBody, longBody)) {
            assertThatThrownBy(() -> service.create("sub-1", r)).isInstanceOf(ValidationException.class);
        }
        verify(announcementMapper, never()).insert(any());
    }

    @Test
    void create_acceptsMaxLengthTitleAndBody() {
        AnnouncementRequest r = request();
        r.setTitle("あ".repeat(100));
        r.setBody("い".repeat(2000));
        when(announcementMapper.insert(any(Announcement.class))).thenReturn(10L);
        when(announcementMapper.findById(10L)).thenReturn(saved(10L, "ALL"));

        assertThatCode(() -> service.create("sub-1", r)).doesNotThrowAnyException();
    }

    @Test
    void create_throwsValidation_whenCategoryIsInvalid() {
        AnnouncementRequest unknown = request();
        unknown.setCategory("不明な種類");
        AnnouncementRequest none = request();
        none.setCategory(null);

        assertThatThrownBy(() -> service.create("sub-1", unknown)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create("sub-1", none)).isInstanceOf(ValidationException.class);
        verify(announcementMapper, never()).insert(any());
    }

    @Test
    void create_throwsValidation_whenTargetDoesNotMatchType() {
        AnnouncementRequest unknownType = request();
        unknownType.setTargetType("EVERYONE");
        AnnouncementRequest allWithDepartment = request();
        allWithDepartment.setTargetDepartment("開発部");
        AnnouncementRequest allWithEmployee = request();
        allWithEmployee.setTargetEmployeeId(3L);
        AnnouncementRequest departmentWithout = request();
        departmentWithout.setTargetType("DEPARTMENT");
        AnnouncementRequest departmentWithEmployee = request();
        departmentWithEmployee.setTargetType("DEPARTMENT");
        departmentWithEmployee.setTargetDepartment("営業部");
        departmentWithEmployee.setTargetEmployeeId(3L);
        AnnouncementRequest employeeWithout = request();
        employeeWithout.setTargetType("EMPLOYEE");
        AnnouncementRequest employeeWithDepartment = request();
        employeeWithDepartment.setTargetType("EMPLOYEE");
        employeeWithDepartment.setTargetEmployeeId(3L);
        employeeWithDepartment.setTargetDepartment("営業部");

        for (AnnouncementRequest r : List.of(unknownType, allWithDepartment, allWithEmployee, departmentWithout,
                departmentWithEmployee, employeeWithout, employeeWithDepartment)) {
            assertThatThrownBy(() -> service.create("sub-1", r)).isInstanceOf(ValidationException.class);
        }
        verify(announcementMapper, never()).insert(any());
    }

    @Test
    void create_throwsValidation_whenDepartmentOrEmployeeDoesNotExist() {
        AnnouncementRequest noDepartment = request();
        noDepartment.setTargetType("DEPARTMENT");
        noDepartment.setTargetDepartment("存在しない部");//countByDepartment は、モックの初期値0を返す
        AnnouncementRequest noEmployee = request();
        noEmployee.setTargetType("EMPLOYEE");
        noEmployee.setTargetEmployeeId(999L);//findDetailById(999) は、モックの初期値nullを返す

        assertThatThrownBy(() -> service.create("sub-1", noDepartment)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create("sub-1", noEmployee)).isInstanceOf(ValidationException.class);
        verify(announcementMapper, never()).insert(any());
    }

    @Test
    void create_throwsValidation_whenSenderHasNoDepartment() {
        when(employeeMapper.findDetailById(1L)).thenReturn(employee(1L, null));

        assertThatThrownBy(() -> service.create("sub-1", request())).isInstanceOf(ValidationException.class);
        verify(announcementMapper, never()).insert(any());
    }

    // ---------- update ----------

    @Test
    void update_changesAndReturnsDetail_withoutChangingSender() {
        AnnouncementRequest r = request();
        r.setTitle("直したタイトル");
        when(announcementMapper.findById(5L)).thenReturn(saved(5L, "ALL"));
        when(announcementMapper.update(eq(5L), any(Announcement.class))).thenReturn(1);

        AnnouncementDetail detail = service.update("sub-2", 5L, r);

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementMapper).update(eq(5L), captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("直したタイトル");
        //差出人は、変えない(Serviceは、差出人を渡さない)
        assertThat(captor.getValue().getSenderEmployeeId()).isNull();
        assertThat(captor.getValue().getSenderDepartment()).isNull();
        assertThat(detail.getId()).isEqualTo(5L);
    }

    @Test
    void update_canChangeTarget() {
        AnnouncementRequest r = request();
        r.setTargetType("DEPARTMENT");
        r.setTargetDepartment("営業部");
        when(employeeMapper.countByDepartment("営業部")).thenReturn(1);
        when(announcementMapper.findById(5L)).thenReturn(saved(5L, "ALL"));
        when(announcementMapper.update(eq(5L), any(Announcement.class))).thenReturn(1);

        service.update("sub-1", 5L, r);

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementMapper).update(eq(5L), captor.capture());
        assertThat(captor.getValue().getTargetType()).isEqualTo("DEPARTMENT");
        assertThat(captor.getValue().getTargetDepartment()).isEqualTo("営業部");
    }

    @Test
    void update_throwsForbidden_forGeneralEmployee_evenIfNotExists() {
        //権限チェックが、存在確認より先:権限がない人に、存在するかを教えない(404ではなく403)
        assertThatThrownBy(() -> service.update("sub-3", 99L, request())).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(announcementMapper);
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        assertThatThrownBy(() -> service.update("sub-2", 99L, request())).isInstanceOf(NotFoundException.class);
        verify(announcementMapper, never()).update(anyLong(), any());
    }

    @Test
    void update_throwsValidation_whenRequestIsInvalid() {
        AnnouncementRequest r = request();
        r.setCategory("不明な種類");
        when(announcementMapper.findById(5L)).thenReturn(saved(5L, "ALL"));

        assertThatThrownBy(() -> service.update("sub-2", 5L, r)).isInstanceOf(ValidationException.class);
        verify(announcementMapper, never()).update(anyLong(), any());
    }

    @Test
    void update_throwsNotFound_whenDeletedJustBeforeUpdate() {
        when(announcementMapper.findById(5L)).thenReturn(saved(5L, "ALL"));
        when(announcementMapper.update(eq(5L), any(Announcement.class))).thenReturn(0);

        assertThatThrownBy(() -> service.update("sub-2", 5L, request())).isInstanceOf(NotFoundException.class);
    }

    // ---------- delete(問5:自分で書く) ----------

    @Test
    void delete_succeeds_forSystemAdmin() {
        lenient().when(announcementMapper.findById(5L)).thenReturn(saved(5L, "ALL"));
        when(announcementMapper.softDeleteById(5L)).thenReturn(1);

        assertThatCode(() -> service.delete("sub-1", 5L)).doesNotThrowAnyException();

        verify(announcementMapper).softDeleteById(5L);
    }

    @Test
    void delete_throwsForbidden_forHrAdminAndGeneralEmployee() {
        //削除できるのは、システム管理者だけ(HR管理者も不可)
        assertThatThrownBy(() -> service.delete("sub-2", 5L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.delete("sub-3", 5L)).isInstanceOf(ForbiddenException.class);
        verify(announcementMapper, never()).softDeleteById(anyLong());
    }

    @Test
    void delete_throwsForbidden_beforeCheckingExistence() {
        //存在しないidでも、権限がなければ403(404にしない)
        assertThatThrownBy(() -> service.delete("sub-3", 99L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(announcementMapper);
    }

    @Test
    void delete_throwsNotFound_whenMissingOrAlreadyDeleted() {
        when(announcementMapper.softDeleteById(99L)).thenReturn(0);

        assertThatThrownBy(() -> service.delete("sub-1", 99L)).isInstanceOf(NotFoundException.class);
    }
}
