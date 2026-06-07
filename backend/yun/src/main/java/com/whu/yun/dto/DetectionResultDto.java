package com.whu.yun.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 返回给前端的目标检测结果 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor // 更新构造函数以包含 reportImageName
public class DetectionResultDto {

    private String originalImageName;
    private String annotatedImageName;
    private String reportImageName; // 新增字段：检测报告图片名称
    private List<Detection> detections;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detection {
        private String className;
        private double confidence;
        private List<Integer> box;
    }

    // 【新增】这个内部类用于匹配从Python脚本接收的JSON结构
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PythonResponse {
        private List<Detection> detections;
        private String annotatedImage; // Base64 编码的图片字符串
    }
}