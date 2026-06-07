package com.whu.yun.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 目标检测结果实体类，对应数据库中的 detection_results 表
 */
@Data
public class DetectionResultEntity {
    private Long id;
    private String sourceImageName;
    private String sourceImagePath;
    private String annotatedImagePath;
    private String className;
    private Double confidence;
    private Integer boxXmin;
    private Integer boxYmin;
    private Integer boxXmax;
    private Integer boxYmax;
    private LocalDateTime detectionTime;
}