package com.shop.common;

import com.shop.dto.LoginUser;

/** 当前请求的登录用户（ThreadLocal，随请求生灭） */
public final class UserContext {

    private static final ThreadLocal<LoginUser> USER  = new ThreadLocal<>();
    private static final ThreadLocal<String>    TOKEN = new ThreadLocal<>();

    private UserContext() {}

    public static void set(LoginUser user, String token) {
        USER.set(user);
        TOKEN.set(token);
    }

    public static LoginUser getUser()  { return USER.get(); }
    public static String    getToken() { return TOKEN.get(); }

    public static Long getEmployeeId() {
        LoginUser u = USER.get();
        return u == null ? null : u.getId();
    }

    public static Long getShopId() {
        LoginUser u = USER.get();
        return u == null ? null : u.getShopId();
    }

    /** ★ 必须在请求结束时清理，否则 Tomcat 线程复用会串号 */
    public static void clear() {
        USER.remove();
        TOKEN.remove();
    }
}
