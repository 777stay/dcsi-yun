package com.whu.yun.dto;


import com.whu.yun.entity.ImageEntity;
import com.whu.yun.entity.OdometryEntity;
import com.whu.yun.entity.PointCloudEntity;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DataQueryResultDto {
    // Key: frameId (e.g., "强度点云"), Value: 对应的点云列表
    private Map<String, List<PointCloudEntity>> pointClouds;
    // Key: sessionId (e.g., "目标检测图像"), Value: 对应的图片列表
    private Map<String, List<ImageEntity>> images;
    // 路径数据
    private List<OdometryEntity> odometry;
}