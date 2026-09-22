package com.shop.dto;

import lombok.Data;

import java.util.List;

/**
 * 订单预览（原型图 9 的 step01 → step02 之间）。
 *
 * ★ 为什么要有"预览"这一步而不是直接下单？
 *   原型图 9 是三步流程：选商品 → 确认信息 → 支付。
 *   用户在 step02 要看到「商品总额 / 优惠 / 应付」，还可能返回 step01 改数量。
 *   所以"算钱"必须独立于"生成订单" —— 预览阶段**一行数据都不落库**。
 */
@Data
public class OrderPreviewVO {
    private List<CartItemVO> items;          // 本次要买的商品
    private Double totalAmount;              // 商品总额
    private Double discountAmount;           // 优惠金额（满减）
    private Double payAmount;                // 应付 = total - discount
    private String promotionName;            // 命中的促销名（没命中就是 null）
    private Boolean reachedThreshold;        // ★ 是否已达门槛（没达时前端提示"再买 ¥xx 可减 ¥120"）
    private Double gapToThreshold;           // ★ 差多少到门槛
}
