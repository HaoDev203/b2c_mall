package com.mall.tdd.enums;

/**
 * 订单状态，共 7 个，与 {@code sql/mall-ddl.sql} 里 {@code tb_order.status} 的注释一致。
 *
 * <p>★ {@link #PENDING_GROUP}（待成团）是需求原文点名的快捷标签，而一期末 shop 的
 * 状态集合恰好缺它、反而多一个 {@link #REFUNDING} —— 「状态一共几个」是审计里
 * 最能说明问题的一问，需求要 7 个，旧代码给的是 6 个且集合不同。
 *
 * <p>★ {@link #REFUNDING} <b>不属于任何一个 Tab</b>：快捷标签只有
 * 待付款/待发货/待收货/交易成功/交易失败/待成团 6 个，退款中的订单哪个 Tab 都进不去。
 * 这正是「全部」必须走独立 {@code COUNT(*)} 而不能把 6 个 Tab 相加的原因 ——
 * 相加就会把退款中的订单从「全部」里漏掉（O14）。
 */
public enum OrderStatus {

    /** 待付款 */
    PENDING_PAY,

    /** 待发货：只有这个状态可以执行发货（O19） */
    PENDING_SHIP,

    /** 待收货 */
    PENDING_RECEIVE,

    /** 待成团：拼团订单尚未成团，shop 缺的就是这个 */
    PENDING_GROUP,

    /** 交易成功 */
    SUCCESS,

    /** 交易失败 */
    FAILED,

    /** 退款中：不参与任何 Tab 的计数 */
    REFUNDING
}
