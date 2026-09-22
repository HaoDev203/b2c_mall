package com.shop.pay;

import com.shop.dto.PayRequest;
import com.shop.dto.PayResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 其他方式（线下转账 / 货到付款）—— 原型图 12 的第四个选项。
 *
 * ★★ 差异点：**不生成线上流水号，走线下确认**。
 *    这是策略模式真正的价值所在：新增一种支付方式，
 *    只要加一个实现类，工厂、Service、Controller **一个字都不用改**。
 */
@Slf4j
@Component
public class OtherStrategy implements PayStrategy {

    @Override
    public String getPayType() {
        return "other";
    }

    @Override
    public String getPayName() {
        return "其他方式";
    }

    @Override
    public PayResult pay(PayRequest request) {
        log.info("【支付-其他】订单 {} 选择线下转账，等待财务人工确认", request.getOrderNo());

        return PayResult.builder()
                .success(true)
                .payType(getPayType())
                .tradeNo("OFFLINE-" + request.getOrderNo())
                .message("已提交线下转账申请，等待财务确认")
                .extra("请联系客服确认到账")                   // ★ 差异化信息
                .build();
    }
}
