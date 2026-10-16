package com.mall.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * mall-auth：TDD 练习服务（登录功能）。
 *
 * <p>本模块与其他模块完全隔离，目的是用「红 → 绿 → 重构」的方式
 * 从零实现「用户名 + 密码登录」这条需求链路。
 *
 * <p>分层约定（比 shop 的 controller/service/mapper 更严格）：
 * <pre>
 *   adapter/web       入站：Controller + DTO
 *   application       编排：事务 / 发事件 / 调出站端口
 *   domain            领域：规则 + 状态，纯 Java，零框架依赖  ← TDD 主战场
 *   infrastructure    出站：MyBatis / Redis 等适配实现
 * </pre>
 */
@SpringBootApplication
public class MallAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallAuthApplication.class, args);
    }
}
