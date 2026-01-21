package com.ingenio.backend.dto.response.validation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通用验证响应
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
public class ValidationResponse {

    /**
     * 验证结果ID
     */
    private UUID validationId;

    /**
     * 验证类型
     */
    private String validationType;

    /**
     * 是否通过验证
     */
    private Boolean passed;

    /**
     * 验证状态
     */
    private String status;

    /**
     * 质量评分（0-100）
     */
    private Integer qualityScore;

    /**
     * 验证详情
     */
    private Map<String, Object> details;

    /**
     * 错误消息列表
     */
    private List<String> errors;

    /**
     * 警告消息列表
     */
    private List<String> warnings;

    /**
     * 验证耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 完成时间
     */
    private Instant completedAt;

    public ValidationResponse() {
    }

    public ValidationResponse(UUID validationId, String validationType, Boolean passed, String status,
            Integer qualityScore, Map<String, Object> details, List<String> errors, List<String> warnings,
            Long durationMs, Instant completedAt) {
        this.validationId = validationId;
        this.validationType = validationType;
        this.passed = passed;
        this.status = status;
        this.qualityScore = qualityScore;
        this.details = details;
        this.errors = errors;
        this.warnings = warnings;
        this.durationMs = durationMs;
        this.completedAt = completedAt;
    }

    public static ValidationResponseBuilder builder() {
        return new ValidationResponseBuilder();
    }

    public static class ValidationResponseBuilder {
        private UUID validationId;
        private String validationType;
        private Boolean passed;
        private String status;
        private Integer qualityScore;
        private Map<String, Object> details;
        private List<String> errors;
        private List<String> warnings;
        private Long durationMs;
        private Instant completedAt;

        public ValidationResponseBuilder validationId(UUID validationId) {
            this.validationId = validationId;
            return this;
        }

        public ValidationResponseBuilder validationType(String validationType) {
            this.validationType = validationType;
            return this;
        }

        public ValidationResponseBuilder passed(Boolean passed) {
            this.passed = passed;
            return this;
        }

        public ValidationResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public ValidationResponseBuilder qualityScore(Integer qualityScore) {
            this.qualityScore = qualityScore;
            return this;
        }

        public ValidationResponseBuilder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public ValidationResponseBuilder errors(List<String> errors) {
            this.errors = errors;
            return this;
        }

        public ValidationResponseBuilder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public ValidationResponseBuilder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public ValidationResponseBuilder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public ValidationResponse build() {
            return new ValidationResponse(validationId, validationType, passed, status, qualityScore, details, errors,
                    warnings, durationMs, completedAt);
        }
    }

    // Getters and Setters
    public UUID getValidationId() {
        return validationId;
    }

    public void setValidationId(UUID validationId) {
        this.validationId = validationId;
    }

    public String getValidationType() {
        return validationType;
    }

    public void setValidationType(String validationType) {
        this.validationType = validationType;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Integer qualityScore) {
        this.qualityScore = qualityScore;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
