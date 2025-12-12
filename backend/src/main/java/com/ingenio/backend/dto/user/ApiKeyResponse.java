package com.ingenio.backend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * API密钥响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyResponse {

    /**
     * 密钥ID
     */
    private String id;

    /**
     * 密钥名称
     */
    private String name;

    /**
     * 密钥前缀（显示用，如：ing_xxxxx）
     */
    private String keyPrefix;

    /**
     * 完整密钥（仅在创建时返回一次）
     */
    private String fullKey;

    /**
     * 密钥描述
     */
    private String description;

    /**
     * 权限范围
     */
    private List<String> scopes;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 最后使用时间
     */
    private Instant lastUsedAt;

    /**
     * 最后使用IP
     */
    private String lastUsedIp;

    /**
     * 使用次数
     */
    private Integer usageCount;

    /**
     * 速率限制
     */
    private Integer rateLimit;

    /**
     * 过期时间
     */
    private Instant expiresAt;

    /**
     * 创建时间
     */
    private Instant createdAt;
}
