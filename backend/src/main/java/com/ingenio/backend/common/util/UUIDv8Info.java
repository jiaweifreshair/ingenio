package com.ingenio.backend.common.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * UUIDv8业务信息对象
 *
 * 用于存储从UUIDv8中解析出的业务信息，提供便捷的访问方法
 *
 * @author Ingenio Team
 * @since 2025-11-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UUIDv8Info {

    /**
     * 原始UUID
     */
    private UUID uuid;

    /**
     * 时间戳（毫秒）
     */
    private long timestamp;

    /**
     * 序列号
     */
    private int sequence;

    /**
     * 业务类型
     */
    private IngenioBusinessType businessType;

    /**
     * 子业务类型
     */
    private int subType;

    /**
     * 租户ID
     */
    private int tenantId;

    /**
     * 获取格式化的时间字符串
     *
     * @return 格式化的时间字符串
     */
    public String getFormattedTime() {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
            LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
        );
    }

    /**
     * 检查是否为公共数据（租户ID=0）
     *
     * @return true表示公共数据
     */
    public boolean isPublic() {
        return tenantId == 0;
    }

    /**
     * 检查是否为默认子类型
     *
     * @return true表示默认子类型
     */
    public boolean isDefaultSubType() {
        return subType == 0;
    }

    @Override
    public String toString() {
        return String.format(
            "UUIDv8Info{uuid=%s, time=%s, type=%s(0x%03X), subType=%d, tenant=%d%s, seq=%d}",
            uuid,
            getFormattedTime(),
            businessType.name(),
            businessType.getCode(),
            subType,
            tenantId,
            isPublic() ? "(公共)" : "",
            sequence
        );
    }
}