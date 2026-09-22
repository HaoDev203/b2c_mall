package com.shop.service;

import com.shop.common.BizException;
import com.shop.common.Result;
import com.shop.common.UserContext;
import com.shop.dto.CartItemVO;
import com.shop.dto.OrderSubmitDTO;
import com.shop.dto.PayDTO;
import com.shop.dto.PayRequest;
import com.shop.dto.PayResult;
import com.shop.entity.Cart;
import com.shop.entity.Order;
import com.shop.entity.OrderItem;
import com.shop.entity.Product;
import com.shop.entity.Promotion;
import com.shop.mapper.CartMapper;
import com.shop.mapper.OrderItemMapper;
import com.shop.mapper.OrderMapper;
import com.shop.mapper.OrderStatusLogMapper;
import com.shop.mapper.ProductMapper;
import com.shop.mapper.PromotionMapper;
import com.shop.pay.PayStrategyFactory;
import com.shop.state.OrderContext;
import com.shop.state.OrderStateFactory;
import com.shop.statemachine.OrderStateMachine;
import com.shop.statemachine.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ★★ 前台下单 + 支付服务 —— 阶段 6 两个核心考点在这里汇合。
 *
 * 分工：
 *   submit()  下单：购物车 → 订单（状态 PENDING_PAY）+ 扣库存 + 清购物车
 *   pay()     支付：★★ 两个模式同时出场
 *                ① 策略模式：选支付方式 → PayStrategyFactory.pay(request, payType)
 *                ② 状态模式：支付成功后 → ctx.getCurrentState().paySuccess(ctx)
 *                              → 目标是 PENDING_SHIP（由规则表决定）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MallOrderService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrderMapper          orderMapper;
    private final OrderItemMapper      orderItemMapper;
    private final OrderStatusLogMapper logMapper;
    private final ProductMapper        productMapper;
    private final CartMapper           cartMapper;
    private final PromotionMapper      promotionMapper;

    /** ★ 策略模式的入口 */
    private final PayStrategyFactory   payStrategyFactory;

    /** ★ 状态模式的入口 */
    private final OrderStateFactory    orderStateFactory;
    private final OrderStateMachine    orderStateMachine;

    private Long currentUserId() {
        Long userId = UserContext.getEmployeeId();
        if (userId == null) {
            throw new BizException("未登录或登录已过期");
        }
        return userId;
    }

    // ==================== ① ★★ 提交订单（原型图 step02 → step03）====================
    /**
     * ★ @Transactional：扣库存 / 建订单 / 写明细 / 清购物车，四件事要么全成、要么全回滚。
     *   中途抛异常（比如库存不够），前面已扣的库存会自动还回去 —— 这是"防超卖"的第二道保险。
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> submit(OrderSubmitDTO dto) {
        Long userId = currentUserId();

        // ---------- ① 取购物车行（不传 cartIds = 全选）----------
        List<Long> ids = parseIds(dto.getCartIds());
        List<Cart> carts = (ids == null)
                ? selectAllCartRows(userId)
                : cartMapper.selectByIdsAndUser(userId, ids);
        if (carts.isEmpty()) {
            return Result.fail("请先选择要购买的商品");
        }

        // ---------- ② 逐个扣库存（★ 防超卖的关键）----------
        List<OrderItem> itemDrafts = new ArrayList<>();
        double total = 0.0;
        Long shopId = null;

        for (Cart cart : carts) {
            Product product = productMapper.selectOnSaleById(cart.getProductId());
            if (product == null) {
                throw new BizException("商品不存在或已下架，请刷新购物车");
            }
            if (shopId == null) {
                shopId = product.getShopId();
            }

            // ★★ 带守卫的原子扣减：rows=0 说明库存不够（或并发下被别人抢先扣走）
            int rows = productMapper.decreaseStock(product.getId(), cart.getQuantity());
            if (rows == 0) {
                throw new BizException("「" + product.getName() + "」库存不足，仅剩 "
                        + product.getStock() + " 件");
            }

            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());   // ★ 快照：商品以后改名/改价不影响历史订单
            item.setProductImage(product.getMainImage());
            item.setPrice(product.getPrice());
            item.setQuantity(cart.getQuantity());
            item.setSubtotal(round(product.getPrice() * cart.getQuantity()));
            itemDrafts.add(item);

            total += product.getPrice() * cart.getQuantity();
        }

        // ---------- ③ 算优惠（和预览用同一套规则）----------
        double discount = calcDiscount(total);
        double payAmount = round(total - discount);

        // ---------- ④ 建订单，初始状态 = 待付款 ★ ----------
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setShopId(shopId);
        order.setTotalAmount(round(total));
        order.setPayAmount(payAmount);
        // ★★ 状态模式的起点：新订单一定是"待付款"
        order.setStatus(OrderStatus.PENDING_PAY);
        order.setPayType(dto.getPayType());           // 先记下用户选的方式，支付时再确认
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone(dto.getReceiverPhone());
        order.setReceiverAddress(dto.getReceiverAddress());
        order.setRemark(dto.getRemark());
        order.setCreateTime(LocalDateTime.now().format(FMT));
        orderMapper.insert(order);                    // id 回填到 order.getId()

        // ---------- ⑤ 写明细（批量，一条 SQL）----------
        for (OrderItem item : itemDrafts) {
            item.setOrderId(order.getId());
        }
        orderItemMapper.insertBatch(itemDrafts);

        // ---------- ⑥ 清掉已下单的购物车行 ----------
        for (Cart cart : carts) {
            cartMapper.deleteByIdAndUser(cart.getId(), userId);
        }

        log.info("【下单】买家 {} 创建订单 {} 共 {} 件商品，总额 {} 优惠 {} 应付 {}，状态={}",
                userId, order.getOrderNo(), itemDrafts.size(), order.getTotalAmount(),
                discount, payAmount, OrderStatus.cn(order.getStatus()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId",        order.getId());
        data.put("orderNo",        order.getOrderNo());
        data.put("totalAmount",    order.getTotalAmount());
        data.put("discountAmount", round(discount));
        data.put("payAmount",      payAmount);
        data.put("status",         order.getStatus());
        data.put("statusCn",       OrderStatus.cn(order.getStatus()));
        // ★ 把可选支付方式给前端 —— 原型图 12 收银台的四个选项，前端不用写死
        data.put("payTypes",       payStrategyFactory.listAll());
        return Result.ok(data);
    }

    // ==================== ② ★★★ 支付（两个模式汇合点）====================
    public Result<Map<String, Object>> pay(PayDTO dto) {
        Long userId = currentUserId();

        // ---------- ① 取订单 + 越权校验 ----------
        Order order = orderMapper.selectByIdAndUser(dto.getOrderId(), userId);
        if (order == null) {
            return Result.fail("订单不存在或不属于当前用户");
        }

        // ---------- ② ★ 策略模式：选一个支付策略并执行 ----------
        //     ★★ 这一行替代了 if (alipay) ... else if (wechat) ... else if (paypal) ...
        PayRequest request = PayRequest.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .amount(order.getPayAmount())
                .buyerAccount(dto.getBuyerAccount())
                .build();

        PayResult payResult = payStrategyFactory.pay(request, dto.getPayType());
        log.info("【支付】订单 {} 走 {} 策略 → success={} msg={}",
                order.getOrderNo(), payResult.getPayType(), payResult.getSuccess(), payResult.getMessage());

        if (!Boolean.TRUE.equals(payResult.getSuccess())) {
            return Result.fail("支付失败：" + payResult.getMessage());
        }

        // ---------- ③ 补写支付方式（pay_type 不是"状态"，不走状态机）----------
        orderMapper.updatePayType(order.getId(), payResult.getPayType());

        // ---------- ④ ★★ 状态模式：驱动订单流转 ----------
        //     调用方**不需要知道**支付后该变成什么状态 —— 那是 PendingPayState 的事
        OrderContext ctx = orderStateFactory.create(order, orderStateMachine, orderMapper);
        String beforeStatus = order.getStatus();

        // ★★ 状态模式的全部用法就这一行：把 paySuccess 交给"当前状态对象"
        //    当前是待付款 → 成功流转到待发货；当前已是待发货 → 抛异常（重复支付被拦住）
        ctx.getCurrentState().paySuccess(ctx);

        String afterStatus = ctx.getOrder().getStatus();
        log.info("【支付】订单 {} 状态 {} → {}", order.getOrderNo(),
                OrderStatus.cn(beforeStatus), OrderStatus.cn(afterStatus));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId",        order.getId());
        data.put("orderNo",        order.getOrderNo());
        data.put("payType",        payResult.getPayType());
        data.put("tradeNo",        payResult.getTradeNo());
        data.put("payMessage",     payResult.getMessage());
        data.put("payExtra",       payResult.getExtra());
        data.put("beforeStatus",   beforeStatus);
        data.put("beforeStatusCn", OrderStatus.cn(beforeStatus));
        data.put("status",         afterStatus);
        data.put("statusCn",       OrderStatus.cn(afterStatus));
        return Result.ok(data);
    }

    // ==================== ③ 我的订单列表 ====================
    public Result<Map<String, Object>> myOrders(String status, Integer page, Integer size) {
        Long userId = currentUserId();
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 10 : size;

        List<Order> rows = orderMapper.selectByUserPage(userId, status, (p - 1) * s, s);
        long total = orderMapper.countByUser(userId, status);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list",     rows);
        data.put("total",    total);
        data.put("page",     p);
        data.put("size",     s);
        data.put("statusCn", OrderStatus.all());
        return Result.ok(data);
    }

    // ==================== ④ 我的订单详情 ====================
    public Result<Map<String, Object>> myOrderDetail(Long orderId) {
        Long userId = currentUserId();
        Order order = orderMapper.selectByIdAndUser(orderId, userId);
        if (order == null) {
            return Result.fail("订单不存在或不属于当前用户");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order",     order);
        data.put("statusCn",  OrderStatus.cn(order.getStatus()));
        data.put("items",     orderItemMapper.selectByOrderId(orderId));
        data.put("statusLog", logMapper.selectByOrderId(orderId));
        return Result.ok(data);
    }

    // ==================== ⑤ ★ 展示状态模式：当前状态对象是哪个类 ====================
    /**
     * 这个接口是给答辩用的 —— 它能直接证明"状态模式把状态封装成了对象"。
     * 同一个订单在不同状态下调用它，`stateClass` 会给出不同的类名：
     *   PENDING_PAY → PendingPayState，PENDING_SHIP → PendingShipState ...
     *
     * ★ 这就是状态模式和阶段 4 状态机的可视化差别：
     *   状态机只有一个 OrderStateMachine，"状态"是一张表；
     *   状态模式里"状态"是**一个个对象**，上下文持有当前那一个。
     */
    public Result<Map<String, Object>> stateInfo(Long orderId) {
        Long userId = currentUserId();
        Order order = orderMapper.selectByIdAndUser(orderId, userId);
        if (order == null) {
            return Result.fail("订单不存在或不属于当前用户");
        }
        OrderContext ctx = orderStateFactory.create(order, orderStateMachine, orderMapper);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId",    orderId);
        data.put("status",     order.getStatus());
        data.put("statusCn",   OrderStatus.cn(order.getStatus()));
        data.put("stateClass", ctx.getCurrentState().getClass().getSimpleName());
        data.put("hint", "当前状态对象 = " + ctx.getCurrentState().getClass().getSimpleName()
                + "，它决定 paySuccess / ship / confirm / cancel 四个操作各自被允许还是被拒绝");
        return Result.ok(data);
    }

    // ==================== 小工具 ====================
    /** 不指定 cartIds 时 = 全选：先取购物车条目，再按 id 取回 Cart 行 */
    private List<Cart> selectAllCartRows(Long userId) {
        List<Long> allIds = cartMapper.selectItemsByUser(userId)
                .stream().map(CartItemVO::getCartId).collect(Collectors.toList());
        return allIds.isEmpty() ? new ArrayList<>() : cartMapper.selectByIdsAndUser(userId, allIds);
    }

    /** 和预览用同一套规则：查 tb_promotion 表，不是写死的 1200 / 120 */
    private double calcDiscount(double total) {
        Promotion matched = promotionMapper.selectBestMatch(0L, total);
        return matched == null ? 0.0 : matched.getDiscount();
    }

    private double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /**
     * 订单号：yyyyMMdd + 4 位序号（当天已有订单数 + 1）。
     * ★ 查询用带横线的日期，返回的订单号用紧凑格式 —— 两者格式不同，别搞混。
     */
    private String generateOrderNo() {
        String dayForQuery = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        long todayCount = orderMapper.countToday(dayForQuery);
        return dayForQuery.replace("-", "") + String.format("%04d", todayCount + 1);
    }

    private List<Long> parseIds(String cartIds) {
        if (cartIds == null || cartIds.trim().isEmpty()) {
            return null;
        }
        return Arrays.stream(cartIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).collect(Collectors.toList());
    }
}
