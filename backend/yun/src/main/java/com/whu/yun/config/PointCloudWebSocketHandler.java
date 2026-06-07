package com.whu.yun.config;

import com.example.robotbackend.comm.CommBaseProto;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PointCloudWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = Logger.getLogger(PointCloudWebSocketHandler.class.getName());

    // 存储所有在线的WebSocket会话
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        System.out.println("新的WebSocket连接已建立: " + sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 处理来自前端的消息（如开始/停止接收点云）
        String payload = message.getPayload();
        System.out.println("收到来自客户端的消息: " + payload);

        // 示例：根据消息内容控制数据推送
        if ("start".equalsIgnoreCase(payload)) {
            // 开始推送点云数据（逻辑在其他地方实现）
        } else if ("stop".equalsIgnoreCase(payload)) {
            // 停止推送点云数据
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        System.out.println("WebSocket连接已关闭: " + session.getId() + ", 状态: " + status);
    }

    /**
     * 发送点云数据到所有连接的客户端
     */
    private final Object sendLock = new Object(); // 全局锁对象

    public void sendPointCloudData(String jsonData) {
        if (sessions.isEmpty()) {
            System.out.println("没有在线的WebSocket客户端，跳过推送");
            return;
        }

        // 创建会话副本避免并发修改问题
        Collection<WebSocketSession> sessionCopy;
        synchronized (sessions) {  // 同步sessions集合
            sessionCopy = new ArrayList<>(sessions.values());
        }

        for (WebSocketSession session : sessionCopy) {
            try {
                // 对每个会话的发送操作加锁
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(jsonData));
                    }
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "发送数据到WebSocket客户端时出错", e);
                // 可选：关闭无效连接并从sessions中移除
            }
        }
    }

    /**
     * 将点云数据转换为JSON格式
     */
    private String convertPointCloudToJson(CommBaseProto.PointCloudXYZI pointCloud) throws IOException {
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", pointCloud.getSessionId());
        data.put("timestamp", System.currentTimeMillis());

        // 转换点云数据（为避免大量数据，可选择性发送或分批发送）
        int maxPointsToSend = Math.min(1000, pointCloud.getPointsCount()); // 限制每次发送的点数
        double[][] points = new double[maxPointsToSend][4];

        for (int i = 0; i < maxPointsToSend; i++) {
            CommBaseProto.PointXYZI point = pointCloud.getPoints(i);
            points[i] = new double[]{point.getX(), point.getY(), point.getZ(), point.getIntensity()};
        }

        data.put("points", points);
        return objectMapper.writeValueAsString(data);
    }
}