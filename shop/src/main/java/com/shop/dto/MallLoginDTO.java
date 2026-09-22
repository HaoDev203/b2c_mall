package com.shop.dto;

import lombok.Data;

/** 前台买家登录请求体 */
@Data
public class MallLoginDTO {
    private String username;
    private String password;
}
