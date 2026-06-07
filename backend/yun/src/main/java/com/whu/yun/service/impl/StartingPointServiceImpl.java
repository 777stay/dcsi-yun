package com.whu.yun.service.impl;

import com.whu.yun.config.AppProperties;
import com.whu.yun.config.RobotConfigDto;
import com.whu.yun.entity.GetStartingPointsRequest;
import com.whu.yun.entity.vo.StartingPointVo;
import com.whu.yun.service.RobotOdometryCacheService;
import com.whu.yun.service.StartingPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StartingPointServiceImpl implements StartingPointService {

    private final RobotOdometryCacheService odometryCacheService;
    private final AppProperties appProperties;

    @Override
    public List<StartingPointVo> getStartingPoints(GetStartingPointsRequest request) {
        Map<String, RobotConfigDto> robots = appProperties.getRobots();
        List<StartingPointVo> result = new ArrayList<>();

        List<RobotOdometryCacheService.RobotOdomSnapshot> snapshots = odometryCacheService.list();
        for (RobotOdometryCacheService.RobotOdomSnapshot snapshot : snapshots) {
            String robotId = snapshot.getRobotId();
            String model = "";
            if (robots != null && robots.containsKey(robotId)) {
                RobotConfigDto config = robots.get(robotId);
                model = config != null ? config.getDisplayName() : "";
            }

            double lon = snapshot.getPosX();
            double lat = snapshot.getPosY();

            result.add(new StartingPointVo(robotId, model, lon, lat));
        }

        return result;
    }
}
