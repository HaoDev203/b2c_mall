package com.shop.service;

import com.shop.common.Result;
import com.shop.common.UserContext;
import com.shop.entity.Order;
import com.shop.entity.OrderItem;
import com.shop.entity.OrderStatusLog;
import com.shop.mapper.OrderItemMapper;
import com.shop.mapper.OrderMapper;
import com.shop.mapper.OrderStatusLogMapper;
import com.shop.statemachine.OrderEvent;
import com.shop.statemachine.OrderStateMachine;
import com.shop.statemachine.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段 4 · 订单服务。
 *
 * ★ 注意：这个类里【没有一行】判断订单状态能不能流转的代码 ——
 *   全都交给 OrderStateMachine。这就是"规则跟业务分离"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper          orderMapper;
    private final OrderItemMapper      orderItemMapper;
    private final OrderStatusLogMapper logMapper;
    private final OrderStateMachine    stateMachine;
    private final StringRedisTemplate  redisTemplate;

    @Value("${app.cache.key-prefix:shop:cache:}")
    private String cachePrefix;

    // ==================== ① 订单列表（Tab 分类就在这里）====================
    public Result<Map<String, Object>> list(String status, String orderNo, String receiverPhone,
                                           String payType, Integer page, Integer size) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.fail("未获取到店铺信息");
        }

        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 10 : size;

        List<Order> rows  = orderMapper.selectPage(shopId, status, orderNo,
                                                   receiverPhone, payType, (p - 1) * s, s);
        long        total = orderMapper.countPage(shopId, status, orderNo, receiverPhone, payType);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page",  p);
        data.put("size",  s);
        data.put("list",  rows);
        data.put("statusCn", OrderStatus.all());   // ★ 顺手把 6 个状态的中文名给前端（Tab 直接用）
        log.info("【订单】列表查询 shopId={} status={} 共 {} 条", shopId, status, total);
        return Result.ok(data);
    }

    // ==================== ② 订单详情（含明细 + 流转轨迹）====================
    public Result<Map<String, Object>> detail(Long id) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.fail("未获取到店铺信息");
        }
        Order order = orderMapper.selectById(id);
        // ★ 越权校验：不能看别家店的订单
        if (order == null || !shopId.equals(order.getShopId())) {
            return Result.fail("订单不存在或不属于当前店铺");
        }

        List<OrderItem>      items = orderItemMapper.selectByOrderId(id);
        List<OrderStatusLog> logs  = logMapper.selectByOrderId(id);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order",     order);
        data.put("statusCn",  OrderStatus.cn(order.getStatus()));
        data.put("items",     items);
        data.put("statusLog", logs);   // ★ 状态流转留痕，答辩时展示这个
        return Result.ok(data);
    }

    // ==================== ③ ★ 发货 ====================
    public Result<String> ship(Long orderId) {
        return fire(orderId, OrderEvent.SHIP, "后台点击发货");
    }

    // ==================== ④ 确认收货 ====================
    public Result<String> confirm(Long orderId) {
        return fire(orderId, OrderEvent.CONFIRM, "后台确认收货");
    }

    // ==================== ⑤ 取消订单（超时未支付）====================
    public Result<String> cancel(Long orderId) {
        return fire(orderId, OrderEvent.CANCEL_TIMEOUT, "超时未支付，系统取消");
    }

    /**
     * ★ 三个接口共用这一个私有方法 —— 这就是状态机的价值：
     *   发货 / 确认收货 / 取消 的规则各不相同，但代码路径完全一样。
     *   以后再加「申请售后」也只是换个 event 常量。
     */
    private Result<String> fire(Long orderId, String event, String remark) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.fail("未获取到店铺信息");
        }

        // 越权校验：只能操作自己店铺的订单
        Order order = orderMapper.selectById(orderId);
        if (order == null || !shopId.equals(order.getShopId())) {
            return Result.fail("订单不存在或不属于当前店铺");
        }

        String operator = "员工#" + UserContext.getEmployeeId();
        // ★ 这里可能抛 BizException（非法流转），由 GlobalExceptionHandler 统一处理
        String to = stateMachine.fire(orderId, event, operator, remark);

        // 状态变了 → 订单统计缓存失效（Cache Aside：改库 + 删缓存）
        redisTemplate.delete(cachePrefix + "order:summary:" + shopId);

        return Result.ok("操作成功，订单状态：" + OrderStatus.cn(to));
    }
}
