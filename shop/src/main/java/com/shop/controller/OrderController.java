package com.shop.controller;

import com.shop.common.Result;
import com.shop.dto.ShipDTO;
import com.shop.mapper.StateMachineRuleMapper;
import com.shop.service.OrderService;
import com.shop.statemachine.OrderStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 阶段 4 · 订单接口。
 * 路径前缀 /order —— gateway 的 StripPrefix=1 会剥掉 /shop，
 * 所以外部访问是 http://localhost:8090/shop/order/list
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService           orderService;
    private final StateMachineRuleMapper ruleMapper;   // 只为了展示规则，答辩用

    /** 订单列表。Tab 分类传 status；筛选传 orderNo / receiverPhone / payType */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String  status,
            @RequestParam(required = false) String  orderNo,
            @RequestParam(required = false) String  receiverPhone,
            @RequestParam(required = false) String  payType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return orderService.list(status, orderNo, receiverPhone, payType, page, size);
    }

    /** 订单详情（含明细 + 状态流转轨迹） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return orderService.detail(id);
    }

    /** ★ 发货（状态机：待发货 → 待收货） */
    @PostMapping("/ship")
    public Result<String> ship(@RequestBody ShipDTO dto) {
        return orderService.ship(dto.getOrderId());
    }

    /** 确认收货（状态机：待收货 → 交易成功） */
    @PostMapping("/confirm")
    public Result<String> confirm(@RequestBody ShipDTO dto) {
        return orderService.confirm(dto.getOrderId());
    }

    /** 取消订单（状态机：待付款 → 交易失败） */
    @PostMapping("/cancel")
    public Result<String> cancel(@RequestBody ShipDTO dto) {
        return orderService.cancel(dto.getOrderId());
    }

    /**
     * ★ 展示状态机的全部规则（GET /shop/order/rules）。
     * 这个接口本身没业务价值，但答辩时非常有用 ——
     * 直接告诉老师"规则是配在库里的，这是全部 8 条"。
     */
    @GetMapping("/rules")
    public Result<Object> rules() {
        return Result.ok(ruleMapper.selectByBizType(OrderStateMachine.BIZ_ORDER));
    }
}
