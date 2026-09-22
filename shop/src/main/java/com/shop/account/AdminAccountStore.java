package com.shop.account;

import com.shop.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 后台员工账号 —— 落在 tb_employee 表 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountStore implements AccountStore {

    private static final String DEFAULT_AVATAR = "/avatars/default.png";

    private final EmployeeMapper employeeMapper;

    @Override
    public String getType() {
        return UserType.ADMIN;
    }

    @Override
    public void touchLogin(Long userId, String loginTime) {
        employeeMapper.updateLastLogin(userId, loginTime);
        employeeMapper.increaseLoginCount(userId);
    }

    @Override
    public void initProfile(Long userId) {
        employeeMapper.updateAvatar(userId, DEFAULT_AVATAR);
    }
}
