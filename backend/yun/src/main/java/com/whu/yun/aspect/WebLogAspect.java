package com.whu.yun.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.whu.yun.entity.AccessLog;
import com.whu.yun.service.AccessLogService;

import com.whu.yun.utils.IpUtil;
import lombok.RequiredArgsConstructor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@Aspect
@Component
@RequiredArgsConstructor
public class WebLogAspect {

    private final AccessLogService accessLogService;
    private final ObjectMapper objectMapper;

    /**
     * 定义一个切点，匹配 com.whu.yun.controller 包下的所有类的所有公共方法。
     */
    @Pointcut("execution(public * com.whu.yun.controller..*.*(..))")
    public void webLog() {}

    /**
     * 使用 @Around 注解，可以在目标方法执行前后都进行操作。
     */
    @Around("webLog()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result;
        try {
            // 执行原始的 Controller 方法
            result = joinPoint.proceed();
        } finally {
            // 无论方法是否成功，都记录日志
            long endTime = System.currentTimeMillis();
            recordLog(joinPoint, endTime - startTime);
        }
        return result;
    }

    private void recordLog(ProceedingJoinPoint joinPoint, long executionTime) {
        try {
            AccessLog log = new AccessLog();
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                log.setIpAddress(IpUtil.getIpAddr(request));
                log.setUrl(request.getRequestURL().toString());
                log.setHttpMethod(request.getMethod());

                // 记录请求参数
                Object[] args = joinPoint.getArgs();
                if (args.length > 0) {
                    // 过滤掉文件上传等不可序列化的参数
                    if (!(args[0] instanceof MultipartFile)) {
                        log.setRequestParams(objectMapper.writeValueAsString(args));
                    }
                }
            }

            // 记录执行的类和方法
            log.setClassMethod(joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName());
            
            // 从 Spring Security 上下文中获取当前用户名
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            log.setUsername(username);
            
            log.setExecutionTime(executionTime);
            log.setTimestamp(LocalDateTime.now());

            // 异步保存日志
            accessLogService.saveLog(log);

        } catch (Exception e) {
            // 记录日志本身如果出错，只打印错误，不影响主流程
            System.err.println("记录访问日志时发生错误: " + e.getMessage());
        }
    }
}
