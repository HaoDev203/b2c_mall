package com.mall.tdd.dto;

import lombok.Data;

/**
 * C 端商品卡片。
 *
 * <p>只带页面要用的字段，不把整个 Product 抛给前端：
 * shopId / stockWarn / productCode 这些属于后台，暴露出去没有意义。
 */
@Data
public class MallProductVO {

    private Long id;
    private String name;

    /** 主图 */
    private String mainImage;

    /** 售价（前端红色字体，后端只给数值） */
    private Double price;

    /** 市场价（前端划线，后端只给数值） */
    private Double marketPrice;

    /** 销量 */
    private Integer sales;

    /** 状态：售罄角标/状态标签用；C01 也靠它断言「结果里没有非上架商品」 */
    private String status;
}
