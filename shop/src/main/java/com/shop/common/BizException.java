package com.shop.common;

/**
 * 业务校验异常（可预期的失败：库存为 0、类目不存在、卡券过期……）。
 * 和 NullPointerException 这类"程序 bug"区分开：
 * 业务异常是"用户填错了"，要原样把提示还给前端；
 * 系统异常是"代码写错了"，要打到日志里让人去修。
 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }
}
