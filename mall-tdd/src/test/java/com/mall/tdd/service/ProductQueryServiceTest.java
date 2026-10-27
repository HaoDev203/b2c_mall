package com.mall.tdd.service;

import com.mall.tdd.common.LoginUser;
import com.mall.tdd.common.Result;
import com.mall.tdd.common.UserContext;
import com.mall.tdd.dto.AdminProductVO;
import com.mall.tdd.dto.BatchResultVO;
import com.mall.tdd.dto.ImportResultVO;
import com.mall.tdd.dto.ProductBatchCommand;
import com.mall.tdd.dto.ProductPageVO;
import com.mall.tdd.dto.ProductQuery;
import com.mall.tdd.entity.Product;
import com.mall.tdd.mapper.CategoryMapper;
import com.mall.tdd.mapper.OrderItemMapper;
import com.mall.tdd.mapper.ProductMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * B4 · 店铺商品管理 23 条（P01~P21 = 21 条，P22/P23 导出导入另批）。
 *
 * <p><b>这批测试的两类断言，缺一不可：</b>
 * <ol>
 *   <li><b>返回值断言</b>（参数透传、VO 字段、批量计数）—— Mock 就能验。</li>
 *   <li><b>SQL 结构断言</b>（反射读 @Select / @Update 原文）——
 *       验「SQL 里到底有没有那个条件」。B3 的 U08 就是只有前者，删掉
 *       {@code is_deleted = 0} 它照样绿，是个假绿测试。</li>
 * </ol>
 *
 * <p>本批所有 SQL 结构断言都走 {@link #sqlOf} 反射，不靠 Mock 返回值装作验证过了。
 */
@DisplayName("B4 店铺商品管理")
class ProductQueryServiceTest {

    private static final Long SHOP_ID = 1L;
    private static final Long OPERATOR_ID = 99L;

    private ProductMapper productMapper;
    private CategoryMapper categoryMapper;
    private OrderItemMapper orderItemMapper;
    private ProductQueryService service;

    @BeforeEach
    void setUp() {
        productMapper = mock(ProductMapper.class);
        categoryMapper = mock(CategoryMapper.class);
        orderItemMapper = mock(OrderItemMapper.class);
        service = new ProductQueryService(productMapper, categoryMapper, orderItemMapper);
        UserContext.set(new LoginUser(OPERATOR_ID, SHOP_ID, "店铺老板"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ==========================================================
    // 功能点 1 · 顶部筛选（P01~P07）
    // ==========================================================

    @Test
    @DisplayName("P01 商品名称模糊搜索：参数本身不加 %")
    void should_search_by_name_fuzzy() {
        stubList(Collections.<Product>emptyList(), 0L);

        service.listProducts(query("手机", null, null, null, null, null), 1, 10);

        // ★ 传下去的是裸关键词。写成 "%手机%" 的话，SQL 里再拼一层就变成 %%手机%%，
        //   能查出结果但索引彻底失效（换不来任何好处）。
        verify(productMapper).selectAdminPage(
                eq(SHOP_ID), eq("手机"), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(10), eq(0));

        // % 由 SQL 拼，不是由 Java 拼
        String sql = sqlOf(ProductMapper.class, "selectAdminPage");
        assertTrue(sql.contains("name LIKE '%' || #{keyword} || '%'"),
                "模糊匹配的 % 必须在 SQL 里拼，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("P02 商品类型筛选用的是 goods_type，不是 product_type")
    void should_filter_by_goods_type_not_product_type() {
        stubList(Collections.<Product>emptyList(), 0L);

        service.listProducts(query(null, "seckill", null, null, null, null), 1, 10);

        verify(productMapper).selectAdminPage(
                eq(SHOP_ID), isNull(), eq("seckill"), isNull(), isNull(), isNull(), isNull(),
                eq(10), eq(0));

        // ★ 这条测试的重点在「字段名」。一期末 shop 的 product_type 装的是「类别」
        //   （实物/虚拟/卡券），不是「类型」（普通/秒杀）。用错字段名时
        //   Mock 断言照样绿 —— 因为 Mock 只看参数透传，看不见字段语义。
        //   所以这里必须读 SQL 原文把字段名钉死。
        String sql = sqlOf(ProductMapper.class, "selectAdminPage");
        assertTrue(sql.contains("goods_type = #{goodsType}"),
                "商品类型必须落 goods_type，实际 SQL：" + sql);
        assertFalse(sql.contains("product_type"),
                "不许出现 product_type（库里那个语义是「类别」），实际 SQL：" + sql);
    }

    @Test
    @DisplayName("P03 商品类别筛选用 category_kind（与类型是两个独立维度）")
    void should_filter_by_category_kind() {
        stubList(Collections.<Product>emptyList(), 0L);

        service.listProducts(query(null, null, "virtual", null, null, null), 1, 10);

        verify(productMapper).selectAdminPage(
                eq(SHOP_ID), isNull(), isNull(), eq("virtual"), isNull(), isNull(), isNull(),
                eq(10), eq(0));

        String sql = sqlOf(ProductMapper.class, "selectAdminPage");
        assertTrue(sql.contains("category_kind = #{categoryKind}"),
                "商品类别必须落 category_kind，实际 SQL：" + sql);
        // 两个维度必须各有各的占位符，不能共用
        assertNotEquals(placeholderOf(sql, "goodsType"), placeholderOf(sql, "categoryKind"),
                "类型与类别是两个参数，不能互相顶替");
    }

    @Test
    @DisplayName("P04 状态筛选（上架 / 下架）")
    void should_filter_by_status() {
        stubList(Collections.<Product>emptyList(), 0L);

        service.listProducts(query(null, null, null, "off_sale", null, null), 1, 10);

        verify(productMapper).selectAdminPage(
                eq(SHOP_ID), isNull(), isNull(), isNull(), eq("off_sale"), isNull(), isNull(),
                eq(10), eq(0));

        String sql = sqlOf(ProductMapper.class, "selectAdminPage");
        assertTrue(sql.contains("status = #{status}"), "实际 SQL：" + sql);
    }

    @Test
    @DisplayName("P04b 非法状态值必须报错，不能静默忽略")
    void should_reject_illegal_status_value() {
        // ★ 静默忽略 = 用户以为筛选生效了，看到的其实还是全表。
        //   宁可报错，也不要给一个「看起来对」的结果。
        Result<ProductPageVO> result = service.listProducts(
                query(null, null, null, "abc", null, null), 1, 10);

        assertNotEquals(Integer.valueOf(200), result.getCode(), "非法状态应失败");
        assertNotNull(result.getMsg());
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("P05 配送方式多选组合 ★")
    void should_filter_by_multiple_delivery_types() {
        stubList(Collections.<Product>emptyList(), 0L);

        List<String> types = Arrays.asList("express", "self_pickup");
        service.listProducts(query(null, null, null, null, types, null), 1, 10);

        verify(productMapper).selectAdminPage(
                eq(SHOP_ID), isNull(), isNull(), isNull(), isNull(), eq(types), isNull(),
                eq(10), eq(0));

        String sql = sqlOf(ProductMapper.class, "selectAdminPage");

        // ★ 多选必须由 SQL 的 foreach 展开。写死在 Java 里拼字符串的话，
        //   选 1 个和选 5 个要写 5 个分支。
        assertTrue(sql.contains("<foreach"), "多选条件必须用 <foreach> 展开，实际 SQL：" + sql);

        // ★ 占位符必须是 #{}（预编译），不能是 ${}（字符串拼接 = 注入）
        assertFalse(sql.contains("${"), "筛选条件不许用 ${} 拼接，实际 SQL：" + sql);
        assertTrue(sql.contains("#{dt}"), "foreach 里应使用 #{dt}，实际 SQL：" + sql);

        // ★★ 关键：delivery_types 是逗号分隔列，不能用 `delivery_type IN (?, ?)`
        //   —— 那要求列是单值。商品同时支持快递+自提时列值是
        //   "express,self_pickup"，IN 匹配不上任何一个。
        //   必须按「逗号边界」做整词匹配，否则 express 会误命中 express_fast。
        assertTrue(sql.contains("(',' || delivery_types || ',') LIKE '%,' || #{dt} || ',%'"),
                "逗号分隔列必须按整词边界匹配，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("P05b 配送方式空集合不能拼出 IN ()")
    void should_not_emit_in_clause_when_delivery_types_empty() {
        stubList(Collections.<Product>emptyList(), 0L);

        // ★ 空集合若原样传下去，SQL 会变成 `AND ()` 或 `IN ()`，SQLite 直接语法错误，
        //   整个列表页 500。空集合 = 用户没选，等价于不加这个条件。
        service.listProducts(query(null, null, null, null, new ArrayList<String>(), null), 1, 10);

        verify(productMapper).selectAdminPage(
                eq(SHOP_ID), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(10), eq(0));
    }

    @Test
    @DisplayName("P06 商品分类筛选（category_id，与 P03 的类别不是一回事）")
    void should_filter_by_category_id() {
        stubList(Collections.<Product>emptyList(), 0L);

        service.listProducts(query(null, null, null, null, null, 3L), 1, 10);

        verify(productMapper).selectAdminPage(
                eq(SHOP_ID), isNull(), isNull(), isNull(), isNull(), isNull(), eq(3L),
                eq(10), eq(0));

        String sql = sqlOf(ProductMapper.class, "selectAdminPage");
        assertTrue(sql.contains("category_id = #{categoryId}"), "实际 SQL：" + sql);
    }

    @Test
    @DisplayName("P07 六个条件同时生效（AND，一个都不能丢）")
    void should_apply_all_six_filters_together() {
        stubList(Collections.<Product>emptyList(), 0L);

        List<String> types = Arrays.asList("express", "self_pickup");
        service.listProducts(query("手机", "seckill", "physical", "on_sale", types, 3L), 1, 10);

        // ★ 不写 6 个独立测试：那样测不出「一起传会不会互相覆盖」。
        //   一条里 6 个 eq 全部命中，才说明 6 个条件真的同时进了同一次查询。
        verify(productMapper).selectAdminPage(eq(SHOP_ID),
                eq("手机"), eq("seckill"), eq("physical"), eq("on_sale"), eq(types), eq(3L),
                eq(10), eq(0));

        String sql = sqlOf(ProductMapper.class, "selectAdminPage");
        for (String cond : Arrays.asList("name LIKE", "goods_type =", "category_kind =",
                "status =", "#{dt}", "category_id =")) {
            assertTrue(sql.contains(cond), "SQL 缺少条件 " + cond + "，实际：" + sql);
        }
    }

    @Test
    @DisplayName("P04c 未登录查列表：一个查询都不发")
    void should_reject_list_when_not_logged_in() {
        UserContext.clear();

        Result<ProductPageVO> result = service.listProducts(
                query(null, null, null, null, null, null), 1, 10);

        assertEquals(Integer.valueOf(401), result.getCode(), "未登录应返回 401");
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("P06b 分页缺省：page=1、size=10")
    void should_use_default_page_when_params_are_null() {
        stubList(Collections.<Product>emptyList(), 0L);

        service.listProducts(query(null, null, null, null, null, null), null, null);

        // ★ size 不传就回落 10，不能回落 0 —— LIMIT 0 会让列表永远空白，
        //   看起来像「这个店一件商品都没有」。
        verify(productMapper).selectAdminPage(
                eq(SHOP_ID), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(10), eq(0));
    }

    @Test
    @DisplayName("P06c 空白关键词当「不筛」，不当成空字符串匹配")
    void should_treat_blank_filters_as_no_filter() {
        stubList(Collections.<Product>emptyList(), 0L);

        service.listProducts(query("   ", "", null, null, null, null), 1, 10);

        // ★ 传空串下去 SQL 会变成 LIKE '%' || '' || '%' = 匹配所有，
        //   看似无害；但 status/goods_type 传空串就一条都匹配不上，
        //   用户会以为「没数据」。统一转成 null 才一致。
        verify(productMapper).selectAdminPage(
                eq(SHOP_ID), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(10), eq(0));
    }

    @Test
    @DisplayName("P06d 查询对象为 null 等价于不筛")
    void should_treat_null_query_as_no_filter() {
        stubList(Collections.<Product>emptyList(), 0L);

        service.listProducts(null, 1, 10);

        verify(productMapper).selectAdminPage(
                eq(SHOP_ID), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(10), eq(0));
    }

    @Test
    @DisplayName("P04d 批量设置也拦非法状态")
    void should_reject_batch_with_illegal_status() {
        ProductBatchCommand cmd = command(Arrays.asList(1L, 2L));
        cmd.setStatus("ON_SALE"); // 大小写不对也算非法，不允许「猜用户意图」

        Result<BatchResultVO> result = service.batchUpdate(cmd);

        // 与 P04b 是同一套白名单，两条路径都要拦：列表筛选传错只是查不到，
        // 批量传错就是真把商品改成非法状态，前端再查什么都查不到。
        assertNotEquals(Integer.valueOf(200), result.getCode());
        verifyNoInteractions(productMapper);
    }

    // ==========================================================
    // 功能点 2 · 列表展示（P08~P12）
    // ==========================================================

    @Test
    @DisplayName("P08 分页：页大小上限 + 总数与列表分开统计")
    void should_cap_page_size_and_return_correct_total() {
        stubList(Collections.<Product>emptyList(), 42L);

        Result<ProductPageVO> result = service.listProducts(
                query(null, null, null, null, null, null), 3, 1000);

        // ★ 安全断言，不只是功能断言：不压上限，前端传 size=999999 就能把整表捞出来，
        //   一个参数打成 DoS。
        // page=3 → offset=(3-1)*100=200
        verify(productMapper).selectAdminPage(
                eq(SHOP_ID), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(100), eq(200));

        assertEquals(Long.valueOf(42L), result.getData().getTotal(),
                "总数来自独立的 count 查询，不能拿当前页条数当总数");
    }

    @Test
    @DisplayName("P08b 总数查询必须带同一组筛选条件")
    void should_apply_same_filters_to_count() {
        stubList(Collections.<Product>emptyList(), 7L);

        List<String> types = Arrays.asList("express");
        service.listProducts(query("手机", "seckill", "physical", "on_sale", types, 3L), 1, 10);

        // ★ 经典 bug：列表筛了、总数没筛 → 前端显示「共 100 条」但只翻得出 3 页。
        //   Mock 返回值永远是对的，所以只能读 SQL 原文。
        String sql = sqlOf(ProductMapper.class, "countAdminPage");
        assertTrue(sql.contains("shop_id = #{shopId}"), "总数必须限定本店，实际：" + sql);
        for (String cond : Arrays.asList("name LIKE", "goods_type =", "category_kind =",
                "status =", "#{dt}", "category_id =")) {
            assertTrue(sql.contains(cond), "总数查询缺少条件 " + cond + "，实际：" + sql);
        }
    }

    @Test
    @DisplayName("P09 按 sort 倒序（数字越大越靠前），Service 不重排")
    void should_sort_by_sort_field_desc() {
        // 桩数据故意乱序：如果 Service 在内存里排了一遍，顺序会被改掉
        List<Product> rows = Arrays.asList(
                product(1L, "商品A", 5),
                product(2L, "商品B", 100),
                product(3L, "商品C", 1));
        stubList(rows, 3L);

        Result<ProductPageVO> result = service.listProducts(
                query(null, null, null, null, null, null), 1, 10);

        // 需求原文：「排序：数字，数字越大前台展示越靠前」→ DESC，不是 ASC。
        // ★ 排序发生在 SQL（B2 的 D20 已经踩过：只看返回值分不清排在哪一层），
        //   所以既要读 SQL，又要断言 Service 没重排。
        String sql = sqlOf(ProductMapper.class, "selectAdminPage");
        assertTrue(sql.contains("ORDER BY sort DESC"),
                "需求写死「数字越大越靠前」，必须是 ORDER BY sort DESC，实际：" + sql);

        List<Long> ids = new ArrayList<Long>();
        for (AdminProductVO vo : result.getData().getList()) {
            ids.add(vo.getId());
        }
        assertEquals(Arrays.asList(1L, 2L, 3L), ids,
                "Service 不应改变 SQL 给出的顺序（排序是数据库的职责）");
    }

    @Test
    @DisplayName("P10 列表 12 个字段齐备")
    void should_return_all_twelve_list_fields() {
        Product p = product(1L, "小米手机", 10);
        p.setMainImage("/img/1.jpg");
        p.setMarketPrice(Double.valueOf(2999.00));
        p.setPrice(Double.valueOf(2699.00));
        p.setSales(Integer.valueOf(120));
        p.setInflatedSales(Integer.valueOf(5000));
        p.setWarehouseStock(Integer.valueOf(800));
        p.setStoreStock(Integer.valueOf(35));
        p.setGoodsType("normal");
        p.setCategoryKind("physical");
        p.setStatus("on_sale");
        p.setDeliveryTypes("express,self_pickup");
        stubList(Collections.singletonList(p), 1L);

        AdminProductVO vo = service.listProducts(
                query(null, null, null, null, null, null), 1, 10).getData().getList().get(0);

        // 12 个必填字段（需求列的清单，sort 是第 13 个，只作排序依据，不强制）
        assertNotNull(vo.getMainImage(), "主图");
        assertNotNull(vo.getName(), "名称");
        assertNotNull(vo.getMarketPrice(), "市场价");
        assertNotNull(vo.getPrice(), "销售价");
        assertNotNull(vo.getSales(), "真实销量");
        assertNotNull(vo.getInflatedSales(), "注水销量");
        assertNotNull(vo.getWarehouseStock(), "仓库库存");
        assertNotNull(vo.getStoreStock(), "门店库存");
        assertNotNull(vo.getGoodsType(), "商品类型");
        assertNotNull(vo.getCategoryKind(), "商品类别");
        assertNotNull(vo.getStatus(), "状态");
        assertNotNull(vo.getDeliveryTypes(), "配送方式");
    }

    @Test
    @DisplayName("P11 真实销量与注水销量分开返回，不许合并 ★")
    void should_return_real_and_inflated_sales_separately() {
        Product p = product(1L, "小米手机", 10);
        p.setSales(Integer.valueOf(120));
        p.setInflatedSales(Integer.valueOf(5000));
        stubList(Collections.singletonList(p), 1L);

        AdminProductVO vo = service.listProducts(
                query(null, null, null, null, null, null), 1, 10).getData().getList().get(0);

        assertEquals(Integer.valueOf(120), vo.getSales(), "真实销量必须是 120");
        assertEquals(Integer.valueOf(5000), vo.getInflatedSales(), "注水销量必须是 5000");

        // ★★ 只断言「两个字段都在」不够 —— 实现把注水销量加进 sales 也能过。
        //   必须断言值没被污染：后台一旦合并，商家就再也分不清真实经营数据。
        assertNotEquals(Integer.valueOf(5120), vo.getSales(),
                "真实销量不许把注水销量加进来");
    }

    @Test
    @DisplayName("P12 仓库库存与门店库存分开返回")
    void should_return_warehouse_and_store_stock_separately() {
        // ★ 桩值必须让两者不等：都传 100 的话，实现把两个字段映射到同一个源也照样绿
        Product p = product(1L, "小米手机", 10);
        p.setWarehouseStock(Integer.valueOf(800));
        p.setStoreStock(Integer.valueOf(35));
        stubList(Collections.singletonList(p), 1L);

        AdminProductVO vo = service.listProducts(
                query(null, null, null, null, null, null), 1, 10).getData().getList().get(0);

        assertEquals(Integer.valueOf(800), vo.getWarehouseStock(), "仓库库存");
        assertEquals(Integer.valueOf(35), vo.getStoreStock(), "门店库存");
        assertNotEquals(vo.getWarehouseStock(), vo.getStoreStock(),
                "两个库存字段必须独立，不能是同一个值");
    }

    // ==========================================================
    // 功能点 3 · 删除（P13~P15）
    // ==========================================================

    @Test
    @DisplayName("P13 已产生订单的商品禁止删除 ★★")
    void should_reject_delete_when_product_has_orders() {
        when(productMapper.countByIdAndShop(5L, SHOP_ID)).thenReturn(1);
        when(orderItemMapper.countByProductId(5L)).thenReturn(2L);

        Result<Void> result = service.deleteProduct(5L);

        assertNotEquals(Integer.valueOf(200), result.getCode(), "有订单引用必须拒绝删除");
        assertTrue(result.getMsg().contains("已产生订单"),
                "提示要让商家看懂为什么删不了，实际：" + result.getMsg());

        // ★★ 只断言「返回失败」远远不够：实现完全可以「先删了、再发现不该删、然后返回失败」，
        //    返回码一样是 500，数据却没了。必须证明删除从未发生。
        verify(productMapper, never()).deleteByIdAndShop(any(), any());
    }

    @Test
    @DisplayName("P14 无订单引用可正常删除（与 P13 成对，防「永远拒绝」）")
    void should_allow_delete_when_product_has_no_order() {
        when(productMapper.countByIdAndShop(6L, SHOP_ID)).thenReturn(1);
        when(orderItemMapper.countByProductId(6L)).thenReturn(0L);
        when(productMapper.deleteByIdAndShop(6L, SHOP_ID)).thenReturn(1);

        Result<Void> result = service.deleteProduct(6L);

        // ★ 这条必须和 P13 成对存在。只有 P13 的话，实现写成「永远返回失败」也能绿,
        //   那就把删除功能彻底废了。
        assertEquals(Integer.valueOf(200), result.getCode(), "无订单引用应允许删除");
        verify(productMapper, times(1)).deleteByIdAndShop(eq(6L), eq(SHOP_ID));
    }

    @Test
    @DisplayName("P15 越权：删别家商品被最先拦下，不查订单引用")
    void should_not_delete_other_shops_product() {
        // 商品 9 属于店铺 2，本店是 1
        when(productMapper.countByIdAndShop(9L, SHOP_ID)).thenReturn(0);

        Result<Void> result = service.deleteProduct(9L);

        assertNotEquals(Integer.valueOf(200), result.getCode(), "别家商品不许删");
        verify(productMapper, never()).deleteByIdAndShop(any(), any());

        // ★ 越权必须在第一步就拦掉。继续往下走会去查 tb_order_item，
        //   查出来「有订单」就会回一句「已产生订单」—— 既是错误原因，
        //   又泄露了别家商品的订单情况。
        verifyNoInteractions(orderItemMapper);
    }

    @Test
    @DisplayName("P15b 未登录：一个查询都不发")
    void should_reject_delete_when_not_logged_in() {
        UserContext.clear();

        Result<Void> result = service.deleteProduct(9L);

        // ★ 401 而不是 500：前端据此跳登录页，而不是弹一个「商品不存在」的错误提示。
        assertEquals(Integer.valueOf(401), result.getCode(), "未登录应返回 401");
        verifyNoInteractions(productMapper, orderItemMapper, categoryMapper);
    }

    @Test
    @DisplayName("P15c 商品 id 为空：直接拒绝，不发一个查询")
    void should_reject_delete_when_id_is_null() {
        Result<Void> result = service.deleteProduct(null);

        assertNotEquals(Integer.valueOf(200), result.getCode());
        verifyNoInteractions(productMapper, orderItemMapper);
    }

    @Test
    @DisplayName("P15d 预检通过但删除时商品已被删：如实失败，不假装成功")
    void should_fail_when_product_vanishes_before_delete() {
        when(productMapper.countByIdAndShop(7L, SHOP_ID)).thenReturn(1);
        when(orderItemMapper.countByProductId(7L)).thenReturn(0L);
        // 预检到这里之间被别的请求删掉了 / 店铺被转了
        when(productMapper.deleteByIdAndShop(7L, SHOP_ID)).thenReturn(0);

        Result<Void> result = service.deleteProduct(7L);

        // 删除语句自带 WHERE shop_id，所以并发下唯一能拿到的证据就是「影响 0 行」。
        // 这里若返回 200，商家以为删掉了，刷新一下商品还在。
        assertNotEquals(Integer.valueOf(200), result.getCode());
    }

    // ==========================================================
    // 功能点 4 · 批量设置（P16~P21）
    // ==========================================================

    @Test
    @DisplayName("P16 批量改状态：逐条独立，一条失败不影响其余 ★")
    void should_change_status_per_item_independently() {
        when(productMapper.updateStatus(1L, SHOP_ID, "off_sale")).thenReturn(1);
        when(productMapper.updateStatus(2L, SHOP_ID, "off_sale")).thenReturn(0); // 别家商品
        when(productMapper.updateStatus(3L, SHOP_ID, "off_sale")).thenReturn(1);

        ProductBatchCommand cmd = command(Arrays.asList(1L, 2L, 3L));
        cmd.setStatus("off_sale");
        Result<BatchResultVO> result = service.batchUpdate(cmd);

        BatchResultVO data = result.getData();
        assertEquals(Integer.valueOf(3), data.getTotal(), "总数 = 提交条数");
        assertEquals(Integer.valueOf(2), data.getSuccess(), "1 和 3 应成功");
        assertEquals(Integer.valueOf(1), data.getFailed(), "2 越权应失败");

        assertEquals(3, data.getResults().size(), "每条都要有明细");
        assertFalse(data.getResults().get(1).isSuccess(), "第 2 条应标记失败");
        assertNotNull(data.getResults().get(1).getMessage());

        // ★ 批量操作一律不加 @Transactional：整体回滚意味着「3 个里 1 个越权，
        //   另外 2 个也不改了」，商家点一次什么都没发生还不知道为什么。
        verify(productMapper, times(1)).updateStatus(eq(1L), eq(SHOP_ID), eq("off_sale"));
        verify(productMapper, times(1)).updateStatus(eq(3L), eq(SHOP_ID), eq("off_sale"));
    }

    @Test
    @DisplayName("P17 一条抛异常也不回滚，明细里带上原因")
    void should_report_partial_failure_without_rolling_back() {
        when(productMapper.updateStatus(1L, SHOP_ID, "off_sale")).thenReturn(1);
        when(productMapper.updateStatus(2L, SHOP_ID, "off_sale"))
                .thenThrow(new IllegalStateException("database is locked"));
        when(productMapper.updateStatus(3L, SHOP_ID, "off_sale")).thenReturn(1);

        ProductBatchCommand cmd = command(Arrays.asList(1L, 2L, 3L));
        cmd.setStatus("off_sale");

        // ★ 批量接口不许抛异常。抛了前端只看到 500，不知道哪几条成功了、
        //   该重试哪几条 —— 商家只能全量重来，可能又改一遍已成功的。
        Result<BatchResultVO> result = service.batchUpdate(cmd);

        BatchResultVO data = result.getData();
        assertEquals(Integer.valueOf(3), data.getTotal());
        assertEquals(Integer.valueOf(2), data.getSuccess());
        assertEquals(Integer.valueOf(1), data.getFailed());
        assertFalse(data.getResults().get(1).getMessage().isEmpty(), "失败明细必须带原因");
        assertTrue(result.getMsg() == null || !result.getMsg().contains("Exception"),
                "异常信息不该原样透给前端");
    }

    @Test
    @DisplayName("P18 批量改配送方式（多选序列化成逗号分隔）")
    void should_batch_update_delivery_types() {
        when(productMapper.updateDeliveryTypes(any(), eq(SHOP_ID), any())).thenReturn(1);

        ProductBatchCommand cmd = command(Arrays.asList(1L, 2L, 3L));
        cmd.setDeliveryTypes(Arrays.asList("express", "self_pickup"));
        Result<BatchResultVO> result = service.batchUpdate(cmd);

        assertEquals(Integer.valueOf(3), result.getData().getSuccess(), "3 条都应成功");

        // ★ delivery_types 是逗号分隔列，多选值必须序列化后存，
        //   不能只存第一个（那是「多选」变成「单选」）。
        for (Long id : Arrays.asList(1L, 2L, 3L)) {
            verify(productMapper).updateDeliveryTypes(eq(id), eq(SHOP_ID), eq("express,self_pickup"));
        }
    }

    @Test
    @DisplayName("P18b 配送方式传空集合 = 清空（不是「不修改」）")
    void should_clear_delivery_types_when_empty_list() {
        when(productMapper.updateDeliveryTypes(any(), eq(SHOP_ID), any())).thenReturn(1);

        ProductBatchCommand cmd = command(Collections.singletonList(1L));
        cmd.setDeliveryTypes(new ArrayList<String>());
        service.batchUpdate(cmd);

        // 语义固定成「清空」：空集合代表商家把配送方式全取消了。
        // 若实现成「不修改」，商家勾掉全部选项再保存会毫无反应。
        verify(productMapper).updateDeliveryTypes(eq(1L), eq(SHOP_ID), eq(""));
    }

    @Test
    @DisplayName("P18c 配送方式这条改不动（商品已被删）也要如实记失败")
    void should_report_unauthorized_when_delivery_update_misses() {
        // 商家开着列表页，另一个标签页把商品删了，再点批量设置 —— 影响 0 行
        ProductBatchCommand cmd = command(Collections.singletonList(1L));
        cmd.setDeliveryTypes(Arrays.asList("express"));

        BatchResultVO data = service.batchUpdate(cmd).getData();

        assertEquals(Integer.valueOf(1), data.getFailed(), "改不动的必须记失败");
        assertFalse(data.getResults().get(0).isSuccess());
    }

    @Test
    @DisplayName("P18d 配送方式里的空串要跳过，不能拼出 express,,self_pickup")
    void should_ignore_blank_delivery_type_in_serialization() {
        when(productMapper.updateDeliveryTypes(any(), eq(SHOP_ID), any())).thenReturn(1);

        ProductBatchCommand cmd = command(Collections.singletonList(1L));
        // 表单绑定出来带空项是常事（多选框多一个空隐藏域）
        cmd.setDeliveryTypes(Arrays.asList("express", "", "   ", "self_pickup"));
        service.batchUpdate(cmd);

        // ★ 空项不跳过就会存成 "express,,self_pickup"，前端拆出来是两个空按钮，
        //   而且下次按配送方式筛选时 (',' || col || ',') LIKE '%,,%' 永远不成立。
        verify(productMapper).updateDeliveryTypes(eq(1L), eq(SHOP_ID),
                eq("express,self_pickup"));
    }

    @Test
    @DisplayName("P19 批量改分类（先校验分类存在，只校验一次）")
    void should_batch_update_category_id() {
        when(categoryMapper.countById(3L)).thenReturn(1);
        when(productMapper.updateCategoryId(any(), eq(SHOP_ID), any())).thenReturn(1);

        ProductBatchCommand cmd = command(Arrays.asList(1L, 2L, 3L));
        cmd.setCategoryId(3L);
        Result<BatchResultVO> result = service.batchUpdate(cmd);

        assertEquals(Integer.valueOf(3), result.getData().getSuccess());
        for (Long id : Arrays.asList(1L, 2L, 3L)) {
            verify(productMapper).updateCategoryId(eq(id), eq(SHOP_ID), eq(3L));
        }

        // ★ 分类存在性只查一次，不是每条查一次：3 条就多 2 次无谓往返。
        verify(categoryMapper, times(1)).countById(eq(3L));
    }

    @Test
    @DisplayName("P19b 分类不存在则整批拒绝")
    void should_reject_batch_when_category_missing() {
        when(categoryMapper.countById(999L)).thenReturn(0);

        ProductBatchCommand cmd = command(Arrays.asList(1L, 2L, 3L));
        cmd.setCategoryId(999L);
        Result<BatchResultVO> result = service.batchUpdate(cmd);

        // 指向不存在的分类，改完商品就在分类树里消失了（列表按分类筛时全查不到）
        assertNotEquals(Integer.valueOf(200), result.getCode(), "分类不存在应整批拒绝");
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("P19c 改分类时一条落空也要记失败")
    void should_report_unauthorized_when_category_update_misses() {
        when(categoryMapper.countById(3L)).thenReturn(1);
        // 分类存在，但商品已被删（updateCategoryId 默认返回 0）

        ProductBatchCommand cmd = command(Collections.singletonList(1L));
        cmd.setCategoryId(3L);

        BatchResultVO data = service.batchUpdate(cmd).getData();

        assertEquals(Integer.valueOf(1), data.getFailed());
    }

    @Test
    @DisplayName("P20 批量改排序（统一置同一个值）")
    void should_batch_update_sort() {
        when(productMapper.updateSort(any(), eq(SHOP_ID), any())).thenReturn(1);

        ProductBatchCommand cmd = command(Arrays.asList(1L, 2L, 3L));
        cmd.setSort(Integer.valueOf(50));
        Result<BatchResultVO> result = service.batchUpdate(cmd);

        assertEquals(Integer.valueOf(3), result.getData().getSuccess());
        // 需求原文只写「批量修改…排序」，按统一值实现（不是依次递增）
        for (Long id : Arrays.asList(1L, 2L, 3L)) {
            verify(productMapper).updateSort(eq(id), eq(SHOP_ID), eq(50));
        }
    }

    @Test
    @DisplayName("P20b 改排序时一条落空也要记失败")
    void should_report_unauthorized_when_sort_update_misses() {
        ProductBatchCommand cmd = command(Collections.singletonList(1L));
        cmd.setSort(Integer.valueOf(50));

        BatchResultVO data = service.batchUpdate(cmd).getData();

        // 这一条最容易漏：排序改动「看起来」总是成功（页面上就是个数），
        // 落空时若记成功，商家会以为已经顶到前面去了。
        assertEquals(Integer.valueOf(1), data.getFailed());
    }

    @Test
    @DisplayName("P21 批量改库存：两个库存字段一起改")
    void should_batch_update_stock() {
        when(productMapper.updateStock(any(), eq(SHOP_ID), any(), any())).thenReturn(1);

        ProductBatchCommand cmd = command(Arrays.asList(1L, 2L, 3L));
        cmd.setWarehouseStock(Integer.valueOf(100));
        cmd.setStoreStock(Integer.valueOf(20));
        Result<BatchResultVO> result = service.batchUpdate(cmd);

        assertEquals(Integer.valueOf(3), result.getData().getSuccess());
        for (Long id : Arrays.asList(1L, 2L, 3L)) {
            // 两个都传，两个都更新 —— 只更新一个等于商家改了个寂寞
            verify(productMapper).updateStock(eq(id), eq(SHOP_ID), eq(100), eq(20));
        }
    }

    @Test
    @DisplayName("P21b 只传仓库库存时，门店库存必须保持不变")
    void should_keep_store_stock_when_only_warehouse_given() {
        when(productMapper.updateStock(any(), eq(SHOP_ID), any(), any())).thenReturn(1);

        ProductBatchCommand cmd = command(Collections.singletonList(1L));
        cmd.setWarehouseStock(Integer.valueOf(100));
        // storeStock 不传 = null
        service.batchUpdate(cmd);

        // ★ 传 null 给 Mapper，SQL 里的 <if> 才会跳过这一列。
        //   如果 Service 把 null 兜成 0，商家只想改仓库库存，门店库存会被清成 0。
        verify(productMapper).updateStock(eq(1L), eq(SHOP_ID), eq(100), isNull());

        String sql = sqlOf(ProductMapper.class, "updateStock");
        assertTrue(sql.contains("<if test='storeStock != null'>") || sql.contains("<if test=\"storeStock != null\">"),
                "门店库存必须由 <if> 控制是否更新，实际：" + sql);
        assertTrue(sql.contains("<if test='warehouseStock != null'>")
                        || sql.contains("<if test=\"warehouseStock != null\">"),
                "仓库库存必须由 <if> 控制是否更新，实际：" + sql);
    }

    @Test
    @DisplayName("P21f 改库存时一条落空也要记失败")
    void should_report_unauthorized_when_stock_update_misses() {
        ProductBatchCommand cmd = command(Collections.singletonList(1L));
        cmd.setWarehouseStock(Integer.valueOf(100));

        BatchResultVO data = service.batchUpdate(cmd).getData();

        assertEquals(Integer.valueOf(1), data.getFailed());
    }

    @Test
    @DisplayName("P21c 一个字段都没传时整批拒绝")
    void should_reject_batch_when_nothing_to_update() {
        ProductBatchCommand cmd = command(Arrays.asList(1L, 2L));

        Result<BatchResultVO> result = service.batchUpdate(cmd);

        // 空批量 = 前端按钮没禁用。真发下去就是 N 次 update 什么都没改，
        // 还返回「全部成功」，比报错更糟。
        assertNotEquals(Integer.valueOf(200), result.getCode(), "没有任何修改项应拒绝");
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("P21e 一条商品都没选：整批拒绝")
    void should_reject_batch_when_ids_empty() {
        ProductBatchCommand cmd = command(new ArrayList<Long>());
        cmd.setStatus("off_sale");

        Result<BatchResultVO> result = service.batchUpdate(cmd);

        assertNotEquals(Integer.valueOf(200), result.getCode(), "空 id 列表应拒绝");
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("P21d 批量未登录：整批拒绝且不发 SQL")
    void should_reject_batch_when_not_logged_in() {
        UserContext.clear();

        ProductBatchCommand cmd = command(Arrays.asList(1L));
        cmd.setStatus("off_sale");
        Result<BatchResultVO> result = service.batchUpdate(cmd);

        assertEquals(Integer.valueOf(401), result.getCode(), "未登录应返回 401");
        verifyNoInteractions(productMapper);
    }

    // ==========================================================
    // P22 · 导出当前筛选结果
    // ==========================================================

    @Test
    @DisplayName("P22 导出的 3 行是筛选结果而不是全表；分页参数被忽略")
    void should_export_current_filtered_result() throws Exception {
        // 全表 7 条，符合 status=on_sale + keyword=手机 的只有这 3 条
        List<Product> hits = Arrays.asList(
                productForExport(Long.valueOf(1), "SC-P1", "智能手机 Pro"),
                productForExport(Long.valueOf(2), "SC-P2", "智能手机 Air"),
                productForExport(Long.valueOf(3), "SC-P3", "智能手机 mini"));
        stubList(hits, 3L);

        Result<byte[]> result = service.exportProducts(
                query("手机", null, null, "on_sale", null, null));

        assertEquals(Integer.valueOf(200), result.getCode(), result.getMsg());
        byte[] xlsx = result.getData();
        assertNotNull(xlsx, "导出必须返回文件内容");
        assertTrue(xlsx.length > 0, "导出文件不能是 0 字节");

        // ★ 真的把这个字节流当 xlsx 打开读回来 —— 断言的是「文件可用」，
        //   不是「我们调用过某个写文件的方法」。mock 一个 Workbook 永远是绿的。
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals(3, sheet.getLastRowNum(), "第 0 行是表头，数据行应为 3（全表是 7）");

            // 列头就是后台列表的 12 个字段 + 商品编码（编码不进列表，但导出必须带，
            // 否则导出→改→导入时认不出是哪个商品，全变成新增）
            assertEquals(Arrays.asList("商品编码", "商品名称", "主图", "市场价", "销售价",
                            "真实销量", "注水销量", "仓库库存", "门店库存", "商品类型",
                            "商品类别", "状态", "配送方式"),
                    headersOf(sheet));

            assertEquals("SC-P1", textAt(sheet, 1, 0));
            assertEquals("智能手机 Pro", textAt(sheet, 1, 1));
            assertEquals("99", textAt(sheet, 1, 4), "销售价按数值写入，Excel 里可参与求和");
            // ★ 真实销量与注水销量必须是两列，合并了商家就分不清真实经营数据
            assertEquals("120", textAt(sheet, 1, 5));
            assertEquals("5000", textAt(sheet, 1, 6));
            assertEquals("8", textAt(sheet, 1, 7));
            assertEquals("2", textAt(sheet, 1, 8));
            assertEquals("express,self_pickup", textAt(sheet, 1, 12));
        }

        // ★ 筛选条件原样透传 + 分页被忽略（offset 从 0 起、上限远大于一页）
        ArgumentCaptor<Integer> limitCap = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> offsetCap = ArgumentCaptor.forClass(Integer.class);
        verify(productMapper).selectAdminPage(eq(SHOP_ID), eq("手机"), isNull(), isNull(),
                eq("on_sale"), isNull(), isNull(), limitCap.capture(), offsetCap.capture());
        assertEquals(Integer.valueOf(0), offsetCap.getValue(), "导出不分页，从第 0 行开始");
        assertTrue(limitCap.getValue().intValue() > 100,
                "导出上限必须大于后台一页的 100 条，否则只导了第一页，实际：" + limitCap.getValue());
    }

    @Test
    @DisplayName("P22b 命中 130 条就导出 130 行：不能被一页 100 条截断")
    void should_export_all_matching_rows_beyond_page_size() throws Exception {
        List<Product> many = new ArrayList<Product>();
        for (int i = 1; i <= 130; i++) {
            many.add(productForExport(Long.valueOf(i), "SC-" + i, "手机" + i));
        }
        stubList(many, 130L);

        Result<byte[]> result = service.exportProducts(
                query("手机", null, null, "on_sale", null, null));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result.getData()))) {
            assertEquals(130, wb.getSheetAt(0).getLastRowNum(),
                    "导出的是筛选结果全集，不是当前这一页");
        }
    }

    @Test
    @DisplayName("P22c 导出沿用列表的状态白名单：非法 status 不许绕过校验导全表")
    void should_reject_export_with_invalid_status() {
        Result<byte[]> result = service.exportProducts(
                query(null, null, null, "deleted", null, null));

        assertEquals(Integer.valueOf(500), result.getCode());
        assertNull(result.getData(), "校验失败不该产出任何文件");
        // ★ 导出是绕开列表页最省事的后门：这里不校验，随便传个 status 就把全状态数据导走了
        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("P22d 未登录导出：返回 401 且一条 SQL 都不发")
    void should_reject_export_when_not_logged_in() {
        UserContext.clear();

        Result<byte[]> result = service.exportProducts(query(null, null, null, null, null, null));

        assertEquals(Integer.valueOf(401), result.getCode());
        verifyNoInteractions(productMapper);
    }

    // ==========================================================
    // P23 · 导入：按商品编码判定新增 / 更新
    // ==========================================================

    @Test
    @DisplayName("P23 编码已存在则更新、不存在则新增；Excel 里的 shop_id 被忽略")
    void should_import_by_product_code_upsert() throws Exception {
        Map<String, Integer> existing = new LinkedHashMap<String, Integer>();
        existing.put("P001", Integer.valueOf(1));
        existing.put("P002", Integer.valueOf(1));
        existing.put("P999", Integer.valueOf(0));
        for (Map.Entry<String, Integer> e : existing.entrySet()) {
            when(productMapper.countByProductCode(SHOP_ID, e.getKey()))
                    .thenReturn(e.getValue().intValue());
        }

        // ★ 故意多塞一列 shop_id = 2（别家店铺）：导入必须只认当前登录店铺
        byte[] xlsx = buildWorkbook(
                Arrays.asList("商品编码", "商品名称", "销售价", "市场价", "总库存",
                        "仓库库存", "门店库存", "商品类型", "商品类别", "配送方式",
                        "状态", "排序", "主图", "真实销量", "注水销量", "shop_id"),
                Arrays.asList(
                        row("P001", "手机壳", "19.9", "29.9", "100", "80", "20", "normal",
                                "physical", "express", "on_sale", "5", "http://img/a.jpg", "10", "0", "2"),
                        row("P002", "数据线", "9.9", "19.9", "50", "40", "10", "normal",
                                "physical", "express", "on_sale", "3", "http://img/b.jpg", "8", "0", "2"),
                        row("P999", "新品手机", "2999", "3299", "20", "18", "2", "normal",
                                "physical", "express,self_pickup", "on_sale", "9",
                                "http://img/c.jpg", "0", "0", "2")));

        Result<ImportResultVO> result = service.importProducts(new ByteArrayInputStream(xlsx));

        assertEquals(Integer.valueOf(200), result.getCode(), result.getMsg());
        ImportResultVO data = result.getData();
        assertEquals(Integer.valueOf(3), data.getTotal(), "total = 数据行数，不含表头");
        assertEquals(Integer.valueOf(1), data.getInsert(), "只有 P999 是新增");
        assertEquals(Integer.valueOf(2), data.getUpdate(), "P001 / P002 已存在");
        assertEquals(Integer.valueOf(0), data.getFailed());

        // 新增的那一条必须强制带上当前店铺
        ArgumentCaptor<Product> insertCap = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).insert(insertCap.capture());
        Product inserted = insertCap.getValue();
        assertEquals("P999", inserted.getProductCode());
        assertEquals(SHOP_ID, inserted.getShopId(), "★ Excel 里写 shop_id=2 也不生效");
        assertEquals("新品手机", inserted.getName());
        assertEquals(Double.valueOf(2999.0), inserted.getPrice());
        assertEquals(Double.valueOf(3299.0), inserted.getMarketPrice());
        assertEquals(Integer.valueOf(20), inserted.getStock());
        assertEquals(Integer.valueOf(18), inserted.getWarehouseStock());
        assertEquals(Integer.valueOf(2), inserted.getStoreStock());
        assertEquals(Integer.valueOf(9), inserted.getSort());
        assertEquals("express,self_pickup", inserted.getDeliveryTypes());

        // 更新的两条同样带 shop_id，越权改不到别家的同名编码
        ArgumentCaptor<Long> shopCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Product> updCap = ArgumentCaptor.forClass(Product.class);
        verify(productMapper, times(2)).updateByProductCode(shopCap.capture(), updCap.capture());
        for (Long s : shopCap.getAllValues()) {
            assertEquals(SHOP_ID, s, "update 的 WHERE 必须带当前店铺");
        }
        assertEquals("P001", updCap.getAllValues().get(0).getProductCode());
        assertEquals("P002", updCap.getAllValues().get(1).getProductCode());
    }

    @Test
    @DisplayName("P23b 编码为空的行跳过并计入失败，绝不当新商品插进去")
    void should_skip_row_without_product_code() throws Exception {
        when(productMapper.countByProductCode(SHOP_ID, "P001")).thenReturn(1);
        when(productMapper.countByProductCode(SHOP_ID, "P999")).thenReturn(0);

        byte[] xlsx = buildWorkbook(
                Arrays.asList("商品编码", "商品名称", "销售价"),
                Arrays.asList(
                        row("P001", "手机壳", "19.9"),
                        row("", "没有编码的商品", "9.9"),
                        row("P999", "新品", "2999")));

        Result<ImportResultVO> result = service.importProducts(new ByteArrayInputStream(xlsx));

        ImportResultVO data = result.getData();
        assertEquals(Integer.valueOf(3), data.getTotal());
        assertEquals(Integer.valueOf(1), data.getInsert());
        assertEquals(Integer.valueOf(1), data.getUpdate());
        assertEquals(Integer.valueOf(1), data.getFailed(), "空编码那一行算失败");
        // ★ 少了这道判断，每导一次就多一批没有编码的空商品，而且删都删不干净
        verify(productMapper, times(1)).insert(any(Product.class));
    }

    @Test
    @DisplayName("P23c 未登录导入：返回 401，一个商品都不写")
    void should_reject_import_when_not_logged_in() throws Exception {
        UserContext.clear();

        byte[] xlsx = buildWorkbook(
                Arrays.asList("商品编码", "商品名称"),
                Collections.singletonList(row("P001", "手机壳")));

        Result<ImportResultVO> result = service.importProducts(new ByteArrayInputStream(xlsx));

        assertEquals(Integer.valueOf(401), result.getCode());
        verifyNoInteractions(productMapper);
    }

    /** 供导出断言用的商品：把导出涉及的每一列都填上不同的值 */
    private Product productForExport(Long id, String code, String name) {
        Product p = product(id, name, Integer.valueOf(10));
        p.setProductCode(code);
        p.setMainImage("http://img/" + code + ".jpg");
        p.setMarketPrice(Double.valueOf(129.00));
        p.setPrice(Double.valueOf(99.00));
        p.setSales(Integer.valueOf(120));
        p.setInflatedSales(Integer.valueOf(5000));
        p.setWarehouseStock(Integer.valueOf(8));
        p.setStoreStock(Integer.valueOf(2));
        p.setGoodsType("normal");
        p.setCategoryKind("physical");
        p.setStatus("on_sale");
        p.setDeliveryTypes("express,self_pickup");
        return p;
    }

    private static List<String> row(String... values) {
        return Arrays.asList(values);
    }

    /**
     * 造一个 xlsx 字节流。全部按<b>文本</b>写入 —— 商家填模板时单元格格式千奇百怪，
     * 导入端必须能从文本里把价格、库存解析成数字，不能指望单元格类型是 NUMERIC。
     */
    private static byte[] buildWorkbook(List<String> headers, List<List<String>> rows)
            throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("商品");
            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                head.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row line = sheet.createRow(r + 1);
                List<String> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    String v = values.get(c);
                    if (v == null) {
                        continue;   // 空编码：留成空单元格
                    }
                    line.createCell(c).setCellValue(v);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static List<String> headersOf(Sheet sheet) {
        Row head = sheet.getRow(0);
        List<String> list = new ArrayList<String>();
        for (int i = 0; i < head.getLastCellNum(); i++) {
            Cell c = head.getCell(i);
            list.add(c == null ? null : c.getStringCellValue());
        }
        return list;
    }

    /** 读单元格文本：数值型按整数形态输出（120 而不是 120.0），其余取字符串 */
    private static String textAt(Sheet sheet, int rowIdx, int colIdx) {
        Cell c = sheet.getRow(rowIdx).getCell(colIdx);
        if (c == null) {
            return null;
        }
        switch (c.getCellType()) {
            case NUMERIC:
                double d = c.getNumericCellValue();
                return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
            case STRING:
                return c.getStringCellValue();
            case BLANK:
                return "";
            default:
                return String.valueOf(c);
        }
    }

    // ==========================================================
    // 桩与断言辅助
    // ==========================================================

    private void stubList(List<Product> rows, long total) {
        when(productMapper.selectAdminPage(eq(SHOP_ID), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(rows);
        when(productMapper.countAdminPage(eq(SHOP_ID), any(), any(), any(), any(), any(), any()))
                .thenReturn(total);
    }

    private ProductQuery query(String keyword, String goodsType, String categoryKind,
                               String status, List<String> deliveryTypes, Long categoryId) {
        ProductQuery q = new ProductQuery();
        q.setKeyword(keyword);
        q.setGoodsType(goodsType);
        q.setCategoryKind(categoryKind);
        q.setStatus(status);
        q.setDeliveryTypes(deliveryTypes);
        q.setCategoryId(categoryId);
        return q;
    }

    private Product product(Long id, String name, Integer sort) {
        Product p = new Product();
        p.setId(id);
        p.setShopId(SHOP_ID);
        p.setName(name);
        p.setSort(sort);
        p.setPrice(Double.valueOf(99.00));
        p.setStock(Integer.valueOf(10));
        return p;
    }

    private ProductBatchCommand command(List<Long> ids) {
        ProductBatchCommand cmd = new ProductBatchCommand();
        cmd.setIds(ids);
        return cmd;
    }

    /** 取 Mapper 方法上 @Select / @Update 的 SQL 原文，空白归一化后便于断言 */
    private static String sqlOf(Class<?> mapper, String methodName) {
        for (Method m : mapper.getDeclaredMethods()) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            Select select = m.getAnnotation(Select.class);
            Update update = m.getAnnotation(Update.class);
            Annotation ann = select != null ? select : update;
            assertNotNull(ann, methodName + " 上应有 @Select 或 @Update");
            String[] parts = select != null ? select.value() : update.value();
            for (String part : parts) {
                sb.append(part);
            }
            return sb.toString().replaceAll("\\s+", " ");
        }
        throw new AssertionError("ProductMapper 上找不到方法 " + methodName);
    }

    /** 取 SQL 里 #{xxx} 占位符的列名，用于断「两个维度不能共用同一占位符」 */
    private static String placeholderOf(String sql, String name) {
        return sql.contains("#{" + name + "}") ? name : "<缺 " + name + ">";
    }
}
