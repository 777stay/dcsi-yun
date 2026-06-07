package com.whu.yun.controller;

import com.whu.yun.dto.ApiResponse;
import com.whu.yun.dto.PageDto;
import com.whu.yun.entity.MissionPlanEntity;
import com.whu.yun.service.MissionPlanManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 负责 KML 任务规划管理的控制器 (查询、删除)。
 */
@RestController
@RequestMapping("/api/mission-plans")
@RequiredArgsConstructor
public class MissionPlanManagementController {

    private final MissionPlanManagementService missionPlanManagementService;

    /**
     * 分页获取所有任务规划记录。
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageDto<MissionPlanEntity>>> getAllMissionPlans(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PageDto<MissionPlanEntity> page = missionPlanManagementService.getMissionPlans(pageNum, pageSize);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    /**
     * 根据ID删除一个任务规划记录及其文件。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteMissionPlan(@PathVariable Long id) {
        try {
            missionPlanManagementService.deleteMissionPlan(id);
            return ResponseEntity.ok(ApiResponse.success("删除成功"));
        } catch (Exception e) {
            // 返回一个更详细的错误信息
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, "删除失败: " + e.getMessage()));
        }
    }
    /**
     * 【新增】根据任务ID下载关联的KML文件压缩包。
     * @param id 任务ID
     * @return 包含 ZIP 文件内容的文件流响应
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadKmlFiles(@PathVariable Long id) {
        try {
            Resource resource = missionPlanManagementService.prepareKmlDownload(id);
            String filename = "mission_plan_" + id + "_kml.zip";

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (Exception e) {
            // 在实际应用中，这里可以返回一个更友好的错误响应
            // 但为了简单起见，我们直接返回 404
            return ResponseEntity.notFound().build();
        }
    }
}
