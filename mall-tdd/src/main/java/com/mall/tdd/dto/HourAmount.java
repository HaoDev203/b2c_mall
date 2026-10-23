package com.mall.tdd.dto;

import lombok.Data;

/**
 * 按小时聚合的支付金额 —— 趋势图的原始行。
 *
 * <p>只有「有成交的小时」才会出现在 SQL 结果里，缺口由 Service 补 0（见 D13）：
 * {@code null} 会让 ECharts 断线，{@code 0} 才是正确的「无成交」。
 */
@Data
public class HourAmount {

    /** 0~23 */
    private Integer hour;

    /** 该小时支付金额合计 */
    private Double amount;
}
