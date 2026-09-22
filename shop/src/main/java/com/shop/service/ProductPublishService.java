package com.shop.service;

import com.shop.common.BizException;
import com.shop.common.Result;
import com.shop.common.UserContext;
import com.shop.dto.ProductDTO;
import com.shop.entity.Product;
import com.shop.template.AbstractProductPublishTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 商品发布入口 + 工厂分发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductPublishService {

    /**
     * ★ 简单工厂的核心就这一行。
     *   Spring 会把所有 AbstractProductPublishTemplate 的子类 Bean 自动装进这个 Map：
     *     key   = @Service("physicalProductPublish") 里的名字
     *     value = 实现类实例
     *   这就是需求「避坑清单」说的「用 Map 注入，不要写 if-else」。
     *
     *   好处：以后要支持"组合商品"，只需新增一个子类 + 这里加一个 case，
     *   模板骨架和另外两个子类都不用动。
     */
    private final Map<String, AbstractProductPublishTemplate> templates;

    public Result<Product> publish(ProductDTO dto) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return Result.fail("未获取到店铺信息");
        }
        AbstractProductPublishTemplate template = resolve(dto.getProductType());
        Product product = template.publish(dto, shopId);
        return Result.ok(product);
    }

    /** 按 productType 选择实现类 —— 需求 Step 3.4 的「工厂分发」 */
    private AbstractProductPublishTemplate resolve(String productType) {
        String type = productType == null ? "" : productType.trim().toLowerCase();
        String beanName;
        switch (type) {
            case "physical":
                beanName = "physicalProductPublish";
                break;
            case "virtual":
            case "card":          // 电子卡券复用虚拟商品那套合规校验
                beanName = "virtualProductPublish";
                break;
            case "combo":
                throw new BizException("组合商品暂未开放发布（扩展方式：新增一个 ComboProductPublishService 即可，骨架不用改）");
            default:
                throw new BizException("不支持的商品类型：" + productType);
        }
        AbstractProductPublishTemplate template = templates.get(beanName);
        if (template == null) {
            throw new BizException("未找到对应的发布实现：" + beanName);
        }
        log.info("【发布工厂】productType={} -> 分发到 {}", type, template.getClass().getSimpleName());
        return template;
    }
}
