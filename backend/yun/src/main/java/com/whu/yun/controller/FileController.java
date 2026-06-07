package com.whu.yun.controller;

import com.whu.yun.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin
public class FileController {

    private final FileService fileService;

    /**
     * 提供文件下载的API端点。
     * @param type 文件类型 (e.g., "images", "pointclouds", "annotated_images")
     * @param filename 文件名
     * @return 包含文件内容的文件流响应
     */
    @GetMapping("/{type}/{filename:.+}")
    public ResponseEntity<Resource> getFile(@PathVariable String type, @PathVariable String filename, HttpServletRequest request) {
        Resource resource = fileService.loadFileAsResource(type, filename);

        // 尝试确定文件的 MIME 类型
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            // 忽略，使用默认的MIME类型
        }

        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
