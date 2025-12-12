package com.ingenio.backend.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UUIDv8Generator单元测试
 *
 * 测试覆盖范围:
 * - UUID生成（各种业务类型和租户ID）
 * - UUID解析和信息提取
 * - 线程安全性和并发测试
 * - 参数验证和边界条件
 * - 时间戳和序列号管理
 * - 格式化和显示
 *
 * @author Ingenio Team
 * @since 2025-11-10
 */
@DisplayName("UUIDv8生成器单元测试")
class UUIDv8GeneratorTest {

    // ==================== UUID生成基本功能测试 ====================

    @Test
    @DisplayName("生成USER类型UUID")
    void testGenerateUserUuid() {
        UUID uuid = UUIDv8Generator.generate(IngenioBusinessType.USER, 1001);

        assertNotNull(uuid);
        assertEquals(8, uuid.version(), "UUID版本应为8");
    }

    @Test
    @DisplayName("生成PROJECT类型UUID")
    void testGenerateProjectUuid() {
        UUID uuid = UUIDv8Generator.generate(IngenioBusinessType.PROJECT, 2001);

        assertNotNull(uuid);
        assertEquals(8, uuid.version());
    }

    @ParameterizedTest
    @EnumSource(IngenioBusinessType.class)
    @DisplayName("参数化测试：生成所有业务类型的UUID")
    void testGenerateAllBusinessTypes(IngenioBusinessType businessType) {
        UUID uuid = UUIDv8Generator.generate(businessType, 1001);

        assertNotNull(uuid);
        assertEquals(8, uuid.version());

        // 验证可以成功解析
        UUIDv8Info info = UUIDv8Generator.parse(uuid);
        assertEquals(businessType, info.getBusinessType());
    }

    @Test
    @DisplayName("生成公共数据UUID（租户ID=0）")
    void testGeneratePublicDataUuid() {
        UUID uuid = UUIDv8Generator.generate(IngenioBusinessType.SYSTEM_CONFIG, 0);

        assertNotNull(uuid);
        assertEquals(8, uuid.version());

        UUIDv8Info info = UUIDv8Generator.parse(uuid);
        assertEquals(0, info.getTenantId(), "租户ID应为0（公共数据）");
    }

    @Test
    @DisplayName("生成带子类型的UUID")
    void testGenerateUuidWithSubType() {
        UUID uuid = UUIDv8Generator.generate(IngenioBusinessType.USER, 5, 1001);

        assertNotNull(uuid);
        assertEquals(8, uuid.version());

        UUIDv8Info info = UUIDv8Generator.parse(uuid);
        assertEquals(5, info.getSubType());
        assertEquals(IngenioBusinessType.USER, info.getBusinessType());
        assertEquals(1001, info.getTenantId());
    }

    @Test
    @DisplayName("验证UUID的唯一性")
    void testUuidUniqueness() {
        Set<UUID> uuids = new HashSet<>();
        int count = 10000;

        for (int i = 0; i < count; i++) {
            UUID uuid = UUIDv8Generator.generate(IngenioBusinessType.USER, 1001);
            uuids.add(uuid);
        }

        assertEquals(count, uuids.size(), "生成的UUID应全部唯一");
    }

    // ==================== UUID解析功能测试 ====================

    @Test
    @DisplayName("解析UUID并提取业务信息")
    void testParseUuid() {
        int tenantId = 1001;
        IngenioBusinessType businessType = IngenioBusinessType.PROJECT;

        UUID uuid = UUIDv8Generator.generate(businessType, tenantId);
        UUIDv8Info info = UUIDv8Generator.parse(uuid);

        assertEquals(businessType, info.getBusinessType());
        assertEquals(tenantId, info.getTenantId());
        assertNotNull(info.getUuid());
        assertTrue(info.getTimestamp() > 0);
    }

