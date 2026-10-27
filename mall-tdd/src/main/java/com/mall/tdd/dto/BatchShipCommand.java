package com.mall.tdd.dto;

import lombok.Data;

import java.util.List;

/** 批量发货入参。orderIds 用 List 走请求体，避免 {@code ?orderIds=1,2,3} 字符串拼接 */
@Data
public class BatchShipCommand {

    private List<Long> orderIds;
    /** 可选：同一次批量共用一个运单号的场景（多数情况每家快递单独回传，这里是便利） */
    private String trackingNo;
}
