package com.whu.yun.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

    /**
     * 将 Token 添加到黑名单
     * @param token 要拉黑的 JWT
     * @param durationInSeconds Token 剩余的有效时间（秒）
     */
    public void addToBlacklist(String token, long durationInSeconds) {
        String key = BLACKLIST_KEY_PREFIX + token;
        // 将 token 存入 Redis，并设置过期时间为 token 本身的剩余有效时间
        stringRedisTemplate.opsForValue().set(key, "blacklisted", durationInSeconds, TimeUnit.SECONDS);
    }

    /**
     * 检查 Token 是否在黑名单中
     * @param token 要检查的 JWT
     * @return 如果在黑名单中则返回 true
     */
    public boolean isBlacklisted(String token) {
        String key = BLACKLIST_KEY_PREFIX + token;
        return stringRedisTemplate.hasKey(key);
    }
}
