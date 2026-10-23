package com.mall.tdd.dto;

import lombok.Data;

/** 订单卡片里的一行商品。 */
@Data
public class OrderItemVO {

    private Long productId;
    private String productName;
    private String productImage;
    private Double price;
    private Integer quantity;
}
