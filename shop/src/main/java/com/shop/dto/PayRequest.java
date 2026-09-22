package com.shop.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 支付入参（策略模式的"上下文数据"）。
 * ★ 三个策略收到的**入参完全一样**，区别只在内部实现 ——
 *   这正是策略模式成立的前提：**接口统一，算法不同**。
 */
@Data
@Builder
public class PayRequest {
    private Long   orderId;
    private String orderNo;
    private Double amount;        // 应付金额（人民币）
    private String buyerAccount;  // 买家账号 / 邮箱
}
