package com.whu.yun.config;

import net.devh.boot.grpc.server.security.authentication.GrpcAuthenticationReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 这个配置类解决了 gRPC 服务和 Spring Security 自动配置之间的冲突。
 * 当 Spring Security 存在时，gRPC 的安全自动配置会尝试寻找一个 GrpcAuthenticationReader bean。
 * 我们在这里提供一个简单的实现来满足这个依赖，从而允许应用正常启动。
 */
@Configuration
public class GrpcSecurityConfig {

    /**
     * 创建一个 GrpcAuthenticationReader bean。
     * 由于我们的 gRPC 服务用于机器人内部通信，不需要复杂的认证流程，
     * 因此我们返回一个简单的匿名实现，它不执行任何认证操作。
     *
     * @return 一个不执行任何操作的 GrpcAuthenticationReader 实例。
     */
    @Bean
    public GrpcAuthenticationReader grpcAuthenticationReader() {
        // 返回一个简单的、不执行任何操作的实现
        return (call, headers) -> null;
    }

    // --- [已移除] ---
    // 移除了多余的 grpcAuthenticationManager bean，以避免与 SecurityConfig 中的主 AuthenticationManager 冲突。
    // 主 AuthenticationManager 将通过 @Primary 注解被优先选择。
}