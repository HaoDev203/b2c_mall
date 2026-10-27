package com.mall.tdd.dto;

import lombok.Data;

import java.util.List;

/**
 * 后台订单列表的一行。
 *
 * <p>★ 手机上只给<b>脱敏后</b>的 {@link #receiverPhoneMasked}，没有原号码字段。
 * 脱敏必须发生在后端：前端遮只是视觉效果，F12 一看响应体原文就在。
 * O16 用反射守着「不许出现 getReceiverPhone()」，防以后有人图方便加回去。
 */
@Data
public class AdminOrderVO {

    private Long id;
    private String orderNo;

    /** 店铺名称快照，不 JOIN tb_shop：店铺改名不该改变历史订单的展示 */
    private String shopName;

    private Double payAmount;
    private Double totalAmount;

    /**
     * 「共 N 件」。
     * ★ N = 明细 {@code quantity} 之和，<b>不是明细行数</b>：
     * 一条订单有「商品A x1 / 商品B x8」两行，共 9 件而不是 2 件（O15）。
     */
    private int totalQuantity;

    private String receiverName;

    /** 脱敏后：13800138000 → 138****8000。null / 非 11 位原样返回 */
    private String receiverPhoneMasked;

    private String receiverAddress;

    private String status;
    private String payType;
    private String orderType;
    private String orderCategory;
    private String logisticsType;
    private String storeName;
    private String trackingNo;
    private String afterSaleStatus;

    private String createTime;
    private String payTime;
    private String shipTime;

    /**
     * 标签，<b>可能是多个</b>：这 5 种标签混了 4 个维度（状态 / 售后 / 类别 / 物流），
     * 一条订单可以同时是「待发货 + 退款申请中 + 电子卡券 + 同城配送」（O17）。
     * 这里返回的是枚举码，中文文案由前端映射 —— 改文案不该改后端。
     */
    private List<String> tags;

    private List<OrderItemVO> items;
}
