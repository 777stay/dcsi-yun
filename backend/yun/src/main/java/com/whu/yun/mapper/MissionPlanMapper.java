package com.whu.yun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.yun.entity.MissionPlanEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MissionPlanMapper extends BaseMapper<MissionPlanEntity> {

    @Insert("INSERT INTO mission_plans (number_device, drone_start, drone_end, drone_speed, kml_file_path, scan_density, time) " +
            "VALUES(#{numberDevice}, #{droneStart}, #{droneEnd}, #{droneSpeed}, #{kmlFilePath}, #{scanDensity}, #{time})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertMissionPlan(MissionPlanEntity missionPlan);

    /**
     * 分页查询所有任务规划记录
     * @param page 分页对象
     * @return 任务规划列表
     */
    @Select("SELECT * FROM mission_plans ORDER BY time DESC")
    IPage<MissionPlanEntity> findAll(Page<MissionPlanEntity> page);

    /**
     * 【新增】根据ID查询单个任务规划记录
     * @param id 任务ID
     * @return 任务规划实体
     */
    @Select("SELECT * FROM mission_plans WHERE id = #{id}")
    MissionPlanEntity findById(Long id);

    /**
     * 【新增】根据ID删除任务规划记录
     * @param id 任务ID
     */
    @Delete("DELETE FROM mission_plans WHERE id = #{id}")
    void deleteById(Long id);

}