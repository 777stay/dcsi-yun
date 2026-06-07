package com.whu.yun.controller;
import com.whu.yun.config.AppProperties;
import com.whu.yun.config.RobotConfigDto;
import com.whu.yun.dto.ApiResponse;
import com.whu.yun.dto.ImageDto;
import com.whu.yun.entity.ImageEntity;
import com.whu.yun.entity.MissionPlannerRequested;
import com.whu.yun.entity.Result;
import com.whu.yun.mapper.ImageMapper;
import com.whu.yun.service.CacheService;
import com.whu.yun.service.MissionPlannerService1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Slf4j
@RestController
@RequestMapping("/api") // 将公共路径 /api 提取到类级别
@RequiredArgsConstructor
@CrossOrigin(maxAge = 3600)
public class RobotDataController {

    private final ImageMapper imageMapper;
    private final CacheService cacheService;
    private final AppProperties appProperties; // --- [新增] 注入配置属性

    private static final String IMAGE_CACHE_KEY_PREFIX = "image:";


    // --- [新增] 提供机器人配置的接口 ---
    @GetMapping("/robots")
    public ApiResponse<Map<String, RobotConfigDto>> getRobotConfigs() {
        return ApiResponse.success(appProperties.getRobots());
    }

    @GetMapping("/data/image/{id}")
    public ApiResponse<ImageDto> getImageInfo(@PathVariable Long id) {
        String cacheKey = IMAGE_CACHE_KEY_PREFIX + id;
        ImageEntity entity = cacheService.queryWithCacheLogic(
                cacheKey,
                () -> imageMapper.findById(id),
                3600 // 缓存1小时
        );

        if (entity == null) {
            return ApiResponse.error(404, "Image not found");
        }
        return ApiResponse.success(ImageDto.fromEntity(entity));
    }

    @GetMapping("/data/image/download/{id}")
    public ResponseEntity<Resource> downloadImage(@PathVariable Long id) {
        ImageEntity entity = imageMapper.findById(id);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            Path filePath = Paths.get(entity.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                throw new RuntimeException("Could not read the file!");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/data/image/{id}")
    public ApiResponse<Void> deleteImage(@PathVariable Long id) {
        ImageEntity entity = imageMapper.findById(id);
        if (entity == null) {
            return ApiResponse.error(404, "Image not found");
        }

        try {
            int affectedRows = imageMapper.deleteById(id);
            if (affectedRows > 0) {
                cacheService.delete(IMAGE_CACHE_KEY_PREFIX + id);
                Files.deleteIfExists(Paths.get(entity.getFilePath()));
                return ApiResponse.success();
            } else {
                return ApiResponse.error(500, "Failed to delete image from database.");
            }
        } catch (Exception e) {
            return ApiResponse.error(500, "Error deleting file: " + e.getMessage());
        }
    }
}