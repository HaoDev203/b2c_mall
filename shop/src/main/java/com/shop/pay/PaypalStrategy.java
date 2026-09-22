package com.shop.pay;

import com.shop.dto.PayRequest;
import com.shop.dto.PayResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * PayPal：跨境支付，需要买家邮箱，金额要换算成美元。
 *
 * ★★ 差异点：**入参金额是人民币，这里要换算**。
 *    几个策略收到的 PayRequest 完全一样，但 PayPal 多做了一步汇率处理 ——
 *    这就是"同一动作、不同算法"的直观体现。
 */
@Slf4j
@Component
public class PaypalStrategy implements PayStrategy {

    /** 教学项目写死汇率，真实项目要从汇率服务拿 */
    private static final double USD_RATE = 7.2;

    @Override
    public String getPayType() {
        return "paypal";
    }

    @Override
    public String getPayName() {
        return "PayPal";
    }

    @Override
    public PayResult pay(PayRequest request) {
        String account   = request.getBuyerAccount() == null ? "guest@paypal.com" : request.getBuyerAccount();
        double usdAmount = Math.round(request.getAmount() / USD_RATE * 100) / 100.0;
        String tradeNo   = "PP" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        log.info("【支付-PayPal】订单 {} 人民币 {} 换算 USD {}，账户 {}，交易号 {}",
                request.getOrderNo(), request.getAmount(), usdAmount, account, tradeNo);

        return PayResult.builder()
                .success(true)
                .payType(getPayType())
                .tradeNo(tradeNo)
                .message("PayPal 支付成功")
                .extra("USD " + usdAmount + " @" + account)   // ★ 差异化信息：美元金额 + 账户
                .build();
    }
}
