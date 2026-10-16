package com.mall.auth.domain.model;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
public class RawPasswordTest {
    // 32 位：26 个小写字母 + 6 个数字 = 32
    private static final String P32 = "abcdefghijklmnopqrstuvwxyz012345";
    // 33 位：在 P32 后面接一个字符
    private static final String P33 = "abcdefghijklmnopqrstuvwxyz0123456";


    @Test
    void should_reject_when_password_is_null() {
        assertThatThrownBy(() -> new RawPassword(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void should_reject_when_password_is_empty() {
        assertThatThrownBy(() -> new RawPassword(""))
        .isInstanceOf(IllegalArgumentException.class);

    }
    @Test
    void should_reject_when_length_is_5() {
        assertThatThrownBy(() -> new RawPassword("abc12"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void should_accept_when_length_is_6() {
        assertThat(new RawPassword("abc123").value()).isEqualTo("abc123");
    }
    @Test
    void should_accept_when_length_is_32() {
        assertThat(new RawPassword(P32).value()).hasSize(32);
    }
    @Test
    void should_reject_when_length_is_33() {
        assertThatThrownBy(() -> new RawPassword(P33))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
