package com.shop.dto;

import lombok.Data;

/** 发货 / 确认收货 / 取消 的入参：{ "orderId": 1 } */
@Data
public class ShipDTO {
    private Long orderId;
}
