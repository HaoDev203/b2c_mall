package com.shop.account;

import com.shop.common.BizException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 userType 找到对应的 AccountStore。
 *
 * ★ 这里的 Map<String, AccountStore> 是 Spring 帮我们填的：
 *   构造器参数写成 List<AccountStore>，Spring 会把容器里所有 AccountStore 实现全塞进来，
 *   我们遍历一遍转成 Map 即可。**以后新增一个实现类，这里的代码一个字都不用改。**
 *
 * ★ 这个套路和阶段 3 的 ProductPublishService（Map<String, ProductPublishTemplate>）完全一样，
 *   区别只是那次按 productType 取，这次按 userType 取。
 */
@Component
public class AccountStoreResolver {

    private final Map<String, AccountStore> storeMap = new HashMap<>();

    public AccountStoreResolver(List<AccountStore> stores) {
        for (AccountStore store : stores) {
            storeMap.put(store.getType(), store);
        }
    }

    /**
     * 取对应类型的账号仓储。
     * ★ 这里选择「抛异常」而不是「返回 null 或兜底用 admin」：
     *   万一 userType 传错了，宁可立刻炸掉，也不要悄悄地更新到错误的表里。
     */
    public AccountStore of(String userType) {
        AccountStore store = storeMap.get(userType);
        if (store == null) {
            throw new BizException("未知的用户类型：" + userType);
        }
        return store;
    }
}
