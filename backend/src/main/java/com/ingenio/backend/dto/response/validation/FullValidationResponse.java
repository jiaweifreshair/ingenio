package com.ingenio.backend.dto.response.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 三环集成验证响应
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FullValidationResponse {

    /**
     * 验证任务ID
     */
    private UUID validationTaskId;

    /**
     * 各阶段验证结果
     * - compile: 编译验证
     * - test: 测试验证
     * - business: 业务验证
     */
    private Map<String, StageResult> stages;

    /**
     * 整体验证状态
     * - passed: 全部通过
     * - failed: 有阶段失败
     * - partial: 部分通过（failFast=false时）
     */
    private String overallStatus;

    /**
     * 整体质量评分（0-100）
     */
    private Integer overallScore;

    /**
     * 总耗时（毫秒）
     */
    private Long totalDurationMs;

    /**
     * 完成时间
     */
    private Instant completedAt;

    /**
     * 单阶段结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageResult {
        /**
         * 阶段名称
         */
        private String stage;

        /**
         * 验证状态
         * - passed: 通过
         * - failed: 失败
         * - skipped: 跳过（前置失败）
         */
        private String status;

        /**
         * 是否通过
         */
        private Boolean passed;

        /**
         * 错误消息
         */
        private String errorMessage;

        /**
         * 详细结果
         */
        private Map<String, Object> details;

        /**
         * 耗时（毫秒）
         */
        private Long durationMs;
    }
}
