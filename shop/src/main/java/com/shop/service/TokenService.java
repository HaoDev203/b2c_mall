package com.shop.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.dto.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 令牌服务：登录签发、每次请求校验、退出销毁。
 * 存储用 Redis（不透明令牌），这样能主动失效——JWT 做不到。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final long TTL_MINUTES         = 30;   // 令牌有效期（分钟）
    private static final long RENEW_THRESHOLD_MIN = 10;   // 剩余不足 10 分钟则自动续期

    @Value("${app.token.key-prefix:shop:token:}")
    private String keyPrefix;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    /** 登录成功后签发令牌 */
    public String create(LoginUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            redisTemplate.opsForValue().set(keyPrefix + token,
                    objectMapper.writeValueAsString(user), TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("令牌序列化失败", e);
        }
        log.info("【TokenService】签发令牌 {} -> 用户 {}（有效期 {} 分钟）",
                mask(token), user.getUsername(), TTL_MINUTES);
        return token;
    }

    /** 校验令牌：有效返回用户信息，无效返回 null */
    public LoginUser get(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String key  = keyPrefix + token;
        String json = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        // 滑动续期：快过期了且用户还在用，就再续 30 分钟
        Long ttl = redisTemplate.getExpire(key, TimeUnit.MINUTES);
        if (ttl != null && ttl >= 0 && ttl < RENEW_THRESHOLD_MIN) {
            redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);
        }
        try {
            return objectMapper.readValue(json, LoginUser.class);
        } catch (JsonProcessingException e) {
            log.warn("【TokenService】令牌内容解析失败 token={}", mask(token), e);
            return null;
        }
    }

    /** 退出登录：立刻让令牌失效 */
    public void remove(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        redisTemplate.delete(keyPrefix + token);
        log.info("【TokenService】已注销令牌 {}", mask(token));
    }

    /** 日志里不打完整令牌 */
    private String mask(String token) {
        return (token == null || token.length() < 8) ? "***" : token.substring(0, 8) + "***";
    }
}
