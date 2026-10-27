package com.mall.tdd.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单明细（{@code tb_order_item}）。
 *
 * <p>本模块只用到一件事：<b>这个商品有没有被卖出过</b>（P13 删除前置校验）。
 * 不在这里做订单明细的增删改 —— 那是下单流程的事，不在本期范围。
 */
@Mapper
public interface OrderItemMapper {

    /**
     * 商品被订单引用的次数。
     *
     * <p>★ 返回 {@code long} 而不是 {@code int}：{@code COUNT(*)} 在 MyBatis 里
     * 默认映射成 Long，写 int 会多一次拆箱转换，条数超过 21 亿时还会溢出（虽然不可能，
     * 但没必要造一个能溢出的接口）。
     *
     * <p>★ 这里<b>不过滤订单状态</b>：只要产生过订单就禁止删除商品，
     * 不管那单是已取消还是已完成 —— 取消的订单后台仍要能查明细，
     * 商品删了明细里的 {@code product_id} 就成了悬空外键。
     */
    @Select("SELECT COUNT(*) FROM tb_order_item WHERE product_id = #{productId}")
    long countByProductId(@Param("productId") Long productId);
}
