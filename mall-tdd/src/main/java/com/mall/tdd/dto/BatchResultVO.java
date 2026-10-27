package com.mall.tdd.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量设置结果：{@code {total, success, failed, results[]}}。
 *
 * <p>★★ 结构性约定：<b>这个接口永远返回 code=200，除非未登录或整批参数非法。</b>
 * 部分失败通过 {@code failed} + {@code results} 表达，不靠抛异常。
 *
 * <p>为什么：批量操作不加 {@code @Transactional}，3 条里 1 条失败，另外 2 条<b>已经改成功了</b>。
 * 若返回 500，前端只看到「操作失败」，商家会整批重来 —— 又改一遍已成功的那 2 条，
 * 也不知道到底哪条没生效。
 */
@Data
public class BatchResultVO {

    private Integer total;

    private Integer success;

    private Integer failed;

    /** 每条的明细，顺序与提交顺序一致 */
    private List<BatchItemResultVO> results;
}
