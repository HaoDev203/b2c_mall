package com.shop.pay;

import com.shop.common.BizException;
import com.shop.dto.PayRequest;
import com.shop.dto.PayResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ★★ 支付策略工厂（策略模式的"上下文"角色）
 *
 * ★ 需求原文要求：「用 Map 自动收集所有策略，按 payType 取用」。
 *
 * 两种收集方式对比一下（这是本项目第 3 次用这个套路了）：
 *   - 阶段 3 ProductPublishService：按 productType 分派
 *   - 阶段 5 AccountStoreResolver：注入 `List<实现>` 自己转 Map —— 因为 getType() 是自定义的
 *   这里沿用阶段 5 的写法，因为 getPayType() 也是自定义的。
 *   直接注入 `Map<String, PayStrategy>` 时 Spring 给的 key 是 **Bean 名**（alipayStrategy），
 *   虽然碰巧能对上，但**依赖 Bean 名是脆的**（改个类名就断了）。
 */
@Component
public class PayStrategyFactory {

    private final Map<String, PayStrategy> strategyMap = new HashMap<>();

    public PayStrategyFactory(List<PayStrategy> strategies) {
        for (PayStrategy s : strategies) {
            strategyMap.put(s.getPayType(), s);
        }
    }

    /** 取策略。传错 payType 直接抛异常，不要静默兜底 —— 兜底会让用户付错渠道 */
    public PayStrategy get(String payType) {
        PayStrategy strategy = strategyMap.get(payType);
        if (strategy == null) {
            throw new BizException("不支持的支付方式：" + payType);
        }
        return strategy;
    }

    /**
     * ★ 收银台用：返回全部可选支付方式（原型图 12 的四个选项）。
     *   前端不用写死列表，加一个策略这里自动多一个选项。
     */
    public Map<String, String> listAll() {
        Map<String, String> all = new LinkedHashMap<>();
        for (PayStrategy s : strategyMap.values()) {
            all.put(s.getPayType(), s.getPayName());
        }
        return all;
    }

    /** 执行支付 —— 一行搞定，**没有任何 if-else** */
    public PayResult pay(PayRequest request, String payType) {
        return get(payType).pay(request);
    }
}
