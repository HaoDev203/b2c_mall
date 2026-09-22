package com.shop.dto;

import lombok.Data;

/** 修改购物车数量（quantity=0 等价于删除） */
@Data
public class CartUpdateDTO {
    private Long    cartId;      // cart_id
    private Integer quantity;
}
