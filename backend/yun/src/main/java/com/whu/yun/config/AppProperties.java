package com.whu.yun.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app") // 匹配 application-prod1.yml 中 "app" 前缀的配置
public class AppProperties {
    // 这个Map会自动映射 "app.robots" 下的所有键值对
    private Map<String, RobotConfigDto> robots;
    private FileStorage fileStorage = new FileStorage();
    private Python python = new Python();

    @Data
    public static class FileStorage {
        private String imagePath;
        private String pointCloudPath;
        private String kmlPath;
        // 【新增】用于存放带标注的结果图片
        private String annotatedImagePath;
        private String reportImagePath; // 新增字段：用于存放生成的报告图片
    }

    @Data
    public static class Python {
        // Python 解释器路径
        private String interpreterPath;
        // 目标检测脚本路径
        private String detectionScriptPath;
        // 模型权重路径
        private String modelWeightPath;
        // 【新增】报告生成脚本路径
        private String reportScriptPath;
        // 【新增】置信度阈值
        private double confidenceThreshold = 0.5; // 默认值
    }
}