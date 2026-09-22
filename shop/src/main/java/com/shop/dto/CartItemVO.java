package com.shop.dto;

import lombok.Data;

/**
 * 购物车列表项。
 * ★ 为什么不直接返回 Cart 实体？
 *   因为购物车里只存了 productId，前端要显示商品名 / 图片 / 单价 / 小计，
 *   这些必须从 tb_product 查出来拼上去。
 *   ★ 叫 VO（View Object）而不是 DTO，是为了区分"进"和"出"：
 *     DTO = 前端传进来的；VO = 我们返回给前端的。
 */
@Data
public class CartItemVO {
    private Long    cartId;       // 购物车条目 id（改数量 / 删除要用它）
    private Long    productId;
    private String  productName;
    private String  productImage;
    private Double  price;        // 当前单价
    private Integer quantity;
    private Double  subtotal;     // price × quantity
    private Integer stock;        // ★ 当前库存，前端要提示"库存不足"
    private Boolean stockEnough;  // ★ quantity <= stock
}
