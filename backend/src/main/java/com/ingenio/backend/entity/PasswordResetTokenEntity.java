package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 密码重置令牌实体类
 * 用于管理密码重置流程的临时令牌
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("password_reset_tokens")
public class PasswordResetTokenEntity {

    /**
     * 令牌ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 重置令牌（64位随机字符串）
     */
    @TableField("token")
    private String token;

    /**
     * 过期时间（默认1小时后）
     */
    @TableField("expires_at")
    private Instant expiresAt;

    /**
     * 是否已使用
     */
    @TableField("used")
    private Boolean used;

    /**
     * 使用时间
     */
    @TableField("used_at")
    private Instant usedAt;

    /**
     * 请求IP地址
     */
    @TableField("ip_address")
    private String ipAddress;

    /**
     * User Agent
     */
    @TableField("user_agent")
    private String userAgent;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 检查令牌是否有效
     *
     * @return 是否有效（未过期且未使用）
     */
    public boolean isValid() {
        return !used && expiresAt.isAfter(Instant.now());
    }

    /**
     * 标记令牌为已使用
     */
    public void markAsUsed() {
        this.used = true;
        this.usedAt = Instant.now();
    }
}
