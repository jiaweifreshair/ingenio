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
 * 派生关系实体类
 * 记录项目的派生（Fork）关系
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "forks")
public class ForkEntity {

    /**
     * 派生记录ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 源项目ID
     */
    @TableField("source_project_id")
    private UUID sourceProjectId;

    /**
     * 派生项目ID
     */
    @TableField("forked_project_id")
    private UUID forkedProjectId;

    /**
     * 派生者租户ID
     */
    @TableField("forked_by_tenant_id")
    private UUID forkedByTenantId;

    /**
     * 派生者用户ID
     */
    @TableField("forked_by_user_id")
    private UUID forkedByUserId;

    /**
     * 派生时的定制化修改（JSON）
     * 例如：{ "changedPages": ["home"], "addedFeatures": ["darkMode"] }
     */
    @TableField(value = "customizations", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> customizations;

    /**
     * 派生说明
     */
    @TableField("fork_description")
    private String forkDescription;

    /**
     * 派生时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 元数据（JSON）
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;
}
