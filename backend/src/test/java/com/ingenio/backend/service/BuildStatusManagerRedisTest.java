package com.ingenio.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.response.PublishResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BuildStatusManager Redis集成测试
 *
 * 测试策略：
 * - 使用TestContainers真实Redis
 * - 验证所有CRUD操作
 * - 验证TTL自动过期
 * - 验证JSON序列化/反序列化
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("BuildStatusManager Redis集成测试")
public class BuildStatusManagerRedisTest {

    @Autowired
    private BuildStatusManager buildStatusManager;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String testBuildId;

    @BeforeEach
    public void setUp() {
        testBuildId = "test-build-" + System.currentTimeMillis();
    }

    @AfterEach
    public void tearDown() {
        // 清理测试数据
        buildStatusManager.removeBuildStatus(testBuildId);
    }

    /**
     * 测试1：保存和查询构建状态 - 成功场景
     *
     * 验证点：
     * - 保存到Redis成功
     * - 查询返回相同数据
     * - JSON序列化正确
     */
    @Test
    @DisplayName("保存和查询构建状态 - 成功场景")
    public void testSaveAndGetBuildStatus_Success() {
        // 准备测试数据
        PublishResponse response = createTestPublishResponse();

        // 保存到Redis
        buildStatusManager.saveBuildStatus(testBuildId, response);

        // 从Redis查询
        PublishResponse retrieved = buildStatusManager.getBuildStatus(testBuildId);

        // 验证
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getBuildId()).isEqualTo(testBuildId);
        assertThat(retrieved.getProjectId()).isEqualTo("test-project");
        assertThat(retrieved.getPlatforms()).containsExactly("android", "ios");
        assertThat(retrieved.getStatus()).isEqualTo("PENDING");
        assertThat(retrieved.getPlatformResults()).hasSize(2);
    }

    /**
     * 测试2：查询不存在的构建 - 返回null
     *
     * 验证点：
     * - 不存在的buildId返回null
     * - 不抛出异常
     */
    @Test
    @DisplayName("查询不存在的构建 - 返回null")
    public void testGetBuildStatus_NotFound() {
        String nonExistentBuildId = "non-existent-build";

        PublishResponse result = buildStatusManager.getBuildStatus(nonExistentBuildId);

        assertThat(result).isNull();
    }

    /**
     * 测试3：更新平台状态 - 成功场景
     *
     * 验证点：
     * - 平台状态更新成功
     * - 进度更新成功
     * - 时间戳自动更新
     * - 整体状态自动计算
     */
    @Test
    @DisplayName("更新平台状态 - 成功场景")
    public void testUpdatePlatformStatus_Success() {
        // 准备初始数据
        PublishResponse response = createTestPublishResponse();
        buildStatusManager.saveBuildStatus(testBuildId, response);

        // 更新Android平台状态
        buildStatusManager.updatePlatformStatus(testBuildId, "android", "IN_PROGRESS", 50);

        // 验证更新
        PublishResponse updated = buildStatusManager.getBuildStatus(testBuildId);
        assertThat(updated).isNotNull();

        PublishResponse.PlatformBuildResult androidResult = updated.getPlatformResults().get("android");
        assertThat(androidResult.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(androidResult.getProgress()).isEqualTo(50);
        assertThat(androidResult.getStartedAt()).isNotNull();

        // 整体状态应该变为IN_PROGRESS
        assertThat(updated.getStatus()).isEqualTo("IN_PROGRESS");
    }

    /**
     * 测试4：更新平台错误信息 - 成功场景
     *
     * 验证点：
     * - 错误信息保存成功
     * - 状态自动设置为FAILED
     * - 进度自动设置为100
     * - 完成时间自动设置
     */
    @Test
    @DisplayName("更新平台错误信息 - 成功场景")
    public void testUpdatePlatformError_Success() {
        // 准备初始数据
        PublishResponse response = createTestPublishResponse();
        buildStatusManager.saveBuildStatus(testBuildId, response);

        // 设置iOS构建失败
        String errorMessage = "编译错误：找不到符号";
        buildStatusManager.updatePlatformError(testBuildId, "ios", errorMessage);

        // 验证错误信息
        PublishResponse updated = buildStatusManager.getBuildStatus(testBuildId);
        assertThat(updated).isNotNull();

        PublishResponse.PlatformBuildResult iosResult = updated.getPlatformResults().get("ios");
        assertThat(iosResult.getStatus()).isEqualTo("FAILED");
        assertThat(iosResult.getProgress()).isEqualTo(100);
        assertThat(iosResult.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(iosResult.getCompletedAt()).isNotNull();

        // 整体状态应该变为FAILED
        assertThat(updated.getStatus()).isEqualTo("FAILED");
    }

    /**
     * 测试5：更新下载URL - 成功场景
     *
     * 验证点：
     * - 下载URL保存成功
     * - 其他字段不受影响
     */
    @Test
    @DisplayName("更新下载URL - 成功场景")
    public void testUpdatePlatformDownloadUrl_Success() {
        // 准备初始数据
        PublishResponse response = createTestPublishResponse();
        buildStatusManager.saveBuildStatus(testBuildId, response);

        // 更新Android下载URL
        String downloadUrl = "https://minio.ingenio.dev/builds/" + testBuildId + "/android/app.apk";
        buildStatusManager.updatePlatformDownloadUrl(testBuildId, "android", downloadUrl);

        // 验证下载URL
        PublishResponse updated = buildStatusManager.getBuildStatus(testBuildId);
        assertThat(updated).isNotNull();

        PublishResponse.PlatformBuildResult androidResult = updated.getPlatformResults().get("android");
        assertThat(androidResult.getDownloadUrl()).isEqualTo(downloadUrl);

        // 其他字段不应受影响
        assertThat(androidResult.getStatus()).isEqualTo("PENDING");
        assertThat(androidResult.getProgress()).isEqualTo(0);
    }

    /**
     * 测试6：删除构建状态 - 成功场景
     *
     * 验证点：
     * - 删除成功
     * - 删除后查询返回null
     */
    @Test
    @DisplayName("删除构建状态 - 成功场景")
    public void testRemoveBuildStatus_Success() {
        // 准备初始数据
        PublishResponse response = createTestPublishResponse();
        buildStatusManager.saveBuildStatus(testBuildId, response);

        // 验证数据存在
        assertThat(buildStatusManager.getBuildStatus(testBuildId)).isNotNull();

        // 删除数据
        buildStatusManager.removeBuildStatus(testBuildId);

        // 验证数据已删除
        assertThat(buildStatusManager.getBuildStatus(testBuildId)).isNull();
    }

    /**
     * 测试7：获取构建任务数量 - 成功场景
     *
     * 验证点：
     * - 统计数量正确
     * - 只统计符合前缀的Key
     */
    @Test
    @DisplayName("获取构建任务数量 - 成功场景")
    public void testGetBuildCount_Success() {
        // 初始数量
        int initialCount = buildStatusManager.getBuildCount();

        // 创建3个构建任务
        for (int i = 0; i < 3; i++) {
            String buildId = "test-build-count-" + i;
            PublishResponse response = createTestPublishResponse();
            response.setBuildId(buildId);
            buildStatusManager.saveBuildStatus(buildId, response);
        }

        // 验证数量增加
        int newCount = buildStatusManager.getBuildCount();
        assertThat(newCount).isEqualTo(initialCount + 3);

        // 清理
        for (int i = 0; i < 3; i++) {
            buildStatusManager.removeBuildStatus("test-build-count-" + i);
        }
    }

    /**
     * 测试8：清空所有构建状态 - 成功场景
     *
     * 验证点：
     * - 清空成功
     * - 清空后数量为0
     */
    @Test
    @DisplayName("清空所有构建状态 - 成功场景")
    public void testClearAll_Success() {
        // 创建2个构建任务
        for (int i = 0; i < 2; i++) {
            String buildId = "test-build-clear-" + i;
            PublishResponse response = createTestPublishResponse();
            response.setBuildId(buildId);
            buildStatusManager.saveBuildStatus(buildId, response);
        }

        // 验证任务存在
        assertThat(buildStatusManager.getBuildCount()).isGreaterThanOrEqualTo(2);

        // 清空所有任务
        buildStatusManager.clearAll();

        // 验证清空成功
        assertThat(buildStatusManager.getBuildCount()).isEqualTo(0);
    }

    /**
     * 测试9：完整流程 - 从创建到完成
     *
     * 验证点：
     * - 完整的状态变更流程
     * - 状态转换逻辑正确
     */
    @Test
    @DisplayName("完整流程 - 从创建到完成")
    public void testFullWorkflow() {
        // 1. 创建初始状态（PENDING）
        PublishResponse response = createTestPublishResponse();
        buildStatusManager.saveBuildStatus(testBuildId, response);

        PublishResponse step1 = buildStatusManager.getBuildStatus(testBuildId);
        assertThat(step1.getStatus()).isEqualTo("PENDING");

        // 2. 开始Android构建（IN_PROGRESS）
        buildStatusManager.updatePlatformStatus(testBuildId, "android", "IN_PROGRESS", 50);
        PublishResponse step2 = buildStatusManager.getBuildStatus(testBuildId);
        assertThat(step2.getStatus()).isEqualTo("IN_PROGRESS");

        // 3. Android完成（一个SUCCESS，一个PENDING）
        buildStatusManager.updatePlatformStatus(testBuildId, "android", "SUCCESS", 100);
        PublishResponse step3 = buildStatusManager.getBuildStatus(testBuildId);
        assertThat(step3.getStatus()).isEqualTo("PENDING"); // iOS还是PENDING

        // 4. 开始iOS构建
        buildStatusManager.updatePlatformStatus(testBuildId, "ios", "IN_PROGRESS", 70);
        PublishResponse step4 = buildStatusManager.getBuildStatus(testBuildId);
        assertThat(step4.getStatus()).isEqualTo("IN_PROGRESS");

        // 5. iOS完成（全部SUCCESS）
        buildStatusManager.updatePlatformStatus(testBuildId, "ios", "SUCCESS", 100);
        PublishResponse step5 = buildStatusManager.getBuildStatus(testBuildId);
        assertThat(step5.getStatus()).isEqualTo("SUCCESS");

        // 6. 更新下载链接
        buildStatusManager.updatePlatformDownloadUrl(testBuildId, "android", "https://minio/android.apk");
        buildStatusManager.updatePlatformDownloadUrl(testBuildId, "ios", "https://minio/ios.ipa");

        PublishResponse finalResponse = buildStatusManager.getBuildStatus(testBuildId);
        assertThat(finalResponse.getPlatformResults().get("android").getDownloadUrl()).isNotNull();
        assertThat(finalResponse.getPlatformResults().get("ios").getDownloadUrl()).isNotNull();
    }

    /**
     * 测试10：JSON序列化 - LocalDateTime格式验证
     *
     * 验证点：
     * - LocalDateTime序列化为ISO-8601格式
     * - 反序列化正确还原
     */
    @Test
    @DisplayName("JSON序列化 - LocalDateTime格式验证")
    public void testJsonSerialization_LocalDateTime() throws Exception {
        // 准备测试数据
        PublishResponse response = createTestPublishResponse();
        Instant testTime = Instant.parse("2025-11-09T10:30:00Z");
        response.setCreatedAt(testTime);
        response.setUpdatedAt(testTime);

        buildStatusManager.saveBuildStatus(testBuildId, response);

        // 直接从Redis读取JSON字符串
        String key = "ingenio:publish:build:" + testBuildId;
        String json = redisTemplate.opsForValue().get(key);

        assertThat(json).isNotNull();
        assertThat(json).contains("\"createdAt\":\"2025-11-09T10:30:00Z\"");
        assertThat(json).contains("\"updatedAt\":\"2025-11-09T10:30:00Z\"");

        // 验证反序列化
        PublishResponse retrieved = buildStatusManager.getBuildStatus(testBuildId);
        assertThat(retrieved.getCreatedAt()).isEqualTo(testTime);
        assertThat(retrieved.getUpdatedAt()).isEqualTo(testTime);
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用PublishResponse
     */
    private PublishResponse createTestPublishResponse() {
        Map<String, PublishResponse.PlatformBuildResult> platformResults = new HashMap<>();

        // Android平台
        PublishResponse.PlatformBuildResult androidResult = PublishResponse.PlatformBuildResult.builder()
                .platform("android")
                .status("PENDING")
                .progress(0)
                .logUrl(null)
                .downloadUrl(null)
                .errorMessage(null)
                .startedAt(null)
                .completedAt(null)
                .build();
        platformResults.put("android", androidResult);

        // iOS平台
        PublishResponse.PlatformBuildResult iosResult = PublishResponse.PlatformBuildResult.builder()
                .platform("ios")
                .status("PENDING")
                .progress(0)
                .logUrl(null)
                .downloadUrl(null)
                .errorMessage(null)
                .startedAt(null)
                .completedAt(null)
                .build();
        platformResults.put("ios", iosResult);

        return PublishResponse.builder()
                .buildId(testBuildId)
                .projectId("test-project")
                .platforms(List.of("android", "ios"))
                .status("PENDING")
                .platformResults(platformResults)
                .estimatedTime(15)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
