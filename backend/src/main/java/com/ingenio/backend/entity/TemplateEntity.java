package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 模板实体类
 * 官方和社区提供的应用模板
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "templates")
public class TemplateEntity {

    /**
     * 模板ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 租户ID
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 模板唯一标识名称
     */
    @TableField("name")
    private String name;

    /**
     * 模板显示名称
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 模板描述
     */
    @TableField("description")
    private String description;

    /**
     * 缩略图URL
     */
    @TableField("thumbnail_url")
    private String thumbnailUrl;

    /**
     * 分类ID
     */
    @TableField("category_id")
    private UUID categoryId;

    /**
     * 标签数组
     */
    @TableField(value = "tags", typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /**
     * 模板内容（完整AppSpec JSON）
     */
    @TableField(value = "template_content", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> templateContent;

    /**
     * 模板类型：official/community/private
     */
    @TableField("template_type")
    private String templateType;

    /**
     * 状态：draft/published/archived
     */
    @TableField("status")
    private String status;

    /**
     * 使用次数
     */
    @TableField("usage_count")
    private Integer usageCount;

    /**
     * 平均评分（0-5）
     */
    @TableField("rating_average")
    private BigDecimal ratingAverage;

    /**
     * 评分次数
     */
    @TableField("rating_count")
    private Integer ratingCount;

    /**
     * 创建者用户ID
     */
    @TableField("created_by_user_id")
    private UUID createdByUserId;

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
     * 模板类型枚举
     */
    public enum TemplateType {
        OFFICIAL("official"),
        COMMUNITY("community"),
        PRIVATE("private");

        private final String value;

        TemplateType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 模板状态枚举
     */
    public enum Status {
        DRAFT("draft"),
        PUBLISHED("published"),
        ARCHIVED("archived");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
