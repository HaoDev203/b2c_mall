package com.shop.entity;

import lombok.Data;

/**
 * 前台买家（C 端用户）。
 * ★ 和 Employee 是两张独立的表 —— 买家不属于任何店铺，所以没有 shopId。
 * ★ 时间字段一律用 String，和 Employee / Message / Order 保持一致。
 */
@Data
public class User {
    private Long    id;
    private String  username;
    private String  password;        // BCrypt 密文
    private String  nickname;
    private String  phone;
    private String  avatarUrl;       // avatar_url
    private String  lastLoginTime;   // last_login_time
    private Integer loginCount;      // login_count
    private Integer status;          // 1-正常 0-禁用
    private String  createdAt;       // created_at
    private String  updatedAt;       // updated_at
}
