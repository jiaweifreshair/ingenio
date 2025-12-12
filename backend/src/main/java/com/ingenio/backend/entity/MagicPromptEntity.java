package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 魔法提示词模板实体类
 * 存储针对不同年龄段学生的快速启动模板
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "magic_prompts")
public class MagicPromptEntity {

    /**
     * 模板ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 模板标题
     */
    @TableField("title")
    private String title;

    /**
     * 模板描述
     */
    @TableField("description")
    private String description;

    /**
     * 封面图片URL
     */
    @TableField("cover_image_url")
    private String coverImageUrl;

    /**
     * 提示词模板（包含变量占位符）
     * 例如："创建一个{subject}学习{tool_type}，包含{features}功能"
     */
    @TableField("prompt_template")
    private String promptTemplate;

    /**
     * 预填充变量（JSON）
     * 例如：{ "subject": "数学", "tool_type": "工具", "features": "练习和测试" }
     */
    @TableField(value = "default_variables", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> defaultVariables;

    /**
     * 年龄分组：elementary/middle_school/high_school/university
     */
    @TableField("age_group")
    private String ageGroup;

    /**
     * 分类：education/game/productivity/creative/social
     */
    @TableField("category")
    private String category;

    /**
     * 难度级别：beginner/intermediate/advanced
     */
    @TableField("difficulty_level")
    private String difficultyLevel;

    /**
     * 示例项目ID（可选）
     */
    @TableField("example_project_id")
    private UUID exampleProjectId;

    /**
     * 使用次数
     */
    @TableField("usage_count")
    private Integer usageCount;

    /**
     * 点赞数
     */
    @TableField("like_count")
    private Integer likeCount;

    /**
     * 状态：active/draft/archived
     */
    @TableField("status")
    private String status;

    /**
     * 标签数组
     */
    @TableField(value = "tags", typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

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
     * 创建者用户ID
     */
    @TableField("created_by_user_id")
    private UUID createdByUserId;

    /**
     * 元数据（JSON）
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 年龄分组枚举
     */
    public enum AgeGroup {
        ELEMENTARY("elementary"),
        MIDDLE_SCHOOL("middle_school"),
        HIGH_SCHOOL("high_school"),
        UNIVERSITY("university");

        private final String value;

        AgeGroup(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 分类枚举
     */
    public enum Category {
        EDUCATION("education"),
        GAME("game"),
        PRODUCTIVITY("productivity"),
        CREATIVE("creative"),
        SOCIAL("social");

        private final String value;

        Category(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 难度级别枚举
     */
    public enum DifficultyLevel {
        BEGINNER("beginner"),
        INTERMEDIATE("intermediate"),
        ADVANCED("advanced");

        private final String value;

        DifficultyLevel(String value) {
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
        DRAFT("draft"),
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
