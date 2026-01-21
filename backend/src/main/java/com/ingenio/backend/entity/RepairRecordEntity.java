package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * V2.0 AI自动修复记录实体
 *
 * 用途：
 * - 记录AI自动修复的完整历程
 * - 支持最多3次迭代修复
 * - 记录每次修复的策略、代码变更、验证结果
 * - 为人工介入提供上下文
 *
 * 修复流程：
 * 1. 验证失败 → 触发修复
 * 2. 生成修复建议 → 应用修复
 * 3. 重新验证 → 判断是否成功
 * 4. 3次失败 → 升级人工介入
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 4
 */
@TableName(value = "repair_records")
public class RepairRecordEntity {

    /**
     * 修复记录ID
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
     * 关联的验证结果ID（触发修复的验证）
     */
    @TableField("validation_result_id")
    private UUID validationResultId;

    /**
     * 失败类型
     * - compile: 编译错误
     * - test: 测试失败
     * - type_error: 类型错误
     * - dependency: 依赖缺失
     * - business_logic: 业务逻辑错误
     */
    @TableField("failure_type")
    private String failureType;

    /**
     * 修复状态
     * - pending: 待修复
     * - analyzing: 分析中
     * - repairing: 修复中
     * - validating: 验证中
     * - success: 修复成功
     * - failed: 修复失败
     * - escalated: 已升级人工
     */
    @TableField("status")
    private String status;

    /**
     * 当前迭代次数（1-3）
     */
    @TableField("current_iteration")
    private Integer currentIteration;

    /**
     * 最大迭代次数（默认3次）
     */
    @TableField("max_iterations")
    private Integer maxIterations;

    /**
     * 修复策略
     * - type_inference: 类型推断修复
     * - dependency_install: 依赖安装
     * - code_refactor: 代码重构
     * - business_logic_fix: 业务逻辑修复
     * - ai_suggestion: AI建议修复
     */
    @TableField("repair_strategy")
    private String repairStrategy;

