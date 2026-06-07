package com.whu.yun.service;

import com.whu.yun.entity.SessionInfo;
import com.whu.yun.mapper.SessionInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class MyBatisDataFlowControlService {

    private final StringRedisTemplate redisTemplate;
    private final SessionInfoMapper sessionInfoMapper;
    
    private static final String DATA_RECEPTION_COUNT_KEY = "robot:data:reception:count";
    private final AtomicBoolean isProcessingEnabled = new AtomicBoolean(true);

    @PostConstruct
    public void init() {
        syncCountFromDatabase();
    }

    private void syncCountFromDatabase() {
        try {
            Long maxCount = sessionInfoMapper.selectMaxSessionCount();
            Long currentCount = maxCount != null ? maxCount : 0L;
            redisTemplate.opsForValue().set(DATA_RECEPTION_COUNT_KEY, currentCount.toString());
            log.info("从数据库同步会话计数: {}", currentCount);
        } catch (Exception e) {
            log.error("同步数据库计数失败", e);
            redisTemplate.opsForValue().setIfAbsent(DATA_RECEPTION_COUNT_KEY, "0");
        }
    }

    /**
     * 启用处理并记录开始时间到数据库
     */
    @Transactional
    public synchronized void enableProcessing() {
        if (isProcessingEnabled.get()) {
            log.info("数据处理已经处于启用状态");
            return;
        }
        
        try {
            // 1. 增加接收次数
            Long newCount = incrementAndGetCount();
            
            // 2. 创建新会话记录并保存到数据库
            SessionInfo session = new SessionInfo();
            session.setSessionCount(newCount);
            session.setStartTime(LocalDateTime.now());
            session.setStatus(SessionInfo.SessionStatus.ACTIVE);
            
            int result = sessionInfoMapper.insertSession(session);
            if (result > 0) {
                log.info("会话已保存到数据库: ID={}, SessionCount={}, StartTime={}", 
                    session.getId(), session.getSessionCount(), session.getStartTime());
            } else {
                throw new RuntimeException("保存会话记录失败");
            }
            
            // 3. 启用处理
            isProcessingEnabled.set(true);
            
        } catch (Exception e) {
            log.error("启用数据处理失败", e);
            throw new RuntimeException("启用数据处理失败", e);
        }
    }

    /**
     * 禁用处理并记录结束时间到数据库
     */
    @Transactional
    public synchronized void disableProcessing() {
        if (!isProcessingEnabled.get()) {
            log.info("数据处理已经处于禁用状态");
            return;
        }
        
        try {
            // 1. 获取当前活跃会话
            SessionInfo activeSession = sessionInfoMapper.selectByStatus("ACTIVE");
            
            if (activeSession != null) {
                // 2. 更新会话结束时间
                activeSession.complete(LocalDateTime.now());
                
                // 3. 保存到数据库
                int result = sessionInfoMapper.updateSession(activeSession);
                if (result > 0) {
                    log.info("会话已更新到数据库: SessionCount={}, EndTime={}", 
                        activeSession.getSessionCount(), activeSession.getEndTime());
                } else {
                    throw new RuntimeException("更新会话记录失败");
                }
            }
            
            // 4. 禁用处理
            isProcessingEnabled.set(false);
            
        } catch (Exception e) {
            log.error("禁用数据处理失败", e);
            throw new RuntimeException("禁用数据处理失败", e);
        }
    }

    public boolean isProcessingEnabled() {
        return isProcessingEnabled.get();
    }

    public Long incrementAndGetCount() {
        return redisTemplate.opsForValue().increment(DATA_RECEPTION_COUNT_KEY);
    }

    public Long getCurrentCount() {
        String countStr = redisTemplate.opsForValue().get(DATA_RECEPTION_COUNT_KEY);
        try {
            return Long.parseLong(countStr);
        } catch (NumberFormatException e) {
            syncCountFromDatabase();
            countStr = redisTemplate.opsForValue().get(DATA_RECEPTION_COUNT_KEY);
            return Long.parseLong(countStr != null ? countStr : "0");
        }
    }

    /**
     * 获取当前活跃会话（直接从数据库查询）
     */
    public SessionInfo getCurrentActiveSession() {
        try {
            return sessionInfoMapper.selectByStatus("ACTIVE");
        } catch (Exception e) {
            log.error("获取当前活跃会话失败", e);
            return null;
        }
    }

    /**
     * 根据会话次数获取会话信息
     */
    public SessionInfo getSessionByCount(Long sessionCount) {
        try {
            return sessionInfoMapper.selectBySessionCount(sessionCount);
        } catch (Exception e) {
            log.error("获取会话信息失败，会话次数: {}", sessionCount, e);
            return null;
        }
    }
}