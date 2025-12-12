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
 * 用户响应DTO
 * 用于返回用户信息给前端（不包含密码等敏感信息）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    /**
     * 用户ID
     */
    private UUID id;

    /**
     * 租户ID
     */
    private UUID tenantId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 用户角色：admin/teacher/student
     */
    private String role;

    /**
     * 用户状态：active/inactive/suspended
     */
    private String status;

    /**
     * 头像URL
     */
    private String avatarUrl;

    /**
     * 最后登录时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Instant lastLoginAt;

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
     * 元数据（JSON）
     */
    private Map<String, Object> metadata;
}
