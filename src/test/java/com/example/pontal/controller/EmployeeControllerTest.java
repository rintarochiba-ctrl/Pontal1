package com.example.pontal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.example.pontal.config.SecurityConfig;
import com.example.pontal.dto.EmployeeCreateRequest;
import com.example.pontal.dto.EmployeeDetail;
import com.example.pontal.dto.EmployeePage;
import com.example.pontal.dto.LoginEmployee;
import com.example.pontal.exception.ConflictException;
import com.example.pontal.exception.ForbiddenException;
import com.example.pontal.exception.NotFoundException;
import com.example.pontal.exception.ValidationException;
import com.example.pontal.service.EmployeeService;

//EmployeeControllerのテスト。Controllerと認証・入力検証・例外変換だけを起動し、Serviceはモックにする
@WebMvcTest(controllers = EmployeeController.class, properties = {
        "cognito.region=ap-southeast-2",
        "cognito.user-pool-id=test-pool" })
@Import(SecurityConfig.class)//本物の認証設定(未ログインなら401)を読み込む
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;//サーバーを立てずにHTTPリクエストを模擬するクライアント

    @MockBean
    private EmployeeService employeeService;//偽のService

    //「Cognitoで認証済みで、subは sub-admin」というリクエストを再現する(実際のJWT検証は行わない)
    private RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("sub-admin"));
    }

    // 正しい社員編集リクエストのJSON
    private static final String VALID_UPDATE = """
            {"name":"鈴木一郎","joinDate":"2021-10-01","department":"営業部",
             "isSystemAdmin":false,"isHrAdmin":false}
            """;

    // 正しい社員登録リクエストのJSON
    private static final String VALID_CREATE = """
            {"name":"新入社員","email":"new@example.com","initialPassword":"TempPass123!",
             "joinDate":"2026-10-01","department":"開発部"}
            """;

    // ---------- 認証(全API共通) ----------

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.result").value(false));
    }

    // ---------- GET /api/me ----------

    @Test
    void me_returnsLoginEmployee_andPassesSubToService() throws Exception {
        LoginEmployee login = new LoginEmployee();
        login.setEmployeeId(1L);
        login.setName("山田太郎");
        login.setIsSystemAdmin(true);
        when(employeeService.getLoginEmployee("sub-admin")).thenReturn(login);

        mockMvc.perform(get("/api/me").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(1))
                .andExpect(jsonPath("$.name").value("山田太郎"))
                .andExpect(jsonPath("$.isSystemAdmin").value(true))
                .andExpect(jsonPath("$.isHrAdmin").value(false));
    }

    // ---------- GET /api/employees ----------

    @Test
    void list_usesDefaultPageAndSize() throws Exception {
        when(employeeService.search(0, 20, null)).thenReturn(new EmployeePage());

        mockMvc.perform(get("/api/employees").with(admin()))
                .andExpect(status().isOk());
        verify(employeeService).search(0, 20, null);//未指定ならpage=0,size=20
    }

    @Test
    void list_passesQueryParametersToService() throws Exception {
        when(employeeService.search(1, 2, "人事")).thenReturn(new EmployeePage());

        mockMvc.perform(get("/api/employees").with(admin())
                .param("page", "1").param("size", "2").param("keyword", "人事"))
                .andExpect(status().isOk());
        verify(employeeService).search(1, 2, "人事");
    }

    @Test
    void list_returns400_whenServiceRejectsParameters() throws Exception {
        when(employeeService.search(0, 0, null)).thenThrow(new ValidationException("sizeは1〜100で指定してください"));

        mockMvc.perform(get("/api/employees").with(admin()).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sizeは1〜100で指定してください"));
    }

    @Test
    void list_returns400_whenPageIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/employees").with(admin()).param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    // ---------- GET /api/employees/{id} ----------

    @Test
    void detail_returnsEmployee() throws Exception {
        EmployeeDetail detail = new EmployeeDetail();
        detail.setId(2L);
        detail.setName("佐藤花子");
        detail.setIsHrAdmin(true);
        when(employeeService.getDetail(2L)).thenReturn(detail);

        mockMvc.perform(get("/api/employees/2").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("佐藤花子"))
                .andExpect(jsonPath("$.isHrAdmin").value(true));
    }

    @Test
    void detail_returns404_whenNotFound() throws Exception {
        when(employeeService.getDetail(999L)).thenThrow(new NotFoundException("社員が見つかりません"));

        mockMvc.perform(get("/api/employees/999").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.result").value(false))
                .andExpect(jsonPath("$.message").value("社員が見つかりません"));
    }

    @Test
    void detail_returns400_whenIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/employees/abc").with(admin()))
                .andExpect(status().isBadRequest());
    }

    // ---------- POST /api/employees ----------

    @Test
    void create_returns200_andPassesSubAndBodyToService() throws Exception {
        EmployeeDetail created = new EmployeeDetail();
        created.setId(10L);
        when(employeeService.create(eq("sub-admin"), any())).thenReturn(created);

        mockMvc.perform(post("/api/employees").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        ArgumentCaptor<EmployeeCreateRequest> captor = ArgumentCaptor.forClass(EmployeeCreateRequest.class);
        verify(employeeService).create(eq("sub-admin"), captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@example.com");
        //権限フラグを省略したらfalse(権限なし)になる
        assertThat(captor.getValue().getIsSystemAdmin()).isFalse();
        assertThat(captor.getValue().getIsHrAdmin()).isFalse();
    }

    @Test
    void create_returns400_whenEmailIsInvalid() throws Exception {
        String body = VALID_CREATE.replace("new@example.com", "not-an-email");

        mockMvc.perform(post("/api/employees").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("email")));
        verifyNoInteractions(employeeService);//入力が不正ならServiceまで届かない
    }

    @Test
    void create_returns400_whenInitialPasswordIsMissing() throws Exception {
        String body = """
                {"name":"新入社員","email":"new@example.com","joinDate":"2026-10-01","department":"開発部"}
                """;

        mockMvc.perform(post("/api/employees").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("initialPassword")));
    }

    @Test
    void create_returns400_whenDepartmentIsMissing() throws Exception {
        String body = """
                {"name":"新入社員","email":"new@example.com","initialPassword":"TempPass123!","joinDate":"2026-10-01"}
                """;

        mockMvc.perform(post("/api/employees").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("department")));
    }

    @Test
    void create_returns403_whenServiceRejects() throws Exception {
        when(employeeService.create(anyString(), any())).thenThrow(new ForbiddenException("社員を登録する権限がありません"));

        mockMvc.perform(post("/api/employees").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("社員を登録する権限がありません"));
    }

    @Test
    void create_returns409_whenEmailAlreadyExists() throws Exception {
        when(employeeService.create(anyString(), any()))
                .thenThrow(new ConflictException("このメールアドレスは既に登録されています"));

        mockMvc.perform(post("/api/employees").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isConflict());
    }

    // ---------- PUT /api/employees/{id} ----------

    @Test
    void update_returns200_andPassesArgumentsToService() throws Exception {
        EmployeeDetail updated = new EmployeeDetail();
        updated.setId(3L);
        updated.setName("鈴木一郎");
        when(employeeService.update(eq("sub-admin"), eq(3L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/employees/3").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("鈴木一郎"));
    }

    @Test
    void update_returns400_whenNameIsBlank() throws Exception {
        String body = VALID_UPDATE.replace("鈴木一郎", "");

        mockMvc.perform(put("/api/employees/3").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name")));
        verifyNoInteractions(employeeService);
    }

    @Test
    void update_returns400_whenNameIsTooLong() throws Exception {
        String body = VALID_UPDATE.replace("鈴木一郎", "あ".repeat(51));//上限は50文字

        mockMvc.perform(put("/api/employees/3").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns400_whenJoinDateIsMissing() throws Exception {
        String body = """
                {"name":"鈴木一郎","isSystemAdmin":false,"isHrAdmin":false}
                """;

        mockMvc.perform(put("/api/employees/3").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("joinDate")));
    }

    @Test
    void update_returns400_whenAdminFlagIsMissing() throws Exception {
        //フラグを省略すると権限が黙って外れてしまうため、必須にしている
        String body = """
                {"name":"鈴木一郎","joinDate":"2021-10-01","isHrAdmin":false}
                """;

        mockMvc.perform(put("/api/employees/3").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("isSystemAdmin")));
    }

    @Test
    void update_returns400_whenAgeIsOutOfRange() throws Exception {
        String body = VALID_UPDATE.replace("\"department\"", "\"age\":151,\"department\"");

        mockMvc.perform(put("/api/employees/3").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns403_whenServiceRejects() throws Exception {
        when(employeeService.update(anyString(), anyLong(), any()))
                .thenThrow(new ForbiddenException("社員を編集する権限がありません"));

        mockMvc.perform(put("/api/employees/3").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_returns404_whenTargetNotFound() throws Exception {
        when(employeeService.update(anyString(), eq(999L), any()))
                .thenThrow(new NotFoundException("社員が見つかりません"));

        mockMvc.perform(put("/api/employees/999").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isNotFound());
    }

    // ---------- DELETE /api/employees/{id} ----------

    @Test
    void delete_returns204_withEmptyBody() throws Exception {
        mockMvc.perform(delete("/api/employees/3").with(admin()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(employeeService).delete("sub-admin", 3L);
    }

    @Test
    void delete_withoutToken_returns401_andDoesNotCallService() throws Exception {
        mockMvc.perform(delete("/api/employees/3"))
                .andExpect(status().isUnauthorized());
        verify(employeeService, never()).delete(anyString(), anyLong());
    }

    @Test
    void delete_returns403_whenServiceRejects() throws Exception {
        org.mockito.Mockito.doThrow(new ForbiddenException("自分自身は削除できません"))
                .when(employeeService).delete("sub-admin", 1L);

        mockMvc.perform(delete("/api/employees/1").with(admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("自分自身は削除できません"));
    }

    @Test
    void delete_returns404_whenTargetNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new NotFoundException("社員が見つかりません"))
                .when(employeeService).delete("sub-admin", 999L);

        mockMvc.perform(delete("/api/employees/999").with(admin()))
                .andExpect(status().isNotFound());
    }
}
