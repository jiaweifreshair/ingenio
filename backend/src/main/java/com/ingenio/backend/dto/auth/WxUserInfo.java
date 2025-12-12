package com.ingenio.backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信用户信息DTO
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WxUserInfo {

    /**
     * 微信OpenID（唯一标识，每个应用不同）
     */
    private String openid;

    /**
     * 微信UnionID（同一开放平台下多个应用统一ID）
     */
    private String unionid;

    /**
     * 微信昵称
     */
    private String nickname;

    /**
     * 微信头像URL
     */
    private String headimgurl;

    /**
     * 性别（0:未知, 1:男, 2:女）
     */
    private Integer sex;

    /**
     * 国家
     */
    private String country;

    /**
     * 省份
     */
    private String province;

    /**
     * 城市
     */
    private String city;

    /**
     * 语言
     */
    private String language;
}
