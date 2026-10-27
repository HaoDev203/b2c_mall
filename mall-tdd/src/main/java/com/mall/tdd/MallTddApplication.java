package com.mall.tdd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 需求 v1.1 的验收模块（88 条）。
 * 与 shop 模块物理隔离：不引用 shop 的任何类，不共用 shop.db。
 *
 * <p>★ {@code @MapperScan} 刻意不写在这里，而是放在 {@link com.mall.tdd.config.MyBatisConfig}。
 * 写在这里会被 {@code @WebMvcTest} 一并应用：Mapper 被注册，但 MyBatis 的
 * SqlSessionFactory 自动配置被 Web 切片排除，导致测试上下文直接起不来
 * （{@code Property 'sqlSessionFactory' is required}）。
 */
@SpringBootApplication
public class MallTddApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallTddApplication.class, args);
    }
}
