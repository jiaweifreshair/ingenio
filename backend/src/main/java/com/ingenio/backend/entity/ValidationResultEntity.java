package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * V2.0 验证结果实体
 *
 * 用途：
 * - 记录三环验证（编译→测试→业务）的完整结果
 * - 支持并行验证和串行验证两种模式
 * - 为AI自动修复提供验证历史
 *
 * 三环验证说明：
 * 1. 编译验证（Compile Validation）：TypeScript/Java编译检查
 * 2. 测试验证（Test Validation）：单元测试+覆盖率+质量门禁
 * 3. 业务验证（Business Validation）：API契约+Schema+业务流程
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
@TableName(value = "validation_results")
public class ValidationResultEntity {

    /**
     * 验证结果ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    /**
     * 租户ID - 用于租户隔离
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 关联的AppSpec ID
     */
    @TableField("app_spec_id")
    private UUID appSpecId;

    /**
     * 验证类型
     * - compile: 编译验证
     * - test: 测试验证（单元测试+覆盖率）
     * - quality_gate: 质量门禁验证
     * - contract: API契约验证
     * - schema: 数据库Schema验证
     * - business_flow: 业务流程验证
     * - full: 三环集成验证
     */
    @TableField("validation_type")
    private String validationType;

    /**
     * 验证状态
     * - running: 正在验证
     * - passed: 验证通过
     * - failed: 验证失败
     * - skipped: 跳过（前置验证失败）
     */
    @TableField("status")
    private String status;

    /**
     * 是否通过验证
     */
    @TableField("is_passed")
    private Boolean isPassed;

    /**
     * 验证详细结果（JSON格式）
     * 编译验证：{ compileSuccess: true, errors: [] }
     * 测试验证：{ totalTests: 10, passedTests: 9, failedTests: 1 }
     * 覆盖率验证：{ lineCoverage: 88.5, branchCoverage: 85.2 }
     */
    @TableField(value = "validation_details", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> validationDetails;

    /**
     * 错误消息列表（JSON数组）
     */
    @TableField(value = "error_messages", typeHandler = JacksonTypeHandler.class)
    private java.util.List<String> errorMessages;

    /**
     * 警告消息列表（JSON数组）
     */
    @TableField(value = "warning_messages", typeHandler = JacksonTypeHandler.class)
    private java.util.List<String> warningMessages;

    /**
     * 质量评分（0-100）
     * 编译验证：100分或0分
     * 测试覆盖率：实际覆盖率百分比
     * 质量门禁：综合评分
     */
    @TableField("quality_score")
    private Integer qualityScore;

    /**
     * 开始时间
     */
    @TableField("started_at")
    private Instant startedAt;

    /**
     * 完成时间
     */
    @TableField("completed_at")
    private Instant completedAt;

    /**
     * 验证耗时（毫秒）
     */
    @TableField("duration_ms")
    private Long durationMs;

    /**
     * 元数据（JSON格式）
     * 存储额外信息，如：
     * - 编译器版本
     * - 测试框架版本
     * - 验证工具配置
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    public ValidationResultEntity() {
    }

    public ValidationResultEntity(UUID id, UUID tenantId, UUID appSpecId, String validationType, String status,
            Boolean isPassed, Map<String, Object> validationDetails, java.util.List<String> errorMessages,
            java.util.List<String> warningMessages, Integer qualityScore, Instant startedAt, Instant completedAt,
            Long durationMs, Map<String, Object> metadata, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.appSpecId = appSpecId;
        this.validationType = validationType;
        this.status = status;
        this.isPassed = isPassed;
        this.validationDetails = validationDetails;
        this.errorMessages = errorMessages;
        this.warningMessages = warningMessages;
        this.qualityScore = qualityScore;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
        this.metadata = metadata;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ValidationResultEntityBuilder builder() {
        return new ValidationResultEntityBuilder();
    }

    public static class ValidationResultEntityBuilder {
        private UUID id;
        private UUID tenantId;
        private UUID appSpecId;
        private String validationType;
        private String status;
        private Boolean isPassed;
        private Map<String, Object> validationDetails;
        private java.util.List<String> errorMessages;
        private java.util.List<String> warningMessages;
        private Integer qualityScore;
        private Instant startedAt;
        private Instant completedAt;
        private Long durationMs;
        private Map<String, Object> metadata;
        private Instant createdAt;
        private Instant updatedAt;

        public ValidationResultEntityBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public ValidationResultEntityBuilder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public ValidationResultEntityBuilder appSpecId(UUID appSpecId) {
            this.appSpecId = appSpecId;
            return this;
        }

        public ValidationResultEntityBuilder validationType(String validationType) {
            this.validationType = validationType;
            return this;
        }

        public ValidationResultEntityBuilder status(String status) {
            this.status = status;
            return this;
        }

        public ValidationResultEntityBuilder isPassed(Boolean isPassed) {
            this.isPassed = isPassed;
            return this;
        }

        public ValidationResultEntityBuilder validationDetails(Map<String, Object> validationDetails) {
            this.validationDetails = validationDetails;
            return this;
        }

        public ValidationResultEntityBuilder errorMessages(java.util.List<String> errorMessages) {
            this.errorMessages = errorMessages;
            return this;
        }

        public ValidationResultEntityBuilder warningMessages(java.util.List<String> warningMessages) {
            this.warningMessages = warningMessages;
            return this;
        }

        public ValidationResultEntityBuilder qualityScore(Integer qualityScore) {
            this.qualityScore = qualityScore;
            return this;
        }

        public ValidationResultEntityBuilder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public ValidationResultEntityBuilder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public ValidationResultEntityBuilder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public ValidationResultEntityBuilder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ValidationResultEntityBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ValidationResultEntityBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public ValidationResultEntity build() {
            return new ValidationResultEntity(id, tenantId, appSpecId, validationType, status, isPassed,
                    validationDetails, errorMessages, warningMessages, qualityScore, startedAt, completedAt, durationMs,
                    metadata, createdAt, updatedAt);
        }
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getAppSpecId() {
        return appSpecId;
    }

    public void setAppSpecId(UUID appSpecId) {
        this.appSpecId = appSpecId;
    }

    public String getValidationType() {
        return validationType;
    }

    public void setValidationType(String validationType) {
        this.validationType = validationType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsPassed() {
        return isPassed;
    }

    public void setIsPassed(Boolean isPassed) {
        this.isPassed = isPassed;
    }

    public Map<String, Object> getValidationDetails() {
        return validationDetails;
    }

    public void setValidationDetails(Map<String, Object> validationDetails) {
        this.validationDetails = validationDetails;
    }

    public java.util.List<String> getErrorMessages() {
        return errorMessages;
    }

    public void setErrorMessages(java.util.List<String> errorMessages) {
        this.errorMessages = errorMessages;
    }

    public java.util.List<String> getWarningMessages() {
        return warningMessages;
    }

    public void setWarningMessages(java.util.List<String> warningMessages) {
        this.warningMessages = warningMessages;
    }

    public Integer getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Integer qualityScore) {
        this.qualityScore = qualityScore;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 验证类型枚举
     */
    public enum ValidationType {
        COMPILE("compile"),
        TEST("test"),
        COVERAGE("coverage"),
        QUALITY_GATE("quality_gate"),
        CONTRACT("contract"),
        SCHEMA("schema"),
        BUSINESS_FLOW("business_flow"),
        FULL("full");

        private final String value;

        ValidationType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 验证状态枚举
     */
    public enum Status {
        RUNNING("running"),
        PASSED("passed"),
        FAILED("failed"),
        SKIPPED("skipped");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
