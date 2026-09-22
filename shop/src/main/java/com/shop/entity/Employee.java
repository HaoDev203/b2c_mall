package com.shop.entity;

import lombok.Data;

@Data
public class Employee {
    private Long    id;
    private Long    shopId;
    private String  username;
    private String  password;        // BCrypt 哈希
    private String  avatarUrl;
    private String  lastLoginTime;   // ← String，不是 LocalDateTime
    private Integer loginCount;
    private Integer status;
    private String  createdAt;
    private String  updatedAt;
}
