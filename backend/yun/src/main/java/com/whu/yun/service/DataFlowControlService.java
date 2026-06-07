package com.whu.yun.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 全局服务，用于控制数据流处理的开关，并持久化统计信息。
 */
@Service
@RequiredArgsConstructor // 使用 Lombok 自动注入 final 字段
public class DataFlowControlService {

    // 注入 StringRedisTemplate 用于与 Redis 交互
    private final StringRedisTemplate redisTemplate;

    // 定义一个在 Redis 中存储计数的 Key
    private static final String DATA_RECEPTION_COUNT_KEY = "robot:data:reception:count";

    // 开关状态保留在内存中，因为它需要被高频访问，且重启后默认为开启是合理的。
    private final AtomicBoolean isProcessingEnabled = new AtomicBoolean(true);

    /**
     * 在服务启动时，确保 Redis 中的计数器有一个初始值 0 (如果它还不存在)
     */
    @PostConstruct
    public void init() {
        redisTemplate.opsForValue().setIfAbsent(DATA_RECEPTION_COUNT_KEY, "0");
    }

    public void enableProcessing() {
        isProcessingEnabled.set(true);
    }

    public void disableProcessing() {
        isProcessingEnabled.set(false);
    }

    public boolean isProcessingEnabled() {
        return isProcessingEnabled.get();
    }

    /**
     * 原子性地增加接收次数，并返回最新的总次数。
     * @return 增加后的总次数。
     */
    public Long incrementAndGetCount() {
        // 使用 Redis 的 INCR 命令，这是一个原子操作，保证了线程安全。
        return redisTemplate.opsForValue().increment(DATA_RECEPTION_COUNT_KEY);
    }

    /**
     * 获取当前的接收总次数。
     * @return 当前的总次数。
     */
    public Long getCurrentCount() {
        String countStr = redisTemplate.opsForValue().get(DATA_RECEPTION_COUNT_KEY);
        try {
            // Redis 返回的是字符串，需要转换为 Long
            return Long.parseLong(countStr);
        } catch (NumberFormatException e) {
            // 如果 Redis 中的值不是一个有效的数字（例如 key 不存在返回 null），则返回 0
            return 0L;
        }
    }
}

