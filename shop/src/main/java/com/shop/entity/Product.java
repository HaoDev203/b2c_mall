package com.shop.entity;

import lombok.Data;

@Data
public class Product {
    private Long    id;
    private Long    shopId;            // shop_id
    private String  name;
    private String  keyword;
    private String  sellingPoint;      // selling_point
    private Long    categoryId;
    private Long    shopCategoryId;
    private String  productType;       // physical / virtual / combo / card
    private String  mainImage;
    private String  video;
    private String  brand;
    private Double  price;
    private Double  marketPrice;
    private Integer stock;
    private Integer stockWarn;
    private Integer sales;             // ★ 阶段 5 新增：真实销量（支付成功后累加）
    private Integer fakeSales;         // ★ 阶段 5 新增：fake_sales 注水销量
    private String  productCode;
    private String  validEndTime;      // valid_end_time
    private String  status;            // draft / on_sale / off_sale
    private Integer sort;
    private String  createTime;        // ★ 时间字段用 String，和 Employee / Message 保持一致
}
