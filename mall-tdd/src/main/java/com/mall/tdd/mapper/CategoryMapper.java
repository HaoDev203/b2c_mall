package com.mall.tdd.mapper;

import com.mall.tdd.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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

    /**
     * 分类是否存在。批量设置分类前的校验（P19）。
     *
     * <p>★ 只查一次，不是每条商品查一次 —— 3 条商品就多 2 次无谓往返，
     * 而分类存不存在跟查几次没关系。
     *
     * <p>★ 这个校验不能省：把商品指到一个不存在的分类上，改完商品就从
     * 所有分类筛选项里消失了（按 category_id 查不到），商家会以为商品被删了。
     */
    @Select("SELECT COUNT(*) FROM tb_category WHERE id = #{id}")
    int countById(@Param("id") Long id);
}
