package com.ingenio.backend.common.util;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static com.ingenio.backend.common.util.UUIDv8Constants.*;

/**
 * Ingenio UUIDv8生成器 - 嵌入业务标识的UUID生成工具
 *
 * 基于PulseHive项目的UUIDv8实现，适配Ingenio业务场景
 *
 * 功能特性：
 * 1. 符合RFC 9562 UUIDv8标准
 * 2. 嵌入业务类型、租户ID、时间戳等业务信息
 * 3. 保持UUID全局唯一性和时序性
 * 4. 支持高并发场景（单机每秒100万+）
 * 5. 支持从UUID解析业务信息
 *
 * UUID v8布局 (128 bits):
 * <pre>
 * xxxxxxxx-xxxx-8xxx-xxxx-xxxxxxxxxxxx
 * |      | |  ||  | |  ||          |
 * |      | |  ||  | |  ||          +-- 随机数/序列号 (48 bits)
 * |      | |  ||  | |  |+------------- 租户ID后12位 (12 bits)
 * |      | |  ||  | |  +-------------- 租户ID前2位 (2 bits)
 * |      | |  ||  | +----------------- 变体位 (固定为 10)
 * |      | |  ||  +------------------- 子业务类型 (4 bits)
 * |      | |  |+---------------------- 版本位 (固定为 1000 = 8)
 * |      | |  +----------------------- 主业务类型 (12 bits)
 * |      | +-------------------------- 时间戳低16位 (毫秒+序列)
 * |      +---------------------------- Unix时间戳秒数 (32 bits)
 * </pre>
 *
 * 使用示例:
 * <pre>
 * // 生成用户UUID
 * UUID userId = UUIDv8Generator.generate(IngenioBusinessType.USER, 1001);
 *
 * // 生成项目UUID
 * UUID projectId = UUIDv8Generator.generate(IngenioBusinessType.PROJECT, 1001);
 *
 * // 生成公共数据UUID（租户ID=0表示公共）
 * UUID publicId = UUIDv8Generator.generate(IngenioBusinessType.SYSTEM_CONFIG, 0);
 *
 * // 解析UUID
 * UUIDv8Info info = UUIDv8Generator.parse(projectId);
 * System.out.println("类型: " + info.getBusinessType());
 * System.out.println("租户: " + info.getTenantId());
 * System.out.println("时间: " + info.getTimestamp());
 * </pre>
 *
 * @author Ingenio Team
 * @since 2025-11-05
 */
@Slf4j
public class UUIDv8Generator {

    /**
     * 安全随机数生成器（线程安全）
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 序列号计数器（用于同一毫秒内生成多个UUID）
     * 范围: 0-4095 (12 bits)，使用AtomicInteger保证线程安全
     */
    private static final AtomicInteger SEQUENCE_COUNTER = new AtomicInteger(SEQUENCE_COUNTER_MIN);

    /**
     * 上一次生成UUID的时间戳（毫秒）
     * 使用AtomicLong保证线程安全，配合CAS实现无锁并发
     */
    private static final AtomicLong LAST_TIMESTAMP = new AtomicLong(0L);

    /**
     * 生成UUIDv8 - 使用默认子类型
     *
     * @param businessType 业务类型
     * @param tenantId 租户ID (0-16383，0表示公共数据)
     * @return UUIDv8实例
     */
    public static UUID generate(IngenioBusinessType businessType, int tenantId) {
        return generate(businessType, DEFAULT_SUB_TYPE, tenantId);
    }

    /**
     * 生成UUIDv8 - 完整参数版本
     *
     * @param businessType 业务类型
     * @param subType 子业务类型 (0-15)
     * @param tenantId 租户ID (0-16383，0表示公共数据)
     * @return UUIDv8实例
     * @throws IllegalArgumentException 如果参数无效
     */
    public static UUID generate(IngenioBusinessType businessType, int subType, int tenantId) {
        // 参数验证
        validateParameters(businessType, subType, tenantId);

        // 获取时间戳和序列号
        long[] timeAndSeq = getTimeAndSequence();
        long timestampSec = timeAndSeq[0];
        int millis = (int) timeAndSeq[1];
        int sequence = (int) timeAndSeq[2];

        // 构造UUID的两个64位部分
        long msb = buildMostSignificantBits(timestampSec, millis, sequence, businessType);
        long lsb = buildLeastSignificantBits(subType, tenantId);

        return new UUID(msb, lsb);
    }

