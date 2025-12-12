package com.ingenio.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信扫码状态响应DTO
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WxScanStatusResponse {

    /**
     * 扫码状态：pending（未扫码）/ scanned（已扫码，等待确认）/ confirmed（已确认）/ expired（已过期）
     */
    private String status;

    /**
     * JWT Token（仅status=confirmed时返回）
     */
    private String token;

    /**
     * 用户信息（仅status=confirmed时返回）
     */
    private UserInfo userInfo;

    /**
     * 用户信息内嵌类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        /**
         * 用户ID（UUID）
         */
        private String id;

        /**
         * 用户名
         */
        private String username;

        /**
         * 邮箱
         */
        private String email;

        /**
         * 头像URL
         */
        private String avatar;
    }

    /**
     * 扫码状态枚举
     */
    public enum Status {
        PENDING("pending"),
        SCANNED("scanned"),
        CONFIRMED("confirmed"),
        EXPIRED("expired");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
