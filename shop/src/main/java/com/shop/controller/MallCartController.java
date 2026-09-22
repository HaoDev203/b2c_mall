package com.shop.controller;

import com.shop.common.Result;
import com.shop.dto.CartAddDTO;
import com.shop.dto.CartItemVO;
import com.shop.dto.CartUpdateDTO;
import com.shop.dto.OrderPreviewVO;
import com.shop.service.MallCartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前台购物车（原型图 10）。
 * ★ 路径 /mall/cart/** —— **没有**加进 WebMvcConfig 白名单，所以必须登录。
 *   这是故意的：游客不该有购物车。
 * ★ @RequestParam / @PathVariable 必须写名字（父 pom 没开 -parameters）。
 */
@RestController
@RequestMapping("/mall/cart")
@RequiredArgsConstructor
public class MallCartController {

    private final MallCartService mallCartService;

    /** 加入购物车 */
    @PostMapping("/add")
    public Result<String> add(@RequestBody CartAddDTO dto) {
        return mallCartService.add(dto);
    }

    /** 修改数量（quantity=0 等价于删除） */
    @PostMapping("/update")
    public Result<String> update(@RequestBody CartUpdateDTO dto) {
        return mallCartService.updateQuantity(dto);
    }

    /** 删除一条 */
    @DeleteMapping("/{cartId}")
    public Result<String> remove(@PathVariable("cartId") Long cartId) {
        return mallCartService.remove(cartId);
    }

    /** 购物车列表 */
    @GetMapping("/list")
    public Result<List<CartItemVO>> list() {
        return mallCartService.list();
    }

    /** ★★ 结算预览（step01 → step02）：只算钱，不落库 */
    @GetMapping("/preview")
    public Result<OrderPreviewVO> preview(
            @RequestParam(value = "cartIds", required = false) String cartIds) {
        return mallCartService.preview(cartIds);
    }
}
