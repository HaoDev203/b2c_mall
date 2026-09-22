package com.shop.pay;

import com.shop.dto.PayRequest;
import com.shop.dto.PayResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 支付宝：生成二维码，扫码后立即成功。
 * ★ 模拟实现：真实项目里这里要调 alipay-sdk，教学项目只造一个可信的流水号。
 */
@Slf4j
@Component
public class AlipayStrategy implements PayStrategy {

    @Override
    public String getPayType() {
        return "alipay";
    }

    @Override
    public String getPayName() {
        return "支付宝";
    }

    @Override
    public PayResult pay(PayRequest request) {
        String tradeNo = "ALI" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        log.info("【支付-支付宝】订单 {} 金额 {} 元，交易号 {}，立即返回成功",
                request.getOrderNo(), request.getAmount(), tradeNo);

        return PayResult.builder()
                .success(true)
                .payType(getPayType())
                .tradeNo(tradeNo)
                .message("支付宝支付成功")
                .extra("https://qr.alipay.com/" + tradeNo)   // ★ 差异化信息：二维码链接
                .build();
    }
}
