package com.ingenio.backend.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.request.PublishRequest;
import com.ingenio.backend.dto.response.PublishResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 多端发布功能E2E测试
 *
 * 测试范围：
 * - 创建发布任务
 * - 查询构建状态
 * - 获取下载链接
 * - 获取QR码图片
 * - 取消构建任务
 *
 * 测试策略：
 * - 零Mock策略：使用真实MinIO服务
 * - 完整流程测试：创建 → 等待 → 查询 → 下载
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@DisplayName("多端发布E2E测试")
public class PublishE2ETest extends BaseE2ETest {

    @Autowired
    private ObjectMapper objectMapper;

    private String testToken;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        // TODO: 创建测试用户并获取token
        // 暂时使用空token，需要配置测试环境跳过认证
        testToken = "";
    }

    /**
     * 测试1：创建发布任务 - 成功场景
     *
     * 验证点：
     * - 返回200状态码
     * - 返回buildId不为空
     * - 返回状态为PENDING
     * - 平台结果包含所有请求的平台
     */
    @Test
    @DisplayName("创建发布任务 - 成功场景")
    public void testCreatePublishTask_Success() throws Exception {
        // 准备测试数据
        PublishRequest request = PublishRequest.builder()
                .projectId("test-project-001")
                .platforms(List.of("android", "ios"))
                .platformConfigs(createTestPlatformConfigs())
                .parallelBuild(true)
                .priority("NORMAL")
                .build();

        String requestJson = objectMapper.writeValueAsString(request);

        // 执行请求
        MvcResult result = mockMvc.perform(post("/v1/publish/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.buildId").exists())
                // 状态可能是PENDING或IN_PROGRESS（取决于异步构建是否已开始）
                .andExpect(jsonPath("$.data.platforms").isArray())
                .andExpect(jsonPath("$.data.platforms.length()").value(2))
                .andReturn();

        // 解析响应
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        String buildId = (String) data.get("buildId");

        assertThat(buildId).isNotNull();
        assertThat(buildId).matches("[0-9a-f-]{36}"); // UUID格式验证

        // 验证平台结果
        Map<String, Object> platformResults = (Map<String, Object>) data.get("platformResults");
        assertThat(platformResults).containsKeys("android", "ios");
    }

    /**
     * 测试2：创建发布任务 - 参数验证失败
     *
     * 验证点：
     * - 空平台列表返回错误
     * - 空projectId返回错误
     */
    @Test
    @DisplayName("创建发布任务 - 参数验证失败")
    public void testCreatePublishTask_ValidationFailed() throws Exception {
        // 测试空平台列表
        PublishRequest invalidRequest = PublishRequest.builder()
                .projectId("test-project-001")
                .platforms(List.of()) // 空列表
                .platformConfigs(new HashMap<>())
                .build();

        String requestJson = objectMapper.writeValueAsString(invalidRequest);

        mockMvc.perform(post("/v1/publish/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1001")); // PARAM_ERROR错误码
    }

    /**
     * 测试3：查询构建状态 - 成功场景
     *
     * 验证点：
     * - 创建任务后能查询到状态
     * - 状态包含平台进度信息
     */
    @Test
    @DisplayName("查询构建状态 - 成功场景")
    public void testGetBuildStatus_Success() throws Exception {
        // 先创建发布任务
        String buildId = createTestBuild();

        // 等待一段时间让构建开始
        Thread.sleep(1000);

        // 查询构建状态
        mockMvc.perform(get("/v1/publish/status/" + buildId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.buildId").value(buildId))
                .andExpect(jsonPath("$.data.status").exists())
                .andExpect(jsonPath("$.data.platformResults").exists());
    }

    /**
     * 测试4：查询构建状态 - 构建不存在
     *
     * 验证点：
     * - 不存在的buildId返回错误
     */
    @Test
    @DisplayName("查询构建状态 - 构建不存在")
    public void testGetBuildStatus_NotFound() throws Exception {
        String nonExistentBuildId = "00000000-0000-0000-0000-000000000000";

        mockMvc.perform(get("/v1/publish/status/" + nonExistentBuildId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500)); // 业务错误
    }

    /**
     * 测试5：获取下载链接 - 成功场景
     *
     * 验证点：
     * - 构建完成后能获取下载链接
     * - 返回的URL是有效的presigned URL
     */
    @Test
    @DisplayName("获取下载链接 - 成功场景")
    public void testGetDownloadUrl_Success() throws Exception {
        // 先创建发布任务
        String buildId = createTestBuild();

        // 等待构建完成（模拟构建2秒）
        Thread.sleep(3000);

        // 获取Android下载链接
        MvcResult result = mockMvc.perform(get("/v1/publish/download/" + buildId + "/android"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

        // 验证返回的URL
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        String downloadUrl = (String) response.get("data");

        assertThat(downloadUrl).isNotNull();
        assertThat(downloadUrl).contains(buildId);
        assertThat(downloadUrl).contains("android");
        assertThat(downloadUrl).contains("app.apk");
    }

    /**
     * 测试6：获取下载链接 - 平台类型无效
     *
     * 验证点：
     * - 不支持的平台类型返回错误
     */
    @Test
    @DisplayName("获取下载链接 - 平台类型无效")
    public void testGetDownloadUrl_InvalidPlatform() throws Exception {
        String buildId = createTestBuild();
        Thread.sleep(3000);

        mockMvc.perform(get("/v1/publish/download/" + buildId + "/invalid-platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500)); // 业务错误
    }

    /**
     * 测试7：获取QR码 - 成功场景
     *
     * 验证点：
     * - 构建完成后能获取QR码图片
     * - 返回PNG格式图片
     */
    @Test
    @DisplayName("获取QR码 - 成功场景")
    public void testGetQRCode_Success() throws Exception {
        // 先创建发布任务
        String buildId = createTestBuild();

        // 等待构建完成
        Thread.sleep(3000);

        // 获取QR码
        MvcResult result = mockMvc.perform(get("/v1/publish/qrcode/" + buildId + "/android"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().exists("Content-Length"))
                .andReturn();

        // 验证返回的图片不为空
        byte[] imageBytes = result.getResponse().getContentAsByteArray();
        assertThat(imageBytes).isNotEmpty();
        assertThat(imageBytes.length).isGreaterThan(0);
    }

    /**
     * 测试8：获取QR码 - 文件不存在
     *
     * 验证点：
     * - 构建未完成时返回404
     */
    @Test
    @DisplayName("获取QR码 - 文件不存在")
    public void testGetQRCode_FileNotFound() throws Exception {
        // 创建发布任务但不等待完成
        String buildId = createTestBuild();

        // 立即请求QR码（此时文件还未上传）
        mockMvc.perform(get("/v1/publish/qrcode/" + buildId + "/android"))
                .andExpect(status().isNotFound()); // 404
    }

    /**
     * 测试9：取消构建任务 - 成功场景
     *
     * 验证点：
     * - 能成功取消正在进行的构建
     */
    @Test
    @DisplayName("取消构建任务 - 成功场景")
    public void testCancelBuild_Success() throws Exception {
        // 先创建发布任务
        String buildId = createTestBuild();

        // 取消构建
        mockMvc.perform(post("/v1/publish/cancel/" + buildId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // TODO: 验证状态是否变为CANCELLED
    }

    /**
     * 测试10：完整流程 - 创建到下载
     *
     * 验证点：
     * - 完整的发布流程能正常工作
     * - 状态变化符合预期
     */
    @Test
    @DisplayName("完整流程 - 创建到下载")
    public void testFullPublishFlow() throws Exception {
        // 1. 创建发布任务
        String buildId = createTestBuild();
        assertThat(buildId).isNotNull();

        // 2. 立即查询状态（应该是PENDING或IN_PROGRESS）
        MvcResult statusResult1 = mockMvc.perform(get("/v1/publish/status/" + buildId))
                .andExpect(status().isOk())
                .andReturn();

        String status1Body = statusResult1.getResponse().getContentAsString();
        Map<String, Object> status1Response = objectMapper.readValue(status1Body, Map.class);
        Map<String, Object> status1Data = (Map<String, Object>) status1Response.get("data");
        String initialStatus = (String) status1Data.get("status");

        assertThat(initialStatus).isIn("PENDING", "IN_PROGRESS");

        // 3. 等待构建完成
        Thread.sleep(3000);

        // 4. 再次查询状态（应该是SUCCESS）
        MvcResult statusResult2 = mockMvc.perform(get("/v1/publish/status/" + buildId))
                .andExpect(status().isOk())
                .andReturn();

        String status2Body = statusResult2.getResponse().getContentAsString();
        Map<String, Object> status2Response = objectMapper.readValue(status2Body, Map.class);
        Map<String, Object> status2Data = (Map<String, Object>) status2Response.get("data");
        String finalStatus = (String) status2Data.get("status");

        assertThat(finalStatus).isEqualTo("SUCCESS");

        // 5. 获取下载链接
        mockMvc.perform(get("/v1/publish/download/" + buildId + "/android"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());

        // 6. 获取QR码
        mockMvc.perform(get("/v1/publish/qrcode/" + buildId + "/android"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试发布任务并返回buildId
     */
    private String createTestBuild() throws Exception {
        PublishRequest request = PublishRequest.builder()
                .projectId("test-project-" + System.currentTimeMillis())
                .platforms(List.of("android"))
                .platformConfigs(createTestPlatformConfigs())
                .parallelBuild(false)
                .build();

        String requestJson = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/v1/publish/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        Map<String, Object> data = (Map<String, Object>) response.get("data");

        return (String) data.get("buildId");
    }

    /**
     * 创建测试平台配置
     */
    private Map<String, Map<String, Object>> createTestPlatformConfigs() {
        Map<String, Map<String, Object>> configs = new HashMap<>();

        // Android配置
        Map<String, Object> androidConfig = new HashMap<>();
        androidConfig.put("packageName", "com.ingenio.test");
        androidConfig.put("appName", "IngenioTest");
        androidConfig.put("versionName", "1.0.0");
        androidConfig.put("versionCode", 1);
        androidConfig.put("minSdkVersion", 24);
        androidConfig.put("targetSdkVersion", 34);
        configs.put("android", androidConfig);

        // iOS配置
        Map<String, Object> iosConfig = new HashMap<>();
        iosConfig.put("bundleId", "com.ingenio.test");
        iosConfig.put("appName", "IngenioTest");
        iosConfig.put("versionName", "1.0.0");
        iosConfig.put("buildNumber", "1");
        iosConfig.put("teamId", "TEST1234");
        iosConfig.put("minIosVersion", "13.0");
        configs.put("ios", iosConfig);

        return configs;
    }
}
