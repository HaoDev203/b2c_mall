package com.shop.template;

import com.shop.common.BizException;
import com.shop.dto.ProductDTO;
import com.shop.entity.Category;
import com.shop.entity.Product;
import com.shop.mapper.CategoryMapper;
import com.shop.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 商品发布模板 —— 抽象类，定义固定流程骨架。
 *
 * ★ 好莱坞原则："别调用我们，我们会调用你（Don't call us, we'll call you）"
 *   子类从不主动调用父类的任何方法；
 *   是父类的 publish() 在合适的时机"回调"子类的 auditContent / afterProcess。
 */
@Slf4j
public abstract class AbstractProductPublishTemplate {

    protected static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 支持的商品类型白名单 */
    private static final List<String> SUPPORTED_TYPES =
            Arrays.asList("physical", "virtual", "combo", "card");

    /**
     * ★ 这里故意用 @Autowired 字段注入，而不是 @RequiredArgsConstructor 构造器注入。
     *   原因：子类若用构造器注入，每个子类都必须手写一遍 super(productMapper, categoryMapper)；
     *   一旦父类多加一个依赖，所有子类都要改 —— 那模板方法的"骨架收敛"就白搭了。
     *   字段注入让子类只需关心自己的钩子。
     */
    @Autowired
    protected ProductMapper productMapper;

    @Autowired
    protected CategoryMapper categoryMapper;

    // ==================== ★★★ 模板方法 ====================
    /**
     * 商品发布骨架：7 个步骤的顺序在父类里一次定死。
     * 子类只能填第 4 步（auditContent）和第 7 步（afterProcess），其余 5 步都改不了。
     *
     * ★★ final 是模板方法模式的命门：
     *    不写 final，子类一旦重写 publish()，整个骨架就被绕过去了，这个模式等于没设计。
     *    需求文档「避坑清单」第 2 条专门点了这一条。
     */
    public final Product publish(ProductDTO dto, Long shopId) {
        log.info("========== 【模板方法】开始发布商品：{}，类型={} ==========", dto.getName(), dto.getProductType());

        validateParams(dto);                // 1. 参数校验（通用）
        validateCategory(dto);              // 2. 类目归属校验（通用）
        validatePriceAndStock(dto);         // 3. 价格库存校验（通用）
        auditContent(dto);                  // 4. 内容合规审核     ← ★ 抽象钩子，子类实现
        Product saved = save(dto, shopId);  // 5. 保存商品信息（通用）
        onSale(saved);                      // 6. 上架（通用）
        afterProcess(saved);                // 7. 后置处理         ← ★ 可选钩子，父类空实现

        log.info("========== 【模板方法】发布完成：id={}, status={} ==========", saved.getId(), saved.getStatus());
        return saved;
    }

    // ==================== 钩子方法 ====================

    /**
     * ★ 抽象钩子：必须由子类实现。
     *   实物商品 → 校验库存；虚拟商品 → 校验合规（卡券编号 + 有效期）。
     */
    protected abstract void auditContent(ProductDTO dto);

    /**
     * ★ 可选钩子：父类默认什么都不做，子类想扩展才覆盖。
     *   这正是「钩子方法（hook method）」的经典用法 —— 预留扩展点，但不强迫子类实现。
     */
    protected void afterProcess(Product product) {
        // 默认空实现，故意留空
    }

    // ==================== 通用步骤（private：子类看不到，也就改不了）====================

    /** 1. 参数校验（通用） */
    private void validateParams(ProductDTO dto) {
        if (!StringUtils.hasText(dto.getName())) {
            throw new BizException("商品名称不能为空");
        }
        if (!StringUtils.hasText(dto.getProductType())) {
            throw new BizException("商品类型（productType）不能为空");
        }
        String type = dto.getProductType().trim().toLowerCase();
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new BizException("不支持的商品类型：" + dto.getProductType()
                    + "（支持 physical / virtual / combo / card）");
        }
        dto.setProductType(type);   // 统一转小写，后面工厂分发才不会 miss
        log.info("【模板方法】① 参数校验通过");
    }

    /** 2. 类目归属校验（通用） */
    private void validateCategory(ProductDTO dto) {
        if (dto.getCategoryId() == null) {
            throw new BizException("必须选择平台类目（categoryId）");
        }
        Category category = categoryMapper.selectById(dto.getCategoryId());
        if (category == null) {
            throw new BizException("类目不存在：categoryId=" + dto.getCategoryId());
        }
        // 二级类目必须能挂到一级类目上
        if (category.getLevel() != null && category.getLevel() == 2) {
            if (category.getParentId() == null || categoryMapper.selectById(category.getParentId()) == null) {
                throw new BizException("类目归属异常：二级类目「" + category.getName() + "」找不到上级类目");
            }
        }
        log.info("【模板方法】② 类目校验通过：{}（level={}）", category.getName(), category.getLevel());
    }

    /** 3. 价格库存校验（通用） */
    private void validatePriceAndStock(ProductDTO dto) {
        if (dto.getPrice() == null || dto.getPrice() <= 0) {
            throw new BizException("销售价必须大于 0");
        }
        if (dto.getMarketPrice() != null && dto.getMarketPrice() < dto.getPrice()) {
            throw new BizException("市场价不能低于销售价");
        }
        // ★ 这里只校验"不能为负"，**不**校验"必须大于 0"。
        //   因为虚拟商品没有库存，库存 0 合法；"必须 > 0" 是实物商品的专属规则，放在子类的钩子里。
        if (dto.getStock() != null && dto.getStock() < 0) {
            throw new BizException("库存不能为负数");
        }
        if (dto.getStockWarn() != null && dto.getStockWarn() < 0) {
            throw new BizException("库存预警值不能为负数");
        }
        log.info("【模板方法】③ 价格库存校验通过");
    }

    /** 5. 保存商品信息（通用） */
    private Product save(ProductDTO dto, Long shopId) {
        Product p = new Product();
        p.setShopId(shopId);
        p.setName(dto.getName().trim());
        p.setKeyword(dto.getKeyword());
        p.setSellingPoint(dto.getSellingPoint());
        p.setCategoryId(dto.getCategoryId());
        p.setShopCategoryId(dto.getShopCategoryId());
        p.setProductType(dto.getProductType());
        p.setMainImage(dto.getMainImage());
        p.setVideo(dto.getVideo());
        p.setBrand(dto.getBrand());
        p.setPrice(dto.getPrice());
        p.setMarketPrice(dto.getMarketPrice());
        p.setStock(dto.getStock() == null ? 0 : dto.getStock());
        p.setStockWarn(dto.getStockWarn() == null ? 0 : dto.getStockWarn());
        p.setProductCode(dto.getProductCode());
        p.setValidEndTime(dto.getValidEndTime());
        p.setStatus("draft");                                   // 先落库成草稿
        p.setSort(dto.getSort() == null ? 0 : dto.getSort());
        p.setCreateTime(LocalDateTime.now().format(FMT));

        productMapper.insert(p);   // ★ insert 之后 p.getId() 被回填（@Options useGeneratedKeys）
        log.info("【模板方法】⑤ 商品已保存为草稿：id={}", p.getId());
        return p;
    }

    /** 6. 上架（通用） */
    private void onSale(Product product) {
        productMapper.updateStatus(product.getId(), product.getShopId(), "on_sale");
        product.setStatus("on_sale");          // 同步内存对象，让返回值反映最终状态
        log.info("【模板方法】⑥ 商品已上架：id={}", product.getId());
    }
}
