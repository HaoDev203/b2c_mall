package com.mall.tdd.service;

import com.mall.tdd.common.Result;
import com.mall.tdd.common.UserContext;
import com.mall.tdd.common.XlsxReader;
import com.mall.tdd.common.XlsxWriter;
import com.mall.tdd.dto.AdminProductVO;
import com.mall.tdd.dto.BatchItemResultVO;
import com.mall.tdd.dto.BatchResultVO;
import com.mall.tdd.dto.ImportResultVO;
import com.mall.tdd.dto.ProductBatchCommand;
import com.mall.tdd.dto.ProductPageVO;
import com.mall.tdd.dto.ProductQuery;
import com.mall.tdd.entity.Product;
import com.mall.tdd.mapper.CategoryMapper;
import com.mall.tdd.mapper.OrderItemMapper;
import com.mall.tdd.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * 导出上限。
     *
     * <p>★ 导出<b>必须忽略分页</b>（只导当前页 10 条是这类功能最经典的 bug），
     * 但也不能真的 {@code LIMIT} 无限：一家店几十万商品时一次导出会打爆内存。
     * 10000 是「不会挡住正常商家、又不会把服务拖死」的折中，超出时表里会少数据 ——
     * 真要全量导出应该走异步任务生成文件，那是另一条需求。
     */
    public static final int EXPORT_MAX_ROWS = 10000;

    /** 导出列顺序 = 后台列表的 12 个字段 + 商品编码（顺序即文件里的列序） */
    private static final List<String> EXPORT_HEADERS = Collections.unmodifiableList(
            Arrays.asList("商品编码", "商品名称", "主图", "市场价", "销售价", "真实销量",
                    "注水销量", "仓库库存", "门店库存", "商品类型", "商品类别", "状态", "配送方式"));

    /** 导入按表头名取列，不按列序号 —— 商家插一列、调个顺序都不该读错 */
    private static final String COL_CODE = "商品编码";
    private static final String COL_CODE_ALIAS = "product_code";
    private static final String COL_NAME = "商品名称";
    private static final String COL_MAIN_IMAGE = "主图";
    private static final String COL_PRICE = "销售价";
    private static final String COL_MARKET_PRICE = "市场价";
    private static final String COL_STOCK = "总库存";
    private static final String COL_WAREHOUSE_STOCK = "仓库库存";
    private static final String COL_STORE_STOCK = "门店库存";
    private static final String COL_GOODS_TYPE = "商品类型";
    private static final String COL_CATEGORY_KIND = "商品类别";
    private static final String COL_DELIVERY_TYPES = "配送方式";
    private static final String COL_STATUS = "状态";
    private static final String COL_SORT = "排序";
    private static final String COL_INFLATED_SALES = "注水销量";

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

        ProductFilter f = normalize(query == null ? new ProductQuery() : query);
        if (f.status != null && !ALLOWED_STATUS.contains(f.status)) {
            return Result.fail("状态取值非法：" + f.status);
        }

        int pageSize = normalizePageSize(size);
        int offset = (normalizePage(page) - 1) * pageSize;

        List<Product> rows = productMapper.selectAdminPage(shopId, f.keyword, f.goodsType,
                f.categoryKind, f.status, f.deliveryTypes, f.categoryId, pageSize, offset);

        ProductPageVO data = new ProductPageVO();
        data.setList(toVOs(rows));
        // 总数与列表同口径：同一组条件、同一个 shopId，否则「共 N 条」和翻页对不上
        data.setTotal(productMapper.countAdminPage(shopId, f.keyword, f.goodsType,
                f.categoryKind, f.status, f.deliveryTypes, f.categoryId));
        return Result.ok(data);
    }

    // ==================================================================
    // 导出 / 导入（P22~P23）
    // ==================================================================

    /**
     * 导出<b>当前筛选后</b>的商品列表（P22）。
     *
     * <p>★★ 需求和实现上最容易错的一点：「当前筛选后」。最常见的 bug 是导出全表 ——
     * 商家筛出 3 个下架商品点导出，拿到 300 个商品的表，然后照着表去改，改错一片。
     *
     * <p>★ 所以这里<b>复用列表的 {@link ProductFilter} 和同一个 Mapper 方法</b>，
     * 只把分页换成 {@code (EXPORT_MAX_ROWS, 0)}：
     * 条件与列表同源，就不存在「列表改了筛选、导出忘了跟上」的漂移。
     *
     * <p>★ 状态白名单照样校验 —— 导出是绕开列表页最省事的后门，
     * 这里不守，随便传个 status 就能把全状态（含下架、草稿）的数据导走。
     */
    public Result<byte[]> exportProducts(ProductQuery query) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.unauthorized("请先登录");
        }

        ProductFilter f = normalize(query == null ? new ProductQuery() : query);
        if (f.status != null && !ALLOWED_STATUS.contains(f.status)) {
            return Result.fail("状态取值非法：" + f.status);
        }

        List<Product> rows = productMapper.selectAdminPage(shopId, f.keyword, f.goodsType,
                f.categoryKind, f.status, f.deliveryTypes, f.categoryId, EXPORT_MAX_ROWS, 0);

        List<List<Object>> data = new ArrayList<List<Object>>(rows.size());
        for (Product p : rows) {
            List<Object> line = new ArrayList<Object>(EXPORT_HEADERS.size());
            // ★ 带商品编码：列表页不展示它，但文件里必须有 ——
            //   否则「导出→改价→导入」时每一行都认不出是哪个商品，全变成新增，一店商品翻倍
            line.add(p.getProductCode());
            line.add(p.getName());
            line.add(p.getMainImage());
            line.add(p.getMarketPrice());
            line.add(p.getPrice());
            line.add(p.getSales());
            line.add(p.getInflatedSales());
            line.add(p.getWarehouseStock());
            line.add(p.getStoreStock());
            line.add(p.getGoodsType());
            line.add(p.getCategoryKind());
            line.add(p.getStatus());
            line.add(p.getDeliveryTypes());
            data.add(line);
        }
        return Result.ok(XlsxWriter.write("商品列表", EXPORT_HEADERS, data));
    }

    /**
     * 导入 Excel：按<b>商品编码</b>判定新增还是更新（P23）。
     *
     * <p>★★ 三条铁律：
     * <ol>
     *   <li><b>判定依据是 {@code product_code}，不是 id</b> —— Excel 里没有数据库 id；</li>
     *   <li><b>编码为空的行跳过并计入失败</b> —— 少了这条，每导一次就多一批
     *       没有编码的空商品，而且删都删不干净（无法定位）；</li>
     *   <li><b>{@code shop_id} 一律取登录态</b> —— Excel 里就算有 shop_id 列也根本不读。
     *       读了它，改一下表格就能往别人店里塞商品。</li>
     * </ol>
     *
     * <p>★ 逐行 try/catch，一行坏不影响其余（同批量设置的口径）：
     * 500 行的表不会因为第 37 行价格写成「¥99」而整批失败。
     *
     * <p>★ 并发下 {@code count} 与 {@code insert} 之间仍有窗口，兜底是
     * {@code uk_product_shop_code} 这个唯一索引：第二个 INSERT 会失败并计入 failed，
     * 不会真的插出两条同编码商品。
     */
    public Result<ImportResultVO> importProducts(InputStream in) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.unauthorized("请先登录");
        }
        if (in == null) {
            return Result.fail("请上传 Excel 文件");
        }

        List<Map<String, String>> rows;
        try {
            rows = XlsxReader.read(in);
        } catch (RuntimeException e) {
            // 用户传了 .xls / 图片 / 改了扩展名的文件，都可能在这里失败。
            // 与其抛 500，不如告诉他用导出的模板。
            return Result.fail("Excel 解析失败，请使用导出的模板（.xlsx）重新填写");
        }
        if (rows.isEmpty()) {
            return Result.fail("Excel 里没有数据行");
        }

        int inserted = 0;
        int updated = 0;
        int failed = 0;
        for (Map<String, String> row : rows) {
            String code = trimToNull(codeOf(row));
            if (code == null) {
                // ★ 不 trim 成空串就插，会产生一批无法定位、也无法删除的空商品
                failed++;
                continue;
            }
            try {
                Product p = toProduct(row);
                p.setShopId(shopId);        // ★ 覆盖任何来源的 shop_id
                p.setProductCode(code);
                if (productMapper.countByProductCode(shopId, code) > 0) {
                    productMapper.updateByProductCode(shopId, p);
                    updated++;
                } else {
                    productMapper.insert(p);
                    inserted++;
                }
            } catch (RuntimeException e) {
                failed++;
            }
        }

        ImportResultVO data = new ImportResultVO();
        data.setTotal(Integer.valueOf(rows.size()));
        data.setInsert(Integer.valueOf(inserted));
        data.setUpdate(Integer.valueOf(updated));
        data.setFailed(Integer.valueOf(failed));
        // 部分失败也返回 200：前端靠 failed 告诉商家「有几行没导进去」。
        // 返回 500 会让商家以为整个文件都没生效，把已成功的再导一遍 → 新增变更新，不算灾难但很乱。
        return Result.ok(data);
    }

    /** 商品编码支持中英文两种表头：模板是中文，但接口对接的人常用 product_code */
    private String codeOf(Map<String, String> row) {
        String code = row.get(COL_CODE);
        return code == null ? row.get(COL_CODE_ALIAS) : code;
    }

    /**
     * 一行 Excel → {@link Product}。
     *
     * <p>★ 所有数字都从<b>文本</b>解析：商家填模板时单元格格式千奇百怪
     * （有的存成文本、有的存成数值、有的带千分位逗号），
     * 只认某一种类型的话，同一份文件在不同人手里时好时坏。
     *
     * <p>★ 读不到的数字留 {@code null} 而不是 0：0 会让「没填」变成「库存清零」。
     */
    private Product toProduct(Map<String, String> row) {
        Product p = new Product();
        p.setName(trimToNull(row.get(COL_NAME)));
        p.setMainImage(trimToNull(row.get(COL_MAIN_IMAGE)));
        p.setPrice(toDouble(row.get(COL_PRICE)));
        p.setMarketPrice(toDouble(row.get(COL_MARKET_PRICE)));
        p.setStock(toInteger(row.get(COL_STOCK)));
        p.setWarehouseStock(toInteger(row.get(COL_WAREHOUSE_STOCK)));
        p.setStoreStock(toInteger(row.get(COL_STORE_STOCK)));
        p.setInflatedSales(toInteger(row.get(COL_INFLATED_SALES)));
        p.setGoodsType(trimToNull(row.get(COL_GOODS_TYPE)));
        p.setCategoryKind(trimToNull(row.get(COL_CATEGORY_KIND)));
        p.setDeliveryTypes(trimToNull(row.get(COL_DELIVERY_TYPES)));
        p.setStatus(trimToNull(row.get(COL_STATUS)));
        p.setSort(toInteger(row.get(COL_SORT)));
        return p;
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

    /**
     * 6 个筛选条件规范化后的形态。
     *
     * <p>★ 抽出来只为一个目的：<b>列表和导出必须用同一份条件</b>。
     * 各写一遍的话，某天列表加了个筛选、导出忘了跟，就会出现
     * 「列表显示 3 条、导出拿到 300 条」——而这种 bug 只有商家拿着文件对不上数时才会发现。
     */
    private static final class ProductFilter {
        private String keyword;
        private String goodsType;
        private String categoryKind;
        private String status;
        private List<String> deliveryTypes;
        private Long categoryId;
    }

    private ProductFilter normalize(ProductQuery q) {
        ProductFilter f = new ProductFilter();
        f.keyword = trimToNull(q.getKeyword());
        f.goodsType = trimToNull(q.getGoodsType());
        f.categoryKind = trimToNull(q.getCategoryKind());
        f.status = trimToNull(q.getStatus());
        // ★ 空集合转 null：原样传下去 <if> 会成立，SQL 拼出空的 foreach → 语法错误，整页 500
        f.deliveryTypes = emptyToNull(q.getDeliveryTypes());
        f.categoryId = q.getCategoryId();
        return f;
    }

    /** 文本转数字。解析不了返回 null —— 不兜成 0，0 会让「没填」变成「库存清零」 */
    private Double toDouble(String s) {
        String t = trimToNull(s);
        if (t == null) {
            return null;
        }
        try {
            // 顺手去掉千分位：商家从别处复制过来的「1,299」很常见
            return Double.valueOf(t.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInteger(String s) {
        Double d = toDouble(s);
        return d == null ? null : Integer.valueOf(d.intValue());
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
