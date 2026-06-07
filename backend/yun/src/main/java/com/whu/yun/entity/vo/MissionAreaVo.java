package com.whu.yun.entity.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class MissionAreaVo {
    /**
     * 无人机数量
     */
    private int numberDevice;
    /**
     * 扫描密度
     */
    private int scanDensity;
    /**
     * 规划模式
     */
    private int planMode;
    /**
     * 杆塔的起始编号
     */
    private int droneStart;
    /**
     * 杆塔的结束编号
     */
    private int droneEnd;

    /**
     * 任务区域的点集合（二维坐标列表）
     */
    private List<Point> missionLayerPointArr;
    /**
     *  障碍物区域的点集合（二维坐标列表）。
     */
    private List<List<Point>> obstacleLayerPointArr;
    /**
     * 初始位置的字符串表示（如经纬度）
     */
    private List<Point> initialLocations;
    /**
     * 分配比例（百分比）
     */
    private List<Double> distributionRatios;
    private String kmldir;

    @Data
    public static class Point {
        private double lat;
        @JsonProperty("long") // JSON 里是 long，Java 里用 longitude
        private double longitude;
    }
}
