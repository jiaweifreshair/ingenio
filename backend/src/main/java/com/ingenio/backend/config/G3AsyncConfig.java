package com.ingenio.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * G3引擎异步执行配置
 * 提供专用的线程池执行G3代码生成任务
 *
 * 配置策略：
 * - 核心线程数：2（避免过多并发AI调用）
 * - 最大线程数：5（允许一定程度的并发）
 * - 队列容量：50（防止任务堆积过多）
 * - 拒绝策略：CallerRunsPolicy（调用者执行，避免任务丢失）
 */
@Slf4j
@Configuration
@EnableAsync
public class G3AsyncConfig {

    /**
     * G3任务执行器
     * 专用于执行G3代码生成和编译验证任务
     */
    @Bean("g3TaskExecutor")
    public Executor g3TaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数（保持较小，避免过多AI调用）
        executor.setCorePoolSize(2);

        // 最大线程数（允许一定的并发处理）
        executor.setMaxPoolSize(5);

        // 队列容量（防止任务堆积过多）
        executor.setQueueCapacity(50);

        // 线程名前缀（便于日志追踪）
        executor.setThreadNamePrefix("g3-task-");

        // 空闲线程存活时间（秒）
        executor.setKeepAliveSeconds(60);

        // 拒绝策略：由调用者执行，避免任务丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任务完成后再关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 最大等待关闭时间
        executor.setAwaitTerminationSeconds(300);

        // 初始化
        executor.initialize();

        log.info("[G3AsyncConfig] G3任务执行器初始化完成: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }
}
