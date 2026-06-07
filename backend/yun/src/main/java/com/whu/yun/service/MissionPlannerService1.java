package com.whu.yun.service;

import com.whu.yun.entity.MissionPlannerRequested;
import com.whu.yun.entity.Result;
import com.whu.yun.entity.vo.MissionAreaVo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface MissionPlannerService1 {
    List<List<double[]>> startMissionPlanner(MissionPlannerRequested request) ;

}
