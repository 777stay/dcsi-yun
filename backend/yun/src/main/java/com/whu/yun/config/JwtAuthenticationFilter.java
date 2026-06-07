package com.whu.yun.config;

import com.whu.yun.service.TokenBlacklistService;
import com.whu.yun.service.UserDetailsServiceImpl;
import com.whu.yun.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserDetailsServiceImpl userDetailsService;
    @Autowired
    private TokenBlacklistService tokenBlacklistService; // --- [新增] 注入黑名单服务
    private final List<AntPathRequestMatcher> publicMatchers = Arrays.asList(
            new AntPathRequestMatcher("/pointclouds/**"),
            new AntPathRequestMatcher("/api/pointclouds/**")
            // ... 如果有其他公开路径，也在这里添加
    ).stream().collect(Collectors.toList());
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 2. 在所有逻辑之前，首先检查当前请求是否是公开路径
        if (publicMatchers.stream().anyMatch(matcher -> matcher.matches(request))) {
            // 如果是公开路径，则不执行任何 Token 验证，直接放行
            filterChain.doFilter(request, response);
            return; // 直接返回，终止后续在本过滤器中的执行
        }
        try {
            String jwt = parseJwt(request);
            // --- [修改] 增加黑名单检查 ---
            if (jwt != null && jwtUtil.validateToken(jwt) && !tokenBlacklistService.isBlacklisted(jwt)) {
                String username = jwtUtil.getUsernameFromToken(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e);
        }
        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}