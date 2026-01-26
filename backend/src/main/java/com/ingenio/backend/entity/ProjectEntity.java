package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ingenio.backend.common.handler.PostgreSQLStringArrayTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 项目实体类
 * 学生创作的项目（用于社区展示和分享）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "projects")
public class ProjectEntity {

    /**
     * 项目ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 租户ID
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 所有者用户ID
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 项目名称
     */
    @TableField("name")
    private String name;

    /**
     * 项目描述
     */
    @TableField("description")
    private String description;

    /**
     * 封面图片URL
     */
    @TableField("cover_image_url")
    private String coverImageUrl;

    /**
     * 关联的AppSpec ID（当前活跃版本）
     */
    @TableField("app_spec_id")
    private UUID appSpecId;

    /**
     * 项目状态：draft/published/archived
     */
    @TableField("status")
    private String status;

    /**
     * 可见性：private/public/unlisted
     */
    @TableField("visibility")
    private String visibility;

    /**
     * 浏览次数
     */
    @TableField("view_count")
    private Integer viewCount;

    /**
     * 点赞数
     */
    @TableField("like_count")
    private Integer likeCount;

    /**
     * 派生数
     */
    @TableField("fork_count")
    private Integer forkCount;

    /**
     * 评论数
     */
    @TableField("comment_count")
    private Integer commentCount;

    /**
     * 标签数组（用于分类和搜索）
     * PostgreSQL数组类型映射到Java List
     */
    @TableField(value = "tags", typeHandler = PostgreSQLStringArrayTypeHandler.class)
    private List<String> tags;

    /**
     * 年龄分组：elementary/middle_school/high_school/university
     */
    @TableField("age_group")
    private String ageGroup;

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
     * 发布时间
     */
    @TableField("published_at")
    private Instant publishedAt;

    /**
     * 元数据（JSON）
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 项目状态枚举
     * - DRAFT: 草稿（初始状态，需求已创建但未生成代码）
     * - GENERATING: 生成中（正在执行代码生成）
     * - COMPLETED: 生成完成（代码生成成功，可预览）
     * - ARCHIVED: 已归档（用户手动归档）
     */
    public enum Status {
        DRAFT("draft"),
        GENERATING("generating"),
        COMPLETED("completed"),
        ARCHIVED("archived");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        /**
         * 从字符串值获取枚举
         *
         * @param value 状态字符串
         * @return 对应的枚举值，如果无匹配则返回 null
         */
        public static Status fromValue(String value) {
            for (Status status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return null;
        }
    }

    /**
     * 可见性枚举
     */
    public enum Visibility {
        PRIVATE("private"),
        PUBLIC("public"),
        UNLISTED("unlisted");

        private final String value;

        Visibility(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

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
}
