package com.mall.tdd.dto;

import lombok.Data;

/**
 * 后台订单查询页的 7 个 Tab 计数。
 *
 * <p>★ 7 个 Tab = 「全部」+ 6 个状态，别数成「7 个状态 + 全部」。
 *
 * <p>★★ {@code all} 是<b>独立 COUNT(*)</b>，不是 6 个 Tab 相加。
 * 7 种状态里 {@code REFUNDING} 不属于任何 Tab，相加必然少掉退款中的订单 ——
 * 而退款中的订单恰恰是最需要商家处理的。B3 买家侧 4 个 Tab 同理
 * （真库实测 shop1 是 38 vs 36）。
 */
@Data
public class AdminTabCountVO {

    /** 全部：独立统计，见类注释 */
    private long all;

    private long pendingPay;
    private long pendingShip;
    private long pendingReceive;
    private long success;
    private long failed;

    /** 待成团：shop 缺的那个状态，对应 OrderStatus.PENDING_GROUP */
    private long pendingGroup;
}
