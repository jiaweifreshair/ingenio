package com.ingenio.backend.common.security;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.RegisteredPayload;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.ingenio.backend.config.AdminServiceJwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Admin API 服务间 JWT 生成器
 *
 * 用途：生成供 JeecgBoot 调用 Ingenio Admin API 使用的 JWT Token
 *
 * 使用场景：
 * 1. JeecgBoot 项目集成时，可参考此类实现 JWT 生成逻辑
 * 2. 本地测试时，可通过 /admin/status/generate-test-token 生成测试Token
 *
 * Token 结构：
 * <pre>
 * Header: { "alg": "HS256", "typ": "JWT" }
 * Payload: {
 *   "iss": "jeecgboot",       // 签发方
 *   "aud": "ingenio",         // 受众
 *   "sub": "admin-service",   // 主题
 *   "iat": 1704067200,        // 签发时间
 *   "exp": 1704153600,        // 过期时间
 *   "nbf": 1704067200,        // 生效时间
 *   "service": "jeecgboot",   // 服务标识
 *   "tenant_id": "xxx"        // 租户ID（可选）
 * }
 * </pre>
 *
 * @author Claude
 * @since 2025-12-31 (JeecgBoot 集成)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminServiceJwtGenerator {

    private final AdminServiceJwtProperties properties;

    /**
     * 默认Token有效期（24小时）
     */
    private static final long DEFAULT_EXPIRE_SECONDS = 24 * 60 * 60;

    /**
     * 生成服务间 JWT Token
     *
     * @param serviceId 服务标识（如 "jeecgboot"）
     * @param tenantId  租户ID（可选）
     * @return JWT Token 字符串
     */
    public String generateToken(String serviceId, String tenantId) {
        return generateToken(serviceId, tenantId, DEFAULT_EXPIRE_SECONDS);
    }

    /**
     * 生成服务间 JWT Token（指定有效期）
     *
     * @param serviceId     服务标识（如 "jeecgboot"）
     * @param tenantId      租户ID（可选）
     * @param expireSeconds 有效期（秒）
     * @return JWT Token 字符串
     */
    public String generateToken(String serviceId, String tenantId, long expireSeconds) {
        if (!StringUtils.hasText(properties.getSecretKey())) {
            throw new IllegalStateException("Admin服务JWT密钥未配置：请设置 ingenio.admin.service-jwt.secret-key");
        }

        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date notBefore = new Date(now);
        Date expiresAt = new Date(now + expireSeconds * 1000);

        Map<String, Object> payload = new HashMap<>();
        // 标准声明
        payload.put(RegisteredPayload.ISSUED_AT, issuedAt.getTime() / 1000);
        payload.put(RegisteredPayload.NOT_BEFORE, notBefore.getTime() / 1000);
        payload.put(RegisteredPayload.EXPIRES_AT, expiresAt.getTime() / 1000);
        payload.put(RegisteredPayload.SUBJECT, "admin-service");

        // 签发方和受众（使用配置值或默认值）
        String issuer = StringUtils.hasText(properties.getIssuer()) ? properties.getIssuer() : "jeecgboot";
        String audience = StringUtils.hasText(properties.getAudience()) ? properties.getAudience() : "ingenio";
        payload.put(RegisteredPayload.ISSUER, issuer);
        payload.put(RegisteredPayload.AUDIENCE, audience);

        // 自定义声明
        payload.put("service", serviceId != null ? serviceId : "jeecgboot");
        if (StringUtils.hasText(tenantId)) {
            payload.put("tenant_id", tenantId);
        }

        JWTSigner signer = JWTSignerUtil.hs256(properties.getSecretKey().getBytes(StandardCharsets.UTF_8));
        String token = JWTUtil.createToken(payload, signer);

        log.debug("生成服务间JWT Token: service={}, tenantId={}, expiresAt={}",
                serviceId, tenantId, expiresAt);

        return token;
    }

    /**
     * 解析 Token 中的声明（用于调试）
     *
     * @param token JWT Token
     * @return 声明Map
     */
    public Map<String, Object> parseTokenClaims(String token) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            Map<String, Object> claims = new HashMap<>();

            // 标准声明
            claims.put("iss", jwt.getPayload(RegisteredPayload.ISSUER));
            claims.put("aud", jwt.getPayload(RegisteredPayload.AUDIENCE));
            claims.put("sub", jwt.getPayload(RegisteredPayload.SUBJECT));
            claims.put("iat", jwt.getPayload(RegisteredPayload.ISSUED_AT));
            claims.put("exp", jwt.getPayload(RegisteredPayload.EXPIRES_AT));
            claims.put("nbf", jwt.getPayload(RegisteredPayload.NOT_BEFORE));

            // 自定义声明
            claims.put("service", jwt.getPayload("service"));
            claims.put("tenant_id", jwt.getPayload("tenant_id"));

            return claims;
        } catch (Exception e) {
            log.warn("解析Token失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 检查 Token 是否即将过期（剩余时间不足指定秒数）
     *
     * @param token         JWT Token
     * @param thresholdSecs 阈值（秒）
     * @return true 表示即将过期
     */
    public boolean isTokenExpiringSoon(String token, long thresholdSecs) {
        try {
            JWT jwt = JWTUtil.parseToken(token);
            Object exp = jwt.getPayload(RegisteredPayload.EXPIRES_AT);
            if (exp == null) {
                return true;
            }
            long expireTime = ((Number) exp).longValue() * 1000;
            long remainingMs = expireTime - System.currentTimeMillis();
            return remainingMs < thresholdSecs * 1000;
        } catch (Exception e) {
            log.warn("检查Token过期时间失败: {}", e.getMessage());
            return true;
        }
    }
}