    /**
     * 解析UUIDv8，提取业务信息
     *
     * @param uuid 待解析的UUID
     * @return UUID业务信息对象
     * @throws IllegalArgumentException 如果UUID不是v8版本
     */
    public static UUIDv8Info parse(UUID uuid) {
        // 验证UUID版本
        if (uuid.version() != UUID_VERSION) {
            throw new IllegalArgumentException(
                String.format("UUID版本错误: 期望v%d，实际v%d", UUID_VERSION, uuid.version())
            );
        }

        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();

        // 提取时间戳 (秒 + 毫秒)
        long timestampSec = (msb >>> TIMESTAMP_SEC_SHIFT) & TIMESTAMP_SEC_MASK;
        // timeMid结构: [毫秒10位 | 序列6位]，位于第16-31位
        long timeMid = (msb >>> TIME_MID_SHIFT) & 0xFFFF;
        int millis = (int) ((timeMid >>> MILLIS_IN_TIME_MID_SHIFT) & MILLIS_MASK);
        long timestampMs = timestampSec * MILLIS_PER_SECOND + millis;

        // 提取序列号 (6 bits)
        int sequence = (int) (timeMid & SEQUENCE_MASK);

        // 提取业务类型 (12 bits)
        int businessTypeCode = (int) (msb & BUSINESS_TYPE_CODE_MASK);
        IngenioBusinessType businessType = IngenioBusinessType.fromCode(businessTypeCode);

        // 提取子类型 (4 bits)
        int subType = (int) ((lsb >>> SUB_TYPE_SHIFT) & SUB_TYPE_MASK);

        // 提取租户ID (14 bits)
        int tenantIdHigh = (int) ((lsb >>> (SUB_TYPE_SHIFT - TENANT_ID_HIGH_BITS)) & TENANT_ID_HIGH_MASK);
        int tenantIdLow = (int) ((lsb >>> TENANT_ID_LOW_SHIFT) & TENANT_ID_LOW_MASK);
        int tenantId = (tenantIdHigh << TENANT_ID_HIGH_EXTRACT_SHIFT) | tenantIdLow;

        return new UUIDv8Info(
            uuid,
            timestampMs,
            sequence,
            businessType,
            subType,
            tenantId
        );
    }

    /**
     * 格式化UUID业务信息为可读字符串
     *
     * @param uuid 待格式化的UUID
     * @return 格式化后的字符串
     */
    public static String format(UUID uuid) {
        try {
            UUIDv8Info info = parse(uuid);
            return info.toString();
        } catch (Exception e) {
            return "UUID: " + uuid + " (解析失败: " + e.getMessage() + ")";
        }
    }

    /**
     * 验证参数有效性
     *
     * @param businessType 业务类型
     * @param subType 子类型 (0-15)
     * @param tenantId 租户ID (0-16383)
     * @throws IllegalArgumentException 如果参数无效
     */
    private static void validateParameters(IngenioBusinessType businessType, int subType, int tenantId) {
        if (businessType == null) {
            throw new IllegalArgumentException("业务类型不能为null");
        }
        if (!isValidSubType(subType)) {
            throw new IllegalArgumentException(
                String.format("子类型必须在%d-%d范围内: %d", SUB_TYPE_MIN, SUB_TYPE_MAX, subType)
            );
        }
        if (!isValidTenantId(tenantId)) {
            throw new IllegalArgumentException(
                String.format("租户ID必须在%d-%d范围内: %d", TENANT_ID_MIN, TENANT_ID_MAX, tenantId)
            );
        }
    }

    /**
     * 获取时间戳和序列号（线程安全 - 使用CAS + AtomicLong）
     *
     * 并发控制机制（无锁化设计）：
     * - AtomicLong + CAS: 使用AtomicLong替代volatile保证原子性
     * - 乐观锁重试: do-while循环配合CAS，失败时自动重试
     * - 序列号自增: 使用AtomicInteger.incrementAndGet()保证线程安全
     * - 智能等待: 使用LockSupport.parkNanos(1ms)替代忙等待
     * - 时钟回拨容错: 检测到回拨时等待恢复（最多5次重试）
     *
     * @return 长度为3的数组：[秒时间戳, 毫秒数, 序列号]
     * @throws IllegalStateException 如果时钟回拨超过5次重试或回拨超过5ms
     */
    private static long[] getTimeAndSequence() {
        long currentMs = System.currentTimeMillis();
        long lastMs;
        int clockBacktrackRetries = 0;
        final int MAX_CLOCK_BACKTRACK_RETRIES = 5;
        final long MAX_CLOCK_BACKTRACK_MS = 5L;

        do {
            lastMs = LAST_TIMESTAMP.get();

            // 检测时钟回拨（系统时间被调整）
            if (currentMs < lastMs) {
                long backtrackMs = lastMs - currentMs;

                // 如果回拨时间很小（≤5ms），可能是系统时钟精度问题，等待后重试
                if (backtrackMs <= MAX_CLOCK_BACKTRACK_MS && clockBacktrackRetries < MAX_CLOCK_BACKTRACK_RETRIES) {
                    clockBacktrackRetries++;
                    if (log.isDebugEnabled()) {
                        log.debug("检测到时钟回拨{}ms（第{}次），等待恢复...", backtrackMs, clockBacktrackRetries);
                    }
                    // 等待回拨时间 + 1ms，确保时间前进
                    LockSupport.parkNanos((backtrackMs + 1) * 1_000_000L);
                    currentMs = System.currentTimeMillis();
                    continue; // 重新开始do-while循环
                } else {
                    // 严重的时钟回拨（>5ms或重试次数超限），抛出异常
                    String errorMsg = String.format(
                        "严重时钟回拨: 上次时间戳=%d, 当前时间戳=%d, 回拨=%dms, 重试次数=%d",
                        lastMs, currentMs, backtrackMs, clockBacktrackRetries
                    );
                    log.error(errorMsg);
                    throw new IllegalStateException(errorMsg);
                }
            }

            // 如果是同一毫秒，递增序列号
            if (currentMs == lastMs) {
                int seq = SEQUENCE_COUNTER.incrementAndGet();

                // 如果序列号溢出（超过12 bits = 4096），等待下一毫秒
                if (seq >= SEQUENCE_OVERFLOW_THRESHOLD) {
                    LockSupport.parkNanos(CLOCK_BACKTRACK_WAIT_NANOS);
                    currentMs = System.currentTimeMillis();
                    SEQUENCE_COUNTER.set(SEQUENCE_COUNTER_MIN);
                    seq = SEQUENCE_COUNTER_MIN;

                    if (log.isDebugEnabled()) {
                        log.debug("序列号溢出，等待下一毫秒: currentMs={}", currentMs);
                    }
                }
            } else {
                // 新的毫秒，重置序列号
                SEQUENCE_COUNTER.set(SEQUENCE_COUNTER_MIN);
            }

            // 使用CAS更新时间戳，失败则重试
        } while (!LAST_TIMESTAMP.compareAndSet(lastMs, currentMs));

        long timestampSec = currentMs / MILLIS_PER_SECOND;
        int millis = (int) (currentMs % MILLIS_PER_SECOND);
        int sequence = SEQUENCE_COUNTER.get();

        return new long[]{timestampSec, millis, sequence};
    }

