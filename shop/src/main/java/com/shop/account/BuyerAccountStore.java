package com.shop.account;

import com.shop.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 前台买家账号 —— 落在 tb_user 表 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuyerAccountStore implements AccountStore {

    private static final String DEFAULT_AVATAR = "/avatars/default.png";

    private final UserMapper userMapper;

    @Override
    public String getType() {
        return UserType.BUYER;
    }

    @Override
    public void touchLogin(Long userId, String loginTime) {
        userMapper.updateLastLogin(userId, loginTime);
        userMapper.increaseLoginCount(userId);
    }

    @Override
    public void initProfile(Long userId) {
        userMapper.updateAvatar(userId, DEFAULT_AVATAR);
    }
}
