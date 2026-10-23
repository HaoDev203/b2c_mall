package com.mall.tdd.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * B0 探针：UserContext 是 88 条测试的地基。
 *
 * <p>后面每一个 Service 测试都要在 {@code @BeforeEach} 里手动 set 身份，
 * 否则「按 shop_id 隔离」「越权删除被拒」这类断言根本无从写起。
 * 这个类本身错了，88 条会集体假绿。
 */
@DisplayName("B0 探针：UserContext 静态上下文")
class UserContextProbeTest {

    @AfterEach
    void tearDown() {
        // ★ 静态 ThreadLocal 必须手动清理。漏一次，同线程的下一个测试方法
        //   就会读到上一个用例的身份 —— 表现为「单独跑绿、全量跑红」。
        UserContext.clear();
    }

    @Test
    @DisplayName("set 之后能取到当前登录身份")
    void 设置后可读取() {
        UserContext.set(new LoginUser(1L, 7L, "张三"));

        assertEquals(Long.valueOf(1L), UserContext.getUserId(), "userId 应为登录用户 id");
        assertEquals(Long.valueOf(7L), UserContext.getShopId(), "shopId 应为登录用户所属店铺");
    }

    @Test
    @DisplayName("clear 之后取不到任何身份（防线程复用串号）")
    void 清理后取不到身份() {
        UserContext.set(new LoginUser(1L, 7L, "张三"));

        UserContext.clear();

        assertNull(UserContext.getUserId(), "清理后 userId 必须为 null");
        assertNull(UserContext.getShopId(),
                "清理后 shopId 必须为 null，否则 Tomcat 复用线程时会把 A 店的数据返回给 B 店");
    }
}
