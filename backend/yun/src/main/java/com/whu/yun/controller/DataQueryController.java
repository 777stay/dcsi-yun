package com.whu.yun.controller;

import com.whu.yun.dto.*;
import com.whu.yun.entity.ImageEntity;
import com.whu.yun.entity.OdometryEntity;
import com.whu.yun.entity.PointCloudEntity;
import com.whu.yun.entity.SessionInfo;
import com.whu.yun.service.DataQueryService;
import com.whu.yun.service.MyBatisDataFlowControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
@CrossOrigin
public class DataQueryController {

    private final DataQueryService dataQueryService;
    private final MyBatisDataFlowControlService dataFlowControlService;
    @GetMapping("/robots")
    public ApiResponse<List<String>> getRobotIds() {
        return ApiResponse.success(dataQueryService.getRobotIds());
    }
    // 获取所有唯一的接收次数，作为查询的第一层级
    @GetMapping("/reception-counts1")
    public ResponseEntity<ApiResponse<List<Long>>> getReceptionCounts1() {
        return ResponseEntity.ok(ApiResponse.success(dataQueryService.getDistinctReceptionCounts()));
    }
    @GetMapping("/reception-counts")
    public ResponseEntity<ApiResponse<List<SessionTimeRangeDto>>> getReceptionCounts() {
        List<SessionTimeRangeDto> sessionTimeRanges = dataQueryService.getAllSessionTimeRanges();
        return ResponseEntity.ok(ApiResponse.success(sessionTimeRanges));
    }
    /**
     * 获取指定会话的时间区间信息
     */
    @GetMapping("/session-time-range/{sessionCount}")
    public ResponseEntity<ApiResponse<SessionTimeRangeDto>> getSessionTimeRange(
            @PathVariable Long sessionCount) {
        SessionTimeRangeDto timeRange = dataQueryService.getSessionTimeRange(sessionCount);
        return ResponseEntity.ok(ApiResponse.success(timeRange));
    }

    /**
     * 获取当前活跃会话信息
     */
    @GetMapping("/current-session")
    public ResponseEntity<ApiResponse<SessionInfo>> getCurrentSession() {
        SessionInfo currentSession = dataFlowControlService.getCurrentActiveSession();
        return ResponseEntity.ok(ApiResponse.success(currentSession));
    }
    // 根据接收次数获取机器人列表，作为查询的第二层级
    @GetMapping("/robots-by-count/{receptionCount}")
    public ResponseEntity<ApiResponse<List<String>>> getRobotsByCount(@PathVariable Long receptionCount) {
        return ResponseEntity.ok(ApiResponse.success(dataQueryService.getRobotIdsByReceptionCount(receptionCount)));
    }

    @GetMapping("/odometry/{robotId}")
    public ApiResponse<PageDto<OdometryEntity>> getOdometry(
            @PathVariable String robotId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(dataQueryService.getOdometry(robotId, pageNum, pageSize));
    }
    @GetMapping("/pointclouds/categories/{robotId}")
    public ApiResponse<List<String>> getPointCloudCategories(@PathVariable String robotId) {
        return ApiResponse.success(dataQueryService.getPointCloudCategories(robotId));
    }

    @GetMapping("/images/categories/{robotId}")
    public ApiResponse<List<String>> getImageCategories(@PathVariable String robotId) {
        return ApiResponse.success(dataQueryService.getImageCategories(robotId));
    }

    // --- [新增] 按分类分页查询的接口 ---
    @GetMapping("/pointclouds/{robotId}/{frame}")
    public ApiResponse<PageDto<PointCloudEntity>> getPointCloudsByCategory(
            @PathVariable String robotId, @PathVariable String frame,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(dataQueryService.getPointCloudsByCategory(robotId, frame, pageNum, pageSize));
    }
    @GetMapping("/images/{robotId}/{session}")
    public ApiResponse<PageDto<ImageEntity>> getImagesByCategory(
            @PathVariable String robotId, @PathVariable String session,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "12") int pageSize) {
        return ApiResponse.success(dataQueryService.getImagesByCategory(robotId, session, pageNum, pageSize));
    }
    /**
     * 根据接收次数和机器人ID，获取图像分类 (sessions)。
     */
    @GetMapping("/image-categories/{robotId}/{receptionCount}")
    public ResponseEntity<ApiResponse<List<String>>> getImageCategories(
            @PathVariable String robotId,
            @PathVariable Long receptionCount) {
        List<String> imageCategories = dataQueryService.getImageCategories(robotId, receptionCount);
        return ResponseEntity.ok(ApiResponse.success(imageCategories));
    }

    /**
     * 根据接收次数和机器人ID，获取点云分类 (frames)。
     */
    @GetMapping("/pointcloud-categories/{robotId}/{receptionCount}")
    public ResponseEntity<ApiResponse<List<String>>> getPointCloudCategories(
            @PathVariable String robotId,
            @PathVariable Long receptionCount) {
        List<String> pointCloudCategories = dataQueryService.getPointCloudCategories(robotId, receptionCount);
        return ResponseEntity.ok(ApiResponse.success(pointCloudCategories));
    }

    /**
     * 根据接收次数和机器人ID，获取点云数据分类 (分页)。
     */
    @GetMapping("/pointclouds/{robotId}/{receptionCount}/{frame}")
    public ResponseEntity<ApiResponse<PageDto<DataQueryDto.PointCloud>>> getPointCloudsByCategory(
            @PathVariable String robotId,
            @PathVariable String frame,
            @PathVariable Long receptionCount,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageDto<DataQueryDto.PointCloud> pointClouds = dataQueryService.getPointCloudsByCategory(robotId, frame, receptionCount, pageNum, pageSize);
        return ResponseEntity.ok(ApiResponse.success(pointClouds));
    }

    /**
     * 根据接收次数和机器人ID，获取图像数据分类 (分页)。
     */
    @GetMapping("/images/{robotId}/{receptionCount}/{session}")
    public ResponseEntity<ApiResponse<PageDto<DataQueryDto.Image>>> getImagesByCategory(
            @PathVariable String robotId,
            @PathVariable String session,
            @PathVariable Long receptionCount,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageDto<DataQueryDto.Image> images = dataQueryService.getImagesByCategory(robotId, session, receptionCount, pageNum, pageSize);
        return ResponseEntity.ok(ApiResponse.success(images));
    }

    /**
     * 根据接收次数和机器人ID，获取路径数据 (分页)。
     */
    @GetMapping("/odometry/{receptionCount}/{robotId}")
    public ResponseEntity<ApiResponse<PageDto<DataQueryDto.Odometry>>> getOdometry(
            @PathVariable String robotId,
            @PathVariable Long receptionCount,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageDto<DataQueryDto.Odometry> odometryData = dataQueryService.getOdometry(robotId, receptionCount, pageNum, pageSize);
        return ResponseEntity.ok(ApiResponse.success(odometryData));
    }
}
