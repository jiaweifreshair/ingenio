package com.ingenio.backend.entity.g3;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ingenio.backend.config.UUIDv8TypeHandler;
import lombok.AllArgsConstructor;
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
 *
 * 核心工作流：
 * 1. QUEUED: 任务排队等待
 * 2. PLANNING: Architect Agent 生成契约(OpenAPI + DB Schema)
 * 3. CODING: Coder Agents 生成代码
 * 4. TESTING: Sandbox 编译/测试验证
 * 5. COMPLETED/FAILED: 任务结束
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
     * 任务状态
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
     * 默认：{"backend": "spring-boot", "frontend": "react", "database": "postgresql"}
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
     * 格式：[{timestamp, role, message, level}]
     * @see G3LogEntry
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

    /**
     * 任务状态枚举
     */
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

    /**
     * 沙箱提供商枚举
     */
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

    /**
     * 检查任务是否已完成（成功或失败）
     */
    public boolean isFinished() {
        return Status.COMPLETED.getValue().equals(status)
            || Status.FAILED.getValue().equals(status);
    }

    /**
     * 检查任务是否正在执行中
     */
    public boolean isRunning() {
        return Status.PLANNING.getValue().equals(status)
            || Status.CODING.getValue().equals(status)
            || Status.TESTING.getValue().equals(status);
    }

    /**
     * 检查是否可以继续修复
     */
    public boolean canRetry() {
        return currentRound < maxRounds;
    }

    /**
     * 添加执行日志
     */
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

    /**
     * 进入下一轮修复
     */
    public void nextRound() {
        this.currentRound = (this.currentRound == null ? 0 : this.currentRound) + 1;
    }

    /**
     * 更新任务状态
     */
    public void updateStatus(Status newStatus) {
        this.status = newStatus.getValue();

        // 自动设置时间戳
        if (newStatus == Status.PLANNING && this.startedAt == null) {
            this.startedAt = Instant.now();
        }
        if (newStatus == Status.COMPLETED || newStatus == Status.FAILED) {
            this.completedAt = Instant.now();
        }
    }

    /**
     * 锁定契约（不再修改OpenAPI和Schema）
     */
    public void lockContract() {
        this.contractLocked = true;
        this.contractLockedAt = Instant.now();
    }

    /**
     * 记录错误
     */
    public void recordError(String error) {
        this.lastError = error;
        this.errorCount = (this.errorCount == null ? 0 : this.errorCount) + 1;
    }

    /**
     * 创建新的G3任务
     */
    public static G3JobEntity create(String requirement, UUID userId, UUID tenantId) {
        return G3JobEntity.builder()
            .requirement(requirement)
            .userId(userId)
            .tenantId(tenantId)
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
