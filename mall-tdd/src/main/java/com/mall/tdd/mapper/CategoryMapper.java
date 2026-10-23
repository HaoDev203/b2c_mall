package com.mall.tdd.mapper;

import com.mall.tdd.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CategoryMapper {

    /**
     * 取全部分类。
     *
     * <p>★ 刻意不做 DISTINCT：同名不同 id 的行必须原样取回来，
     * 去重放在 Service 层按 name 保留首条（见 MallCategoryService C02 的注释）。
     */
    @Select("SELECT * FROM tb_category ORDER BY sort DESC, id ASC")
    List<Category> selectAll();
}
