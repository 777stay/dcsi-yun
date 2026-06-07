
package com.whu.yun.controller;

import com.whu.yun.dto.ApiResponse;
import com.whu.yun.entity.SessionInfo;
import com.whu.yun.service.MyBatisDataFlowControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 负责全局数据流控制的控制器。
 * 支持MyBatis版本的会话时间区间记录
 */
@Slf4j
@RestController
@RequestMapping("/api/data-flow")
@RequiredArgsConstructor
@CrossOrigin
public class DataFlowController1 {

    private final MyBatisDataFlowControlService dataFlowControlService;

    /**
     * 【修改】启用数据处理，记录会话开始时间到数据库，并增加计数，返回最新的总次数。
     */
    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enableProcessing() {
        try {
            // 启用处理（内部会自动增加计数并记录会话开始时间）
            dataFlowControlService.enableProcessing();

            // 获取当前计数
            Long newCount = dataFlowControlService.getCurrentCount();

            // 获取当前活跃会话信息
            SessionInfo currentSession = dataFlowControlService.getCurrentActiveSession();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "数据接收已启用");
            response.put("receptionCount", newCount);

            // 添加会话信息
            if (currentSession != null) {
                response.put("sessionInfo", createSessionInfoMap(currentSession));
                response.put("detailedMessage", String.format("数据接收已启用，会话 #%d 开始于 %s",
                        currentSession.getSessionCount(),
                        currentSession.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            }

            log.info("数据处理已启用，当前计数: {}, 会话信息: {}", newCount, currentSession);
            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("启用数据处理失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "启用失败: " + e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(201, errorResponse.toString()));
        }
    }

    /**
     * 【修改】禁用数据处理，记录会话结束时间到数据库。
     */
    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Map<String, Object>>> disableProcessing() {
        try {
            // 获取禁用前的会话信息
            SessionInfo beforeDisable = dataFlowControlService.getCurrentActiveSession();

            // 禁用处理（内部会自动记录会话结束时间）
            dataFlowControlService.disableProcessing();

            Long currentCount = dataFlowControlService.getCurrentCount();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "数据接收已禁用");
            response.put("receptionCount", currentCount);

            // 添加已完成的会话信息
            if (beforeDisable != null) {
                response.put("completedSessionInfo", createSessionInfoMap(beforeDisable));
                response.put("detailedMessage", String.format("数据接收已禁用，会话 #%d 已完成",
                        beforeDisable.getSessionCount()));
            }

            log.info("数据处理已禁用，当前计数: {}, 已完成会话: {}", currentCount, beforeDisable);
            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("禁用数据处理失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "禁用失败: " + e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(201, errorResponse.toString()));
        }
    }

    /**
     * 【增强】获取当前数据流的开关状态、接收总次数和会话信息。
     * 这个接口用于前端页面加载时初始化显示。
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        try {
            boolean isEnabled = dataFlowControlService.isProcessingEnabled();
            Long currentCount = dataFlowControlService.getCurrentCount();
            SessionInfo currentSession = dataFlowControlService.getCurrentActiveSession();

            Map<String, Object> status = new HashMap<>();
            status.put("isEnabled", isEnabled);
            status.put("receptionCount", currentCount);

            // 添加当前会话信息
            if (currentSession != null) {
                status.put("currentSession", createSessionInfoMap(currentSession));
                status.put("statusMessage", String.format("当前处于第 %d 次接收中，开始于 %s",
                        currentSession.getSessionCount(),
                        currentSession.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            } else {
                status.put("currentSession", null);
                status.put("statusMessage", isEnabled ? "数据接收已启用，等待会话开始" : "数据接收已禁用");
            }

            return ResponseEntity.ok(ApiResponse.success(status));

        } catch (Exception e) {
            log.error("获取状态失败", e);
            Map<String, Object> errorStatus = new HashMap<>();
            errorStatus.put("isEnabled", false);
            errorStatus.put("receptionCount", 0L);
            errorStatus.put("error", "状态获取失败: " + e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(201, errorStatus.toString()));
        }
    }

    /**
     * 【新增】获取指定会话的详细信息
     */
    @GetMapping("/session/{sessionCount}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSessionInfo(@PathVariable Long sessionCount) {
        try {
            SessionInfo session = dataFlowControlService.getSessionByCount(sessionCount);

            Map<String, Object> response = new HashMap<>();
            if (session != null) {
                response.put("sessionInfo", createSessionInfoMap(session));
                response.put("message", "会话信息获取成功");
            } else {
                response.put("sessionInfo", null);
                response.put("message", "未找到指定会话");
            }

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("获取会话信息失败，会话次数: {}", sessionCount, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "获取会话信息失败: " + e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(201, errorResponse.toString()));
        }
    }

    /**
     * 【新增】手动同步数据库计数（管理功能）
     */
    @PostMapping("/sync-count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncCount() {
        try {
            // 重新初始化，从数据库同步计数
            dataFlowControlService.init();

            Long currentCount = dataFlowControlService.getCurrentCount();
            SessionInfo currentSession = dataFlowControlService.getCurrentActiveSession();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "计数同步成功");
            response.put("receptionCount", currentCount);
            if (currentSession != null) {
                response.put("currentSession", createSessionInfoMap(currentSession));
            }

            log.info("计数同步完成，当前计数: {}", currentCount);
            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("同步计数失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "同步失败: " + e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(201, errorResponse.toString()));
        }
    }

    /**
     * 【新增】健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "MyBatisDataFlowControlService");
        health.put("timestamp", System.currentTimeMillis());

        try {
            // 简单的服务可用性检查
            dataFlowControlService.getCurrentCount();
            health.put("database", "UP");
        } catch (Exception e) {
            health.put("database", "DOWN");
            health.put("error", e.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.success(health));
    }

    /**
     * 创建会话信息Map（用于API响应）
     */
    private Map<String, Object> createSessionInfoMap(SessionInfo session) {
        if (session == null) return null;

        Map<String, Object> sessionMap = new HashMap<>();
        sessionMap.put("id", session.getId());
        sessionMap.put("sessionCount", session.getSessionCount());
        sessionMap.put("startTime", session.getStartTime());
        sessionMap.put("endTime", session.getEndTime());
        sessionMap.put("status", session.getStatus().toString());
        sessionMap.put("timeRangeDisplay", session.getTimeRangeDisplay());
        sessionMap.put("createdAt", session.getCreatedAt());
        sessionMap.put("updatedAt", session.getUpdatedAt());

        return sessionMap;
    }
}
