package com.mall.tdd.mapper;

import com.mall.tdd.entity.Product;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    // ==================================================================
    // 下方为 B4 店铺商品管理（后台）
    // ==================================================================

    /**
     * 后台商品列表（P01~P09）。
     *
     * <p>★ 6 个筛选条件全部是 {@code <if>}，且全部用 #{} 预编译占位（不是 ${}）：
     * <ul>
     *   <li>名称用 {@code LIKE '%' || #{keyword} || '%'} —— <b>% 由 SQL 拼</b>。
     *       Service 传的是裸关键词，传 {@code "%手机%"} 会变成 {@code %%手机%%}，
     *       能查但索引失效。</li>
     *   <li>{@code goods_type}（类型）与 {@code category_kind}（类别）是<b>两个独立维度</b>，
     *       别合并成一个参数。</li>
     *   <li>配送方式是多选，用 {@code <foreach>} 展开。★ 但<b>不能写成 {@code delivery_type IN (...)}</b>：
     *       {@code delivery_types} 是逗号分隔列（"express,self_pickup"），
     *       IN 要求列是单值，多选一个都匹配不上。所以按<b>逗号边界整个词匹配</b>：
     *       {@code (',' || delivery_types || ',') LIKE '%,' || ? || ',%'}。
     *       不加边界的话 {@code express} 会误命中 {@code express_fast}。</li>
     * </ul>
     *
     * <p>★ 排序写死 {@code sort DESC}：需求原文「排序：数字，数字越大前台展示越靠前」，
     * 方向是需求指定的，不需要白名单。附带的 {@code id DESC} 只是为了让同 sort 值的
     * 商品在翻页时顺序稳定 —— 没有它，LIMIT/OFFSET 会漏行或重复。
     */
    @Select("<script>"
            + "SELECT * FROM tb_product WHERE shop_id = #{shopId} "
            + "<if test='keyword != null'> AND name LIKE '%' || #{keyword} || '%' </if>"
            + "<if test='goodsType != null'> AND goods_type = #{goodsType} </if>"
            + "<if test='categoryKind != null'> AND category_kind = #{categoryKind} </if>"
            + "<if test='status != null'> AND status = #{status} </if>"
            + "<if test='deliveryTypes != null'> AND ( "
            + "<foreach collection='deliveryTypes' item='dt' separator=' OR '>"
            + "(',' || delivery_types || ',') LIKE '%,' || #{dt} || ',%'"
            + "</foreach> ) </if>"
            + "<if test='categoryId != null'> AND category_id = #{categoryId} </if>"
            + "ORDER BY sort DESC, id DESC "
            + "LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Product> selectAdminPage(@Param("shopId") Long shopId,
                                 @Param("keyword") String keyword,
                                 @Param("goodsType") String goodsType,
                                 @Param("categoryKind") String categoryKind,
                                 @Param("status") String status,
                                 @Param("deliveryTypes") List<String> deliveryTypes,
                                 @Param("categoryId") Long categoryId,
                                 @Param("limit") int limit,
                                 @Param("offset") int offset);

    /**
     * 后台列表总数。
     *
     * <p>★★ 条件必须与 {@link #selectAdminPage} <b>逐条一致</b>。
     * 列表筛了、总数没筛，前端会显示「共 300 条」却只翻得出 3 页 ——
     * 这类 bug 在页面上看起来像「数据丢了」，排查成本极高。
     * 两段 SQL 刻意保持同样的书写顺序，方便对照。
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM tb_product WHERE shop_id = #{shopId} "
            + "<if test='keyword != null'> AND name LIKE '%' || #{keyword} || '%' </if>"
            + "<if test='goodsType != null'> AND goods_type = #{goodsType} </if>"
            + "<if test='categoryKind != null'> AND category_kind = #{categoryKind} </if>"
            + "<if test='status != null'> AND status = #{status} </if>"
            + "<if test='deliveryTypes != null'> AND ( "
            + "<foreach collection='deliveryTypes' item='dt' separator=' OR '>"
            + "(',' || delivery_types || ',') LIKE '%,' || #{dt} || ',%'"
            + "</foreach> ) </if>"
            + "<if test='categoryId != null'> AND category_id = #{categoryId} </if>"
            + "</script>")
    long countAdminPage(@Param("shopId") Long shopId,
                        @Param("keyword") String keyword,
                        @Param("goodsType") String goodsType,
                        @Param("categoryKind") String categoryKind,
                        @Param("status") String status,
                        @Param("deliveryTypes") List<String> deliveryTypes,
                        @Param("categoryId") Long categoryId);

    /**
     * 本店是否真的有这个商品。
     *
     * <p>★ 越权校验就落在这个 {@code WHERE id = ? AND shop_id = ?} 里，
     * 不是「先把商品查出来再在 Java 里比 shopId」—— 后者既要多查一列，
     * 又给了「忘了比」的机会。
     *
     * <p>删除前的第一步就是它：先确认是本店的，才不会出现「去查别家商品的订单引用、
     * 然后回一句『已产生订单』」这种既报错又泄露信息的结果（P15）。
     */
    @Select("SELECT COUNT(*) FROM tb_product WHERE id = #{id} AND shop_id = #{shopId}")
    int countByIdAndShop(@Param("id") Long id, @Param("shopId") Long shopId);

    /**
     * 删除商品。
     *
     * <p>★ 物理删除，且带 {@code shop_id} 条件。
     * 与买家订单的软删除不同：需求只对订单写了「仅隐藏」，对商品写的是「删除商品」，
     * 并且用<b>「已产生订单的商品禁止删除」</b>这条规则来保护历史数据的完整性 ——
     * 也就是说，能走到这一步的商品一定没有订单引用，物理删除是安全的。
     */
    @Delete("DELETE FROM tb_product WHERE id = #{id} AND shop_id = #{shopId}")
    int deleteByIdAndShop(@Param("id") Long id, @Param("shopId") Long shopId);

    /** 批量设置：状态。影响 0 行 = 越权或已不存在，由 Service 记入失败明细 */
    @Update("UPDATE tb_product SET status = #{status} WHERE id = #{id} AND shop_id = #{shopId}")
    int updateStatus(@Param("id") Long id, @Param("shopId") Long shopId,
                     @Param("status") String status);

    /** 批量设置：配送方式（Service 已把多选序列化成逗号分隔串） */
    @Update("UPDATE tb_product SET delivery_types = #{deliveryTypes} "
            + "WHERE id = #{id} AND shop_id = #{shopId}")
    int updateDeliveryTypes(@Param("id") Long id, @Param("shopId") Long shopId,
                            @Param("deliveryTypes") String deliveryTypes);

    /** 批量设置：分类 */
    @Update("UPDATE tb_product SET category_id = #{categoryId} "
            + "WHERE id = #{id} AND shop_id = #{shopId}")
    int updateCategoryId(@Param("id") Long id, @Param("shopId") Long shopId,
                         @Param("categoryId") Long categoryId);

    /** 批量设置：排序值（统一置成同一个值） */
    @Update("UPDATE tb_product SET sort = #{sort} WHERE id = #{id} AND shop_id = #{shopId}")
    int updateSort(@Param("id") Long id, @Param("shopId") Long shopId,
                   @Param("sort") Integer sort);

    /**
     * 批量设置：库存。两个库存字段各自可独立更新。
     *
     * <p>★ 每个字段都套 {@code <if>}：商家只想改仓库库存时 {@code storeStock} 为 null，
     * 这一列就<b>完全不出现在 SET 里</b>。若把 null 兜成 0，
     * 门店库存会被悄悄清空 —— 商家改了个仓库数，门店就卖不出货了。
     *
     * <p>★ {@code id = id} 不是笔误：它是<b>用来吃掉逗号</b>的。
     * {@code <if>} 拼接最容易出的错是末尾多一个逗号（{@code SET a = 1, WHERE ...} 直接语法错误）。
     * 把 {@code id = id} 固定在最后，前面每个 {@code <if>} 都敢放心带逗号。
     * 写自己等于没写，SQLite 不会因此产生任何额外开销。
     */
    @Update("<script>"
            + "UPDATE tb_product SET "
            + "<if test='warehouseStock != null'> warehouse_stock = #{warehouseStock}, </if>"
            + "<if test='storeStock != null'> store_stock = #{storeStock}, </if>"
            + "id = id "
            + "WHERE id = #{id} AND shop_id = #{shopId}"
            + "</script>")
    int updateStock(@Param("id") Long id, @Param("shopId") Long shopId,
                    @Param("warehouseStock") Integer warehouseStock,
                    @Param("storeStock") Integer storeStock);

    // ==================================================================
    // 下方为 P22/P23 导出导入
    // ==================================================================

    /**
     * 本店是否已有这个商品编码 —— 导入时判定「新增 or 更新」的唯一依据。
     *
     * <p>★★ 判定依据是 {@code product_code} 而<b>不是 id</b>：Excel 里没有数据库 id，
     * 商家也不知道 id。用 id 判定的实现只能靠「编码当 id 塞进去」，一跑就全乱。
     *
     * <p>★ 带 {@code shop_id}：同一个小米产品编码在别家店铺也可能存在，
     * 不带隔离就会把别家的商品当成自己的去更新。
     */
    @Select("SELECT COUNT(*) FROM tb_product WHERE shop_id = #{shopId} "
            + "AND product_code = #{productCode}")
    int countByProductCode(@Param("shopId") Long shopId,
                           @Param("productCode") String productCode);

    /**
     * 导入新增。
     *
     * <p>★ 不写 {@code sales}（真实销量）：新商品的真实销量必然是 0，
     * 从 Excel 读进来只会引入脏数据 —— 而且销量是交易结果，不是商家能填的资料。
     * {@code inflated_sales}（注水销量）则相反，它本来就是商家可配置的。
     *
     * <p>★ {@code shop_id} 由 Service 从登录态强制写入，这个 SQL 里没有对应的外部输入。
     */
    @Insert("INSERT INTO tb_product (shop_id, name, main_image, price, market_price, "
            + "stock, warehouse_stock, store_stock, stock_warn, inflated_sales, goods_type, "
            + "category_kind, delivery_types, product_code, status, sort, create_time) "
            + "VALUES (#{p.shopId}, #{p.name}, #{p.mainImage}, #{p.price}, #{p.marketPrice}, "
            + "#{p.stock}, #{p.warehouseStock}, #{p.storeStock}, #{p.stockWarn}, "
            + "#{p.inflatedSales}, #{p.goodsType}, #{p.categoryKind}, #{p.deliveryTypes}, "
            + "#{p.productCode}, #{p.status}, #{p.sort}, datetime('now','localtime'))")
    int insert(@Param("p") Product p);

    /**
     * 导入更新，按 {@code (shop_id, product_code)} 定位。
     *
     * <p>★ SET 里<b>刻意没有 {@code sales}</b>：真实销量是订单跑出来的数据，
     * 商家拿一份改过价格的 Excel 导回来，不该顺手把销量改成表里的数字。
     * {@code inflated_sales} 可以改 —— 它是商家自己填的展示资源。
     * （P23 用反射读这段 SQL 原文守着这条，别以为漏了。）
     *
     * <p>★ 不带 {@code update_time}：这张表没有这一列，不要为了「规范」去加。
     */
    @Update("UPDATE tb_product SET name = #{p.name}, main_image = #{p.mainImage}, "
            + "price = #{p.price}, market_price = #{p.marketPrice}, stock = #{p.stock}, "
            + "warehouse_stock = #{p.warehouseStock}, store_stock = #{p.storeStock}, "
            + "inflated_sales = #{p.inflatedSales}, goods_type = #{p.goodsType}, "
            + "category_kind = #{p.categoryKind}, delivery_types = #{p.deliveryTypes}, "
            + "status = #{p.status}, sort = #{p.sort} "
            + "WHERE shop_id = #{shopId} AND product_code = #{p.productCode}")
    int updateByProductCode(@Param("shopId") Long shopId, @Param("p") Product p);
}
