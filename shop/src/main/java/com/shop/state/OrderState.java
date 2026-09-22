package com.shop.state;

/**
 * ★★ 订单状态接口（阶段 6 考点之一）
 *
 * 需求原文的方法签名：
 *   void paySuccess(OrderContext ctx);   // 支付成功
 *   void ship(OrderContext ctx);         // 发货
 *   void confirm(OrderContext ctx);      // 确认收货
 *   void cancel(OrderContext ctx);       // 取消
 *
 * ★ 这就是状态模式的判据：**每个操作都把"上下文"传进来**。
 *   因为状态类需要它来读订单 id、判断要不要流转、以及转移到下一个状态。
 *
 * ★ 和策略模式的接口对比一下，很像但有本质区别：
 *   策略：`PayResult pay(PayRequest request)` —— 返回结果，**不改变自己**
 *   状态：`void ship(OrderContext ctx)`        —— 不返回结果，**改变 ctx 的状态**
 */
public interface OrderState {

    /** 支付成功（前台支付） */
    void paySuccess(OrderContext ctx);

    /** 发货（后台操作，阶段 4 已有，这里保持接口完整） */
    void ship(OrderContext ctx);

    /** 确认收货 */
    void confirm(OrderContext ctx);

    /** 取消订单 */
    void cancel(OrderContext ctx);

    /** 当前状态码，让工厂能对上 order.getStatus() */
    String getStatus();
}
