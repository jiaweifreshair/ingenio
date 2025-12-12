package com.ingenio.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Google OAuth用户信息DTO
 * 对应Google OAuth2 Userinfo API响应结构
 *
 * @author Ingenio Team
 * @since 1.0.0
 * @see <a href="https://www.googleapis.com/oauth2/v2/userinfo">Google Userinfo API</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleOAuthUserInfo {

    /**
     * Google用户唯一标识（OpenID）
     */
    @JsonProperty("id")
    private String id;

    /**
     * 用户邮箱
     */
    @JsonProperty("email")
    private String email;

    /**
     * 邮箱是否已验证
     */
    @JsonProperty("verified_email")
    private Boolean verifiedEmail;

    /**
     * 用户姓名
     */
    @JsonProperty("name")
    private String name;

    /**
     * 用户名（given_name）
     */
    @JsonProperty("given_name")
    private String givenName;

    /**
     * 用户姓（family_name）
     */
    @JsonProperty("family_name")
    private String familyName;

    /**
     * 用户头像URL
     */
    @JsonProperty("picture")
    private String picture;

    /**
     * 用户语言偏好
     */
    @JsonProperty("locale")
    private String locale;
}
