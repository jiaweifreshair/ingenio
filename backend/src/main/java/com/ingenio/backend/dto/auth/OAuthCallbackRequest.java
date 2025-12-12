package com.ingenio.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth回调请求DTO
 * 用于接收Google/GitHub OAuth授权回调
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthCallbackRequest {

    /**
     * 授权码
     * Google/GitHub返回的临时授权码，用于换取access_token
     */
    @NotBlank(message = "授权码不能为空")
    private String code;

    /**
     * 状态参数（可选）
     * 用于防止CSRF攻击，验证请求来源
     */
    private String state;

    /**
     * OAuth提供商类型
     * google / github
     */
    @NotBlank(message = "OAuth提供商类型不能为空")
    private String provider;
}
