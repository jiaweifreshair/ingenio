package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 模板使用记录实体类
 * 统计模板使用情况和用户反馈
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "template_usage")
public class TemplateUsageEntity {

    /**
     * 使用记录ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 模板ID
     */
    @TableField("template_id")
    private UUID templateId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 生成的AppSpec ID
     */
    @TableField("app_spec_id")
    private UUID appSpecId;

    /**
     * 用户评分（1-5）
     */
    @TableField("rating")
    private Integer rating;

    /**
     * 用户反馈文本
     */
    @TableField("feedback")
    private String feedback;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;
}
