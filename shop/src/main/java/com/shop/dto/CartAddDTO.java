package com.shop.dto;

import lombok.Data;

/** 加入购物车 */
@Data
public class CartAddDTO {
    private Long    productId;   // product_id
    private Integer quantity;    // 不传默认 1
}
