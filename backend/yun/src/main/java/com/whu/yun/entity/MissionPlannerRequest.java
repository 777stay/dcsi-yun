package com.whu.yun.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.omg.PortableInterceptor.INACTIVE;


import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MissionPlannerRequest {
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
     * 路径是否严格限制在多边形内。
     */
    private boolean pathsStrictlyInPoly;
    /**
     * 任务区域的点集合（二维坐标列表）
     */
    private List<List<Double>> missionLayerPointArr;
    /**
     *  障碍物区域的点集合（二维坐标列表）。
     */
    private List<List<Double>> obstacleLayerPointArr;
    /**
     * 初始位置的字符串表示（如经纬度）
     */
    private List<String> initialLocations;
    /**
     * 分配比例（百分比）
     */
    private List<Double> distributionRatios;

    private String kmldir;

    private Integer flyMode;


    private List<UavConfig> uavConfigs;

    List<Map<String, Double>> kmlTowerPoints;

    private Float overlapDegree;
}

/**
 * {
 *   "polygon": [
 *     {
 *       "lat": 23.339163311430777,
 *       "long": 113.00629377365114
 *     },
 *     {
 *       "lat": 23.33601070693789,
 *       "long": 113.0021095275879
 *     },
 *     {
 *       "lat": 23.33639493461523,
 *       "long": 113.01131486892702
 *     }
 *   ],
 *   "obstacles": [
 *     [
 *       {
 *         "lat": 23.337389701654427,
 *         "long": 113.00676584243776
 *       },
 *       {
 *         "lat": 23.33690695749673,
 *         "long": 113.00603091716768
 *       },
 *       {
 *         "lat": 23.33693651331193,
 *         "long": 113.00694286823274
 *       }
 *     ]
 *   ],
 *   "pathsStrictlyInPoly": true,
 *   "rPortions": [
 *     0.34,
 *     0.33,
 *     0.33
 *   ],
 *   "initialPos": [
 *     {
 *       "lat": 23.338222,
 *       "long": 113.007994
 *     },
 *     {
 *       "lat": 23.337981,
 *       "long": 113.004738
 *     },
 *     {
 *       "lat": 23.336207,
 *       "long": 113.007286
 *     }
 *   ],
 *   "scanningDensity": 5,
 *   "droneNo": 3
 * }
 */
