package com.whu.yun.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务规划实体类，对应数据库中的 mission_plans 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MissionPlanEntity {

    private Long id; // 自增ID
    private Integer numberDevice; // 设备数量
    private String droneStart; // 起飞点
    private String droneEnd; // 结束点
    private String droneSpeed; // 速度
    private String kmlFilePath; // KML文件路径
    private String scanDensity; // 扫描密度
    private LocalDateTime time; // 时间
    private Long receptionCount;
}