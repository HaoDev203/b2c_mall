package com.shop.state;

import com.shop.common.BizException;
import com.shop.entity.Order;
import com.shop.mapper.OrderMapper;
import com.shop.statemachine.OrderStateMachine;
import com.shop.statemachine.OrderStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ★★ 订单状态上下文（状态模式的"上下文"角色）
 *
 * 它的职责有三个：
 *   ① 持有"当前状态"—— 知道订单现在处于哪个状态对象
 *   ② ★ 提供"转移到下一个状态"的方法 —— 这是状态模式的核心机制
 *   ③ 给各个状态类提供它们要用的东西（stateMachine / orderMapper）
 *
 * ★★ 关键设计：`moveTo()` 不自己改状态，而是**委托给阶段 4 的 OrderStateMachine**。
 *    这样两个考点共用同一份规则表（需求 Step 6.4 明确要求的），
 *    "能不能流转"永远只有一处判断，不会出现两套规则对不上的情况。
 *
 * ⚠️ 这个类**不是 Spring Bean**（没有 @Component）——
 *    它绑定了一个具体订单，每次业务操作 new 一个。
 *    状态类（PendingPayState 等）才是 Bean。
 */
@Slf4j
@Getter
@RequiredArgsConstructor
public class OrderContext {

    /** 订单（含当前 status） */
    private final Order order;

    /** ★ 复用阶段 4 的状态机 —— 规则的唯一来源 */
    private final OrderStateMachine stateMachine;

    private final OrderMapper orderMapper;

    /**
     * 当前状态对象。
     * ★ 由 OrderStateFactory 根据 order.getStatus() 决定，不是硬编码。
     */
    private OrderState currentState;

    /** package-private：只有同包的工厂能设置它，外部只能读 */
    void setCurrentState(OrderState state) {
        this.currentState = state;
    }

    /** 当前状态的中文名 —— 给日志和返回值用 */
    public String currentStatusCn() {
        return OrderStatus.cn(order.getStatus());
    }

    /**
     * ★★ 执行一次流转，并**自动把当前状态对象换成新的**。
     *
     * 这是状态模式和阶段 4 状态机最关键的区别：
     *   阶段 4：`fire()` 改完库就结束了，调用方拿到新状态字符串，**对象本身不变**。
     *   阶段 6：流转完之后，context 会自动指向**新的状态对象** ——
     *          于是"同一个 ctx 连续调两次"会得到不同结果（第一次成功、第二次报错）。
     *
     * 这个"状态对象自己会换"的特性，就是状态模式。
     */
    void moveTo(String event, String operator, String remark) {
        // ① 让阶段 4 的状态机去改库（查规则表 → 乐观锁更新 → 留痕）
        String toStatus = stateMachine.fire(order.getId(), event, operator, remark);

        // ② 订单对象内存里的状态跟着变，否则后面读到的还是旧值
        order.setStatus(toStatus);

        // ③ ★ 换成下一个状态对象 —— 这一步让"状态"从数据变成了对象
        this.currentState = OrderStateFactory.of(this, toStatus);
        log.info("【状态模式】订单 {} 的当前状态对象已切换为 {}",
                order.getId(), currentState.getClass().getSimpleName());
    }

    /** 非法操作统一从这里抛 —— 保证四个状态类的报错格式一致 */
    void reject(String action) {
        throw new BizException("订单当前状态【" + currentStatusCn() + "】不允许执行【" + action + "】");
    }
}
