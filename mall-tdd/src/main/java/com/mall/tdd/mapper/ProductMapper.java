package com.mall.tdd.mapper;

import com.mall.tdd.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductMapper {

    /**
     * C 端商品列表：上架 + 可选分类 + 排序。
     *
     * <p>★ {@code status = 'on_sale'} 写死在 SQL 里，不通过参数传：
     * 让调用方能传 status，就等于允许它查出下架商品，C01 的约束会退化成「调用方自觉」。
     *
     * <p>★ {@code ${sortColumn}} / {@code ${sortDirection}} 用的是 ${} 而非 #{}：
     * ORDER BY 位置放 #{} 会变成 `ORDER BY 'price'` 常量，排序直接失效。
     * 安全性由 Service 的白名单保证 —— 这两个值只可能来自 {"sales","price","id"}
     * 与 {"ASC","DESC"}，用户输入的原文字段永远到不了这里。
     */
    @Select("<script>"
            + "SELECT * FROM tb_product WHERE status = 'on_sale' "
            + "<if test='categoryId != null'> AND category_id = #{categoryId} </if>"
            + "ORDER BY ${sortColumn} ${sortDirection} "
            + "LIMIT #{limit}"
            + "</script>")
    List<Product> selectOnSalePage(@Param("categoryId") Long categoryId,
                                   @Param("sortColumn") String sortColumn,
                                   @Param("sortDirection") String sortDirection,
                                   @Param("limit") int limit);

    /**
     * C 端商品详情。
     *
     * <p>★ 带 on_sale 条件：详情是按 id 直接查的，列表的 SQL 过滤管不到它，
     * 所以必须在查询本身再拦一次（C09）。
     */
    @Select("SELECT * FROM tb_product WHERE id = #{id} AND status = 'on_sale'")
    Product selectByIdAndOnSale(@Param("id") Long id);

    /**
     * 库存预警商品数（Dashboard 的 stockWarning 卡片）。
     *
     * <p>★ 口径两条，都是有意为之：
     * <ul>
     *   <li>{@code stock <= stock_warn} <b>含等于</b>。需求原文写「低于阈值」，
     *       但库存 3、阈值 3 按 {@code <} 算会漏掉刚好卡线的那批，按 {@code <=} 更符合
     *       商家直觉（演示数据里的电饭煲 stock=10、阈值=10 就是为了钉这条）。</li>
     *   <li>{@code stock_warn IS NULL} 的商品<b>有意不预警</b>。
     *       不要加 {@code OR stock_warn IS NULL} —— 那会让所有没配预警线的商品
     *       一上架就飘红，预警等于失效。</li>
     * </ul>
     *
     * <p>不限 status：需求原文只说「库存低于阈值的商品数量」，没提只算在售商品。
     */
    @Select("SELECT COUNT(*) FROM tb_product "
            + "WHERE shop_id = #{shopId} "
            + "  AND stock_warn IS NOT NULL "
            + "  AND stock <= stock_warn")
    long countStockWarning(@Param("shopId") Long shopId);
}
