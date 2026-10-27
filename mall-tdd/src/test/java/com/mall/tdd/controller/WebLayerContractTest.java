package com.mall.tdd.controller;

import com.mall.tdd.common.Result;
import com.mall.tdd.common.UserContext;
import com.mall.tdd.dto.AdminOrderPageVO;
import com.mall.tdd.dto.AdminOrderQuery;
import com.mall.tdd.dto.BatchResultVO;
import com.mall.tdd.dto.BatchShipCommand;
import com.mall.tdd.dto.BuyNowVO;
import com.mall.tdd.dto.ImportResultVO;
import com.mall.tdd.dto.MallProductVO;
import com.mall.tdd.dto.OrderListVO;
import com.mall.tdd.dto.ProductBatchCommand;
import com.mall.tdd.dto.ProductPageVO;
import com.mall.tdd.dto.ProductQuery;
import com.mall.tdd.entity.Category;
import com.mall.tdd.service.DashboardService;
import com.mall.tdd.service.MallCategoryService;
import com.mall.tdd.service.MallOrderService;
import com.mall.tdd.service.MallProductService;
import com.mall.tdd.service.OrderQueryService;
import com.mall.tdd.service.ProductQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W 系列 · Web 层契约验收（19 个接口）。
 *
 * <p>与 88 条需求验收分开记：88 条验的是「Service 逻辑对不对」，
 * 这批验的是<b>外面能不能进来</b>——路径、参数绑定、状态码、响应头、身份来源。
 * 两层测的东西没有交集，所以 155 条全绿对这里一个字都不能证明。
 *
 * <p>★ 用 {@code @WebMvcTest} 而不是 {@code @SpringBootTest}：只起 Controller 那一圈 Bean，
 * 不连库、不起 MyBatis，跑起来和纯单测一样快。
 */
