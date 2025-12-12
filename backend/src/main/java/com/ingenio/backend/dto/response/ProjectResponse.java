package com.ingenio.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 项目响应DTO
 * 用于返回项目信息给前端
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {

    /**
     * 项目ID
     */
    private UUID id;

    /**
     * 租户ID
     */
    private UUID tenantId;

    /**
     * 所有者用户ID
     */
    private UUID userId;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 项目描述
     */
    private String description;

    /**
     * 封面图片URL
     */
    private String coverImageUrl;

    /**
     * 关联的AppSpec ID
     */
    private UUID appSpecId;

    /**
     * 项目状态：draft/published/archived
     */
    private String status;

    /**
     * 可见性：private/public/unlisted
     */
    private String visibility;

    /**
     * 浏览次数
     */
    private Integer viewCount;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 派生数
     */
    private Integer forkCount;

    /**
     * 评论数
     */
    private Integer commentCount;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 年龄分组：elementary/middle_school/high_school/university
     */
    private String ageGroup;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Instant createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Instant updatedAt;

    /**
     * 发布时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Instant publishedAt;

    /**
     * 元数据（JSON）
     */
    private Map<String, Object> metadata;

    /**
     * 所有者信息（用于前端展示）
     */
    private UserResponse owner;

    /**
     * 当前用户是否点赞
     */
    private Boolean isLiked;

    /**
     * 当前用户是否收藏
     */
    private Boolean isFavorited;
}
