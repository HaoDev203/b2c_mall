package com.mall.tdd.entity;

import lombok.Data;

/** tb_order 映射。字段与 sql/mall-ddl.sql 一一对应。 */
@Data
public class Order {

    private Long id;
    private String orderNo;

    /** C 端隔离维度 */
    private Long userId;
    /** 后台隔离维度 */
    private Long shopId;
    /** 店铺名称快照：下单时写入，店铺改名不影响历史订单 */
    private String shopName;

    private Double totalAmount;
    private Double payAmount;

    /** PENDING_PAY / PENDING_SHIP / PENDING_RECEIVE / PENDING_GROUP / SUCCESS / FAILED / REFUNDING */
    private String status;

    /** alipay / wechat / paypal / other。只在待发货及之后才有值 */
    private String payType;

    private String orderType;
    private String orderCategory;
    private String logisticsType;
    private String storeName;
    private String trackingNo;

    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;

    private String shippingStatus;
    private String afterSaleStatus;

    /** 买家软删除标记：1 = 买家侧隐藏，后台仍可见。物理删除禁止 */
    private Integer isDeleted;

    private String remark;
    private String createTime;
    private String payTime;
    private String shipTime;
}
