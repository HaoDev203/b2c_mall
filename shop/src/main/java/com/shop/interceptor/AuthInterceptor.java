package com.shop.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.Result;
import com.shop.common.UserContext;
import com.shop.dto.LoginUser;
import com.shop.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * 令牌校验拦截器 —— 对应 Spring MVC 的「责任链模式」：
 * 多个拦截器依次 preHandle，任一返回 false 即中断整条链。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER   = "Authorization";
    private static final String TOKEN_HEADER  = "token";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行跨域预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token   = resolveToken(request);
        LoginUser user = tokenService.get(token);

        if (user == null) {
            log.warn("【AuthInterceptor】拦截未授权请求：{} {}", request.getMethod(), request.getRequestURI());
            writeUnauthorized(response);
            return false;
        }

        UserContext.set(user, token);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // ★ 必须清理，否则 Tomcat 线程池复用会把上一个用户的身份带进下一个请求
        UserContext.clear();
    }

    /** 支持 Authorization: Bearer xxx，也兼容直接传 token 头 */
    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(authorization)) {
            return authorization.startsWith(BEARER_PREFIX)
                    ? authorization.substring(BEARER_PREFIX.length()).trim()
                    : authorization.trim();
        }
        return request.getHeader(TOKEN_HEADER);
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(Result.unauthorized("未登录或登录已过期")));
        }
    }
}
