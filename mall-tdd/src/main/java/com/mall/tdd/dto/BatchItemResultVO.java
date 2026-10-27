package com.mall.tdd.dto;

import lombok.Data;

/** 批量操作的单条结果：哪一条成功、哪一条为什么失败 */
@Data
public class BatchItemResultVO {

    private Long id;

    private boolean success;

    /** 失败原因。成功时为 null */
    private String message;
}
