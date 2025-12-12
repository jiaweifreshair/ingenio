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
 * 生成的API接口实体
 *
 * 用于存储AI生成的API接口规格：
 * - OpenAPI 3.0规格（openapiSpec）
 * - API端点列表（endpoints）
 * - 版本管理（version）
 * - 部署状态（status）
 */
@Data
@TableName(value = "generated_apis")
public class GeneratedAPIEntity {

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
     * 关联的Schema ID
     */
    private UUID schemaId;

    /**
     * API名称（如BlogAPI）
     */
    private String apiName;

    /**
     * API描述
     */
    private String description;

    /**
     * OpenAPI 3.0完整规格（JSON格式）
     * 包含：info, servers, paths, components等
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> openapiSpec;

    /**
     * API端点列表（简化索引用）
     * 格式：[{method: "GET", path: "/api/blogs", summary: "获取博客列表"}]
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> endpoints;

    /**
     * API版本号（如1.0.0）
     */
    private String version;

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
