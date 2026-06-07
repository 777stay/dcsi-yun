package com.whu.yun.controller;

import com.whu.yun.dto.ApiResponse;
import com.whu.yun.dto.LoginRequest;
import com.whu.yun.dto.LoginResponse;
import com.whu.yun.entity.User;
import com.whu.yun.mapper.UserMapper;
import com.whu.yun.service.TokenBlacklistService;
import com.whu.yun.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(maxAge = 3600)
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService; // --- [新增] 注入黑名单服务

    @PostMapping("/login")
    public ApiResponse<LoginResponse> authenticateUser(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtil.generateToken(authentication);

        return ApiResponse.success(new LoginResponse(jwt));
    }

    @PostMapping("/register")
    public ApiResponse<String> registerUser(@RequestBody LoginRequest registerRequest) {
        if (userMapper.findByUsername(registerRequest.getUsername()) != null) {
            return ApiResponse.error(400, "Username is already taken!");
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        userMapper.insertUser(user);

        return ApiResponse.success("User registered successfully!");
    }

    // --- [新增] 登出接口 ---
    @PostMapping("/logout")
    public ApiResponse<String> logoutUser(HttpServletRequest request) {
        String token = parseJwt(request);
        if (token != null) {
            Date expirationDate = jwtUtil.getExpirationDateFromToken(token);
            long remainingDuration = expirationDate.getTime() - System.currentTimeMillis();
            if (remainingDuration > 0) {
                // 将 Token 加入黑名单，有效期为 Token 的剩余有效期
                tokenBlacklistService.addToBlacklist(token, remainingDuration / 1000);
            }
        }
        return ApiResponse.success("Logout successful!");
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}