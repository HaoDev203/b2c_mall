package com.shop.dto;

import lombok.Data;

/** 前台买家注册请求体 */
@Data
public class MallRegisterDTO {
    private String username;     // 账号（必填）
    private String password;     // 密码（明文，进来后立刻加密）
    private String nickname;     // 昵称（可空，空了自动生成）
    private String phone;        // 手机号（可空）
}
