package com.ingenio.backend.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token刷新响应DTO
 * 返回刷新后的Token信息
 *
 * @author Ingenio Team
 * @since Phase 7.1
 * @date 2025-11-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Token刷新响应")
public class TokenRefreshResponse {

    /**
     * 刷新后的Token值
     * 使用SaToken的renewTimeout重置Token活动超时，返回原Token值
     */
    @Schema(description = "刷新后的Token值", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    /**
     * Token类型
     */
    @Schema(description = "Token类型", example = "Bearer")
    private String tokenType;

    /**
     * Token剩余有效时间（秒）
     */
    @Schema(description = "Token剩余有效时间（秒）", example = "86400")
    private Long expiresIn;

    /**
     * Token刷新时间（Unix时间戳）
     */
    @Schema(description = "Token刷新时间", example = "1700000000")
    private Long refreshedAt;
}
