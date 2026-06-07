package com.whu.yun.controller;

import com.whu.yun.entity.GetStartingPointsRequest;
import com.whu.yun.entity.MissionPlannerRequest;
import com.whu.yun.entity.Result;
import com.whu.yun.entity.vo.MissionAreaVo;
import com.whu.yun.entity.vo.StartingPointVo;
import com.whu.yun.service.MissionPlannerService;
import com.whu.yun.service.StartingPointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/mission")
public class MissionPlannerController {

    @Autowired
    private MissionPlannerService missionPlannerService;  // 注入服务类
    @Autowired
    private StartingPointService startingPointService;

    @PostMapping("/plan")
    public Result<List<List<double[]>>> startMissionPlanner(@RequestBody MissionPlannerRequest request) {
        // 调用 Service 层的业务方法
        List<List<double[]>> result = missionPlannerService.startMissionPlanner(request);
        if(result == null || result.isEmpty()){
            return Result.fail(300,"towers not found in DEM, please use mannual H");
        }
        return Result.ok(result);
    }

    @PostMapping("/uploadKML")
    public Result<MissionAreaVo> uploadKML(@RequestBody MissionPlannerRequest request) throws Exception {
        return missionPlannerService.uploadKML(request);
    }


    @PostMapping("/planTower")
    public Result<List<List<double[]>>> startTowerMissionPlanner(@RequestBody MissionPlannerRequest request) {
        List<List<double[]>> result = missionPlannerService.startTowerMissionPlanner(request);
        if(result == null || result.isEmpty()){
            return Result.fail(300,"towers not found in DEM, please use mannual H");
        }
        return Result.ok(result);
    }

    @PostMapping("/getStartingPoints")
    public Result<List<StartingPointVo>> getStartingPoints(
        @RequestBody(required = false)  GetStartingPointsRequest request
    ) {
        return Result.ok(startingPointService.getStartingPoints(request));
    }
}
