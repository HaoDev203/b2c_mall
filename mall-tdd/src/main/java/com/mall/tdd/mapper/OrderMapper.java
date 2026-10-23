package com.mall.tdd.mapper;

import com.mall.tdd.dto.HourAmount;
import com.mall.tdd.entity.Order;
import com.mall.tdd.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    // ==========================================================
    // C 端买家侧（按 user_id 隔离）
    //
    // ★ 三个方法都带 is_deleted = 0：买家删除 = 仅隐藏。
    // ★ 订单号用 = 而不是 LIKE：需求原文专门强调「精确检索」。
    // ==========================================================

    @Select("<script>"
            + "SELECT * FROM tb_order WHERE user_id = #{userId} AND is_deleted = 0 "
            + "<if test='status != null'>AND status = #{status} </if>"
            + "<if test='orderNo != null'>AND order_no = #{orderNo} </if>"
            + "ORDER BY create_time DESC, id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Order> selectByUserPage(@Param("userId") Long userId,
                                 @Param("status") String status,
                                 @Param("orderNo") String orderNo,
                                 @Param("limit") int limit,
                                 @Param("offset") int offset);

    /**
     * 买家侧订单总数。
     *
     * <p>★ 必须与 {@link #selectByUserPage} 用完全相同的过滤条件，
     * 否则出现「列表只有 2 条，却显示共 3 条」的分页错乱。
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM tb_order WHERE user_id = #{userId} AND is_deleted = 0 "
            + "<if test='status != null'>AND status = #{status} </if>"
            + "<if test='orderNo != null'>AND order_no = #{orderNo} </if>"
            + "</script>")
    long countByUser(@Param("userId") Long userId,
                     @Param("status") String status,
                     @Param("orderNo") String orderNo);

    /**
     * 买家删除订单 —— <b>只置位，不删行</b>。需求原文：「仅隐藏，后台保留订单数据」。
     *
     * <p>★ {@code AND user_id = #{userId}} 就是越权防线：条件写在 SQL 的 WHERE 里，
     * 别人的订单影响行数天然为 0，不需要「先查再判」（那样有 TOCTOU 窗口还多一次查询）。
     *
     * <p>★ 这个接口上<b>不允许出现任何以 delete 开头的方法</b>，有测试用反射守着。
     */
    @Update("UPDATE tb_order SET is_deleted = 1 WHERE id = #{orderId} AND user_id = #{userId}")
    int softDeleteByIdAndUser(@Param("orderId") Long orderId, @Param("userId") Long userId);

    /**
     * 后台订单列表（按 shop_id 隔离）。
     *
     * <p>★★ <b>刻意不带 is_deleted 条件</b>。买家删掉只是从自己眼前隐藏，
     * 商家必须仍然看得到 —— 被买家删掉的往往正是有问题的订单，客诉时就要靠它。
     * 不要为了「和买家侧统一」在这里加 {@code AND is_deleted = 0}。
     */
    @Select("SELECT * FROM tb_order WHERE shop_id = #{shopId} "
            + "ORDER BY create_time DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Order> selectAdminPage(@Param("shopId") Long shopId,
                                @Param("limit") int limit,
                                @Param("offset") int offset);

    /**
     * 批量取订单明细。
     *
     * <p>一次 IN 查询代替每条订单查一次：10 条订单的列表页如果逐条查就是 11 次往返。
     */
    @Select("<script>"
            + "SELECT * FROM tb_order_item WHERE order_id IN "
            + "<foreach collection='orderIds' item='oid' open='(' separator=',' close=')'>#{oid}</foreach>"
            + "</script>")
    List<OrderItem> selectItemsByOrderIds(@Param("orderIds") List<Long> orderIds);
}
