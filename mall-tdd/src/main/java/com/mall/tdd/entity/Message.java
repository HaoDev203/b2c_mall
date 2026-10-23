package com.mall.tdd.entity;

import lombok.Data;

/**
 * 消息通知表 tb_message。
 */
@Data
public class Message {

    private Long id;

    /** 店铺隔离维度 —— 消息表最容易漏这个条件，写成「查全部消息」 */
    private Long shopId;

    /** pay_success / refund_apply / confirm_receive */
    private String type;

    /** 关联订单编号，前端点击跳转用 */
    private String orderNo;

    /** 完整文案，存库不拼装 */
    private String content;

    private Integer isRead;

    private String createTime;
}
