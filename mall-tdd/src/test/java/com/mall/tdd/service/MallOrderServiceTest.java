package com.mall.tdd.service;

import com.mall.tdd.common.LoginUser;
import com.mall.tdd.common.Result;
import com.mall.tdd.common.UserContext;
import com.mall.tdd.dto.BuyNowVO;
import com.mall.tdd.dto.OrderItemVO;
import com.mall.tdd.dto.OrderListVO;
import com.mall.tdd.dto.OrderVO;
import com.mall.tdd.entity.Order;
import com.mall.tdd.entity.OrderItem;
import com.mall.tdd.entity.Product;
import com.mall.tdd.mapper.OrderMapper;
import com.mall.tdd.mapper.ProductMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * B3 · C 端订单管理 9 条（U01~U09）。
 *
 * <p>纯 Mockito，不启动 Spring 容器。Service 由 {@code @RequiredArgsConstructor}
 * 生成构造器，直接 {@code new} 出来即可。
 *
 * <p>★ {@code UserContext} 是静态 ThreadLocal，必须每个方法手动 set/clear，
 * 否则上一个用例的身份会串到下一个（Tomcat 线程复用就是这么串号的）。
 */
@DisplayName("B3 · C 端订单管理")
class MallOrderServiceTest {

    private static final Long USER_ID = 1001L;
    private static final Long OTHER_USER_ID = 1002L;
    private static final Long SHOP_ID = 1L;
    private static final String SHOP_NAME = "云边优选旗舰店";

