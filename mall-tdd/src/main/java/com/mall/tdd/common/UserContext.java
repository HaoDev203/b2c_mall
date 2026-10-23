package com.mall.tdd.common;

/**
 * 当前请求的登录用户（ThreadLocal，随请求生灭）。
 *
 * <p>88 条里有 10 条以上是「只返回本店数据」「不可查看其他店铺」这类隔离断言，
 * 全部依赖这里取到 shopId / userId。它一错，隔离类断言会集体假绿。
 *
 * <p>★ 用完后必须 {@link #clear()}：Tomcat 线程复用，漏清理会把 A 店的身份
 * 带到 B 店的请求上。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> USER = new ThreadLocal<LoginUser>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        USER.set(user);
    }

    public static LoginUser getUser() {
        return USER.get();
    }

    /** 未登录返回 null，由 Service 决定是抛异常还是返回失败 */
    public static Long getUserId() {
        LoginUser u = USER.get();
        return u == null ? null : u.getId();
    }

    /** C 端买家为 null；B 端未登录也为 null —— 两者在 Service 里要分开判断 */
    public static Long getShopId() {
        LoginUser u = USER.get();
        return u == null ? null : u.getShopId();
    }

    /** 必须在请求结束时清理 */
    public static void clear() {
        USER.remove();
    }
}
