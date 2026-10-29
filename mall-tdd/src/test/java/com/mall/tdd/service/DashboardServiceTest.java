package com.mall.tdd.service;

import com.mall.tdd.common.LoginUser;
import com.mall.tdd.common.Result;
import com.mall.tdd.common.UserContext;
import com.mall.tdd.dto.HourAmount;
import com.mall.tdd.dto.MessageVO;
import com.mall.tdd.dto.MetricVO;
import com.mall.tdd.dto.MetricsVO;
import com.mall.tdd.dto.TrendVO;
import com.mall.tdd.entity.Message;
import com.mall.tdd.mapper.MessageMapper;
import com.mall.tdd.mapper.OrderMapper;
import com.mall.tdd.mapper.ProductMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * B2 · 店铺 Dashboard —— 23 条验收（D01~D23）。
 *
 * <p>纯 Mockito，不启动 Spring 容器。UserContext 是静态 ThreadLocal，
 * 所以在 {@code @BeforeEach} 手动 set、{@code @AfterEach} 手动 clear，
 * <b>不要 mockStatic</b> —— 静态 mock 与参数化测试一起跑会互相污染。
 *
 * <p>日期来自注入的 {@link Clock}，setUp 里用 {@code Clock.fixed} 钉死：
 * Service 和断言取同一个来源，跨零点也不会算出不同的「今天」。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("B2 店铺 Dashboard")
class DashboardServiceTest {

    /** 本店 id —— 三个 TO B 页面的隔离维度 */
    private static final Long SHOP_ID = 1L;

    /** 干扰店 id：演示数据里它每天都有一批订单，混入立刻能看出来 */
    private static final Long SHOP_ID_OTHER = 2L;

    private static final Long USER_ID = 9L;

    private static final int HOURS_IN_DAY = 24;

    private String today;
    private String yesterday;

    /** 钉死的时钟：Service 与断言取同一个来源，跨零点也不会不一致 */
    private Clock clock;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private MessageMapper messageMapper;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        // ★ 注入固定时钟，而不是让 Service 自己 LocalDate.now()：
        //   两边同源，断言不会在零点前后跑出「昨天/今天」错位（原注释承认过这个风险）
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        today = LocalDate.now(clock).toString();
        yesterday = LocalDate.now(clock).minusDays(1).toString();

        UserContext.set(new LoginUser(USER_ID, SHOP_ID, "店长"));

