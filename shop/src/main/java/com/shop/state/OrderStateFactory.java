package com.shop.state;

import com.shop.common.BizException;
import com.shop.entity.Order;
import com.shop.mapper.OrderMapper;
import com.shop.statemachine.OrderStateMachine;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ★★ 订单状态工厂（Map 收集，和 PayStrategyFactory 同一个套路）
 *
 * 两个职责：
 *   ① 把状态码（String）翻译成状态对象（OrderState）
 *   ② 提供 `create(order)` —— 给一个订单，造出绑定它的 OrderContext
 *
 * ★ 这里没有把 Map 做成实例字段，而是 static —— 为什么？
 *   因为 `of(ctx, status)` 要在 OrderContext 内部被调用，而 OrderContext **不是 Bean**
 *   （每次业务操作 new 一个），拿不到注入进来的工厂实例，
 *   所以需要一个**静态**入口。两种写法都对，这里选静态是为了让 OrderContext 保持"普通对象"。
 */
@Component
public class OrderStateFactory {

    private static final Map<String, OrderState> STATE_MAP = new HashMap<>();

    /** Spring 启动时把所有 OrderState 实现收集进来（构造器注入 List） */
    public OrderStateFactory(List<OrderState> states) {
        STATE_MAP.clear();       // ★ static 会被多实例共享，先清再装，行为可预期
        for (OrderState state : states) {
            STATE_MAP.put(state.getStatus(), state);
        }
    }

    /** ★ 静态入口：把状态码翻译成状态对象（给 OrderContext.moveTo 用） */
    static OrderState of(OrderContext ctx, String status) {
        OrderState state = STATE_MAP.get(status);
        if (state == null) {
            throw new BizException("未知的订单状态：" + status);
        }
        return state;
    }

    /**
     * ★★ 造一个绑定订单的上下文 —— 这是所有状态操作的入口。
     *
     * ★ 注意 `currentState` 是**根据 order.getStatus() 从库里读出来的**，
     *   不是由调用方指定的。所以调用方永远不需要知道"这个订单现在是什么状态" ——
     *   它只管调 `ctx.getCurrentState().ship(ctx)`。
     */
    public OrderContext create(Order order, OrderStateMachine stateMachine, OrderMapper orderMapper) {
        OrderContext ctx = new OrderContext(order, stateMachine, orderMapper);
        ctx.setCurrentState(of(ctx, order.getStatus()));
        return ctx;
    }

    /** 调试用：列出全部已注册状态 */
    static Map<String, OrderState> allStates() {
        return STATE_MAP;
    }
}
