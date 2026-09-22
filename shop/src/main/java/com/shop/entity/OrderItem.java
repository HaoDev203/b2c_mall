package com.shop.entity;

import lombok.Data;

/** 订单明细 tb_order_item */
@Data
public class OrderItem {
    private Long    id;
    private Long    orderId;
    private Long    productId;
    private String  productName;    // ★ 下单时的商品快照
    private String  productImage;
    private Double  price;
    private Integer quantity;
    private Double  subtotal;
}
