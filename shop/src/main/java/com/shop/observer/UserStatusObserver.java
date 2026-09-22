package com.shop.observer;

import com.shop.account.AccountStoreResolver;
import com.shop.event.UserLoginEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserStatusObserver {

    /** ★ 改造点：原来注入 EmployeeMapper（写死了员工），现在注入解析器 —— 观察者不再关心是哪张表 */
    private final AccountStoreResolver accountStoreResolver;

    @EventListener
    public void onUserLogin(UserLoginEvent event) {
        accountStoreResolver.of(event.getUserType())
                .touchLogin(event.getUserId(), event.getLoginTime());

        log.info("【观察者-用户状态】已更新 {}（{}）的最后登录时间与累计登录次数",
                event.getUsername(), event.getUserType());
    }
}
