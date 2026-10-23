package com.mall.tdd.common;

import lombok.Data;

/**
 * 统一返回体。
 * code：200 成功 / 500 业务失败 / 401 未登录。
 */
@Data
public class Result<T> {

    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<T>();
        r.setCode(200);
        r.setMsg("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> fail(String msg) {
        Result<T> r = new Result<T>();
        r.setCode(500);
        r.setMsg(msg);
        return r;
    }
}
