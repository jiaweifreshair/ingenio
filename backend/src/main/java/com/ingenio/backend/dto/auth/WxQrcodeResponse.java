package com.ingenio.backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信二维码响应DTO
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WxQrcodeResponse {

    /**
     * 二维码图片URL
     */
    private String qrcodeUrl;

    /**
     * 场景值（UUID），用于轮询扫码状态
     */
    private String sceneStr;

    /**
     * 二维码过期时间（秒），推荐1800秒（30分钟）
     */
    private Integer expiresIn;
}
