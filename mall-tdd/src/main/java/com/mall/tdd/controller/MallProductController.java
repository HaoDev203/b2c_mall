package com.mall.tdd.controller;

import com.mall.tdd.common.Result;
import com.mall.tdd.dto.MallProductVO;
import com.mall.tdd.entity.Category;
import com.mall.tdd.service.MallCategoryService;
import com.mall.tdd.service.MallProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端（买家侧）商品：分类导航、列表、详情。3 个接口。
 *
 * <p>这一层只做三件事：接参数、调 Service、原样返回。
 * <b>不写任何业务判断</b>——判断写在这里就等于绕过了 155 条测试。
 */
@RestController
@RequestMapping("/api/mall")
@RequiredArgsConstructor
public class MallProductController {

    private final MallCategoryService categoryService;
    private final MallProductService productService;

    /**
     * 分类导航。
     *
     * <p>{@link MallCategoryService#list()} 返回的是 {@code List<Category>} 而不是 {@code Result}：
     * 查分类没有入参，也就没有失败分支。这里包一层 {@code Result.ok} 是接口层的责任，
     * 不为「统一」回头去改已经绿的 Service 和它的测试。
     */
    @GetMapping("/category/list")
    public Result<List<Category>> categories() {
        return Result.ok(categoryService.list());
    }

    /**
     * @param categoryId 分类筛选，null = 不过滤
     * @param sort       排序方式；具体白名单在 Service 里，这里不校验（校验写两处迟早不一致）
     */
    @GetMapping("/product/list")
    public Result<List<MallProductVO>> list(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "sort", required = false) String sort) {
        return productService.list(categoryId, sort);
    }

    @GetMapping("/product/{id}")
    public Result<MallProductVO> detail(@PathVariable("id") Long id) {
        return productService.detail(id);
    }
}
