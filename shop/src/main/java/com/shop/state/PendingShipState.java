package com.shop.state;

import com.shop.statemachine.OrderEvent;
import com.shop.statemachine.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 待发货状态（PENDING_SHIP）
 *
 * ★ 这是"支付成功之后"的落点 —— 需求 Step 6.5 自测要求的就是这个：
 *   「支付成功 → 订单从「待付款」变「待发货」」
 */
@Slf4j
@Component
public class PendingShipState implements OrderState {

    @Override
    public String getStatus() {
        return OrderStatus.PENDING_SHIP;
    }

    /** ❌ 非法：已经付过款了，不能重复支付 */
    @Override
    public void paySuccess(OrderContext ctx) {
        ctx.reject("支付");
    }

    /** ✅ 合法：待发货 → 待收货（规则表第 3 条） */
    @Override
    public void ship(OrderContext ctx) {
        ctx.moveTo(OrderEvent.SHIP, "后台", "商家发货");
    }

    /** ❌ 非法：还没发货 */
    @Override
    public void confirm(OrderContext ctx) {
        ctx.reject("确认收货");
    }

    /**
     * ⚠️ 待发货理论上可以取消（还没发出去），但规则表里没有 PENDING_SHIP + CANCEL_TIMEOUT 这一行
     *    → 会抛异常。这是故意的：规则表说了算，不是状态类说了算。
     */
    @Override
    public void cancel(OrderContext ctx) {
        ctx.reject("取消");
    }
}
