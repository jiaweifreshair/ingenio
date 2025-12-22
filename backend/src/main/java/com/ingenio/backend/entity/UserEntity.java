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
 * 用户实体类
 * 管理用户认证信息和基本资料
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "users")
public class UserEntity {

    /**
     * 用户ID（UUID）
     * 使用ASSIGN_UUID类型，由MyBatis-Plus在Java端生成UUID
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private UUID id;

    /**
     * 租户ID - 用于租户隔离（UUID类型，与其他表保持一致）
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 用户名 - 唯一标识
     */
    @TableField("username")
    private String username;

    /**
     * 邮箱地址 - 唯一标识
     */
    @TableField("email")
    private String email;

    /**
     * 密码哈希值（bcrypt）
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 用户角色：admin/user/viewer
     */
    @TableField("role")
    private String role;

    /**
     * 权限列表（JSON数组）
     */
    @TableField(value = "permissions", exist = false, typeHandler = JacksonTypeHandler.class)
    private List<String> permissions;

    /**
     * 用户状态：active/inactive/suspended
     */
    @TableField("status")
    private String status;

    /**
     * 用户显示名称
     */
    @TableField(value = "display_name", exist = false)
    private String displayName;

    /**
     * 头像URL
     */
    @TableField("avatar_url")
    private String avatarUrl;

    /**
     * 手机号码
     */
    @TableField(value = "phone", exist = false)
    private String phone;

    /**
     * 个人简介
     */
    @TableField(value = "bio", exist = false)
    private String bio;

    /**
     * 邮箱是否已验证
     */
    @TableField(value = "email_verified", exist = false)
    private Boolean emailVerified;

    /**
     * 手机号是否已验证
     */
    @TableField(value = "phone_verified", exist = false)
    private Boolean phoneVerified;

    /**
     * 最后登录时间
     */
    @TableField(value = "last_login_at", exist = false)
    private Instant lastLoginAt;

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
     * 元数据（JSON）
     */
    @TableField(value = "metadata", exist = false, typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 用户角色枚举
     *
     * SUPER_ADMIN: 超级管理员 - 拥有所有权限，包括删除用户、系统配置
     * ADMIN: 管理员 - 拥有管理权限，但不能删除用户或修改系统配置
     * USER: 普通用户 - 拥有基础权限，可管理自己的资源
     * VIEWER: 查看者 - 仅有查看权限，不能创建或修改资源
     */
    public enum Role {
        SUPER_ADMIN("super_admin"),
        ADMIN("admin"),
        USER("user"),
        VIEWER("viewer");

        private final String value;

        Role(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        /**
         * 根据字符串值获取对应的角色枚举
         *
         * @param value 角色字符串值（如："admin"、"user"）
         * @return 对应的Role枚举，如果未找到返回null
         */
        public static Role fromValue(String value) {
            for (Role role : Role.values()) {
                if (role.value.equalsIgnoreCase(value)) {
                    return role;
                }
            }
            return null;
        }
    }

    /**
     * 用户状态枚举
     */
    public enum Status {
        ACTIVE("active"),
        INACTIVE("inactive"),
        SUSPENDED("suspended");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
