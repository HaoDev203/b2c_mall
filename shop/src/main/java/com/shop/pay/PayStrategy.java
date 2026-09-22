package com.shop.pay;

import com.shop.dto.PayRequest;
import com.shop.dto.PayResult;

/**
 * ★★ 支付策略接口（阶段 6 考点之一）
 *
 * 它要解决的问题：支付宝 / 微信 / PayPal 各自一套支付逻辑，运行时按用户选择切换。
 *
 * 策略模式的三个角色：
 *   ① 抽象策略 = 本接口（定义"支付"这个动作的统一契约）
 *   ② 具体策略 = AlipayStrategy / WechatPayStrategy / PaypalStrategy / OtherStrategy
 *   ③ 上下文   = PayStrategyFactory（持有策略，把请求转给策略）
 *
 * ★ 接口只有三个方法，分工很关键：
 *   getPayType() 是"身份证"，让工厂能找到它；
 *   getPayName() 是"中文名"，给收银台展示用；
 *   pay()        是"干活"，几种支付方式在这里分道扬镳。
 */
public interface PayStrategy {

    /** 支付方式标识，要和前端传的 payType 对上：alipay / wechat / paypal / other */
    String getPayType();

    /** 中文名（返回给前端收银台用，原型图 12 的四个选项） */
    String getPayName();

    /** 执行支付 */
    PayResult pay(PayRequest request);
}
