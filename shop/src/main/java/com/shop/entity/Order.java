package com.shop.entity;

import lombok.Data;

/** 订单主表 tb_order */
@Data
public class Order {
    private Long   id;
    private String orderNo;
    private Long   userId;
    private Long   shopId;
    private Double totalAmount;
    private Double payAmount;
    private String status;              // ★ 状态机的"当前状态"
    private String payType;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String shippingStatus;
    private String afterSaleStatus;
    private String remark;
    private String createTime;
    private String payTime;
    private String shipTime;
}
