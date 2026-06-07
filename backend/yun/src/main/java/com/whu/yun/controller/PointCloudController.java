package com.whu.yun.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/potreeClouds")
// 允许来自 Vue 开发服务器(例如 localhost:8080)的跨域请求
@CrossOrigin
public class PointCloudController {

    @Value("${potree.metajsonUrl.path}")
    private String storagePath;

    @GetMapping
    public List<PointCloudInfo> listPointClouds() throws IOException {
        Path rootPath = Paths.get(storagePath);
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            System.err.println("错误: 点云存储路径未找到或不是一个目录: " + storagePath);
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.walk(rootPath)) {
            return paths
                    // 筛选条件1: 找到所有名为 "metadata.json" 的文件
                    // 如果您的文件名是 meta.json，请在这里修改
                    .filter(path -> path.getFileName().toString().equals("metadata.json"))

                    // 筛选条件2: 确保其父目录的父目录名为 "pointclouds"
                    // 这对应了 .../pointclouds/xx/metadata.json 的结构
                    .filter(path -> {
                        Path parent = path.getParent();
                        if (parent == null) return false;
                        Path grandParent = parent.getParent();
                        return grandParent != null && grandParent.getFileName().toString().equals("pointclouds");
                    })
                    .map(path -> {
                        // 路径示例: /home/dcsi/project/ply_files/xx/pointclouds/xx/metadata.json

                        // 向上查找三级以获取项目文件夹 "xx" 作为名称
                        Path projectDir = path.getParent().getParent().getParent();
                        if (projectDir == null) return null;

                        String name = projectDir.getFileName().toString();

                        // 构建前端可访问的URL
                        Path relativePath = rootPath.relativize(path); // 得到: xx/pointclouds/xx/metadata.json
                        String url = "dist/data/" + relativePath.toString(); // 得到: /pointclouds/xx/pointclouds/xx/metadata.json

                        return new PointCloudInfo(name, url);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }
    public static class PointCloudInfo {
        private String name;
        private String url;

        public PointCloudInfo(String name, String url) {
            this.name = name;
            this.url = url;
        }

        // getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}