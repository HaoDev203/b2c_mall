package com.shop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.Result;
import com.shop.common.UserContext;
import com.shop.entity.Message;
import com.shop.mapper.MessageMapper;
import com.shop.mapper.OrderMapper;
import com.shop.statemachine.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 看板服务。
 * 阶段 2 做了「消息通知」部分；
 * 阶段 4 订单表建好后，补上了「订单状态卡片」（见文件末尾 orderSummary）。
 * 折线趋势图 trend 仍未做 —— 要等阶段 6 支付跑通，订单数据才够看。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 需求 Step 2.3：统计结果缓存 60 秒 */
    private static final long CACHE_SECONDS = 60;

    @Value("${app.cache.key-prefix:shop:cache:}")
    private String cachePrefix;

    private final MessageMapper       messageMapper;
    private final StringRedisTemplate redisTemplate;
    private final OrderMapper         orderMapper;      // ★ 阶段 4 补：订单状态卡片要用
    private final ObjectMapper        objectMapper;     // ★ 阶段 4 补：把 Map 序列化成 JSON 缓存

    // ==================== ① 消息列表 ====================
    /**
     * ★ 列表本身**不缓存**。
     * 它要实时反映「已读 / 未读」—— 缓存了就变成"刚点完已读，界面还是旧的"。
     */
    public Result<List<Message>> messages() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.fail("未获取到店铺信息");
        }
        List<Message> list = messageMapper.selectByShopId(shopId);
        log.info("【Dashboard】查询消息列表 shopId={}，共 {} 条", shopId, list.size());
        return Result.ok(list);
    }

    // ==================== ② 未读数（带缓存）====================
    /**
     * ★★ 本阶段的核心：需求 Step 2.3「统计结果缓存 60 秒」就落在这里。
     *
     * 三步走（这套路叫 Cache Aside，缓存旁路）：
     *   1. 先问 Redis —— 有就直接返回        ← 命中缓存
     *   2. 没有就查数据库                     ← 回源
     *   3. 查完写回 Redis，60 秒自动过期      ← 预热
     */
    public Result<Integer> unreadCount() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.fail("未获取到店铺信息");
        }

        // ★ key 里必须带 shopId！全班共用老师那台 Redis，不带就会串号
        String key = cachePrefix + "unread:" + shopId;

        // 第 1 步：先问缓存
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                log.info("【Dashboard】未读数【命中缓存】key={} -> {}", key, cached);
                return Result.ok(Integer.valueOf(cached));
            } catch (NumberFormatException e) {
                // 缓存里被塞了脏数据：删掉，走回源流程重建
                log.warn("【Dashboard】缓存内容不是数字，删除重建 key={} value={}", key, cached);
                redisTemplate.delete(key);
            }
        }

        // 第 2 步：回源查库
        int count = messageMapper.countUnread(shopId);

        // 第 3 步：写回缓存，60 秒后自动过期
        redisTemplate.opsForValue().set(key, String.valueOf(count), CACHE_SECONDS, TimeUnit.SECONDS);
        log.info("【Dashboard】未读数【回源查库】key={} -> {}（已写入缓存 {} 秒）", key, count, CACHE_SECONDS);
        return Result.ok(count);
    }

    // ==================== ③ 标记已读 ====================
    /**
     * ★ 写操作之后必须让缓存失效，否则最多 60 秒内用户看到的还是旧数字。
     *   这就是「缓存一致性」最基本的一条：改库 + 删缓存。
     */
    public Result<String> markRead(Long id) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.fail("未获取到店铺信息");
        }

        String now = LocalDateTime.now().format(FMT);
        int rows = messageMapper.markRead(id, shopId, now);
        if (rows == 0) {
            return Result.fail("消息不存在或不属于当前店铺");
        }

        // Cache Aside：改完库，立刻删缓存（下次读会重新回源算）
        redisTemplate.delete(cachePrefix + "unread:" + shopId);
        log.info("【Dashboard】消息 {} 已标记为已读，并删除未读数缓存", id);
        return Result.ok("已标记为已读");
    }

    // ==================== ④ ★ 订单状态卡片（阶段 4 补）====================
    /**
     * 需求 Step 2.2：顶部卡片 —— 待发货 / 已完成 / 退款售后。
     * 需求 Step 2.3：统计结果缓存 60 秒。
     *
     * ★ 这次用 Jackson 把 Map 序列化成 JSON 再缓存 ——
     *   阶段 2 缓存的是单个数字，直接 String 存就行；
     *   这里要缓存一个对象，就必须序列化。存 JSON 是标准做法，
     *   千万别用 "1,2,3" 拼字符串（字段顺序一变就全乱了）。
     */
    public Result<Map<String, Object>> orderSummary() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.fail("未获取到店铺信息");
        }

        String key = cachePrefix + "order:summary:" + shopId;

        // 第 1 步：先问缓存
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                Map<String, Object> data = objectMapper.readValue(
                        cached, new TypeReference<Map<String, Object>>() {});
                log.info("【Dashboard】订单卡片【命中缓存】key={}", key);
                return Result.ok(data);
            } catch (Exception e) {
                log.warn("【Dashboard】订单缓存解析失败，删除重建 key={}", key);
                redisTemplate.delete(key);
            }
        }

        // 第 2 步：回源查库（6 个状态的各自数量）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pendingPay",     orderMapper.countByStatus(shopId, OrderStatus.PENDING_PAY));
        data.put("pendingShip",    orderMapper.countByStatus(shopId, OrderStatus.PENDING_SHIP));
        data.put("pendingReceive", orderMapper.countByStatus(shopId, OrderStatus.PENDING_RECEIVE));
        data.put("success",        orderMapper.countByStatus(shopId, OrderStatus.SUCCESS));
        data.put("failed",         orderMapper.countByStatus(shopId, OrderStatus.FAILED));
        data.put("refunding",      orderMapper.countByStatus(shopId, OrderStatus.REFUNDING));
        data.put("statusCn",       OrderStatus.all());

        // 第 3 步：写回缓存，60 秒过期
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data),
                                           CACHE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("【Dashboard】订单卡片写缓存失败", e);
        }
        log.info("【Dashboard】订单卡片【回源查库】key={} -> {}", key, data);
        return Result.ok(data);
    }
}
