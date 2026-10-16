package com.mall.auth.domain.model;
import java.util.Objects;
public class Username {
    private final String value;
    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 20;
    private static final String ALLOWED_PATTERN = "^[A-Za-z0-9_]+$";
    public Username(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (value.trim().length() < MIN_LENGTH) {
            throw new IllegalArgumentException("用户名长度不能少于 " + MIN_LENGTH + " 位");
        }
        if (value.trim().length() > MAX_LENGTH) {
            throw new IllegalArgumentException("用户名长度不能超过 " + MAX_LENGTH + " 位");
        }
        if (!value.trim().matches(ALLOWED_PATTERN)) {
            throw new IllegalArgumentException("用户名只能包含字母、数字和下划线");
        }
        this.value = value.trim();
    }
    @Override

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Username)) {
            return false;
        }
        return Objects.equals(value, ((Username) o).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
    public String value() {
        return value;
    }
}
