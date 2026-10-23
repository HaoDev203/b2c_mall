package com.mall.tdd.service;

import com.mall.tdd.entity.Category;
import com.mall.tdd.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** C 端分类导航。 */
@Service
@RequiredArgsConstructor
public class MallCategoryService {

    private final CategoryMapper categoryMapper;

    /**
     * 一级分类标签，按 name 去重。
     *
     * <p>★ 为什么不在 SQL 里 DISTINCT：
     * <ul>
     *   <li>{@code SELECT DISTINCT *} 去不掉 —— 两行 id 不同，整行就不同；</li>
     *   <li>{@code SELECT DISTINCT name} 能去重但丢了 id，前端点分类传不回参数。</li>
     * </ul>
     * 只能把原始行捞回来，按 name 保留首条。LinkedHashMap 顺带把顺序固定住，
     * 导航栏不会每次刷新换位置。
     */
    public List<Category> list() {
        List<Category> all = categoryMapper.selectAll();

        Map<String, Category> byName = new LinkedHashMap<String, Category>();
        for (Category c : all) {
            byName.putIfAbsent(c.getName(), c);
        }
        return new ArrayList<Category>(byName.values());
    }
}
