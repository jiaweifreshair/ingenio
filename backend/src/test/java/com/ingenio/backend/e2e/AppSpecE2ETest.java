package com.ingenio.backend.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.config.TestSaTokenConfig;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import com.ingenio.backend.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AppSpecController E2E测试
 *
 * 测试场景：
 * 1. 创建AppSpec（成功和失败场景）
 * 2. 查询AppSpec详情
 * 3. 更新AppSpec状态
 * 4. 删除AppSpec（软删除）
 * 5. 分页查询AppSpec列表
 * 6. 创建AppSpec新版本
 *
 * 测试策略：
 * - 使用真实PostgreSQL数据库（TestContainers）
 * - 零Mock策略：使用真实Service和Mapper
 * - 每个测试前初始化测试数据
 * - 验证数据库状态确保正确性
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@DisplayName("AppSpec管理API E2E测试")
public class AppSpecE2ETest extends BaseE2ETest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AppSpecMapper appSpecMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID testUserId;
    private UUID testTenantId;
    private UUID testAppSpecId;

    /**
     * 每个测试前初始化测试数据
     */
    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();

        // 使用TestSaTokenConfig中的固定TEST_USER_ID，确保与Sa-Token Mock的userId一致
        testUserId = TestSaTokenConfig.TEST_USER_ID;
        testTenantId = TestSaTokenConfig.TEST_TENANT_ID;
        long timestamp = System.currentTimeMillis();

        // 删除可能已存在的测试用户（避免主键冲突）
        try {
            userMapper.deleteById(testUserId);
        } catch (Exception e) {
            // 忽略删除错误（用户可能不存在）
        }

        // 创建测试用户（满足外键约束），使用固定的TEST_USER_ID
        UserEntity testUser = UserEntity.builder()
            .id(testUserId)  // 使用固定ID
            .tenantId(testTenantId)
            .username("test-appspec-user-" + timestamp)
            .email("test-appspec-" + timestamp + "@ingenio.test")
            .passwordHash("test-password-hash")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        userMapper.insert(testUser);

        // 创建测试AppSpec
        testAppSpecId = UUID.randomUUID();

        // 使用HashMap构建specContent（避免Map.of()的不可变Map导致JacksonTypeHandler序列化问题）
        Map<String, Object> homeComponent = new HashMap<>();
        homeComponent.put("title", "首页");
        homeComponent.put("components", "Button,Text");

        Map<String, Object> pages = new HashMap<>();
        pages.put("home", homeComponent);

        Map<String, Object> userModel = new HashMap<>();
        userModel.put("fields", "id,name,email");

        Map<String, Object> dataModels = new HashMap<>();
        dataModels.put("User", userModel);

        Map<String, Object> specContent = new HashMap<>();
        specContent.put("pages", pages);
        specContent.put("dataModels", dataModels);

        AppSpecEntity appSpec = AppSpecEntity.builder()
            .id(testAppSpecId)
            .tenantId(testTenantId)
            .createdByUserId(testUserId)
            .specContent(specContent)
            .version(1)
            .status(AppSpecEntity.Status.DRAFT.getValue())
            .qualityScore(0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .metadata(new HashMap<>())
            .build();
        appSpecMapper.insert(appSpec);
    }

    @Test
    @DisplayName("测试1: 创建AppSpec - 成功场景")
    public void testCreateAppSpec_Success() throws Exception {
        // 准备请求数据
        Map<String, Object> newSpecContent = new HashMap<>();
        newSpecContent.put("pages", Map.of("profile", Map.of("title", "个人中心")));
        newSpecContent.put("dataModels", Map.of("Post", Map.of("fields", "id,title,content")));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("specContent", newSpecContent);

        String requestJson = objectMapper.writeValueAsString(requestBody);

        // 执行POST请求
        MvcResult result = mockMvc.perform(post("/api/v1/appspecs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn();

        // 提取创建的AppSpec ID
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
        String appSpecId = ((Map<String, Object>) responseMap.get("data")).get("id").toString();

        // 验证数据库中记录已创建
        AppSpecEntity createdAppSpec = appSpecMapper.selectById(UUID.fromString(appSpecId));
        assertNotNull(createdAppSpec, "数据库中应存在创建的AppSpec记录");
        assertEquals("draft", createdAppSpec.getStatus());
        assertEquals(1, createdAppSpec.getVersion());
    }

    @Test
    @DisplayName("测试2: 创建AppSpec - 参数验证失败")
    public void testCreateAppSpec_InvalidRequest() throws Exception {
        // 准备空的请求体（缺少specContent字段）
        Map<String, Object> requestBody = new HashMap<>();
        String requestJson = objectMapper.writeValueAsString(requestBody);

        // 执行POST请求，期望返回业务错误码（HTTP 200 + code=1001）
        mockMvc.perform(post("/api/v1/appspecs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1001"));
    }

    @Test
    @DisplayName("测试3: 查询AppSpec详情 - 成功场景")
    public void testGetAppSpec_Success() throws Exception {
        // 执行GET请求
        mockMvc.perform(get("/api/v1/appspecs/{id}", testAppSpecId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.id").value(testAppSpecId.toString()))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.specContent.pages.home.title").value("首页"));
    }

    @Test
    @DisplayName("测试4: 更新AppSpec状态 - 成功场景")
    public void testUpdateAppSpecStatus_Success() throws Exception {
        // 准备状态更新请求
        Map<String, String> statusRequest = new HashMap<>();
        statusRequest.put("status", "validated");

        String requestJson = objectMapper.writeValueAsString(statusRequest);

        // 执行PUT请求
        mockMvc.perform(put("/api/v1/appspecs/{id}/status", testAppSpecId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));

        // 验证数据库中状态已更新
        AppSpecEntity updatedAppSpec = appSpecMapper.selectById(testAppSpecId);
        assertEquals("validated", updatedAppSpec.getStatus(), "AppSpec状态应已更新为validated");
    }

    @Test
    @DisplayName("测试5: 删除AppSpec - 软删除成功")
    public void testDeleteAppSpec_Success() throws Exception {
        // 执行DELETE请求
        mockMvc.perform(delete("/api/v1/appspecs/{id}", testAppSpecId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("删除成功"));

        // 验证数据库中记录已被软删除（MyBatis-Plus逻辑删除）
        AppSpecEntity deletedAppSpec = appSpecMapper.selectById(testAppSpecId);
        assertNull(deletedAppSpec, "AppSpec应已被软删除，查询应返回null");
    }

    @Test
    @DisplayName("测试6: 创建AppSpec新版本 - 成功场景")
    public void testCreateAppSpecVersion_Success() throws Exception {
        // 准备新版本的specContent（使用HashMap避免Map.of()的不可变Map问题）
        Map<String, Object> homePageV2 = new HashMap<>();
        homePageV2.put("title", "首页V2");
        homePageV2.put("components", "Button,Text,Image");

        Map<String, Object> profilePage = new HashMap<>();
        profilePage.put("title", "个人中心");
        profilePage.put("components", "Avatar,Form");

        Map<String, Object> pagesV2 = new HashMap<>();
        pagesV2.put("home", homePageV2);
        pagesV2.put("profile", profilePage);

        Map<String, Object> userModelV2 = new HashMap<>();
        userModelV2.put("fields", "id,name,email,avatar");

        Map<String, Object> dataModelsV2 = new HashMap<>();
        dataModelsV2.put("User", userModelV2);

        Map<String, Object> newVersionSpecContent = new HashMap<>();
        newVersionSpecContent.put("pages", pagesV2);
        newVersionSpecContent.put("dataModels", dataModelsV2);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("specContent", newVersionSpecContent);

        String requestJson = objectMapper.writeValueAsString(requestBody);

        // 执行POST请求创建新版本
        MvcResult result = mockMvc.perform(post("/api/v1/appspecs/{id}/versions", testAppSpecId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.parentVersionId").value(testAppSpecId.toString()))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn();

        // 提取新版本ID
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
        String newVersionId = ((Map<String, Object>) responseMap.get("data")).get("id").toString();

        // 验证数据库中新版本已创建
        AppSpecEntity newVersion = appSpecMapper.selectById(UUID.fromString(newVersionId));
        assertNotNull(newVersion, "新版本应已创建");
        assertEquals(2, newVersion.getVersion(), "版本号应为2");
        assertEquals(testAppSpecId, newVersion.getParentVersionId(), "父版本ID应正确");
        assertEquals("draft", newVersion.getStatus(), "新版本状态应为draft");
    }

    @Test
    @DisplayName("测试7: 分页查询AppSpec列表 - 成功场景")
    public void testListAppSpecs_Success() throws Exception {
        // 创建多个测试AppSpec
        for (int i = 1; i <= 5; i++) {
            UUID newAppSpecId = UUID.randomUUID();

            // 使用HashMap避免Map.of()的不可变Map问题
            Map<String, Object> pageData = new HashMap<>();
            pageData.put("title", "页面" + i);

            Map<String, Object> pages = new HashMap<>();
            pages.put("page" + i, pageData);

            Map<String, Object> specContent = new HashMap<>();
            specContent.put("pages", pages);

            AppSpecEntity appSpec = AppSpecEntity.builder()
                .id(newAppSpecId)
                .tenantId(testTenantId)
                .createdByUserId(testUserId)
                .specContent(specContent)
                .version(1)
                .status(AppSpecEntity.Status.DRAFT.getValue())
                .qualityScore(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
            appSpecMapper.insert(appSpec);
        }

        // 执行GET请求查询列表（第1页，每页10条）
        mockMvc.perform(get("/api/v1/appspecs")
                .param("current", "1")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.records", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    @DisplayName("测试8: 查询不存在的AppSpec - 错误处理")
    public void testGetNonExistentAppSpec() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        // 执行GET请求，期望返回错误
        mockMvc.perform(get("/api/v1/appspecs/{id}", nonExistentId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("3000")); // APPSPEC_NOT_FOUND
    }
}
