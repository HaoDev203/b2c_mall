package com.shop.controller;

import com.shop.common.Result;
import com.shop.entity.Category;
import com.shop.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 前台分类导航（原型图 8 顶部那排分类） */
@RestController
@RequestMapping("/mall/category")
@RequiredArgsConstructor
public class MallCategoryController {

    private final CategoryMapper categoryMapper;

    @GetMapping("/list")
    public Result<List<Category>> list(@RequestParam(value = "level", required = false) Integer level) {
        return Result.ok(categoryMapper.selectByLevel(level));
    }
}
