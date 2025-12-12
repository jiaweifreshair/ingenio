package com.ingenio.backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信回调请求DTO
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WxCallbackRequest {

    /**
     * 微信授权码（用于换取access_token）
     */
    private String code;

    /**
     * 场景值（用于防CSRF攻击）
     */
    private String state;
}
