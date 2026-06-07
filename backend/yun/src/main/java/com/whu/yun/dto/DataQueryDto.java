package com.whu.yun.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据查询功能的数据传输对象 (DTO) 集合。
 */
public class DataQueryDto {

    @Data
    public static class PointCloud {
        private Long id;
        private LocalDateTime timestamp;
        private String filePath;
        private Long pointCount;
        private Long receptionCount;
    }

    @Data
    public static class Image {
        private Long id;
        private LocalDateTime timestamp;
        private String filePath;
        private String format;
        private Long receptionCount;
    }

    @Data
    public static class Odometry {
        private Long id;
        private LocalDateTime timestamp;
        private Double posX;
        private Double posY;
        private Double posZ;
        private Double orientX;
        private Double orientY;
        private Double orientZ;
        private Double orientW;
        private Long receptionCount;
    }
}

