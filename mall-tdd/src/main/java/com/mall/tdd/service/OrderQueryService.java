package com.mall.tdd.service;

import com.mall.tdd.common.Result;
import com.mall.tdd.common.UserContext;
import com.mall.tdd.common.XlsxWriter;
import com.mall.tdd.dto.AdminOrderPageVO;
import com.mall.tdd.dto.AdminOrderQuery;
import com.mall.tdd.dto.AdminOrderVO;
import com.mall.tdd.dto.AdminTabCountVO;
import com.mall.tdd.dto.BatchItemResultVO;
import com.mall.tdd.dto.BatchResultVO;
import com.mall.tdd.dto.OrderItemVO;
import com.mall.tdd.entity.Order;
import com.mall.tdd.entity.OrderItem;
import com.mall.tdd.enums.OrderStatus;
import com.mall.tdd.mapper.AdminOrderMapper;
import com.mall.tdd.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台订单管理-订单查询：11 项筛选、快捷时间、7 个 Tab、行内展示、发货（O01~O23）。
 *
 * <p>权限模型：<b>只查看、只操作本店订单</b>。{@code shopId} 一律来自
 * {@link UserContext}，压成 Mapper 的独立参数，<b>不进查询对象</b> ——
 * 进了它就成请求参数，改个 {@code ?shopId=2} 就能跨店（O22 用反射守着）。
 *
 * <p>隔离的落点有四处，缺一处就能被绕过：
 * <ol>
 *   <li>列表与总数的 SQL 都带 {@code WHERE shop_id = ?}；</li>
 *   <li>Tab 计数每个方法都带 shop_id；</li>
 *   <li>详情与状态查询带 shop_id，别家订单查不到（返回 null → Service 报失败）；</li>
 *   <li>发货的 UPDATE 也带 shop_id，越权时影响 0 行。</li>
 * </ol>
 * 列表过滤挡不住「知道 id 直接构造请求」—— 所以 O22 和 O23 是两条不同的测试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    /** 后台上限 100（C 端订单列表是 50）：后台一屏要看得更多，但也不能无上限 */
    private static final int MAX_PAGE_SIZE = 100;

    private static final String QUICK_TODAY = "today";
    private static final String QUICK_YESTERDAY = "yesterday";
    private static final String QUICK_LAST_7 = "last7";
    private static final String QUICK_LAST_30 = "last30";

    private static final Set<String> QUICK_OPTIONS = new HashSet<String>(
            Arrays.asList(QUICK_TODAY, QUICK_YESTERDAY, QUICK_LAST_7, QUICK_LAST_30));

    /** 售后状态「申请中」的取值，同时也是标签 REFUND_APPLYING 的触发条件 */
    private static final String AFTER_SALE_APPLYING = "applying";

    /**
     * 导出上限（同 P22 的口径）：导出必须忽略分页，但也不能真的无限。
     *
     * <p>★ 只导当前页 10 条是这类功能最经典的 bug；真的 {@code LIMIT} 无限
     * 又会在大商家身上打爆内存。全量导出应该走异步任务，那是另一条需求。
     */
    public static final int EXPORT_MAX_ROWS = 10000;

    /** 导出列顺序即文件列序；联系电话取的是<b>脱敏值</b>，原号码不进文件 */
    private static final List<String> EXPORT_HEADERS = Collections.unmodifiableList(
            Arrays.asList("订单编号", "店铺名称", "商品件数", "实付金额", "收货人", "联系电话",
                    "收货地址", "订单状态", "支付方式", "订单类型", "订单类别", "物流类型",
                    "门店名称", "物流单号", "售后状态", "下单时间"));

    private final AdminOrderMapper adminOrderMapper;
    private final OrderMapper orderMapper;
    /** 注入而不是直接 {@code LocalDate.now()}：否则 O12 的 4 组断言会随运行日期漂移 */
    private final Clock clock;

    // ==================================================================
    // 列表
    // ==================================================================

    /**
     * 后台订单列表（O01~O14、O22）。
     *
     * @param query 11 个筛选条件 + 快捷时间，可为 null（等价于不筛）
     * @param page  页码，从 1 开始
     * @param size  页大小；null / 小于 1 取 10，超过 100 压到 100
     */
    public Result<AdminOrderPageVO> listOrders(AdminOrderQuery query, Integer page, Integer size) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.unauthorized("请先登录");
        }

        String quickError = validateQuick(query);
        if (quickError != null) {
            return Result.fail(quickError);
        }

        AdminOrderQuery q = normalize(query, LocalDate.now(clock));

        int pageSize = normalizePageSize(size);
        int offset = (normalizePage(page) - 1) * pageSize;

        List<Order> rows = adminOrderMapper.selectQueryPage(shopId, q, pageSize, offset);

        AdminOrderPageVO data = new AdminOrderPageVO();
        data.setList(toVOs(rows));
        // 总数与列表同口径：同样的 11 个条件 + 同一个 shopId，否则「共 N 条」和翻页对不上
        data.setTotal(adminOrderMapper.countQuery(shopId, q));
        data.setTabs(buildTabs(shopId));
        return Result.ok(data);
    }

    /** 订单详情（O23：别家的订单必须返回失败，不是 200 + 空对象） */
    public Result<AdminOrderVO> detail(Long orderId) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.unauthorized("请先登录");
        }
        if (orderId == null) {
            return Result.fail("订单 id 不能为空");
        }

        Order o = adminOrderMapper.selectByIdAndShop(orderId, shopId);
        if (o == null) {
            // ★ 返回失败而不是 200 + null：后者前端会渲染成空白页，
            //   商家以为订单被删了，实际只是无权查看 —— 两种完全不同的处置。
            return Result.fail("订单不存在或无权查看");
        }

        AdminOrderVO vo = toVO(o);
        List<OrderItem> items = orderMapper.selectItemsByOrderIds(
                Collections.singletonList(orderId));
        vo.setItems(toItemVOs(items));
        vo.setTotalQuantity(sumQuantity(items));
        return Result.ok(vo);
    }

    // ==================================================================
    // 导出（O24）
    // ==================================================================

    /**
     * 导出<b>当前筛选后</b>的订单列表（O24）。
     *
     * <p>★★ 两处最容易漏，而且漏了都很严重：
     * <ol>
     *   <li><b>「当前筛选后」</b> —— 导出全表是这类功能最经典的 bug。
     *       商家筛出 4 单待发货去催发货，拿到 12 单，然后照着表打电话给已经收货的客户。</li>
     *   <li><b>手机号必须脱敏</b> —— 列表页遮了、导出文件却是全员原号码，
     *       这个文件一旦发到群里就是批量泄露，而且是<b>一次性把整店客户名单交出去</b>。
     *       所以这里复用的就是列表的 {@link #toVOs}，脱敏在 {@code toVO} 里，
     *       导出<b>没有另写一套字段映射</b>。</li>
     * </ol>
     *
     * <p>★ 分页同样换成 {@code (EXPORT_MAX_ROWS, 0)}：导的是筛选结果全集。
     */
    public Result<byte[]> exportOrders(AdminOrderQuery query) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.unauthorized("请先登录");
        }
        String quickError = validateQuick(query);
        if (quickError != null) {
            return Result.fail(quickError);
        }

        AdminOrderQuery q = normalize(query, LocalDate.now(clock));
        List<Order> rows = adminOrderMapper.selectQueryPage(shopId, q, EXPORT_MAX_ROWS, 0);

        List<List<Object>> data = new ArrayList<List<Object>>(rows.size());
        for (AdminOrderVO vo : toVOs(rows)) {
            List<Object> line = new ArrayList<Object>(EXPORT_HEADERS.size());
            line.add(vo.getOrderNo());
            line.add(vo.getShopName());
            line.add(Integer.valueOf(vo.getTotalQuantity()));
            line.add(vo.getPayAmount());
            line.add(vo.getReceiverName());
            // ★ 只有脱敏值这一个来源；实体上的原号码出不了 Service
            line.add(vo.getReceiverPhoneMasked());
            line.add(vo.getReceiverAddress());
            line.add(vo.getStatus());
            line.add(vo.getPayType());
            line.add(vo.getOrderType());
            line.add(vo.getOrderCategory());
            line.add(vo.getLogisticsType());
            line.add(vo.getStoreName());
            line.add(vo.getTrackingNo());
            line.add(vo.getAfterSaleStatus());
            line.add(vo.getCreateTime());
            data.add(line);
        }
        return Result.ok(XlsxWriter.write("订单列表", EXPORT_HEADERS, data));
    }

    // ==================================================================
    // 发货
    // ==================================================================

    /**
     * 单条发货（O18~O19）。
     *
     * <p>★ 先查状态再发货，不是「直接 UPDATE 再看影响行数」：
     * 需求要的是「待发货才展示按钮」，用户看到失败原因才有意义，
     * 只回一句「影响 0 行」等于什么都没说。真正的并发兜底仍由 UPDATE 的
     * {@code AND status = 'PENDING_SHIP'} 负责。
     */
    public Result<Void> ship(Long orderId, String trackingNo) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.unauthorized("请先登录");
        }
        if (orderId == null) {
            return Result.fail("订单 id 不能为空");
        }
        String no = trimToNull(trackingNo);
        if (no == null) {
            // 单号是发货弹窗的必填项。空单号发出去，订单变成待收货却没有运单号，
            // 之后想补都找不到入口。
            return Result.fail("请填写物流单号");
        }

        String status = adminOrderMapper.selectStatusByIdAndShop(orderId, shopId);
        if (status == null) {
            // 别家的订单在这里就被 shop_id 挡掉了
            return Result.fail("订单不存在或无权操作");
        }
        if (!OrderStatus.PENDING_SHIP.name().equals(status)) {
            return Result.fail("只有待发货订单可以发货");
        }

        if (adminOrderMapper.shipOrder(orderId, shopId, no) == 0) {
            // 查到状态到这里之间被别人发走了：如实失败，不假装成功
            return Result.fail("发货失败，订单状态已变更");
        }
        return Result.ok(null);
    }

    /**
     * 批量发货（O20~O21）。
     *
     * <p>★★ <b>不加 {@code @Transactional}</b>，逐条独立，一条失败不回滚其余。
     * 整体回滚意味着「3 个里 1 个状态不对，另外 2 个也不发了」——
     * 商家点一次什么都没发生，还不知道是哪条的问题（同 P16）。
     *
     * @param trackingNo 同批共用的物流单号（同一次揽收单号本来就相同）
     */
    public Result<BatchResultVO> batchShip(List<Long> orderIds, String trackingNo) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.unauthorized("请先登录");
        }
        if (orderIds == null || orderIds.isEmpty()) {
            return Result.fail("请先选择要发货的订单");
        }
        String no = trimToNull(trackingNo);
        // ★★ 和单条 {@link #ship} 同一条规则，不能只守一个入口。
        //   放行的后果不可逆：订单会被推进 PENDING_RECEIVE 且 tracking_no 为 NULL，
        //   而 shipOrder 的 WHERE 带 status = 'PENDING_SHIP' —— 状态已经不是待发货，
        //   商家连补单号的入口都没有了。批量是一次调用共用一个单号，没单号即整批非法。
        if (no == null) {
            return Result.fail("请填写物流单号");
        }

        List<BatchItemResultVO> results = new ArrayList<BatchItemResultVO>(orderIds.size());
        int success = 0;
        for (Long id : orderIds) {
            BatchItemResultVO item = shipOne(id, shopId, no);
            results.add(item);
            if (item.isSuccess()) {
                success++;
            }
        }

        BatchResultVO data = new BatchResultVO();
        data.setTotal(Integer.valueOf(orderIds.size()));
        data.setSuccess(Integer.valueOf(success));
        data.setFailed(Integer.valueOf(orderIds.size() - success));
        // ★ results 的顺序必须与入参 ids 一致，前端才能按顺序对着列表标红。
        //   这里用 List 顺次追加，不要换成 HashMap 再遍历。
        data.setResults(results);
        // 部分失败也返回 200：前端靠 failed + results 告诉商家哪几条没发。
        // 返回 500 只会让商家整批重试，把已成功的又发一遍。
        return Result.ok(data);
    }

    /** 发一条。异常一律吃掉转成失败项，批量接口不许把异常抛出去 */
    private BatchItemResultVO shipOne(Long orderId, Long shopId, String trackingNo) {
        BatchItemResultVO item = new BatchItemResultVO();
        item.setId(orderId);
        try {
            if (orderId == null) {
                item.setSuccess(false);
                item.setMessage("订单 id 不能为空");
                return item;
            }
            String status = adminOrderMapper.selectStatusByIdAndShop(orderId, shopId);
            if (status == null) {
                item.setSuccess(false);
                item.setMessage("订单不存在或无权操作");
                return item;
            }
            if (!OrderStatus.PENDING_SHIP.name().equals(status)) {
                item.setSuccess(false);
                item.setMessage("只有待发货订单可以发货");
                return item;
            }
            if (adminOrderMapper.shipOrder(orderId, shopId, trackingNo) == 0) {
                item.setSuccess(false);
                item.setMessage("发货失败，订单状态已变更");
                return item;
            }
            item.setSuccess(true);
        } catch (RuntimeException e) {
            item.setSuccess(false);
            String msg = e.getMessage();
            item.setMessage(msg == null || msg.isEmpty() ? "发货失败" : msg);
            // ★ 同 P17b：批量的 catch 是「设计好的吞异常」，吞了就必须留现场，
            //   否则这条失败只活在前端那一次渲染里。
            log.warn("批量发货失败 shopId={} orderId={}：{}", shopId, orderId, msg, e);
        }
        return item;
    }

    // ==================================================================
    // Tab 计数
    // ==================================================================

    /**
     * 7 个 Tab 的计数。
     *
     * <p>★★ {@code all} 用<b>独立统计</b>，不是 6 个 Tab 相加。7 种状态里
     * {@link OrderStatus#REFUNDING} 不属于任何一个 Tab，相加必然漏掉退款中的订单 ——
     * 而那正是商家最需要点进去处理的。B3 买家侧 4 个 Tab 是同一个道理
     * （真库实测 shop1 共 38 单、4 个 Tab 之和只有 36）。
     */
    private AdminTabCountVO buildTabs(Long shopId) {
        AdminTabCountVO tabs = new AdminTabCountVO();
        tabs.setAll(adminOrderMapper.countAll(shopId));
        tabs.setPendingPay(adminOrderMapper.countTab(shopId, OrderStatus.PENDING_PAY.name()));
        tabs.setPendingShip(adminOrderMapper.countTab(shopId, OrderStatus.PENDING_SHIP.name()));
        tabs.setPendingReceive(adminOrderMapper.countTab(shopId, OrderStatus.PENDING_RECEIVE.name()));
        tabs.setSuccess(adminOrderMapper.countTab(shopId, OrderStatus.SUCCESS.name()));
        tabs.setFailed(adminOrderMapper.countTab(shopId, OrderStatus.FAILED.name()));
        tabs.setPendingGroup(adminOrderMapper.countTab(shopId, OrderStatus.PENDING_GROUP.name()));
        return tabs;
    }

    // ==================================================================
    // 条件规范化
    // ==================================================================

    /**
     * 把入参规范化成新对象（不改调用方的对象）。
     *
     * <p>★ 空串一律当「不筛」：否则 SQL 变成 {@code order_no = ''}，
     * 用户会觉得「怎么搜都搜不到」。
     */
    /**
     * 校验快捷时间取值，合法返回 {@code null}，非法返回错误文案。
     *
     * <p>★ 列表和导出共用同一个校验：导出是绕开页面最省事的后门，
     * 各自写一遍校验，总有一天会忘掉一处。
     *
     * <p>★ 不静默当成「不筛」：那样用户以为筛了近 90 天，实际看到的是全表，
     * 和 P04b（非法状态被忽略）是同一类错误。
     */
    private String validateQuick(AdminOrderQuery query) {
        String quick = trimToNull(query == null ? null : query.getQuickDate());
        if (quick != null && !QUICK_OPTIONS.contains(quick)) {
            return "不支持的快捷时间：" + quick;
        }
        return null;
    }

    private AdminOrderQuery normalize(AdminOrderQuery raw, LocalDate today) {
        AdminOrderQuery q = new AdminOrderQuery();
        if (raw == null) {
            return q;
        }

        q.setOrderNo(trimToNull(raw.getOrderNo()));
        q.setOrderType(trimToNull(raw.getOrderType()));
        q.setOrderCategory(trimToNull(raw.getOrderCategory()));
        q.setStatus(trimToNull(raw.getStatus()));
        q.setReceiverName(trimToNull(raw.getReceiverName()));
        q.setReceiverPhone(trimToNull(raw.getReceiverPhone()));
        q.setPayType(trimToNull(raw.getPayType()));
        q.setLogisticsType(trimToNull(raw.getLogisticsType()));
        q.setStoreName(trimToNull(raw.getStoreName()));

        // ★ 「暂无售后」在库里存的是 NULL。若把 NONE 当字符串透传，
        //   SQL 变成 after_sale_status = 'NONE'，一条都查不到 ——
        //   而页面上「暂无售后」永远是 0 条，看起来像「真的没有」，最难发现。
        String afterSale = trimToNull(raw.getAfterSaleStatus());
        if (AdminOrderQuery.AFTER_SALE_NONE.equalsIgnoreCase(afterSale)) {
            q.setAfterSaleNone(true);
        } else {
            q.setAfterSaleStatus(afterSale);
        }

        q.setStartDate(trimToNull(raw.getStartDate()));
        q.setEndDate(trimToNull(raw.getEndDate()));

        applyQuickDate(q, trimToNull(raw.getQuickDate()), today);
        return q;
    }

    /**
     * 快捷时间：选了就<b>覆盖</b>手填的日期区间。
     *
     * <p>★ 「近 7 天」的口径钉死为「今天-6 ~ 今天」（共 7 天，含今天）。
     * 含不含今天差一天，不同人算出来不一样，所以写死在代码里、也钉死在测试里。
     */
    private void applyQuickDate(AdminOrderQuery q, String quick, LocalDate today) {
        if (quick == null) {
            return;
        }
        LocalDate start;
        LocalDate end;
        if (QUICK_TODAY.equals(quick)) {
            start = today;
            end = today;
        } else if (QUICK_YESTERDAY.equals(quick)) {
            start = today.minusDays(1);
            end = start;
        } else if (QUICK_LAST_7.equals(quick)) {
            start = today.minusDays(6);
            end = today;
        } else {
            // 合法值只剩 last30：调用前已校验过
            start = today.minusDays(29);
            end = today;
        }
        q.setStartDate(start.toString());
        q.setEndDate(end.toString());
    }

    // ==================================================================
    // 装配
    // ==================================================================

    private List<AdminOrderVO> toVOs(List<Order> rows) {
        List<AdminOrderVO> list = new ArrayList<AdminOrderVO>();
        if (rows == null || rows.isEmpty()) {
            return list;
        }

        List<Long> ids = new ArrayList<Long>(rows.size());
        for (Order o : rows) {
            ids.add(o.getId());
        }
        // 一次 IN 查询拿全部明细，避免每条订单查一次（N+1）
        Map<Long, List<OrderItem>> byOrder = groupItems(
                orderMapper.selectItemsByOrderIds(ids));

        for (Order o : rows) {
            AdminOrderVO vo = toVO(o);
            List<OrderItem> items = byOrder.get(o.getId());
            vo.setItems(toItemVOs(items));
            // ★ 「共 N 件」= quantity 之和，不是明细行数（O15）
            vo.setTotalQuantity(sumQuantity(items));
            list.add(vo);
        }
        return list;
    }

    private Map<Long, List<OrderItem>> groupItems(List<OrderItem> items) {
        Map<Long, List<OrderItem>> byOrder = new HashMap<Long, List<OrderItem>>();
        // 不做 items == null 判断：MyBatis 的 List 返回值不会是 null，查不到就是空列表
        for (OrderItem it : items) {
            List<OrderItem> list = byOrder.get(it.getOrderId());
            if (list == null) {
                list = new ArrayList<OrderItem>();
                byOrder.put(it.getOrderId(), list);
            }
            list.add(it);
        }
        return byOrder;
    }

    private AdminOrderVO toVO(Order o) {
        AdminOrderVO vo = new AdminOrderVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setShopName(o.getShopName());
        vo.setPayAmount(o.getPayAmount());
        vo.setTotalAmount(o.getTotalAmount());
        vo.setReceiverName(o.getReceiverName());
        // ★ 只给脱敏值，原号码不出 Service（O16）
        vo.setReceiverPhoneMasked(maskPhone(o.getReceiverPhone()));
        vo.setReceiverAddress(o.getReceiverAddress());
        vo.setStatus(o.getStatus());
        vo.setPayType(o.getPayType());
        vo.setOrderType(o.getOrderType());
        vo.setOrderCategory(o.getOrderCategory());
        vo.setLogisticsType(o.getLogisticsType());
        vo.setStoreName(o.getStoreName());
        vo.setTrackingNo(o.getTrackingNo());
        vo.setAfterSaleStatus(o.getAfterSaleStatus());
        vo.setCreateTime(o.getCreateTime());
        vo.setPayTime(o.getPayTime());
        vo.setShipTime(o.getShipTime());
        vo.setTags(buildTags(o));
        return vo;
    }

    private List<OrderItemVO> toItemVOs(List<OrderItem> items) {
        List<OrderItemVO> list = new ArrayList<OrderItemVO>();
        if (items == null) {
            return list;
        }
        for (OrderItem it : items) {
            OrderItemVO vo = new OrderItemVO();
            vo.setProductId(it.getProductId());
            vo.setProductName(it.getProductName());
            vo.setProductImage(it.getProductImage());
            vo.setPrice(it.getPrice());
            vo.setQuantity(it.getQuantity());
            list.add(vo);
        }
        return list;
    }

    /**
     * 手机号脱敏：{@code 13800138000 → 138****8000}。
     *
     * <p>★ 脱敏必须发生在后端。前端遮只是视觉效果，F12 一看响应体原文就在。
     *
     * <p>★ 只处理 11 位手机号。固话 / 短号 / 空值原样返回，不抛异常 ——
     * 硬按 3-4 位切会把 {@code 0571-8888} 切成 {@code 057****888}，比不脱敏还糟。
     */
    private String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String p = phone.trim();
        if (p.length() != 11) {
            return p;
        }
        return p.substring(0, 3) + "****" + p.substring(7);
    }

    /**
     * 标签：5 种标记混了 4 个维度（状态 / 售后 / 类别 / 物流），可能是多个。
     *
     * <p>★ 返回枚举码而不是中文文案：改文案不该改后端。
     *
     * <p>★ 标签反映「当前状态」，不是「曾经经历过的状态」。已完成（SUCCESS）的订单
     * 不该再挂「待发货」，否则商家会重复发货。
     */
    private List<String> buildTags(Order o) {
        List<String> tags = new ArrayList<String>();
        String status = o.getStatus();
        if (OrderStatus.PENDING_SHIP.name().equals(status)) {
            tags.add("PENDING_SHIP");
        }
        if (OrderStatus.PENDING_RECEIVE.name().equals(status)) {
            tags.add("PENDING_RECEIVE");
        }
        if (AFTER_SALE_APPLYING.equalsIgnoreCase(o.getAfterSaleStatus())) {
            tags.add("REFUND_APPLYING");
        }
        if ("e_card".equals(o.getOrderCategory())) {
            tags.add("E_CARD");
        }
        if ("city".equals(o.getLogisticsType())) {
            tags.add("LOCAL_DELIVERY");
        }
        return tags;
    }

    private int sumQuantity(List<OrderItem> items) {
        if (items == null) {
            return 0;
        }
        int sum = 0;
        for (OrderItem it : items) {
            Integer q = it.getQuantity();
            if (q != null) {
                sum += q.intValue();
            }
        }
        return sum;
    }

    private int normalizePage(Integer page) {
        return page == null || page.intValue() < 1 ? 1 : page.intValue();
    }

    private int normalizePageSize(Integer size) {
        if (size == null || size.intValue() < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size.intValue(), MAX_PAGE_SIZE);
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
