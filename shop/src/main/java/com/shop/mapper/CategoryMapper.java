package com.shop.mapper;

import com.shop.entity.Category;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CategoryMapper {

    @Select("SELECT * FROM tb_category WHERE id = #{id}")
    Category selectById(Long id);

    /** 前台分类导航：不传 level 就返回全部 */
    @Select("SELECT * FROM tb_category WHERE (#{level,jdbcType=INTEGER} IS NULL OR level = #{level,jdbcType=INTEGER}) " +
            "ORDER BY level, id")
    java.util.List<Category> selectByLevel(@Param("level") Integer level);
}
