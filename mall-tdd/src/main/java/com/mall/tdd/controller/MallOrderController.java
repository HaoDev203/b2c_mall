package com.mall.tdd.controller;

import com.mall.tdd.common.Result;
import com.mall.tdd.dto.BuyNowVO;
import com.mall.tdd.dto.OrderListVO;
import com.mall.tdd.service.MallOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端（买家侧）订单：列表 / 删除 / 立即购买。3 个接口。
 *
 * <p>★★ <b>签名里没有、也不能有 userId</b>。买家身份一律由
 * {@code UserContext} 提供，从前端参数取就是最典型的水平越权（U01）——
 * 改个 {@code ?userId=} 就能看别人的订单。W04 用反射守着这条。
 */
@RestController
@RequestMapping("/api/mall/order")
@RequiredArgsConstructor
public class MallOrderController {

    private final MallOrderService mallOrderService;

    /**
     * @param status  Tab 状态；null = 全部（「全部」是独立统计，不是 4 个 Tab 之和）
     * @param orderNo 订单号精确匹配；空串由 Service 当「不筛」处理
     * @param page    页码从 1 开始；null 用默认值
     * @param size    每页条数；null 用默认值，上限 50 —— <b>归一化只在 Service 做一次</b>
     */
    @GetMapping("/list")
    public Result<OrderListVO> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return mallOrderService.listOrders(status, orderNo, page, size);
    }

    /** 买家删除 = 软删除（仅买家侧隐藏，后台仍可见）。物理删除方法在 Mapper 上根本不存在 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        return mallOrderService.deleteOrder(id);
    }

    /** 立即购买之前先校验商品仍在售且有货，不然前端跳到下单页才报错 */
    @PostMapping("/buy-now")
    public Result<BuyNowVO> buyNow(@RequestParam(value = "productId", required = false) Long productId) {
        return mallOrderService.buyNow(productId);
    }
}
