package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API密钥实体类
 * 用于管理用户的API访问密钥
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "api_keys")
public class ApiKeyEntity {

    /**
     * 密钥ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 密钥名称（用户自定义）
     */
    @TableField("name")
    private String name;

    /**
     * API密钥值（SHA256哈希存储）
     */
    @TableField("key_value")
    private String keyValue;

    /**
     * 密钥前缀（显示用，如：ing_xxxxx）
     */
    @TableField("key_prefix")
    private String keyPrefix;

    /**
     * 密钥描述
     */
    @TableField("description")
    private String description;

    /**
     * 权限范围（如：["read", "write"]）
     */
    @TableField(value = "scopes", typeHandler = JacksonTypeHandler.class)
    private List<String> scopes;

    /**
     * 是否启用
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * 最后使用时间
     */
    @TableField("last_used_at")
    private Instant lastUsedAt;

    /**
     * 最后使用IP
     */
    @TableField("last_used_ip")
    private String lastUsedIp;

    /**
     * 使用次数
     */
    @TableField("usage_count")
    private Integer usageCount;

    /**
     * 速率限制（每分钟请求数）
     */
    @TableField("rate_limit")
    private Integer rateLimit;

    /**
     * 过期时间（可选，永久有效则为NULL）
     */
    @TableField("expires_at")
    private Instant expiresAt;

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
     * 检查密钥是否有效
     *
     * @return 是否有效（已启用且未过期）
     */
    public boolean isValid() {
        return isActive && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }

    /**
     * 增加使用次数
     */
    public void incrementUsageCount() {
        this.usageCount = (this.usageCount == null ? 0 : this.usageCount) + 1;
        this.lastUsedAt = Instant.now();
    }
}
