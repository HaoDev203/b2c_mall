package com.shop.dto;

import lombok.Data;

/** 提交订单（step02 → step03） */
@Data
public class OrderSubmitDTO {
    private String cartIds;           // 逗号分隔的购物车 id（如 "1,2,3"），null = 全部
    private String receiverName;      // receiver_name
    private String receiverPhone;     // receiver_phone
    private String receiverAddress;   // receiver_address
    private String remark;            // 订单备注
    private String payType;           // alipay / wechat / paypal / other
}
