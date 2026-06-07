package com.whu.yun.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.yun.entity.vo.MissionAreaVo;

import java.util.List;
import java.util.Map;

public class JsonParser {

    private static final ObjectMapper mapper = new ObjectMapper();
    // Python输出的JSON起始特征（固定为{'droneNo'}）
    private static final String JSON_START_MARKER = "{'droneNo'";

    public static MissionAreaVo parseJson(String rawString) throws Exception {
        // 1. 第一步：精准定位JSON的起始位置（避开前面的警告日志）
        int jsonStartIndex = rawString.indexOf(JSON_START_MARKER);
        if (jsonStartIndex == -1) {
            throw new IllegalArgumentException("未找到有效的JSON数据（缺失{'droneNo'}起始标志）");
        }

        // 2. 第二步：截取从起始标志到最后一个}的完整JSON（排除后面可能的多余字符）
        String pythonJson = rawString.substring(jsonStartIndex);
        int jsonEndIndex = pythonJson.lastIndexOf('}');
        if (jsonEndIndex == -1) {
            throw new IllegalArgumentException("JSON数据不完整（缺失闭合}）");
        }
        pythonJson = pythonJson.substring(0, jsonEndIndex + 1);

        // 3. 第三步：Python JSON转标准JSON（关键修复点）
        String standardJson = pythonJson
                .replace('\'', '"')          // 单引号转双引号
                .replace("True", "true")     // Python布尔值转JSON布尔值（大写转小写）
                .replace("False", "false");  // 修复当前报错的核心步骤

        // 4. 第四步：解析标准JSON
        Map<String, Object> map = mapper.readValue(standardJson, new TypeReference<Map<String, Object>>() {});

        MissionAreaVo vo = new MissionAreaVo();
        // 基础字段赋值
        vo.setNumberDevice(((Number) map.get("droneNo")).intValue());
        vo.setScanDensity(((Number) map.get("scanningDensity")).intValue());

        // 任务区域（polygon -> List<Point>）
        List<MissionAreaVo.Point> polygon = mapper.convertValue(
                map.get("polygon"),
                new TypeReference<List<MissionAreaVo.Point>>() {}
        );
        vo.setMissionLayerPointArr(polygon);

        // 障碍物（obstacles -> List<List<Point>>）
        List<List<MissionAreaVo.Point>> obstacles = mapper.convertValue(
                map.get("obstacles"),
                new TypeReference<List<List<MissionAreaVo.Point>>>() {}
        );
        vo.setObstacleLayerPointArr(obstacles);

        // 初始位置（initialPos -> List<Point>）
        List<MissionAreaVo.Point> initialPos = mapper.convertValue(
                map.get("initialPos"),
                new TypeReference<List<MissionAreaVo.Point>>() {}
        );
        vo.setInitialLocations(initialPos);

        // 分配比例（rPortions -> List<Double>）
        List<Double> rPortions = mapper.convertValue(
                map.get("rPortions"),
                new TypeReference<List<Double>>() {}
        );
        vo.setDistributionRatios(rPortions);

        // 未提供字段赋默认值
        vo.setPlanMode(0);
        vo.setDroneStart(0);
        vo.setDroneEnd(0);
        vo.setKmldir(null);

        return vo;
    }
}