package com.mall.auth.domain.model;

public class RawPassword {
    private final String value;
    private static final int MIN_LENGTH = 6;
    private static final int MAX_LENGTH = 32;
    public RawPassword(String value) {
        if (value==null||value.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (value.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("密码长度不能少于 " + MIN_LENGTH + " 位");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("密码长度不能大于 " + MAX_LENGTH + " 位");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }
}
