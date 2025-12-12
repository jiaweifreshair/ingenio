package com.ingenio.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 性能验证结果
 *
 * 验证指标：
 * - API响应时间（P50/P95/P99）
 * - 内存使用
 * - CPU使用率
 * - 数据库查询性能
 */
@Data
@Builder
public class PerformanceResult {

    /**
     * 是否达标
     */
    @Builder.Default
    private Boolean passed = false;

    /**
     * 平均响应时间（毫秒）
     */
    private Long avgResponseTime;

    /**
     * P50响应时间（毫秒）
     */
    private Long p50ResponseTime;

    /**
     * P95响应时间（毫秒）
     */
    private Long p95ResponseTime;

    /**
     * P99响应时间（毫秒）
     */
    private Long p99ResponseTime;

    /**
     * 最大响应时间（毫秒）
     */
    private Long maxResponseTime;

    /**
     * 最小响应时间（毫秒）
     */
    private Long minResponseTime;

    /**
     * 内存使用（MB）
     */
    private Long memoryUsageMb;

    /**
     * 峰值内存使用（MB）
     */
    private Long peakMemoryUsageMb;

    /**
     * CPU使用率（0-100）
     */
    private Double cpuUsagePercent;

    /**
     * 峰值CPU使用率
     */
    private Double peakCpuUsagePercent;

    /**
     * 数据库查询平均耗时（毫秒）
     */
    private Long avgDbQueryTime;

    /**
     * 最慢的数据库查询耗时
     */
    private Long slowestDbQueryTime;

    /**
     * 并发用户数
     */
    private Integer concurrentUsers;

    /**
     * 每秒请求数（RPS）
     */
    private Double requestsPerSecond;

    /**
     * 错误率（0-1之间）
     */
    private Double errorRate;

    /**
     * 测试持续时间（毫秒）
     */
    private Long testDurationMs;

    /**
     * 性能指标详情
     * key: 接口路径或功能名称
     * value: 性能指标
     */
    private Map<String, EndpointMetrics> endpointMetrics;

    /**
     * 失败原因
     */
    private String failureReason;

    /**
     * 单个接口的性能指标
     */
    @Data
    @Builder
    public static class EndpointMetrics {
        /**
         * 接口路径
         */
        private String path;

        /**
         * 平均响应时间（毫秒）
         */
        private Long avgResponseTime;

        /**
         * P95响应时间（毫秒）
         */
        private Long p95ResponseTime;

        /**
         * 请求次数
         */
        private Integer requestCount;

        /**
         * 错误次数
         */
        private Integer errorCount;

        /**
         * 错误率
         */
        public double getErrorRate() {
            if (requestCount == null || requestCount == 0) {
                return 0.0;
            }
            return (double) errorCount / requestCount;
        }
    }

    /**
     * 检查是否满足性能要求
     * - P95响应时间 < 3000ms
     * - 错误率 < 1%
     * - 内存使用 < 512MB
     */
    public boolean meetsPerformanceGoals() {
        if (p95ResponseTime != null && p95ResponseTime > 3000) {
            return false;
        }
        if (errorRate != null && errorRate > 0.01) {
            return false;
        }
        if (memoryUsageMb != null && memoryUsageMb > 512) {
            return false;
        }
        return true;
    }
}
