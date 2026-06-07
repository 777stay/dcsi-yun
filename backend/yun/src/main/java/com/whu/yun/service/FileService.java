package com.whu.yun.service;

import com.whu.yun.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 负责从服务器磁盘加载文件资源的服务。
 */
@Service
@Slf4j
public class FileService {

    private final Path imageBaseDir;
    private final Path pointCloudBaseDir;
    private final Path annotatedImageBaseDir;
    private final Path reportImageBaseDir; // 新增：报告图片基础目录

    @Autowired
    public FileService(AppProperties appProperties) {
        this.imageBaseDir = Paths.get(appProperties.getFileStorage().getImagePath()).toAbsolutePath().normalize();
        this.pointCloudBaseDir = Paths.get(appProperties.getFileStorage().getPointCloudPath()).toAbsolutePath().normalize();
        this.annotatedImageBaseDir = Paths.get(appProperties.getFileStorage().getAnnotatedImagePath()).toAbsolutePath().normalize();
        this.reportImageBaseDir = Paths.get(appProperties.getFileStorage().getReportImagePath()).toAbsolutePath().normalize(); // 初始化报告图片目录

        // 启动时打印出最终解析的绝对路径，便于调试
        log.info("图片存储根目录已初始化: {}", this.imageBaseDir);
        log.info("点云存储根目录已初始化: {}", this.pointCloudBaseDir);
        log.info("标注图片存储根目录已初始化: {}", this.annotatedImageBaseDir);
    }

    /**
     * 根据文件类型和文件名加载文件资源。
     * @param type 文件类型 ("images", "pointclouds", "annotated_images")
     * @param filename 文件名
     * @return 可加载的文件资源
     */
    public Resource loadFileAsResource(String type, String filename) {
        try {
            Path baseDir;
            // 根据类型选择正确的基础目录
            switch (type) {
                case "images":
                    baseDir = this.imageBaseDir;
                    break;
                case "pointclouds":
                    baseDir = this.pointCloudBaseDir;
                    break;
                case "annotated_images":
                    baseDir = this.annotatedImageBaseDir;
                    break;
                case "report_images": // 新增：处理报告图片类型
                    baseDir = this.reportImageBaseDir;
                    break;
                default:
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的文件类型: " + type);
            }

            Path filePath = baseDir.resolve(filename).normalize();

            // 【关键日志】打印出正在尝试加载的完整路径
            log.info("正在尝试从此绝对路径加载文件: {}", filePath);

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                // 如果文件不存在，也打印日志
                log.error("文件不存在或不可读，路径: {}", filePath);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件未找到: " + filename);
            }
        } catch (MalformedURLException ex) {
            log.error("URL格式错误", ex);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件未找到: " + filename, ex);
        }
    }
}