    /**
     * 构造UUID高64位 (MSB - Most Significant Bits)
     *
     * @param timestampSec 秒时间戳（32 bits）
     * @param millis 毫秒数（10 bits）
     * @param sequence 序列号（6 bits）
     * @param businessType 业务类型（12 bits代码）
     * @return UUID高64位（MSB）
     */
    private static long buildMostSignificantBits(
        long timestampSec,
        int millis,
        int sequence,
        IngenioBusinessType businessType
    ) {
        // 1. 时间戳秒数 (32 bits)
        long timeLow = (timestampSec & TIMESTAMP_SEC_MASK) << TIMESTAMP_SEC_SHIFT;

        // 2. 毫秒数 (10 bits) + 序列号 (6 bits) = 16 bits
        // 注意：必须先转换为long再左移，避免int符号扩展导致的高位污染
        long timeMid = (long) (((millis & MILLIS_MASK) << MILLIS_IN_TIME_MID_SHIFT) | (sequence & SEQUENCE_MASK)) << TIME_MID_SHIFT;

        // 3. 版本位 (4 bits) + 业务类型 (12 bits) = 16 bits
        long versionAndType = ((UUID_VERSION & VERSION_MASK) << VERSION_IN_TYPE_SHIFT) | (businessType.getCode() & BUSINESS_TYPE_CODE_MASK);

        return timeLow | timeMid | versionAndType;
    }

    /**
     * 构造UUID低64位 (LSB - Least Significant Bits)
     *
     * @param subType 子业务类型（4 bits）
     * @param tenantId 租户ID（14 bits）
     * @return UUID低64位（LSB）
     */
    private static long buildLeastSignificantBits(int subType, int tenantId) {
        // 1. 变体位 (2 bits) + 子类型 (4 bits) = 6 bits
        long variant = ((long) UUID_VARIANT << VARIANT_SHIFT) | ((long) (subType & SUB_TYPE_MASK) << SUB_TYPE_SHIFT);

        // 2. 租户ID高2位 (2 bits)
        long tenantIdHigh = ((long) ((tenantId >>> TENANT_ID_HIGH_EXTRACT_SHIFT) & TENANT_ID_HIGH_MASK)) << TENANT_ID_HIGH_SHIFT;

        // 3. 随机数1 (8 bits)
        long random1 = ((long) (SECURE_RANDOM.nextInt(RANDOM1_RANGE) & RANDOM1_MASK)) << RANDOM1_SHIFT;

        // 4. 租户ID低12位 (12 bits)
        long tenantIdLow = ((long) (tenantId & TENANT_ID_LOW_MASK)) << TENANT_ID_LOW_SHIFT;

        // 5. 序列号 (12 bits)
        long sequence = ((long) (SEQUENCE_COUNTER.get() & SEQUENCE_COUNTER_MASK)) << SEQUENCE_COUNTER_SHIFT;

        // 6. 随机数2 (24 bits)
        long random2 = SECURE_RANDOM.nextInt(RANDOM2_RANGE) & RANDOM2_MASK;

        return variant | tenantIdHigh | random1 | tenantIdLow | sequence | random2;
    }

    /**
     * 私有构造函数，禁止实例化
     */
    private UUIDv8Generator() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ==================== 辅助方法 ====================

    /**
     * 验证子类型是否有效
     */
    private static boolean isValidSubType(int subType) {
        return subType >= SUB_TYPE_MIN && subType <= SUB_TYPE_MAX;
    }

    /**
     * 验证租户ID是否有效
     */
    private static boolean isValidTenantId(int tenantId) {
        return tenantId >= TENANT_ID_MIN && tenantId <= TENANT_ID_MAX;
    }
}