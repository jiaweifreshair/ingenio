package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ingenio.backend.config.UUIDv8TypeHandler;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 生成版本管理实体
 *
 * 用于版本控制和回滚：
 * - 版本号管理（versionNumber）
 * - 完整快照（snapshot）
 * - 变更记录（changes）
 * - 部署状态（isDeployed）
 */
@Data
@TableName(value = "generation_versions")
public class GenerationVersionEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    @TableField(typeHandler = UUIDv8TypeHandler.class)
    private UUID id;

    /**
     * 租户ID
     */
    @TableField(value = "tenant_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID tenantId;

    /**
     * 用户ID
     */
    @TableField(value = "user_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID userId;

    /**
     * 关联的生成任务ID
     */
    @TableField(value = "task_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID taskId;

    /**
     * 版本号（自增，从1开始）
     */
    private Integer versionNumber;

    /**
     * 版本类型
     * plan/schema/code/validation_failed/validation_success/fix/rollback/final
     */
    private String versionType;

    /**
     * 版本标签（如v1.0.0）
     */
    private String versionTag;

    /**
     * 版本描述
     */
    private String description;

    /**
     * 变更记录（与上一版本对比）
     * 格式：{
     *   added: ["BlogCommentEntity"],
     *   modified: ["BlogEntity"],
     *   removed: ["TempTable"],
     *   details: {...}
     * }
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> changes;

    /**
     * 完整快照（包含schema、api、code的完整数据）
     * 格式：{
     *   schema: {...},
     *   api: {...},
     *   codeFiles: [...]
     * }
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> snapshot;

    /**
     * 父版本ID（用于构建版本树）
     */
    @TableField(value = "parent_version_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID parentVersionId;

    /**
     * 是否已部署
     */
    private Boolean isDeployed;

    /**
     * 部署时间
     */
    private Instant deployedAt;

    /**
     * 创建时间
     */
    private Instant createdAt;
}
