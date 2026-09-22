package com.shop.entity;

import lombok.Data;

/**
 * ★ 状态机规则：一行 = 一条「合法流转」。
 * 判据就是这张表 —— 规则在数据里，不在代码里。
 */
@Data
public class StateMachineRule {
    private Long   id;
    private String bizType;      // order
    private String fromStatus;   // 当前状态
    private String event;        // 收到的事件
    private String toStatus;     // 允许去的目标状态
    private String remark;
}
