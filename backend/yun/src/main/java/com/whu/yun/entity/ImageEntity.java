package com.whu.yun.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImageEntity {
    private Long id;
    private String robotId;
    private String sessionId;
    private String format;
    private int width;
    private int height;
    private String filePath;
    private LocalDateTime timestamp;
    private Long receptionCount;
}