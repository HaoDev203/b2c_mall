package com.shop.service;

import com.shop.common.Result;
import com.shop.common.UserContext;
import com.shop.entity.Product;
import com.shop.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品查询 / 上下架。
 * ★ 为什么不和 ProductPublishService 合成一个类？
 *   发布走的是模板方法（写流程、有骨架），查询是普通读 —— 两件事的复杂度完全不同。
 *   分开后，读的人一眼知道该去哪个文件找。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductMapper productMapper;

    /** 后台商品管理列表：多条件筛选 + 分页（对应原型图 5） */
    public Result<List<Product>> list(String status, String productType, Long categoryId,
                                      String keyword, Integer page, Integer size) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.fail("未获取到店铺信息");
        }
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 10 : Math.min(size, MAX_PAGE_SIZE);

        List<Product> list = productMapper.selectByPage(shopId, status, productType, categoryId, keyword,
                s, (p - 1) * s);
        log.info("【商品列表】shopId={} status={} type={} keyword={} -> {} 条",
                shopId, status, productType, keyword, list.size());
        return Result.ok(list);
    }

    /** 上架 / 下架 */
    public Result<String> changeStatus(Long id, String status) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.fail("未获取到店铺信息");
        }
        if (!"on_sale".equals(status) && !"off_sale".equals(status)) {
            return Result.fail("状态只能是 on_sale（上架）或 off_sale（下架）");
        }
        int rows = productMapper.updateStatus(id, shopId, status);
        if (rows == 0) {
            return Result.fail("商品不存在或不属于当前店铺");
        }
        log.info("【商品】id={} 状态改为 {}", id, status);
        return Result.ok("on_sale".equals(status) ? "已上架" : "已下架");
    }
}
