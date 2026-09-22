package com.shop.state;

import com.shop.statemachine.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 交易成功状态（SUCCESS）—— 终态。
 *
 * ★ 四个方法全是 reject —— 终态就是这样。看起来"什么都没干"，
 *   但它是有意义的：它保证**交易成功的订单不可能被任何操作改回来**。
 *   用 if-else 写的话，你得在四个地方都写一遍判断，漏一处就是 bug。
 */
@Slf4j
@Component
public class CompletedState implements OrderState {

    @Override
    public String getStatus() {
        return OrderStatus.SUCCESS;
    }

    @Override
    public void paySuccess(OrderContext ctx) {
        ctx.reject("支付");
    }

    @Override
    public void ship(OrderContext ctx) {
        ctx.reject("发货");
    }

    @Override
    public void confirm(OrderContext ctx) {
        ctx.reject("确认收货");
    }

    @Override
    public void cancel(OrderContext ctx) {
        ctx.reject("取消");
    }
}
