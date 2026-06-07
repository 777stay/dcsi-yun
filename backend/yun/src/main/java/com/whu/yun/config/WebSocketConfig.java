package com.whu.yun.config;


import com.whu.yun.handler.GlobalStatusWebSocketHandler;
import com.whu.yun.handler.MissionPlanWebSocketHandler;
import com.whu.yun.handler.RobotWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final RobotWebSocketHandler robotDataHandler; // 用于 /ws/data/{robotId}
    private final GlobalStatusWebSocketHandler globalStatusHandler; // 用于 /ws/status
    private  final MissionPlanWebSocketHandler missionPlanHandler;
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册用于详细数据流的 WebSocket
        registry.addHandler(robotDataHandler, "/ws/data/{robotId}")
                .setAllowedOrigins("*");

        // 注册用于全局状态面板的 WebSocket
        registry.addHandler(globalStatusHandler, "/ws/status")
                .setAllowedOrigins("*");
        // 注册用于任务规划结果的 WebSocket
        registry.addHandler(missionPlanHandler, "/ws/missionPlan")
                .setAllowedOrigins("*");
    }
}
