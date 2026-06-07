package com.whu.yun.controller;

import com.whu.yun.dto.ApiResponse;
import com.whu.yun.dto.DetectionResultDto;
import com.whu.yun.service.DetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/detection")
@RequiredArgsConstructor
@Slf4j
public class DetectionController {

    private final DetectionService detectionService;

    @PostMapping("/detect")
    public ResponseEntity<ApiResponse<DetectionResultDto>> detectObjects(@RequestParam("image") MultipartFile imageFile) {
        if (imageFile.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "上传的文件不能为空。"));
        }
        try {
            DetectionResultDto result = detectionService.detectObjects(imageFile);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("目标检测时发生错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "服务器内部错误: " + e.getMessage()));
        }
    }
}