        service = new DashboardService(orderMapper, productMapper, messageMapper, clock);
    }

    @AfterEach
    void tearDown() {
        // 必须清 —— Tomcat 线程复用，不清会把店铺身份串到下一条测试
        UserContext.clear();
    }

    // ==========================================================
    // 功能点 1 · 顶部订单状态卡片（D01~D09）
    // ==========================================================

    /**
     * D01 · 未取到 shopId 直接失败，不查库。
     *
     * <p>为什么重要：shopId 为 null 时若继续查库，SQL 会变成 {@code WHERE shop_id = NULL}，
     * 查不到任何数据 → 页面全是 0。商家会以为「今天一单都没有」，实际是登录态丢了。
     * <b>失败要显式失败，不能静默返回 0</b>。
     */
    @ParameterizedTest(name = "{0} 在无 shopId 时必须失败且不发查询")
    @ValueSource(strings = {"cards", "trend", "metrics", "messages"})
    void should_fail_without_querying_when_shop_id_is_absent(String api) {
        UserContext.clear();   // 登录态丢失

        Result<?> result;
        if ("cards".equals(api)) {
            result = service.cards();
        } else if ("trend".equals(api)) {
            result = service.trend();
        } else if ("metrics".equals(api)) {
            result = service.metrics();
        } else {
            result = service.messages();
        }

        // ★★ 必须是 401，不能只断言「不是 200」。
        //    这条原本写的是 assertNotEquals(200)，于是返回 500 也照样绿 ——
        //    而 500 会让前端弹「服务器出错」，跳不到登录页。
        //    对照：其余 15 个接口未登录一律 401，而 Dashboard 恰恰是
        //    用户打开后台看到的第一个页面，最需要「跳到登录页」。
        assertEquals(Integer.valueOf(401), result.getCode(),
                api + " 未登录必须返回 401（前端据此跳登录页），不能退化成 500");
        verifyNoInteractions(orderMapper, productMapper, messageMapper);
    }

    /**
     * D24 · 「今天」必须来自注入的 {@link Clock}。
     *
     * <p>★ 原来 Service 里直接写 {@code LocalDate.now()}，测试也跟着系统日期跑，
     * 于是「零点前后执行」会让两边的今天错开一天（原注释自己承认了这个风险）。
     * 同一个项目里 {@code OrderQueryService} 已经注入了 Clock，Dashboard 没做 ——
     * 两种做法并存，等于给下次改动埋一个「改了一边忘了另一边」的坑。
     *
     * <p>★ 钉死到 2026-03-15 之后，如果实现里还是 {@code LocalDate.now()},
     * 这里就是真实日期而不是 2026-03-15，断言直接红。
     */
    @Test
    @DisplayName("D24 trend 的今天/昨天取自注入的 Clock，而不是系统时钟")
    void should_take_dates_from_injected_clock_in_trend() {
        Clock fixed = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"),
                ZoneId.of("Asia/Shanghai"));
        DashboardService withFixed =
                new DashboardService(orderMapper, productMapper, messageMapper, fixed);

        withFixed.trend();

        verify(orderMapper).sumPaidAmountByHour(SHOP_ID, "2026-03-15");
        verify(orderMapper).sumPaidAmountByHour(SHOP_ID, "2026-03-14");
    }

    @Test
    @DisplayName("D24b metrics 的今天/昨天同样取自注入的 Clock")
    void should_take_dates_from_injected_clock_in_metrics() {
        Clock fixed = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"),
                ZoneId.of("Asia/Shanghai"));
        DashboardService withFixed =
                new DashboardService(orderMapper, productMapper, messageMapper, fixed);

        withFixed.metrics();

        verify(orderMapper).countPaidOrders(SHOP_ID, "2026-03-15");
        verify(orderMapper).countPaidOrders(SHOP_ID, "2026-03-14");
    }

    /**
     * D02 · 返回体含 6 个卡片 key。
     *
     * <p>用「集合恰好相等」而不是 6 个 containsKey：后者在实现多返回一个
     * 「交易失败」卡片时照样绿，而需求里只有 6 张。
     */
    @Test
    @DisplayName("D02 恰好 6 个卡片 key")
    void should_return_six_status_card_keys() {
        stubCards(0L, 12L, 2L, 24L, 1L, 3L);

        Map<String, Long> data = service.cards().getData();

        assertEquals(new HashSet<String>(Arrays.asList(
                        "pendingPay", "pendingShip", "pendingReceive",
                        "completed", "refundAfterSale", "stockWarning")),
                data.keySet(),
                "卡片 key 必须恰好这 6 个 —— 多一个就是超出需求");
    }

    @Test
    @DisplayName("D03 待付款 = 本店 PENDING_PAY 订单数")
    void should_count_pending_pay_orders() {
        stubCards(5L, 12L, 2L, 24L, 1L, 3L);

        assertEquals(Long.valueOf(5L), service.cards().getData().get("pendingPay"),
                "待付款卡片取的是 PENDING_PAY 状态计数");
    }

    @Test
    @DisplayName("D04 待发货 = 本店 PENDING_SHIP 订单数")
    void should_count_pending_ship_orders() {
        stubCards(0L, 12L, 2L, 24L, 1L, 3L);

        assertEquals(Long.valueOf(12L), service.cards().getData().get("pendingShip"),
                "待发货 = 已付款未发货，需求示例值 12");
    }

    @Test
    @DisplayName("D05 待收货 = 本店 PENDING_RECEIVE 订单数")
    void should_count_pending_receive_orders() {
        stubCards(0L, 12L, 2L, 24L, 1L, 3L);

        assertEquals(Long.valueOf(2L), service.cards().getData().get("pendingReceive"),
                "待收货 = 已发货未确认，需求示例值 2");
    }

    /**
     * D06 · 已完成 = SUCCESS 订单数。
     *
     * <p>这条的价值是<b>钉死状态枚举的映射</b>：「已完成」对应 {@code SUCCESS} 而不是
     * {@code COMPLETED}。桩只对 SUCCESS 生效，若实现写成 COMPLETED，桩不命中返回 0，
     * 断言立刻红。
     */
    @Test
    @DisplayName("D06 已完成 = SUCCESS（不是 COMPLETED）")
    void should_count_completed_orders() {
        stubCards(0L, 12L, 2L, 24L, 1L, 3L);

        assertEquals(Long.valueOf(24L), service.cards().getData().get("completed"),
                "「已完成」映射到 SUCCESS —— 状态机里没有 COMPLETED 这个值");
        verify(orderMapper).countByStatus(eq(SHOP_ID), eq("SUCCESS"));
    }

    /**
     * D07 · 退款/售后 = REFUNDING 订单数。
     *
     * <p>需求原文是「正在进行退款、售后申请单据总数」。<b>需求缺口</b>：原文把退款与售后
     * 并列为两类单据，但库里只有单一的 {@code REFUNDING} 状态，没有独立售后单表。
     * 本期按单状态合并计算，答辩时需要能说清这一点。
     */
    @Test
    @DisplayName("D07 退款/售后 = REFUNDING（需求未区分两类单据）")
    void should_count_refund_and_after_sale_orders() {
        stubCards(0L, 12L, 2L, 24L, 1L, 3L);

        assertEquals(Long.valueOf(1L), service.cards().getData().get("refundAfterSale"),
                "退款/售后合并为 REFUNDING 一个状态统计");
        verify(orderMapper).countByStatus(eq(SHOP_ID), eq("REFUNDING"));
    }

    /**
     * D08 · 库存预警数 = 库存 ≤ 阈值的商品数。
     *
     * <p>口径两条，都由 SQL 承担（Mock 层只能验「调了哪个方法」，语义由真库探针验）：
     * <ul>
     *   <li>{@code stock <= stock_warn} <b>含等于</b>。需求原文写「低于」，但库存 3、阈值 3
     *       按 {@code <} 算会漏掉刚好卡线的那批，按 {@code <=} 预警更符合直觉 —— 这是
     *       有意的口径选择。</li>
     *   <li>{@code stock_warn IS NULL} 的商品<b>有意不预警</b>。不要为了「看起来完整」
     *       加 {@code OR stock_warn IS NULL} —— 那会让所有没配预警线的商品一上架就飘红。</li>
     * </ul>
     */
    @Test
    @DisplayName("D08 库存预警 = stock <= stock_warn（含等于）")
    void should_count_stock_warning_products() {
        stubCards(0L, 12L, 2L, 24L, 1L, 3L);

        assertEquals(Long.valueOf(3L), service.cards().getData().get("stockWarning"),
                "预警口径 stock <= stock_warn，NULL 阈值的商品不参与");
        verify(productMapper).countStockWarning(eq(SHOP_ID));
    }

    /**
     * D09 · 数据隔离：其他店铺的数据不混入。
     *
     * <p><b>最容易漏测的一条</b>：手测永远只登自己的账号，跨店混入根本发现不了。
     * 这里逐条 {@code never()} 列出来，比 {@code verifyNoMoreInteractions} 更精确。
     */
    @Test
    @DisplayName("D09 只查本店，绝不出现其他 shopId")
    void should_not_mix_other_shop_data() {
        stubCards(5L, 12L, 2L, 24L, 1L, 3L);

        Map<String, Long> data = service.cards().getData();

        assertEquals(Long.valueOf(5L), data.get("pendingPay"));
        assertEquals(Long.valueOf(12L), data.get("pendingShip"));
        assertEquals(Long.valueOf(2L), data.get("pendingReceive"));
        assertEquals(Long.valueOf(24L), data.get("completed"));
        assertEquals(Long.valueOf(1L), data.get("refundAfterSale"));
        assertEquals(Long.valueOf(3L), data.get("stockWarning"));

        // 任何一次查询带上干扰店 id 都算越权
        verify(orderMapper, never()).countByStatus(eq(SHOP_ID_OTHER), anyString());
        verify(productMapper, never()).countStockWarning(eq(SHOP_ID_OTHER));
    }

    // ==========================================================
    // 功能点 2 · 实时数据趋势图（D10~D13）
    // ==========================================================

    /**
     * D10 · 返回 24 个数据点。
     *
     * <p>不能「有数据的小时才返回」—— 前端画 24 个刻度，只给 5 个点会整体错位。
     */
    @Test
    @DisplayName("D10 今日/昨日曲线各 24 个点")
    void should_return_24_points_for_trend() {
        when(orderMapper.sumPaidAmountByHour(eq(SHOP_ID), anyString()))
                .thenReturn(Collections.<HourAmount>emptyList());

        TrendVO vo = service.trend().getData();

        assertEquals(HOURS_IN_DAY, vo.getToday().size(), "今日曲线必须补齐 24 个点");
        assertEquals(HOURS_IN_DAY, vo.getYesterday().size(), "昨日曲线必须补齐 24 个点");
    }

    /**
     * D11 · 今日曲线 = 该小时支付金额<b>合计</b>。
     *
     * <p>桩里的 10 时是两笔（100.00 + 256.50）合成的 356.50 —— 如果实现写成
     * 「每小时覆盖而非累加」，这条会红。
     */
    @Test
    @DisplayName("D11 今日按小时累加支付金额")
    void should_sum_today_paid_amount_by_hour() {
        when(orderMapper.sumPaidAmountByHour(eq(SHOP_ID), eq(today)))
                .thenReturn(Arrays.asList(
                        hour(0, 0.00), hour(9, 128.00), hour(10, 356.50), hour(14, 99.00)));
        when(orderMapper.sumPaidAmountByHour(eq(SHOP_ID), eq(yesterday)))
                .thenReturn(Collections.<HourAmount>emptyList());

        TrendVO vo = service.trend().getData();
        List<Double> todayPoints = vo.getToday();

        assertEquals(128.00, todayPoints.get(9).doubleValue(), 0.001, "9 时 128.00");
        assertEquals(356.50, todayPoints.get(10).doubleValue(), 0.001, "10 时两笔合计 356.50，不是取最后一笔");
        assertEquals(99.00, todayPoints.get(14).doubleValue(), 0.001, "14 时 99.00");
        assertEquals(0.00, todayPoints.get(0).doubleValue(), 0.001, "0 时无成交为 0");
    }

    /**
     * D12 · 昨日曲线，且必须<b>两次独立查询</b>。
     *
     * <p>不允许「查一次最近两天再在内存里按日期拆」—— 数据量一大就崩。
     * 这里用 ArgumentCaptor 抓两次调用的日期，顺序必须是「先今天、后昨天」。
     */
    @Test
    @DisplayName("D12 昨日按小时累加，且今日/昨日各查一次")
    void should_sum_yesterday_paid_amount_by_hour() {
        when(orderMapper.sumPaidAmountByHour(eq(SHOP_ID), eq(today)))
                .thenReturn(Collections.<HourAmount>emptyList());
        when(orderMapper.sumPaidAmountByHour(eq(SHOP_ID), eq(yesterday)))
                .thenReturn(Arrays.asList(hour(11, 200.00), hour(20, 480.00)));

        TrendVO vo = service.trend().getData();
        List<Double> yesterdayPoints = vo.getYesterday();

        assertEquals(200.00, yesterdayPoints.get(11).doubleValue(), 0.001, "昨日 11 时");
        assertEquals(480.00, yesterdayPoints.get(20).doubleValue(), 0.001, "昨日 20 时");
        assertEquals(0.00, yesterdayPoints.get(0).doubleValue(), 0.001, "昨日其余小时为 0");

        ArgumentCaptor<String> dates = ArgumentCaptor.forClass(String.class);
        verify(orderMapper, times(2)).sumPaidAmountByHour(eq(SHOP_ID), dates.capture());
        assertEquals(Arrays.asList(today, yesterday), dates.getAllValues(),
                "两次独立查询，先今天后昨天 —— 不能一次查两天再内存分组");
    }

    /**
     * D13 · 无成交的小时返回 0，不是缺项、更不是 null。
     *
     * <p>{@code null} 会让 ECharts 在缺失点断线，{@code 0} 才是正确的「无成交」。
     */
    @Test
    @DisplayName("D13 无成交补 0，不补 null")
    void should_fill_zero_for_hours_without_orders() {
        when(orderMapper.sumPaidAmountByHour(eq(SHOP_ID), eq(today)))
                .thenReturn(Collections.singletonList(hour(9, 66.00)));
        when(orderMapper.sumPaidAmountByHour(eq(SHOP_ID), eq(yesterday)))
                .thenReturn(Collections.<HourAmount>emptyList());

        List<Double> todayPoints = service.trend().getData().getToday();

        assertEquals(HOURS_IN_DAY, todayPoints.size());
        assertEquals(0.00, todayPoints.get(0).doubleValue(), 0.001, "0 时补 0");
        assertEquals(0.00, todayPoints.get(23).doubleValue(), 0.001, "23 时补 0");
        assertEquals(66.00, todayPoints.get(9).doubleValue(), 0.001, "9 时有数据");
        for (int i = 0; i < HOURS_IN_DAY; i++) {
            assertNotNull(todayPoints.get(i),
                    "第 " + i + " 个点不能是 null —— ECharts 遇到 null 会把曲线断开");
        }
    }

    /**
     * D13 补充 · Mapper 返回脏数据时不能崩。
     *
     * <p>SQL 里的 {@code CAST(strftime('%H', pay_time))} 已经保证小时落在 0~23，
     * 但库不是只有程序能写（演示数据就是手插的）。这里锁两件事：
     * 越界小时<b>忽略而不是抛数组越界</b>；金额为 null 按 0 处理；
     * Mapper 万一带回 null 整个 List 也要能补齐 24 个 0。
     */
    @Test
    @DisplayName("D13c Mapper 返回脏数据时不崩")
    void should_ignore_dirty_rows_from_mapper() {
        when(orderMapper.sumPaidAmountByHour(eq(SHOP_ID), eq(today)))
                .thenReturn(Arrays.asList(
                        hour(-1, 100.00),        // 越界，忽略
                        hour(25, 200.00),        // 越界，忽略
                        hour(8, 50.00),          // 正常
                        hourWithoutAmount(7)));  // 金额为 null → 按 0
        when(orderMapper.sumPaidAmountByHour(eq(SHOP_ID), eq(yesterday)))
                .thenReturn(null);               // Mapper 意外返回 null

        TrendVO vo = service.trend().getData();

        assertEquals(HOURS_IN_DAY, vo.getToday().size(), "越界行不能把数组撑大");
        assertEquals(50.00, vo.getToday().get(8).doubleValue(), 0.001, "合法小时照常落位");
        assertEquals(0.00, vo.getToday().get(7).doubleValue(), 0.001, "金额 null 补 0，不能是 null");
        assertEquals(HOURS_IN_DAY, vo.getYesterday().size(), "Mapper 返回 null 也要补齐 24 个 0");
        assertEquals(0.00, vo.getYesterday().get(0).doubleValue(), 0.001);
    }

    // ==========================================================
    // 功能点 3 · 四项核心经营指标（D14~D19）
    // ==========================================================

    /**
     * D14 · 支付客户数必须 {@code COUNT(DISTINCT user_id)}。
     *
     * <p>桩数据刻意让「订单数 3 ≠ 客户数 2」—— 否则实现写成 {@code COUNT(*)} 也能绿，
     * 这条测试就白写了。需求原文专门写了「<b>独立</b>客户」，这是最容易实现错的一个词。
     */
    @Test
    @DisplayName("D14 支付客户数去重：3 单来自 2 人 = 2")
    void should_count_distinct_paid_customers() {
        stubMetrics(2L, 1L, 3L, 1L, 0.00, 0.00, 600.00, 100.00);

        MetricsVO metrics = service.metrics().getData();

        assertEquals(2.00, metrics.getPaidCustomers().getValue(), 0.001,
                "3 笔订单来自 2 个 user_id，结果必须是 2 不是 3");
        verify(orderMapper).countDistinctPaidCustomers(eq(SHOP_ID), eq(today));
    }

    /**
     * D15 · 支付订单数。
     *
     * <p>口径是 {@code pay_time IS NOT NULL}，<b>不是</b> {@code status = 'PAID'} ——
     * 已支付订单会继续流转到待发货/待收货/成功，按 status 算会漏掉绝大部分。
     */
    @Test
    @DisplayName("D15 支付订单数按 pay_time 口径")
    void should_count_paid_orders() {
        stubMetrics(2L, 1L, 5L, 1L, 0.00, 0.00, 1000.00, 100.00);

        assertEquals(5.00, service.metrics().getData().getPaidOrders().getValue(), 0.001);
    }

    @Test
    @DisplayName("D16 退款金额")
    void should_sum_refund_amount() {
        stubMetrics(2L, 1L, 5L, 1L, 88.50, 10.00, 1000.00, 100.00);

        assertEquals(88.50, service.metrics().getData().getRefundAmount().getValue(), 0.001);
    }

    /**
     * D16 边界 · SUM 返回 null 时必须是 0.00。
     *
     * <p>SQL 里已经写了 {@code COALESCE(SUM(...), 0)}，但 Service 也要兜底 ——
     * 一旦有人改动 SQL 去掉 COALESCE，{@code null} 会让前端格式化直接崩。
     */
    @Test
    @DisplayName("D16b 无退款时返回 0.00 而不是 null")
    void should_return_zero_refund_when_sum_is_null() {
        when(orderMapper.sumRefundAmount(eq(SHOP_ID), eq(today))).thenReturn(null);

        assertEquals(0.00, service.metrics().getData().getRefundAmount().getValue(), 0.001,
                "null 会让前端格式化崩，必须兜底成 0.00");
    }

    /**
     * D17 · 客单价 = 总支付金额 ÷ 支付订单数。
     *
     * <p>用 delta 比较而不是 isEqualTo：{@code double} 有
     * {@code 0.1 + 0.2 = 0.30000000000000004} 的精度问题。
     */
    @Test
    @DisplayName("D17 客单价 = 总额 / 单数")
    void should_compute_average_order_value() {
        stubMetrics(2L, 1L, 4L, 1L, 0.00, 0.00, 1000.00, 100.00);

        assertEquals(250.00, service.metrics().getData().getAverageOrderValue().getValue(), 0.01,
                "1000.00 / 4 = 250.00");
    }

    /**
     * D17 边界 · 分母为 0 时返回 0.00。
     *
     * <p>不能抛 {@code ArithmeticException}，也不能是 {@code NaN}。
     */
    @Test
    @DisplayName("D17b 无支付订单时客单价 = 0.00，不崩不 NaN")
    void should_return_zero_average_when_no_paid_order() {
        stubMetrics(0L, 0L, 0L, 0L, 0.00, 0.00, 0.00, 0.00);

        double value = service.metrics().getData().getAverageOrderValue().getValue();

        assertEquals(0.00, value, 0.001, "分母为 0 必须返回 0.00");
        assertFalse(Double.isNaN(value), "不能是 NaN —— 前端拼 % 会显示 NaN%");
    }

    /**
     * D18 · 同比 = {@code (今日 - 昨日) / 昨日 * 100}。
     *
     * <p>返回值是<b>百分数</b>（50.00），不是倍数（0.5）—— 前端直接 {@code .2f} 拼 {@code %}。
     */
    @Test
    @DisplayName("D18 同比是百分数：150 vs 100 = +50.00")
    void should_compute_dod_as_percentage_change() {
        stubMetrics(2L, 1L, 150L, 100L, 100.00, 100.00, 1000.00, 1000.00);

        MetricVO paidOrders = service.metrics().getData().getPaidOrders();

        assertEquals(150.00, paidOrders.getValue(), 0.001);
        assertEquals(50.00, paidOrders.getDod(), 0.001,
                "(150-100)/100*100 = 50.00，是百分数不是倍数 0.5");
    }

    /** D18 边界 · 下降时必须带负号 */
    @Test
    @DisplayName("D18b 下降返回负数：80 vs 100 = -20.00")
    void should_compute_negative_dod_when_declining() {
        stubMetrics(2L, 1L, 80L, 100L, 100.00, 100.00, 1000.00, 1000.00);

        assertEquals(-20.00, service.metrics().getData().getPaidOrders().getDod(), 0.001,
                "下降必须返回 -20.00，不能是 20.00");
    }

    /**
     * D19 · 昨日为 0 → 固定 100.00%。★★
     *
     * <p><b>需求原文写死</b>的数字：示例数据昨日为 0 时显示「同比昨日 +100.00%」。
     * 数学上「从 0 增长」是无穷大，业务上定死 100%。
     * 四个指标都要遵守，所以一条测试里四个全查。
     */
    @Test
    @DisplayName("D19 昨日为 0 → 四项同比都是 +100.00%")
    void should_return_100_percent_dod_when_yesterday_is_zero() {
        // 昨日四项全为 0，今日都有值
        stubMetrics(3L, 0L, 5L, 0L, 88.50, 0.00, 1000.00, 0.00);

        MetricsVO metrics = service.metrics().getData();

        assertEquals(100.00, metrics.getPaidCustomers().getDod(), 0.001, "支付客户数：昨日 0 → 100.00%");
        assertEquals(100.00, metrics.getPaidOrders().getDod(), 0.001, "支付订单数：昨日 0 → 100.00%");
        assertEquals(100.00, metrics.getRefundAmount().getDod(), 0.001, "退款金额：昨日 0 → 100.00%");
        assertEquals(100.00, metrics.getAverageOrderValue().getDod(), 0.001, "客单价：昨日 0 → 100.00%");
    }

    // ==========================================================
    // 功能点 4 · 消息通知（D20~D23）
    // ==========================================================

    /**
     * D20 · 按时间倒序（12:00 → 10:00 → 09:00）。
     *
     * <p>排序由 SQL 的 {@code ORDER BY create_time DESC} 保证，Service 原样透传。
     * 「不重排」由下一条 {@code should_not_reorder_messages_in_memory} 反向锁死 ——
     * 两条一起看才完整：这条验倒序，下条验顺序不归 Service 管。
     */
    @Test
    @DisplayName("D20 消息按时间倒序")
    void should_order_messages_by_time_desc() {
        when(messageMapper.selectByShop(eq(SHOP_ID), anyInt())).thenReturn(Arrays.asList(
                message("SO-12", "pay_success", "2026-10-23 12:00:00"),
                message("SO-10", "refund_apply", "2026-10-23 10:00:00"),
                message("SO-09", "confirm_receive", "2026-10-23 09:00:00")));

        List<MessageVO> list = service.messages().getData();

        assertEquals(3, list.size());
        assertEquals("SO-12", list.get(0).getOrderNo(), "最新消息在最上方");
        assertEquals("SO-10", list.get(1).getOrderNo());
        assertEquals("SO-09", list.get(2).getOrderNo());
    }

    /**
     * D20 补充 · Service 不得在内存里重排。
     *
     * <p>桩<b>故意给乱序</b>（09 → 10 → 12），如果 Service 做了 {@code stream().sorted()}，
     * 输出会变回倒序 → 这条红。它锁的是「排序责任在 SQL」：分页时内存排序会错。
     */
    @Test
    @DisplayName("D20b Service 不重排，顺序完全交给 SQL")
    void should_not_reorder_messages_in_memory() {
        when(messageMapper.selectByShop(eq(SHOP_ID), anyInt())).thenReturn(Arrays.asList(
                message("SO-09", "confirm_receive", "2026-10-23 09:00:00"),
                message("SO-10", "refund_apply", "2026-10-23 10:00:00"),
                message("SO-12", "pay_success", "2026-10-23 12:00:00")));

        List<MessageVO> list = service.messages().getData();

        assertEquals("SO-09", list.get(0).getOrderNo(),
                "Service 必须原样透传 Mapper 的顺序 —— 排序写在 SQL 的 ORDER BY 里");
        assertEquals("SO-12", list.get(2).getOrderNo());
    }

    /**
     * D21 · 每条消息携带订单编号。★
     *
     * <p>这是需求「点击单条消息跳转订单详情页」的前提字段，缺了功能直接断链。
     * 旧 shop 的 Message 实体只有 title/content/msgType，没有 orderNo —— 新库必须建这一列。
     */
    @Test
    @DisplayName("D21 每条消息都带 orderNo")
    void should_attach_order_no_to_every_message() {
        when(messageMapper.selectByShop(eq(SHOP_ID), anyInt())).thenReturn(Arrays.asList(
                message("SO001", "pay_success", "2026-10-23 12:00:00"),
                message("SO002", "refund_apply", "2026-10-23 11:00:00"),
                message("SO003", "confirm_receive", "2026-10-23 10:00:00")));

        List<MessageVO> list = service.messages().getData();

        assertEquals(3, list.size());
        for (MessageVO vo : list) {
            assertNotNull(vo.getOrderNo(), "每条消息必须带订单编号 —— 前端点击跳转靠它");
            assertFalse(vo.getOrderNo().trim().isEmpty());
        }
        assertEquals("SO001", list.get(0).getOrderNo());
        assertEquals("SO002", list.get(1).getOrderNo());
        assertEquals("SO003", list.get(2).getOrderNo());
    }

    /**
     * D22 · 三类消息文案都含订单号。
     *
     * <p>文案<b>存库</b>（{@code content} 直接存完整文本），不运行时拼 ——
     * 拼的话订单号变了历史消息也会跟着变。所以这里锁的是「原样透传 + 单号不丢」。
     *
     * <p>断言用 {@code contains} 匹配关键片段而不是整句：需求文案里的「xxx」是占位符，
     * 整句断言会在改标点时莫名其妙红。
     */
    @ParameterizedTest(name = "{0} 文案含单号 {1}")
    @CsvSource({
            "pay_success,      SO-D0-001, 已支付成功",
            "refund_apply,     SO-D0-009, 7 天内",
            "confirm_receive,  SO-D1-004, 确认收货"
    })
    @DisplayName("D22 三种消息文案都含订单号")
    void should_build_three_message_types_with_order_no_in_text(
            String type, String orderNo, String keyword) {

        when(messageMapper.selectByShop(eq(SHOP_ID), anyInt()))
                .thenReturn(Collections.singletonList(
                        message(orderNo, type, "2026-10-23 12:00:00")));

        MessageVO vo = service.messages().getData().get(0);

        assertNotNull(vo.getContent(), "content 不能为空");
        assertTrue(vo.getContent().contains(orderNo),
                "文案必须含订单号「" + orderNo + "」，实际：" + vo.getContent());
        assertTrue(vo.getContent().contains(keyword),
                type + " 文案应含「" + keyword + "」，实际：" + vo.getContent());
        assertEquals(type, vo.getType());
    }

    /**
     * D23 · 消息隔离：只返回本店消息。
     *
     * <p>和 D09 是同一类断言，但<b>必须单独写一条</b>：消息表不像订单表那样有天然的
     * {@code shop_id} 业务语义，最容易写成「查全部消息」。
     */
    @Test
    @DisplayName("D23 只返回本店消息")
    void should_only_return_current_shop_messages() {
        when(messageMapper.selectByShop(eq(SHOP_ID), anyInt())).thenReturn(Arrays.asList(
                message("SO001", "pay_success", "2026-10-23 12:00:00"),
                message("SO002", "refund_apply", "2026-10-23 11:00:00"),
                message("SO003", "confirm_receive", "2026-10-23 10:00:00")));

        List<MessageVO> list = service.messages().getData();

        assertEquals(3, list.size(), "干扰店有 5 条消息，混进来就会变成 8 条");
        verify(messageMapper).selectByShop(eq(SHOP_ID), anyInt());
        verify(messageMapper, never()).selectByShop(eq(SHOP_ID_OTHER), anyInt());
    }

    // ==========================================================
    // 桩与构造工具
    // ==========================================================

    /** 把 6 张卡片一次性桩好 —— cards() 每次都会查满这 6 个 */
    private void stubCards(long pendingPay, long pendingShip, long pendingReceive,
                           long completed, long refunding, long stockWarn) {
        when(orderMapper.countByStatus(eq(SHOP_ID), eq("PENDING_PAY"))).thenReturn(pendingPay);
        when(orderMapper.countByStatus(eq(SHOP_ID), eq("PENDING_SHIP"))).thenReturn(pendingShip);
        when(orderMapper.countByStatus(eq(SHOP_ID), eq("PENDING_RECEIVE"))).thenReturn(pendingReceive);
        when(orderMapper.countByStatus(eq(SHOP_ID), eq("SUCCESS"))).thenReturn(completed);
        when(orderMapper.countByStatus(eq(SHOP_ID), eq("REFUNDING"))).thenReturn(refunding);
        when(productMapper.countStockWarning(eq(SHOP_ID))).thenReturn(stockWarn);
    }

    /**
     * 桩四项指标的今日/昨日取值。
     * 参数顺序：客户数(t,y)、订单数(t,y)、退款额(t,y)、支付总额(t,y)。
     */
    private void stubMetrics(long tCustomers, long yCustomers,
                             long tOrders, long yOrders,
                             double tRefund, double yRefund,
                             double tPaidSum, double yPaidSum) {
        when(orderMapper.countDistinctPaidCustomers(eq(SHOP_ID), eq(today))).thenReturn(tCustomers);
        when(orderMapper.countDistinctPaidCustomers(eq(SHOP_ID), eq(yesterday))).thenReturn(yCustomers);
        when(orderMapper.countPaidOrders(eq(SHOP_ID), eq(today))).thenReturn(tOrders);
        when(orderMapper.countPaidOrders(eq(SHOP_ID), eq(yesterday))).thenReturn(yOrders);
        when(orderMapper.sumRefundAmount(eq(SHOP_ID), eq(today))).thenReturn(tRefund);
        when(orderMapper.sumRefundAmount(eq(SHOP_ID), eq(yesterday))).thenReturn(yRefund);
        when(orderMapper.sumPaidAmount(eq(SHOP_ID), eq(today))).thenReturn(tPaidSum);
        when(orderMapper.sumPaidAmount(eq(SHOP_ID), eq(yesterday))).thenReturn(yPaidSum);
    }

    private HourAmount hour(int h, double amount) {
        HourAmount row = new HourAmount();
        row.setHour(Integer.valueOf(h));
        row.setAmount(Double.valueOf(amount));
        return row;
    }

    /** 金额为 null 的行 —— 脏数据靶子 */
    private HourAmount hourWithoutAmount(int h) {
        HourAmount row = new HourAmount();
        row.setHour(Integer.valueOf(h));
        row.setAmount(null);
        return row;
    }

    private Message message(String orderNo, String type, String createTime) {
        Message m = new Message();
        m.setShopId(SHOP_ID);
        m.setType(type);
        m.setOrderNo(orderNo);
        m.setContent(contentOf(orderNo, type));
        m.setIsRead(Integer.valueOf(0));
        m.setCreateTime(createTime);
        return m;
    }

    /** 需求原文的三套文案模板，占位符填上单号 */
    private String contentOf(String orderNo, String type) {
        if ("pay_success".equals(type)) {
            return "用户 张三 已支付成功，订单号 " + orderNo + "，请及时处理";
        }
        if ("refund_apply".equals(type)) {
            return "有买家发起退款，订单编号 " + orderNo + "，请在 7 天内处理";
        }
        return "订单 " + orderNo + " 用户 王五 已经确认收货";
    }
}
