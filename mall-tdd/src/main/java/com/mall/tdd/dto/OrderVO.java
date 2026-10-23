package com.mall.tdd.dto;

import lombok.Data;

import java.util.List;

/** C 端订单卡片。对应需求「订单编号/店铺名称/商品图片名称数量单价/总额/状态/支付方式」。 */
@Data
public class OrderVO {

    private Long id;
    private String orderNo;
    /** 店铺名称快照。tb_order 只有 shop_id，靠冗余列避免每次 JOIN */
    private String shopName;

    private Double totalAmount;
    private String status;
    /** 待付款订单为 null 属正常 —— 还没付，自然没有支付方式 */
    private String payType;

    private String createTime;
    private List<OrderItemVO> items;
}
