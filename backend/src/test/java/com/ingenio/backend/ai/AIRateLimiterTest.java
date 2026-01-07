package com.ingenio.backend.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI调用速率限制器单元测试
 *
 * 测试覆盖：
 * 1. 速率限制许可获取和释放
 * 2. 指数退避计算
 * 3. 速率限制错误检测
 * 4. 状态信息获取
 */
@DisplayName("AIRateLimiter 速率限制器测试")
class AIRateLimiterTest {

    private AIRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new AIRateLimiter();
        // 模拟@PostConstruct调用，确保默认值被正确初始化
        rateLimiter.init();
    }

    @Test
    @DisplayName("应成功获取和释放速率限制许可")
    void shouldAcquireAndReleasePermit() throws InterruptedException {
        // Given: 默认配置的速率限制器

        // When: 获取许可
        boolean acquired = rateLimiter.acquire(5000);

        // Then: 应成功获取
        assertThat(acquired).isTrue();

        // When: 释放许可
        rateLimiter.release();

        // Then: 不应抛出异常
    }

    @Test
    @DisplayName("指数退避计算应正确")
    void shouldCalculateExponentialBackoffCorrectly() {
        // Given: 基础延迟2000ms，最大延迟120000ms

        // When: 计算第1次尝试的退避时间
        long delay1 = rateLimiter.calculateExponentialBackoff(1, 2000, 120000);

        // Then: 第1次应约为2000-2500ms（包含抖动）
        assertThat(delay1).isBetween(2000L, 2500L);

        // When: 计算第2次尝试的退避时间
        long delay2 = rateLimiter.calculateExponentialBackoff(2, 2000, 120000);

        // Then: 第2次应约为4000-5000ms
        assertThat(delay2).isBetween(4000L, 5000L);

        // When: 计算第3次尝试的退避时间
        long delay3 = rateLimiter.calculateExponentialBackoff(3, 2000, 120000);

        // Then: 第3次应约为8000-10000ms
        assertThat(delay3).isBetween(8000L, 10000L);

        // When: 计算第7次尝试的退避时间（应达到最大值）
        long delay7 = rateLimiter.calculateExponentialBackoff(7, 2000, 120000);

        // Then: 应不超过最大延迟
        assertThat(delay7).isLessThanOrEqualTo(150000L); // 120000 + 25%抖动
    }

    @Test
    @DisplayName("应正确检测429速率限制错误")
    void shouldDetectRateLimitErrorBy429Status() {
        // Given: HTTP 429状态码

        // When: 检测是否是速率限制错误
        boolean isRateLimitError = rateLimiter.isRateLimitError(429, null);

        // Then: 应识别为速率限制错误
        assertThat(isRateLimitError).isTrue();
    }

    @Test
    @DisplayName("应正确检测错误消息中的速率限制关键词")
    void shouldDetectRateLimitErrorByMessage() {
        // Given: 包含速率限制关键词的错误消息
        String[] rateLimitMessages = {
                "rate limit reached for RPM",
                "Rate_Limit exceeded",
                "Too Many Requests",
                "RPM limit exceeded",
                "quota exceeded",
                "Request throttled"
        };

        for (String message : rateLimitMessages) {
            // When: 检测是否是速率限制错误
            boolean isRateLimitError = rateLimiter.isRateLimitError(null, message);

            // Then: 应识别为速率限制错误
            assertThat(isRateLimitError)
                    .as("消息 '%s' 应被识别为速率限制错误", message)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("非速率限制错误不应被误判")
    void shouldNotMisidentifyNonRateLimitErrors() {
        // Given: 非速率限制相关的错误消息
        String[] nonRateLimitMessages = {
                "Invalid API key",
                "Internal server error",
                "Connection timeout",
                "Model not found"
        };

        for (String message : nonRateLimitMessages) {
            // When: 检测是否是速率限制错误
            boolean isRateLimitError = rateLimiter.isRateLimitError(200, message);

            // Then: 不应识别为速率限制错误
            assertThat(isRateLimitError)
                    .as("消息 '%s' 不应被识别为速率限制错误", message)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("应正确返回状态信息")
    void shouldReturnCorrectStatus() throws InterruptedException {
        // Given: 获取一个许可
        rateLimiter.acquire(5000);

        // When: 获取状态信息
        String status = rateLimiter.getStatus();

        // Then: 状态信息应包含关键信息
        assertThat(status).contains("RPM:");
        assertThat(status).contains("并发:");
        assertThat(status).contains("上次请求:");

        // Cleanup
        rateLimiter.release();
    }

    @Test
    @DisplayName("多次获取许可应正确计数")
    void shouldCorrectlyCountMultipleAcquires() throws InterruptedException {
        // Given: 获取多个许可

        // When: 连续获取3个许可
        boolean acquired1 = rateLimiter.acquire(5000);
        boolean acquired2 = rateLimiter.acquire(5000);
        boolean acquired3 = rateLimiter.acquire(5000);

        // Then: 都应成功（默认并发限制为3）
        assertThat(acquired1).isTrue();
        assertThat(acquired2).isTrue();
        assertThat(acquired3).isTrue();

        // Cleanup
        rateLimiter.release();
        rateLimiter.release();
        rateLimiter.release();
    }

    @Test
    @DisplayName("空错误消息不应被误判为速率限制错误")
    void shouldHandleNullAndEmptyMessages() {
        // Given: 空或null的错误消息

        // When/Then: null消息
        assertThat(rateLimiter.isRateLimitError(null, null)).isFalse();
        assertThat(rateLimiter.isRateLimitError(null, "")).isFalse();
        assertThat(rateLimiter.isRateLimitError(200, null)).isFalse();
    }

    @Test
    @DisplayName("初始化后默认值应正确设置")
    void shouldHaveCorrectDefaultValues() {
        // Given: 刚初始化的速率限制器

        // Then: 默认值应正确
        assertThat(rateLimiter.getMaxRequestsPerMinute()).isEqualTo(30);
        assertThat(rateLimiter.getMaxConcurrent()).isEqualTo(3);
    }
}
