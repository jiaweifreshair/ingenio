package com.ingenio.backend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 登录设备响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginDeviceResponse {

    /**
     * 设备ID
     */
    private String id;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 地理位置
     */
    private String location;

    /**
     * 是否为当前设备
     */
    private Boolean isCurrent;

    /**
     * 最后活跃时间
     */
    private Instant lastActiveAt;

    /**
     * 首次登录时间
     */
    private Instant createdAt;
}
