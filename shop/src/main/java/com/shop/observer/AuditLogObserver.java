package com.shop.observer;

import com.shop.entity.Loginlog;
import com.shop.event.UserLoginEvent;
import com.shop.mapper.LoginLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogObserver {

    private final LoginLogMapper loginLogMapper;

    @EventListener
    public void onUserLogin(UserLoginEvent event) {
        Loginlog record = new Loginlog();
        record.setUserId(event.getUserId());
        record.setLoginIp(event.getIp());
        record.setLoginDevice(event.getDevice());
        record.setLoginLocation("内网");        // 教学项目简化，不接 IP 归属地 API
        record.setLoginTime(event.getLoginTime());
        record.setUserAgent(event.getDevice());
        record.setStatus(1);
        record.setUserType(event.getUserType());     // ★★ 阶段 5 唯一的新增行：区分前后台登录
        loginLogMapper.insert(record);

        log.info("【观察者-审计日志】{}（{}）登录成功，IP={}",
                event.getUsername(), event.getUserType(), event.getIp());
    }
}
