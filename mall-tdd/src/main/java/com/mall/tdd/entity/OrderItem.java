package com.mall.tdd.entity;

import lombok.Data;

/** tb_order_item 映射。商品名称/图片/单价都是下单时的快照。 */
@Data
public class OrderItem {

    private Long id;
    private Long orderId;
    private Long productId;

    private String productName;
    private String productImage;

    private Double price;
    /** 数量：「共 N 件」= 本字段之和，不是行数 */
    private Integer quantity;
    private Double subtotal;
}
