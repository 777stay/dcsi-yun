package com.whu.yun.mapper;

import com.whu.yun.entity.DetectionResultEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DetectionResultMapper {

    /**
     * 插入一条目标检测结果记录
     * @param entity 检测结果实体
     */
    @Insert("INSERT INTO detection_results (source_image_name, source_image_path, annotated_image_path, class_name, confidence, box_xmin, box_ymin, box_xmax, box_ymax, detection_time) " +
            "VALUES (#{sourceImageName}, #{sourceImagePath}, #{annotatedImagePath}, #{className}, #{confidence}, #{boxXmin}, #{boxYmin}, #{boxXmax}, #{boxYmax}, #{detectionTime})")
    void insert(DetectionResultEntity entity);

}


