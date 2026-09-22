package com.shop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.common.Result;
import com.shop.entity.Product;
import com.shop.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 前台商品列表。
 *
 * ★ 为什么和 ProductQueryService（后台）分成两个类？
 *   后台查的是"我这个店铺的商品"（要 shopId、要令牌）；
 *   前台查的是"全平台在售商品"（无 shopId、免登录）。
 *   两者的**权限模型**和**查询条件**都不一样，合成一个类一定会写出 if-else。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MallProductService {

    private static final int  DEFAULT_SIZE  = 10;
    private static final int  MAX_SIZE      = 50;
    private static final long CACHE_SECONDS = 60;

    /**
     * ★★ 排序白名单 —— 这是「用 ${} 也安全」的唯一依据。
     *    key   = 前端传来的 sort 参数（default / sales / price）
     *    value = 写死的 SQL 排序片段
     *    任何不在 key 里的值都会走兜底，永远不可能被拼进 SQL。
     */
    private static final Map<String, String> SORT_WHITELIST;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("default", "sort DESC, id DESC");
        m.put("sales",   "sales DESC, id DESC");
        m.put("price",   "price ASC, id DESC");
        SORT_WHITELIST = Collections.unmodifiableMap(m);
    }
    private static final String DEFAULT_SORT = "sort DESC, id DESC";

    @Value("${app.cache.key-prefix:shop:cache:}")
    private String cachePrefix;

    private final ProductMapper       productMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    /** 前台商品列表（对应原型图 8） */
    public Result<Map<String, Object>> list(Long categoryId, String keyword, String sort,
                                            Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        // ★ 把前端参数翻译成白名单里的 SQL 片段。取不到就兜底 —— 用户传什么都没用。
        String sortKey = StringUtils.hasText(sort) ? sort : "default";
        String orderBy = SORT_WHITELIST.getOrDefault(sortKey, DEFAULT_SORT);

        // ★ 只缓存「第一页 + 无关键词」：
        //   关键词搜索的组合是无限的，每个组合缓存一份会把 Redis 塞满；
        //   需求原文要求的也只是"缓存首页 / 第一页的列表"。
        boolean cacheable = (p == 1) && !StringUtils.hasText(keyword);
        String key = cachePrefix + "mall:list:" + categoryId + ":" + sortKey + ":" + s;

        if (cacheable) {
            String cached = redisTemplate.opsForValue().get(key);
            if (StringUtils.hasText(cached)) {
                try {
                    Map<String, Object> data = objectMapper.readValue(
                            cached, new TypeReference<Map<String, Object>>() {});
                    log.info("【前台商品列表】命中缓存 key={} -> {} 条", key, data.get("total"));
                    return Result.ok(data);
                } catch (Exception e) {
                    log.warn("【前台商品列表】缓存解析失败，删除重建 key={}", key);
                    redisTemplate.delete(key);
                }
            }
        }

        // 回源查库
        List<Product> list = productMapper.selectOnSalePage(categoryId, keyword, orderBy, s, (p - 1) * s);
        long total = productMapper.countOnSale(categoryId, keyword);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list",    list);
        data.put("total",   total);
        data.put("page",    p);
        data.put("size",    s);
        data.put("pages",   (total + s - 1) / s);
        data.put("hasNext", (long) p * s < total);

        if (cacheable) {
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data),
                                                CACHE_SECONDS, TimeUnit.SECONDS);
                log.info("【前台商品列表】回源查库并写入缓存 key={} -> {} 条", key, list.size());
            } catch (Exception e) {
                log.warn("【前台商品列表】写缓存失败", e);
            }
        } else {
            log.info("【前台商品列表】不缓存（page={} keyword={}）-> {} 条", p, keyword, list.size());
        }
        return Result.ok(data);
    }

    /** 前台商品详情 */
    public Result<Product> detail(Long id) {
        Product product = productMapper.selectOnSaleById(id);
        if (product == null) {
            return Result.fail("商品不存在或已下架");
        }
        return Result.ok(product);
    }
}
