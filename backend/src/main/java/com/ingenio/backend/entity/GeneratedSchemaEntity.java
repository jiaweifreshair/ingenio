package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 生成的数据库Schema实体
 *
 * 用于存储AI生成的数据库表结构：
 * - 表定义（tables）
 * - DDL SQL脚本（ddlSql）
 * - 版本管理（version）
 * - 部署状态（status）
 */
@Data
@TableName(value = "generated_schemas")
public class GeneratedSchemaEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /**
     * 租户ID
     */
    private UUID tenantId;

    /**
     * 用户ID
     */
    private UUID userId;

    /**
     * 关联的生成任务ID
     */
    private UUID taskId;

    /**
     * Schema名称（如blog_schema）
     */
    private String schemaName;

    /**
     * Schema描述
     */
    private String description;

    /**
     * 表结构定义（JSON数组）
     * 格式：[{
     *   tableName: "blogs",
     *   columns: [{name: "id", type: "UUID", primaryKey: true}, ...],
     *   indexes: [{name: "idx_blogs_user", columns: ["user_id"]}],
     *   foreignKeys: [{column: "user_id", references: "users(id)"}]
     * }]
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> tables;

    /**
     * DDL SQL脚本（完整的CREATE TABLE语句）
     */
    @TableField("ddl_sql")
    private String ddlSql;

    /**
     * Schema版本号
     */
    private Integer version;

    /**
     * 状态：draft（草稿）/approved（已批准）/deployed（已部署）/deprecated（已废弃）
     */
    private String status;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;
}
