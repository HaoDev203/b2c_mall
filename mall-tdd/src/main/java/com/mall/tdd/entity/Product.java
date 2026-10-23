package com.mall.tdd.entity;

import lombok.Data;

/** tb_product 映射。字段与 sql/mall-ddl.sql 一一对应。 */
@Data
public class Product {

    private Long id;
    private Long shopId;

    private String name;
    private String keyword;
    private String sellingPoint;

    private Long categoryId;
    private String mainImage;

    /** 销售价 */
    private Double price;
    /** 市场价（前端划线展示，后端只给数值） */
    private Double marketPrice;

    /** 总库存：库存预警与可售量的口径 */
    private Integer stock;
    /** 仓库库存：后台列表单独一列 */
    private Integer warehouseStock;
    /** 门店库存：后台列表单独一列 */
    private Integer storeStock;
    /** 预警阈值。NULL 表示不参与预警 */
    private Integer stockWarn;

    /** 真实销量 */
    private Integer sales;
    /** 注水销量，与真实销量分开返回 */
    private Integer inflatedSales;

    /** 商品类型：normal / seckill */
    private String goodsType;
    /** 商品类别：physical / virtual / card */
    private String categoryKind;
    /** 配送方式，逗号分隔 */
    private String deliveryTypes;

    private String productCode;
    /** draft / on_sale / off_sale */
    private String status;
    private Integer sort;
    private String createTime;
}
