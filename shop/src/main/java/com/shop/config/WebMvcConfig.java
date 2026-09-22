package com.shop.config;

import com.shop.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // ===== 后台：登录注册本身不能拦 =====
                        // ★ 剥掉 /shop 前缀后的真实路径。写 /shop/auth/login 会导致登录接口自己被拦
                        "/auth/login",
                        "/auth/register",

                        // ===== 前台：游客可访问 =====
                        "/mall/auth/login",
                        "/mall/auth/register",
                        "/mall/product/**",       // 商品列表 / 详情，免登录
                        "/mall/category/**",      // 分类导航，免登录
                        // ★ 阶段 6 的 /mall/cart/** 不加进来 —— 购物车必须登录才能看

                        // ★ 不放行 /error 会导致异常被转发后又拦成 401，极难排查
                        "/error"
                );
    }
}
