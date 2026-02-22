package com.ingenio.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * AppSpec响应DTO
 * 用于返回AppSpec信息给前端
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppSpecResponse {

    /**
     * AppSpec ID
     */
    private UUID id;

    /**
     * 租户ID
     */
    private UUID tenantId;

    /**
     * 创建者用户ID
     */
    private UUID createdByUserId;

    /**
     * AppSpec完整JSON内容
     */
    private Map<String, Object> specContent;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 父版本ID
     */
    private UUID parentVersionId;

    /**
     * 状态：draft/validated/generated/published
     */
    private String status;

    /**
     * 质量评分（0-100）
     */
    private Integer qualityScore;

    /**
     * 创建时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;

    /**
     * 元数据（JSON）
     */
    private Map<String, Object> metadata;

    /**
     * 创建者信息（用于前端展示）
     */
    private UserResponse creator;
}
