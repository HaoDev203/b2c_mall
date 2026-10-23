package com.mall.tdd.service;

import com.mall.tdd.common.Result;
import com.mall.tdd.dto.MallProductVO;
import com.mall.tdd.entity.Product;
import com.mall.tdd.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端商品列表 / 详情。
 *
 * <p>权限模型：所有人可看，无 shopId 概念 —— 这是它和后台 ProductQueryService 分开的原因。
 */
@Service
@RequiredArgsConstructor
public class MallProductService {

    /** 列表一次最多返回多少条。需求没提分页，这里给个上限防止全表返回 */
    private static final int FEED_LIMIT = 100;

    private static final String DEFAULT_COLUMN = "id";
    private static final String DEFAULT_DIRECTION = "DESC";

    /**
     * ★★ 排序白名单 —— 这是「用 ${} 也安全」的唯一依据。
     * key = 前端传的 sort，value = {列, 方向}，两个值都写死。
     * 任何不在表里的值（包括 null、空串、注入串）都会走兜底。
     */
    private static final Map<String, String[]> SORT_WHITELIST;
    static {
        Map<String, String[]> m = new LinkedHashMap<String, String[]>();
        // 方向需求没写，这是本项目自定口径：销量降序（卖得多的先露脸）、价格升序（便宜的在前）
        m.put("sales", new String[]{"sales", "DESC"});
        m.put("price", new String[]{"price", "ASC"});
        SORT_WHITELIST = Collections.unmodifiableMap(m);
    }

    private final ProductMapper productMapper;

    /** C 端商品列表。categoryId 为 null 表示「全部」标签 */
    public Result<List<MallProductVO>> list(Long categoryId, String sort) {
        String[] order = resolveSort(sort);

        List<Product> products = productMapper.selectOnSalePage(
                categoryId, order[0], order[1], FEED_LIMIT);

        List<MallProductVO> data = new ArrayList<MallProductVO>(products.size());
        for (Product p : products) {
            data.add(toVO(p));
        }
        return Result.ok(data);
    }

    /** C 端商品详情。下架与不存在返回同一句提示，避免被探测 */
    public Result<MallProductVO> detail(Long id) {
        Product product = productMapper.selectByIdAndOnSale(id);
        if (product == null) {
            return Result.fail("商品不存在或已下架");
        }
        return Result.ok(toVO(product));
    }

    /** 白名单解析。非法值一律回落默认，绝不把原文往下传 */
    private String[] resolveSort(String sort) {
        String[] found = SORT_WHITELIST.get(sort);
        if (found == null) {
            return new String[]{DEFAULT_COLUMN, DEFAULT_DIRECTION};
        }
        return found;
    }

    private MallProductVO toVO(Product p) {
        MallProductVO vo = new MallProductVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setMainImage(p.getMainImage());
        vo.setPrice(p.getPrice());
        vo.setMarketPrice(p.getMarketPrice());
        vo.setSales(p.getSales());
        vo.setStatus(p.getStatus());
        return vo;
    }
}
