package com.mall.tdd.service;

import com.mall.tdd.common.Result;
import com.mall.tdd.dto.MallProductVO;
import com.mall.tdd.entity.Product;
import com.mall.tdd.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B1 · C 端商品列表（C01/C03~C09，共 8 条）。
 *
 * <p>纯 Mockito，不启动 Spring：Service 手动 new，Mapper 是 mock。
 * 这里测的不是「SQL 写对没」（那是真 SQL 探针的事），
 * 而是「Service 有没有把该传的参数传对、该做的判断做够」。
 */
@DisplayName("B1 C 端商品列表")
class MallProductServiceTest {

    private ProductMapper productMapper;
    private MallProductService service;

    @BeforeEach
    void setUp() {
        productMapper = mock(ProductMapper.class);
        service = new MallProductService(productMapper);
    }

    // ---------------------------------------------------------------- C01

    @Test
    @DisplayName("C01 只返回上架商品，过滤发生在 SQL 里")
    void should_only_return_on_sale_products() {
        Product onSaleA = product(1L, "商品A", "on_sale");
        Product onSaleB = product(2L, "商品B", "on_sale");
        Product offSale = product(3L, "商品C", "off_sale");

        when(productMapper.selectOnSalePage(any(), anyString(), anyString(), anyInt()))
                .thenReturn(Arrays.asList(onSaleA, onSaleB));

        Result<List<MallProductVO>> result = service.list(null, null);

        assertEquals(Integer.valueOf(200), result.getCode());
        assertEquals(2, result.getData().size(), "两条上架商品应原样返回");

        for (MallProductVO vo : result.getData()) {
            assertEquals("on_sale", vo.getStatus(), "返回列表里不能出现非上架商品");
        }
        assertTrue(result.getData().stream().noneMatch(vo -> vo.getId().equals(3L)),
                "下架商品（id=3）不应出现在结果里，实际：" + ids(result.getData()));

        // ★ 关键断言：只调了「带 on_sale 条件」的查询方法，且只调一次。
        //   如果实现写成「先用全量查询捞回来再 stream().filter()」，
        //   Mapper 上根本没有全量方法可用（也不该为它开一个），
        //   所以这个「只调一次 selectOnSalePage」就是全表进内存的防线。
        verify(productMapper, times(1))
                .selectOnSalePage(any(), anyString(), anyString(), anyInt());
    }

    // ---------------------------------------------------------------- C03

    @Test
    @DisplayName("C03 默认「全部」：categoryId 必须为 null，不能当 0 传")
    void should_return_all_on_sale_products_when_category_is_absent() {
        when(productMapper.selectOnSalePage(any(), anyString(), anyString(), anyInt()))
                .thenReturn(Arrays.asList(product(1L, "商品A", "on_sale"), product(2L, "商品B", "on_sale")));

        service.list(null, null);

        ArgumentCaptor<Long> categoryId = ArgumentCaptor.forClass(Long.class);
        verify(productMapper).selectOnSalePage(categoryId.capture(), anyString(), anyString(), anyInt());

        // ★ 传 0 的话 SQL 会变成 category_id = 0，「全部」直接变空白页
        assertNull(categoryId.getValue(),
                "「全部」标签必须传 null，实际传了 " + categoryId.getValue());
    }

    // ---------------------------------------------------------------- C04

