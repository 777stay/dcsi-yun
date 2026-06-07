package com.whu.yun.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {

    /**
     * 定义一个专门用于处理点云聚合的线程池。
     * @return Executor 实例
     */
    @Bean("pointCloudAggregationExecutor")
    public Executor pointCloudAggregationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：CPU核心数
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        // 最大线程数：CPU核心数的两倍
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 2);
        // 任务队列容量
        executor.setQueueCapacity(500);
        // 线程名称前缀
        executor.setThreadNamePrefix("agg-pool-");
        // 初始化线程池
        executor.initialize();
        return executor;
    }
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("async-task-");
        executor.initialize();
        return executor;
    }
    @Bean("missionPlanTaskExecutor")// 将这个 Bean 标记为主要的/默认的线程池
    public Executor missionPlantaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：线程池创建时初始化的线程数
        executor.setCorePoolSize(10);
        // 最大线程数：线程池可容纳的最大线程数
        executor.setMaxPoolSize(20);
        // 队列容量：当核心线程都在忙时，新任务会进入队列等待
        executor.setQueueCapacity(200);
        // 线程名前缀，方便在日志中识别
        executor.setThreadNamePrefix("async-task-");
        // 拒绝策略：当队列已满且线程数达到最大时，由调用者线程处理该任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}