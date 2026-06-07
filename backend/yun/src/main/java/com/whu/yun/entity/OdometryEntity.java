package com.whu.yun.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OdometryEntity {
    private Long id;
    private String robotId;
    private String frameId;
    private double posX;
    private double posY;
    private double posZ;
    private double orientX;
    private double orientY;
    private double orientZ;
    private double orientW;
    private LocalDateTime timestamp;
    private Long receptionCount;
}