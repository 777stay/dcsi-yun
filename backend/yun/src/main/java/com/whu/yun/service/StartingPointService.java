package com.whu.yun.service;

import com.whu.yun.entity.GetStartingPointsRequest;
import com.whu.yun.entity.vo.StartingPointVo;

import java.util.List;

public interface StartingPointService {
    List<StartingPointVo> getStartingPoints(GetStartingPointsRequest request);
}
