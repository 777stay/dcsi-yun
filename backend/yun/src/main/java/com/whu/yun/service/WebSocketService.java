package com.whu.yun.service;

import com.whu.yun.entity.Dto;

import java.util.Map;

public interface WebSocketService {
    /**
     * 广播所有机器人的完整状态列表。
     * 用于 /ws/status 频道。
     * @param statuses A map of all robot statuses.
     */
    void broadcastStatusUpdate(Map<String, Dto.RobotStatusDto> statuses);

    /**
     * 广播单个数据包到订阅了特定机器人的客户端。
     * 用于 /ws/data/{robotId} 频道。
     * @param packet The data packet to broadcast.
     */
    void broadcastDataPacket(Dto.FrontendPacketDto<?> packet);
}
