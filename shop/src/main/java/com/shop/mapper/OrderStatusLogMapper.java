package com.shop.mapper;

import com.shop.entity.OrderStatusLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface OrderStatusLogMapper {

    @Insert("INSERT INTO tb_order_status_log(order_id, from_status, to_status, event, operator, remark, create_time) " +
            "VALUES(#{orderId}, #{fromStatus}, #{toStatus}, #{event}, #{operator}, #{remark}, #{createTime})")
    int insert(OrderStatusLog log);

    /** 订单详情里展示"流转轨迹" */
    @Select("SELECT * FROM tb_order_status_log WHERE order_id = #{orderId} ORDER BY id")
    List<OrderStatusLog> selectByOrderId(@Param("orderId") Long orderId);
}
