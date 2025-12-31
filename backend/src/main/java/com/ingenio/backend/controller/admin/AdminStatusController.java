package com.ingenio.backend.controller.admin;

import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.common.security.AdminServiceJwtGenerator;
import com.ingenio.backend.config.AdminServiceJwtProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin API - 状态与工具控制器
 *
 * 用途：
 * 1. Admin API 健康检查
 * 2. 服务间 JWT Token 生成（仅开发/测试环境）
 * 3. JWT 配置信息查看
 *
 * 安全说明：
 * - /status 端点需要有效的服务间 JWT Token
 * - /generate-test-token 端点仅在开发环境可用
 *
 * @author Claude
 * @since 2025-12-31 (JeecgBoot 集成)
 */
@Slf4j
@RestController
@RequestMapping({"/admin", "/api/admin"})
@RequiredArgsConstructor
@Tag(name = "Admin - 状态与工具", description = "Admin API 健康检查与开发工具")
public class AdminStatusController {

    private final AdminServiceJwtGenerator jwtGenerator;
    private final AdminServiceJwtProperties jwtProperties;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    /**
     * Admin API 健康检查
     *
     * 验证 Admin API 的可访问性和认证状态
     *
     * @return 状态信息
     */
    @GetMapping("/status")
    @Operation(
        summary = "Admin API 健康检查",
        description = "验证 Admin API 的可访问性和认证状态"
    )
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "operational");
        status.put("service", "ingenio-admin-api");
        status.put("timestamp", Instant.now().toString());
        status.put("jwtEnabled", jwtProperties.isEnabled());

        return ResponseEntity.ok(status);
    }

    /**
     * 生成测试 Token（仅开发环境）
     *
     * 用于本地测试 Admin API 时生成有效的 JWT Token
     *
     * ⚠️ 安全警告：此端点仅在开发环境可用，生产环境会返回 403
     *
     * @param serviceId     服务标识（默认 jeecgboot）
     * @param tenantId      租户ID（可选）
     * @param expireSeconds 有效期秒数（默认 86400 = 24小时）
     * @return 包含 Token 的响应
     */
    @PostMapping("/generate-test-token")
    @Operation(
        summary = "生成测试 Token（仅开发环境）",
        description = "用于本地测试 Admin API 时生成有效的 JWT Token"
    )
    public Result<Map<String, Object>> generateTestToken(
            @Parameter(description = "服务标识")
            @RequestParam(defaultValue = "jeecgboot") String serviceId,

            @Parameter(description = "租户ID（可选）")
            @RequestParam(required = false) String tenantId,

            @Parameter(description = "有效期（秒）")
            @RequestParam(defaultValue = "86400") long expireSeconds
    ) {
        // 安全检查：仅允许在开发环境使用
        if (!isDevelopmentEnvironment()) {
            log.warn("尝试在非开发环境生成测试Token: profile={}", activeProfile);
            return Result.error("403", "此功能仅在开发环境可用");
        }

        try {
            String token = jwtGenerator.generateToken(serviceId, tenantId, expireSeconds);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("token", token);
            result.put("tokenType", "Bearer");
            result.put("expiresIn", expireSeconds);
            result.put("serviceId", serviceId);
            if (tenantId != null) {
                result.put("tenantId", tenantId);
            }
            result.put("usage", "Authorization: Bearer " + token.substring(0, 20) + "...");

            log.info("生成测试Token: serviceId={}, tenantId={}, expiresIn={}s",
                    serviceId, tenantId, expireSeconds);

            return Result.success(result);
        } catch (IllegalStateException e) {
            log.error("生成测试Token失败: {}", e.getMessage());
            return Result.error("500", e.getMessage());
        }
    }

    /**
     * 查看 JWT 配置信息（脱敏）
     *
     * @return JWT 配置（不包含密钥）
     */
    @GetMapping("/jwt-config")
    @Operation(
        summary = "查看 JWT 配置",
        description = "查看当前的 JWT 配置信息（不包含密钥）"
    )
    public Result<Map<String, Object>> getJwtConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", jwtProperties.isEnabled());
        config.put("issuer", jwtProperties.getIssuer());
        config.put("audience", jwtProperties.getAudience());
        config.put("clockSkewSeconds", jwtProperties.getClockSkewSeconds());
        config.put("secretKeyConfigured", jwtProperties.getSecretKey() != null && !jwtProperties.getSecretKey().isEmpty());

        return Result.success(config);
    }

    /**
     * 验证 Token（调试用）
     *
     * @param token JWT Token
     * @return Token 中的声明信息
     */
    @PostMapping("/verify-token")
    @Operation(
        summary = "验证 Token（调试用）",
        description = "解析并验证 Token，返回声明信息"
    )
    public Result<Map<String, Object>> verifyToken(
            @Parameter(description = "JWT Token")
            @RequestParam String token
    ) {
        // 仅开发环境可用
        if (!isDevelopmentEnvironment()) {
            return Result.error("403", "此功能仅在开发环境可用");
        }

        try {
            Map<String, Object> claims = jwtGenerator.parseTokenClaims(token);
            return Result.success(claims);
        } catch (Exception e) {
            return Result.error("400", "Token 解析失败: " + e.getMessage());
        }
    }

    /**
     * 判断是否为开发环境
     */
    private boolean isDevelopmentEnvironment() {
        return "local".equalsIgnoreCase(activeProfile)
                || "dev".equalsIgnoreCase(activeProfile)
                || "development".equalsIgnoreCase(activeProfile)
                || "test".equalsIgnoreCase(activeProfile);
    }
}
