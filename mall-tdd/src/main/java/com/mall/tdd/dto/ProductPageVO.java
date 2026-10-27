package com.mall.tdd.dto;

import lombok.Data;

import java.util.List;

/**
 * 后台商品列表的分页结果。
 *
 * <p>★ {@code total} 必须来自<b>独立的 count 查询</b>，且带上同一组筛选条件。
 * 拿当前页条数当总数，前端会算成「共 10 条」，翻到第 2 页就没了。
 */
@Data
public class ProductPageVO {

    private Long total;

    private List<AdminProductVO> list;
}
