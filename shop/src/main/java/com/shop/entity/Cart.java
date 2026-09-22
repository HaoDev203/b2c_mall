package com.shop.entity;

import lombok.Data;

/** 购物车条目 tb_cart */
@Data
public class Cart {
    private Long    id;
    private Long    userId;       // user_id，买家
    private Long    productId;    // product_id
    private Integer quantity;
    private String  createTime;   // create_time
    private String  updateTime;   // update_time
}
