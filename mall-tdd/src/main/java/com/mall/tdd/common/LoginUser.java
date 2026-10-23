package com.mall.tdd.common;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录身份。
 * <p>B 端商家账号有 shopId；C 端买家 shopId 为 null（买家不属于任何店铺）。
 */
@Data
@AllArgsConstructor
public class LoginUser {

    /** 登录用户 id。C 端订单按它隔离，B 端操作人也是它 */
    private Long id;

    /** 所属店铺 id。C 端买家为 null —— 隔离维度不同，不能混用 */
    private Long shopId;

    private String name;
}
