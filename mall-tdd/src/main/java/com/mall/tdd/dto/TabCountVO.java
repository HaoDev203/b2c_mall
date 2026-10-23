package com.mall.tdd.dto;

import lombok.Data;

/**
 * 订单列表顶部的 Tab 计数。
 *
 * <p>★ {@code all} 是<b>独立统计</b>，不是 4 个 Tab 相加：
 * 订单有 7 种状态，{@code REFUNDING} / {@code PENDING_GROUP} / {@code FAILED}
 * 不属于任何一个 Tab。真实库实测 shop1 共 38 单、4 个 Tab 之和只有 36 ——
 * 差的就是 2 条退款中。若按求和实现，买家点「全部」会看不到自己正在退款的订单。
 */
@Data
public class TabCountVO {

    private long all;
    private long pendingPay;
    private long pendingShip;
    private long pendingReceive;
    private long success;
}
