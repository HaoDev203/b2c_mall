package com.mall.tdd.dto;

import lombok.Data;

import java.util.List;

/**
 * 24 小时双曲线（需求：蓝线今日支付金额、绿线昨日支付金额）。
 *
 * <p>★ {@code today} / {@code yesterday} 长度<b>恒为 24</b>，无成交的小时放 {@code 0.0}
 * 而不是 {@code null}。前端画 24 个刻度，少一个点整条曲线都会错位；{@code null}
 * 还会让 ECharts 在缺失点断线。
 */
@Data
public class TrendVO {

    /** 今日 24 个点，下标即小时 */
    private List<Double> today;

    /** 昨日 24 个点，下标即小时 */
    private List<Double> yesterday;
}
