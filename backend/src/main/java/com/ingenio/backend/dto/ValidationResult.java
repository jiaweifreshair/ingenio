package com.ingenio.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 验证结果
 *
 * 包含完整的验证流程结果：
 * - 编译验证
 * - 单元测试
 * - E2E测试
 * - 性能测试
 */
@Data
@Builder
public class ValidationResult {

    /**
     * 任务ID
     */
    private UUID taskId;

    /**
     * 是否全部通过
     */
    @Builder.Default
    private Boolean success = false;

    /**
     * 失败原因（如果success=false）
     */
    private String failureReason;

    /**
     * 编译验证结果
     */
    private CompilationResult compilationResult;

    /**
     * 单元测试结果
     */
    private TestResult unitTestResult;

    /**
     * E2E测试结果
     */
    private TestResult e2eTestResult;

    /**
     * 性能验证结果
     */
    private PerformanceResult performanceResult;

    /**
     * 开始时间
     */
    private Instant startTime;

    /**
     * 结束时间
     */
    private Instant endTime;

    /**
     * 总耗时（毫秒）
     */
    private Long totalDurationMs;

    /**
     * 计算总耗时
     */
    public void calculateDuration() {
        if (startTime != null && endTime != null) {
            totalDurationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }
    }
}
