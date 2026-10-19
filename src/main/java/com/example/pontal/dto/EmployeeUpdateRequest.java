package com.example.pontal.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

//社員編集APIのリクエスト PUT /api/employees/{id} で使用(全項目を丸ごと置き換える)
public class EmployeeUpdateRequest {
    @NotBlank
    @Size(max = 50)
    private String name;

    @Size(max = 10)
    private String gender;

    @Min(0)
    @Max(150)
    private Integer age;

    @Size(max = 50)
    private String birthplace;

    @NotNull //DBのjoin_dateがNOT NULLのため必須
    private LocalDate joinDate;

    @Size(max = 50)
    private String department;

    @Size(max = 50)
    private String position;

    @Size(max = 255)
    private String image; //S3オブジェクトキー、DBのimage_urlに保存

    private String bio;

    @Size(max = 255)
    private String hobby;

    private String selfQa;

    @NotNull //未指定でfalse扱いになり権限が外れるのを防ぐ
    private Boolean isSystemAdmin;

    @NotNull
    private Boolean isHrAdmin;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getBirthplace() { return birthplace; }
    public void setBirthplace(String birthplace) { this.birthplace = birthplace; }

    public LocalDate getJoinDate() { return joinDate; }
    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getHobby() { return hobby; }
    public void setHobby(String hobby) { this.hobby = hobby; }

    public String getSelfQa() { return selfQa; }
    public void setSelfQa(String selfQa) { this.selfQa = selfQa; }

    public Boolean getIsSystemAdmin() { return isSystemAdmin; }
    public void setIsSystemAdmin(Boolean isSystemAdmin) { this.isSystemAdmin = isSystemAdmin; }

    public Boolean getIsHrAdmin() { return isHrAdmin; }
    public void setIsHrAdmin(Boolean isHrAdmin) { this.isHrAdmin = isHrAdmin; }
}
