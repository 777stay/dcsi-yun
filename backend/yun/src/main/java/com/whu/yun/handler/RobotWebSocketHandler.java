package com.whu.yun.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RobotWebSocketHandler extends TextWebSocketHandler {

    // 使用 ConcurrentHashMap 存储 session，Key 为 robotId，Value 为该机器人对应的所有 session
    private static final Map<String, Map<String, WebSocketSession>> sessionsByRobot = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String robotId = getRobotId(session);
        if (robotId == null) {
            session.close(CloseStatus.BAD_DATA.withReason("Robot ID is required"));
            return;
        }

        sessionsByRobot.computeIfAbsent(robotId, k -> new ConcurrentHashMap<>()).put(session.getId(), session);
        log.info("WebSocket connection opened for robot: {}, session: {}", robotId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String robotId = getRobotId(session);
        if (robotId != null && sessionsByRobot.containsKey(robotId)) {
            sessionsByRobot.get(robotId).remove(session.getId());
            if (sessionsByRobot.get(robotId).isEmpty()) {
                sessionsByRobot.remove(robotId);
            }
        }
        log.info("WebSocket connection closed for robot: {}, session: {}, status: {}", robotId, session.getId(), status);
    }

    public void broadcastToRobot(String robotId, String message) {
        if (sessionsByRobot.containsKey(robotId)) {
            sessionsByRobot.get(robotId).values().forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(message));
                    }
                } catch (IOException e) {
                    log.error("Failed to send message to session {}", session.getId(), e);
                }
            });
        }
    }

    private String getRobotId(WebSocketSession session) {
        // 从 URI 中解析 robotId, e.g., /ws/data/robot_01
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}