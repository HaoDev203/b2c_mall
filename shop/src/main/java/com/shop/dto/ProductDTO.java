package com.shop.dto;

import lombok.Data;

/**
 * 商品发布入参。
 * ★ 为什么要有 DTO 而不是直接用 Product 实体？
 *   因为前端能传的字段和数据库里能存的字段**不是一回事**：
 *   - Product 有 id / shopId / status / createTime —— 这几个**绝不能由前端传**（可以是 null，由服务端填）
 *   - DTO 里只留前端该传的字段，等于在入口处就把"能改什么"框死了
 */
@Data
public class ProductDTO {
    private String  name;
    private String  keyword;
    private String  sellingPoint;
    private Long    categoryId;
    private Long    shopCategoryId;
    private String  productType;      // physical / virtual / combo / card
    private String  mainImage;
    private String  video;
    private String  brand;
    private Double  price;
    private Double  marketPrice;
    private Integer stock;
    private Integer stockWarn;
    private String  productCode;      // 虚拟商品：卡券编号 / 资质编号
    private String  validEndTime;     // 虚拟商品：有效期 yyyy-MM-dd
    private Integer sort;
}
