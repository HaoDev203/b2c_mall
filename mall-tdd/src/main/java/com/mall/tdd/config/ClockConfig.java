package com.mall.tdd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 时钟 Bean。
 *
 * <p>★ 为什么值得单独一个类：订单查询的「今日 / 昨日 / 近 7 天 / 近 30 天」
 * 全部依赖「今天」，Service 里直接写 {@code LocalDate.now()} 就没法测 ——
 * 断言会在某一天突然变红。注入 {@link Clock} 之后，测试用
 * {@code Clock.fixed(...)} 把「今天」钉死，生产用系统时钟。
 *
 * <p>★ 现在 {@code OrderQueryService} 与 {@code DashboardService} 都通过构造器注入它：
 * 两者都依赖「今天」，一旦直接写 {@code LocalDate.now()}，测试断言就会在
 * 零点前后跟 Service 差一天，变成偶发红。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
