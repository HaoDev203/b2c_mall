package com.shop.account;

/** 用户类型常量。用常量而不是到处写字符串 —— 打错字编译期就能发现。 */
public final class UserType {

    /** 后台员工（tb_employee） */
    public static final String ADMIN = "admin";

    /** 前台买家（tb_user） */
    public static final String BUYER = "buyer";

    private UserType() {
    }
}
