package com.shop.controller;

import com.shop.common.Result;
import com.shop.entity.Message;
import com.shop.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")        // ★ 只写 /dashboard，不要写 /shop/dashboard
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** 消息通知列表 —— 对应原型图 2 右侧那一栏 */
    @GetMapping("/messages")
    public Result<List<Message>> messages() {
        return dashboardService.messages();
    }

    /** 未读数（走 Redis 缓存 60 秒）—— 需求 Step 2.3 的落点 */
    @GetMapping("/messages/unread")
    public Result<Integer> unread() {
        return dashboardService.unreadCount();
    }

    /**
     * 标记单条消息为已读。
     * ★ @PathVariable("id") 括号里的名字必须写：
     *   本项目的父 POM 没配 maven-compiler-plugin，也没有继承 spring-boot-starter-parent，
     *   所以编译时没有 -parameters，参数名在 class 文件里被擦掉了 ——
     *   不写名字会在运行时报 "Name for argument of type [java.lang.Long] not specified"。
     */
    @PostMapping("/messages/{id}/read")
    public Result<String> markRead(@PathVariable("id") Long id) {
        return dashboardService.markRead(id);
    }

    /**
     * ★ 订单状态卡片（阶段 4 补上）。
     * 需求 Step 2.2 的"顶部订单状态卡片"，之前因为没有订单表一直空着。
     */
    @GetMapping("/order-summary")
    public Result<Map<String, Object>> orderSummary() {
        return dashboardService.orderSummary();
    }
}