    @Test
    @DisplayName("C04 选某分类：只查该分类，且仍受「上架」约束")
    void should_filter_products_by_category_id() {
        when(productMapper.selectOnSalePage(any(), anyString(), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(product(5L, "手机", "on_sale")));

        service.list(2L, null);

        ArgumentCaptor<Long> categoryId = ArgumentCaptor.forClass(Long.class);
        verify(productMapper).selectOnSalePage(categoryId.capture(), anyString(), anyString(), anyInt());

        assertEquals(Long.valueOf(2L), categoryId.getValue(), "分类 id 应原样下发");

        // ★ 「选了分类」不等于「可以不筛状态」：调用的仍是 selectOnSalePage，
        //   上架条件写在该方法的 SQL 里，两个条件天然同时生效。
        //   （这是 C01 + C04 的组合约束：换成「有分类就只按分类查」的写法，C01 会红。）
    }

    // ---------------------------------------------------------------- C05

    @Test
    @DisplayName("C05 按销量排序：列=sales，方向=DESC")
    void should_sort_by_sales_desc() {
        when(productMapper.selectOnSalePage(any(), anyString(), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(product(1L, "爆款", "on_sale")));

        service.list(null, "sales");

        ArgumentCaptor<String> column = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> direction = ArgumentCaptor.forClass(String.class);
        verify(productMapper).selectOnSalePage(any(), column.capture(), direction.capture(), anyInt());

        assertEquals("sales", column.getValue(), "排序列应为 sales");
        // ★ 方向需求没写，这是本项目自定的口径：销量降序（卖得多的先露脸）。
        //   答辩被问「需求没说你怎么定」时，答「行业惯例 + 已在测试里固定」。
        assertEquals("DESC", direction.getValue(), "销量应为降序");
    }

    // ---------------------------------------------------------------- C06

    @Test
    @DisplayName("C06 按价格排序：列=price 原样下发")
    void should_sort_by_price() {
        when(productMapper.selectOnSalePage(any(), anyString(), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(product(1L, "便宜货", "on_sale")));

        service.list(null, "price");

        ArgumentCaptor<String> column = ArgumentCaptor.forClass(String.class);
        verify(productMapper).selectOnSalePage(any(), column.capture(), any(), anyInt());

        assertEquals("price", column.getValue(), "排序列应为 price");
        // 方向刻意不在这条断言：C06 只管「参数透传」，方向由 C07 的白名单兜底。
        // 两条混在一起，红了会分不清是参数错还是方向错。
    }

    // ---------------------------------------------------------------- C07

    @Test
    @DisplayName("C07 非法排序参数回落默认值（防 SQL 注入）")
    void should_fall_back_to_default_sort_when_param_is_illegal() {
        when(productMapper.selectOnSalePage(any(), anyString(), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(product(1L, "商品A", "on_sale")));

        String injected = "price; DROP TABLE tb_product";
        service.list(null, injected);

        ArgumentCaptor<String> column = ArgumentCaptor.forClass(String.class);
        verify(productMapper).selectOnSalePage(any(), column.capture(), any(), anyInt());

        // ★ 排序字段在 MyBatis 里只能用 ${} 拼接（#{} 放进 ORDER BY 会变成常量，
        //   排序直接失效），所以「用户输入」与「SQL 片段」之间必须隔一层白名单。
        //   这里断言的正是：原文不可能被拼进去。
        assertNotEquals(injected, column.getValue(), "非法排序参数被原样下发，存在注入风险");
        assertTrue(Arrays.asList("id", "sales", "price").contains(column.getValue()),
                "排序列必须是白名单内的值，实际：" + column.getValue());
        assertEquals("id", column.getValue(), "非法参数应回落默认列 id");
    }

    // ---------------------------------------------------------------- C08

    @Test
    @DisplayName("C08 卡片四字段齐备（测「字段有没有丢」，不测值）")
    void should_return_all_card_fields() {
        Product p = product(1L, "商品A", "on_sale");
        p.setMainImage("/img/a.png");
        p.setPrice(99.00);
        p.setMarketPrice(129.00);
        p.setSales(321);

        when(productMapper.selectOnSalePage(any(), anyString(), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(p));

        MallProductVO vo = service.list(null, null).getData().get(0);

        // ★ 只断言非空，不断言具体值：改一个演示数据就红的测试没有价值。
        assertNotNull(vo.getMainImage(), "主图丢失");
        assertNotNull(vo.getName(), "商品名丢失");
        assertNotNull(vo.getPrice(), "售价丢失");
        assertNotNull(vo.getSales(), "销量丢失（最容易漏映射的字段）");
    }

    // ---------------------------------------------------------------- C09

    @Test
    @DisplayName("C09 已下架商品详情不可见")
    void should_not_expose_detail_of_off_sale_product() {
        // 下架商品在 SQL 层就查不到（selectByIdAndOnSale 带 on_sale 条件）
        when(productMapper.selectByIdAndOnSale(9L)).thenReturn(null);

        Result<MallProductVO> result = service.detail(9L);

        assertNotEquals(Integer.valueOf(200), result.getCode(), "已下架商品不能返回成功");
        assertTrue(result.getMsg().contains("下架") || result.getMsg().contains("不存在"),
                "提示统一为「不存在或已下架」，实际：" + result.getMsg());
        assertNull(result.getData(), "失败时不能带出商品数据");
    }

    // ---------------------------------------------------------------- 工具

    private Product product(Long id, String name, String status) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setStatus(status);
        return p;
    }

    private List<Long> ids(List<MallProductVO> list) {
        return list.stream().map(MallProductVO::getId).collect(Collectors.toList());
    }
}
