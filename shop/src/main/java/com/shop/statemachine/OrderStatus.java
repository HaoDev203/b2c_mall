package com.shop.statemachine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单状态常量。
 * ★ 阶段 4 的状态机 和 阶段 6 的状态模式，共用这一套状态码。
 * ★ 全项目禁止手写 "PENDING_SHIP" 字符串字面量，一律用这里的常量。
 */
public final class OrderStatus {

    private OrderStatus() {}

    public static final String PENDING_PAY     = "PENDING_PAY";      // 待付款
    public static final String PENDING_SHIP    = "PENDING_SHIP";     // 待发货
    public static final String PENDING_RECEIVE = "PENDING_RECEIVE";  // 待收货
    public static final String SUCCESS         = "SUCCESS";          // 交易成功
    public static final String FAILED          = "FAILED";           // 交易失败
    public static final String REFUNDING       = "REFUNDING";        // 待退款

    /** 码 → 中文名。用 LinkedHashMap 保证 JSON 输出的顺序稳定（前端 Tab 顺序） */
    private static final Map<String, String> CN = new LinkedHashMap<>();

    static {
        CN.put(PENDING_PAY,     "待付款");
        CN.put(PENDING_SHIP,    "待发货");
        CN.put(PENDING_RECEIVE, "待收货");
        CN.put(SUCCESS,         "交易成功");
        CN.put(FAILED,          "交易失败");
        CN.put(REFUNDING,       "待退款");
    }

    /** 报错信息 / 前端展示用。查不到就原样返回，方便排查拼错的码 */
    public static String cn(String code) {
        return code == null ? "" : CN.getOrDefault(code, code);
    }

    public static Map<String, String> all() {
        return Collections.unmodifiableMap(CN);
    }
}
