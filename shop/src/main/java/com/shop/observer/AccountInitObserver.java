package com.shop.observer;

import com.shop.account.AccountStoreResolver;
import com.shop.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountInitObserver {

    /** ★ 改造点：默认头像常量搬去了各自的 AccountStore，这里不再需要 */
    private final AccountStoreResolver accountStoreResolver;

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        accountStoreResolver.of(event.getUserType())
                .initProfile(event.getUserId());

        log.info("【观察者-账号初始化】已为 {}（{}）初始化头像与档案",
                event.getUsername(), event.getUserType());
    }
}
