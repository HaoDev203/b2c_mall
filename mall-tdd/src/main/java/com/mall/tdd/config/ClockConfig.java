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
 * <p>本期 Controller 还没写、测试也不启动 Spring 容器，所以这个 Bean 暂时
 * 只有「将来装配时不会因为缺依赖而报错」的作用。但它是 Service 能用
 * 构造器注入 Clock 的前提，缺了它 {@code @RequiredArgsConstructor} 就装不起来。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
