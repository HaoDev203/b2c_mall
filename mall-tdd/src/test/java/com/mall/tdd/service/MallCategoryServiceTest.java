package com.mall.tdd.service;

import com.mall.tdd.entity.Category;
import com.mall.tdd.mapper.CategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** B1 · C 端分类导航（C02，1 条）。 */
@DisplayName("B1 C 端分类导航")
class MallCategoryServiceTest {

    private CategoryMapper categoryMapper;
    private MallCategoryService service;

    @BeforeEach
    void setUp() {
        categoryMapper = mock(CategoryMapper.class);
        service = new MallCategoryService(categoryMapper);
    }

    @Test
    @DisplayName("C02 分类名去重：同名只留首条，顺序不变")
    void should_dedupe_category_navigation_by_name() {
        // 库里允许同名不同 id（多店铺各建了一个「数码」）
        when(categoryMapper.selectAll()).thenReturn(Arrays.asList(
                category(1L, "数码"),
                category(7L, "数码"),
                category(2L, "服饰"),
                category(3L, "食品")));

        List<Category> nav = service.list();

        assertEquals(3, nav.size(), "4 行里只有 3 个不同分类名，实际：" + names(nav));
        assertEquals(Arrays.asList("数码", "服饰", "食品"), names(nav),
                "顺序应与库里的顺序一致，去重不能打乱导航栏");
        assertEquals(Long.valueOf(1L), nav.get(0).getId(),
                "同名保留首条（id=1）。保留 id=7 会让前端拿到的分类 id 与列表首次渲染不一致");
    }

    // ★ 为什么去重不写在 SQL 里（答辩高概率被问）：
    //   SELECT DISTINCT * 去不掉 —— 两行 id 不同，整行就不同；
    //   SELECT DISTINCT name 能去重但丢了 id，前端点分类传不回参数。
    //   所以只能把原始行捞回来，在 Service 层按 name 保留首条。

    private Category category(Long id, String name) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private List<String> names(List<Category> list) {
        return list.stream().map(Category::getName).collect(Collectors.toList());
    }
}
