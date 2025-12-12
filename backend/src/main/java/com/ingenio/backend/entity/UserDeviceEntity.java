package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户登录设备实体类
 * 用于管理用户的登录设备和会话
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_devices")
public class UserDeviceEntity {

    /**
     * 设备ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 设备名称（如：Chrome on Windows）
     */
    @TableField("device_name")
    private String deviceName;

    /**
     * 设备类型：desktop/mobile/tablet
     */
    @TableField("device_type")
    private String deviceType;

    /**
     * 浏览器信息
     */
    @TableField("browser")
    private String browser;

    /**
     * 操作系统
     */
    @TableField("os")
    private String os;

    /**
     * IP地址
     */
    @TableField("ip_address")
    private String ipAddress;

    /**
     * 地理位置（可选）
     */
    @TableField("location")
    private String location;

    /**
     * SaToken的TokenId
     */
    @TableField("token_id")
    private String tokenId;

    /**
     * 最后活跃时间
     */
    @TableField("last_active_at")
    private Instant lastActiveAt;

    /**
     * 是否为当前设备
     */
    @TableField("is_current")
    private Boolean isCurrent;

    /**
     * 创建时间（首次登录时间）
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 设备类型枚举
     */
    public enum DeviceType {
        DESKTOP("desktop"),
        MOBILE("mobile"),
        TABLET("tablet"),
        UNKNOWN("unknown");

        private final String value;

        DeviceType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
