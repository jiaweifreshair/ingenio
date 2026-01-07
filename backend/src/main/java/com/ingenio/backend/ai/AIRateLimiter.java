package com.ingenio.backend.ai;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI调用速率限制器
 *
 * 实现令牌桶算法，控制AI API调用频率，防止触发RPM（Requests Per Minute）限制。
 *
 * 核心功能：
 * 1. 令牌桶速率限制：每分钟最多N次请求
 * 2. 并发控制：最多同时M个请求
 * 3. 指数退避重试：遇到429错误时自动退避重试
 * 4. 调用间隔控制：两次调用之间的最小间隔
 *
 * 配置参数（通过application.yml配置）：
 * - ingenio.ai.rate-limit.rpm: 每分钟最大请求数（默认30）
 * - ingenio.ai.rate-limit.concurrent: 最大并发数（默认3）
 * - ingenio.ai.rate-limit.min-interval-ms: 最小调用间隔（默认2000ms）
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AIRateLimiter {

    /**
     * 默认每分钟最大请求数
     */
    private static final int DEFAULT_RPM = 30;

    /**
     * 默认最大并发数
     */
    private static final int DEFAULT_CONCURRENT = 3;

    /**
     * 默认最小调用间隔（毫秒）
     */
    private static final long DEFAULT_MIN_INTERVAL_MS = 2000;

    /**
     * 每分钟最大请求数（RPM）
     * 设置为30，留有足够余量（七牛云通常限制60 RPM）
     */
    @Value("${ingenio.ai.rate-limit.rpm:30}")
    private int maxRequestsPerMinute = DEFAULT_RPM;

    /**
     * 最大并发请求数
     * 避免同时发起过多请求
     */
    @Value("${ingenio.ai.rate-limit.concurrent:3}")
    private int maxConcurrent = DEFAULT_CONCURRENT;

    /**
     * 两次调用之间的最小间隔（毫秒）
     * 默认2秒，避免请求过于密集
     */
    @Value("${ingenio.ai.rate-limit.min-interval-ms:2000}")
    private long minIntervalMs = DEFAULT_MIN_INTERVAL_MS;

    /**
     * 并发控制信号量
     */
    private Semaphore concurrencyLimiter;

    /**
     * 当前分钟内的请求计数
     */
    private final AtomicInteger requestCount = new AtomicInteger(0);

    /**
     * 当前分钟的起始时间戳
     */
    private final AtomicLong currentMinuteStart = new AtomicLong(System.currentTimeMillis());

    /**
     * 上次请求的时间戳
     */
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    /**
     * 锁对象，用于同步令牌桶操作
     */
    private final Object tokenBucketLock = new Object();

    /**
     * 默认构造函数
     * 使用默认值初始化（用于测试场景或配置未注入时）
     */
    public AIRateLimiter() {
        this.concurrencyLimiter = new Semaphore(DEFAULT_CONCURRENT, true);
    }

    /**
     * Spring初始化后的回调
     * 根据配置值重新初始化信号量
     */
    @PostConstruct
    public void init() {
        // 使用配置值重新初始化并发限制器
        this.concurrencyLimiter = new Semaphore(maxConcurrent, true);
        log.info("[AIRateLimiter] 初始化完成: rpm={}, concurrent={}, minIntervalMs={}",
                maxRequestsPerMinute, maxConcurrent, minIntervalMs);
    }

    /**
     * 请求一个调用许可
     *
     * 此方法会阻塞直到获得许可或超时。
     * 调用结束后必须调用 release() 释放许可。
     *
     * @param timeoutMs 等待超时时间（毫秒）
     * @return true表示获得许可，false表示超时
     * @throws InterruptedException 如果等待被中断
     */
    public boolean acquire(long timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long remainingTime = timeoutMs;

        // 1. 获取并发许可
        if (!concurrencyLimiter.tryAcquire(remainingTime, TimeUnit.MILLISECONDS)) {
            log.warn("[AIRateLimiter] 获取并发许可超时，当前并发数已达上限: {}", maxConcurrent);
            return false;
        }

        try {
            remainingTime = timeoutMs - (System.currentTimeMillis() - startTime);

            // 2. 等待令牌桶许可（RPM限制）
            if (!waitForTokenBucket(remainingTime)) {
                concurrencyLimiter.release();
                return false;
            }

            // 3. 等待最小调用间隔
            waitForMinInterval();

            // 4. 记录本次请求时间
            lastRequestTime.set(System.currentTimeMillis());

            log.debug("[AIRateLimiter] 许可获取成功，当前分钟请求数: {}/{}, 等待时间: {}ms",
                    requestCount.get(), maxRequestsPerMinute, System.currentTimeMillis() - startTime);

            return true;

        } catch (Exception e) {
            concurrencyLimiter.release();
            throw e;
        }
    }

    /**
     * 释放调用许可
     *
     * 调用完成后必须调用此方法释放并发许可。
     */
    public void release() {
        concurrencyLimiter.release();
    }

    /**
     * 等待令牌桶许可
     *
     * @param timeoutMs 超时时间
     * @return true表示获得许可，false表示超时
     */
    private boolean waitForTokenBucket(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;

        synchronized (tokenBucketLock) {
            while (true) {
                long now = System.currentTimeMillis();

                // 检查是否进入新的一分钟
                if (now - currentMinuteStart.get() >= 60_000) {
                    // 重置计数器
                    currentMinuteStart.set(now);
                    requestCount.set(0);
                    log.debug("[AIRateLimiter] 进入新的一分钟，重置请求计数");
                }

                // 检查是否还有配额
                if (requestCount.get() < maxRequestsPerMinute) {
                    requestCount.incrementAndGet();
                    return true;
                }

                // 计算需要等待的时间
                long waitTime = 60_000 - (now - currentMinuteStart.get());
                waitTime = Math.min(waitTime, deadline - now);

                if (waitTime <= 0) {
                    log.warn("[AIRateLimiter] 等待令牌桶许可超时，当前分钟请求数: {}",
                            requestCount.get());
                    return false;
                }

                log.info("[AIRateLimiter] RPM限制达到上限，等待 {}ms 后重试...", waitTime);
                tokenBucketLock.wait(waitTime);
            }
        }
    }

    /**
     * 等待最小调用间隔
     */
    private void waitForMinInterval() throws InterruptedException {
        long lastTime = lastRequestTime.get();
        long now = System.currentTimeMillis();
        long elapsed = now - lastTime;

        if (elapsed < minIntervalMs && lastTime > 0) {
            long waitTime = minIntervalMs - elapsed;
            log.debug("[AIRateLimiter] 等待最小调用间隔: {}ms", waitTime);
            Thread.sleep(waitTime);
        }
    }

    /**
     * 计算指数退避等待时间
     *
     * @param attempt   当前尝试次数（从1开始）
     * @param baseDelayMs 基础延迟时间（毫秒）
     * @param maxDelayMs  最大延迟时间（毫秒）
     * @return 等待时间（毫秒）
     */
    public long calculateExponentialBackoff(int attempt, long baseDelayMs, long maxDelayMs) {
        // 指数退避: delay = base * 2^(attempt-1) + jitter
        long delay = baseDelayMs * (1L << Math.min(attempt - 1, 6)); // 最多64倍
        delay = Math.min(delay, maxDelayMs);

        // 添加随机抖动（0-25%）
        long jitter = (long) (delay * Math.random() * 0.25);
        delay += jitter;

        return delay;
    }

    /**
     * 检查是否是速率限制错误
     *
     * @param httpStatus HTTP状态码
     * @param errorMessage 错误消息
     * @return true表示是速率限制错误
     */
    public boolean isRateLimitError(Integer httpStatus, String errorMessage) {
        // HTTP 429 Too Many Requests
        if (httpStatus != null && httpStatus == 429) {
            return true;
        }

        // 检查错误消息中是否包含速率限制相关关键词
        if (errorMessage != null && !errorMessage.isEmpty()) {
            String lower = errorMessage.toLowerCase();
            return lower.contains("rate limit")
                    || lower.contains("rate_limit")
                    || lower.contains("too many requests")
                    || lower.contains("rpm")
                    || lower.contains("quota exceeded")
                    || lower.contains("throttl");
        }

        return false;
    }

    /**
     * 获取当前状态信息
     *
     * @return 状态描述字符串
     */
    public String getStatus() {
        return String.format("RPM: %d/%d, 并发: %d/%d, 上次请求: %dms前",
                requestCount.get(),
                maxRequestsPerMinute,
                maxConcurrent - concurrencyLimiter.availablePermits(),
                maxConcurrent,
                System.currentTimeMillis() - lastRequestTime.get());
    }

    /**
     * 获取当前RPM限制值（用于测试）
     */
    int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    /**
     * 获取当前并发限制值（用于测试）
     */
    int getMaxConcurrent() {
        return maxConcurrent;
    }
}
