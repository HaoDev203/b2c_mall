package com.mall.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Username 值对象")
public class UsernameTest {
    private static String repeat(char c, int times) {
        StringBuilder sb = new StringBuilder(times);
        for (int i = 0; i < times; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    @Test
    @DisplayName("用户名为 null → 抛 IllegalArgumentException（不是 NullPointerException）")
    void should_reject_when_username_is_null() {
        assertThatThrownBy(() -> new Username(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("用户名全是空格 → 抛 IllegalArgumentException，提示含「用户名」")
    void should_reject_when_username_is_blank() {
        assertThatThrownBy(() -> new Username("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户名");
    }
    @Test
    @DisplayName("用户名长度 3 → 抛 IllegalArgumentException")
    void should_reject_when_username_length_is_3() {
        assertThatThrownBy(() -> new Username("abc"))
        .isInstanceOf(IllegalArgumentException.class);

    }
    @Test
    @DisplayName("用户名长度 21 → 抛 IllegalArgumentException")
    void should_reject_when_length_is_21() {
        assertThatThrownBy(() -> new Username(repeat('a', 21)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    @DisplayName("用户名长度 4 → 创建成功，value() 等于 abcd")
    void should_accept_when_length_is_4() {
        assertThat(new Username("abcd").value()).isEqualTo("abcd");
    }
    @Test
    @DisplayName("用户名长度 20 → 创建成功")
    void should_accept_when_length_is_20() {
        assertThat(new Username(repeat('a', 20)).value()).hasSize(20);
    }
    @Test
    @DisplayName("用户名含非法字符 → 抛 IllegalArgumentException")
    void should_reject_when_charset_is_invalid() {
        assertThatThrownBy(() -> new Username("zhang hao"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Username("张三张三"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Username("zh@ng"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    @DisplayName("首尾空格 → 自动去掉")
    void should_trim_padding() {
        assertThat(new Username("  zhanghao  ").value()).isEqualTo("zhanghao");
    }
    @Test
    @DisplayName("同值的 Username 相等，且 hashCode 一致")
    void should_be_equal_when_same_value() {
        Username a = new Username("zhanghao");
        Username b = new Username("zhanghao");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

}
