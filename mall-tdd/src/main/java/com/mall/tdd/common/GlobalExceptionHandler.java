package com.mall.tdd.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 全局异常：任何异常都转成 {@link Result} 形状，但<b>HTTP 状态码必须保住</b>。
 *
 * <p>没有它的后果：Spring 默认返回 {@code {"timestamp":..,"status":500,"path":..}}，
 * 前端按 {@code res.data.code} 判断会拿到 {@code undefined}，
 * 于是所有失败都变成「未知错误」，具体原因全部丢失。
 *
 * <p>★★ <b>为什么继承 {@link ResponseEntityExceptionHandler} 而不是只写一个
 * {@code @ExceptionHandler(Exception.class)}：</b>
 * 后者会把 Spring MVC 自己抛的标准异常也一起吞掉，全部变成
 * {@code HTTP 200 + "服务器繁忙"}。实测踩到了（W12）：
 * 请求 {@code GET /api/shop/product/listX} 命中了 {@code /{id}} 的路径模式，
 * 只是方法不匹配，本该 405，被兜底吞成 200，前端会以为"接口正常但没数据"。
 *
 * <p>父类已经处理了 400 / 405 / 415 / 406 这些标准异常并带上正确状态码，
 * 这里只需要重写 {@code handleExceptionInternal} 把响应体换成 {@code Result}。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * 所有由父类捕获的标准 MVC 异常都会走到这里。
     *
     * <p>保留 {@code status}（404/405/415/400…），只替换响应体。
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
                                                             HttpHeaders headers,
                                                             HttpStatus status,
                                                             WebRequest request) {
        log.warn("请求被拒绝 {}：{}", status, ex.getMessage());
        return ResponseEntity.status(status).body(Result.fail(msgFor(status)));
    }

    /**
     * 真正的未知异常（NPE、SQL 报错等），兜底成 500。
     *
     * <p>★ 只回固定文案，<b>不回异常消息</b>：异常里常带 SQL 原文、表名、字段名，
     * 属于白盒信息，能帮攻击者推断表结构。细节进日志，不进响应体。
     *
     * <p>路径不存在（{@code NoHandlerFoundException}）不在这里处理 ——
     * 父类 {@link ResponseEntityExceptionHandler} 已经映射了它，
     * 再加一个 @ExceptionHandler 会造成 Ambiguous @ExceptionHandler method 冲突
     * （实测报错）。它同样走 {@code handleExceptionInternal}，状态码保 404。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnknown(Exception e) {
        log.error("未捕获异常", e);
        return Result.fail("服务器繁忙，请稍后重试");
    }

    private String msgFor(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return "接口不存在";
        }
        if (status == HttpStatus.METHOD_NOT_ALLOWED) {
            return "请求方法不支持";
        }
        if (status == HttpStatus.UNSUPPORTED_MEDIA_TYPE) {
            return "请求格式不支持";
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return "请求参数有误";
        }
        return "请求处理失败";
    }
}
