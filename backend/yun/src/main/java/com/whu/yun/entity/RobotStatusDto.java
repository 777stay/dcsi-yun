package com.whu.yun.entity;


import com.example.robotbackend.robot_control.RobotControlProto;
import lombok.Data;

/**
 * 这个 DTO 专门用于向前端发送机器人的完整状态。
 * 它包含了 Protobuf 消息中的所有字段，并额外增加了一个 'online' 标志。
 */
@Data
public class RobotStatusDto {

    private boolean online;
    private String robotId;
    private String timestamp;
    private float cpuPercent;
    private float ramPercent;
    private String currentStatusMessage;

    /**
     * 一个静态工厂方法，用于从 Protobuf 对象和在线状态创建 DTO。
     * @param proto 从 gRPC 接收的 RobotData 对象。
     * @param isOnline 当前的在线状态。
     * @return 一个填充好数据的 RobotStatusDto 对象。
     */
    public static RobotStatusDto fromProto(RobotControlProto.RobotData proto, boolean isOnline) {
        RobotStatusDto dto = new RobotStatusDto();
        dto.setOnline(isOnline);
        dto.setRobotId(proto.getRobotId());
        dto.setTimestamp(proto.getTimestamp());
        dto.setCpuPercent(proto.getCpuPercent());
        dto.setRamPercent(proto.getRamPercent());
        dto.setCurrentStatusMessage(proto.getCurrentStatusMessage());
        return dto;
    }
}
