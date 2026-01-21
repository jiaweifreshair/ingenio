package com.ingenio.backend.service.g3.hooks;

import com.ingenio.backend.entity.g3.G3LogEntry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * G3 Hook 上下文。
 *
 * <p>承载一次 Hook 触发所需的关键信息，便于策略判断与审计记录。</p>
 */
public class G3HookContext {

    /**
     * Hook 事件类型。
     */
    private final G3HookEventType eventType;

    /**
     * 任务ID（可为空）。
     */
    private final UUID jobId;

    /**
     * 租户ID（可为空）。
     */
    private final UUID tenantId;

    /**
     * 用户ID（可为空）。
     */
    private final UUID userId;

    /**
     * 工具名称（仅工具事件使用）。
     */
    private final String toolName;

    /**
     * 工具输入（命令/路径/查询等）。
     */
    private final String toolInput;

    /**
     * 模型名称（仅模型事件使用）。
     */
    private final String modelName;

    /**
     * Prompt 预览（已截断）。
     */
    private final String promptPreview;

    /**
     * Prompt token 数量（可为空）。
     */
    private final Integer promptTokens;

    /**
     * Completion token 数量（可为空）。
     */
    private final Integer completionTokens;

    /**
     * 总 token 数量（可为空）。
     */
    private final Integer totalTokens;

    /**
     * 调用耗时（毫秒，可为空）。
     */
    private final Long durationMs;

    /**
     * 工具执行退出码（可为空）。
     */
    private final Integer exitCode;

    /**
     * 是否成功（可为空）。
     */
    private final Boolean success;

    /**
     * 错误信息（可为空）。
     */
    private final String errorMessage;

    /**
     * 日志回调（用于写入任务日志）。
     */
    private final Consumer<G3LogEntry> logConsumer;

    /**
     * 扩展元数据（可为空）。
     */
    private final Map<String, Object> metadata;

    private G3HookContext(Builder builder) {
        this.eventType = builder.eventType;
        this.jobId = builder.jobId;
        this.tenantId = builder.tenantId;
        this.userId = builder.userId;
        this.toolName = builder.toolName;
        this.toolInput = builder.toolInput;
        this.modelName = builder.modelName;
        this.promptPreview = builder.promptPreview;
        this.promptTokens = builder.promptTokens;
        this.completionTokens = builder.completionTokens;
        this.totalTokens = builder.totalTokens;
        this.durationMs = builder.durationMs;
        this.exitCode = builder.exitCode;
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
        this.logConsumer = builder.logConsumer;
        this.metadata = builder.metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public G3HookEventType getEventType() {
        return eventType;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolInput() {
        return toolInput;
    }

    public String getModelName() {
        return modelName;
    }

    public String getPromptPreview() {
        return promptPreview;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public Boolean getSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Consumer<G3LogEntry> getLogConsumer() {
        return logConsumer;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * G3 Hook 上下文构建器。
     *
     * <p>用于按需填充字段，避免构造函数过长。</p>
     */
    public static class Builder {
        private G3HookEventType eventType;
        private UUID jobId;
        private UUID tenantId;
        private UUID userId;
        private String toolName;
        private String toolInput;
        private String modelName;
        private String promptPreview;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private Long durationMs;
        private Integer exitCode;
        private Boolean success;
        private String errorMessage;
        private Consumer<G3LogEntry> logConsumer;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder eventType(G3HookEventType eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder jobId(UUID jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder toolInput(String toolInput) {
            this.toolInput = toolInput;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder promptPreview(String promptPreview) {
            this.promptPreview = promptPreview;
            return this;
        }

        public Builder promptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public Builder completionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        public Builder totalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Builder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder exitCode(Integer exitCode) {
            this.exitCode = exitCode;
            return this;
        }

        public Builder success(Boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder logConsumer(Consumer<G3LogEntry> logConsumer) {
            this.logConsumer = logConsumer;
            return this;
        }

        public Builder metadata(String key, Object value) {
            if (key != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        public G3HookContext build() {
            return new G3HookContext(this);
        }
    }
}
