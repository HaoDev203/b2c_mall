package com.shop.mapper;

import com.shop.entity.Order;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface OrderMapper {

    @Select("SELECT * FROM tb_order WHERE id = #{id}")
    Order selectById(@Param("id") Long id);

    /** 列表（多条件筛选 + 分页）。Tab 分类就是传 status 进来 */
    @Select("<script>" +
            "SELECT * FROM tb_order WHERE shop_id = #{shopId}" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "<if test='orderNo != null and orderNo != \"\"'> AND order_no LIKE '%' || #{orderNo} || '%'</if>" +
            "<if test='receiverPhone != null and receiverPhone != \"\"'> AND receiver_phone = #{receiverPhone}</if>" +
            "<if test='payType != null and payType != \"\"'> AND pay_type = #{payType}</if>" +
            " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<Order> selectPage(@Param("shopId") Long shopId,
                           @Param("status") String status,
                           @Param("orderNo") String orderNo,
                           @Param("receiverPhone") String receiverPhone,
                           @Param("payType") String payType,
                           @Param("offset") int offset,
                           @Param("size") int size);

    /** 同上条件的总数（分页用） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM tb_order WHERE shop_id = #{shopId}" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "<if test='orderNo != null and orderNo != \"\"'> AND order_no LIKE '%' || #{orderNo} || '%'</if>" +
            "<if test='receiverPhone != null and receiverPhone != \"\"'> AND receiver_phone = #{receiverPhone}</if>" +
            "<if test='payType != null and payType != \"\"'> AND pay_type = #{payType}</if>" +
            "</script>")
    long countPage(@Param("shopId") Long shopId,
                   @Param("status") String status,
                   @Param("orderNo") String orderNo,
                   @Param("receiverPhone") String receiverPhone,
                   @Param("payType") String payType);

    /**
     * ★★ 状态流转专用（全项目唯一改 tb_order.status 的 SQL）。
     *
     * 两个关键点：
     * 1. WHERE 带 status = 原状态  → 乐观锁。并发时只有一个人能成功，
     *    另一个人 rows=0，说明状态已被别人改过（这就是"防重复发货"）。
     * 2. pay_time / ship_time 用 CASE WHEN 在 SQL 里按目标状态决定要不要写 ——
     *    这样 Java 里就不用再堆 if (to.equals(...)) 的代码。
     *
     * ⚠️ 注意：这条 UPDATE 自己【不判断流转合不合法】。
     *    合法性由 OrderStateMachine 里 selectRule() 查规则表保证 ——
     *    绝不要绕过 fire() 直接调这个 Mapper。
     */
    @Update("UPDATE tb_order SET status = #{toStatus}," +
            " pay_time  = CASE WHEN #{toStatus} = 'PENDING_SHIP'    THEN #{now} ELSE pay_time  END," +
            " ship_time = CASE WHEN #{toStatus} = 'PENDING_RECEIVE' THEN #{now} ELSE ship_time END," +
            " shipping_status = CASE WHEN #{toStatus} = 'PENDING_RECEIVE' THEN '已发货'" +
            "                         WHEN #{toStatus} = 'SUCCESS'         THEN '已签收'" +
            "                         ELSE shipping_status END" +
            " WHERE id = #{id} AND status = #{fromStatus}")
    int updateStatus(@Param("id") Long id,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus,
                     @Param("now") String now);

    /** Dashboard 订单状态卡片：按状态数数 */
    @Select("SELECT COUNT(*) FROM tb_order WHERE shop_id = #{shopId} AND status = #{status}")
    int countByStatus(@Param("shopId") Long shopId, @Param("status") String status);

    // ==================== 阶段 6：前台下单 + 支付 ====================

    /**
     * 创建订单（id 回填到 order.getId()）。
     * ★ shipping_status 初始写 '未发货' —— 和阶段 4 的 updateStatus 里 CASE WHEN 用的中文值对齐。
     */
    @Insert("INSERT INTO tb_order(order_no, user_id, shop_id, total_amount, pay_amount, status, " +
            "pay_type, receiver_name, receiver_phone, receiver_address, shipping_status, remark, create_time) " +
            "VALUES(#{orderNo}, #{userId}, #{shopId}, #{totalAmount}, #{payAmount}, #{status}, " +
            "#{payType}, #{receiverName}, #{receiverPhone}, #{receiverAddress}, '未发货', #{remark}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Order order);

    /** ★ 买家维度订单列表（前台"我的订单"）。和后台 selectPage 的区别是没有 shop_id 条件 */
    @Select("<script>" +
            "SELECT * FROM tb_order WHERE user_id = #{userId}" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<Order> selectByUserPage(@Param("userId") Long userId, @Param("status") String status,
                                 @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM tb_order WHERE user_id = #{userId}" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "</script>")
    long countByUser(@Param("userId") Long userId, @Param("status") String status);

    /**
     * ★ 支付成功时补写 pay_type（阶段 4 的 updateStatus 只写 pay_time，不改 pay_type）。
     *   WHERE 带 status：只有「待付款」的订单才允许补支付方式，防止改到已支付的单。
     *
     * ⚠️ 注意：这一条 UPDATE 的 WHERE 条件是写死的 'PENDING_PAY'，
     *    它不是"状态流转"，是"支付时补一个字段"，所以不走状态机。
     *    状态流转本身仍然只能通过 fire() 完成。
     */
    @Update("UPDATE tb_order SET pay_type = #{payType} WHERE id = #{id} AND status = 'PENDING_PAY'")
    int updatePayType(@Param("id") Long id, @Param("payType") String payType);

    /** 买家查看订单详情时的越权校验用 */
    @Select("SELECT * FROM tb_order WHERE id = #{id} AND user_id = #{userId}")
    Order selectByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 生成订单号用：统计今天已有多少单。
     *
     * ⚠️ 参数是 **yyyy-MM-dd**（带横线），因为 tb_order.create_time 存的是
     *    '2026-09-21 08:17:11' 这个格式。如果传 '20260921' 去 LIKE 匹配，**永远匹配不到 0 条**，
     *    订单号就会一直从 0001 开始重复。
     *    这里用 date() 函数把时间戳截成日期再比，比 LIKE 更明确。
     */
    @Select("SELECT COUNT(*) FROM tb_order WHERE date(create_time) = #{day}")
    long countToday(@Param("day") String day);
}
