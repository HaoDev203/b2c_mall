package com.shop.dto;

import lombok.Data;

@Data
public class RegisterDTO {
    private String shopName;     // 店铺名
    private String username;     // 管理员账号
    private String password;     // 管理员密码（明文，进来后立刻加密）
}
