package com.whu.yun.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.yun.dto.*;
import com.whu.yun.entity.ImageEntity;
import com.whu.yun.entity.OdometryEntity;
import com.whu.yun.entity.PointCloudEntity;
import com.whu.yun.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j // --- [新增] 添加 Slf4j 注解以启用日志功能
@Service
@RequiredArgsConstructor
public class DataQueryService {
    private final DataQueryMapper dataQueryMapper;
    private final OdometryMapper odometryMapper;
    private final SessionInfoMapper sessionInfoMapper; // 新增注入
    public List<String> getRobotIds() { return dataQueryMapper.getDistinctRobotIds(); }
    /**
     * 获取所有唯一的接收次数，按降序排列。
     */
    public List<Long> getDistinctReceptionCounts() {
        log.info("正在查询所有唯一的数据接收会话次数...");
        return dataQueryMapper.findDistinctReceptionCounts();
    }

    /**
     * 根据接收次数获取参与该会话的机器人列表。
     */
    public List<String> getRobotIdsByReceptionCount(Long receptionCount) {
        log.info("正在查询会话 #{} 中的机器人列表...", receptionCount);
        return dataQueryMapper.findRobotIdsByReceptionCount(receptionCount);
    }


    public List<SessionTimeRangeDto> getAllSessionTimeRanges() {
        try {
            // 直接使用MyBatis查询，已经格式化好了时间显示
            return sessionInfoMapper.selectAllSessionTimeRanges();
        } catch (Exception e) {
            log.error("获取所有会话时间区间失败", e);
            return new ArrayList<>();
        }
    }


    public SessionTimeRangeDto getSessionTimeRange(Long sessionCount) {
        try {
            return sessionInfoMapper.selectSessionTimeRangeByCount(sessionCount);
        } catch (Exception e) {
            log.error("获取会话时间区间失败，会话次数: {}", sessionCount, e);
            return null;
        }
    }

    /**
     * 根据机器人ID和接收次数，获取图像的数据分类 (sessions)。
     */
    public List<String> getImageCategories(String robotId, Long receptionCount) {
        log.info("正在查询机器人 {} 在会话 #{} 中的图像分类...", robotId, receptionCount);
        return dataQueryMapper.findImageSessionsByRobotId(robotId, receptionCount);
    }

    /**
     * 按分类分页获取点云数据。
     */
    public PageDto<DataQueryDto.PointCloud> getPointCloudsByCategory(String robotId, String frame, Long receptionCount, int pageNum, int pageSize) {
        log.info("查询点云: robotId='{}', frame='{}', count={}, pageNum={}, pageSize={}", robotId, frame, receptionCount, pageNum, pageSize);
        Page<DataQueryDto.PointCloud> page = new Page<>(pageNum, pageSize);
        IPage<DataQueryDto.PointCloud> result = dataQueryMapper.findPointCloudsByFrame(page, robotId, frame, receptionCount);
        log.info("为该查询找到 {} 条点云记录。", result.getRecords().size());
        return PageDto.fromIPage(result);
    }

    /**
     * 按分类分页获取图像数据。
     */
    public PageDto<DataQueryDto.Image> getImagesByCategory(String robotId, String session, Long receptionCount, int pageNum, int pageSize) {
        log.info("查询图像: robotId='{}', session='{}', count={}, pageNum={}, pageSize={}", robotId, session, receptionCount, pageNum, pageSize);
        Page<DataQueryDto.Image> page = new Page<>(pageNum, pageSize);
        IPage<DataQueryDto.Image> result = dataQueryMapper.findImagesBySession(page, robotId, session, receptionCount);
        log.info("为该查询找到 {} 条图像记录。", result.getRecords().size());
        return PageDto.fromIPage(result);
    }

