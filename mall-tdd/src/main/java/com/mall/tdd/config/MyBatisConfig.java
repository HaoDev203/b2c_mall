package com.mall.tdd.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Mapper 扫描配置。
 *
 * <p>★ 从启动类搬到这里的原因：{@code @WebMvcTest} 会把启动类上的注解一并应用，
 * 于是 Mapper 被注册、而 MyBatis 的 SqlSessionFactory 自动配置不在 Web 切片里，
 * 测试上下文报 {@code Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required}。
 *
 * <p>普通 {@code @Configuration} 不在 Web 切片的保留类型里（它只保留
 * {@code @Controller} / {@code @ControllerAdvice} / {@code WebMvcConfigurer} 等），
 * 所以放到这里后，Web 层测试不会再捎带加载 Mapper。
 */
@Configuration
@MapperScan("com.mall.tdd.mapper")
public class MyBatisConfig {
}
