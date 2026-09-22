package com.shop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 登录成功后缓存进 Redis 的用户快照（不含密码） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long   id;
    private Long   shopId;
    private String username;
    private String avatarUrl;
}
