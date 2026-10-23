package com.mall.tdd.dto;

import lombok.Data;

/**
 * 后台消息通知。
 *
 * <p>★ {@code orderNo} 是需求「点击单条消息跳转订单详情页」的前提字段，
 * 缺了整条链路断开。shop 的 Message 实体只有 title/content/msgType，没有它。
 *
 * <p>★ {@code content} 是<b>存库的完整文本</b>，原样透传，不在运行时拼 ——
 * 拼的话订单号一变，历史消息内容也会跟着变。
 */
@Data
public class MessageVO {

    private Long id;

    /** pay_success 支付成功 / refund_apply 退款申请 / confirm_receive 确认收货 */
    private String type;

    /** 关联订单编号 */
    private String orderNo;

    /** 完整文案（存库，含单号） */
    private String content;

    /** 0 未读 / 1 已读 */
    private Integer isRead;

    private String createTime;
}