    /**
     * 分页获取路径数据。
     */
    public PageDto<DataQueryDto.Odometry> getOdometry(String robotId, Long receptionCount, int pageNum, int pageSize) {
        log.info("查询路径: robotId='{}', count={}, pageNum={}, pageSize={}", robotId, receptionCount, pageNum, pageSize);
        Page<DataQueryDto.Odometry> page = new Page<>(pageNum, pageSize);
        IPage<DataQueryDto.Odometry> result = dataQueryMapper.findOdometryByRobotId(page, robotId, receptionCount);
        log.info("为该查询找到 {} 条路径记录。", result.getRecords().size());
        return PageDto.fromIPage(result);
    }
    /**
     * 根据机器人ID和接收次数，获取点云的数据分类 (frames)。
     */
    public List<String> getPointCloudCategories(String robotId, Long receptionCount) {
        log.info("正在查询机器人 {} 在会话 #{} 中的点云分类...", robotId, receptionCount);
        return dataQueryMapper.findPointCloudFramesByRobotId(robotId, receptionCount);
    }

    public List<String> getPointCloudCategories(String robotId) {
        return dataQueryMapper.getPointCloudFramesByRobotId(robotId);
    }
    public List<String> getImageCategories(String robotId) {
        return dataQueryMapper.getImageSessionsByRobotId(robotId);
    }

    public PageDto<PointCloudEntity> getPointCloudsByCategory(String robotId, String frame, int pageNum, int pageSize) {
        log.info("Querying PointClouds for robotId='{}', frame='{}', pageNum={}, pageSize={}", robotId, frame, pageNum, pageSize);

        // 使用PointCloudMapper的selectPage方法
        Page<PointCloudEntity> page = new Page<>(pageNum, pageSize);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PointCloudEntity> queryWrapper = 
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        queryWrapper.eq("robot_id", robotId).eq("frame_id", frame);
        
        // 需要注入PointCloudMapper
        List<PointCloudEntity> list = dataQueryMapper.findPointCloudsByFrame(robotId, frame);
        
        // 手动构建分页结果
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, list.size());
        List<PointCloudEntity> pageList = list.subList(start, end);
        
        PageDto<PointCloudEntity> result = new PageDto<>();
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setSize(pageList.size());
        result.setTotal(list.size());
        result.setPages((int) Math.ceil((double) list.size() / pageSize));
        result.setList(pageList);
        result.setFirstPage(pageNum == 1);
        result.setLastPage(pageNum >= result.getPages());
        result.setPreviousPage(pageNum > 1);
        result.setNextPage(pageNum < result.getPages());
        
        log.info("Found {} point cloud records for this query.", pageList.size());
        return result;
    }
    
    public PageDto<ImageEntity> getImagesByCategory(String robotId, String session, int pageNum, int pageSize) {
        log.info("Querying Images for robotId='{}', session='{}', pageNum={}, pageSize={}", robotId, session, pageNum, pageSize);

        // 使用ImageMapper的方法
        List<ImageEntity> list = dataQueryMapper.findImagesBySession(robotId, session);
        
        // 手动构建分页结果
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, list.size());
        List<ImageEntity> pageList = list.subList(start, end);
        
        PageDto<ImageEntity> result = new PageDto<>();
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setSize(pageList.size());
        result.setTotal(list.size());
        result.setPages((int) Math.ceil((double) list.size() / pageSize));
        result.setList(pageList);
        result.setFirstPage(pageNum == 1);
        result.setLastPage(pageNum >= result.getPages());
        result.setPreviousPage(pageNum > 1);
        result.setNextPage(pageNum < result.getPages());
        
        log.info("Found {} image records for this query.", pageList.size());
        return result;
    }

    public PageDto<OdometryEntity> getOdometry(String robotId, int pageNum, int pageSize) {
        // 使用OdometryMapper的selectPage方法
        Page<OdometryEntity> page = new Page<>(pageNum, pageSize);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OdometryEntity> queryWrapper = 
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        queryWrapper.eq("robot_id", robotId);
        
        IPage<OdometryEntity> result = odometryMapper.selectPage(page, queryWrapper);
        return PageDto.fromIPage(result);
    }
}
