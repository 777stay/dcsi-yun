package com.whu.yun.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * API 访问日志实体类
 */
@Data
public class AccessLog {
    private Long id;
    private String username;
    private String ipAddress;
    private String url;
    private String httpMethod;
    private String classMethod;
    private String requestParams;
    private Long executionTime;
    private LocalDateTime timestamp;
}
