package com.whu.yun.controller;

import com.whu.yun.dto.ApiResponse;
import com.whu.yun.entity.SessionInfo;
import com.whu.yun.service.MyBatisDataFlowControlService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/data-flow1")
@RequiredArgsConstructor
@CrossOrigin
@Slf4j
public class DataFlowControlController {

    private final MyBatisDataFlowControlService dataFlowControlService;

    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<String>> enableProcessing() {
        try {
            dataFlowControlService.enableProcessing();
            SessionInfo currentSession = dataFlowControlService.getCurrentActiveSession();
            String message = currentSession != null 
                ? String.format("数据处理已启用，会话 #%d 开始于 %s", 
                    currentSession.getSessionCount(), 
                    currentSession.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                : "数据处理已启用";
            return ResponseEntity.ok(ApiResponse.success(message));
        } catch (Exception e) {
            log.error("启用数据处理失败", e);
            return ResponseEntity.ok(ApiResponse.error(201 ,e.getMessage()));
        }
    }

    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<String>> disableProcessing() {
        try {
            dataFlowControlService.disableProcessing();
            return ResponseEntity.ok(ApiResponse.success("数据处理已禁用"));
        } catch (Exception e) {
            log.error("禁用数据处理失败", e);
            return ResponseEntity.ok(ApiResponse.error(201, e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<DataFlowStatus>> getStatus() {
        DataFlowStatus status = new DataFlowStatus();
        status.setProcessingEnabled(dataFlowControlService.isProcessingEnabled());
        status.setCurrentCount(dataFlowControlService.getCurrentCount());
        status.setCurrentSession(dataFlowControlService.getCurrentActiveSession());
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @Data
    public static class DataFlowStatus {
        private boolean processingEnabled;
        private Long currentCount;
        private SessionInfo currentSession;
    }
}