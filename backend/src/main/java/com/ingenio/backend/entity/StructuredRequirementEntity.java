package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 结构化需求实体（AI分析结果）
 *
 * 用于存储AI从自然语言需求中提取的结构化信息：
 * - 实体列表（entities）
 * - 实体关系（relationships）
 * - 业务操作（operations）
 * - 约束条件（constraints）
 */
@Data
@TableName(value = "structured_requirements")
public class StructuredRequirementEntity {

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
     * 原始自然语言需求
     */
    private String rawRequirement;

    /**
     * 提取的实体列表
     * 格式：[{name: "Blog", attributes: [{name: "title", type: "string"}], description: "博客文章"}]
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> entities;

    /**
     * 实体间关系
     * 格式：[{from: "Blog", to: "Comment", type: "one-to-many", cardinality: "1:N"}]
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> relationships;

    /**
     * 业务操作列表
     * 格式：[{name: "createBlog", type: "POST", params: ["title", "content"], description: "创建博客"}]
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> operations;

    /**
     * 约束条件
     * 格式：[{field: "title", constraint: "max_length", value: 200}]
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> constraints;

    /**
     * 使用的AI模型（如qwen-max, gpt-4等）
     */
    private String aiModel;

    /**
     * 置信度分数（0-100）
     */
    private BigDecimal confidenceScore;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;
}
