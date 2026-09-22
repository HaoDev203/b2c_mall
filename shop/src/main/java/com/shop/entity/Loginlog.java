package com.shop.entity;

import lombok.Data;

@Data
public class Loginlog {
    private Long    id;
    private Long    userId;          // user_id，外键 → tb_employee(id)
    private String  loginIp;         // login_ip
    private String  loginDevice;     // login_device
    private String  loginLocation;   // login_location，教学项目写"内网"
    private String  loginTime;       // login_time，String！
    private String  userAgent;       // user_agent
    private Integer status;          // 1-成功 0-失败
    private String  userType;        // ★ 阶段 5 新增：admin / buyer
}
