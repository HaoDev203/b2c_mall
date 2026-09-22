package com.shop.entity;

import lombok.Data;

/** 状态流转留痕 tb_order_status_log */
@Data
public class OrderStatusLog {
    private Long   id;
    private Long   orderId;
    private String fromStatus;
    private String toStatus;
    private String event;
    private String operator;
    private String remark;
    private String createTime;
}
