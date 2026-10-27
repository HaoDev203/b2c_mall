package com.mall.tdd.service;

import com.mall.tdd.common.Result;
import com.mall.tdd.common.UserContext;
import com.mall.tdd.dto.AdminProductVO;
import com.mall.tdd.dto.BatchItemResultVO;
import com.mall.tdd.dto.BatchResultVO;
import com.mall.tdd.dto.ProductBatchCommand;
import com.mall.tdd.dto.ProductPageVO;
import com.mall.tdd.dto.ProductQuery;
import com.mall.tdd.entity.Product;
import com.mall.tdd.mapper.CategoryMapper;
import com.mall.tdd.mapper.OrderItemMapper;
import com.mall.tdd.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 后台店铺商品管理：列表筛选 / 删除 / 批量设置（P01~P21）。
 *
 * <p>权限模型：<b>只操作本店商品</b>。{@code shopId} 一律从
 * {@link UserContext} 取，绝不从请求参数取 —— 否则改个 {@code ?shopId=} 就能
 * 看别家的商品、甚至批量改别人的价格。
 *
 * <p>隔离的落点有三处，缺一不可：
 * <ol>
 *   <li>列表/总数的 SQL 都有 {@code WHERE shop_id = #{shopId}}；</li>
 *   <li>删除与每个批量更新的 SQL 都带 {@code AND shop_id = #{shopId}}，
 *       越权时影响 0 行，而不是「先查出来再比」；</li>
 *   <li>删除前先 {@code countByIdAndShop} 预检，别家的商品连订单引用都不去查。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ProductQueryService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    /** 上限防止 {@code ?size=999999} 把整表捞出来 */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 合法的状态取值。
     *
     * <p>★ 只白名单<b>状态</b>，不白名单类型/类别/配送方式，理由是需求原文的措辞：
     * <ul>
     *   <li>状态：「上架、下架」—— 封闭的两项，写死才对；</li>
     *   <li>类型：「普通商品、秒杀商品<b>等</b>」、配送方式：「同城配送、用户自提、快递配送<b>等</b>」
     *       —— 带「等」字说明枚举是开放的，写死白名单会把以后新增的类型挡在门外。</li>
     * </ul>
     *
     * <p>非法状态必须报错而不是静默忽略：静默忽略会让用户以为筛选生效了，
     * 实际看到的还是全表（P04b）。
     */
    private static final Set<String> ALLOWED_STATUS =
            new HashSet<String>(Arrays.asList("on_sale", "off_sale"));

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final OrderItemMapper orderItemMapper;

    // ==================================================================
    // 列表
    // ==================================================================

    /**
     * 后台商品列表（P01~P12）。
     *
     * @param query 6 个筛选条件，可为 null（等价于不筛）
     * @param page  页码，从 1 开始；null / 小于 1 当 1
     * @param size  页大小；null / 小于 1 取 10，超过 100 压到 100
     */
    public Result<ProductPageVO> listProducts(ProductQuery query, Integer page, Integer size) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.unauthorized("请先登录");
        }

        ProductQuery q = query == null ? new ProductQuery() : query;
        String status = trimToNull(q.getStatus());
        if (status != null && !ALLOWED_STATUS.contains(status)) {
            return Result.fail("状态取值非法：" + status);
        }

        String keyword = trimToNull(q.getKeyword());
        String goodsType = trimToNull(q.getGoodsType());
        String categoryKind = trimToNull(q.getCategoryKind());
        // ★ 空集合转 null：原样传下去 <if> 会成立，SQL 拼出空的 foreach → 语法错误，整页 500
        List<String> deliveryTypes = emptyToNull(q.getDeliveryTypes());
        Long categoryId = q.getCategoryId();

        int pageSize = normalizePageSize(size);
        int offset = (normalizePage(page) - 1) * pageSize;

        List<Product> rows = productMapper.selectAdminPage(shopId, keyword, goodsType,
                categoryKind, status, deliveryTypes, categoryId, pageSize, offset);

        ProductPageVO data = new ProductPageVO();
        data.setList(toVOs(rows));
        // 总数与列表同口径：同一组条件、同一个 shopId，否则「共 N 条」和翻页对不上
        data.setTotal(productMapper.countAdminPage(shopId, keyword, goodsType,
                categoryKind, status, deliveryTypes, categoryId));
        return Result.ok(data);
    }

    // ==================================================================
    // 删除
    // ==================================================================

    /**
     * 删除商品（P13~P15）。
     *
     * <p>三步顺序不能动：
     * <ol>
     *   <li><b>先确认是本店商品</b> —— 别家的商品连订单引用都不查。
     *       反过来先查引用会得到「已产生订单」这种错因，还顺带泄露了别家商品的销售情况；</li>
     *   <li><b>再查订单引用</b> —— 有引用直接拒，一行都不删；</li>
     *   <li><b>最后删</b> —— WHERE 仍带 shop_id，预检到删除之间若被转走就影响 0 行。</li>
     * </ol>
     *
     * <p>★ 顺序反了数据就没了：先删后查，查出来有引用也补不回来。
     */
    public Result<Void> deleteProduct(Long productId) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.unauthorized("请先登录");
        }
        if (productId == null) {
            return Result.fail("商品 id 不能为空");
        }

        if (productMapper.countByIdAndShop(productId, shopId) == 0) {
            return Result.fail("商品不存在或无权操作");
        }

        // ★ 需求原文：「删除商品，弹出二次确认弹窗；已产生订单的商品禁止删除」
        //   弹窗是前端的事，「禁止」必须由后端守 —— 前端能绕过。
        if (orderItemMapper.countByProductId(productId) > 0) {
            return Result.fail("商品已产生订单，禁止删除");
        }

        if (productMapper.deleteByIdAndShop(productId, shopId) == 0) {
            // 预检到这里之间被并发删掉 / 店铺被转走。如实失败，不假装成功。
            return Result.fail("商品不存在或已被删除");
        }
        return Result.ok(null);
    }

    // ==================================================================
    // 批量设置
    // ==================================================================

    /**
     * 批量设置状态 / 配送方式 / 分类 / 排序 / 库存（P16~P21）。
     *
     * <p>★★ <b>不加 {@code @Transactional}</b>，逐条 try/catch，一条失败不回滚其余。
     * 整体回滚意味着「3 个里 1 个越权，另外 2 个也不改了」——
     * 商家点一次什么都没发生，还不知道为什么。这是本项目「事务边界」的关键取舍。
     *
     * <p>★ 参数校验放在循环<b>之前</b>：能整批拒绝的（状态非法、分类不存在、
     * 什么都没传）就别进去跑一遍再逐条报错 —— 那样会得到 N 条一模一样的失败明细。
     */
    public Result<BatchResultVO> batchUpdate(ProductBatchCommand cmd) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.unauthorized("请先登录");
        }
        if (cmd == null || cmd.getIds() == null || cmd.getIds().isEmpty()) {
            return Result.fail("请先选择要修改的商品");
        }

        String status = trimToNull(cmd.getStatus());
        // ★ deliveryTypes 用 != null 判断，不用 isEmpty：空集合是「清空配送方式」，
        //   本身就是一个有效的修改项
        boolean hasDelivery = cmd.getDeliveryTypes() != null;
        boolean hasCategory = cmd.getCategoryId() != null;
        boolean hasSort = cmd.getSort() != null;
        boolean hasWarehouse = cmd.getWarehouseStock() != null;
        boolean hasStore = cmd.getStoreStock() != null;

        if (status == null && !hasDelivery && !hasCategory && !hasSort
                && !hasWarehouse && !hasStore) {
            return Result.fail("没有需要修改的内容");
        }
        if (status != null && !ALLOWED_STATUS.contains(status)) {
            return Result.fail("状态取值非法：" + status);
        }
        if (hasCategory && categoryMapper.countById(cmd.getCategoryId()) == 0) {
            // 整批拒绝：指到不存在的分类，改完商品就从所有分类筛选里消失了
            return Result.fail("分类不存在：" + cmd.getCategoryId());
        }

        String deliveryValue = hasDelivery ? joinDeliveryTypes(cmd.getDeliveryTypes()) : null;

        List<BatchItemResultVO> results = new ArrayList<BatchItemResultVO>(cmd.getIds().size());
        int success = 0;
        for (Long id : cmd.getIds()) {
            BatchItemResultVO item = applyOne(id, shopId, status, hasDelivery, deliveryValue,
                    hasCategory, cmd.getCategoryId(), hasSort, cmd.getSort(),
                    hasWarehouse, cmd.getWarehouseStock(), hasStore, cmd.getStoreStock());
            results.add(item);
            if (item.isSuccess()) {
                success++;
            }
        }

        BatchResultVO data = new BatchResultVO();
        data.setTotal(Integer.valueOf(cmd.getIds().size()));
        data.setSuccess(Integer.valueOf(success));
        data.setFailed(Integer.valueOf(cmd.getIds().size() - success));
        data.setResults(results);
        // 部分失败也返回 200：前端靠 failed + results 告诉商家「哪几条没改」。
        // 返回 500 只会让商家整批重来，把已成功的又改一遍。
        return Result.ok(data);
    }

    /**
     * 改一条商品。所有修改都靠 SQL 里的 {@code AND shop_id = ?} 兜底，
     * 影响 0 行 = 越权或已不存在。
     */
    private BatchItemResultVO applyOne(Long id, Long shopId, String status,
                                       boolean hasDelivery, String deliveryValue,
                                       boolean hasCategory, Long categoryId,
                                       boolean hasSort, Integer sort,
                                       boolean hasWarehouse, Integer warehouseStock,
                                       boolean hasStore, Integer storeStock) {
        BatchItemResultVO item = new BatchItemResultVO();
        item.setId(id);
        try {
            boolean miss = false;
            // 注意不能写成 `miss || mapper.xxx(...) == 0`：|| 会短路，
            // 前一项为 true 时后面的 update 就不执行了，等于默默跳过。
            if (status != null && productMapper.updateStatus(id, shopId, status) == 0) {
                miss = true;
            }
            if (hasDelivery && productMapper.updateDeliveryTypes(id, shopId, deliveryValue) == 0) {
                miss = true;
            }
            if (hasCategory && productMapper.updateCategoryId(id, shopId, categoryId) == 0) {
                miss = true;
            }
            if (hasSort && productMapper.updateSort(id, shopId, sort) == 0) {
                miss = true;
            }
            // 两个库存字段走同一条 UPDATE，各自由 <if> 决定是否更新
            if ((hasWarehouse || hasStore)
                    && productMapper.updateStock(id, shopId, warehouseStock, storeStock) == 0) {
                miss = true;
            }

            if (miss) {
                item.setSuccess(false);
                item.setMessage("商品不存在或无权操作");
            } else {
                item.setSuccess(true);
            }
        } catch (RuntimeException e) {
            // ★ 批量接口不许把异常抛出去，否则前端只看到 500，
            //   不知道哪几条成功了、该重试哪几条。
            item.setSuccess(false);
            String msg = e.getMessage();
            item.setMessage(msg == null || msg.isEmpty() ? "更新失败" : msg);
        }
        return item;
    }

    // ==================================================================
    // 装配与工具
    // ==================================================================

    private List<AdminProductVO> toVOs(List<Product> rows) {
        List<AdminProductVO> list = new ArrayList<AdminProductVO>(rows.size());
        for (Product p : rows) {
            list.add(toVO(p));
        }
        return list;
    }

    private AdminProductVO toVO(Product p) {
        AdminProductVO vo = new AdminProductVO();
        vo.setId(p.getId());
        vo.setMainImage(p.getMainImage());
        vo.setName(p.getName());
        vo.setMarketPrice(p.getMarketPrice());
        vo.setPrice(p.getPrice());
        // ★ 真实销量与注水销量分别映射，绝不合并（P11）
        vo.setSales(p.getSales());
        vo.setInflatedSales(p.getInflatedSales());
        // ★ 两个库存同样独立（P12）
        vo.setWarehouseStock(p.getWarehouseStock());
        vo.setStoreStock(p.getStoreStock());
        vo.setGoodsType(p.getGoodsType());
        vo.setCategoryKind(p.getCategoryKind());
        vo.setStatus(p.getStatus());
        vo.setDeliveryTypes(p.getDeliveryTypes());
        vo.setSort(p.getSort());
        vo.setProductCode(p.getProductCode());
        return vo;
    }

    /** 多选配送方式序列化成逗号分隔列。空集合 → 空串（= 清空） */
    private String joinDeliveryTypes(List<String> types) {
        StringBuilder sb = new StringBuilder();
        for (String t : types) {
            String v = trimToNull(t);
            if (v == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(v);
        }
        return sb.toString();
    }

    private int normalizePage(Integer page) {
        return page == null || page.intValue() < 1 ? 1 : page.intValue();
    }

    private int normalizePageSize(Integer size) {
        if (size == null || size.intValue() < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size.intValue(), MAX_PAGE_SIZE);
    }

    private List<String> emptyToNull(List<String> list) {
        return list == null || list.isEmpty() ? null : list;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
