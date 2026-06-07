package com.whu.yun.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    // 从 application-prod1.yml 读取文件存储的基础路径
    @Value("${app.file-storage.point-cloud-path}")
    private String pointCloudBasePath;

    @Value("${app.file-storage.image-path}")
    private String imageBasePath;

    @Value("${potree.metajsonUrl.path}")
    private String potreeDataPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 URL 路径 /data/pointclouds/** 映射到本地文件系统的点云存储目录
        registry.addResourceHandler("/data/pointclouds/**")
                .addResourceLocations("file:" + pointCloudBasePath);

        // 将 URL 路径 /data/images/** 映射到本地文件系统的图片存储目录
        registry.addResourceHandler("/data/images/**")
                .addResourceLocations("file:" + imageBasePath);

        registry.addResourceHandler("/dist/data/**")
                .addResourceLocations("file:" + potreeDataPath);

    }

}
