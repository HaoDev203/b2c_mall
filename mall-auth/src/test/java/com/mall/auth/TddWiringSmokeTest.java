package com.mall.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD 基建自检（canary）。
 *
 * <p>2026-10-13 已验证：本类曾额外包含一个 {@code fail(...)} 的用例，
 * 执行 mvn test 得到 {@code Tests run: 2, Failures: 1} —— 证明 surefire 真的在跑 JUnit 5，
 * 而不是「静默跳过还报 BUILD SUCCESS」。验证完成后删除了那个用例。
 *
 * <p>保留这个文件作为常驻哨兵：以后如果它突然不执行了，说明构建配置被改坏了。
 */
@DisplayName("TDD 基建自检")
class TddWiringSmokeTest {

    @Test
    @DisplayName("JUnit 5 + AssertJ 可正常执行")
    void should_pass_when_infrastructure_is_wired() {
        assertThat("mall-auth").startsWith("mall");
    }
}
