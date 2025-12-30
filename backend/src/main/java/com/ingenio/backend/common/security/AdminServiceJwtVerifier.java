package com.ingenio.backend.common.security;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTValidator;
import cn.hutool.jwt.RegisteredPayload;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.ingenio.backend.config.AdminServiceJwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;

/**
 * Admin API（/api/admin/**）服务间JWT校验器。
 *
 * 说明：
 * - 仅做“服务JWT”的签名与声明校验（exp/nbf/iat/iss/aud）；
 * - 不依赖Sa-Token登录态，避免与C端用户认证耦合；
 * - 任何校验失败都应被视为未授权（由上层Filter统一返回401）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminServiceJwtVerifier {

    private final AdminServiceJwtProperties properties;

    /**
     * 校验服务JWT。
     *
     * @param token JWT原文（不含Bearer前缀）
     * @return 校验结果（不抛出异常，避免被上层Sa-Token Filter吞掉并改写为200）
     */
    public VerificationResult verify(String token) {
        if (!properties.isEnabled()) {
            return VerificationResult.passed();
        }

        if (!StringUtils.hasText(token)) {
            return VerificationResult.fail("缺少服务间认证Token");
        }

        if (!StringUtils.hasText(properties.getSecretKey())) {
            log.error("Admin服务JWT密钥未配置：请设置 ingenio.admin.service-jwt.secret-key（建议通过环境变量注入）");
            return VerificationResult.fail("服务间认证未正确配置");
        }

        JWTSigner signer = JWTSignerUtil.hs256(properties.getSecretKey().getBytes(StandardCharsets.UTF_8));
        try {
            if (!JWTUtil.verify(token, signer)) {
                return VerificationResult.fail("服务间认证Token无效");
            }

            JWT jwt = JWTUtil.parseToken(token);
            JWTValidator.of(jwt)
                .validateAlgorithm(signer)
                .validateDate(new Date(), properties.getClockSkewSeconds());

            if (StringUtils.hasText(properties.getIssuer())) {
                Object issuer = jwt.getPayload(RegisteredPayload.ISSUER);
                if (!properties.getIssuer().equals(issuer)) {
                    return VerificationResult.fail("服务间认证Token签发方不匹配");
                }
            }

            if (StringUtils.hasText(properties.getAudience())) {
                Object audience = jwt.getPayload(RegisteredPayload.AUDIENCE);
                if (!audienceMatches(audience, properties.getAudience())) {
                    return VerificationResult.fail("服务间认证Token受众不匹配");
                }
            }

            return VerificationResult.passed();
        } catch (ValidateException e) {
            // exp/nbf/iat 校验失败
            return VerificationResult.fail("服务间认证Token已过期或尚未生效");
        } catch (Exception e) {
            // 解析或其它校验异常
            return VerificationResult.fail("服务间认证Token解析失败");
        }
    }

    private boolean audienceMatches(Object audClaim, String expectedAudience) {
        if (!StringUtils.hasText(expectedAudience)) {
            return true;
        }
        if (audClaim == null) {
            return false;
        }
        if (audClaim instanceof String aud) {
            return expectedAudience.equals(aud);
        }
        if (audClaim instanceof Collection<?> audiences) {
            return audiences.stream().anyMatch(aud -> expectedAudience.equals(String.valueOf(aud)));
        }
        if (audClaim.getClass().isArray()) {
            Object[] audiences = (Object[]) audClaim;
            for (Object aud : audiences) {
                if (expectedAudience.equals(String.valueOf(aud))) {
                    return true;
                }
            }
            return false;
        }
        return expectedAudience.equals(String.valueOf(audClaim));
    }

    /**
     * 服务JWT校验结果。
     *
     * @param valid 是否通过校验
     * @param message 失败原因（ok=true时为空）
     */
    public record VerificationResult(boolean valid, String message) {
        public static VerificationResult passed() {
            return new VerificationResult(true, null);
        }

        public static VerificationResult fail(String message) {
            return new VerificationResult(false, message);
        }
    }
}
