package com.mall.tdd.dto;

import lombok.Data;

/**
 * 后台订单查询的 11 个筛选条件 + 1 个快捷时间。
 *
 * <p>★★ <b>这里刻意没有 {@code shopId}</b>。隔离维度只能来自
 * {@code UserContext}，一旦放进查询对象，它就成了请求参数，
 * 改个 {@code ?shopId=2} 就能看别家订单 —— O22 用反射守着这一点。
 *
 * <p>★★ 三组容易写混的字段，一次说清：
 * <ul>
 *   <li>{@code orderType} = <b>订单类型</b>（普通 / 拼团 / 预售）→ {@code order_type}</li>
 *   <li>{@code orderCategory} = <b>订单类别</b>（普通 / 电子卡券）→ {@code order_category}</li>
 *   <li>{@code logisticsType} = <b>物流类型</b>（这一单怎么送）→ {@code logistics_type}，
 *       <b>不是</b>商品的 {@code delivery_types}（商品支持哪几种配送方式，P05）</li>
 * </ul>
 *
 * <p>★ 同一个页面里匹配方式故意不一致：{@code orderNo} / {@code storeName} 精确，
 * {@code receiverName} / {@code receiverPhone} 模糊。写成统一的一种就是错。
 *
 * <p>用链式 setter 只是为了让测试一次性把 11 个条件铺开，业务上等价于逐个 set。
 */
@Data
public class AdminOrderQuery {

    /** 「暂无售后」的入参约定：库里存的是 NULL，传这个值由 Service 翻译成 IS NULL */
    public static final String AFTER_SALE_NONE = "NONE";

    /** 快捷时间：today / yesterday / last7 / last30。非空时覆盖手填的日期区间 */
    private String quickDate;

    /** 订单编号。★ 精确匹配 */
    private String orderNo;

    /** 订单类型：normal / group / presale */
    private String orderType;

    /** 订单类别：normal / e_card */
    private String orderCategory;

    /** 订单状态，取值见 {@code OrderStatus} */
    private String status;

    /** 收件人姓名。★ 模糊 */
    private String receiverName;

    /** 联系电话。★ 模糊 */
    private String receiverPhone;

    /** 支付方式：balance / wechat / alipay */
    private String payType;

    /** 售后状态：applying / done。{@link #AFTER_SALE_NONE} 表示「暂无售后」→ IS NULL */
    private String afterSaleStatus;

    /** 「暂无售后」开关：由 Service 从 afterSaleStatus = NONE 翻译而来，SQL 里走 IS NULL */
    private boolean afterSaleNone;

    /** 物流类型：express / city */
    private String logisticsType;

    /** 门店名称。★ 精确匹配（下拉选出来的就是全名） */
    private String storeName;

    /** 下单时间起，含。格式 YYYY-MM-DD */
    private String startDate;

    /** 下单时间止，含<b>当天整天</b>。格式 YYYY-MM-DD */
    private String endDate;

    public AdminOrderQuery quickDate(String v) {
        this.quickDate = v;
        return this;
    }

    public AdminOrderQuery orderNo(String v) {
        this.orderNo = v;
        return this;
    }

    public AdminOrderQuery orderType(String v) {
        this.orderType = v;
        return this;
    }

    public AdminOrderQuery orderCategory(String v) {
        this.orderCategory = v;
        return this;
    }

    public AdminOrderQuery status(String v) {
        this.status = v;
        return this;
    }

    public AdminOrderQuery receiverName(String v) {
        this.receiverName = v;
        return this;
    }

    public AdminOrderQuery receiverPhone(String v) {
        this.receiverPhone = v;
        return this;
    }

    public AdminOrderQuery payType(String v) {
        this.payType = v;
        return this;
    }

    public AdminOrderQuery afterSaleStatus(String v) {
        this.afterSaleStatus = v;
        return this;
    }

    public AdminOrderQuery logisticsType(String v) {
        this.logisticsType = v;
        return this;
    }

    public AdminOrderQuery storeName(String v) {
        this.storeName = v;
        return this;
    }

    public AdminOrderQuery range(String start, String end) {
        this.startDate = start;
        this.endDate = end;
        return this;
    }
}
