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
 * MCP认证配置实体类
 * 存储多数据源的认证信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "mcp_auth_configs")
public class MCPAuthConfigEntity {

    /**
     * 认证配置ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 租户ID
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 配置名称
     */
    @TableField("config_name")
    private String configName;

    /**
     * 显示名称
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 认证类型：oauth2/api_key/basic/jwt
     */
    @TableField("auth_type")
    private String authType;

    /**
     * 认证配置（JSON，需加密）
     */
    @TableField(value = "auth_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> authConfig;

    /**
     * 状态：active/expired/revoked
     */
    @TableField("status")
    private String status;

    /**
     * 过期时间
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
     * 逻辑删除标记
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /**
     * 元数据（JSON）
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 认证类型枚举
     */
    public enum AuthType {
        OAUTH2("oauth2"),
        API_KEY("api_key"),
        BASIC("basic"),
        JWT("jwt");

        private final String value;

        AuthType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 状态枚举
     */
    public enum Status {
        ACTIVE("active"),
        EXPIRED("expired"),
        REVOKED("revoked");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
