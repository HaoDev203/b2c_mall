package com.mall.tdd.common;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 把请求里的身份写进 {@link UserContext}，请求结束时清掉。
 *
 * <p>★★ <b>这不是登录，是身份透传。</b> 当前直接从请求头读 {@code X-User-Id} /
 * {@code X-Shop-Id}，任何人手工加一个头就能冒充任意用户。
 * 之所以先这么做：{@code mall-auth} 的 87 项还是 0/87，没有 token 体系可用；
 * 而 Service 层 20 多处 {@code UserContext.getUserId()} 需要一个填充来源，
 * 否则整个模块对外永远是「未登录」。
 *
 * <p>接入登录后改动只在这一处：把读请求头换成解析 {@code Authorization: Bearer <token>}，
 * 其余代码不用动 —— 这也是当初把身份收敛到 UserContext 的目的。
 *
 * <p>★ 没有身份头时<b>放行不拦截</b>：让请求进到 Service，由 Service 返回
 * {@code Result.unauthorized(401)}。如果在这里直接 401，Service 的未登录分支
 * 就永远跑不到，那几条测试也就成了摆设。
 */
public class UserContextInterceptor implements HandlerInterceptor {

    public static final String USER_HEADER = "X-User-Id";
    public static final String SHOP_HEADER = "X-Shop-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = trimToNull(request.getHeader(USER_HEADER));
        String shopId = trimToNull(request.getHeader(SHOP_HEADER));
        if (userId == null && shopId == null) {
            return true;
        }

        // LoginUser 只有全参构造（@AllArgsConstructor），name 是展示用字段，这里没有来源
        UserContext.set(new LoginUser(parseLong(userId), parseLong(shopId), null));
        return true;
    }

    /**
     * ★★ 必须清空。Tomcat 的工作线程是复用的，漏了这一步，
     * 上一个请求的身份会留在 ThreadLocal 里，被下一个请求读到 ——
     * <b>表现是全站串号，而且手工点页面 100% 发现不了</b>（只在并发或特定线程调度下显现）。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    /** 非法值当作没传，不抛异常：一个畸形请求头不该导致 500 */
    private Long parseLong(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
