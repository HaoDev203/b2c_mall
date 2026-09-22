package com.shop.entity;

import lombok.Data;

/** 促销规则 tb_promotion（满减） */
@Data
public class Promotion {
    private Long    id;
    private Long    shopId;       // 0 = 全平台通用
    private String  name;
    private Double  threshold;    // 门槛
    private Double  discount;     // 减免
    private Integer status;       // 1-启用
    private Integer sort;
}
