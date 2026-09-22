package com.shop.mapper;

import com.shop.entity.OrderItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface OrderItemMapper {

    @Select("SELECT * FROM tb_order_item WHERE order_id = #{orderId} ORDER BY id")
    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);

    @Insert("INSERT INTO tb_order_item(order_id, product_id, product_name, product_image, price, quantity, subtotal) " +
            "VALUES(#{orderId}, #{productId}, #{productName}, #{productImage}, #{price}, #{quantity}, #{subtotal})")
    int insert(OrderItem item);

    /**
     * ★ 批量插入订单明细（一个订单多个商品，一次 SQL 插完）。
     *   为什么要批量而不是循环调上面的 insert？一个订单 5 个商品就是 5 次数据库往返，
     *   <foreach> 拼成一条 SQL 只需 1 次 —— 这是 MyBatis 处理"一对多写入"的标准做法。
     */
    @Insert("<script>" +
            "INSERT INTO tb_order_item(order_id, product_id, product_name, product_image, price, quantity, subtotal) VALUES " +
            "<foreach collection='items' item='it' separator=','>" +
            "(#{it.orderId}, #{it.productId}, #{it.productName}, #{it.productImage}, #{it.price}, #{it.quantity}, #{it.subtotal})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("items") List<OrderItem> items);
}