    /**
     * 错误详情（JSON格式）
     * 包含：错误行号、错误消息、堆栈信息
     */
    @TableField(value = "error_details", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> errorDetails;

    /**
     * 修复建议列表（JSON数组）
     * AI生成的多个修复方案
     */
    @TableField(value = "repair_suggestions", typeHandler = JacksonTypeHandler.class)
    private java.util.List<Map<String, Object>> repairSuggestions;

    /**
     * 选中的修复方案索引（从0开始）
     */
    @TableField("selected_suggestion_index")
    private Integer selectedSuggestionIndex;

    /**
     * 修复后的代码变更（JSON格式）
     * { "filePath": "src/App.tsx", "before": "...", "after": "..." }
     */
    @TableField(value = "code_changes", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> codeChanges;

    /**
     * 受影响的文件列表（JSON数组）
     */
    @TableField(value = "affected_files", typeHandler = JacksonTypeHandler.class)
    private java.util.List<String> affectedFiles;

    /**
     * 修复后的验证结果ID
     */
    @TableField("repair_validation_result_id")
    private UUID repairValidationResultId;

    /**
     * 是否修复成功
     */
    @TableField("is_success")
    private Boolean isSuccess;

    /**
     * 失败原因（如果修复失败）
     */
    @TableField("failure_reason")
    private String failureReason;

    /**
     * 是否已升级人工介入
     */
    @TableField("is_escalated")
    private Boolean isEscalated;

    /**
     * 升级时间
     */
    @TableField("escalated_at")
    private Instant escalatedAt;

    /**
     * 升级通知是否已发送
     */
    @TableField("notification_sent")
    private Boolean notificationSent;

    /**
     * AI推理过程（JSON格式）
     * 记录AI的思考过程，用于调试和优化
     */
    @TableField(value = "ai_reasoning", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> aiReasoning;

    /**
     * Token使用量统计（JSON格式）
     * { "promptTokens": 1000, "completionTokens": 500, "totalTokens": 1500 }
     */
    @TableField(value = "token_usage", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> tokenUsage;

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
     * 修复耗时（毫秒）
     */
    @TableField("duration_ms")
    private Long durationMs;

    /**
     * 元数据（JSON格式）
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

    public RepairRecordEntity() {
    }

    public RepairRecordEntity(UUID id, UUID tenantId, UUID appSpecId, UUID validationResultId, String failureType,
            String status, Integer currentIteration, Integer maxIterations, String repairStrategy,
            Map<String, Object> errorDetails, java.util.List<Map<String, Object>> repairSuggestions,
            Integer selectedSuggestionIndex, Map<String, Object> codeChanges, java.util.List<String> affectedFiles,
            UUID repairValidationResultId, Boolean isSuccess, String failureReason, Boolean isEscalated,
            Instant escalatedAt, Boolean notificationSent, Map<String, Object> aiReasoning,
            Map<String, Object> tokenUsage, Instant startedAt, Instant completedAt, Long durationMs,
            Map<String, Object> metadata, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.appSpecId = appSpecId;
        this.validationResultId = validationResultId;
        this.failureType = failureType;
        this.status = status;
        this.currentIteration = currentIteration;
        this.maxIterations = maxIterations;
        this.repairStrategy = repairStrategy;
        this.errorDetails = errorDetails;
        this.repairSuggestions = repairSuggestions;
        this.selectedSuggestionIndex = selectedSuggestionIndex;
        this.codeChanges = codeChanges;
        this.affectedFiles = affectedFiles;
        this.repairValidationResultId = repairValidationResultId;
        this.isSuccess = isSuccess;
        this.failureReason = failureReason;
        this.isEscalated = isEscalated;
        this.escalatedAt = escalatedAt;
        this.notificationSent = notificationSent;
        this.aiReasoning = aiReasoning;
        this.tokenUsage = tokenUsage;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
        this.metadata = metadata;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static RepairRecordEntityBuilder builder() {
        return new RepairRecordEntityBuilder();
    }

    public static class RepairRecordEntityBuilder {
        private UUID id;
        private UUID tenantId;
        private UUID appSpecId;
        private UUID validationResultId;
        private String failureType;
        private String status;
        private Integer currentIteration;
        private Integer maxIterations;
        private String repairStrategy;
        private Map<String, Object> errorDetails;
        private java.util.List<Map<String, Object>> repairSuggestions;
        private Integer selectedSuggestionIndex;
        private Map<String, Object> codeChanges;
        private java.util.List<String> affectedFiles;
        private UUID repairValidationResultId;
        private Boolean isSuccess;
        private String failureReason;
        private Boolean isEscalated;
        private Instant escalatedAt;
        private Boolean notificationSent;
        private Map<String, Object> aiReasoning;
        private Map<String, Object> tokenUsage;
        private Instant startedAt;
        private Instant completedAt;
        private Long durationMs;
        private Map<String, Object> metadata;
        private Instant createdAt;
        private Instant updatedAt;

        public RepairRecordEntityBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public RepairRecordEntityBuilder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public RepairRecordEntityBuilder appSpecId(UUID appSpecId) {
            this.appSpecId = appSpecId;
            return this;
        }

        public RepairRecordEntityBuilder validationResultId(UUID validationResultId) {
            this.validationResultId = validationResultId;
            return this;
        }

        public RepairRecordEntityBuilder failureType(String failureType) {
            this.failureType = failureType;
            return this;
        }

        public RepairRecordEntityBuilder status(String status) {
            this.status = status;
            return this;
        }

        public RepairRecordEntityBuilder currentIteration(Integer currentIteration) {
            this.currentIteration = currentIteration;
            return this;
        }

        public RepairRecordEntityBuilder maxIterations(Integer maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public RepairRecordEntityBuilder repairStrategy(String repairStrategy) {
            this.repairStrategy = repairStrategy;
            return this;
        }

        public RepairRecordEntityBuilder errorDetails(Map<String, Object> errorDetails) {
            this.errorDetails = errorDetails;
            return this;
        }

        public RepairRecordEntityBuilder repairSuggestions(java.util.List<Map<String, Object>> repairSuggestions) {
            this.repairSuggestions = repairSuggestions;
            return this;
        }

        public RepairRecordEntityBuilder selectedSuggestionIndex(Integer selectedSuggestionIndex) {
            this.selectedSuggestionIndex = selectedSuggestionIndex;
            return this;
        }

        public RepairRecordEntityBuilder codeChanges(Map<String, Object> codeChanges) {
            this.codeChanges = codeChanges;
            return this;
        }

        public RepairRecordEntityBuilder affectedFiles(java.util.List<String> affectedFiles) {
            this.affectedFiles = affectedFiles;
            return this;
        }

        public RepairRecordEntityBuilder repairValidationResultId(UUID repairValidationResultId) {
            this.repairValidationResultId = repairValidationResultId;
            return this;
        }

        public RepairRecordEntityBuilder isSuccess(Boolean isSuccess) {
            this.isSuccess = isSuccess;
            return this;
        }

        public RepairRecordEntityBuilder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }

        public RepairRecordEntityBuilder isEscalated(Boolean isEscalated) {
            this.isEscalated = isEscalated;
            return this;
        }

        public RepairRecordEntityBuilder escalatedAt(Instant escalatedAt) {
            this.escalatedAt = escalatedAt;
            return this;
        }

        public RepairRecordEntityBuilder notificationSent(Boolean notificationSent) {
            this.notificationSent = notificationSent;
            return this;
        }

        public RepairRecordEntityBuilder aiReasoning(Map<String, Object> aiReasoning) {
            this.aiReasoning = aiReasoning;
            return this;
        }

        public RepairRecordEntityBuilder tokenUsage(Map<String, Object> tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }

        public RepairRecordEntityBuilder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public RepairRecordEntityBuilder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public RepairRecordEntityBuilder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public RepairRecordEntityBuilder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public RepairRecordEntityBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public RepairRecordEntityBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public RepairRecordEntity build() {
            return new RepairRecordEntity(id, tenantId, appSpecId, validationResultId, failureType, status,
                    currentIteration, maxIterations, repairStrategy, errorDetails, repairSuggestions,
                    selectedSuggestionIndex, codeChanges, affectedFiles, repairValidationResultId, isSuccess,
                    failureReason, isEscalated, escalatedAt, notificationSent, aiReasoning, tokenUsage, startedAt,
                    completedAt, durationMs, metadata, createdAt, updatedAt);
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

    public UUID getValidationResultId() {
        return validationResultId;
    }

    public void setValidationResultId(UUID validationResultId) {
        this.validationResultId = validationResultId;
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCurrentIteration() {
        return currentIteration;
    }

    public void setCurrentIteration(Integer currentIteration) {
        this.currentIteration = currentIteration;
    }

    public Integer getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(Integer maxIterations) {
        this.maxIterations = maxIterations;
    }

    public String getRepairStrategy() {
        return repairStrategy;
    }

    public void setRepairStrategy(String repairStrategy) {
        this.repairStrategy = repairStrategy;
    }

    public Map<String, Object> getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(Map<String, Object> errorDetails) {
        this.errorDetails = errorDetails;
    }

    public java.util.List<Map<String, Object>> getRepairSuggestions() {
        return repairSuggestions;
    }

    public void setRepairSuggestions(java.util.List<Map<String, Object>> repairSuggestions) {
        this.repairSuggestions = repairSuggestions;
    }

    public Integer getSelectedSuggestionIndex() {
        return selectedSuggestionIndex;
    }

    public void setSelectedSuggestionIndex(Integer selectedSuggestionIndex) {
        this.selectedSuggestionIndex = selectedSuggestionIndex;
    }

    public Map<String, Object> getCodeChanges() {
        return codeChanges;
    }

    public void setCodeChanges(Map<String, Object> codeChanges) {
        this.codeChanges = codeChanges;
    }

    public java.util.List<String> getAffectedFiles() {
        return affectedFiles;
    }

    public void setAffectedFiles(java.util.List<String> affectedFiles) {
        this.affectedFiles = affectedFiles;
    }

    public UUID getRepairValidationResultId() {
        return repairValidationResultId;
    }

    public void setRepairValidationResultId(UUID repairValidationResultId) {
        this.repairValidationResultId = repairValidationResultId;
    }

    public Boolean getIsSuccess() {
        return isSuccess;
    }

    public void setIsSuccess(Boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Boolean getIsEscalated() {
        return isEscalated;
    }

    public void setIsEscalated(Boolean isEscalated) {
        this.isEscalated = isEscalated;
    }

    public Instant getEscalatedAt() {
        return escalatedAt;
    }

    public void setEscalatedAt(Instant escalatedAt) {
        this.escalatedAt = escalatedAt;
    }

    public Boolean getNotificationSent() {
        return notificationSent;
    }

    public void setNotificationSent(Boolean notificationSent) {
        this.notificationSent = notificationSent;
    }

    public Map<String, Object> getAiReasoning() {
        return aiReasoning;
    }

    public void setAiReasoning(Map<String, Object> aiReasoning) {
        this.aiReasoning = aiReasoning;
    }

    public Map<String, Object> getTokenUsage() {
        return tokenUsage;
    }

    public void setTokenUsage(Map<String, Object> tokenUsage) {
        this.tokenUsage = tokenUsage;
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
     * 失败类型枚举
     */
    public enum FailureType {
        COMPILE("compile"),
        TEST("test"),
        TYPE_ERROR("type_error"),
        DEPENDENCY("dependency"),
        BUSINESS_LOGIC("business_logic");

        private final String value;

        FailureType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 修复状态枚举
     */
    public enum Status {
        PENDING("pending"),
        ANALYZING("analyzing"),
        REPAIRING("repairing"),
        VALIDATING("validating"),
        SUCCESS("success"),
        FAILED("failed"),
        ESCALATED("escalated");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 修复策略枚举
     */
    public enum RepairStrategy {
        TYPE_INFERENCE("type_inference"),
        DEPENDENCY_INSTALL("dependency_install"),
        CODE_REFACTOR("code_refactor"),
        BUSINESS_LOGIC_FIX("business_logic_fix"),
        AI_SUGGESTION("ai_suggestion");

        private final String value;

        RepairStrategy(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
