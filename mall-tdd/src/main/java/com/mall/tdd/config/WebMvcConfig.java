package com.mall.tdd.config;

import com.mall.tdd.common.UserContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册身份拦截器。
 *
 * <p>拦截器用 {@code new} 而不是 {@code @Component}：它无状态（只读写 ThreadLocal），
 * 实例化出来即可，省掉一个 Bean；也让 {@code @WebMvcTest} 不需要额外配置就能加载。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserContextInterceptor()).addPathPatterns("/api/**");
    }
}
