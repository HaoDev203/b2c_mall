package com.mall.tdd.dto;

import lombok.Data;

import java.util.List;

/** C 端订单列表：当前页 + 筛选后总数 + Tab 计数（一次请求全给，避免前端连发 5 个请求）。 */
@Data
public class OrderListVO {

    private List<OrderVO> orders;
    /** 与列表同口径的总数：必须一起过滤 is_deleted，否则「共 N 条」对不上 */
    private long total;
    private TabCountVO tabs;
}
