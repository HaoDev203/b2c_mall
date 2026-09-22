package com.shop.state;

import com.shop.statemachine.OrderEvent;
import com.shop.statemachine.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 待付款状态（PENDING_PAY）
 *
 * ★★ 这是四个状态里**唯一**允许 paySuccess 的 —— 这就是"状态不能乱跳"。
 *    已支付的订单再支付一次，会被 PendingShipState / CompletedState 拦住。
 */
@Slf4j
@Component
public class PendingPayState implements OrderState {

    @Override
    public String getStatus() {
        return OrderStatus.PENDING_PAY;
    }

    /** ✅ 合法：待付款 → 待发货（规则表第 1 条：PENDING_PAY + PAY_SUCCESS → PENDING_SHIP） */
    @Override
    public void paySuccess(OrderContext ctx) {
        log.info("【状态-待付款】订单 {} 收到支付成功事件，流转到待发货", ctx.getOrder().getId());
        // ★ 复用阶段 4 的状态机：查规则表 + 乐观锁 + 留痕，一步不少
        ctx.moveTo(OrderEvent.PAY_SUCCESS, "买家#" + ctx.getOrder().getUserId(), "前台支付成功");
    }

    /** ❌ 非法：还没付钱，不能发货（规则表里没有 PENDING_PAY + SHIP 这行） */
    @Override
    public void ship(OrderContext ctx) {
        ctx.reject("发货");
    }

    /** ❌ 非法：还没发货，谈不上收货 */
    @Override
    public void confirm(OrderContext ctx) {
        ctx.reject("确认收货");
    }

    /** ✅ 合法：待付款 → 交易失败（规则表第 2 条：超时取消） */
    @Override
    public void cancel(OrderContext ctx) {
        ctx.moveTo(OrderEvent.CANCEL_TIMEOUT, "系统", "超时未支付，自动取消");
    }
}
