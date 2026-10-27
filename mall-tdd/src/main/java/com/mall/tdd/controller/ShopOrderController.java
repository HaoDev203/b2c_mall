package com.mall.tdd.controller;

import com.mall.tdd.common.DownloadResponse;
import com.mall.tdd.common.Result;
import com.mall.tdd.dto.AdminOrderPageVO;
import com.mall.tdd.dto.AdminOrderQuery;
import com.mall.tdd.dto.BatchResultVO;
import com.mall.tdd.dto.BatchShipCommand;
import com.mall.tdd.service.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * B 端（商家侧）订单查询：列表 / 发货 / 批量发货 / 导出。4 个接口。
 *
 * <p>★ 没有订单详情接口：拆解-03 的需求原文摘要 7 项里没有「订单详情」，
 * 9 条验收的字段断言全在列表卡片上，是契约文档多列了一个接口，不做。
 */
@RestController
@RequestMapping("/api/shop/order")
@RequiredArgsConstructor
public class ShopOrderController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderQueryService orderQueryService;

    /**
     * 11 个筛选条件 + 6 个快捷时间 + 7 个 Tab 计数，全部由
     * {@link AdminOrderQuery} 承接、在 Service 里归一化。
     */
    @GetMapping("/list")
    public Result<AdminOrderPageVO> list(
            AdminOrderQuery query,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return orderQueryService.listOrders(query, page, size);
    }

    /**
     * 单个发货。<b>状态流转与运单号在同一条 UPDATE 里写完</b>——
     * 拆两步会出现「有运单号但状态还是待发货」的中间态，买家看得到单号、订单卡住。
     */
    @PostMapping("/ship")
    public Result<Void> ship(
            @RequestParam(value = "orderId", required = false) Long orderId,
            @RequestParam(value = "trackingNo", required = false) String trackingNo) {
        return orderQueryService.ship(orderId, trackingNo);
    }

    /**
     * 批量发货。逐条独立成败，不整体回滚，返回 {@code {total, success, failed, results}}。
     *
     * <p>orderIds 走请求体而不是 {@code ?orderIds=1,2,3}：后者要自己拼串再解析，
     * 一旦有条目是空串就会静默少发一条。
     */
    @PostMapping("/batch-ship")
    public Result<BatchResultVO> batchShip(@RequestBody(required = false) BatchShipCommand cmd) {
        if (cmd == null || cmd.getOrderIds() == null || cmd.getOrderIds().isEmpty()) {
            return Result.fail("请选择要发货的订单");
        }
        return orderQueryService.batchShip(cmd.getOrderIds(), cmd.getTrackingNo());
    }

    /** 导出订单 Excel。复用列表的筛选条件，只把分页换成导出上限 */
    @GetMapping("/export")
    public ResponseEntity<?> export(AdminOrderQuery query) {
        String filename = "orders_" + LocalDate.now().format(DAY) + ".xlsx";
        return DownloadResponse.attachment(orderQueryService.exportOrders(query), filename);
    }
}
