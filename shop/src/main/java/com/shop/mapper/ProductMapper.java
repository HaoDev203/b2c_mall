package com.shop.mapper;

import com.shop.entity.Product;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface ProductMapper {

    /**
     * 保存商品。
     * ★ @Options(useGeneratedKeys = true, keyProperty = "id")
     *   让 insert 之后 product.getId() 自动被回填为数据库生成的主键 ——
     *   ShopMapper 里就是这么用的（注册时 shop.getId() 回填后赋给 employee.shopId）。
     */
    @Insert("INSERT INTO tb_product(shop_id, name, keyword, selling_point, category_id, shop_category_id, " +
            "product_type, main_image, video, brand, price, market_price, stock, stock_warn, product_code, " +
            "valid_end_time, status, sort, create_time) " +
            "VALUES(#{shopId}, #{name}, #{keyword}, #{sellingPoint}, #{categoryId}, #{shopCategoryId}, " +
            "#{productType}, #{mainImage}, #{video}, #{brand}, #{price}, #{marketPrice}, #{stock}, #{stockWarn}, " +
            "#{productCode}, #{validEndTime}, #{status}, #{sort}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Product product);

    /**
     * 上架 / 下架。
     * ★ WHERE 里带 shop_id：和 MessageMapper.markRead 同一个思路，防止改到别人店铺的商品。
     */
    @Update("UPDATE tb_product SET status = #{status} WHERE id = #{id} AND shop_id = #{shopId}")
    int updateStatus(@Param("id") Long id, @Param("shopId") Long shopId, @Param("status") String status);

    /**
     * 后台商品列表：多条件筛选 + 分页。
     * ★ 写法讲解：`#{status} IS NULL OR status = #{status}` 是"参数为空就不筛这个条件"的经典 SQL 技巧。
     *   好处是**不用写 <script> 动态 SQL**，一条静态语句搞定所有组合。
     */
    @Select("SELECT * FROM tb_product WHERE shop_id = #{shopId} " +
            "AND (#{status,jdbcType=VARCHAR} IS NULL OR status = #{status,jdbcType=VARCHAR}) " +
            "AND (#{productType,jdbcType=VARCHAR} IS NULL OR product_type = #{productType,jdbcType=VARCHAR}) " +
            "AND (#{categoryId,jdbcType=INTEGER} IS NULL OR category_id = #{categoryId,jdbcType=INTEGER}) " +
            "AND (#{keyword,jdbcType=VARCHAR} IS NULL OR name LIKE '%' || #{keyword,jdbcType=VARCHAR} || '%') " +
            "ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<Product> selectByPage(@Param("shopId") Long shopId,
                               @Param("status") String status,
                               @Param("productType") String productType,
                               @Param("categoryId") Long categoryId,
                               @Param("keyword") String keyword,
                               @Param("size") int size,
                               @Param("offset") int offset);

    // ==================== 阶段 5：前台商品查询 ====================

    /**
     * 前台商品列表：只查已上架商品，支持分类筛选、关键词搜索、三种排序、分页。
     *
     * ★★ 为什么要用 ${orderBy} 而不是 #{orderBy}？
     *    #{} 是「预编译参数占位符」，只能替换**值**（如 WHERE price = ?），
     *    而 ORDER BY 后面跟的是**列名/表达式**，JDBC 传不进去，只能字符串拼接。
     *    ${} 就是字符串拼接 —— 所以**天然有 SQL 注入风险**。
     *
     *    这里安全的唯一原因是：orderBy 的值**绝不来自用户输入**，
     *    而是 Service 层从一张写死的白名单 Map 里取出来的（见 MallProductService）。
     *    用户传 sort=xxx 或者 sort=id;DROP TABLE，只会命中白名单兜底值。
     *
     *    ★ 铁律：${} 只能用在「表名/列名/排序表达式」这类不能参数化的地方，
     *      并且值必须来自白名单 —— 永远不要直接把前端传的字符串塞进 ${}。
     */
    @Select("SELECT * FROM tb_product WHERE status = 'on_sale' " +
            "AND (#{categoryId,jdbcType=INTEGER} IS NULL OR category_id = #{categoryId,jdbcType=INTEGER}) " +
            "AND (#{keyword,jdbcType=VARCHAR} IS NULL OR name LIKE '%' || #{keyword,jdbcType=VARCHAR} || '%') " +
            "ORDER BY ${orderBy} LIMIT #{size} OFFSET #{offset}")
    List<Product> selectOnSalePage(@Param("categoryId") Long categoryId,
                                   @Param("keyword") String keyword,
                                   @Param("orderBy") String orderBy,
                                   @Param("size") int size,
                                   @Param("offset") int offset);

    /** 前台列表的 total —— 分页组件要显示总条数和总页数 */
    @Select("SELECT COUNT(*) FROM tb_product WHERE status = 'on_sale' " +
            "AND (#{categoryId,jdbcType=INTEGER} IS NULL OR category_id = #{categoryId,jdbcType=INTEGER}) " +
            "AND (#{keyword,jdbcType=VARCHAR} IS NULL OR name LIKE '%' || #{keyword,jdbcType=VARCHAR} || '%')")
    long countOnSale(@Param("categoryId") Long categoryId, @Param("keyword") String keyword);

    /** 商品详情 —— 前台详情页用，只放行已上架的 */
    @Select("SELECT * FROM tb_product WHERE id = #{id} AND status = 'on_sale'")
    Product selectOnSaleById(Long id);

    // ==================== 阶段 6：下单扣库存 ====================

    /**
     * ★★ 扣库存（下单时用）—— 防超卖的关键。
     *
     * 为什么不能写成「先 select 查库存，Java 里判断，再 update」？
     *   两个并发请求都读到 stock=10，都判定 10 >= 8 成立，于是都扣 → 最后扣成 2，超卖了。
     *   把判断塞进 WHERE（stock >= #{qty}）之后，这条 UPDATE 是原子的：
     *   只有一个人能拿到 rows=1，另一个 rows=0 → 我们据此抛异常回滚。
     *
     * ★ 这个手法和阶段 4 的 `WHERE status = 原状态`（乐观锁）完全一样：
     *   都是把「检查」和「执行」合成一条原子语句。
     */
    @Update("UPDATE tb_product SET stock = stock - #{qty}, sales = sales + #{qty} " +
            "WHERE id = #{id} AND stock >= #{qty}")
    int decreaseStock(@Param("id") Long id, @Param("qty") int qty);

    /** 回滚库存（订单取消 / 支付失败时把库存还回去，sales 也还回去） */
    @Update("UPDATE tb_product SET stock = stock + #{qty}, sales = sales - #{qty} WHERE id = #{id}")
    int increaseStock(@Param("id") Long id, @Param("qty") int qty);
}
