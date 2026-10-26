package com.example.pontal.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.pontal.dto.AnnouncementDetail;
import com.example.pontal.dto.AnnouncementPage;
import com.example.pontal.dto.AnnouncementRequest;
import com.example.pontal.service.AnnouncementService;

//お知らせ関係のAPIを提供するコントローラー
//入力チェックは、権限チェックのあとに行う決まりのため、@ValidではなくServiceで行う
@RestController
public class AnnouncementController {
    //AnnouncementServiceをDI注入するためのフィールド
    private final AnnouncementService announcementService;

    //コンストラクタでDI注入
    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    //お知らせ一覧取得API(API018)
    //  GET /api/announcements?scope=all&category=技術&page=0&size=10 → 一覧(items,totalCount,next,prev)
    //  scope: all(既定)=見られるもの全部 / mine=自分宛て / everyone=全員向け / manage=全件(管理者のみ)
    @GetMapping("/api/announcements")
    public AnnouncementPage list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return announcementService.list(jwt.getSubject(), scope, category, page, size);
    }

    //お知らせ詳細取得API(API019)
    //  GET /api/announcements/{id} → 詳細(本文、canEdit、canDelete を含む)。見られない場合は404
    @GetMapping("/api/announcements/{id}")
    public AnnouncementDetail detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return announcementService.get(jwt.getSubject(), id);
    }

    //お知らせ作成API(API020)
    //  POST /api/announcements → 作成したお知らせの詳細を201で返す(システム管理者・HR管理者のみ)
    @PostMapping("/api/announcements")
    @ResponseStatus(HttpStatus.CREATED) //201を返す
    public AnnouncementDetail create(@AuthenticationPrincipal Jwt jwt, @RequestBody AnnouncementRequest request) {
        return announcementService.create(jwt.getSubject(), request);
    }

    //お知らせ編集API(API021)
    //  PUT /api/announcements/{id} → 全項目を置き換えて、編集後の詳細を返す(システム管理者・HR管理者のみ)
    @PutMapping("/api/announcements/{id}")
    public AnnouncementDetail update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestBody AnnouncementRequest request) {
        return announcementService.update(jwt.getSubject(), id, request);
    }

    //お知らせ削除API(API022)
    //  DELETE /api/announcements/{id} → 論理削除して、成功+Bodyなしの204を返す(システム管理者のみ)
    @DeleteMapping("/api/announcements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT) //204を返す
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        announcementService.delete(jwt.getSubject(), id);
    }

    //部署の選択肢取得API(API023)
    //  GET /api/departments → お知らせの宛先に選べる部署名の配列(ログイン済みなら誰でも)
    @GetMapping("/api/departments")
    public List<String> departments() {
        return announcementService.getDepartments();
    }
}
