package com.ingenio.backend.dto.request.validation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * 质量门禁验证请求
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
public class QualityGateRequest {

    /**
     * AppSpec ID
     */
    @NotNull(message = "AppSpec ID不能为空")
    private UUID appSpecId;

    /**
     * 质量指标
     * - coverage: 测试覆盖率（0-100）
     * - complexity: 圈复杂度（0-100）
     * - duplicatio n: 代码重复率（0-100）
     */
    @NotNull(message = "质量指标不能为空")
    private Map<String, Integer> metrics;

    /**
     * 覆盖率阈值（默认85%）
     */
    @Min(value = 0, message = "覆盖率阈值必须≥0")
    @Max(value = 100, message = "覆盖率阈值必须≤100")
    private Integer coverageThreshold = 85;

    /**
     * 圈复杂度阈值（默认10）
     */
    @Min(value = 0, message = "圈复杂度阈值必须≥0")
    private Integer complexityThreshold = 10;

    public QualityGateRequest() {
    }

    public QualityGateRequest(UUID appSpecId, Map<String, Integer> metrics, Integer coverageThreshold,
            Integer complexityThreshold) {
        this.appSpecId = appSpecId;
        this.metrics = metrics;
        this.coverageThreshold = coverageThreshold != null ? coverageThreshold : 85;
        this.complexityThreshold = complexityThreshold != null ? complexityThreshold : 10;
    }

    public static QualityGateRequestBuilder builder() {
        return new QualityGateRequestBuilder();
    }

    public static class QualityGateRequestBuilder {
        private UUID appSpecId;
        private Map<String, Integer> metrics;
        private Integer coverageThreshold = 85;
        private Integer complexityThreshold = 10;

        public QualityGateRequestBuilder appSpecId(UUID appSpecId) {
            this.appSpecId = appSpecId;
            return this;
        }

        public QualityGateRequestBuilder metrics(Map<String, Integer> metrics) {
            this.metrics = metrics;
            return this;
        }

        public QualityGateRequestBuilder coverageThreshold(Integer coverageThreshold) {
            this.coverageThreshold = coverageThreshold;
            return this;
        }

        public QualityGateRequestBuilder complexityThreshold(Integer complexityThreshold) {
            this.complexityThreshold = complexityThreshold;
            return this;
        }

        public QualityGateRequest build() {
            return new QualityGateRequest(appSpecId, metrics, coverageThreshold, complexityThreshold);
        }
    }

    // Getters and Setters
    public UUID getAppSpecId() {
        return appSpecId;
    }

    public void setAppSpecId(UUID appSpecId) {
        this.appSpecId = appSpecId;
    }

    public Map<String, Integer> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Integer> metrics) {
        this.metrics = metrics;
    }

    public Integer getCoverageThreshold() {
        return coverageThreshold;
    }

    public void setCoverageThreshold(Integer coverageThreshold) {
        this.coverageThreshold = coverageThreshold;
    }

    public Integer getComplexityThreshold() {
        return complexityThreshold;
    }

    public void setComplexityThreshold(Integer complexityThreshold) {
        this.complexityThreshold = complexityThreshold;
    }
}
