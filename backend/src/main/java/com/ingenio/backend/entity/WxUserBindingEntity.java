package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 微信用户绑定实体类
 * 用于支持微信扫码登录功能，存储微信用户信息和系统用户的绑定关系
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "wx_user_bindings")
public class WxUserBindingEntity {

    /**
     * 绑定记录ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 关联的系统用户ID
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 微信OpenID（唯一标识，每个应用不同）
     */
    @TableField("wx_openid")
    private String wxOpenid;

    /**
     * 微信UnionID（同一开放平台下多个应用统一ID）
     */
    @TableField("wx_unionid")
    private String wxUnionid;

    /**
     * 微信昵称
     */
    @TableField("wx_nickname")
    private String wxNickname;

    /**
     * 微信头像URL
     */
    @TableField("wx_avatar_url")
    private String wxAvatarUrl;

    /**
     * 国家
     */
    @TableField("wx_country")
    private String wxCountry;

    /**
     * 省份
     */
    @TableField("wx_province")
    private String wxProvince;

    /**
     * 城市
     */
    @TableField("wx_city")
    private String wxCity;

    /**
     * 性别（0:未知, 1:男, 2:女）
     */
    @TableField("wx_sex")
    private Integer wxSex;

    /**
     * 语言
     */
    @TableField("wx_language")
    private String wxLanguage;

    /**
     * 首次绑定时间
     */
    @TableField("bind_time")
    private Instant bindTime;

    /**
     * 最后一次微信登录时间
     */
    @TableField("last_login_time")
    private Instant lastLoginTime;

    /**
     * 微信登录总次数（统计用）
     */
    @TableField("login_count")
    private Integer loginCount;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /**
     * 性别枚举
     */
    public enum Sex {
        UNKNOWN(0, "未知"),
        MALE(1, "男"),
        FEMALE(2, "女");

        private final Integer code;
        private final String description;

        Sex(Integer code, String description) {
            this.code = code;
            this.description = description;
        }

        public Integer getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        /**
         * 根据代码获取性别枚举
         *
         * @param code 性别代码
         * @return 性别枚举
         */
        public static Sex fromCode(Integer code) {
            if (code == null) {
                return UNKNOWN;
            }
            for (Sex sex : values()) {
                if (sex.code.equals(code)) {
                    return sex;
                }
            }
            return UNKNOWN;
        }
    }
}
