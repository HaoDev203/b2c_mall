package com.shop.dto;

import lombok.Data;

/** 发起支付 */
@Data
public class PayDTO {
    private Long   orderId;      // order_id
    private String payType;      // alipay / wechat / paypal / other
    private String buyerAccount; // 模拟用的账号（PayPal 需要邮箱）
}
