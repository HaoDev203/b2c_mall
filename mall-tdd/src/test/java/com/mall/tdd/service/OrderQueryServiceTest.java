package com.mall.tdd.service;

import com.mall.tdd.common.LoginUser;
import com.mall.tdd.common.Result;
import com.mall.tdd.common.UserContext;
import com.mall.tdd.dto.AdminOrderPageVO;
import com.mall.tdd.dto.AdminOrderQuery;
import com.mall.tdd.dto.AdminOrderVO;
import com.mall.tdd.dto.AdminTabCountVO;
import com.mall.tdd.dto.BatchResultVO;
import com.mall.tdd.entity.Order;
import com.mall.tdd.entity.OrderItem;
import com.mall.tdd.enums.OrderStatus;
import com.mall.tdd.mapper.AdminOrderMapper;
import com.mall.tdd.mapper.OrderMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B5 · 订单管理-订单查询 23 条（O01~O23；O24 导出要 POI，与 P22/P23 同批）。
 *
 * <p><b>这批的断言分三层，缺一层就会留假绿：</b>
 * <ol>
 *   <li><b>参数透传</b>（Mock）：证明 Service 把 11 个条件原样交给了 Mapper。</li>
 *   <li><b>SQL 原文</b>（反射读 {@code @Select}）：证明 <b>= 还是 LIKE</b>、
 *       隔离条件在不在、日期有没有套函数。这一层 Mock 永远看不见 ——
 *       B3 的 U08 就是只写了第一层，删掉 {@code is_deleted = 0} 照样绿。</li>
 *   <li><b>边界桩值</b>：让「求和」与「独立统计」、「行数」与「件数」的期望值不同，
 *       否则实现写错也绿（O14/O15 全靠这个）。</li>
 * </ol>
 *
 * <p>★ 时间必须可控：O12 的快捷时间依赖「今天」，用 {@link Clock#fixed} 钉死 2026-10-23，
 * 绝不写 {@code LocalDate.now()} —— 否则这 4 组断言会在某一天突然变红。
 */
@DisplayName("B5 订单管理-订单查询")
class OrderQueryServiceTest {

    private static final Long SHOP_ID = 1L;
    private static final Long OPERATOR_ID = 99L;

    /** 固定「今天」：O12 的四组期望值全部由它推出 */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private AdminOrderMapper adminOrderMapper;
    private OrderMapper orderMapper;
    private OrderQueryService service;

    @BeforeEach
    void setUp() {
        adminOrderMapper = mock(AdminOrderMapper.class);
        orderMapper = mock(OrderMapper.class);
        Clock clock = Clock.fixed(TODAY.atTime(10, 0).atZone(ZONE).toInstant(), ZONE);
        service = new OrderQueryService(adminOrderMapper, orderMapper, clock);
        UserContext.set(new LoginUser(OPERATOR_ID, SHOP_ID, "店铺老板"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ==========================================================
    // 功能点 1 · 顶部筛选 11 项（O01~O11）
    // ==========================================================

    @Test
    @DisplayName("O01 订单编号精确匹配：order_no = 而不是 LIKE")
    void should_search_by_order_no_exactly() {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query().orderNo("SO202610230001"), 1, 10);

        AdminOrderQuery q = captureQuery();
        assertEquals("SO202610230001", q.getOrderNo());

        // ★★ 这是整批最要紧的一条。一期末 shop 的 SQL 是
        //   `order_no LIKE '%' || ? || '%'`，而需求原文专门写了「精确输入」。
        //   Mock 只能证明参数传下去了，证明不了 = 还是 LIKE —— 必须读 SQL 原文。
        String sql = sqlOf(AdminOrderMapper.class, "selectQueryPage");
        assertTrue(sql.contains("order_no = #{q.orderNo}"),
                "订单编号必须用 = 精确匹配，实际 SQL：" + sql);
        assertFalse(sql.contains("order_no LIKE"),
                "订单编号不许用 LIKE（前缀会误命中，见 O01），实际 SQL：" + sql);
    }

    @Test
    @DisplayName("O02 订单类型筛选用 order_type（与订单类别是两个维度）")
    void should_filter_by_order_type() {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query().orderType("group"), 1, 10);

        assertEquals("group", captureQuery().getOrderType());

        String sql = sqlOf(AdminOrderMapper.class, "selectQueryPage");
        assertTrue(sql.contains("order_type = #{q.orderType}"), "实际 SQL：" + sql);
        assertFalse(sql.contains("order_category = #{q.orderType}"),
                "类型不许落到 order_category 上，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("O03 订单类别筛选用 order_category（普通 / 电子卡券）")
    void should_filter_by_order_category() {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query().orderCategory("e_card"), 1, 10);

        assertEquals("e_card", captureQuery().getOrderCategory());

        String sql = sqlOf(AdminOrderMapper.class, "selectQueryPage");
        assertTrue(sql.contains("order_category = #{q.orderCategory}"), "实际 SQL：" + sql);
        assertFalse(sql.contains("order_type = #{q.orderCategory}"),
                "O02 的「类型」和 O03 的「类别」是两个字段，不能共用占位符，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("O04 状态筛选含「待成团」：枚举必须是 7 个")
    void should_filter_by_status_including_group_pending() {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query().status("PENDING_GROUP"), 1, 10);

        assertEquals("PENDING_GROUP", captureQuery().getStatus());

        // ★ 一期末 shop 的 OrderStatus 只有 6 个、缺 PENDING_GROUP，反而多一个 REFUNDING。
        //   这里把 7 个钉死：少一个「待成团」时，筛这个 Tab 会永久返回空列表，
        //   而前端只是显示 0 条 —— 没人会报 bug，但成团订单在后台彻底不可见。
        assertEquals(7, OrderStatus.values().length, "订单状态应为 7 个");
        assertEquals("PENDING_GROUP", OrderStatus.PENDING_GROUP.name());

        assertTrue(sqlOf(AdminOrderMapper.class, "selectQueryPage").contains("status = #{q.status}"));
    }

    @Test
    @DisplayName("O05 收件人姓名模糊搜索（注意 O01 是精确，这里才是模糊）")
    void should_search_by_receiver_name_fuzzy() {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query().receiverName("张"), 1, 10);

        // 传下去的是裸关键词，% 由 SQL 拼
        assertEquals("张", captureQuery().getReceiverName());

        String sql = sqlOf(AdminOrderMapper.class, "selectQueryPage");
        assertTrue(sql.contains("receiver_name LIKE '%' || #{q.receiverName} || '%'"),
                "收件人姓名是模糊，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("O06 联系电话模糊搜索：138 必须能命中 13800138000")
    void should_search_by_receiver_phone_fuzzy() {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query().receiverPhone("138"), 1, 10);

        assertEquals("138", captureQuery().getReceiverPhone());

        // ★ 一期末 shop 的电话筛选是精确 =，需求要模糊。同一页面上
        //   O01（订单号）精确、O05/O06（姓名/电话）模糊，这是最容易写成一致的错误。
        String sql = sqlOf(AdminOrderMapper.class, "selectQueryPage");
        assertTrue(sql.contains("receiver_phone LIKE '%' || #{q.receiverPhone} || '%'"),
                "联系电话是模糊，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("O07 支付方式筛选：英文枚举入库，不存中文")
    void should_filter_by_pay_type() {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query().payType("wechat"), 1, 10);

        assertEquals("wechat", captureQuery().getPayType());
        assertTrue(sqlOf(AdminOrderMapper.class, "selectQueryPage").contains("pay_type = #{q.payType}"));
    }

    @Test
    @DisplayName("O08 售后状态筛选：APPLYING 走等值匹配")
    void should_filter_by_after_sale_status() {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query().afterSaleStatus("applying"), 1, 10);

        AdminOrderQuery q = captureQuery();
        assertEquals("applying", q.getAfterSaleStatus());
        assertFalse(q.isAfterSaleNone(), "「申请中」不是「暂无售后」");

        assertTrue(sqlOf(AdminOrderMapper.class, "selectQueryPage")
                .contains("after_sale_status = #{q.afterSaleStatus}"));
    }

    @Test
    @DisplayName("O08b 售后状态：「暂无售后」必须映射成 IS NULL，不是字符串 NONE")
    void should_map_none_after_sale_to_is_null() {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query().afterSaleStatus(AdminOrderQuery.AFTER_SALE_NONE), 1, 10);

        AdminOrderQuery q = captureQuery();
        // ★ 库里「暂无售后」存的是 NULL。若把 NONE 当字符串透传，
        //   SQL 变成 after_sale_status = 'NONE'，一条都查不到 ——
        //   而页面上「暂无售后」永远是 0 条，看起来像"真的没有"，最难发现。
        assertNull(q.getAfterSaleStatus(), "NONE 不能原样透传成字符串条件");
        assertTrue(q.isAfterSaleNone(), "NONE 要转成 IS NULL 的开关");

        assertTrue(sqlOf(AdminOrderMapper.class, "selectQueryPage")
                .contains("after_sale_status IS NULL"), "实际 SQL 里缺 IS NULL 分支");
    }

    @Test
    @DisplayName("O09 物流类型筛选：logistics_type 不是商品的 delivery_types")
    void should_filter_by_logistics_type() {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query().logisticsType("city"), 1, 10);

        assertEquals("city", captureQuery().getLogisticsType());

        // ★ 「物流类型」是这一单怎么送（订单维度），
        //   「配送方式」是商品支持哪几种（商品维度，P05 的 delivery_types）。
        //   两者语义不同、表也不同，共用字段名就会把订单筛成商品。
        String sql = sqlOf(AdminOrderMapper.class, "selectQueryPage");
        assertTrue(sql.contains("logistics_type = #{q.logisticsType}"), "实际 SQL：" + sql);
        assertFalse(sql.contains("delivery_types"), "订单表里没有 delivery_types，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("O10 门店名称筛选：下拉选全名，走精确匹配")
    void should_filter_by_store_name() {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query().storeName("南京新街口店"), 1, 10);

        assertEquals("南京新街口店", captureQuery().getStoreName());

        // 门店是下拉选出来的全名，精确匹配即可；
        // 做成模糊会让「新街口」同时命中「南京新街口店」和「新街口二店」。
        String sql = sqlOf(AdminOrderMapper.class, "selectQueryPage");
        assertTrue(sql.contains("store_name = #{q.storeName}"), "实际 SQL：" + sql);
        assertFalse(sql.contains("store_name LIKE"), "门店是精确匹配，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("O11 下单时间闭区间：含结束日整天，且不在列上套函数")
    void should_filter_by_create_time_range() {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query().range("2026-10-01", "2026-10-23"), 1, 10);

        AdminOrderQuery q = captureQuery();
        assertEquals("2026-10-01", q.getStartDate());
        assertEquals("2026-10-23", q.getEndDate());

        String sql = sqlOf(AdminOrderMapper.class, "selectQueryPage");
        // ★★ 两端都要闭：endDate 写成 create_time <= '2026-10-23' 会漏掉
        //   结束日全部订单 —— 因为 '2026-10-23 10:00' > '2026-10-23'（字典序）。
        //   正确写法是 < date(endDate, '+1 day')，既闭区间又不套函数。
        assertTrue(sql.contains("create_time >= #{q.startDate}"), "实际 SQL：" + sql);
        // 注解原文里 < 必须是 XML 转义写法 &lt;（<script> 按 XML 解析），
        // 所以这里断言的是转义后的原文；语义仍是「小于结束日次日零点」= 闭区间取整天。
        assertTrue(sql.contains("create_time &lt; date(#{q.endDate}, '+1 day')"),
                "结束日必须用 < date(endDate,'+1 day') 取整天，实际 SQL：" + sql);
        // ★ 与 B2 同一条原则：date(create_time) 会让 idx_order_shop / 时间条件全部失效
        assertFalse(sql.contains("date(create_time)"),
                "不在列上套函数，实际 SQL：" + sql);
        assertFalse(sql.contains("create_time <= #{q.endDate}"),
                "<= endDate 会漏掉结束日，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("O11b 只传一端时各自单独生效")
    void should_apply_single_bound_when_only_one_date() {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query().range("2026-10-01", null), 1, 10);

        AdminOrderQuery q = captureQuery();
        assertEquals("2026-10-01", q.getStartDate());
        assertNull(q.getEndDate(), "没传结束日就是 null，不能补成今天");

        // 一个 <if> 包一个边界：只传开始就只筛开始，不会因为缺 endDate 整个区间失效
        String sql = sqlOf(AdminOrderMapper.class, "selectQueryPage");
        assertTrue(sql.contains("q.startDate != null"), "实际 SQL：" + sql);
        assertTrue(sql.contains("q.endDate != null"), "实际 SQL：" + sql);
    }

    // ==========================================================
    // 功能点 2 · 快捷时间与组合筛选（O12~O13）
    // ==========================================================

    @ParameterizedTest(name = "{0} → {1} ~ {2}")
    @CsvSource({
            "today,   2026-10-23, 2026-10-23",
            "yesterday,2026-10-22, 2026-10-22",
            "last7,   2026-10-17, 2026-10-23",
            "last30,  2026-09-24, 2026-10-23"
    })
    @DisplayName("O12 快捷时间换算（今日/昨日/近7天/近30天）")
    void should_resolve_quick_date_range(String quick, String start, String end) {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query().quickDate(quick), 1, 10);

        AdminOrderQuery q = captureQuery();
        // ★ 「近 7 天」的口径钉死为「今天-6 ~ 今天」（共 7 天，含今天）。
        //   含/不含今天差一天，不同人算出来不一样，必须写死在这里。
        assertEquals(start, q.getStartDate(), quick + " 的开始日");
        assertEquals(end, q.getEndDate(), quick + " 的结束日");
    }

    @Test
    @DisplayName("O12b 未知快捷项要报错，不能静默当成「不筛」")
    void should_reject_unknown_quick_date() {
        Result<AdminOrderPageVO> r = service.listOrders(query().quickDate("last90"), 1, 10);

        // ★ 静默忽略 = 用户以为筛了「近 90 天」，实际看到的是全表，
        //   和 P04b（非法状态静默忽略）是同一类错误。
        assertEquals(Integer.valueOf(500), r.getCode());
        verify(adminOrderMapper, never()).selectQueryPage(anyLong(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("O13 11 个筛选条件同时生效，不互相覆盖")
    void should_apply_all_filters_together() {
        stubList(Collections.<Order>emptyList(), 0L);

        service.listOrders(query()
                .orderNo("SO202610230001")
                .orderType("group")
                .orderCategory("e_card")
                .status("PENDING_SHIP")
                .receiverName("张")
                .receiverPhone("138")
                .payType("wechat")
                .afterSaleStatus("applying")
                .logisticsType("city")
                .storeName("南京新街口店")
                .range("2026-10-01", "2026-10-23"), 1, 10);

        // 一次调用捕获，逐项断言 —— 不能拆成 11 个独立测试，
        // 那样测不出「同时传会不会互相覆盖」。这是 11 项里最容易出错的点：
        // 后一个 if 覆盖前一个变量，单测各自都绿，一起用就只剩最后一个条件。
        AdminOrderQuery q = captureQuery();
        assertEquals("SO202610230001", q.getOrderNo());
        assertEquals("group", q.getOrderType());
        assertEquals("e_card", q.getOrderCategory());
        assertEquals("PENDING_SHIP", q.getStatus());
        assertEquals("张", q.getReceiverName());
        assertEquals("138", q.getReceiverPhone());
        assertEquals("wechat", q.getPayType());
        assertEquals("applying", q.getAfterSaleStatus());
        assertEquals("city", q.getLogisticsType());
        assertEquals("南京新街口店", q.getStoreName());
        assertEquals("2026-10-01", q.getStartDate());
        assertEquals("2026-10-23", q.getEndDate());

        // 11 个条件在 SQL 里各占一个 <if>，一个都不能少
        String sql = sqlOf(AdminOrderMapper.class, "selectQueryPage");
        for (String cond : Arrays.asList("order_no = #{q.orderNo}", "order_type = #{q.orderType}",
                "order_category = #{q.orderCategory}", "status = #{q.status}",
                "receiver_name LIKE", "receiver_phone LIKE", "pay_type = #{q.payType}",
                "after_sale_status = #{q.afterSaleStatus}", "logistics_type = #{q.logisticsType}",
                "store_name = #{q.storeName}", "create_time >= #{q.startDate}")) {
            assertTrue(sql.contains(cond), "SQL 缺条件 " + cond + "，实际：" + sql);
        }
    }

    // ==========================================================
    // 功能点 3 · 状态快捷标签栏（O14）
    // ==========================================================

    @Test
    @DisplayName("O14 七个 Tab：「全部」独立统计，不是 6 个状态之和")
    void should_return_count_for_seven_tabs() {
        stubList(Collections.<Order>emptyList(), 0L);
        // ★ 关键桩：全部故意给 999，6 个状态各给不同值。
        //   若实现写成求和，all 会等于 11+22+33+44+55+66 ≠ 999 → 立刻红。
        when(adminOrderMapper.countAll(SHOP_ID)).thenReturn(999L);
        when(adminOrderMapper.countTab(SHOP_ID, "PENDING_PAY")).thenReturn(11L);
        when(adminOrderMapper.countTab(SHOP_ID, "PENDING_SHIP")).thenReturn(22L);
        when(adminOrderMapper.countTab(SHOP_ID, "PENDING_RECEIVE")).thenReturn(33L);
        when(adminOrderMapper.countTab(SHOP_ID, "SUCCESS")).thenReturn(44L);
        when(adminOrderMapper.countTab(SHOP_ID, "FAILED")).thenReturn(55L);
        when(adminOrderMapper.countTab(SHOP_ID, "PENDING_GROUP")).thenReturn(66L);

        Result<AdminOrderPageVO> r = service.listOrders(query(), 1, 10);

        AdminTabCountVO tabs = r.getData().getTabs();
        // ★★ 「全部」= 独立 COUNT(*)，不是 6 个 Tab 相加。
        //   7 种状态里 REFUNDING 不属于任何 Tab，求和会让商家点「全部」时
        //   看不到正在退款的订单 —— 而这恰恰是最需要处理的订单。
        //   （B3 的买家侧 4 Tab 同理，真库实测 shop1 是 38 vs 36。）
        assertEquals(999L, tabs.getAll(), "「全部」必须是独立统计");
        assertEquals(11L, tabs.getPendingPay());
        assertEquals(22L, tabs.getPendingShip());
        assertEquals(33L, tabs.getPendingReceive());
        assertEquals(44L, tabs.getSuccess());
        assertEquals(55L, tabs.getFailed());
        assertEquals(66L, tabs.getPendingGroup());

        // 7 个状态里有 1 个（REFUNDING）不属于任何 Tab —— 这正是「全部」必须独立统计的理由
        assertNotNull(OrderStatus.REFUNDING, "7 个状态里必须有不属于任何 Tab 的那个");
    }

    // ==========================================================
    // 功能点 4 · 每行订单展示（O15~O17）
    // ==========================================================

    @Test
    @DisplayName("O15 「共 N 件」= 明细数量之和，不是明细行数")
    void should_compute_total_quantity_from_order_items() {
        Order o = order(1L, "SO001", "PENDING_SHIP");
        stubList(Collections.singletonList(o), 1L);
        // 2 行明细、共 9 件。★ 两者必须不同，否则实现写成 items.size() 也会绿
        when(orderMapper.selectItemsByOrderIds(any()))
                .thenReturn(Arrays.asList(item(1L, "商品A", 1), item(1L, "商品B", 8)));

        Result<AdminOrderPageVO> r = service.listOrders(query(), 1, 10);

        AdminOrderVO vo = r.getData().getList().get(0);
        assertEquals(9, vo.getTotalQuantity(), "共 N 件是数量合计（1+8=9），不是 2 行");
        assertEquals(2, vo.getItems().size(), "明细行数仍是 2");
    }

    @Test
    @DisplayName("O16 收件人手机号必须由后端脱敏")
    void should_mask_receiver_phone() {
        Order o = order(1L, "SO001", "PENDING_SHIP");
        o.setReceiverPhone("13800138000");
        stubList(Collections.singletonList(o), 1L);

        Result<AdminOrderPageVO> r = service.listOrders(query(), 1, 10);

        AdminOrderVO vo = r.getData().getList().get(0);
        // ★★ 脱敏必须发生在后端。前端遮只是视觉效果，F12 一看原文就在响应体里。
        assertEquals("138****8000", vo.getReceiverPhoneMasked());

        // ★ 返回对象上只允许有「脱敏版」一个手机号字段。若以后有人图方便加回
        //   getReceiverPhone()，脱敏等于白做 —— 用反射把这条路封死。
        for (Method m : AdminOrderVO.class.getDeclaredMethods()) {
            assertFalse(m.getName().equals("getReceiverPhone"),
                    "AdminOrderVO 不许暴露未脱敏的 getReceiverPhone()");
        }
    }

    @Test
    @DisplayName("O16b 手机号为空 / 非 11 位时不抛异常")
    void should_handle_null_phone_when_masking() {
        Order a = order(1L, "SO001", "PENDING_SHIP");
        a.setReceiverPhone(null);
        Order b = order(2L, "SO002", "PENDING_SHIP");
        b.setReceiverPhone("0571-8888");
        stubList(Arrays.asList(a, b), 2L);

        Result<AdminOrderPageVO> r = assertDoesNotThrow(() -> service.listOrders(query(), 1, 10));

        List<AdminOrderVO> list = r.getData().getList();
        assertNull(list.get(0).getReceiverPhoneMasked(), "null 原样返回，不抛异常");
        assertEquals("0571-8888", list.get(1).getReceiverPhoneMasked(),
                "短号/固话原样返回 —— 硬切 3-4 位会把号码切坏");
    }

    @ParameterizedTest(name = "{0} → 含 {1}")
    @CsvSource({
            "PENDING_SHIP,      PENDING_SHIP",
            "PENDING_RECEIVE,   PENDING_RECEIVE"
    })
    @DisplayName("O17 状态标签：待发货 / 待收货")
    void should_return_status_tags(String status, String expectedTag) {
        Order o = order(1L, "SO001", status);
        o.setOrderType("normal");
        o.setOrderCategory("normal");
        o.setLogisticsType("express");
        stubList(Collections.singletonList(o), 1L);

        Result<AdminOrderPageVO> r = service.listOrders(query(), 1, 10);

        AdminOrderVO vo = r.getData().getList().get(0);
        assertTrue(vo.getTags().contains(expectedTag),
                status + " 应带标签 " + expectedTag + "，实际：" + vo.getTags());
    }

    @Test
    @DisplayName("O17c 交易成功的订单不带「待发货/待收货」标签")
    void should_not_tag_finished_order_as_pending() {
        Order o = order(1L, "SO001", "SUCCESS");
        o.setOrderCategory("normal");
        o.setLogisticsType("express");
        stubList(Collections.singletonList(o), 1L);

        Result<AdminOrderPageVO> r = service.listOrders(query(), 1, 10);

        List<String> tags = r.getData().getList().get(0).getTags();
        // 标签是「已发生的状态」，不是「曾经经历过的状态」：
        // 已完成订单若还挂着「待发货」，商家会重复发货。
        assertFalse(tags.contains("PENDING_SHIP"), "实际：" + tags);
        assertFalse(tags.contains("PENDING_RECEIVE"), "实际：" + tags);
    }

    @Test
    @DisplayName("O17b 一条订单可同时有多个标签（状态/售后/类别/物流四个维度）")
    void should_return_multiple_tags_for_one_order() {
        Order o = order(1L, "SO001", "PENDING_SHIP");
        o.setAfterSaleStatus("applying");
        o.setOrderCategory("e_card");
        o.setLogisticsType("city");
        stubList(Collections.singletonList(o), 1L);

        Result<AdminOrderPageVO> r = service.listOrders(query(), 1, 10);

        // ★ 返回值是数组不是单值：这 5 个标签混了 4 个维度（状态/售后/类别/物流），
        //   同一单可以同时是「待发货 + 退款申请中 + 电子卡券 + 同城配送」。
        List<String> tags = r.getData().getList().get(0).getTags();
        assertTrue(tags.containsAll(Arrays.asList(
                        "PENDING_SHIP", "REFUND_APPLYING", "E_CARD", "LOCAL_DELIVERY")),
                "实际标签：" + tags);
    }

    // ==========================================================
    // 功能点 5 · 发货（O18~O21）
    // ==========================================================

    @Test
    @DisplayName("O18 发货写入物流单号，状态转待收货")
    void should_write_tracking_no_when_shipping() {
        when(adminOrderMapper.selectStatusByIdAndShop(1L, SHOP_ID)).thenReturn("PENDING_SHIP");
        when(adminOrderMapper.shipOrder(1L, SHOP_ID, "SF1234567890")).thenReturn(1);

        Result<Void> r = service.ship(1L, "SF1234567890");

        assertEquals(Integer.valueOf(200), r.getCode());
        verify(adminOrderMapper).shipOrder(1L, SHOP_ID, "SF1234567890");

        // ★★ 状态流转与单号写入必须是<b>同一条 UPDATE</b>，不能拆成两步。
        //   拆开就有中间态：先写单号后改状态，会留下「有运单号但仍是待发货」的脏数据 ——
        //   买家看得到单号，订单却停在待发货，这是最难解释的线上问题。
        String sql = sqlOf(AdminOrderMapper.class, "shipOrder");
        assertTrue(sql.contains("status = 'PENDING_RECEIVE'"), "实际 SQL：" + sql);
        assertTrue(sql.contains("tracking_no = #{trackingNo}"), "实际 SQL：" + sql);
        // WHERE 里再兜一次状态，防并发重复发货
        assertTrue(sql.contains("status = 'PENDING_SHIP'"), "WHERE 必须再校验状态，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("O18b 物流单号为空直接拒绝，不发任何 SQL")
    void should_reject_ship_without_tracking_no() {
        Result<Void> r = service.ship(1L, "   ");

        assertEquals(Integer.valueOf(500), r.getCode());
        // 单号是需求里发货弹窗的必填项，空单号发出去等于没发
        verify(adminOrderMapper, never()).shipOrder(anyLong(), anyLong(), any());
        verify(adminOrderMapper, never()).selectStatusByIdAndShop(anyLong(), anyLong());
    }

    @ParameterizedTest(name = "状态 {0} 不可发货")
    @ValueSource(strings = {"PENDING_PAY", "PENDING_RECEIVE", "PENDING_GROUP", "SUCCESS",
            "FAILED", "REFUNDING"})
    @DisplayName("O19 仅「待发货」可发货")
    void should_reject_ship_when_status_is_not_pending_ship(String status) {
        when(adminOrderMapper.selectStatusByIdAndShop(1L, SHOP_ID)).thenReturn(status);

        Result<Void> r = service.ship(1L, "SF1234567890");

        // ★ 前端隐藏按钮不是约束：直接 curl 就能绕过，后端必须自己校验。
        assertEquals(Integer.valueOf(500), r.getCode());
        verify(adminOrderMapper, never()).shipOrder(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("O20 批量发货逐条独立：一条失败不回滚其余")
    void should_batch_ship_each_order_independently() {
        when(adminOrderMapper.selectStatusByIdAndShop(1L, SHOP_ID)).thenReturn("PENDING_SHIP");
        when(adminOrderMapper.selectStatusByIdAndShop(2L, SHOP_ID)).thenReturn("PENDING_RECEIVE");
        when(adminOrderMapper.selectStatusByIdAndShop(3L, SHOP_ID)).thenReturn("PENDING_SHIP");
        when(adminOrderMapper.shipOrder(1L, SHOP_ID, "SF999")).thenReturn(1);
        when(adminOrderMapper.shipOrder(3L, SHOP_ID, "SF999")).thenReturn(1);

        // 批量发货填一个共用单号（同批次同一次揽收，单号本来就相同）；
        // 不传单号的话 tracking_no 会被写成 NULL，等于静默把已填的单号清空。
        Result<BatchResultVO> r = service.batchShip(Arrays.asList(1L, 2L, 3L), "SF999");

        BatchResultVO data = r.getData();
        assertEquals(3, data.getTotal());
        assertEquals(2, data.getSuccess());
        assertEquals(1, data.getFailed());
        // ★ 不加 @Transactional：整体回滚 = 商家点一次什么都没发生，还不知道是哪条的问题
        verify(adminOrderMapper).shipOrder(1L, SHOP_ID, "SF999");
        verify(adminOrderMapper).shipOrder(3L, SHOP_ID, "SF999");
        verify(adminOrderMapper, never()).shipOrder(eq(2L), anyLong(), any());
    }

    @Test
    @DisplayName("O21 部分失败给明细，顺序与入参一致")
    void should_report_partial_failure_without_rollback() {
        when(adminOrderMapper.selectStatusByIdAndShop(1L, SHOP_ID)).thenReturn("PENDING_SHIP");
        when(adminOrderMapper.selectStatusByIdAndShop(2L, SHOP_ID)).thenReturn("SUCCESS");
        when(adminOrderMapper.selectStatusByIdAndShop(3L, SHOP_ID)).thenReturn("PENDING_SHIP");
        when(adminOrderMapper.shipOrder(1L, SHOP_ID, "SF999")).thenReturn(1);
        when(adminOrderMapper.shipOrder(3L, SHOP_ID, "SF999")).thenReturn(1);

        Result<BatchResultVO> r = assertDoesNotThrow(
                () -> service.batchShip(Arrays.asList(1L, 2L, 3L), "SF999"));

        BatchResultVO data = r.getData();
        // results 的顺序必须与入参 ids 一致，前端才能对着列表标红。
        assertEquals(1L, data.getResults().get(0).getId());
        assertEquals(2L, data.getResults().get(1).getId());
        assertEquals(3L, data.getResults().get(2).getId());
        assertTrue(data.getResults().get(0).isSuccess());
        assertFalse(data.getResults().get(1).isSuccess());
        assertNotNull(data.getResults().get(1).getMessage(), "失败项必须带原因");
        assertTrue(data.getResults().get(2).isSuccess(), "第 3 条不该被第 2 条连累");
        // 部分失败也返回 200：前端靠 failed + results 标红，返回 500 会让商家整批重试
        assertEquals(Integer.valueOf(200), r.getCode());
    }

    // ==========================================================
    // 功能点 6 · 数据隔离（O22~O23）
    // ==========================================================

    @Test
    @DisplayName("O22 三种入口都必须带 shop_id，别家订单不出现")
    void should_not_return_other_shops_orders() {
        stubList(Collections.<Order>emptyList(), 0L);

        // 入口一：无筛选
        service.listOrders(query(), 1, 10);
        // 入口二：带筛选
        service.listOrders(query().status("PENDING_SHIP"), 1, 10);
        // 入口三：快捷时间
        service.listOrders(query().quickDate("last7"), 1, 10);

        // ★ 11 个筛选条件 + 1 个隔离条件。隔离条件必须在所有分支都带上，
        //   任何一个分支漏了 shop_id 就是跨店数据泄露。
        verify(adminOrderMapper, times(3)).selectQueryPage(eq(SHOP_ID), any(), anyInt(), anyInt());
        verify(adminOrderMapper, times(3)).countQuery(eq(SHOP_ID), any());

        // ★ shopId 绝不放进查询对象：放进去就能被请求参数注入，
        //   改个 ?shopId=2 就跨店了。它只能来自 UserContext。
        for (Method m : AdminOrderQuery.class.getDeclaredMethods()) {
            assertFalse(m.getName().toLowerCase().contains("shopid"),
                    "AdminOrderQuery 不许有 shopId 字段：" + m.getName());
        }

        String sql = sqlOf(AdminOrderMapper.class, "selectQueryPage");
        assertTrue(sql.contains("shop_id = #{shopId}"), "列表 SQL 缺隔离条件，实际：" + sql);
        assertTrue(sqlOf(AdminOrderMapper.class, "countQuery").contains("shop_id = #{shopId}"));
    }

    @Test
    @DisplayName("O22b 每一个带 shopId 参数的方法，SQL 都必须含隔离条件")
    void should_cover_shop_isolation_in_every_sql() {
        // ★★ 这条是变异验证逼出来的：把 countAll 的 shop_id 删掉时，
        //   其余 15 条断言全绿 —— 因为 O22 只盯着列表和总数两条 SQL。
        //   Tab 计数同样按店铺隔离，漏一处就能看到别家的单量。
        int checked = 0;
        for (Method m : AdminOrderMapper.class.getDeclaredMethods()) {
            boolean hasShopParam = false;
            for (java.lang.annotation.Annotation[] anns : m.getParameterAnnotations()) {
                for (java.lang.annotation.Annotation a : anns) {
                    if (a instanceof Param && "shopId".equals(((Param) a).value())) {
                        hasShopParam = true;
                    }
                }
            }
            if (!hasShopParam) {
                continue;
            }
            String sql = sqlOf(AdminOrderMapper.class, m.getName());
            assertTrue(sql.contains("shop_id = #{shopId}"),
                    m.getName() + " 缺 shop_id 隔离条件，实际 SQL：" + sql);
            checked++;
        }
        // 方法数写死：新增一个带 shopId 的查询方法时，这里会红，
        // 逼着作者重新确认那条 SQL 也带了隔离。
        assertEquals(7, checked, "带 shopId 参数的 Mapper 方法数变了，请逐个复核隔离条件");
    }

    @Test
    @DisplayName("O23 知道 id 也动不了别家的订单：发货/详情都返回失败")
    void should_reject_ship_and_detail_of_other_shop_order() {
        // 订单 7 属于 shop 2 → 带 shop_id 的查询查不到
        when(adminOrderMapper.selectStatusByIdAndShop(7L, SHOP_ID)).thenReturn(null);
        when(adminOrderMapper.selectByIdAndShop(7L, SHOP_ID)).thenReturn(null);

        Result<Void> ship = service.ship(7L, "SF1234567890");
        // ★ 订单 7 属于 shop 2，当前店是 shop 1 → 查不到状态，状态校验必然失败
        assertEquals(Integer.valueOf(500), ship.getCode());
        verify(adminOrderMapper, never()).shipOrder(anyLong(), anyLong(), any());

        Result<AdminOrderVO> detail = service.detail(7L);
        // ★ 必须返回「失败」，不是 200 + null。返回 200 + null 会让前端渲染空白页，
        //   商家以为订单被删了，实际是被拒了 —— 两种完全不同的处置方式。
        assertEquals(Integer.valueOf(500), detail.getCode());
        assertNull(detail.getData());
    }

    // ==========================================================
    // 桩与断言工具
    // ==========================================================

    /** 列表 + 总数 + 6 个 Tab 的默认桩：只给需要走完整个列表流程的测试用 */
    private void stubList(List<Order> rows, long total) {
        when(adminOrderMapper.selectQueryPage(eq(SHOP_ID), any(AdminOrderQuery.class),
                anyInt(), anyInt())).thenReturn(rows);
        when(adminOrderMapper.countQuery(eq(SHOP_ID), any(AdminOrderQuery.class))).thenReturn(total);
        when(adminOrderMapper.countAll(SHOP_ID)).thenReturn(0L);
        when(adminOrderMapper.countTab(eq(SHOP_ID), any())).thenReturn(0L);
        // 空列表时 Service 不会去查明细（提前 return），这时候再桩就是 UnnecessaryStubbing
        if (!rows.isEmpty()) {
            when(orderMapper.selectItemsByOrderIds(any()))
                    .thenReturn(Collections.<OrderItem>emptyList());
        }
    }

    private AdminOrderQuery captureQuery() {
        ArgumentCaptor<AdminOrderQuery> captor = ArgumentCaptor.forClass(AdminOrderQuery.class);
        verify(adminOrderMapper).selectQueryPage(eq(SHOP_ID), captor.capture(), anyInt(), anyInt());
        return captor.getValue();
    }

    private Order order(Long id, String orderNo, String status) {
        Order o = new Order();
        o.setId(id);
        o.setOrderNo(orderNo);
        o.setShopId(SHOP_ID);
        o.setShopName("测试店铺");
        o.setStatus(status);
        o.setPayAmount(Double.valueOf(128.00));
        o.setTotalAmount(Double.valueOf(128.00));
        o.setReceiverName("张三");
        o.setReceiverPhone("13800138000");
        o.setCreateTime("2026-10-23 09:00:00");
        return o;
    }

    private OrderItem item(Long orderId, String name, int quantity) {
        OrderItem it = new OrderItem();
        it.setOrderId(orderId);
        it.setProductName(name);
        it.setQuantity(Integer.valueOf(quantity));
        it.setPrice(Double.valueOf(10.00));
        return it;
    }

    private static AdminOrderQuery query() {
        return new AdminOrderQuery();
    }

    /** 取 Mapper 方法上 @Select / @Update 的 SQL 原文，空白归一化后便于断言 */
    private static String sqlOf(Class<?> mapper, String methodName) {
        for (Method m : mapper.getDeclaredMethods()) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            Select select = m.getAnnotation(Select.class);
            Update update = m.getAnnotation(Update.class);
            Annotation ann = select != null ? select : update;
            assertNotNull(ann, methodName + " 上应有 @Select 或 @Update");
            String[] parts = select != null ? select.value() : update.value();
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                sb.append(part);
            }
            return sb.toString().replaceAll("\\s+", " ");
        }
        throw new AssertionError("AdminOrderMapper 上找不到方法 " + methodName);
    }
}
