package com.ingenio.backend.dto.auth;

/**
 * 验证码类型枚举
 *
 * 用途：
 * - REGISTER: 用户注册时的邮箱验证
 * - RESET_PASSWORD: 找回密码时的邮箱验证
 * - CHANGE_EMAIL: 修改邮箱时的验证（验证新邮箱）
 *
 * Redis Key设计：
 * - verification:code:{type}:{email} = "123456" (TTL: 5分钟)
 * - verification:rate_limit:{email} = timestamp (TTL: 60秒)
 *
 * @author Ingenio Team
 * @since Phase 5.1
 */
public enum VerificationType {
    /**
     * 注册验证
     */
    REGISTER("register", "注册验证"),

    /**
     * 找回密码
     */
    RESET_PASSWORD("reset_password", "找回密码"),

    /**
     * 修改邮箱
     */
    CHANGE_EMAIL("change_email", "修改邮箱");

    private final String code;
    private final String description;

    VerificationType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
