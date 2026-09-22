package com.shop.controller;

import com.shop.common.Result;
import com.shop.entity.Product;
import com.shop.service.MallProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 前台商品接口。
 * ★ 免登录 —— 游客也要能浏览商品。放行规则在 WebMvcConfig 里。
 * ★ @RequestParam 的 value 必须写全！本项目父 pom 没开 -parameters 编译参数，
 *   不写 value 就会报 "Name for argument of type [java.lang.String] not specified"。
 */
@RestController
@RequestMapping("/mall/product")
@RequiredArgsConstructor
public class MallProductController {

    private final MallProductService mallProductService;

    /** 商品列表：分类筛选 + 关键词 + 三种排序 + 分页 */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "categoryId", required = false) Long    categoryId,
            @RequestParam(value = "keyword",    required = false) String  keyword,
            @RequestParam(value = "sort",       required = false) String  sort,
            @RequestParam(value = "page",       required = false) Integer page,
            @RequestParam(value = "size",       required = false) Integer size) {
        return mallProductService.list(categoryId, keyword, sort, page, size);
    }

    /** 商品详情 */
    @GetMapping("/{id}")
    public Result<Product> detail(@PathVariable("id") Long id) {
        return mallProductService.detail(id);
    }
}
