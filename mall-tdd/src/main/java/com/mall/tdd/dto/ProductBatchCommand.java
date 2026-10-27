package com.mall.tdd.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量设置命令（P16~P21）。
 *
 * <p>★ 所有修改项都是<b>可选</b>，null = 不修改该字段。这条约定是 P21b 的前提：
 * 商家只想改仓库库存时，{@code storeStock} 为 null，SQL 里的 {@code <if>}
 * 就不会碰门店库存那一列。
 *
 * <p>★ 例外是 {@code deliveryTypes}：<b>空集合 = 清空</b>，不是「不修改」。
 * 因为多选框全勾掉的语义就是「这商品不再支持任何配送方式」，
 * 若实现成「不修改」，商家怎么点都没反应。
 */
@Data
public class ProductBatchCommand {

    private List<Long> ids;

    /** 目标状态。null = 不改 */
    private String status;

    /** 目标配送方式。null = 不改；空集合 = 清空 */
    private List<String> deliveryTypes;

    /** 目标分类。null = 不改 */
    private Long categoryId;

    /** 目标排序值（统一置成同一个值）。null = 不改 */
    private Integer sort;

    /** 目标仓库库存。null = 不改 */
    private Integer warehouseStock;

    /** 目标门店库存。null = 不改 */
    private Integer storeStock;
}
