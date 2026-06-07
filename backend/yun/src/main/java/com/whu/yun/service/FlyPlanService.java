package com.whu.yun.service;


import com.whu.yun.entity.FlyPoint;
import com.whu.yun.utils.FlyPlan_GBK;
import org.springframework.stereotype.Service;


import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.List;

@Service
public class FlyPlanService {

    private final FlyPlan_GBK flyPlan;

    // 构造器注入 FlyPlan_GBK 类
    public FlyPlanService(FlyPlan_GBK flyPlan) {
        this.flyPlan = flyPlan;
    }

    public List<FlyPoint> loadFlyPoints(String txtPath) {
        return flyPlan.loadDataFromPath(txtPath);
    }

    public void generateKmlFile(String folderPath, String fileName, List<FlyPoint> flyPoints,int speed) throws UnsupportedEncodingException, FileNotFoundException {
        flyPlan.WriteFlyPlanKml(folderPath, fileName, flyPoints,speed);
    }
}

