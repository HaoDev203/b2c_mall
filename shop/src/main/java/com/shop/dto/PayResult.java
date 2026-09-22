package com.shop.dto;

import lombok.Builder;
import lombok.Data;

/** 支付结果 */
@Data
@Builder
public class PayResult {
    private Boolean success;      // 是否支付成功
    private String  payType;      // 用了哪个策略
    private String  tradeNo;      // 交易流水号
    private String  message;      // 提示（"支付成功" / "请扫码" / "等待确认"）
    private String  extra;        // ★ 策略差异化信息：支付宝=二维码链接 / PayPal=USD 金额 / 微信=预支付单号
}
