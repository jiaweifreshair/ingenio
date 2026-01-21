package com.ingenio.backend.dto.response.validation;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 三环集成验证响应
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
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

    public FullValidationResponse() {
    }

    public FullValidationResponse(UUID validationTaskId, Map<String, StageResult> stages, String overallStatus,
            Integer overallScore, Long totalDurationMs, Instant completedAt) {
        this.validationTaskId = validationTaskId;
        this.stages = stages;
        this.overallStatus = overallStatus;
        this.overallScore = overallScore;
        this.totalDurationMs = totalDurationMs;
        this.completedAt = completedAt;
    }

    public static FullValidationResponseBuilder builder() {
        return new FullValidationResponseBuilder();
    }

    public static class FullValidationResponseBuilder {
        private UUID validationTaskId;
        private Map<String, StageResult> stages;
        private String overallStatus;
        private Integer overallScore;
        private Long totalDurationMs;
        private Instant completedAt;

        public FullValidationResponseBuilder validationTaskId(UUID validationTaskId) {
            this.validationTaskId = validationTaskId;
            return this;
        }

        public FullValidationResponseBuilder stages(Map<String, StageResult> stages) {
            this.stages = stages;
            return this;
        }

        public FullValidationResponseBuilder overallStatus(String overallStatus) {
            this.overallStatus = overallStatus;
            return this;
        }

        public FullValidationResponseBuilder overallScore(Integer overallScore) {
            this.overallScore = overallScore;
            return this;
        }

        public FullValidationResponseBuilder totalDurationMs(Long totalDurationMs) {
            this.totalDurationMs = totalDurationMs;
            return this;
        }

        public FullValidationResponseBuilder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public FullValidationResponse build() {
            return new FullValidationResponse(validationTaskId, stages, overallStatus, overallScore, totalDurationMs,
                    completedAt);
        }
    }

    // Getters and Setters
    public UUID getValidationTaskId() {
        return validationTaskId;
    }

    public void setValidationTaskId(UUID validationTaskId) {
        this.validationTaskId = validationTaskId;
    }

    public Map<String, StageResult> getStages() {
        return stages;
    }

    public void setStages(Map<String, StageResult> stages) {
        this.stages = stages;
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public Integer getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(Integer overallScore) {
        this.overallScore = overallScore;
    }

    public Long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(Long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    /**
     * 单阶段结果
     */
    public static class StageResult {
        private String stage;
        private String status;
        private Boolean passed;
        private String errorMessage;
        private Map<String, Object> details;
        private Long durationMs;

        public StageResult() {
        }

        public StageResult(String stage, String status, Boolean passed, String errorMessage,
                Map<String, Object> details, Long durationMs) {
            this.stage = stage;
            this.status = status;
            this.passed = passed;
            this.errorMessage = errorMessage;
            this.details = details;
            this.durationMs = durationMs;
        }

        public static StageResultBuilder builder() {
            return new StageResultBuilder();
        }

        public static class StageResultBuilder {
            private String stage;
            private String status;
            private Boolean passed;
            private String errorMessage;
            private Map<String, Object> details;
            private Long durationMs;

            public StageResultBuilder stage(String stage) {
                this.stage = stage;
                return this;
            }

            public StageResultBuilder status(String status) {
                this.status = status;
                return this;
            }

            public StageResultBuilder passed(Boolean passed) {
                this.passed = passed;
                return this;
            }

            public StageResultBuilder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }

            public StageResultBuilder details(Map<String, Object> details) {
                this.details = details;
                return this;
            }

            public StageResultBuilder durationMs(Long durationMs) {
                this.durationMs = durationMs;
                return this;
            }

            public StageResult build() {
                return new StageResult(stage, status, passed, errorMessage, details, durationMs);
            }
        }

        public String getStage() {
            return stage;
        }

        public void setStage(String stage) {
            this.stage = stage;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Boolean getPassed() {
            return passed;
        }

        public void setPassed(Boolean passed) {
            this.passed = passed;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public Map<String, Object> getDetails() {
            return details;
        }

        public void setDetails(Map<String, Object> details) {
            this.details = details;
        }

        public Long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(Long durationMs) {
            this.durationMs = durationMs;
        }
    }
}
