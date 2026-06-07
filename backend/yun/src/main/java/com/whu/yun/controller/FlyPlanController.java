package com.whu.yun.controller;

import com.whu.yun.entity.FlyPoint;
import com.whu.yun.service.FlyPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.List;

@RestController
@RequestMapping("/flyplan")
public class FlyPlanController {

    @Autowired
    private FlyPlanService flyPlanService;

    // 通过请求路径加载数据并生成 KML
    @GetMapping("/generate-kml")
    public String generateKml(@RequestParam String txtPath, @RequestParam String folderPath, @RequestParam String fileName) {
        try {
            // 加载飞行点数据
            List<FlyPoint> flyPoints = flyPlanService.loadFlyPoints(txtPath);
            // 生成 KML 文件
            flyPlanService.generateKmlFile(folderPath, fileName, flyPoints,20);
            return "KML file generated successfully!";
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "Error occurred while generating KML file.";
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}

