package com.ingenio.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Spring Retry配置类
 * 启用重试机制，用于处理数据库连接瞬时失败等场景
 *
 * 使用方式：
 * 在需要重试的方法上添加 @Retryable 注解：
 * <pre>
 * {@code
 * @Retryable(
 *     retryFor = {CannotGetJdbcConnectionException.class, DataAccessResourceFailureException.class},
 *     maxAttempts = 3,
 *     backoff = @Backoff(delay = 1000, multiplier = 2)
 * )
 * public User findById(Long id) {
 *     return userMapper.selectById(id);
 * }
 * }
 * </pre>
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Configuration
@EnableRetry
public class RetryConfig {
    // Spring Retry通过@EnableRetry注解启用
    // 具体重试策略通过@Retryable注解在方法级别配置
}
