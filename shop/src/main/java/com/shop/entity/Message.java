package com.shop.entity;

import lombok.Data;

@Data
public class Message {
    private Long    id;
    private Long    shopId;          // shop_id
    private Long    senderId;        // sender_id，系统消息可为 null
    private String  title;           // title
    private String  content;         // content
    private Integer msgType;         // msg_type：1-系统通知 2-个人消息 3-公告
    private Integer isRead;          // ★ is_read，用 Integer，不要用 boolean
    private String  readTime;        // read_time，String！
    private String  createdAt;       // created_at
    private String  updatedAt;       // updated_at
}
