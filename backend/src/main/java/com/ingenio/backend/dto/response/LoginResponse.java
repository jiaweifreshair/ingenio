package com.ingenio.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应DTO
 * 包含JWT Token和用户基本信息
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * JWT访问令牌
     * 用于后续API请求的认证
     */
    private String accessToken;

    /**
     * Token类型（固定为"Bearer"）
     */
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * Token过期时间（秒）
     */
    private Long expiresIn;

    /**
     * 用户信息
     */
    private UserResponse user;
}