    @Test
    @DisplayName("解析UUID提取子类型")
    void testParseUuidWithSubType() {
        int subType = 3;
        int tenantId = 2001;
        IngenioBusinessType businessType = IngenioBusinessType.APP_SPEC;

        UUID uuid = UUIDv8Generator.generate(businessType, subType, tenantId);
        UUIDv8Info info = UUIDv8Generator.parse(uuid);

        assertEquals(subType, info.getSubType());
        assertEquals(businessType, info.getBusinessType());
        assertEquals(tenantId, info.getTenantId());
    }

    @Test
    @DisplayName("解析非v8版本UUID应抛出异常")
    void testParseNonV8UuidThrowsException() {
        // 生成v4 UUID
        UUID v4Uuid = UUID.randomUUID();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> UUIDv8Generator.parse(v4Uuid)
        );

        assertTrue(exception.getMessage().contains("UUID版本错误"));
    }

    @Test
    @DisplayName("格式化UUID信息为字符串")
    void testFormatUuid() {
        UUID uuid = UUIDv8Generator.generate(IngenioBusinessType.USER, 1001);
        String formatted = UUIDv8Generator.format(uuid);

        assertNotNull(formatted);
        assertTrue(formatted.contains("USER"));
        assertTrue(formatted.contains("1001"));
    }

    @Test
    @DisplayName("格式化非v8 UUID应返回错误提示")
    void testFormatNonV8Uuid() {
        UUID v4Uuid = UUID.randomUUID();
        String formatted = UUIDv8Generator.format(v4Uuid);

        assertTrue(formatted.contains("解析失败"));
    }

    // ==================== 参数验证测试 ====================

    @Test
    @DisplayName("业务类型为null应抛出异常")
    void testNullBusinessTypeThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> UUIDv8Generator.generate(null, 1001)
        );

        assertTrue(exception.getMessage().contains("业务类型不能为null"));
    }

    @Test
    @DisplayName("无效的子类型应抛出异常")
    void testInvalidSubTypeThrowsException() {
        // 子类型范围: 0-15（4 bits）
        assertThrows(IllegalArgumentException.class,
            () -> UUIDv8Generator.generate(IngenioBusinessType.USER, -1, 1001));

        assertThrows(IllegalArgumentException.class,
            () -> UUIDv8Generator.generate(IngenioBusinessType.USER, 16, 1001));

        assertThrows(IllegalArgumentException.class,
            () -> UUIDv8Generator.generate(IngenioBusinessType.USER, 100, 1001));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 10, 15})
    @DisplayName("参数化测试：有效的子类型范围（0-15）")
    void testValidSubTypeRange(int subType) {
        assertDoesNotThrow(() -> UUIDv8Generator.generate(IngenioBusinessType.USER, subType, 1001));
    }

    @Test
    @DisplayName("无效的租户ID应抛出异常")
    void testInvalidTenantIdThrowsException() {
        // 租户ID范围: 0-16383（14 bits）
        assertThrows(IllegalArgumentException.class,
            () -> UUIDv8Generator.generate(IngenioBusinessType.USER, -1));

        assertThrows(IllegalArgumentException.class,
            () -> UUIDv8Generator.generate(IngenioBusinessType.USER, 16384));

        assertThrows(IllegalArgumentException.class,
            () -> UUIDv8Generator.generate(IngenioBusinessType.USER, 100000));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 100, 1000, 10000, 16383})
    @DisplayName("参数化测试：有效的租户ID范围（0-16383）")
    void testValidTenantIdRange(int tenantId) {
        assertDoesNotThrow(() -> UUIDv8Generator.generate(IngenioBusinessType.USER, tenantId));
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("测试最小租户ID（0）")
    void testMinTenantId() {
        UUID uuid = UUIDv8Generator.generate(IngenioBusinessType.SYSTEM_CONFIG, 0);
        UUIDv8Info info = UUIDv8Generator.parse(uuid);

        assertEquals(0, info.getTenantId());
    }

    @Test
    @DisplayName("测试最大租户ID（16383）")
    void testMaxTenantId() {
        UUID uuid = UUIDv8Generator.generate(IngenioBusinessType.USER, 16383);
        UUIDv8Info info = UUIDv8Generator.parse(uuid);

        assertEquals(16383, info.getTenantId());
    }

    @Test
    @DisplayName("测试最小子类型（0）")
    void testMinSubType() {
        UUID uuid = UUIDv8Generator.generate(IngenioBusinessType.USER, 0, 1001);
        UUIDv8Info info = UUIDv8Generator.parse(uuid);

        assertEquals(0, info.getSubType());
    }

    @Test
    @DisplayName("测试最大子类型（15）")
    void testMaxSubType() {
        UUID uuid = UUIDv8Generator.generate(IngenioBusinessType.USER, 15, 1001);
        UUIDv8Info info = UUIDv8Generator.parse(uuid);

        assertEquals(15, info.getSubType());
    }

    // ==================== 时间戳和序列号测试 ====================

    @Test
    @DisplayName("验证时间戳的准确性")
    void testTimestampAccuracy() {
        long beforeGen = System.currentTimeMillis();
        UUID uuid = UUIDv8Generator.generate(IngenioBusinessType.USER, 1001);
        long afterGen = System.currentTimeMillis();

        UUIDv8Info info = UUIDv8Generator.parse(uuid);
        long timestamp = info.getTimestamp();

        assertTrue(timestamp >= beforeGen && timestamp <= afterGen,
            String.format("时间戳%d应在[%d, %d]范围内", timestamp, beforeGen, afterGen));
    }

    @Test
    @DisplayName("同一毫秒内生成多个UUID应有不同序列号")
    void testSequenceNumberInSameMillisecond() {
        List<UUID> uuids = IntStream.range(0, 100)
            .mapToObj(i -> UUIDv8Generator.generate(IngenioBusinessType.USER, 1001))
            .collect(Collectors.toList());

        // 验证所有UUID唯一
        Set<UUID> uniqueUuids = new HashSet<>(uuids);
        assertEquals(100, uniqueUuids.size(), "所有UUID应唯一");

        // 验证至少部分UUID的时间戳相同（在同一毫秒内）
        List<UUIDv8Info> infos = uuids.stream()
            .map(UUIDv8Generator::parse)
            .collect(Collectors.toList());

        Map<Long, Long> timestampCounts = infos.stream()
            .collect(Collectors.groupingBy(UUIDv8Info::getTimestamp, Collectors.counting()));

        // 至少应有一些UUID在同一毫秒内生成
        assertTrue(timestampCounts.values().stream().anyMatch(count -> count > 1),
            "应有部分UUID在同一毫秒内生成");
    }

    // ==================== 线程安全和并发测试 ====================

    @Test
    @DisplayName("并发生成UUID的线程安全性")
    void testConcurrentUuidGeneration() throws InterruptedException, ExecutionException {
        int threadCount = 10;
        int uuidsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        List<Future<List<UUID>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            Future<List<UUID>> future = executor.submit(() -> {
                List<UUID> uuids = new ArrayList<>();
                for (int j = 0; j < uuidsPerThread; j++) {
                    uuids.add(UUIDv8Generator.generate(IngenioBusinessType.USER, 1001));
                }
                return uuids;
            });
            futures.add(future);
        }

        // 收集所有生成的UUID
        Set<UUID> allUuids = new HashSet<>();
        for (Future<List<UUID>> future : futures) {
            allUuids.addAll(future.get());
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 验证所有UUID唯一
        int expectedCount = threadCount * uuidsPerThread;
        assertEquals(expectedCount, allUuids.size(),
            String.format("并发生成的%d个UUID应全部唯一", expectedCount));
    }

    @Test
    @DisplayName("高并发下的UUID唯一性验证")
    void testHighConcurrencyUuidUniqueness() throws InterruptedException {
        int threadCount = 20;
        int uuidsPerThread = 500;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ConcurrentHashMap<UUID, Boolean> uuidMap = new ConcurrentHashMap<>();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // 等待所有线程准备好
                    for (int j = 0; j < uuidsPerThread; j++) {
                        UUID uuid = UUIDv8Generator.generate(IngenioBusinessType.PROJECT, 2001);
                        Boolean previous = uuidMap.put(uuid, Boolean.TRUE);
                        if (previous != null) {
                            fail("发现重复UUID: " + uuid);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown(); // 同时启动所有线程
        endLatch.await(15, TimeUnit.SECONDS);

        int expectedCount = threadCount * uuidsPerThread;
        assertEquals(expectedCount, uuidMap.size(),
            String.format("高并发生成的%d个UUID应全部唯一", expectedCount));
    }

    // ==================== 性能测试 ====================

    @Test
    @DisplayName("性能测试：批量生成UUID")
    void testBatchUuidGenerationPerformance() {
        int count = 10000;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            UUIDv8Generator.generate(IngenioBusinessType.USER, 1001);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 验证性能：10000次生成应在1秒内完成
        assertTrue(duration < 1000,
            String.format("生成%d个UUID耗时%dms，应小于1000ms", count, duration));

        // 计算每秒生成速率
        double rate = (count * 1000.0) / duration;
        assertTrue(rate > 10000, String.format("生成速率%.0f/s应大于10000/s", rate));
    }

    @Test
    @DisplayName("性能测试：UUID解析")
    void testUuidParsingPerformance() {
        // 预先生成UUID
        List<UUID> uuids = IntStream.range(0, 10000)
            .mapToObj(i -> UUIDv8Generator.generate(IngenioBusinessType.USER, i % 100))
            .collect(Collectors.toList());

        long startTime = System.currentTimeMillis();

        for (UUID uuid : uuids) {
            UUIDv8Generator.parse(uuid);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 验证性能：10000次解析应在500ms内完成
        assertTrue(duration < 500,
            String.format("解析%d个UUID耗时%dms，应小于500ms", uuids.size(), duration));
    }

    // ==================== 集成测试 ====================

    @Test
    @DisplayName("集成测试：完整的UUID生命周期")
    void testCompleteUuidLifecycle() {
        // 1. 生成UUID
        IngenioBusinessType businessType = IngenioBusinessType.APP_SPEC;
        int subType = 2;
        int tenantId = 5001;

        UUID uuid = UUIDv8Generator.generate(businessType, subType, tenantId);

        // 2. 验证UUID基本属性
        assertNotNull(uuid);
        assertEquals(8, uuid.version());

        // 3. 解析UUID
        UUIDv8Info info = UUIDv8Generator.parse(uuid);

        // 4. 验证解析结果
        assertEquals(businessType, info.getBusinessType());
        assertEquals(subType, info.getSubType());
        assertEquals(tenantId, info.getTenantId());
        assertEquals(uuid, info.getUuid());
        assertTrue(info.getTimestamp() > 0);
        assertTrue(info.getSequence() >= 0);

        // 5. 格式化显示
        String formatted = UUIDv8Generator.format(uuid);
        assertNotNull(formatted);
        assertTrue(formatted.contains(businessType.name()));
        assertTrue(formatted.contains(String.valueOf(tenantId)));
    }

    @Test
    @DisplayName("验证UUID字符串格式")
    void testUuidStringFormat() {
        UUID uuid = UUIDv8Generator.generate(IngenioBusinessType.USER, 1001);
        String uuidStr = uuid.toString();

        // 验证UUID字符串格式: xxxxxxxx-xxxx-8xxx-xxxx-xxxxxxxxxxxx
        assertTrue(uuidStr.matches("[0-9a-f]{8}-[0-9a-f]{4}-8[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}"),
            "UUID字符串格式不正确: " + uuidStr);

        // 验证版本位（第三部分第一个字符应为8）
        char versionChar = uuidStr.split("-")[2].charAt(0);
        assertEquals('8', versionChar, "UUID版本位应为8");
    }
}
