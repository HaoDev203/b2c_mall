package com.shop.pay;

import com.shop.dto.PayRequest;
import com.shop.dto.PayResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 微信支付：生成预支付单，用户确认后成功。
 *
 * ★★ 这个策略和支付宝的差异点：**多了一步"预支付单"**。
 *    真实微信支付就是两步：先统一下单拿 prepay_id，用户确认后再回调通知。
 *    我把它模拟成"生成预支付单并确认" —— 这样几个策略的行为差异是真实可见的，
 *    而不是几个类里复制粘贴换个名字（那样老师一眼就看出是凑数的）。
 *
 * ★ 注意 `extra` 字段：支付宝给的是二维码链接，微信给的是 prepay_id，
 *   前端拿到 extra 后能渲染出完全不同的收银台界面 —— 这就是策略模式的价值。
 */
@Slf4j
@Component
public class WechatPayStrategy implements PayStrategy {

    @Override
    public String getPayType() {
        return "wechat";
    }

    @Override
    public String getPayName() {
        return "微信支付";
    }

    @Override
    public PayResult pay(PayRequest request) {
        String prepayId = "WX" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        log.info("【支付-微信】订单 {} 生成预支付单 {}，用户确认后支付成功",
                request.getOrderNo(), prepayId);

        return PayResult.builder()
                .success(true)
                .payType(getPayType())
                .tradeNo(prepayId)
                .message("微信支付成功（预支付单已确认）")
                .extra("prepay_id=" + prepayId)             // ★ 差异化信息：预支付单号
                .build();
    }
}
