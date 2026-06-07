package com.whu.yun.entity;


import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PointCloudEntity {
    private Long id;
    private String robotId;
    private String frameId;
    private String filePath;
    private Long pointCount;
    private LocalDateTime timestamp;
    private Long receptionCount;
}