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
 * 社交互动实体类
 * 记录点赞、收藏、评论等社交互动
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "social_interactions")
public class SocialInteractionEntity {

    /**
     * 互动记录ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 租户ID
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 目标项目ID
     */
    @TableField("project_id")
    private UUID projectId;

    /**
     * 互动类型：like/favorite/comment/view
     */
    @TableField("interaction_type")
    private String interactionType;

    /**
     * 评论内容（仅评论类型）
     */
    @TableField("comment_content")
    private String commentContent;

    /**
     * 互动时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 元数据（JSON）
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 互动类型枚举
     */
    public enum InteractionType {
        LIKE("like"),
        FAVORITE("favorite"),
        COMMENT("comment"),
        VIEW("view");

        private final String value;

        InteractionType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
