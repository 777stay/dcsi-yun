package com.whu.yun.service;


import com.example.robotbackend.robot_control.RobotControlProto;
import com.whu.yun.entity.RobotStatusDto;
import com.whu.yun.handler.GlobalStatusWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class GlobalRobotStatusService {

    private final GlobalStatusWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    // --- [修改] --- 现在存储的是面向前端的 DTO，而不是内部的 Protobuf 对象
    private final Map<String, RobotStatusDto> robotStatuses = new ConcurrentHashMap<>();

    public GlobalRobotStatusService(@Lazy GlobalStatusWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * 当收到新的 gRPC 状态数据时，更新或创建状态，并标记为在线。
     * @param statusData 从 gRPC 接收的 Protobuf 状态数据。
     */
    public void updateRobotStatus(RobotControlProto.RobotData statusData) {
        // 将 Protobuf 数据转换为 DTO，并明确设置 online 为 true
        RobotStatusDto statusDto = RobotStatusDto.fromProto(statusData, true);
        robotStatuses.put(statusData.getRobotId(), statusDto);
        broadcastAllStatuses();
    }

    /**
     * 当 gRPC 连接断开时，将机器人标记为离线。
     * @param robotId 断开连接的机器人ID。
     */
    public void markRobotAsOffline(String robotId) {
        RobotStatusDto currentStatus = robotStatuses.get(robotId);
        if (currentStatus != null) {
            // 只修改状态，不替换整个对象
            currentStatus.setOnline(false);
            currentStatus.setCurrentStatusMessage("Offline");
            robotStatuses.put(robotId, currentStatus);
            broadcastAllStatuses();
        }
    }

    /**
     * 将所有机器人的当前状态广播给所有连接的 WebSocket 客户端。
     */
    private void broadcastAllStatuses() {
        try {
            // ObjectMapper 现在可以安全地序列化 Map<String, RobotStatusDto>
            String jsonStatuses = objectMapper.writeValueAsString(robotStatuses);
            webSocketHandler.broadcast(jsonStatuses);
        } catch (Exception e) {
            log.error("Failed to broadcast robot statuses", e);
        }
    }

    public String getAllStatusesAsJson() {
        try {
            return objectMapper.writeValueAsString(robotStatuses);
        } catch (Exception e) {
            log.error("Failed to get all statuses as JSON", e);
            return "{}";
        }
    }
}
