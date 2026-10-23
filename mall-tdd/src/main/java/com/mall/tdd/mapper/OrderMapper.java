package com.mall.tdd.mapper;

import com.mall.tdd.dto.HourAmount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 后台订单查询（Dashboard 用到的聚合部分）。
 *
 * <p>★ 日期条件一律写成 {@code pay_time >= #{date} AND pay_time < date(#{date}, '+1 day')}，
 * <b>不用 {@code date(pay_time) = #{date}}</b>：
 * 后者在列上套了函数，{@code idx_order_pay_time} 直接失效，数据量一大就全表扫。
 * pay_time 是 {@code 'YYYY-MM-DD HH:MM:SS'} 文本，字典序与时间序一致，范围比较是正确的。
 *
 * <p>★ 「已支付」的唯一口径是 {@code pay_time IS NOT NULL}，<b>不看 status</b>：
 * 订单支付后还会继续流转到待发货/待收货/成功，按 status 算会大面积漏掉。
 */
@Mapper
public interface OrderMapper {

    /** 状态卡片：本店某一状态的订单总数 */
    @Select("SELECT COUNT(*) FROM tb_order WHERE shop_id = #{shopId} AND status = #{status}")
    long countByStatus(@Param("shopId") Long shopId, @Param("status") String status);

    /** 趋势图：本店某天按小时聚合的支付金额（只有有成交的小时才会返回行） */
    @Select("SELECT CAST(strftime('%H', pay_time) AS INTEGER) AS hour, "
            + "       SUM(pay_amount) AS amount "
            + "FROM tb_order "
            + "WHERE shop_id = #{shopId} "
            + "  AND pay_time IS NOT NULL "
            + "  AND pay_time >= #{date} AND pay_time < date(#{date}, '+1 day') "
            + "GROUP BY hour ORDER BY hour")
    List<HourAmount> sumPaidAmountByHour(@Param("shopId") Long shopId, @Param("date") String date);

    /**
     * 支付客户数。
     *
     * <p>★ 必须是 {@code COUNT(DISTINCT user_id)}：需求原文写的是「独立客户数量」。
     * 写成 {@code COUNT(*)} 的话，同一客户下两单会被算成两个人。
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM tb_order "
            + "WHERE shop_id = #{shopId} "
            + "  AND pay_time IS NOT NULL "
            + "  AND pay_time >= #{date} AND pay_time < date(#{date}, '+1 day')")
    long countDistinctPaidCustomers(@Param("shopId") Long shopId, @Param("date") String date);

    /** 支付订单数 */
    @Select("SELECT COUNT(*) FROM tb_order "
            + "WHERE shop_id = #{shopId} "
            + "  AND pay_time IS NOT NULL "
            + "  AND pay_time >= #{date} AND pay_time < date(#{date}, '+1 day')")
    long countPaidOrders(@Param("shopId") Long shopId, @Param("date") String date);

    /** 支付总额（客单价的分子） */
    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM tb_order "
            + "WHERE shop_id = #{shopId} "
            + "  AND pay_time IS NOT NULL "
            + "  AND pay_time >= #{date} AND pay_time < date(#{date}, '+1 day')")
    Double sumPaidAmount(@Param("shopId") Long shopId, @Param("date") String date);

    /**
     * 退款金额。
     *
     * <p>★ 日期按 {@code create_time} 归属，不是 pay_time —— 退款发生在下单之后，
     * 可能跨天，本期库里没有独立的退款时间列，用下单时间作为归属口径。
     *
     * <p>★ {@code COALESCE(..., 0)}：无退款时 SUM 返回 NULL，前端格式化会崩。
     */
    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM tb_order "
            + "WHERE shop_id = #{shopId} "
            + "  AND status = 'REFUNDING' "
            + "  AND create_time >= #{date} AND create_time < date(#{date}, '+1 day')")
    Double sumRefundAmount(@Param("shopId") Long shopId, @Param("date") String date);
}
