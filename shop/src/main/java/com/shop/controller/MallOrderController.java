package com.shop.controller;

import com.shop.common.Result;
import com.shop.dto.OrderSubmitDTO;
import com.shop.dto.PayDTO;
import com.shop.pay.PayStrategyFactory;
import com.shop.service.MallOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 前台订单 + 支付（原型图 9 / 11 / 12）。
 * ★ 必须登录（/mall/order/** 不在白名单里）。
 */
@RestController
@RequestMapping("/mall/order")
@RequiredArgsConstructor
public class MallOrderController {

    private final MallOrderService   mallOrderService;
    private final PayStrategyFactory payStrategyFactory;   // 收银台展示用

    /** ★★ 提交订单（step02 → step03）：建订单 + 扣库存 + 清购物车 */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody OrderSubmitDTO dto) {
        return mallOrderService.submit(dto);
    }

    /** ★★★ 支付 —— 策略模式 + 状态模式同时出场 */
    @PostMapping("/pay")
    public Result<Map<String, Object>> pay(@RequestBody PayDTO dto) {
        return mallOrderService.pay(dto);
    }

    /** 收银台：可选支付方式列表（原型图 12 的四个选项） */
    @GetMapping("/pay-types")
    public Result<Map<String, String>> payTypes() {
        return Result.ok(payStrategyFactory.listAll());
    }

    /** 我的订单列表（按状态筛 Tab） */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "status", required = false) String  status,
            @RequestParam(value = "page",   required = false) Integer page,
            @RequestParam(value = "size",   required = false) Integer size) {
        return mallOrderService.myOrders(status, page, size);
    }

    /** 我的订单详情（含明细 + 流转轨迹） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return mallOrderService.myOrderDetail(id);
    }

    /**
     * ★ 状态模式展示接口（答辩用）：当前订单的状态对象是哪个类。
     * 同一个订单，付款前调是 PendingPayState，付款后调变成 PendingShipState。
     */
    @GetMapping("/{id}/state")
    public Result<Map<String, Object>> state(@PathVariable("id") Long id) {
        return mallOrderService.stateInfo(id);
    }
}
