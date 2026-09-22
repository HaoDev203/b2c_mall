package com.shop.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 * 没有它的话，钩子抛出来的 BizException 会变成 Spring 默认的 500 页面：
 *   {"timestamp":"...","status":500,"error":"Internal Server Error","path":"/product/publish"}
 * 有了它，才变成我们统一的 Result 结构：
 *   {"code":500,"msg":"实物商品库存必须大于 0（当前：0）","data":null}
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务校验失败：用户填错了，把原话还给他 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("【业务校验失败】{}", e.getMessage());
        return Result.fail(e.getMessage());
    }

    /** 兜底：其他没预料的异常，日志里打全栈，返回给前端简略信息 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("【系统异常】", e);
        return Result.fail("系统异常：" + e.getMessage());
    }
}
