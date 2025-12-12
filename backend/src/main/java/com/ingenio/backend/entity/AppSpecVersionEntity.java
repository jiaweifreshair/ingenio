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
 * AppSpec版本实体类
 * 记录每次AI生成的完整推理过程（用于"时光机调试"功能）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "app_spec_versions")
public class AppSpecVersionEntity {

    /**
     * 版本ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 关联的AppSpec ID
     */
    @TableField("app_spec_id")
    private UUID appSpecId;

    /**
     * 版本号（从1开始递增）
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * AppSpec快照（完整JSON）
     */
    @TableField(value = "snapshot_content", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> snapshotContent;

    /**
     * AI推理过程元数据（包含PlanAgent/ExecuteAgent/ValidateAgent的决策过程）
     * 格式：
     * {
     *   "planAgent": { "understanding": "...", "decisions": [...], "alternatives": [...] },
     *   "executeAgent": { "reasoning": "...", "codeChoices": [...], "tradeoffs": [...] },
     *   "validateAgent": { "checks": [...], "warnings": [...], "suggestions": [...] },
     *   "userInput": "原始用户需求",
     *   "timestamp": "2024-01-01T00:00:00Z",
     *   "modelVersion": "deepseek-chat-v1",
     *   "confidence": 0.95
     * }
     */
    @TableField(value = "generation_metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> generationMetadata;

    /**
     * 变更描述（用户可编辑）
     */
    @TableField("change_description")
    private String changeDescription;

    /**
     * 变更类型：user_edit/ai_generation/rollback/fork
     */
    @TableField("change_type")
    private String changeType;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 创建者用户ID
     */
    @TableField("created_by_user_id")
    private UUID createdByUserId;

    /**
     * 元数据（JSON）
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 变更类型枚举
     */
    public enum ChangeType {
        USER_EDIT("user_edit"),
        AI_GENERATION("ai_generation"),
        ROLLBACK("rollback"),
        FORK("fork");

        private final String value;

        ChangeType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
