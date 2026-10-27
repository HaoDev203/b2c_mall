package com.mall.tdd.mapper;

import com.mall.tdd.dto.AdminOrderQuery;
import com.mall.tdd.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 后台订单查询与发货（B5）。
 *
 * <p>★ <b>每个方法的 WHERE 都带 {@code shop_id = #{shopId}}</b>，
 * 包括列表、总数、Tab 计数、详情、状态查询、发货。少一处就是跨店泄露：
 * 列表过滤挡不住直接构造 id 调接口（O22 vs O23）。
 *
 * <p>★ {@code shopId} 一律作为独立参数传入，<b>不放进 {@link AdminOrderQuery}</b> ——
 * 放进去它就成了请求参数，改个 {@code ?shopId=2} 就能看别家订单。
 *
 * <p>★ 与买家侧的 {@code OrderMapper} 分开：那边按 {@code user_id} 隔离、
 * 且带 {@code is_deleted = 0}；这边按 {@code shop_id} 隔离、
 * <b>刻意不带 is_deleted</b>（被买家删掉的订单商家仍要能查，客诉时就靠它）。
 *
 * <p>★ 时间条件写成 {@code create_time >= #{startDate} AND create_time &lt; date(#{endDate}, '+1 day')}：
 * 既保证结束日整天都在区间内，又不在列上套函数。
 * 写成 {@code date(create_time) >= ?} 会因为列上套函数让索引整体失效（同 B2 原则）。
 * create_time 是 {@code 'YYYY-MM-DD HH:MM:SS'} 文本，字典序 = 时间序，范围比较成立。
 */
@Mapper
public interface AdminOrderMapper {

    /**
     * 订单列表：11 个筛选条件 + 店铺隔离 + 分页。
     *
     * <p>★ 只有 {@code orderNo} 和 {@code storeName} 是 {@code =}，
     * {@code receiverName} / {@code receiverPhone} 是 {@code LIKE}。
     * 同一页面里两种匹配并存，统一成一种就是错：订单号模糊会把
     * {@code SO2026102300019} 也当成 {@code SO202610230001} 命中（O01）。
     *
     * <p>★ 排序写死 {@code create_time DESC, id DESC}：加 id 是为了
     * 同一秒的下单时间翻页时漏行/重复。
     */
    @Select("<script>"
            + "SELECT * FROM tb_order WHERE shop_id = #{shopId} "
            + "<if test='q.orderNo != null'>AND order_no = #{q.orderNo} </if>"
            + "<if test='q.orderType != null'>AND order_type = #{q.orderType} </if>"
            + "<if test='q.orderCategory != null'>AND order_category = #{q.orderCategory} </if>"
            + "<if test='q.status != null'>AND status = #{q.status} </if>"
            + "<if test='q.receiverName != null'>"
            + "AND receiver_name LIKE '%' || #{q.receiverName} || '%' </if>"
            + "<if test='q.receiverPhone != null'>"
            + "AND receiver_phone LIKE '%' || #{q.receiverPhone} || '%' </if>"
            + "<if test='q.payType != null'>AND pay_type = #{q.payType} </if>"
            + "<if test='q.afterSaleNone'>AND after_sale_status IS NULL </if>"
            + "<if test='q.afterSaleStatus != null'>"
            + "AND after_sale_status = #{q.afterSaleStatus} </if>"
            + "<if test='q.logisticsType != null'>AND logistics_type = #{q.logisticsType} </if>"
            + "<if test='q.storeName != null'>AND store_name = #{q.storeName} </if>"
            + "<if test='q.startDate != null'>AND create_time >= #{q.startDate} </if>"
            + "<if test='q.endDate != null'>"
            + "AND create_time &lt; date(#{q.endDate}, '+1 day') </if>"
            + "ORDER BY create_time DESC, id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Order> selectQueryPage(@Param("shopId") Long shopId,
                                @Param("q") AdminOrderQuery query,
                                @Param("limit") int limit,
                                @Param("offset") int offset);

    /**
     * 总数：条件必须与 {@link #selectQueryPage} <b>逐条一致</b>，
     * 否则「共 N 条」和翻页对不上（同 P08b）。
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM tb_order WHERE shop_id = #{shopId} "
            + "<if test='q.orderNo != null'>AND order_no = #{q.orderNo} </if>"
            + "<if test='q.orderType != null'>AND order_type = #{q.orderType} </if>"
            + "<if test='q.orderCategory != null'>AND order_category = #{q.orderCategory} </if>"
            + "<if test='q.status != null'>AND status = #{q.status} </if>"
            + "<if test='q.receiverName != null'>"
            + "AND receiver_name LIKE '%' || #{q.receiverName} || '%' </if>"
            + "<if test='q.receiverPhone != null'>"
            + "AND receiver_phone LIKE '%' || #{q.receiverPhone} || '%' </if>"
            + "<if test='q.payType != null'>AND pay_type = #{q.payType} </if>"
            + "<if test='q.afterSaleNone'>AND after_sale_status IS NULL </if>"
            + "<if test='q.afterSaleStatus != null'>"
            + "AND after_sale_status = #{q.afterSaleStatus} </if>"
            + "<if test='q.logisticsType != null'>AND logistics_type = #{q.logisticsType} </if>"
            + "<if test='q.storeName != null'>AND store_name = #{q.storeName} </if>"
            + "<if test='q.startDate != null'>AND create_time >= #{q.startDate} </if>"
            + "<if test='q.endDate != null'>"
            + "AND create_time &lt; date(#{q.endDate}, '+1 day') </if>"
            + "</script>")
    long countQuery(@Param("shopId") Long shopId, @Param("q") AdminOrderQuery query);

    /**
     * 「全部」Tab 的计数 —— <b>独立 COUNT(*)，不做 6 个 Tab 相加</b>。
     * 7 种状态里 REFUNDING 不属于任何 Tab，相加会漏掉退款中的订单。
     */
    @Select("SELECT COUNT(*) FROM tb_order WHERE shop_id = #{shopId}")
    long countAll(@Param("shopId") Long shopId);

    /** 单个状态 Tab 的计数 */
    @Select("SELECT COUNT(*) FROM tb_order WHERE shop_id = #{shopId} AND status = #{status}")
    long countTab(@Param("shopId") Long shopId, @Param("status") String status);

    /**
     * 订单详情。
     *
     * <p>★ 带 {@code shop_id}：别家的订单查不到。返回 null 时 Service 要返回
     * 「失败」而不是「200 + 空对象」—— 后者会让前端渲染空白页，商家以为订单没了。
     */
    @Select("SELECT * FROM tb_order WHERE id = #{orderId} AND shop_id = #{shopId}")
    Order selectByIdAndShop(@Param("orderId") Long orderId, @Param("shopId") Long shopId);

    /** 只取状态：发货前的校验用，不把整行订单捞出来 */
    @Select("SELECT status FROM tb_order WHERE id = #{orderId} AND shop_id = #{shopId}")
    String selectStatusByIdAndShop(@Param("orderId") Long orderId, @Param("shopId") Long shopId);

    /**
     * 发货：状态流转 + 写单号，<b>一条 UPDATE 完成</b>。
     *
     * <p>★★ 不能拆成「先写单号、再改状态」两步：中间态会留下
     * 「有运单号但状态还是待发货」的脏数据 —— 买家看得到单号、订单却停在待发货，
     * 这是最难解释的线上问题（O18）。
     *
     * <p>★ WHERE 再带一次 {@code status = 'PENDING_SHIP'}：Service 查过状态到这里之间
     * 可能已被别人发走，影响 0 行即如实失败，不会重复发货。
     */
    @Update("UPDATE tb_order SET status = 'PENDING_RECEIVE', "
            + "tracking_no = #{trackingNo}, "
            + "ship_time = datetime('now', 'localtime') "
            + "WHERE id = #{orderId} AND shop_id = #{shopId} AND status = 'PENDING_SHIP'")
    int shipOrder(@Param("orderId") Long orderId,
                  @Param("shopId") Long shopId,
                  @Param("trackingNo") String trackingNo);
}
