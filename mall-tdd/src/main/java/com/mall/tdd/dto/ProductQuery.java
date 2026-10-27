package com.mall.tdd.dto;

import lombok.Data;

import java.util.List;

/**
 * 后台商品列表的 6 个筛选条件。
 *
 * <p>★★ 三个最容易混淆的字段，写在这里一次说清：
 * <ul>
 *   <li>{@code goodsType} = <b>商品类型</b>（普通 / 秒杀）→ 落库 {@code goods_type}</li>
 *   <li>{@code categoryKind} = <b>商品类别</b>（实物 / 虚拟 / 卡券）→ 落库 {@code category_kind}</li>
 *   <li>{@code categoryId} = <b>商品分类</b>（类目树上的节点）→ 落库 {@code category_id}</li>
 * </ul>
 * 「类型 / 类别 / 分类」中文只差一个字，一期末 shop 用同一个 {@code productType}
 * 字段同时背前两个概念，是 16 处需求缺口里唯一的「结构性」问题。
 *
 * <p>金额用 {@code Double}、库存用 {@code Integer}，与实体保持一致。
 */
@Data
public class ProductQuery {

    /** 商品名称关键词。★ 传裸词，% 由 SQL 拼 */
    private String keyword;

    /** 商品类型：normal / seckill */
    private String goodsType;

    /** 商品类别：physical / virtual / card */
    private String categoryKind;

    /** 状态：on_sale / off_sale */
    private String status;

    /** 配送方式，多选。空集合 = 不筛（不是「什么都不匹配」） */
    private List<String> deliveryTypes;

    /** 商品分类 id */
    private Long categoryId;
}
