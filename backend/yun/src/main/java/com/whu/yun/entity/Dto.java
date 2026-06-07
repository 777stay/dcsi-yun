package com.whu.yun.entity;



import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTOs for communication with the frontend.
 * 这些类对应于 Python 代码中通过 WebSocket 发送的 JSON 对象。
 */
public class Dto {

    /**
     * 对应于 Python 中单个机器人的状态信息。
     * 用于 /ws/status 和状态更新。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RobotStatusDto {
        private String robotId;
        private boolean online;
        private double cpuPercent;
        private double ramPercent;
        private String lastUpdate;
        private String currentStatusMessage;
        private String timestamp;
    }

    /**
     * 对应于 Python 中发送给 /api/settings/{robot_id} 的设置对象。
     */
    @Data
    public static class RobotSettingsDto {
        private double pointDensity;
    }

    /**
     * 对应于 Python 中发送给 /api/robots 的机器人配置。
     * 使用 @JsonProperty 来匹配前端期望的 camelCase 命名。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RobotConfigDto {
        @JsonProperty("displayName")
        private String displayName;
    }

    // --- Frontend Packet DTOs ---
    // 这些 DTOs 对应于 Python 中 data_queue_processor 推送的 `frontend_packet`
    // 使用一个基类和多个子类来清晰地表示不同类型的数据

    /**
     * 发送给前端的数据包的通用结构。
     * @param <T> a payload type
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrontendPacketDto<T> {
        private String type; // e.g., "PACKET_POINTCLOUD_XYZI", "PACKET_IMAGE"
        private String robotId;
        private String frame;
        private T payload; // The actual data payload
    }

    /**
     * 点云数据 (XYZI) 的载荷。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointCloudXyziDto {
        private List<PointXyziDto> points;
    }

    /**
     * 单个 XYZI 点。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointXyziDto {
        private float x;
        private float y;
        private float z;
        private float intensity;
    }

    /**
     * 点云数据 (XYZRGB) 的载荷。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointCloudXyzrgbDto {
        private List<PointXyzrgbDto> points;
    }

    /**
     * 单个 XYZRGB 点。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointXyzrgbDto {
        private float x;
        private float y;
        private float z;
        private int r;
        private int g;
        private int b;
    }

    /**
     * 图像数据的载荷。
     * 图像数据被编码为 Base64 字符串。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageDto {
        private String data; // Base64 encoded image data
        private String format;
        private int width;
        private int height;
    }

    /**
     * 里程计数据的载荷。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OdomDto {
        private PoseDto pose;
        // Python 代码中注释掉了速度，这里也保持一致
        // private VelocityDto velocity;
    }

    /**
     * 姿态数据。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PoseDto {
        private PositionDto position;
        private QuaternionDto orientation;
    }

    /**
     * 位置数据。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionDto {
        private float x;
        private float y;
        private float z;
    }

    /**
     * 四元数数据。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuaternionDto {
        private float x;
        private float y;
        private float z;
        private float w;
    }
}
