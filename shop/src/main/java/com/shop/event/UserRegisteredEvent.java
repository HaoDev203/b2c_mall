package com.shop.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserRegisteredEvent {
    private Long   userId;
    private Long   shopId;      // 前台买家为 null
    private String username;
    private String userType;    // ★ 阶段 5 新增：admin / buyer
}
