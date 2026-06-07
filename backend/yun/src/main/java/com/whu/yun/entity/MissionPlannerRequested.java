package com.whu.yun.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MissionPlannerRequested {
    private int numberDevice;
    private int scanDensity;
    private int planMode;
    private int droneStart;
    private int droneEnd;
    private int droneSpeed;
    private boolean pathsStrictlyInPoly;
    private List<List<Double>> missionLayerPointArr;
    private List<List<Double>> obstacleLayerPointArr;
    private String location1;
    private String location2;
    private String location3;
    private int distributionRatio1;
    private int distributionRatio2;
    private int distributionRatio3;
}

