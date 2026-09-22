package com.shop.entity;

import lombok.Data;

@Data
public class Shop {
    private Long    id;
    private String  shopName;        // shop_name
    private String  adminAccount;    // admin_account
    private String  adminPassword;   // admin_password，存 BCrypt 哈希
    private String  logoUrl;         // logo_url
    private Integer status;          // 1-正常 0-禁用
    private String  createdAt;       // created_at
    private String  updatedAt;       // updated_at
}
