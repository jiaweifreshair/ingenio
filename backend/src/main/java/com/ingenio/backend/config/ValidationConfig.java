package com.ingenio.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * V2.0 验证服务线程池配置
 *
 * 功能：
 * 1. 为并行验证提供线程池（validateFull并行模式）
 * 2. Spring管理的线程池，自动优雅关闭
 * 3. 可配置的线程池参数
 *
 * 线程池配置：
 * - 核心线程数：3（支持compile+test+business并行）
 * - 最大线程数：10（高峰时期扩容）
 * - 队列容量：100（排队等待的验证任务）
 * - 线程名前缀：validation-（便于日志追踪）
 * - 拒绝策略：CallerRunsPolicy（调用者线程执行）
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 4
 */
@Slf4j
@Configuration
public class ValidationConfig {

    /**
     * 验证服务专用线程池
     *
     * 用途：
     * - validateFull()并行验证模式
     * - 支持compile/test/coverage/business阶段并行执行
     *
     * 优雅关闭：
     * - Spring容器销毁时自动调用shutdown()
     * - 等待运行中的任务完成（最长60秒）
     * - 强制中断超时任务
     *
     * @return ThreadPoolTaskExecutor实例
     */
    @Bean(name = "validationExecutor")
    public ThreadPoolTaskExecutor validationExecutor() {
        log.info("初始化验证服务线程池...");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数：3个（对应compile/test/business三个阶段）
        executor.setCorePoolSize(3);

        // 最大线程数：10个（支持高并发场景）
        executor.setMaxPoolSize(10);

        // 队列容量：100个任务（排队等待）
        executor.setQueueCapacity(100);

        // 线程名称前缀（便于日志追踪）
        executor.setThreadNamePrefix("validation-");

        // 拒绝策略：CallerRunsPolicy（队列满时由调用者线程执行）
        // 优点：避免任务丢失，提供背压机制
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 优雅关闭配置
        executor.setWaitForTasksToCompleteOnShutdown(true);  // 等待任务完成
        executor.setAwaitTerminationSeconds(60);              // 最长等待60秒

        // 初始化线程池
        executor.initialize();

        log.info("验证服务线程池初始化完成 - corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;
    }
}
