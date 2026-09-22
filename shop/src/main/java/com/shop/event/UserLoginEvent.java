package com.shop.event;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用户登录事件。
 * ★ 阶段 5 新增 userType：admin（后台员工）/ buyer（前台买家）。
 *   ★★ 观察者**不解析**这个字段的含义，只是原样转交给 AccountStoreResolver ——
 *      它是"路由的钥匙"，不是"业务判断的依据"。
 */
@Data
@AllArgsConstructor
public class UserLoginEvent {
    private Long   userId;
    private Long   shopId;      // 前台买家为 null
    private String username;
    private String userType;    // ★ 阶段 5 新增：admin / buyer
    private String ip;
    private String device;      // 浏览器 UA
    private String loginTime;   // String
}
