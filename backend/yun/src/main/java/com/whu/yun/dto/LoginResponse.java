package com.whu.yun.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 这个类用于向前端返回登录成功后的响应。
 * 它包含了生成的 JWT 和 Token 类型 (固定为 "Bearer")。
 */
@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType = "Bearer";
    public LoginResponse(String token) {
        this.token = token;
    }
}