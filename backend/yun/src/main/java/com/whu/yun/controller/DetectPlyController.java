package com.whu.yun.controller;

import com.whu.yun.dto.TowerResultDto;
import com.whu.yun.service.ScriptExecutionService;
// 假设 TowerResultDto 定义在 service 包下，如果它是 ScriptExecutionService 的内部类，请调整导入

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/script")
public class DetectPlyController {

    private static final Logger logger = LoggerFactory.getLogger(DetectPlyController.class);

    private final ScriptExecutionService scriptService;

    public DetectPlyController(ScriptExecutionService scriptService) {
        this.scriptService = scriptService;
    }

    @PostMapping("/run-detection")
    public ResponseEntity<?> runDetection(
            @RequestParam("plyFile1") MultipartFile plyFile1,
            @RequestParam("plyFile2") MultipartFile plyFile2,
            @RequestParam("kmlFile") MultipartFile kmlFile) {
        try {
            // 调用 Service，现在它返回的是包含多个杆塔结果的列表 (List<TowerResultDto>)
            List<TowerResultDto> results = scriptService.runChangeDetectionScripts(plyFile1, plyFile2, kmlFile);

            // 直接返回列表，Spring Boot 会自动将其序列化为 JSON 数组
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            logger.error("执行脚本失败", e);
            // 返回 JSON 格式的错误信息，键名使用 "message" 以匹配前端的 error.response.data.message
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "执行失败: " + e.getMessage()));
        }
    }
}