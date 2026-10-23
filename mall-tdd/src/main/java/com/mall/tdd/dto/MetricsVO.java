package com.mall.tdd.dto;

import lombok.Data;

/**
 * 四项核心经营数据。
 *
 * <p>客单价没有独立的 Mapper 查询 —— 它是 {@code 支付总额 ÷ 支付订单数} 算出来的，
 * 所以分子分母都要单独查（见 D17）。
 */
@Data
public class MetricsVO {

    /** 支付客户数：同一客户当日多单只算 1 个（COUNT DISTINCT） */
    private MetricVO paidCustomers;

    /** 支付订单数：口径是 pay_time IS NOT NULL，不是 status = 'PAID' */
    private MetricVO paidOrders;

    /** 退款金额 */
    private MetricVO refundAmount;

    /** 客单价 = 支付总额 ÷ 支付订单数，分母为 0 时返回 0.00 */
    private MetricVO averageOrderValue;
}
