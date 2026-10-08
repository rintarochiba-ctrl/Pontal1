package com.example.pontal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.pontal.dto.EmployeeCreateRequest;
import com.example.pontal.dto.EmployeeDetail;
import com.example.pontal.dto.EmployeePage;
import com.example.pontal.dto.EmployeeSummary;
import com.example.pontal.dto.EmployeeUpdateRequest;
import com.example.pontal.dto.LoginEmployee;
import com.example.pontal.exception.ConflictException;
import com.example.pontal.exception.ExternalServiceException;
import com.example.pontal.exception.ForbiddenException;
import com.example.pontal.exception.NotFoundException;
import com.example.pontal.exception.UnauthorizedException;
import com.example.pontal.exception.ValidationException;
import com.example.pontal.mapper.EmployeeMapper;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDisableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

//EmployeeServiceの単体テスト。MapperとCognitoクライアントはモック(偽物)に差し替え、DBやAWSには繋がない
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;//偽のMapper:when(...)で返す値を決める、verify(...)で呼ばれたかを確認する

    @Mock
    private CognitoIdentityProviderClient cognitoClient;//偽のCognitoクライアント

    @InjectMocks
    private EmployeeService employeeService;//テスト対象。上の2つのモックがコンストラクタに注入される

    @BeforeEach
    void setUp() {
        //@Valueで注入される値はSpring無しでは入らないため、リフレクションで直接セットする
        ReflectionTestUtils.setField(employeeService, "userPoolId", "test-pool");
    }

    //テスト用のログイン社員を作る
    private LoginEmployee login(Long id, boolean isSystemAdmin) {
        LoginEmployee e = new LoginEmployee();
        e.setEmployeeId(id);
        e.setName("テスト");
        e.setIsSystemAdmin(isSystemAdmin);
        e.setIsHrAdmin(false);
        return e;
    }

    //JWTのsub "sub-admin" で、システム管理者(id=1)としてログインしている状態にする
    private void loginAsAdmin() {
        when(employeeMapper.findByCognitoSub("sub-admin")).thenReturn(login(1L, true));
    }

    //JWTのsub "sub-member" で、権限なしの社員(id=4)としてログインしている状態にする
    private void loginAsMember() {
        when(employeeMapper.findByCognitoSub("sub-member")).thenReturn(login(4L, false));
    }

    // ---------- getLoginEmployee (API003) ----------

    @Test
    void getLoginEmployee_returnsEmployee() {
        loginAsAdmin();

        LoginEmployee result = employeeService.getLoginEmployee("sub-admin");

        assertThat(result.getEmployeeId()).isEqualTo(1L);
    }

    @Test
    void getLoginEmployee_throws401_whenEmployeeNotFound() {
        when(employeeMapper.findByCognitoSub("unknown")).thenReturn(null);

        assertThatThrownBy(() -> employeeService.getLoginEmployee("unknown"))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---------- getDetail (API005) ----------

    @Test
    void getDetail_returnsDetail() {
        EmployeeDetail detail = new EmployeeDetail();
        detail.setId(2L);
        when(employeeMapper.findDetailById(2L)).thenReturn(detail);

        assertThat(employeeService.getDetail(2L).getId()).isEqualTo(2L);
    }

    @Test
    void getDetail_throws404_whenNotFound() {
        when(employeeMapper.findDetailById(999L)).thenReturn(null);

        assertThatThrownBy(() -> employeeService.getDetail(999L))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- search (API004) ----------

    @Test
    void search_throws400_whenPageIsNegative() {
        assertThatThrownBy(() -> employeeService.search(-1, 20, null))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(employeeMapper);//不正な値ならDBに問い合わせない
    }

    @Test
    void search_throws400_whenSizeIsZero() {
        assertThatThrownBy(() -> employeeService.search(0, 0, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void search_throws400_whenSizeIsOver100() {
        assertThatThrownBy(() -> employeeService.search(0, 101, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void search_passesOffsetAndKeywordToMapper() {
        when(employeeMapper.selectByKeyword("人事", 2, 2L)).thenReturn(List.of(new EmployeeSummary()));
        when(employeeMapper.countByKeyword("人事")).thenReturn(5);

        EmployeePage result = employeeService.search(1, 2, "人事");//page=1,size=2 → offset=2

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalCount()).isEqualTo(5);
    }

    @Test
    void search_firstPage_hasNextButNoPrev() {
        when(employeeMapper.selectByKeyword(any(), anyInt(), anyLong())).thenReturn(List.of());
        when(employeeMapper.countByKeyword(any())).thenReturn(5);

        EmployeePage result = employeeService.search(0, 2, null);//5件を2件ずつ → 0,1,2ページ

        assertThat(result.getNext()).isEqualTo(1);
        assertThat(result.getPrev()).isNull();
    }

    @Test
    void search_middlePage_hasNextAndPrev() {
        when(employeeMapper.selectByKeyword(any(), anyInt(), anyLong())).thenReturn(List.of());
        when(employeeMapper.countByKeyword(any())).thenReturn(5);

        EmployeePage result = employeeService.search(1, 2, null);

        assertThat(result.getNext()).isEqualTo(2);
        assertThat(result.getPrev()).isEqualTo(0);
    }

    @Test
    void search_lastPage_hasNoNext() {
        when(employeeMapper.selectByKeyword(any(), anyInt(), anyLong())).thenReturn(List.of());
        when(employeeMapper.countByKeyword(any())).thenReturn(5);

        EmployeePage result = employeeService.search(2, 2, null);

        assertThat(result.getNext()).isNull();
        assertThat(result.getPrev()).isEqualTo(1);
    }

    @Test
    void search_outOfRangePage_prevIsClampedToLastPage() {
        when(employeeMapper.selectByKeyword(any(), anyInt(), anyLong())).thenReturn(List.of());
        when(employeeMapper.countByKeyword(any())).thenReturn(5);

        EmployeePage result = employeeService.search(99, 2, null);//最終ページは2

        assertThat(result.getNext()).isNull();
        assertThat(result.getPrev()).isEqualTo(2);//98ではなく最終ページに丸められる
    }

    @Test
    void search_noResults_prevIsNotNegative() {
        when(employeeMapper.selectByKeyword(any(), anyInt(), anyLong())).thenReturn(List.of());
        when(employeeMapper.countByKeyword(any())).thenReturn(0);

        EmployeePage result = employeeService.search(3, 20, "zzz");

        assertThat(result.getPrev()).isEqualTo(0);
    }

    // ---------- update (API007) ----------

    @Test
    void update_throws403_whenNotSystemAdmin() {
        loginAsMember();

        assertThatThrownBy(() -> employeeService.update("sub-member", 3L, new EmployeeUpdateRequest()))
                .isInstanceOf(ForbiddenException.class);
        verify(employeeMapper, never()).update(anyLong(), any());//権限が無ければ更新SQLを呼ばない
    }

    @Test
    void update_throws404_whenTargetNotFound() {
        loginAsAdmin();
        when(employeeMapper.update(eq(999L), any())).thenReturn(0);

        assertThatThrownBy(() -> employeeService.update("sub-admin", 999L, new EmployeeUpdateRequest()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_returnsUpdatedDetail() {
        loginAsAdmin();
        EmployeeUpdateRequest request = new EmployeeUpdateRequest();
        EmployeeDetail detail = new EmployeeDetail();
        detail.setId(3L);
        when(employeeMapper.update(3L, request)).thenReturn(1);
        when(employeeMapper.findDetailById(3L)).thenReturn(detail);

        EmployeeDetail result = employeeService.update("sub-admin", 3L, request);

        assertThat(result.getId()).isEqualTo(3L);
    }

    // ---------- delete (API008) ----------

    @Test
    void delete_throws403_whenNotSystemAdmin() {
        loginAsMember();

        assertThatThrownBy(() -> employeeService.delete("sub-member", 3L))
                .isInstanceOf(ForbiddenException.class);
        verify(employeeMapper, never()).softDeleteById(anyLong());
        verifyNoInteractions(cognitoClient);//権限が無ければCognitoにも触れない
    }

    @Test
    void delete_throws403_whenDeletingSelf() {
        loginAsAdmin();//ログイン社員のidは1

        assertThatThrownBy(() -> employeeService.delete("sub-admin", 1L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("自分自身");
        verify(employeeMapper, never()).softDeleteById(anyLong());
        verifyNoInteractions(cognitoClient);
    }

    @Test
    void delete_throws404_whenTargetNotFound() {
        loginAsAdmin();
        when(employeeMapper.softDeleteById(999L)).thenReturn(0);

        assertThatThrownBy(() -> employeeService.delete("sub-admin", 999L))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(cognitoClient);//DBで削除できなければCognitoは無効化しない
    }

    @Test
    void delete_softDeletesTarget_andDisablesCognitoUser() {
        loginAsAdmin();
        when(employeeMapper.softDeleteById(3L)).thenReturn(1);
        when(employeeMapper.findCognitoSubById(3L)).thenReturn("target-sub");

        employeeService.delete("sub-admin", 3L);

        verify(employeeMapper).softDeleteById(3L);
        ArgumentCaptor<AdminDisableUserRequest> captor = ArgumentCaptor.forClass(AdminDisableUserRequest.class);
        verify(cognitoClient).adminDisableUser(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("target-sub");//削除した社員のsubで無効化している
        assertThat(captor.getValue().userPoolId()).isEqualTo("test-pool");
    }

    @Test
    void delete_succeeds_whenUserDoesNotExistInCognito() {
        loginAsAdmin();
        when(employeeMapper.softDeleteById(3L)).thenReturn(1);
        when(employeeMapper.findCognitoSubById(3L)).thenReturn("dummy-sub");
        //Cognitoに存在しないユーザー(ダミーデータなど)の場合。無効化する相手がいないので成功扱い
        when(cognitoClient.adminDisableUser(any(AdminDisableUserRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("not found").build());

        assertThatCode(() -> employeeService.delete("sub-admin", 3L)).doesNotThrowAnyException();
        verify(employeeMapper).softDeleteById(3L);
    }

    @Test
    void delete_throwsError_whenCognitoDisableFailsForOtherReasons() {
        loginAsAdmin();
        when(employeeMapper.softDeleteById(3L)).thenReturn(1);
        when(employeeMapper.findCognitoSubById(3L)).thenReturn("real-sub");
        //権限エラーや通信エラーなど、「ユーザーがいない」以外の失敗
        when(cognitoClient.adminDisableUser(any(AdminDisableUserRequest.class)))
                .thenThrow(SdkClientException.create("network error"));

        //成功扱いにせず、エラーで管理者に知らせる(DBの論理削除は済んでいる)
        assertThatThrownBy(() -> employeeService.delete("sub-admin", 3L))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("Cognitoの無効化に失敗しました");
        verify(employeeMapper).softDeleteById(3L);
    }

    // ---------- create (API006) ----------

    //テスト用の社員登録リクエスト
    private EmployeeCreateRequest createRequest() {
        EmployeeCreateRequest r = new EmployeeCreateRequest();
        r.setName("新入社員");
        r.setEmail("new@example.com");
        r.setInitialPassword("TempPass123!");
        r.setJoinDate(LocalDate.of(2026, 10, 1));
        r.setDepartment("開発部");
        return r;
    }

    //Cognitoが「ユーザーを作りました。subは new-sub です」と返した状況を作る
    private AdminCreateUserResponse cognitoCreated(String sub) {
        return AdminCreateUserResponse.builder()
                .user(UserType.builder()
                        .attributes(AttributeType.builder().name("sub").value(sub).build())
                        .build())
                .build();
    }

    @Test
    void create_throws403_whenNotSystemAdmin() {
        loginAsMember();

        assertThatThrownBy(() -> employeeService.create("sub-member", createRequest()))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(cognitoClient);//権限が無ければCognitoには触れない
    }

    @Test
    void create_throws409_whenEmailAlreadyInDb() {
        loginAsAdmin();
        when(employeeMapper.countByEmail("new@example.com")).thenReturn(1);

        assertThatThrownBy(() -> employeeService.create("sub-admin", createRequest()))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(cognitoClient);//DBで重複が分かればCognitoにゴミを作らない
    }

    @Test
    void create_throws409_whenCognitoSaysUserExists() {
        loginAsAdmin();
        when(employeeMapper.countByEmail(any())).thenReturn(0);
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenThrow(UsernameExistsException.builder().message("exists").build());

        assertThatThrownBy(() -> employeeService.create("sub-admin", createRequest()))
                .isInstanceOf(ConflictException.class);
        verify(employeeMapper, never()).insert(any(), any());//Cognitoで失敗したらDBには登録しない
    }

    @Test
    void create_throws400_whenPasswordViolatesPolicy() {
        loginAsAdmin();
        when(employeeMapper.countByEmail(any())).thenReturn(0);
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenThrow(InvalidPasswordException.builder().message("weak").build());

        assertThatThrownBy(() -> employeeService.create("sub-admin", createRequest()))
                .isInstanceOf(ValidationException.class);
        verify(employeeMapper, never()).insert(any(), any());
    }

    @Test
    void create_savesCognitoSubToDb_andReturnsDetail() {
        loginAsAdmin();
        EmployeeCreateRequest request = createRequest();
        EmployeeDetail detail = new EmployeeDetail();
        detail.setId(10L);
        when(employeeMapper.countByEmail("new@example.com")).thenReturn(0);
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class))).thenReturn(cognitoCreated("new-sub"));
        when(employeeMapper.insert(request, "new-sub")).thenReturn(10L);
        when(employeeMapper.findDetailById(10L)).thenReturn(detail);

        EmployeeDetail result = employeeService.create("sub-admin", request);

        assertThat(result.getId()).isEqualTo(10L);
        verify(employeeMapper).insert(request, "new-sub");//Cognitoが発行したsubがDBに渡っている
        verify(cognitoClient, never()).adminDeleteUser(any(AdminDeleteUserRequest.class));//成功時は巻き戻さない
    }

    @Test
    void create_rollsBackCognitoUser_whenDbInsertFails() {
        loginAsAdmin();
        EmployeeCreateRequest request = createRequest();
        when(employeeMapper.countByEmail(any())).thenReturn(0);
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class))).thenReturn(cognitoCreated("new-sub"));
        when(employeeMapper.insert(any(), any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> employeeService.create("sub-admin", request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");//元の例外はそのまま呼び出し元へ
        verify(cognitoClient).adminDeleteUser(any(AdminDeleteUserRequest.class));//作ったCognitoユーザーを消している
    }

    @Test
    void create_doesNotDeleteCognitoUser_whenFailingAfterInsert() {
        loginAsAdmin();
        EmployeeCreateRequest request = createRequest();
        when(employeeMapper.countByEmail(any())).thenReturn(0);
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class))).thenReturn(cognitoCreated("new-sub"));
        when(employeeMapper.insert(request, "new-sub")).thenReturn(10L);
        //INSERTは成功したが、登録した社員を読み直せなかった場合
        when(employeeMapper.findDetailById(10L)).thenReturn(null);

        assertThatThrownBy(() -> employeeService.create("sub-admin", request))
                .isInstanceOf(NotFoundException.class);
        //DBには行が残っているので、Cognitoのユーザーを消してはいけない(消すとDBだけ残って不整合になる)
        verify(cognitoClient, never()).adminDeleteUser(any(AdminDeleteUserRequest.class));
    }

    @Test
    void create_deletesCognitoUser_whenSubIsMissingInResponse() {
        loginAsAdmin();
        when(employeeMapper.countByEmail(any())).thenReturn(0);
        //Cognitoは、ユーザーを作ったが、応答にsubが入っていなかった場合
        AdminCreateUserResponse noSub = AdminCreateUserResponse.builder()
                .user(UserType.builder().attributes(AttributeType.builder().name("email").value("new@example.com").build()).build())
                .build();
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class))).thenReturn(noSub);

        assertThatThrownBy(() -> employeeService.create("sub-admin", createRequest()))
                .isInstanceOf(IllegalStateException.class);
        verify(employeeMapper, never()).insert(any(), any());//subが無いのでDBには登録しない
        verify(cognitoClient).adminDeleteUser(any(AdminDeleteUserRequest.class));//作ったCognitoユーザーは消す
    }

    @Test
    void create_keepsOriginalError_whenCognitoRollbackAlsoFails() {
        loginAsAdmin();
        EmployeeCreateRequest request = createRequest();
        when(employeeMapper.countByEmail(any())).thenReturn(0);
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class))).thenReturn(cognitoCreated("new-sub"));
        when(employeeMapper.insert(any(), any())).thenThrow(new RuntimeException("DB error"));
        //巻き戻し(Cognitoユーザーの削除)も失敗する場合
        when(cognitoClient.adminDeleteUser(any(AdminDeleteUserRequest.class)))
                .thenThrow(SdkClientException.create("network error"));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> employeeService.create("sub-admin", request));

        //外に出るのは、もともとのエラー(DBのエラー)。巻き戻しの失敗で、原因が分からなくならない
        assertThat(thrown).isInstanceOf(RuntimeException.class).hasMessage("DB error");
        //巻き戻しの失敗は、付け足されて残っている
        assertThat(thrown.getSuppressed()).hasSize(1);
        assertThat(thrown.getSuppressed()[0]).isInstanceOf(SdkClientException.class);
    }
}
