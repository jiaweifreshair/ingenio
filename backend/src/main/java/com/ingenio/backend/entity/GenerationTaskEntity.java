package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ingenio.backend.config.UUIDv8TypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 生成任务实体类
 * 存储AI生成任务的执行状态和结果信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "generation_tasks")
public class GenerationTaskEntity {

    /**
     * 任务ID（UUID主键）
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    @TableField(typeHandler = UUIDv8TypeHandler.class)
    private UUID id;

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
     * 任务名称
     */
    @TableField("task_name")
    private String taskName;

    /**
     * 用户原始需求描述
     */
    @TableField("user_requirement")
    private String userRequirement;

    /**
     * 任务状态
     */
    @TableField("status")
    private String status;

    /**
     * 当前执行的Agent
     */
    @TableField("current_agent")
    private String currentAgent;

    /**
     * 任务进度百分比（0-100）
     */
    @TableField("progress")
    private Integer progress;

    /**
     * Agent状态信息（JSON格式）
     */
    @TableField(value = "agents_info", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> agentsInfo;

    /**
     * PlanAgent执行结果
     */
    @TableField(value = "plan_result", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> planResult;

    /**
     * ExecuteAgent生成的AppSpec内容
     */
    @TableField(value = "app_spec_content", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> appSpecContent;

    /**
     * ValidateAgent验证结果
     */
    @TableField(value = "validate_result", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> validateResult;

    /**
     * 最终生成的AppSpec记录ID
     */
    @TableField(value = "app_spec_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID appSpecId;

    /**
     * AppSpec质量评分（0-100）
     */
    @TableField("quality_score")
    private Integer qualityScore;

    /**
     * 生成代码的下载链接
     */
    @TableField("download_url")
    private String downloadUrl;

    /**
     * 应用预览链接
     */
    @TableField("preview_url")
    private String previewUrl;

    /**
     * Token使用统计信息
     */
    @TableField(value = "token_usage", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> tokenUsage;

    /**
     * 任务失败时的错误信息
     */
    @TableField("error_message")
    private String errorMessage;

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

    /**
     * 任务元数据
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 任务状态枚举
     */
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

    /**
     * Agent类型枚举
     */
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

    /**
     * 检查任务是否已完成（成功或失败）
     */
    public boolean isFinished() {
        return Status.COMPLETED.getValue().equals(status)
            || Status.FAILED.getValue().equals(status)
            || Status.CANCELLED.getValue().equals(status);
    }

    /**
     * 检查任务是否正在执行中
     */
    public boolean isRunning() {
        return Status.PLANNING.getValue().equals(status)
            || Status.EXECUTING.getValue().equals(status)
            || Status.VALIDATING.getValue().equals(status)
            || Status.GENERATING.getValue().equals(status);
    }

    /**
     * 更新任务状态
     */
    public void updateStatus(Status newStatus, AgentType currentAgent, Integer progress) {
        this.status = newStatus.getValue();
        if (currentAgent != null) {
            this.currentAgent = currentAgent.getValue();
        }
        if (progress != null) {
            this.progress = progress;
        }

        // 自动设置完成时间
        if (newStatus == Status.COMPLETED || newStatus == Status.FAILED || newStatus == Status.CANCELLED) {
            this.completedAt = Instant.now();
        }

        // 任务开始执行时设置开始时间
        if (newStatus == Status.PLANNING && this.startedAt == null) {
            this.startedAt = Instant.now();
        }
    }
}