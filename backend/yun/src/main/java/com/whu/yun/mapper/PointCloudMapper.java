package com.whu.yun.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.yun.entity.PointCloudEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PointCloudMapper extends BaseMapper<PointCloudEntity> {
    /**
     * 插入一条点云数据记录
     * @param pointCloudEntity 包含点云元数据的实体
     * @return 返回影响的行数
     */


    int insertPointCloud(PointCloudEntity pointCloudEntity);

    List<PointCloudEntity> findByRobotId(String robotId); // 新增
}