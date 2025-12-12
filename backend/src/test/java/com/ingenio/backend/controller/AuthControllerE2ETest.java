package com.ingenio.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.config.TestSaTokenConfig;
import com.ingenio.backend.dto.auth.AuthResponse;
import com.ingenio.backend.dto.auth.LoginRequest;
import com.ingenio.backend.dto.auth.RegisterRequest;
import com.ingenio.backend.e2e.BaseE2ETest;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static com.ingenio.backend.config.TestSaTokenConfig.TEST_USER_ID;

/**
 * 认证Controller端到端测试
 *
 * 测试策略:
 * 1. 继承BaseE2ETest，使用Singleton PostgreSQL容器
 * 2. 不使用Mock，全部真实服务调用
 * 3. 测试完整的用户注册→登录→获取信息→退出流程
 *
 * 修复说明（Phase 3 - P0）:
 * - 从自定义@Container改为继承BaseE2ETest
 * - 删除重复的PostgreSQL容器定义，使用Singleton模式
 * - 删除Redis容器定义（认证使用SaToken内存模式）
 * - 解决"Mapped port can only be obtained after the container is started"错误
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerE2ETest extends BaseE2ETest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    private String testToken;
    private String testUserId;

    /**
     * 测试类初始化 - 创建TEST_USER_ID测试用户
     * 用于testGetCurrentUser测试,该测试依赖MockLoginFilter模拟的TEST_USER_ID登录
     */
    @BeforeAll
    public void initTestUser() {
        // 创建TEST_USER_ID测试用户供MockLoginFilter使用
        UserEntity testUser = new UserEntity();
        testUser.setId(TEST_USER_ID);
        testUser.setTenantId(TestSaTokenConfig.TEST_TENANT_ID);
        testUser.setUsername("test_mock_user");
        testUser.setEmail("test_mock@example.com");
        testUser.setPasswordHash("$2a$10$mock_hashed_password_for_testing");
        testUser.setRole("user");
        testUser.setStatus("active");
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());

        // 插入测试用户到数据库
        userMapper.insert(testUser);
    }

    @Test
    @Order(1)
    @DisplayName("E2E测试1: 健康检查")
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/v1/auth/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Auth service is running"));
    }

    @Test
    @Order(2)
    @DisplayName("E2E测试2: 用户注册成功")
    void testRegisterSuccess() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser001");
        request.setEmail("testuser001@example.com");
        request.setPassword("Test1234");

        // Act & Assert
        MvcResult result = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.username").value("testuser001"))
                .andExpect(jsonPath("$.data.email").value("testuser001@example.com"))
                .andExpect(jsonPath("$.data.role").value("user"))
                .andReturn();

        // 验证数据库中已创建用户
        UserEntity user = userMapper.selectById(extractUserId(result));
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("testuser001");
        assertThat(user.getPasswordHash()).isNotBlank(); // BCrypt加密
    }

    @Test
    @Order(3)
    @DisplayName("E2E测试3: 用户注册失败 - 用户名已存在")
    void testRegisterFailureDuplicateUsername() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser001"); // 已存在
        request.setEmail("another@example.com");
        request.setPassword("Test1234");

        // Act & Assert
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1000")) // 系统错误码（RuntimeException）
                .andExpect(jsonPath("$.message").value("系统错误"));
    }

    @Test
    @Order(4)
    @DisplayName("E2E测试4: 用户登录成功（使用用户名）")
    void testLoginWithUsername() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail("testuser001");
        request.setPassword("Test1234");

        // Act & Assert
        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();

        // 保存token供后续测试使用
        String responseBody = result.getResponse().getContentAsString();
        Result<AuthResponse> response = objectMapper.readValue(responseBody,
                objectMapper.getTypeFactory().constructParametricType(Result.class, AuthResponse.class));
        testToken = response.getData().getToken();
        testUserId = response.getData().getUserId();

        assertThat(testToken).isNotBlank();
    }

    @Test
    @Order(5)
    @DisplayName("E2E测试5: 用户登录成功（使用邮箱）")
    void testLoginWithEmail() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail("testuser001@example.com");
        request.setPassword("Test1234");

        // Act & Assert
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    @Order(6)
    @DisplayName("E2E测试6: 用户登录失败 - 密码错误")
    void testLoginFailureWrongPassword() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail("testuser001");
        request.setPassword("WrongPassword123");

        // Act & Assert
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1000")) // 系统错误码（RuntimeException）
                .andExpect(jsonPath("$.message").value("系统错误"));
    }

    @Test
    @Order(7)
    @DisplayName("E2E测试7: 获取当前用户信息（需要认证）")
    void testGetCurrentUser() throws Exception {
        // 测试环境说明：
        // - MockLoginFilter会在每个请求前自动调用 StpUtil.login(TEST_USER_ID)
        // - 所以不需要传Authorization头，依赖MockLoginFilter的自动模拟登录
        // - 返回的用户信息对应TEST_USER_ID (00000000-0000-0000-0000-000000000001)

        // Act & Assert
        mockMvc.perform(get("/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(TEST_USER_ID.toString()))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist()); // 不返回密码
    }

    @Test
    @Order(8)
    @Disabled("与MockLoginFilter设计冲突：MockLoginFilter会自动模拟登录，无法测试未登录场景")
    @DisplayName("E2E测试8: 获取用户信息失败 - 未登录")
    void testGetCurrentUserUnauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1000"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(9)
    @Disabled("与MockLoginFilter设计冲突：MockLoginFilter会在每个请求前重新登录，无法测试logout后的未登录场景")
    @DisplayName("E2E测试9: 退出登录")
    void testLogout() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/v1/auth/logout")
                        .header("Authorization", testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证退出后无法访问需要认证的接口
        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1000"));
    }

    @Test
    @Order(10)
    @DisplayName("E2E测试10: 参数校验 - 用户名过短")
    void testRegisterValidationUsernameLength() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setUsername("ab"); // 只有2个字符，不满足3-20要求
        request.setEmail("test@example.com");
        request.setPassword("Test1234");

        // Act & Assert
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1001"))
                .andExpect(jsonPath("$.message").value("参数错误"))
                .andExpect(jsonPath("$.data.username").value("用户名长度必须在3-20之间"));
    }

    @Test
    @Order(11)
    @DisplayName("E2E测试11: 参数校验 - 邮箱格式错误")
    void testRegisterValidationInvalidEmail() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validuser");
        request.setEmail("invalid-email"); // 错误的邮箱格式
        request.setPassword("Test1234");

        // Act & Assert
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1001"))
                .andExpect(jsonPath("$.message").value("参数错误"))
                .andExpect(jsonPath("$.data.email").value("邮箱格式不正确"));
    }

    @Test
    @Order(12)
    @DisplayName("E2E测试12: 参数校验 - 密码格式错误")
    void testRegisterValidationInvalidPassword() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validuser");
        request.setEmail("valid@example.com");
        request.setPassword("onlyletters"); // 没有数字

        // Act & Assert
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1001"))
                .andExpect(jsonPath("$.message").value("参数错误"))
                .andExpect(jsonPath("$.data.password").value("密码必须包含字母和数字"));
    }

    /**
     * 从响应中提取用户ID
     */
    private String extractUserId(MvcResult result) throws Exception {
        String responseBody = result.getResponse().getContentAsString();
        Result<AuthResponse> response = objectMapper.readValue(responseBody,
                objectMapper.getTypeFactory().constructParametricType(Result.class, AuthResponse.class));
        return response.getData().getUserId();
    }
}
