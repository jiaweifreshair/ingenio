package com.ingenio.backend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 用户日志响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLogResponse {

    /**
     * 日志ID
     */
    private String id;

    /**
     * 操作类型
     */
    private String action;

    /**
     * 操作分类
     */
    private String actionCategory;

    /**
     * 操作描述
     */
    private String description;

    /**
     * 资源类型
     */
    private String resourceType;

    /**
     * 资源ID
     */
    private String resourceId;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 请求方法
     */
    private String requestMethod;

    /**
     * 请求路径
     */
    private String requestPath;

    /**
     * 操作状态
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 执行时间（毫秒）
     */
    private Integer executionTimeMs;

    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    private Instant createdAt;
}
