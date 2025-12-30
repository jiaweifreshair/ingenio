package com.ingenio.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Admin API（/api/admin/**）专用的服务间JWT配置。
 *
 * 设计目标：
 * 1) 与C端用户JWT完全隔离：使用独立密钥与独立校验规则；
 * 2) 默认开启：确保新增的管理端点不会“裸奔”；
 * 3) 仅用于服务间通信：不参与Sa-Token的登录态与权限体系。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ingenio.admin.service-jwt")
public class AdminServiceJwtProperties {

    /**
     * 是否启用 Admin API 的服务间JWT校验。
     * 默认开启，确保 /api/admin/** 默认受保护。
     */
    private boolean enabled = true;

    /**
     * HS256 签名密钥（建议通过环境变量注入）。
     * 生产环境必须替换为高强度随机密钥，且禁止硬编码到仓库。
     */
    private String secretKey;

    /**
     * 期望的签发方（iss）。为空则不校验。
     */
    private String issuer;

    /**
     * 期望的受众（aud）。为空则不校验。
     */
    private String audience;

    /**
     * 时钟偏移容忍（秒），用于容忍服务间时间误差。
     */
    private long clockSkewSeconds = 60;
}

