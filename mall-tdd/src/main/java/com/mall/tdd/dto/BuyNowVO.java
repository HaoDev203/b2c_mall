package com.mall.tdd.dto;

import lombok.Data;

/** 「立即购买」跳下单页所需的商品信息。只有仍在售且有货的商品才会返回。 */
@Data
public class BuyNowVO {

    private Long productId;
    private String name;
    private Double price;
    private Integer stock;
    private String status;
}
