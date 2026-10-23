package com.mall.tdd.entity;

import lombok.Data;

/** tb_category 映射。 */
@Data
public class Category {

    private Long id;
    private Long parentId;
    private String name;
    private Integer level;
    /** platform / shop */
    private String type;
    private Integer sort;
}
