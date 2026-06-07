package com.whu.yun.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")// 允许的路径
                .allowedOriginPatterns("*") // 你前端应用的地址
                .allowedMethods("GET", "POST", "PUT", "DELETE") // 允许的方法
                .allowCredentials(true);
    }
}