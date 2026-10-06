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

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    //ログインユーザー情報API(API003)
    @GetMapping("/api/me")
    public LoginEmployee me(@AuthenticationPrincipal Jwt jwt) {
        //subはCognitoユーザー固有のID。employee.cognito_subと突き合わせる
        return employeeService.getLoginEmployee(jwt.getSubject());
    }

    //社員詳細取得API(API005)
    @GetMapping("/api/employees/{id}")
    public EmployeeDetail detail(@PathVariable Long id) {
        return employeeService.getDetail(id);
    }

    //社員一覧取得API(API004)
    @GetMapping("/api/employees")
    public EmployeePage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return employeeService.search(page, size, keyword);
    }

    //社員登録API(API006)
    @PostMapping("/api/employees")
    public EmployeeDetail create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmployeeCreateRequest request) {
        return employeeService.create(jwt.getSubject(), request);
    }

    //社員編集API(API007)
    @PutMapping("/api/employees/{id}")
    public EmployeeDetail update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody EmployeeUpdateRequest request) {
        return employeeService.update(jwt.getSubject(), id, request);
    }

    //社員削除API(API008)
    @DeleteMapping("/api/employees/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)//204を返す
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        employeeService.delete(jwt.getSubject(), id);
    }

}
