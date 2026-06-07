package com.whu.yun.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private static final String LOCK_PREFIX = "lock:";
    private static final Object NULL_VALUE = new Object();

    public <T> T queryWithCacheLogic(String key, Supplier<T> dbLoader, long ttlInSeconds) {
        Object cachedValue = redisTemplate.opsForValue().get(key);
        if (cachedValue != null) {
            return NULL_VALUE.equals(cachedValue) ? null : (T) cachedValue;
        }

        RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
        try {
            if (lock.tryLock()) {
                cachedValue = redisTemplate.opsForValue().get(key);
                if (cachedValue != null) {
                    return NULL_VALUE.equals(cachedValue) ? null : (T) cachedValue;
                }

                T dbValue = dbLoader.get();
                long randomTtl = ttlInSeconds + new Random().nextInt((int) (ttlInSeconds / 10) + 1);

                if (dbValue != null) {
                    redisTemplate.opsForValue().set(key, dbValue, randomTtl, TimeUnit.SECONDS);
                    return dbValue;
                } else {
                    redisTemplate.opsForValue().set(key, NULL_VALUE, 1, TimeUnit.MINUTES);
                    return null;
                }
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        
        try {
            Thread.sleep(50);
            return queryWithCacheLogic(key, dbLoader, ttlInSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }
}