@WebMvcTest(controllers = {
        MallProductController.class,
        DashboardController.class,
        MallOrderController.class,
        ShopProductController.class,
        ShopOrderController.class
})
class WebLayerContractTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final String SHOP_HEADER = "X-Shop-Id";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MallCategoryService categoryService;
    @MockBean
    private MallProductService productService;
    @MockBean
    private DashboardService dashboardService;
    @MockBean
    private MallOrderService mallOrderService;
    @MockBean
    private ProductQueryService productQueryService;
    @MockBean
    private OrderQueryService orderQueryService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ==========================================================
    // W01 · 19 个路径全部命中
    // ==========================================================

    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of("GET", "/api/mall/category/list"),
                Arguments.of("GET", "/api/mall/product/list"),
                Arguments.of("GET", "/api/mall/product/5"),
                Arguments.of("GET", "/api/dashboard/cards"),
                Arguments.of("GET", "/api/dashboard/metrics"),
                Arguments.of("GET", "/api/dashboard/trend"),
                Arguments.of("GET", "/api/dashboard/messages"),
                Arguments.of("GET", "/api/mall/order/list"),
                Arguments.of("DELETE", "/api/mall/order/1"),
                Arguments.of("POST", "/api/mall/order/buy-now?productId=5"),
                Arguments.of("GET", "/api/shop/product/list"),
                Arguments.of("DELETE", "/api/shop/product/1"),
                Arguments.of("POST", "/api/shop/product/batch"),
                Arguments.of("GET", "/api/shop/product/export"),
                Arguments.of("POST", "/api/shop/product/import"),
                Arguments.of("GET", "/api/shop/order/list"),
                Arguments.of("POST", "/api/shop/order/ship?orderId=1&trackingNo=SF001"),
                Arguments.of("POST", "/api/shop/order/batch-ship"),
                Arguments.of("GET", "/api/shop/order/export")
        );
    }

    /**
     * W01 ★ 19 个接口每个都必须能被路由到。
     *
     * <p>★★ 只断言「不是 404」这一件事。这是本批性价比最高的测试：
     * 路径拼错（{@code /products/list} 多了个 s）、{@code @RequestMapping} 少写一段、
     * 方法签名改成 {@code @PostMapping}——编译器全都不管，只有真发一个请求才知道。
     * 没有这条，剩下 18 条测试可以全部通过，而前端一个接口都调不到。
     */
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("endpoints")
    @DisplayName("W01 每个契约路径都必须命中（不能 404/405）")
    void should_route_every_contract_endpoint(String method, String path) throws Exception {
        stubAllOk();
        int code = call(method, path).getResponse().getStatus();
        assertFalse(code == 404, method + " " + path + " 未命中，返回 404 —— 路径拼错或少了映射");
        assertFalse(code == 405, method + " " + path + " 方法不被支持，返回 405 —— @GetMapping/@PostMapping 写错");
    }

    // ==========================================================
    // W02 · 401 不能被打包成成功
    // ==========================================================

    /**
     * W02 ★ Service 返回未登录时，Controller 必须原样透传 401。
     *
     * <p>陷阱：Controller 里顺手写 {@code Result.ok(service.list(...))} 或
     * {@code service.list(...).getData()} 会把 401 变成「成功但数据为空」，
     * 前端不会跳登录页，用户看到的是"你没有订单"而不是"你登录过期了"。
     */
    @Test
    @DisplayName("W02 Service 返回未登录时，响应体 code 必须是 401")
    void should_pass_through_unauthorized() throws Exception {
        when(mallOrderService.listOrders(any(), any(), any(), any()))
                .thenReturn(Result.<OrderListVO>unauthorized("请先登录"));

        String body = call("GET", "/api/mall/order/list").getResponse().getContentAsString();

        assertTrue(body.contains("\"code\":401"), "未登录必须原样透传 401，实际：" + body);
    }

    // ==========================================================
    // W03 · 参数绑定
    // ==========================================================

    /**
     * W03 ★ 路径变量要真的进到 Service 参数里。
     *
     * <p>陷阱：{@code @PathVariable} 少了 {@code value}、或者参数名和占位符不一致
     * （{@code /{id}} 配 {@code @PathVariable("productId")}），编译期一切正常，
     * 运行时直接 500 或拿到 null。
     */
    @Test
    @DisplayName("W03 路径变量与查询参数必须正确绑定")
    void should_bind_path_and_query_params() throws Exception {
        when(productService.detail(anyLong())).thenReturn(Result.<MallProductVO>ok(new MallProductVO()));
        when(mallOrderService.listOrders(any(), any(), any(), any()))
                .thenReturn(Result.<OrderListVO>ok(new OrderListVO()));

        call("GET", "/api/mall/product/99");

        ArgumentCaptor<Long> idCap = ArgumentCaptor.forClass(Long.class);
        verify(productService).detail(idCap.capture());
        assertEquals(99L, idCap.getValue());

        call("GET", "/api/mall/order/list?status=PENDING_SHIP&orderNo=SO001&page=2&size=20");

        ArgumentCaptor<String> statusCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> noCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> pageCap = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sizeCap = ArgumentCaptor.forClass(Integer.class);
        verify(mallOrderService).listOrders(statusCap.capture(), noCap.capture(),
                pageCap.capture(), sizeCap.capture());
        assertEquals("PENDING_SHIP", statusCap.getValue());
        assertEquals("SO001", noCap.getValue());
        assertEquals(2, pageCap.getValue());
        assertEquals(20, sizeCap.getValue());
    }

    /**
     * W03b 没传分页参数时，必须把 null 透传给 Service。
     *
     * <p>不能在这里 {@code page == null ? 1 : page} 兜底：分页默认值和上限
     * （C 端 50 / B 端 100）已经由 Service 统一归一化，两处各写一遍迟早不一致，
     * 出现「Controller 放行 size=999，Service 才截断」这种双重标准。
     */
    @Test
    @DisplayName("W03b 缺省分页参数传 null，由 Service 归一化")
    void should_pass_null_paging_to_service() throws Exception {
        when(mallOrderService.listOrders(any(), any(), any(), any()))
                .thenReturn(Result.<OrderListVO>ok(new OrderListVO()));

        call("GET", "/api/mall/order/list");

        verify(mallOrderService).listOrders(isNull(), isNull(), isNull(), isNull());
    }

    /**
     * W04 ★★ 身份只能来自 UserContext，Controller 签名里不许出现 userId / shopId 参数。
     *
     * <p>这条用反射守着，理由和 U05 的"OrderMapper 不许有 delete 方法"一样：
     * 只断言"这次没传"防不住以后有人为了省事加个 {@code ?shopId=} 参数——
     * 那是最典型的水平越权，改个 URL 就能看别家店的数据。
     */
    @Test
    @DisplayName("W04 Controller 参数里不许出现 userId / shopId")
    void should_never_accept_identity_from_request() {
        List<Class<?>> controllers = Arrays.<Class<?>>asList(
                MallProductController.class, DashboardController.class, MallOrderController.class,
                ShopProductController.class, ShopOrderController.class);

        List<String> offenders = new ArrayList<String>();
        for (Class<?> c : controllers) {
            for (Method m : c.getDeclaredMethods()) {
                for (Parameter p : m.getParameters()) {
                    String n = p.getName().toLowerCase();
                    if (n.contains("userid") || n.contains("shopid")) {
                        offenders.add(c.getSimpleName() + "#" + m.getName() + "(" + p.getName() + ")");
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "身份参数必须从 UserContext 取，不许从请求传：\n" + offenders);
    }

    // ==========================================================
    // W05 / W06 · 导出
    // ==========================================================

    /**
     * W05 ★ 导出必须带下载响应头，且字节原样写出。
     *
     * <p>陷阱：直接把 {@code Result<byte[]>} 返回给 Spring，会被当成普通对象序列化成
     * {@code {"code":200,"data":"UEsDBB..."}} —— base64 一大坨 JSON，
     * 浏览器既不弹下载，用 Excel 也打不开。
     */
    @Test
    @DisplayName("W05 导出带 Content-Disposition 且字节原样写出")
    void should_export_with_download_headers() throws Exception {
        byte[] bytes = new byte[]{'P', 'K', 3, 4, 9, 9};
        when(productQueryService.exportProducts(any(ProductQuery.class)))
                .thenReturn(Result.ok(bytes));

        MvcResult r = call("GET", "/api/shop/product/export");

        String disposition = r.getResponse().getHeader("Content-Disposition");
        assertTrue(disposition != null && disposition.contains("attachment"),
                "缺 Content-Disposition: attachment，浏览器不会下载，实际：" + disposition);
        assertTrue(disposition.contains(".xlsx"), "文件名要以 .xlsx 结尾，实际：" + disposition);
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                r.getResponse().getContentType(), "Content-Type 必须是 xlsx，否则 Excel 不认");
        assertTrue(Arrays.equals(bytes, r.getResponse().getContentAsByteArray()),
                "写出的字节必须与 Service 返回的完全一致");
    }

    /**
     * W06 ★★ 导出失败时必须返回 JSON 错误，不能返回一个 0 字节的 .xlsx。
     *
     * <p>这是导出类接口最容易出的线上问题：Service 返回 {@code Result.fail("无数据")}，
     * {@code data} 是 null，Controller 直接 {@code new ResponseEntity<>(data, ...)} 写出 null，
     * 用户下载到一个 0KB 文件，Excel 打开报"文件已损坏"——
     * 而日志里一条错误都没有，因为根本没抛异常。
     */
    @Test
    @DisplayName("W06 导出失败时返回 JSON 错误，不是空文件")
    void should_not_write_empty_file_when_export_fails() throws Exception {
        when(productQueryService.exportProducts(any(ProductQuery.class)))
                .thenReturn(Result.<byte[]>fail("没有符合条件的商品"));

        MvcResult r = call("GET", "/api/shop/product/export");

        assertNull(r.getResponse().getHeader("Content-Disposition"),
                "失败时不能带下载头，否则浏览器会存下一个错误页");
        String body = r.getResponse().getContentAsString();
        assertTrue(body.contains("\"code\":500"), "失败要返回 Result 形状，实际：" + body);
        assertTrue(body.contains("没有符合条件的商品"), "错误原因要带给前端，实际：" + body);
        assertEquals(0, r.getResponse().getContentAsByteArray().length, "失败时不该写文件内容");
    }

    @Test
    @DisplayName("W06b 订单导出同样带下载头")
    void should_export_orders_with_download_headers() throws Exception {
        when(orderQueryService.exportOrders(any(AdminOrderQuery.class)))
                .thenReturn(Result.ok(new byte[]{'P', 'K'}));

        MvcResult r = call("GET", "/api/shop/order/export");

        String disposition = r.getResponse().getHeader("Content-Disposition");
        assertTrue(disposition != null && disposition.contains(".xlsx"), "实际：" + disposition);
    }

    // ==========================================================
    // W07 · 导入
    // ==========================================================

    /**
     * W07 ★ multipart 文件的字段名必须与前端一致，且流要真的到达 Service。
     *
     * <p>陷阱：注解写成 {@code @RequestParam("excel")} 而前端传的是 {@code file}，
     * 请求直接 400，Service 一次都不会被调用。
     */
    @Test
    @DisplayName("W07 导入的 multipart 文件能到达 Service")
    void should_pass_uploaded_file_to_service() throws Exception {
        when(productQueryService.importProducts(any()))
                .thenReturn(Result.<ImportResultVO>ok(new ImportResultVO()));

        MockMultipartFile file = new MockMultipartFile("file", "products.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{'P', 'K', 1, 2});

        mvc.perform(multipart("/api/shop/product/import").file(file)
                        .header(SHOP_HEADER, "1"))
                .andReturn();

        verify(productQueryService).importProducts(any());
    }

    /**
     * W07b 没传文件时必须给出明确提示，不能 500。
     *
     * <p>真实场景：商家点了"导入"但没选文件，或者浏览器把空表单提交上来。
     * 直接 NPE 会让用户看到"系统错误"，而实际是自己的操作问题。
     */
    @Test
    @DisplayName("W07b 没传文件时返回可读提示，不是 500 堆栈")
    void should_report_missing_file_clearly() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "", null, new byte[0]);

        String body = mvc.perform(multipart("/api/shop/product/import").file(empty)
                        .header(SHOP_HEADER, "1"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"code\":500") || body.contains("\"code\":400"),
                "没传文件要给业务提示，实际：" + body);
        assertTrue(body.contains("文件") || body.contains("file"), "提示要说明是文件问题，实际：" + body);
        verify(productQueryService, never()).importProducts(any());
    }

    // ==========================================================
    // W08 / W09 · 身份注入与清理
    // ==========================================================

    /**
     * W08 ★ 请求头里的身份要写进 UserContext，否则 Service 全部返回 401。
     *
     * <p>这是本批的根节点：Service 里 20 多处 {@code UserContext.getUserId()}，
     * 一个填不进去，整个模块对外就是「永远是未登录」。
     */
    @Test
    @DisplayName("W08 请求头身份写入 UserContext")
    void should_populate_user_context_from_headers() throws Exception {
        AtomicReference<Long> seenUser = new AtomicReference<Long>();
        AtomicReference<Long> seenShop = new AtomicReference<Long>();
        when(dashboardService.cards()).thenAnswer(inv -> {
            seenUser.set(UserContext.getUserId());
            seenShop.set(UserContext.getShopId());
            return Result.ok(Collections.singletonMap("pendingShip", 9L));
        });

        mvc.perform(get("/api/dashboard/cards")
                        .header(USER_HEADER, "1001").header(SHOP_HEADER, "1"))
                .andReturn();

        assertEquals(1001L, seenUser.get(), "userId 没进 UserContext");
        assertEquals(1L, seenShop.get(), "shopId 没进 UserContext");
    }

    /**
     * W09 ★★ 请求结束后必须清空 UserContext。
     *
     * <p>Tomcat 线程是复用的。漏了 {@code clear()}，上一个请求的身份会留在线程里，
     * 下一个匿名请求读到的是别人的身份——<b>看起来功能全正常，实际是全站串号</b>。
     * 这种问题只在并发压测或特定线程调度下才显现，手工点页面 100% 发现不了。
     *
     * <p>MockMvc 在同一个线程执行，所以两次请求能复现这个泄漏。
     */
    @Test
    @DisplayName("W09 请求结束后 UserContext 必须清空，不能串到下一个请求")
    void should_clear_user_context_after_request() throws Exception {
        AtomicReference<Long> seen = new AtomicReference<Long>();
        when(dashboardService.cards()).thenAnswer(inv -> {
            seen.set(UserContext.getShopId());
            return Result.ok(Collections.singletonMap("pendingShip", 0L));
        });

        mvc.perform(get("/api/dashboard/cards").header(SHOP_HEADER, "1")).andReturn();
        assertEquals(1L, seen.get(), "第一个请求应当拿到店 1 的身份");

        // 第二个请求不带任何身份头，模拟未登录
        mvc.perform(get("/api/dashboard/cards")).andReturn();
        assertNull(seen.get(),
                "上一个请求的 shopId 泄漏到了下一个请求 —— Tomcat 线程复用会串号");
    }

    // ==========================================================
    // W10 · 全局异常
    // ==========================================================

    /**
     * W10 ★★ Service 抛异常时，响应仍然是 Result 形状。
     *
     * <p>没有全局异常处理，Spring 会返回 {@code {"timestamp":...,"status":500,"path":...}}，
     * 前端按 {@code res.data.code} 判断时会拿到 {@code undefined}，
     * 于是所有失败都变成「未知错误」，具体原因全部丢失。
     *
     * <p>同时断言：不许把堆栈或 SQL 原文回给调用方——那是白盒信息，能帮攻击者构造注入。
     */
    @Test
    @DisplayName("W10 未捕获异常仍返回 Result 形状，且不泄露内部细节")
    void should_wrap_uncaught_exception_into_result() throws Exception {
        when(productService.detail(anyLong()))
                .thenThrow(new RuntimeException("SELECT * FROM tb_product 炸了"));

        String body = call("GET", "/api/mall/product/1").getResponse().getContentAsString();

        assertTrue(body.contains("\"code\":500"), "必须是 Result 形状，实际：" + body);
        assertFalse(body.contains("SELECT *"), "不许把 SQL 原文回给调用方，实际：" + body);
    }

    // ==========================================================
    // W11 · 请求体绑定
    // ==========================================================

    /**
     * W11 ★ 批量接口的 JSON 数组要绑上。
     *
     * <p>陷阱：{@code @RequestBody} 漏了 → 整个参数是 null → NPE 500；
     * 或者 DTO 字段名和前端 JSON 不一致（{@code ids} vs {@code productIds}）→ 绑成空集合，
     * 接口返回「成功 0 条」，用户以为设置生效了。
     */
    @Test
    @DisplayName("W11 批量设置与批量发货的请求体能正确绑定")
    void should_bind_batch_request_bodies() throws Exception {
        when(productQueryService.batchUpdate(any(ProductBatchCommand.class)))
                .thenReturn(Result.<BatchResultVO>ok(new BatchResultVO()));
        when(orderQueryService.batchShip(any(), any()))
                .thenReturn(Result.<BatchResultVO>ok(new BatchResultVO()));

        mvc.perform(post("/api/shop/product/batch")
                        .header(SHOP_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2,3],\"status\":\"off_sale\"}"))
                .andReturn();

        ArgumentCaptor<ProductBatchCommand> cmdCap = ArgumentCaptor.forClass(ProductBatchCommand.class);
        verify(productQueryService).batchUpdate(cmdCap.capture());
        assertEquals(Arrays.asList(1L, 2L, 3L), cmdCap.getValue().getIds(),
                "ids 没绑上，检查 DTO 字段名与前端 JSON 是否一致");

        mvc.perform(post("/api/shop/order/batch-ship")
                        .header(SHOP_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderIds\":[7,8],\"trackingNo\":\"SF100\"}"))
                .andReturn();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idsCap = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> noCap = ArgumentCaptor.forClass(String.class);
        verify(orderQueryService).batchShip(idsCap.capture(), noCap.capture());
        assertEquals(Arrays.asList(7L, 8L), idsCap.getValue());
        assertEquals("SF100", noCap.getValue());
    }

    // ==========================================================
    // W12 · 不要兜底吞掉未知路径
    // ==========================================================

    /**
     * W12 未知路径仍然 404。
     *
     * <p>防止有人为了"统一返回格式"加一个 {@code /**} 兜底映射，
     * 把所有拼错的路径都变成 200 空数据——那样 W01 就再也测不出来了。
     */
    @Test
    @DisplayName("W12 未知路径保持 404，不被兜底吞掉")
    void should_keep_unknown_path_404() throws Exception {
        stubAllOk();
        assertEquals(404, call("GET", "/api/not-exists").getResponse().getStatus());
        assertEquals(404, call("GET", "/api/shop/product/listX").getResponse().getStatus());
    }

    // ==========================================================
    // 辅助
    // ==========================================================

    private MvcResult call(String method, String path) throws Exception {
        if ("GET".equals(method)) {
            return mvc.perform(get(path).header(SHOP_HEADER, "1").header(USER_HEADER, "1001"))
                    .andReturn();
        }
        if ("DELETE".equals(method)) {
            return mvc.perform(delete(path).header(SHOP_HEADER, "1").header(USER_HEADER, "1001"))
                    .andReturn();
        }
        return mvc.perform(post(path).header(SHOP_HEADER, "1").header(USER_HEADER, "1001")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn();
    }

    /** W01/W12 只需要「能路由到」，不关心业务值，所以全部给 lenient 默认桩 */
    private void stubAllOk() {
        lenient().when(categoryService.list()).thenReturn(Collections.singletonList(new Category()));
        lenient().when(productService.list(any(), any()))
                .thenReturn(Result.ok(Collections.<MallProductVO>emptyList()));
        lenient().when(productService.detail(anyLong()))
                .thenReturn(Result.<MallProductVO>ok(new MallProductVO()));
        lenient().when(dashboardService.cards()).thenReturn(Result.ok(Collections.singletonMap("a", 1L)));
        lenient().when(dashboardService.metrics()).thenReturn(Result.ok(null));
        lenient().when(dashboardService.trend()).thenReturn(Result.ok(null));
        lenient().when(dashboardService.messages()).thenReturn(Result.ok(null));
        lenient().when(mallOrderService.listOrders(any(), any(), any(), any()))
                .thenReturn(Result.<OrderListVO>ok(new OrderListVO()));
        lenient().when(mallOrderService.deleteOrder(anyLong())).thenReturn(Result.<Void>ok(null));
        lenient().when(mallOrderService.buyNow(anyLong()))
                .thenReturn(Result.<BuyNowVO>ok(new BuyNowVO()));
        lenient().when(productQueryService.listProducts(any(ProductQuery.class), any(), any()))
                .thenReturn(Result.<ProductPageVO>ok(new ProductPageVO()));
        lenient().when(productQueryService.deleteProduct(anyLong())).thenReturn(Result.<Void>ok(null));
        lenient().when(productQueryService.batchUpdate(any(ProductBatchCommand.class)))
                .thenReturn(Result.<BatchResultVO>ok(new BatchResultVO()));
        lenient().when(productQueryService.exportProducts(any(ProductQuery.class)))
                .thenReturn(Result.ok(new byte[]{'P', 'K'}));
        lenient().when(orderQueryService.listOrders(any(AdminOrderQuery.class), any(), any()))
                .thenReturn(Result.<AdminOrderPageVO>ok(new AdminOrderPageVO()));
        lenient().when(orderQueryService.ship(anyLong(), any()))
                .thenReturn(Result.<Void>ok(null));
        lenient().when(orderQueryService.batchShip(any(), any()))
                .thenReturn(Result.<BatchResultVO>ok(new BatchResultVO()));
        lenient().when(orderQueryService.exportOrders(any(AdminOrderQuery.class)))
                .thenReturn(Result.ok(new byte[]{'P', 'K'}));
    }
}
