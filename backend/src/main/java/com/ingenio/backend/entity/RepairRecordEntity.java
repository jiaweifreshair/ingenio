package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
