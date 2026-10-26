package com.example.pontal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import java.time.LocalDateTime;
import java.util.List;

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
import com.example.pontal.dto.AnnouncementDetail;
import com.example.pontal.dto.AnnouncementItem;
import com.example.pontal.dto.AnnouncementPage;
import com.example.pontal.dto.AnnouncementRequest;
import com.example.pontal.exception.ForbiddenException;
import com.example.pontal.exception.NotFoundException;
import com.example.pontal.exception.ValidationException;
import com.example.pontal.service.AnnouncementService;

//AnnouncementControllerのテスト。Controllerと認証・例外変換だけを起動し、Serviceはモックにする
@WebMvcTest(controllers = AnnouncementController.class, properties = {
        "cognito.region=ap-southeast-2",
        "cognito.user-pool-id=test-pool" })
@Import(SecurityConfig.class)//本物の認証設定(未ログインなら401)を読み込む
class AnnouncementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnnouncementService announcementService;//偽のService

    //「Cognitoで認証済みで、subは sub-user」というリクエストを再現する
    private RequestPostProcessor user() {
        return jwt().jwt(j -> j.subject("sub-user"));
    }

    //正しい作成・編集リクエストのJSON(全員向け)
    private static final String VALID_BODY = """
            {"title":"メンテナンスのお知らせ","body":"夜間に行います","category":"技術",
             "isImportant":true,"targetType":"ALL"}
            """;

    private AnnouncementDetail detail(Long id) {
        AnnouncementDetail d = new AnnouncementDetail();
        d.setId(id);
        d.setTitle("メンテナンスのお知らせ");
        d.setBody("夜間に行います");
        d.setCategory("技術");
        d.setIsImportant(true);
        d.setSenderDepartment("開発部");
        d.setTargetType("ALL");
        d.setTargetLabel("全員");
        d.setCreatedAt(LocalDateTime.of(2026, 10, 20, 9, 0));
        d.setUpdatedAt(LocalDateTime.of(2026, 10, 21, 9, 0));
        d.setCanEdit(true);
        d.setCanDelete(false);
        return d;
    }

    // ---------- 認証(全API共通) ----------

    @Test
    void withoutToken_returns401_andDoesNotCallService() throws Exception {
        mockMvc.perform(get("/api/announcements")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/announcements/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/announcements").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/announcements/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/announcements/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/departments")).andExpect(status().isUnauthorized());
        verifyNoInteractions(announcementService);
    }

    // ---------- GET /api/announcements ----------

    @Test
    void list_usesDefaults_andPassesSubToService() throws Exception {
        when(announcementService.list("sub-user", "all", null, 0, 10)).thenReturn(new AnnouncementPage());

        mockMvc.perform(get("/api/announcements").with(user())).andExpect(status().isOk());

        //未指定なら scope=all、category なし、page=0、size=10
        verify(announcementService).list("sub-user", "all", null, 0, 10);
    }

    @Test
    void list_passesQueryParametersToService() throws Exception {
        when(announcementService.list("sub-user", "mine", "技術", 2, 5)).thenReturn(new AnnouncementPage());

        mockMvc.perform(get("/api/announcements").with(user())
                .param("scope", "mine").param("category", "技術").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(announcementService).list("sub-user", "mine", "技術", 2, 5);
    }

    @Test
    void list_returnsItemsAndPaging() throws Exception {
        AnnouncementItem item = new AnnouncementItem();
        item.setId(7L);
        item.setTitle("勉強会のお知らせ");
        item.setCategory("技術");
        item.setIsImportant(true);
        item.setSenderDepartment("開発部");
        item.setTargetType("DEPARTMENT");
        item.setTargetLabel("営業部");
        item.setCreatedAt(LocalDateTime.of(2026, 10, 20, 9, 0));
        AnnouncementPage page = new AnnouncementPage();
        page.setItems(List.of(item));
        page.setTotalCount(11);
        page.setNext(1);
        when(announcementService.list("sub-user", "all", null, 0, 10)).thenReturn(page);

        mockMvc.perform(get("/api/announcements").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].title").value("勉強会のお知らせ"))
                .andExpect(jsonPath("$.items[0].isImportant").value(true))
                .andExpect(jsonPath("$.items[0].senderDepartment").value("開発部"))
                .andExpect(jsonPath("$.items[0].targetType").value("DEPARTMENT"))
                .andExpect(jsonPath("$.items[0].targetLabel").value("営業部"))
                .andExpect(jsonPath("$.items[0].body").doesNotExist()) //一覧に本文は含めない
                .andExpect(jsonPath("$.totalCount").value(11))
                .andExpect(jsonPath("$.next").value(1))
                .andExpect(jsonPath("$.prev").doesNotExist());
    }

    @Test
    void list_returns400_whenServiceRejectsParameters() throws Exception {
        when(announcementService.list("sub-user", "abc", null, 0, 10))
                .thenThrow(new ValidationException("scopeが正しくありません"));

        mockMvc.perform(get("/api/announcements").with(user()).param("scope", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("scopeが正しくありません"));
    }

    @Test
    void list_returns403_whenManageIsNotAllowed() throws Exception {
        when(announcementService.list("sub-user", "manage", null, 0, 10))
                .thenThrow(new ForbiddenException("権限がありません"));

        mockMvc.perform(get("/api/announcements").with(user()).param("scope", "manage"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_returns400_whenPageIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/announcements").with(user()).param("page", "abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(announcementService);
    }

    // ---------- GET /api/announcements/{id} ----------

    @Test
    void detail_returnsDetailWithBodyAndFlags() throws Exception {
        when(announcementService.get("sub-user", 5L)).thenReturn(detail(5L));

        mockMvc.perform(get("/api/announcements/5").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.body").value("夜間に行います"))
                .andExpect(jsonPath("$.targetLabel").value("全員"))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.canDelete").value(false))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void detail_returnsTargetDepartmentAndEmployeeId_forEditForm() throws Exception {
        AnnouncementDetail personal = detail(7L);
        personal.setTargetType("EMPLOYEE");
        personal.setTargetLabel("鈴木一郎");
        personal.setTargetEmployeeId(3L);
        AnnouncementDetail dept = detail(8L);
        dept.setTargetType("DEPARTMENT");
        dept.setTargetLabel("営業部");
        dept.setTargetDepartment("営業部");
        when(announcementService.get("sub-user", 7L)).thenReturn(personal);
        when(announcementService.get("sub-user", 8L)).thenReturn(dept);

        mockMvc.perform(get("/api/announcements/7").with(user()))
                .andExpect(jsonPath("$.targetEmployeeId").value(3))
                .andExpect(jsonPath("$.targetDepartment").doesNotExist());
        mockMvc.perform(get("/api/announcements/8").with(user()))
                .andExpect(jsonPath("$.targetDepartment").value("営業部"))
                .andExpect(jsonPath("$.targetEmployeeId").doesNotExist());
    }

    @Test
    void detail_returns404_whenNotFoundOrInvisible() throws Exception {
        when(announcementService.get("sub-user", 99L)).thenThrow(new NotFoundException("お知らせが見つかりません"));

        mockMvc.perform(get("/api/announcements/99").with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("お知らせが見つかりません"));
    }

    // ---------- POST /api/announcements ----------

    @Test
    void create_returns201WithDetail_andPassesRequestToService() throws Exception {
        when(announcementService.create(eq("sub-user"), any(AnnouncementRequest.class))).thenReturn(detail(10L));

        mockMvc.perform(post("/api/announcements").with(user())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));

        ArgumentCaptor<AnnouncementRequest> captor = ArgumentCaptor.forClass(AnnouncementRequest.class);
        verify(announcementService).create(eq("sub-user"), captor.capture());
        AnnouncementRequest sent = captor.getValue();
        assertThat(sent.getTitle()).isEqualTo("メンテナンスのお知らせ");
        assertThat(sent.getBody()).isEqualTo("夜間に行います");
        assertThat(sent.getCategory()).isEqualTo("技術");
        assertThat(sent.getIsImportant()).isTrue();
        assertThat(sent.getTargetType()).isEqualTo("ALL");
    }

    @Test
    void create_passesDepartmentAndEmployeeTargets() throws Exception {
        when(announcementService.create(eq("sub-user"), any(AnnouncementRequest.class))).thenReturn(detail(10L));
        String department = """
                {"title":"t","body":"b","category":"技術","targetType":"DEPARTMENT","targetDepartment":"営業部"}
                """;
        String employee = """
                {"title":"t","body":"b","category":"技術","targetType":"EMPLOYEE","targetEmployeeId":3}
                """;

        mockMvc.perform(post("/api/announcements").with(user())
                .contentType(MediaType.APPLICATION_JSON).content(department)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/announcements").with(user())
                .contentType(MediaType.APPLICATION_JSON).content(employee)).andExpect(status().isCreated());

        ArgumentCaptor<AnnouncementRequest> captor = ArgumentCaptor.forClass(AnnouncementRequest.class);
        verify(announcementService, org.mockito.Mockito.times(2)).create(eq("sub-user"), captor.capture());
        assertThat(captor.getAllValues().get(0).getTargetDepartment()).isEqualTo("営業部");
        assertThat(captor.getAllValues().get(1).getTargetEmployeeId()).isEqualTo(3L);
    }

    @Test
    void create_returns403_whenNotAllowed() throws Exception {
        when(announcementService.create(eq("sub-user"), any(AnnouncementRequest.class)))
                .thenThrow(new ForbiddenException("お知らせを投稿する権限がありません"));

        mockMvc.perform(post("/api/announcements").with(user())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("お知らせを投稿する権限がありません"));
    }

    @Test
    void create_returns400_whenServiceRejectsInput() throws Exception {
        when(announcementService.create(eq("sub-user"), any(AnnouncementRequest.class)))
                .thenThrow(new ValidationException("種類が正しくありません"));

        mockMvc.perform(post("/api/announcements").with(user())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("種類が正しくありません"));
    }

    @Test
    void create_returns400_whenBodyIsNotJson() throws Exception {
        mockMvc.perform(post("/api/announcements").with(user())
                .contentType(MediaType.APPLICATION_JSON).content("{壊れたJSON"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(announcementService);
    }

    // ---------- PUT /api/announcements/{id} ----------

    @Test
    void update_returnsDetail_andPassesIdAndRequestToService() throws Exception {
        when(announcementService.update(eq("sub-user"), eq(5L), any(AnnouncementRequest.class)))
                .thenReturn(detail(5L));

        mockMvc.perform(put("/api/announcements/5").with(user())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));

        ArgumentCaptor<AnnouncementRequest> captor = ArgumentCaptor.forClass(AnnouncementRequest.class);
        verify(announcementService).update(eq("sub-user"), eq(5L), captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("メンテナンスのお知らせ");
    }

    @Test
    void update_returns403And404_fromService() throws Exception {
        when(announcementService.update(eq("sub-user"), eq(1L), any(AnnouncementRequest.class)))
                .thenThrow(new ForbiddenException("お知らせを編集する権限がありません"));
        when(announcementService.update(eq("sub-user"), eq(99L), any(AnnouncementRequest.class)))
                .thenThrow(new NotFoundException("お知らせが見つかりません"));

        mockMvc.perform(put("/api/announcements/1").with(user())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/announcements/99").with(user())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_returns400_whenServiceRejectsInput() throws Exception {
        when(announcementService.update(eq("sub-user"), eq(5L), any(AnnouncementRequest.class)))
                .thenThrow(new ValidationException("指定した部署が存在しません"));

        mockMvc.perform(put("/api/announcements/5").with(user())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns400_whenIdIsNotANumber() throws Exception {
        mockMvc.perform(put("/api/announcements/abc").with(user())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(announcementService);
    }

    // ---------- DELETE /api/announcements/{id} ----------

    @Test
    void delete_returns204WithoutBody_andPassesIdToService() throws Exception {
        mockMvc.perform(delete("/api/announcements/5").with(user()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(announcementService).delete("sub-user", 5L);
    }

    @Test
    void delete_returns403And404_fromService() throws Exception {
        org.mockito.Mockito.doThrow(new ForbiddenException("お知らせを削除する権限がありません"))
                .when(announcementService).delete("sub-user", 1L);
        org.mockito.Mockito.doThrow(new NotFoundException("お知らせが見つかりません"))
                .when(announcementService).delete("sub-user", 99L);

        mockMvc.perform(delete("/api/announcements/1").with(user())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/announcements/99").with(user())).andExpect(status().isNotFound());
    }

    // ---------- GET /api/departments ----------

    @Test
    void departments_returnsArrayOfNames() throws Exception {
        when(announcementService.getDepartments()).thenReturn(List.of("開発部", "人事部"));

        mockMvc.perform(get("/api/departments").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("開発部"))
                .andExpect(jsonPath("$[1]").value("人事部"))
                .andExpect(jsonPath("$.length()").value(2));
    }
}