    private OrderMapper orderMapper;
    private ProductMapper productMapper;
    private MallOrderService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        productMapper = mock(ProductMapper.class);
        service = new MallOrderService(orderMapper, productMapper);
        // 买家身份：id=1001，shopId 为 null（买家不属于任何店铺）
        UserContext.set(new LoginUser(USER_ID, null, "张三"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ==========================================================
    // 功能点 1 · 权限隔离（U01）
    // ==========================================================

    @Test
    @DisplayName("U01 只返回当前登录用户的订单")
    void should_only_return_current_user_orders() {
        // 桩按「当前用户」给的，实现若拿别人的 id 去查，桩不匹配会返回空列表
        when(orderMapper.selectByUserPage(eq(USER_ID), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(order(1L, "SO-D0-001"), order(2L, "SO-D0-003")));
        stubTabCounts(null);
        stubItems();

        Result<OrderListVO> result = service.listOrders(null, null, 1, 10);

        assertEquals(Integer.valueOf(200), result.getCode(), "正常登录应成功");
        assertEquals(2, result.getData().getOrders().size(), "应返回当前用户的 2 条订单");

        // ★ 核心：查询用的必须是登录用户 id，且只用了这一次
        verify(orderMapper, times(1))
                .selectByUserPage(eq(USER_ID), any(), any(), anyInt(), anyInt());
        // ★ userId 只能来自 UserContext，不能来自请求参数 —— 否则改个参数就能看别人的订单
        verify(orderMapper, never())
                .selectByUserPage(eq(OTHER_USER_ID), any(), any(), anyInt(), anyInt());
        verify(orderMapper, never()).countByUser(eq(OTHER_USER_ID), any(), any());
    }

    @Test
    @DisplayName("U01b 未登录时拒绝，且一个查询都不发")
    void should_reject_list_when_not_logged_in() {
        UserContext.clear();

        Result<OrderListVO> result = service.listOrders(null, null, 1, 10);

        assertEquals(Integer.valueOf(401), result.getCode(), "未登录应返回 401");
        // ★ 不是「返回空列表」就完事：如果继续查，SQL 会变成 WHERE user_id = NULL，
        //   页面显示 0 条，用户会以为自己的订单没了，而不是以为登录过期了。
        verifyNoInteractions(orderMapper);
    }

    // ==========================================================
    // 功能点 2 · Tab 计数（U02）
    // ==========================================================

    @Test
    @DisplayName("U02 各状态 Tab 计数，全部 = 独立统计（不是 4 个之和）")
    void should_return_count_per_status_tab() {
        when(orderMapper.selectByUserPage(eq(USER_ID), any(), any(), anyInt(), anyInt()))
                .thenReturn(Collections.<Order>emptyList());
        stubTabCounts(99L);
        stubItems();

        Result<OrderListVO> result = service.listOrders(null, null, 1, 10);
        OrderListVO data = result.getData();

        // 需求原文的示例数字：待付款 0 / 待发货 9 / 待收货 0 / 已完成 10
        assertEquals(0L, data.getTabs().getPendingPay(), "待付款");
        assertEquals(9L, data.getTabs().getPendingShip(), "待发货");
        assertEquals(0L, data.getTabs().getPendingReceive(), "待收货");
        assertEquals(10L, data.getTabs().getSuccess(), "已完成");

        // ★★ 「全部」必须是独立统计（99），不是 4 个 Tab 相加（0+9+0+10=19）。
        //    理由：订单有 7 种状态，REFUNDING / PENDING_GROUP / FAILED 不属于任何 Tab。
        //    真实库实测：shop1 共 38 单，4 个 Tab 之和只有 36 —— 差的就是 2 条退款中。
        //    若按「求和」实现，买家点「全部」会看不到自己正在退款的订单。
        assertEquals(99L, data.getTabs().getAll(), "全部应是独立统计，含所有状态");
        assertTrue(data.getTabs().getAll() != 19L,
                "全部 ≠ 4 个 Tab 之和，否则退款中/待成团的订单会凭空消失");
    }

    // ==========================================================
    // 功能点 3 · 订单号精确检索（U03）
    // ==========================================================

    @Test
    @DisplayName("U03 订单号精确检索，参数原样下发")
    void should_search_by_order_no_exactly() {
        String orderNo = "SO202610230001";
        when(orderMapper.selectByUserPage(eq(USER_ID), any(), eq(orderNo), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(order(1L, orderNo)));
        stubTabCounts(null);
        stubItems();

        Result<OrderListVO> result = service.listOrders(null, orderNo, 1, 10);

        assertEquals(1, result.getData().getOrders().size());
        assertEquals(orderNo, result.getData().getOrders().get(0).getOrderNo(), "订单号应完全相等");

        // ★ 订单号原样透传给 Mapper，绝不拼接 % 通配符。
        //   Mock 只能证明「参数没被加工」，证明不了 SQL 里是 = 还是 LIKE，
        //   所以另有一条真 SQL 探针：用前缀 SO-D0 去查必须返回 0 条。
        verify(orderMapper).selectByUserPage(
                eq(USER_ID), isNull(), eq(orderNo), anyInt(), anyInt());
        verify(orderMapper).countByUser(eq(USER_ID), isNull(), eq(orderNo));
    }

    // ==========================================================
    // 功能点 4 · 订单卡片字段（U04）
    // ==========================================================

    @Test
    @DisplayName("U04 订单卡片 9 个字段齐备")
    void should_return_all_order_card_fields() {
        Order o = order(1L, "SO-D0-001");
        o.setShopName(SHOP_NAME);
        o.setTotalAmount(Double.valueOf(128.00));
        o.setStatus("PENDING_SHIP");
        o.setPayType("alipay");

        when(orderMapper.selectByUserPage(eq(USER_ID), any(), any(), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(o));
        stubTabCounts(null);
        when(orderMapper.selectItemsByOrderIds(anyList()))
                .thenReturn(Arrays.asList(item(1L, "智能手机 Pro", Double.valueOf(128.00), 1),
                                           item(1L, "数据线", Double.valueOf(19.90), 2)));

        OrderVO vo = service.listOrders(null, null, 1, 10).getData().getOrders().get(0);

        assertEquals("SO-D0-001", vo.getOrderNo(), "订单编号");
        assertEquals(SHOP_NAME, vo.getShopName(),
                "店铺名称 —— tb_order 只有 shop_id，靠 shop_name 快照列（冗余写入，店铺改名不影响历史订单）");
        assertEquals(Double.valueOf(128.00), vo.getTotalAmount(), "总额");
        assertEquals("PENDING_SHIP", vo.getStatus(), "状态");
        assertEquals("alipay", vo.getPayType(),
                "支付方式 —— 只在待发货及之后才有值，待付款订单为空是正常的");

        assertEquals(2, vo.getItems().size(), "2 条商品明细");
        OrderItemVO first = vo.getItems().get(0);
        assertEquals("智能手机 Pro", first.getProductName(), "商品名称");
        assertEquals("/img/p1.png", first.getProductImage(), "商品图片");
        assertEquals(Integer.valueOf(1), first.getQuantity(), "数量");
        assertEquals(Double.valueOf(128.00), first.getPrice(), "单价");
    }

    // ==========================================================
    // 功能点 5 · 软删除（U05 / U06 / U07 / U08）
    // ==========================================================

    @Test
    @DisplayName("U05 删除订单 = 软删除，且 Mapper 上不存在物理删除方法")
    void should_soft_delete_order_not_physically() {
        when(orderMapper.softDeleteByIdAndUser(1L, USER_ID)).thenReturn(1);

        Result<Void> result = service.deleteOrder(1L);

        assertEquals(Integer.valueOf(200), result.getCode(), "删除应成功");
        verify(orderMapper, times(1)).softDeleteByIdAndUser(eq(1L), eq(USER_ID));

        // ★ 双保险之一：只断言「返回成功」是不够的 —— 实现完全可以先物理删了再返回成功。
        // ★ 双保险之二（更强）：OrderMapper 类上根本不许有物理删除方法。
        //   never() 只能证明「这次没调」，反射能证明「这个类里不存在」——
        //   后者阻止了未来任何人写出 deleteById 这种危险方法。
        List<String> physicalDeletes = new ArrayList<String>();
        for (Method m : OrderMapper.class.getDeclaredMethods()) {
            if (m.getName().startsWith("delete")) {
                physicalDeletes.add(m.getName());
            }
        }
        assertTrue(physicalDeletes.isEmpty(),
                "OrderMapper 上不允许出现物理删除方法，实际发现：" + physicalDeletes
                        + "（需求原文：删除 = 仅隐藏，后台保留订单数据）");
    }

    @Test
    @DisplayName("U06 买家删除后，后台查询仍能看到该订单")
    void should_keep_order_visible_in_admin_after_buyer_deletes() {
        // ★★ 这条锁的是 SQL 结构，不是返回值 —— Mock 根本没有 SQL，只能这样验。
        //    买家侧 3 个方法都必须带 is_deleted = 0；
        //    后台 selectAdminPage 刻意不带：若为了「统一」也加上，
        //    商家就再也看不到被买家删掉的问题订单，而这恰恰是客诉最需要的。
        assertTrue(sqlOf("selectByUserPage").contains("is_deleted = 0"),
                "买家列表必须过滤已删除");
        assertTrue(sqlOf("countByUser").contains("is_deleted = 0"),
                "买家总数必须过滤已删除（否则列表 2 条却显示共 3 条）");

        String adminSql = sqlOf("selectAdminPage");
        assertFalse(adminSql.contains("is_deleted"),
                "后台查询不许带 is_deleted 条件 —— 买家删了只是从自己眼前隐藏，"
                        + "后台必须仍然可见。实际 SQL：" + adminSql);

        // 后台的口径是 shop_id，不是 user_id：商家看的是本店所有订单
        assertTrue(adminSql.contains("shop_id"),
                "后台查询按 shop_id 隔离，实际 SQL：" + adminSql);
    }

    @Test
    @DisplayName("U07 越权：删不到别人的订单")
    void should_not_delete_other_users_order() {
        // 订单 2 属于用户 1002，当前登录 1001 → SQL 的 WHERE 同时带 id 和 user_id，影响 0 行
        when(orderMapper.softDeleteByIdAndUser(2L, USER_ID)).thenReturn(0);

        Result<Void> result = service.deleteOrder(2L);

        assertEquals(Integer.valueOf(500), result.getCode(), "影响 0 行应返回失败，不能傻乎乎报成功");

        // ★ 越权校验必须写在 SQL 的 WHERE 里（WHERE id=? AND user_id=?），
        //   不能「先查出来再判 userId」：后者有 TOCTOU 窗口，还多一次查询。
        assertTrue(sqlOf("softDeleteByIdAndUser").contains("user_id"),
                "删除语句必须自带 user_id 条件，实际 SQL：" + sqlOf("softDeleteByIdAndUser"));
    }

    @Test
    @DisplayName("U08 已删订单不出现在买家列表，且列表数与总数一致")
    void should_hide_deleted_orders_from_buyer_list() {
        // 用户 1001 共 3 单，其中订单 1 已软删除 → SQL 只返回 2 条
        when(orderMapper.selectByUserPage(eq(USER_ID), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(order(2L, "SO-D0-003"), order(3L, "SO-D1-001")));
        stubTabCounts(null);
        stubItems();

        // 总数必须与列表同一口径：都是 2。只改列表不改 total 会出现分页错乱
        when(orderMapper.countByUser(eq(USER_ID), isNull(), isNull())).thenReturn(2L);

        OrderListVO data = service.listOrders(null, null, 1, 10).getData();

        assertEquals(2, data.getOrders().size(), "已删订单不该出现");
        assertEquals(2L, data.getTotal(), "总数必须与列表同口径，否则「共 N 条」对不上");
        for (OrderVO vo : data.getOrders()) {
            assertFalse(Long.valueOf(1L).equals(vo.getId()), "订单 1 已删除，不该出现");
        }

        // ★ 上面三条的数据全部来自 mock，本质上是「mock 给什么就断言什么」，触不到 SQL。
        //   变异验证时把 selectByUserPage 里的 is_deleted = 0 删掉，这三条依然全绿 ——
        //   所以真正能锁住「过滤」的只有 SQL 结构断言，不能只靠返回值。
        assertTrue(sqlOf("selectByUserPage").contains("is_deleted = 0"),
                "买家列表 SQL 必须带 is_deleted = 0");
        assertTrue(sqlOf("countByUser").contains("is_deleted = 0"),
                "总数 SQL 必须同样过滤，只改列表不改总数就是「列表 2 条却显示共 3 条」");
    }

    // ==========================================================
    // 功能点 6 · 立即购买（U09）
    // ==========================================================

    @ParameterizedTest(name = "[{index}] 库存={0} 状态={1} → 可购买={2}")
    @CsvSource({
            "10, on_sale,  true",
            "0,  on_sale,  false",
            "10, off_sale, false"
    })
    @DisplayName("U09 立即购买：在售且有货才返回可下单信息")
    void should_return_buy_now_info_for_purchasable_product(
            int stock, String status, boolean purchasable) {
        // SQL 里带 status = 'on_sale'，所以下架商品根本查不出来（返回 null 而不是一个 off_sale 的对象）
        if (!"off_sale".equals(status)) {
            Product p = new Product();
            p.setId(Long.valueOf(5L));
            p.setName("智能手机 Pro");
            p.setPrice(Double.valueOf(99.00));
            p.setStock(Integer.valueOf(stock));
            p.setStatus(status);
            when(productMapper.selectByIdAndOnSale(5L)).thenReturn(p);
        }
        // off_sale 分支不桩：Mockito 默认返回 null，正好模拟「SQL 查不到」

        Result<BuyNowVO> result = service.buyNow(5L);

        if (!purchasable) {
            assertEquals(Integer.valueOf(500), result.getCode(),
                    stock == 0 ? "库存为 0 不能返回可购买信息" : "已下架不能返回可购买信息");
            assertNull(result.getData(), "失败时不该带可购买信息");
            return;
        }

        assertEquals(Integer.valueOf(200), result.getCode());
        BuyNowVO vo = result.getData();
        assertNotNull(vo, "应有可购买信息");
        assertEquals(Long.valueOf(5L), vo.getProductId(), "商品 id");
        assertEquals("智能手机 Pro", vo.getName(), "商品名称");
        assertEquals(Double.valueOf(99.00), vo.getPrice(), "售价");
        assertEquals(Integer.valueOf(stock), vo.getStock(), "库存");
        assertEquals("on_sale", vo.getStatus(), "状态");
    }

    // ==========================================================
    // 桩与断言辅助
    // ==========================================================

    /**
     * Tab 计数桩。
     *
     * <p>★ {@code allCount} 用 {@code lenient}：把「全部」桩成 99 是为了与 4 个 Tab 之和（19）
     * 拉开差距，用不一致的桩值才能逼出「实现到底是独立统计还是求和」。
     * 参数传 null 时不桩该值，因为不筛选时「列表 total」与「全部」是同一个调用。
     */
    private void stubTabCounts(Long allCount) {
        if (allCount != null) {
            lenient().when(orderMapper.countByUser(eq(USER_ID), isNull(), isNull()))
                    .thenReturn(allCount.longValue());
        }
        lenient().when(orderMapper.countByUser(eq(USER_ID), eq("PENDING_PAY"), isNull()))
                .thenReturn(0L);
        lenient().when(orderMapper.countByUser(eq(USER_ID), eq("PENDING_SHIP"), isNull()))
                .thenReturn(9L);
        lenient().when(orderMapper.countByUser(eq(USER_ID), eq("PENDING_RECEIVE"), isNull()))
                .thenReturn(0L);
        lenient().when(orderMapper.countByUser(eq(USER_ID), eq("SUCCESS"), isNull()))
                .thenReturn(10L);
    }

    /** 明细批量查询桩：列表为空时实现不该发这个查询，所以用 lenient 兜住 */
    private void stubItems() {
        lenient().when(orderMapper.selectItemsByOrderIds(anyList()))
                .thenReturn(Collections.<OrderItem>emptyList());
    }

    private Order order(Long id, String orderNo) {
        Order o = new Order();
        o.setId(id);
        o.setOrderNo(orderNo);
        o.setUserId(USER_ID);
        o.setShopId(SHOP_ID);
        o.setShopName(SHOP_NAME);
        return o;
    }

    private OrderItem item(Long orderId, String name, Double price, int quantity) {
        OrderItem it = new OrderItem();
        it.setOrderId(orderId);
        it.setProductId(Long.valueOf(1L));
        it.setProductName(name);
        it.setProductImage("/img/p1.png");
        it.setPrice(price);
        it.setQuantity(Integer.valueOf(quantity));
        return it;
    }

    /** 读 Mapper 方法上 {@code @Select}/{@code @Update} 注解里的 SQL 原文 */
    private String sqlOf(String methodName) {
        for (Method m : OrderMapper.class.getDeclaredMethods()) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            Select select = m.getAnnotation(Select.class);
            if (select != null) {
                return String.join(" ", select.value());
            }
            org.apache.ibatis.annotations.Update update =
                    m.getAnnotation(org.apache.ibatis.annotations.Update.class);
            if (update != null) {
                return String.join(" ", update.value());
            }
            fail(methodName + " 上既没有 @Select 也没有 @Update");
        }
        fail("OrderMapper 缺少方法：" + methodName);
        return null;
    }
}
