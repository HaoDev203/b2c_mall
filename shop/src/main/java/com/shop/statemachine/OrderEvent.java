package com.shop.statemachine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 状态流转事件常量。
 * ★ 状态机的调用方只传"事件"（要做什么），不传"目标状态"（改成什么）——
 *   这是状态机和「直接改 status 字段」最本质的区别。
 */
public final class OrderEvent {

    private OrderEvent() {}

    public static final String PAY_SUCCESS    = "PAY_SUCCESS";     // 支付成功
    public static final String CANCEL_TIMEOUT = "CANCEL_TIMEOUT";  // 超时未支付取消
    public static final String SHIP           = "SHIP";            // 后台发货
    public static final String CONFIRM        = "CONFIRM";         // 确认收货
    public static final String APPLY_REFUND   = "APPLY_REFUND";    // 申请售后

    private static final Map<String, String> CN = new LinkedHashMap<>();

    static {
        CN.put(PAY_SUCCESS,    "支付成功");
        CN.put(CANCEL_TIMEOUT, "超时取消");
        CN.put(SHIP,           "发货");
        CN.put(CONFIRM,        "确认收货");
        CN.put(APPLY_REFUND,   "申请售后");
    }

    public static String cn(String code) {
        return code == null ? "" : CN.getOrDefault(code, code);
    }

    public static Map<String, String> all() {
        return Collections.unmodifiableMap(CN);
    }
}
