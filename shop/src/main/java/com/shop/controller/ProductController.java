package com.shop.controller;

import com.shop.common.Result;
import com.shop.dto.ProductDTO;
import com.shop.entity.Product;
import com.shop.service.ProductPublishService;
import com.shop.service.ProductQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ★ 路径只写 /product，不要写 /shop/product ——
 *   网关的 StripPrefix=1 会把 /shop 剥掉再转发，浏览器看到的路径 ≠ 代码里的路径。
 * ★ 本 Controller 所有接口都受 AuthInterceptor 保护，必须带令牌。
 */
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductPublishService productPublishService;
    private final ProductQueryService   productQueryService;

    /** 商品发布 —— 阶段 3 核心考点，走模板方法骨架 */
    @PostMapping("/publish")
    public Result<Product> publish(@RequestBody ProductDTO dto) {
        return productPublishService.publish(dto);
    }

    /** 后台商品列表：多条件筛选 + 分页 */
    @GetMapping("/list")
    public Result<List<Product>> list(@RequestParam(value = "status",      required = false) String  status,
                                      @RequestParam(value = "productType", required = false) String  productType,
                                      @RequestParam(value = "categoryId",  required = false) Long    categoryId,
                                      @RequestParam(value = "keyword",     required = false) String  keyword,
                                      @RequestParam(value = "page",        required = false) Integer page,
                                      @RequestParam(value = "size",        required = false) Integer size) {
        return productQueryService.list(status, productType, categoryId, keyword, page, size);
    }

    /** 上架 / 下架 */
    @PostMapping("/{id}/status")
    public Result<String> changeStatus(@PathVariable("id") Long id,
                                       @RequestParam("status") String status) {
        return productQueryService.changeStatus(id, status);
    }
}
