package com.ingenio.backend.common.util;

/**
 * Ingenio UUIDv8常量定义类
 *
 * 基于PulseHive项目的UUIDv8常量，适配Ingenio业务场景
 *
 * @author Ingenio Team
 * @since 2025-11-05
 */
public final class UUIDv8Constants {

    // ==================== UUID标���常量 ====================

    /**
     * UUID版本号 (UUIDv8)
     */
    public static final int UUID_VERSION = 8;

    /**
     * UUID变体位 (RFC 9562标准)
     */
    public static final int UUID_VARIANT = 0x2;

    // ==================== 位宽常量 ====================

    public static final int TIMESTAMP_SEC_BITS = 32;
    public static final int MILLIS_BITS = 10;
    public static final int SEQUENCE_BITS = 6;
    public static final int VERSION_BITS = 4;
    public static final int BUSINESS_TYPE_BITS = 12;
    public static final int VARIANT_BITS = 2;
    public static final int SUB_TYPE_BITS = 4;
    public static final int TENANT_ID_BITS = 14;
    public static final int TENANT_ID_HIGH_BITS = 2;
    public static final int TENANT_ID_LOW_BITS = 12;
    public static final int SEQUENCE_COUNTER_BITS = 12;
    public static final int RANDOM1_BITS = 8;
    public static final int RANDOM2_BITS = 24;

    // ==================== 取值范围常量 ====================

    public static final int BUSINESS_TYPE_CODE_MIN = 0x000;
    public static final int BUSINESS_TYPE_CODE_MAX = 0xFFF;
    public static final int SUB_TYPE_MIN = 0;
    public static final int SUB_TYPE_MAX = 15;
    public static final int TENANT_ID_MIN = 0;
    public static final int TENANT_ID_MAX = 16383;
    public static final int MILLIS_MIN = 0;
    public static final int MILLIS_MAX = 1023;
    public static final int SEQUENCE_MIN = 0;
    public static final int SEQUENCE_MAX = 63;
    public static final int SEQUENCE_COUNTER_MIN = 0;
    public static final int SEQUENCE_COUNTER_MAX = 4095;
    public static final int SEQUENCE_OVERFLOW_THRESHOLD = 4096;
    public static final int RANDOM1_MIN = 0;
    public static final int RANDOM1_MAX = 255;
    public static final int RANDOM2_MIN = 0;
    public static final int RANDOM2_MAX = 0xFFFFFF;

    // ==================== 位移量常量 ====================

    public static final int TIMESTAMP_SEC_SHIFT = 32;
    public static final int TIME_MID_SHIFT = 16;
    public static final int MILLIS_IN_TIME_MID_SHIFT = 6;
    public static final int VERSION_IN_TYPE_SHIFT = 12;
    public static final int VARIANT_SHIFT = 62;
    public static final int SUB_TYPE_SHIFT = 58;
    public static final int TENANT_ID_HIGH_SHIFT = 56;
    public static final int RANDOM1_SHIFT = 48;
    public static final int TENANT_ID_LOW_SHIFT = 36;
    public static final int SEQUENCE_COUNTER_SHIFT = 24;
    public static final int TENANT_ID_HIGH_EXTRACT_SHIFT = 12;

    // ==================== 掩码常量 ====================

    public static final long TIMESTAMP_SEC_MASK = 0xFFFFFFFFL;
    public static final int MILLIS_MASK = 0x3FF;
    public static final int SEQUENCE_MASK = 0x3F;
    public static final int VERSION_MASK = 0xF;
    public static final int BUSINESS_TYPE_CODE_MASK = 0xFFF;
    public static final int SUB_TYPE_MASK = 0xF;
    public static final int TENANT_ID_HIGH_MASK = 0x3;
    public static final int TENANT_ID_LOW_MASK = 0xFFF;
    public static final int SEQUENCE_COUNTER_MASK = 0xFFF;
    public static final int RANDOM1_MASK = 0xFF;
    public static final int RANDOM2_MASK = 0xFFFFFF;

    // ==================== 业务常量 ====================

    public static final int PUBLIC_TENANT_ID = 0;
    public static final int DEFAULT_SUB_TYPE = 0;
    public static final int BUSINESS_TYPE_VERSION_MIN = 1;

    // ==================== 性能优化常量 ====================

    public static final int RANDOM2_RANGE = 1 << RANDOM2_BITS;
    public static final int RANDOM1_RANGE = 1 << RANDOM1_BITS;
    public static final int MILLIS_PER_SECOND = 1000;
    public static final long CLOCK_BACKTRACK_WAIT_NANOS = 1_000_000L;

    // ==================== 私有构造函数 ====================

    private UUIDv8Constants() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }
}