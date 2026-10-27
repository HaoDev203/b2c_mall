package com.mall.tdd.dto;

import lombok.Data;

/**
 * 后台商品列表行（P10 的 12 个必填字段 + id / sort / productCode）。
 *
 * <p>★ 两组字段<b>必须分开返回</b>，不能合并：
 * <ul>
 *   <li>{@code sales}（真实成交销量）与 {@code inflatedSales}（注水销量）。
 *       合并后商家再也分不清真实经营数据（P11）。</li>
 *   <li>{@code warehouseStock}（中心仓库）与 {@code storeStock}（门店可售）。
 *       两者相差几十倍是常态，合成一个数就没有意义（P12）。</li>
 * </ul>
 */
@Data
public class AdminProductVO {

    private Long id;

    /** 主图 URL。前端负责缩略图裁切 */
    private String mainImage;

    private String name;

    /** 市场价。前端划线展示，后端只给数值 */
    private Double marketPrice;

    /** 销售价 */
    private Double price;

    /** 真实销量 */
    private Integer sales;
    /** 注水销量（商家可配置），与真实销量独立 */
    private Integer inflatedSales;

    /** 仓库库存 */
    private Integer warehouseStock;
    /** 门店库存 */
    private Integer storeStock;

    /** 商品类型：normal / seckill */
    private String goodsType;
    /** 商品类别：physical / virtual / card */
    private String categoryKind;

    /** 上架状态 */
    private String status;

    /**
     * 配送方式，<b>逗号分隔原样下发</b>（如 {@code "express,self_pickup"}）。
     * 前端按逗号拆成多选框。不在这里拆成 List：拆了就要处理空串、重复值，
     * 而前端本来就要自己渲染勾选态。
     */
    private String deliveryTypes;

    /** 排序值，数字越大越靠前。随行返回方便前端调试排序 */
    private Integer sort;

    /** 商品编码，导入时的唯一识别依据 */
    private String productCode;
}
