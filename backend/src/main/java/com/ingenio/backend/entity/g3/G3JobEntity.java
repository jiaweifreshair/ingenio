package com.ingenio.backend.entity.g3;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ingenio.backend.config.UUIDv8TypeHandler;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * G3引擎任务实体类
 * 存储代码生成任务的状态、配置和执行日志
 */
@Data
@Builder
@NoArgsConstructor
@TableName(value = "g3_jobs", autoResultMap = true)
public class G3JobEntity {

    /**
     * 任务ID（UUID主键）
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    @TableField(typeHandler = UUIDv8TypeHandler.class)
    private UUID id;

    /**
     * 关联的应用规格ID（可选）
     */
    @TableField(value = "app_spec_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID appSpecId;

    /**
     * 租户ID - 用于多租户隔离
     */
    @TableField(value = "tenant_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID tenantId;

    /**
     * 用户ID
     */
    @TableField(value = "user_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID userId;

    /**
     * 原始需求文本
     */
    @TableField("requirement")
    private String requirement;

    /**
     * Scout推荐模版上下文
     */
    @TableField("template_context")
    private String templateContext;

    // ==================== Blueprint（蓝图规范）====================

    /**
     * 匹配/选中的行业模板ID（可选）
     */
    @TableField(value = "matched_template_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID matchedTemplateId;

    /**
     * Blueprint 完整规范（JSONB，可选）
     */
    @TableField(value = "blueprint_spec", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> blueprintSpec;

    /**
     * Blueprint 模式是否启用
     */
    @TableField("blueprint_mode_enabled")
    private Boolean blueprintModeEnabled;

    /**
     * 任务状态
     * 
     * @see Status
     */
    @TableField("status")
    private String status;

    /**
     * 当前修复轮次（0表示首次生成）
     */
    @TableField("current_round")
    private Integer currentRound;

    /**
     * 最大修复轮次限制
     */
    @TableField("max_rounds")
    private Integer maxRounds;

    /**
     * OpenAPI契约YAML（由Architect Agent生成）
     */
    @TableField("contract_yaml")
    private String contractYaml;

    /**
     * 数据库Schema SQL（由Architect Agent生成）
     */
    @TableField("db_schema_sql")
    private String dbSchemaSql;

    /**
     * 契约锁定状态（锁定后不再修改）
     */
    @TableField("contract_locked")
    private Boolean contractLocked;

    /**
     * 契约锁定时间
     */
    @TableField("contract_locked_at")
    private Instant contractLockedAt;

    /**
     * 目标技术栈配置
     */
    @TableField(value = "target_stack", typeHandler = JacksonTypeHandler.class)
    private Map<String, String> targetStack;

    /**
     * 生成选项（如是否生成测试、是否启用缓存等）
     */
    @TableField(value = "generation_options", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> generationOptions;

    /**
     * 执行日志数组
     */
    @TableField(value = "logs", typeHandler = JacksonTypeHandler.class)
    private List<G3LogEntry> logs;

    /**
     * E2B沙箱实例ID
     */
    @TableField("sandbox_id")
    private String sandboxId;

    /**
     * 沙箱访问URL
     */
    @TableField("sandbox_url")
    private String sandboxUrl;

    /**
     * 沙箱提供商（e2b/docker/local）
     */
    @TableField("sandbox_provider")
    private String sandboxProvider;

    /**
     * 最近一次错误信息
     */
    @TableField("last_error")
    private String lastError;

    /**
     * 累计错误次数
     */
    @TableField("error_count")
    private Integer errorCount;

    /**
     * 任务开始执行时间
     */
    @TableField("started_at")
    private Instant startedAt;

    /**
     * 任务完成时间
     */
    @TableField("completed_at")
    private Instant completedAt;

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

    // Manual Boilerplate
    public G3JobEntity(UUID id, UUID appSpecId, UUID tenantId, UUID userId, String requirement, String templateContext,
            UUID matchedTemplateId, Map<String, Object> blueprintSpec, Boolean blueprintModeEnabled, String status,
            Integer currentRound, Integer maxRounds, String contractYaml, String dbSchemaSql, Boolean contractLocked,
            Instant contractLockedAt, Map<String, String> targetStack, Map<String, Object> generationOptions,
            List<G3LogEntry> logs, String sandboxId, String sandboxUrl, String sandboxProvider, String lastError,
            Integer errorCount, Instant startedAt, Instant completedAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.appSpecId = appSpecId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.requirement = requirement;
        this.templateContext = templateContext;
        this.matchedTemplateId = matchedTemplateId;
        this.blueprintSpec = blueprintSpec;
        this.blueprintModeEnabled = blueprintModeEnabled;
        this.status = status;
        this.currentRound = currentRound;
        this.maxRounds = maxRounds;
        this.contractYaml = contractYaml;
        this.dbSchemaSql = dbSchemaSql;
        this.contractLocked = contractLocked;
        this.contractLockedAt = contractLockedAt;
        this.targetStack = targetStack;
        this.generationOptions = generationOptions;
        this.logs = logs;
        this.sandboxId = sandboxId;
        this.sandboxUrl = sandboxUrl;
        this.sandboxProvider = sandboxProvider;
        this.lastError = lastError;
        this.errorCount = errorCount;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAppSpecId() {
        return appSpecId;
    }

    public void setAppSpecId(UUID appSpecId) {
        this.appSpecId = appSpecId;
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

    public String getRequirement() {
        return requirement;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }

    public String getTemplateContext() {
        return templateContext;
    }

    public void setTemplateContext(String templateContext) {
        this.templateContext = templateContext;
    }

    public UUID getMatchedTemplateId() {
        return matchedTemplateId;
    }

    public void setMatchedTemplateId(UUID matchedTemplateId) {
        this.matchedTemplateId = matchedTemplateId;
    }

    public Map<String, Object> getBlueprintSpec() {
        return blueprintSpec;
    }

    public void setBlueprintSpec(Map<String, Object> blueprintSpec) {
        this.blueprintSpec = blueprintSpec;
    }

    public Boolean getBlueprintModeEnabled() {
        return blueprintModeEnabled;
    }

    public void setBlueprintModeEnabled(Boolean blueprintModeEnabled) {
        this.blueprintModeEnabled = blueprintModeEnabled;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(Integer currentRound) {
        this.currentRound = currentRound;
    }

    public Integer getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(Integer maxRounds) {
        this.maxRounds = maxRounds;
    }

    public String getContractYaml() {
        return contractYaml;
    }

    public void setContractYaml(String contractYaml) {
        this.contractYaml = contractYaml;
    }

    public String getDbSchemaSql() {
        return dbSchemaSql;
    }

    public void setDbSchemaSql(String dbSchemaSql) {
        this.dbSchemaSql = dbSchemaSql;
    }

    public Boolean getContractLocked() {
        return contractLocked;
    }

    public void setContractLocked(Boolean contractLocked) {
        this.contractLocked = contractLocked;
    }

    public Instant getContractLockedAt() {
        return contractLockedAt;
    }

    public void setContractLockedAt(Instant contractLockedAt) {
        this.contractLockedAt = contractLockedAt;
    }

    public Map<String, String> getTargetStack() {
        return targetStack;
    }

    public void setTargetStack(Map<String, String> targetStack) {
        this.targetStack = targetStack;
    }

    public Map<String, Object> getGenerationOptions() {
        return generationOptions;
    }

    public void setGenerationOptions(Map<String, Object> generationOptions) {
        this.generationOptions = generationOptions;
    }

    public List<G3LogEntry> getLogs() {
        return logs;
    }

    public void setLogs(List<G3LogEntry> logs) {
        this.logs = logs;
    }

    public String getSandboxId() {
        return sandboxId;
    }

    public void setSandboxId(String sandboxId) {
        this.sandboxId = sandboxId;
    }

    public String getSandboxUrl() {
        return sandboxUrl;
    }

    public void setSandboxUrl(String sandboxUrl) {
        this.sandboxUrl = sandboxUrl;
    }

    public String getSandboxProvider() {
        return sandboxProvider;
    }

    public void setSandboxProvider(String sandboxProvider) {
        this.sandboxProvider = sandboxProvider;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Integer errorCount) {
        this.errorCount = errorCount;
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

    public static G3JobEntityBuilder builder() {
        return new G3JobEntityBuilder();
    }

    public static class G3JobEntityBuilder {
        private UUID id;
        private UUID appSpecId;
        private UUID tenantId;
        private UUID userId;
        private String requirement;
        private String templateContext;
        private UUID matchedTemplateId;
        private Map<String, Object> blueprintSpec;
        private Boolean blueprintModeEnabled;
        private String status;
        private Integer currentRound;
        private Integer maxRounds;
        private String contractYaml;
        private String dbSchemaSql;
        private Boolean contractLocked;
        private Instant contractLockedAt;
        private Map<String, String> targetStack;
        private Map<String, Object> generationOptions;
        private List<G3LogEntry> logs;
        private String sandboxId;
        private String sandboxUrl;
        private String sandboxProvider;
        private String lastError;
        private Integer errorCount;
        private Instant startedAt;
        private Instant completedAt;
        private Instant createdAt;
        private Instant updatedAt;

        G3JobEntityBuilder() {
        }

        public G3JobEntityBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public G3JobEntityBuilder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public G3JobEntityBuilder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public G3JobEntityBuilder requirement(String requirement) {
            this.requirement = requirement;
            return this;
        }

        public G3JobEntityBuilder templateContext(String templateContext) {
            this.templateContext = templateContext;
            return this;
        }

        public G3JobEntityBuilder status(String status) {
            this.status = status;
            return this;
        }

        public G3JobEntityBuilder currentRound(Integer currentRound) {
            this.currentRound = currentRound;
            return this;
        }

        public G3JobEntityBuilder maxRounds(Integer maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public G3JobEntityBuilder contractLocked(Boolean contractLocked) {
            this.contractLocked = contractLocked;
            return this;
        }

        public G3JobEntityBuilder errorCount(Integer errorCount) {
            this.errorCount = errorCount;
            return this;
        }

        public G3JobEntityBuilder sandboxProvider(String sandboxProvider) {
            this.sandboxProvider = sandboxProvider;
            return this;
        }

        public G3JobEntityBuilder logs(List<G3LogEntry> logs) {
            this.logs = logs;
            return this;
        }

        // ... omitted some builder methods for brevity but typically all fields should
        // be supported
        public G3JobEntityBuilder appSpecId(UUID appSpecId) {
            this.appSpecId = appSpecId;
            return this;
        }

        public G3JobEntityBuilder matchedTemplateId(UUID matchedTemplateId) {
            this.matchedTemplateId = matchedTemplateId;
            return this;
        }

        public G3JobEntityBuilder blueprintSpec(Map<String, Object> blueprintSpec) {
            this.blueprintSpec = blueprintSpec;
            return this;
        }

        public G3JobEntityBuilder blueprintModeEnabled(Boolean blueprintModeEnabled) {
            this.blueprintModeEnabled = blueprintModeEnabled;
            return this;
        }

        public G3JobEntityBuilder contractYaml(String contractYaml) {
            this.contractYaml = contractYaml;
            return this;
        }

        public G3JobEntityBuilder dbSchemaSql(String dbSchemaSql) {
            this.dbSchemaSql = dbSchemaSql;
            return this;
        }

        public G3JobEntityBuilder contractLockedAt(Instant contractLockedAt) {
            this.contractLockedAt = contractLockedAt;
            return this;
        }

        public G3JobEntityBuilder targetStack(Map<String, String> targetStack) {
            this.targetStack = targetStack;
            return this;
        }

        public G3JobEntityBuilder generationOptions(Map<String, Object> generationOptions) {
            this.generationOptions = generationOptions;
            return this;
        }

        public G3JobEntityBuilder sandboxId(String sandboxId) {
            this.sandboxId = sandboxId;
            return this;
        }

        public G3JobEntityBuilder sandboxUrl(String sandboxUrl) {
            this.sandboxUrl = sandboxUrl;
            return this;
        }

        public G3JobEntityBuilder lastError(String lastError) {
            this.lastError = lastError;
            return this;
        }

        public G3JobEntityBuilder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public G3JobEntityBuilder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public G3JobEntityBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public G3JobEntityBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public G3JobEntity build() {
            return new G3JobEntity(id, appSpecId, tenantId, userId, requirement, templateContext, matchedTemplateId,
                    blueprintSpec, blueprintModeEnabled, status, currentRound, maxRounds, contractYaml, dbSchemaSql,
                    contractLocked, contractLockedAt, targetStack, generationOptions, logs, sandboxId, sandboxUrl,
                    sandboxProvider, lastError, errorCount, startedAt, completedAt, createdAt, updatedAt);
        }
    }

    public enum Status {
        QUEUED("QUEUED", "排队等待"),
        PLANNING("PLANNING", "架构规划中"),
        CODING("CODING", "代码生成中"),
        TESTING("TESTING", "测试验证中"),
        COMPLETED("COMPLETED", "任务完成"),
        FAILED("FAILED", "任务失败");

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
            throw new IllegalArgumentException("Unknown G3 job status: " + value);
        }
    }

    public enum SandboxProvider {
        E2B("e2b", "E2B Cloud Sandbox"),
        DOCKER("docker", "Local Docker Container"),
        LOCAL("local", "Local Maven Build");

        private final String value;
        private final String description;

        SandboxProvider(String value, String description) {
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
        return Status.COMPLETED.getValue().equals(status) || Status.FAILED.getValue().equals(status);
    }

    public boolean isRunning() {
        return Status.PLANNING.getValue().equals(status) || Status.CODING.getValue().equals(status)
                || Status.TESTING.getValue().equals(status);
    }

    public boolean canRetry() {
        return currentRound < maxRounds;
    }

    public void addLog(G3LogEntry.Role role, String message, G3LogEntry.Level level) {
        if (this.logs == null) {
            this.logs = new ArrayList<>();
        }
        this.logs.add(G3LogEntry.builder()
                .timestamp(Instant.now().toString())
                .role(role.getValue())
                .message(message)
                .level(level.getValue())
                .build());
    }

    public void nextRound() {
        this.currentRound = (this.currentRound == null ? 0 : this.currentRound) + 1;
    }

    public void updateStatus(Status newStatus) {
        this.status = newStatus.getValue();
        if (newStatus == Status.PLANNING && this.startedAt == null) {
            this.startedAt = Instant.now();
        }
        if (newStatus == Status.COMPLETED || newStatus == Status.FAILED) {
            this.completedAt = Instant.now();
        }
    }

    public void lockContract() {
        this.contractLocked = true;
        this.contractLockedAt = Instant.now();
    }

    public void recordError(String error) {
        this.lastError = error;
        this.errorCount = (this.errorCount == null ? 0 : this.errorCount) + 1;
    }

    public static G3JobEntity create(String requirement, UUID userId, UUID tenantId, String templateContext) {
        return G3JobEntity.builder()
                .requirement(requirement)
                .userId(userId)
                .tenantId(tenantId)
                .templateContext(templateContext)
                .status(Status.QUEUED.getValue())
                .currentRound(0)
                .maxRounds(3)
                .contractLocked(false)
                .errorCount(0)
                .sandboxProvider(SandboxProvider.E2B.getValue())
                .logs(new ArrayList<>())
                .build();
    }
}
