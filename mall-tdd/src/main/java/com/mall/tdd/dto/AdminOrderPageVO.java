package com.mall.tdd.dto;

import lombok.Data;

import java.util.List;

/** 后台订单查询页：列表 + 总数 + 7 个 Tab */
@Data
public class AdminOrderPageVO {

    private List<AdminOrderVO> list;

    /** 与列表同口径的总数（同样的 11 个条件 + 同一个 shopId），否则「共 N 条」和翻页对不上 */
    private long total;

    private AdminTabCountVO tabs;
}
