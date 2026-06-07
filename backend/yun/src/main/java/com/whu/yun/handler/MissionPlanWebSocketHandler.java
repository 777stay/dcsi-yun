package com.whu.yun.handler;

import com.google.gson.Gson;
import com.whu.yun.service.GlobalRobotStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 这个处理器专门处理 /ws/status 的全局状态 WebSocket 连接。
 */
@Slf4j
@Component
public class MissionPlanWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Gson gson;

    public MissionPlanWebSocketHandler(Gson gson) {
        this.gson = gson;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("Global status WebSocket connected: {}", session.getId());
        // 新客户端连接后，立即发送一次当前的所有状态
        HashMap<String,String> map = new HashMap<>();
        map.put("taskId","1");
        session.sendMessage(new TextMessage(gson.toJson(map)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        log.info("Global status WebSocket disconnected: {}", session.getId());
    }
    /**
     * 向所有连接的客户端广播消息。
     * @param message 要发送的 JSON 字符串。
     */
    public void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        sessions.values().forEach(session -> {
            try {
                // --- [开始修改] ---
                // 使用 synchronized 块来确保对同一个 session 的写入操作是线程安全的。
                // 这可以防止多个 gRPC 线程同时写入同一个 WebSocket 连接，从而避免 IllegalStateException。
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                    }
                }
                // --- [结束修改] ---
            } catch (IOException e) {
                log.error("Failed to broadcast status to session {}", session.getId(), e);
            }
        });
    }
}