package com.shop.entity;

import lombok.Data;

@Data
public class Category {
    private Long    id;
    private Long    parentId;     // parent_id，顶级为 0
    private String  name;
    private Integer level;        // 1/2/3
    private String  type;         // platform / shop
}
