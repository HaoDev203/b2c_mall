package com.mall.tdd.dto;

import lombok.Data;

/**
 * 一项经营指标：数值 + 同比昨日变化率。
 *
 * <p>★ {@code dod} 是<b>百分数</b>（50.00 表示 +50.00%），不是倍数（0.5）——
 * 前端直接 {@code String.format("%.2f%%", dod)} 即可。
 *
 * <p>★ 昨日为 0 时固定 {@code 100.00}，这是需求原文写死的边界（见 D19）：
 * 数学上「从 0 增长」是无穷大，业务上定死 100%。
 */
@Data
public class MetricVO {

    /** 今日数值 */
    private double value;

    /** 同比昨日变化率，百分数 */
    private double dod;

    /** 需求原文固定值：昨日为 0 时同比按 100% 展示 */
    public static final double DOD_WHEN_YESTERDAY_ZERO = 100.00D;

    public static MetricVO of(double value, double yesterday) {
        MetricVO vo = new MetricVO();
        vo.setValue(value);
        vo.setDod(dod(value, yesterday));
        return vo;
    }

    /**
     * 同比 = (今日 - 昨日) / 昨日 × 100。
     *
     * <p>昨日为 0 直接返回 {@code 100.00}，<b>不能让它去做除法</b> ——
     * double 除法在这里会得到 {@code Infinity} 或 {@code NaN}，前端会显示成 "Infinity%"。
     */
    public static double dod(double today, double yesterday) {
        if (yesterday == 0D) {
            return DOD_WHEN_YESTERDAY_ZERO;
        }
        return (today - yesterday) / yesterday * 100D;
    }
}
