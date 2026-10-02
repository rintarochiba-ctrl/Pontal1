package com.example.pontal.dto;

//社員一人の詳細情報を持つ GET詳細,POST登録,PUT編集で使用
public class EmployeeDetail {
    private Long id;
    private String name;
    private String email;
    private String department;
    private String position;
    private java.time.LocalDate joinDate;
    private String gender;
    private Integer age;
    private String birthplace;
    private String imageUrl;
    private String bio;
    private String hobby;
    private String selfQa;
    private boolean isSystemAdmin;
    private boolean isHrAdmin;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public java.time.LocalDate getJoinDate() { return joinDate; }
    public void setJoinDate(java.time.LocalDate joinDate) { this.joinDate = joinDate; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getBirthplace() { return birthplace; }
    public void setBirthplace(String birthplace) { this.birthplace = birthplace; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getHobby() { return hobby; }
    public void setHobby(String hobby) { this.hobby = hobby; }

    public String getSelfQa() { return selfQa; }
    public void setSelfQa(String selfQa) { this.selfQa = selfQa; }

    public boolean getIsSystemAdmin() { return isSystemAdmin; }
    public void setIsSystemAdmin(boolean isSystemAdmin) { this.isSystemAdmin = isSystemAdmin; }

    public boolean getIsHrAdmin() { return isHrAdmin; }
    public void setIsHrAdmin(boolean isHrAdmin) { this.isHrAdmin = isHrAdmin; }
}
