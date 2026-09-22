package com.shop.template;

import com.shop.common.BizException;
import com.shop.dto.ProductDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 实物商品发布实现。
 * ★ 注意这个类有多短：7 个步骤里只写了 1 个（auditContent），
 *   参数校验、类目校验、价格校验、保存、上架、后置处理全都是父类给的。
 *   这就是模板方法的价值 —— 子类只描述"我和别人不一样的地方"。
 *
 * ★ @Service("physicalProductPublish") 里的名字，就是工厂 Map 的 key（见 ProductPublishService）。
 */
@Slf4j
@Service("physicalProductPublish")
public class PhysicalProductPublishService extends AbstractProductPublishTemplate {

    /** ★ 实物商品的钩子：只做一件事 —— 验库存 */
    @Override
    protected void auditContent(ProductDTO dto) {
        Integer stock = dto.getStock();
        if (stock == null || stock <= 0) {
            throw new BizException("实物商品库存必须大于 0（当前：" + stock + "）");
        }
        Integer warn = dto.getStockWarn();
        if (warn != null && warn > stock) {
            throw new BizException("库存预警值不能大于库存（预警 " + warn + " > 库存 " + stock + "）");
        }
        log.info("【模板方法·实物】④ 库存校验通过：stock={}, stockWarn={}", stock, warn);
    }
}
