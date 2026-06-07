package com.whu.yun.dto;

import lombok.Data;

/**
 * 这个类用于接收前端发送过来的登录请求体。
 * 它包含了用户名和密码两个字段。
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
}