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
 * MCP动态表记录实体类
 * 支持多数据源动态表创建
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "mcp_dynamic_tables")
public class MCPDynamicTableEntity {

    /**
     * 动态表记录ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 租户ID
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 表名
     */
    @TableField("table_name")
    private String tableName;

    /**
     * 显示名称
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 描述
     */
    @TableField("description")
    private String description;

    /**
     * 表结构定义（JSON Schema）
     */
    @TableField(value = "schema_definition", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> schemaDefinition;

    /**
     * 数据源类型：postgresql/mysql/api/graphql
     */
    @TableField("datasource_type")
    private String datasourceType;

    /**
     * 数据源配置（JSON）
     */
    @TableField(value = "datasource_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> datasourceConfig;

    /**
     * 同步策略：manual/realtime/scheduled
     */
    @TableField("sync_strategy")
    private String syncStrategy;

    /**
     * 同步间隔（秒）
     */
    @TableField("sync_interval")
    private Integer syncInterval;

    /**
     * 最后同步时间
     */
    @TableField("last_synced_at")
    private Instant lastSyncedAt;

    /**
     * 状态：active/paused/error
     */
    @TableField("status")
    private String status;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

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
     * 同步策略枚举
     */
    public enum SyncStrategy {
        MANUAL("manual"),
        REALTIME("realtime"),
        SCHEDULED("scheduled");

        private final String value;

        SyncStrategy(String value) {
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
        PAUSED("paused"),
        ERROR("error");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
