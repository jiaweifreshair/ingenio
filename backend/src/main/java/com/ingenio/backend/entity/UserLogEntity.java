package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 用户操作日志实体类
 * 用于记录用户的所有关键操作
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "user_logs")
public class UserLogEntity {

    /**
     * 日志ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 操作类型（如：login/logout/create_project）
     */
    @TableField("action")
    private String action;

    /**
     * 操作分类（auth/user/project/app等）
     */
    @TableField("action_category")
    private String actionCategory;

    /**
     * 操作描述
     */
    @TableField("description")
    private String description;

    /**
     * 资源类型（project/app/version）
     */
    @TableField("resource_type")
    private String resourceType;

    /**
     * 资源ID
     */
    @TableField("resource_id")
    private String resourceId;

    /**
     * IP地址
     */
    @TableField("ip_address")
    private String ipAddress;

    /**
     * User Agent
     */
    @TableField("user_agent")
    private String userAgent;

    /**
     * HTTP方法
     */
    @TableField("request_method")
    private String requestMethod;

    /**
     * 请求路径
     */
    @TableField("request_path")
    private String requestPath;

    /**
     * 操作状态：success/failure
     */
    @TableField("status")
    private String status;

    /**
     * 错误信息（失败时）
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 执行时间（毫秒）
     */
    @TableField("execution_time_ms")
    private Integer executionTimeMs;

    /**
     * 额外元数据（JSON）
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 操作分类枚举
     */
    public enum ActionCategory {
        AUTH("auth"),           // 认证相关
        USER("user"),           // 用户管理
        PROJECT("project"),     // 项目管理
        APP("app"),             // 应用管理
        VERSION("version"),     // 版本管理
        PUBLISH("publish"),     // 发布相关
        SYSTEM("system");       // 系统操作

        private final String value;

        ActionCategory(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 操作状态枚举
     */
    public enum Status {
        SUCCESS("success"),
        FAILURE("failure");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
