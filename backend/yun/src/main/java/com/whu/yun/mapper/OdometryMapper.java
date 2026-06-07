package com.whu.yun.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.yun.entity.OdometryEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OdometryMapper extends BaseMapper<OdometryEntity> {
    /**
     * 插入一条里程计数据记录
     * @param odometryEntity 包含里程计数据的实体
     * @return 返回影响的行数
     */
    int insertOdometry(OdometryEntity odometryEntity);

    List<OdometryEntity> findByRobotId(String robotId);

    OdometryEntity findLatestByRobotId(String robotId);
}
