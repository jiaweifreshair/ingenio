package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ingenio.backend.config.UUIDv8TypeHandler;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 生成任务实体类
 */
@TableName(value = "generation_tasks")
public class GenerationTaskEntity {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    @TableField(typeHandler = UUIDv8TypeHandler.class)
    private UUID id;

    @TableField(value = "tenant_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID tenantId;

    @TableField(value = "user_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID userId;

    @TableField("task_name")
    private String taskName;

    @TableField("user_requirement")
    private String userRequirement;

    @TableField("status")
    private String status;

    @TableField("current_agent")
    private String currentAgent;

    @TableField("progress")
    private Integer progress;

    @TableField(value = "agents_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> agentsInfo;

    @TableField(value = "plan_result", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> planResult;

    @TableField(value = "app_spec_content", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> appSpecContent;

    @TableField(value = "validate_result", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> validateResult;

    @TableField(value = "app_spec_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID appSpecId;

    @TableField("quality_score")
    private Integer qualityScore;

    @TableField("download_url")
    private String downloadUrl;

    @TableField("preview_url")
    private String previewUrl;

    @TableField(value = "token_usage", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> tokenUsage;

    @TableField("error_message")
    private String errorMessage;

    @TableField("started_at")
    private Instant startedAt;

    @TableField("completed_at")
    private Instant completedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    public GenerationTaskEntity() {
    }

    public GenerationTaskEntity(UUID id, UUID tenantId, UUID userId, String taskName, String userRequirement,
            String status, String currentAgent, Integer progress, Map<String, Object> agentsInfo,
            Map<String, Object> planResult, Map<String, Object> appSpecContent, Map<String, Object> validateResult,
            UUID appSpecId, Integer qualityScore, String downloadUrl, String previewUrl, Map<String, Object> tokenUsage,
            String errorMessage, Instant startedAt, Instant completedAt, Instant createdAt, Instant updatedAt,
            Map<String, Object> metadata) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.taskName = taskName;
        this.userRequirement = userRequirement;
        this.status = status;
        this.currentAgent = currentAgent;
        this.progress = progress;
        this.agentsInfo = agentsInfo;
        this.planResult = planResult;
        this.appSpecContent = appSpecContent;
        this.validateResult = validateResult;
        this.appSpecId = appSpecId;
        this.qualityScore = qualityScore;
        this.downloadUrl = downloadUrl;
        this.previewUrl = previewUrl;
        this.tokenUsage = tokenUsage;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.metadata = metadata;
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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getUserRequirement() {
        return userRequirement;
    }

    public void setUserRequirement(String userRequirement) {
        this.userRequirement = userRequirement;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentAgent() {
        return currentAgent;
    }

    public void setCurrentAgent(String currentAgent) {
        this.currentAgent = currentAgent;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Map<String, Object> getAgentsInfo() {
        return agentsInfo;
    }

    public void setAgentsInfo(Map<String, Object> agentsInfo) {
        this.agentsInfo = agentsInfo;
    }

    public Map<String, Object> getPlanResult() {
        return planResult;
    }

    public void setPlanResult(Map<String, Object> planResult) {
        this.planResult = planResult;
    }

    public Map<String, Object> getAppSpecContent() {
        return appSpecContent;
    }

    public void setAppSpecContent(Map<String, Object> appSpecContent) {
        this.appSpecContent = appSpecContent;
    }

    public Map<String, Object> getValidateResult() {
        return validateResult;
    }

    public void setValidateResult(Map<String, Object> validateResult) {
        this.validateResult = validateResult;
    }

    public UUID getAppSpecId() {
        return appSpecId;
    }

    public void setAppSpecId(UUID appSpecId) {
        this.appSpecId = appSpecId;
    }

    public Integer getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Integer qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public Map<String, Object> getTokenUsage() {
        return tokenUsage;
    }

    public void setTokenUsage(Map<String, Object> tokenUsage) {
        this.tokenUsage = tokenUsage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public enum Status {
        PENDING("pending", "等待执行"),
        PLANNING("planning", "需求规划中"),
        EXECUTING("executing", "AppSpec生成中"),
        VALIDATING("validating", "质量验证中"),
        GENERATING("generating", "代码生成中"),
        COMPLETED("completed", "任务完成"),
        FAILED("failed", "任务失败"),
        CANCELLED("cancelled", "任务取消");

        private final String value;
        private final String description;

        Status(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static Status fromValue(String value) {
            for (Status status : Status.values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown status: " + value);
        }
    }

    public enum AgentType {
        PLAN("plan", "规划Agent"),
        EXECUTE("execute", "执行Agent"),
        VALIDATE("validate", "验证Agent"),
        GENERATE("generate", "代码生成Agent");

        private final String value;
        private final String description;

        AgentType(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }
    }

    public boolean isFinished() {
        return Status.COMPLETED.getValue().equals(status)
                || Status.FAILED.getValue().equals(status)
                || Status.CANCELLED.getValue().equals(status);
    }

    public boolean isRunning() {
        return Status.PLANNING.getValue().equals(status)
                || Status.EXECUTING.getValue().equals(status)
                || Status.VALIDATING.getValue().equals(status)
                || Status.GENERATING.getValue().equals(status);
    }

    public void updateStatus(Status newStatus, AgentType currentAgent, Integer progress) {
        this.status = newStatus.getValue();
        if (currentAgent != null) {
            this.currentAgent = currentAgent.getValue();
        }
        if (progress != null) {
            this.progress = progress;
        }

        if (newStatus == Status.COMPLETED || newStatus == Status.FAILED || newStatus == Status.CANCELLED) {
            this.completedAt = Instant.now();
        }

        if (newStatus == Status.PLANNING && this.startedAt == null) {
            this.startedAt = Instant.now();
        }
    }
}