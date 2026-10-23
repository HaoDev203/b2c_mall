package com.mall.tdd;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 需求 v1.1 的验收模块（88 条）。
 * 与 shop 模块物理隔离：不引用 shop 的任何类，不共用 shop.db。
 */
@MapperScan("com.mall.tdd.mapper")
@SpringBootApplication
public class MallTddApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallTddApplication.class, args);
    }
}
