package com.ingenio.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置
 *
 * 提供两个独立的线程池：
 * 1. multimodalExecutor - 用于多模态输入的异步处理（语音转文字、图像分析）
 * 2. generationTaskExecutor - 用于代码生成任务的异步执行
 *
 * 线程池设计原则：
 * - 核心线程数：根据业务负载设置，避免过度竞争
 * - 最大线程数：2倍核心线程数，应对突发流量
 * - 队列容量：100-200，防止OOM
 * - 优雅关闭：等待任务完成后再关闭，避免数据丢失
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 默认异步执行器（多模态处理）
     *
     * 用于语音转文字、图像分析等轻量级任务
     * 核心线程：5个
     * 最大线程：10个
     * 队列容量：100
     */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-multimodal-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("多模态异步任务线程池已初始化: corePoolSize=5, maxPoolSize=10, queueCapacity=100");

        return executor;
    }

    /**
     * 代码生成任务执行器（重量级任务）
     *
     * 用于AI代码生成任务，单个任务耗时较长（2-10分钟）
     * 核心线程：3个（控制并发数，避免AI API限流）
     * 最大线程：5个
     * 队列容量：50（控制排队数量，避免用户等待过久）
     *
     * @return 代码生成任务执行器
     */
    @Bean(name = "generationTaskExecutor")
    public Executor generationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数：3（限制并发AI请求数，避免限流）
        executor.setCorePoolSize(3);

        // 最大线程数：5（应对短时峰值）
        executor.setMaxPoolSize(5);

        // 队列容量：50（限制排队任务数）
        executor.setQueueCapacity(50);

        // 线程名称前缀：便于日志追踪
        executor.setThreadNamePrefix("async-generation-");

        // 拒绝策略：CallerRunsPolicy（队列满时由调用者线程执行，实现背压）
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        // 优雅关闭：等待任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 最多等待120秒（代码生成任务较长）
        executor.setAwaitTerminationSeconds(120);

        executor.initialize();

        log.info("代码生成异步任务线程池已初始化: corePoolSize=3, maxPoolSize=5, queueCapacity=50");

        return executor;
    }
}
