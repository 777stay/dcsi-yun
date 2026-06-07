package com.whu.yun.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetStartingPointsRequest {
    private Long missionId;
    private Integer planMode;
    private String kmldir;
    private List<List<Double>> missionLayerPointArr;
}
