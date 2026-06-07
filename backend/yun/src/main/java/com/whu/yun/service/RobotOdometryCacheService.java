package com.whu.yun.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RobotOdometryCacheService {

    private final ConcurrentHashMap<String, RobotOdomSnapshot> latest = new ConcurrentHashMap<>();

    public void update(String robotId, double posX, double posY) {
        latest.put(robotId, new RobotOdomSnapshot(robotId, posX, posY, System.currentTimeMillis()));
    }

    public List<RobotOdomSnapshot> list() {
        return new ArrayList<>(latest.values());
    }

    @Data
    @AllArgsConstructor
    public static class RobotOdomSnapshot {
        private String robotId;
        private double posX;
        private double posY;
        private long updateTimeMs;
    }
}
