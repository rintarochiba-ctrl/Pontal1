package com.example.pontal.controller;

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

import com.example.pontal.dto.EmployeeCreateRequest;
import com.example.pontal.dto.EmployeeDetail;
import com.example.pontal.dto.EmployeePage;
import com.example.pontal.dto.EmployeeUpdateRequest;
import com.example.pontal.dto.LoginEmployee;
import com.example.pontal.service.EmployeeService;

import jakarta.validation.Valid;

//社員関係のAPIを提供するコントローラー
@RestController
public class EmployeeController {
    //EmployeeServiceをDI注入するためのフィールド
    private final EmployeeService employeeService;
    //コンストラクタでDI注入
    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    //ログインユーザー情報取得API(API003)
    //  GET /api/me → 4項目(employeeId, name, isSystemAdmin, isHrAdmin)を取得
    @GetMapping("/api/me")
    // Springが検証済みのJWTを渡す
    public LoginEmployee me(@AuthenticationPrincipal Jwt jwt) {
        //subはCognitoのJWTに含まれるユーザー固有のクレーム。employee.cognito_subと紐づく
        return employeeService.getLoginEmployee(jwt.getSubject());
    }

    //社員詳細取得API(API005)
    //  GET /api/employees/{id} → 社員詳細15項目を取得
    @GetMapping("/api/employees/{id}")
    public EmployeeDetail detail(@PathVariable Long id) {
        return employeeService.getDetail(id);
    }

    //社員一覧取得API(API004)
    // GET /api/employees?page=0&size=20&keyword=xxx → 社員一覧(items,totalCount,next,prev)を取得
    @GetMapping("/api/employees")
    public EmployeePage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return employeeService.list(page, size, keyword);
    }

    //社員登録API(API006)
    // POST /api/employees → 社員詳細15項目を新規登録後に返す(管理者のみ)
    @PostMapping("/api/employees")
    public EmployeeDetail create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmployeeCreateRequest request) {
        return employeeService.create(jwt.getSubject(), request);
    }

    //社員編集API(API007)
    // PUT /api/employees/{id} → 社員詳細13/15項目を編集する(管理者のみ)
    @PutMapping("/api/employees/{id}")
    public EmployeeDetail update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody EmployeeUpdateRequest request) {
        return employeeService.update(jwt.getSubject(), id, request);
    }

    //社員削除API(API008)
    // DELETE /api/employees/{id} → 社員を論理削除し、Cognito側も無効化(DTO不要、成功+Bodyなし 204を返す)(管理者のみ)
    @DeleteMapping("/api/employees/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)//204を返す
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        employeeService.delete(jwt.getSubject(), id);
    }

}
