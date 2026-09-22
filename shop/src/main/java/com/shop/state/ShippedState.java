package com.shop.state;

import com.shop.statemachine.OrderEvent;
import com.shop.statemachine.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 待收货状态（PENDING_RECEIVE）—— 对应需求里写的 ShippedState */
@Slf4j
@Component
public class ShippedState implements OrderState {

    @Override
    public String getStatus() {
        return OrderStatus.PENDING_RECEIVE;
    }

    @Override
    public void paySuccess(OrderContext ctx) {
        ctx.reject("支付");
    }

    /** ❌ 非法：★ 这正是阶段 4 验收的 AG 用例 —— 发货后再发一次必须报错 */
    @Override
    public void ship(OrderContext ctx) {
        ctx.reject("发货");
    }

    /** ✅ 合法：待收货 → 交易成功（规则表第 4 条） */
    @Override
    public void confirm(OrderContext ctx) {
        ctx.moveTo(OrderEvent.CONFIRM, "买家", "确认收货");
    }

    @Override
    public void cancel(OrderContext ctx) {
        ctx.reject("取消");
    }
}
