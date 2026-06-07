package com.whu.yun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.yun.dto.DataQueryDto;
import com.whu.yun.dto.ReceptionCountWithTimeRange;
import com.whu.yun.entity.ImageEntity;
import com.whu.yun.entity.OdometryEntity;
import com.whu.yun.entity.PointCloudEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DataQueryMapper extends BaseMapper<Object> {
    /**
     * 从所有数据表中查询出所有出现过的机器人ID
     * @return 机器人ID列表
     */
    List<String> getDistinctRobotIds();
    List<String> getPointCloudFramesByRobotId(String robotId);
    List<String> getImageSessionsByRobotId(String robotId);
    List<PointCloudEntity> findPointCloudsByFrame(@Param("robotId") String robotId, @Param("frameId") String frameId);
    List<ImageEntity> findImagesBySession(@Param("robotId") String robotId, @Param("sessionId") String sessionId);
    // --- 【新增】按 receptionCount 查询 ---
    @Select("SELECT DISTINCT reception_count FROM point_cloud_data WHERE reception_count IS NOT NULL " +
            "UNION " +
            "SELECT DISTINCT reception_count FROM image_data WHERE reception_count IS NOT NULL " +
            "UNION " +
            "SELECT DISTINCT reception_count FROM odometry_data WHERE reception_count IS NOT NULL " +
            "ORDER BY reception_count DESC")
    List<Long> findDistinctReceptionCounts();

    @Select("SELECT DISTINCT robot_id FROM point_cloud_data WHERE reception_count = #{receptionCount} " +
            "UNION " +
            "SELECT DISTINCT robot_id FROM image_data WHERE reception_count = #{receptionCount} " +
            "UNION " +
            "SELECT DISTINCT robot_id FROM odometry_data WHERE reception_count = #{receptionCount}")
    List<String> findRobotIdsByReceptionCount(@Param("receptionCount") Long receptionCount);

    // --- 【修改】为所有分类和数据查询方法增加 receptionCount 参数 ---

    List<String> findPointCloudFramesByRobotId(@Param("robotId") String robotId, @Param("receptionCount") Long receptionCount);

    List<String> findImageSessionsByRobotId(@Param("robotId") String robotId, @Param("receptionCount") Long receptionCount);

    IPage<DataQueryDto.PointCloud> findPointCloudsByFrame(Page<DataQueryDto.PointCloud> page, @Param("robotId") String robotId, @Param("frame") String frame, @Param("receptionCount") Long receptionCount);

    IPage<DataQueryDto.Image> findImagesBySession(Page<DataQueryDto.Image> page, @Param("robotId") String robotId, @Param("session") String session, @Param("receptionCount") Long receptionCount);

    IPage<DataQueryDto.Odometry> findOdometryByRobotId(Page<DataQueryDto.Odometry> page, @Param("robotId") String robotId, @Param("receptionCount") Long receptionCount);


}