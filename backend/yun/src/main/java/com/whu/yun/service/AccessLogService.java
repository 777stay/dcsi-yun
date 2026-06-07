package com.whu.yun.service;


import com.whu.yun.entity.AccessLog;
import com.whu.yun.mapper.AccessLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogMapper accessLogMapper;

    /**
     * 使用 @Async 注解，告诉 Spring 在一个独立的线程中执行此方法。
     * 这可以确保日志记录不会阻塞主 API 请求的响应。
     */
    @Async
    public void saveLog(AccessLog log) {
        accessLogMapper.save(log);
    }
}
