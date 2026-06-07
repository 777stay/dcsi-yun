package com.whu.yun.service;

import com.whu.yun.entity.MissionPlannerRequest;
import com.whu.yun.entity.Result;
import com.whu.yun.entity.vo.MissionAreaVo;
import org.springframework.stereotype.Service;

import java.util.List;


public interface MissionPlannerService {
    List<List<double[]>> startMissionPlanner(MissionPlannerRequest request) ;


    Result<MissionAreaVo> uploadKML(MissionPlannerRequest request) throws Exception;

    List<List<double[]>> startTowerMissionPlanner(MissionPlannerRequest request);
}
