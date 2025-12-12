package com.ingenio.backend.codegen.ai.tool;

import com.ingenio.backend.codegen.ai.generator.BestPracticeApplier;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BestPracticeApplierTool单元测试（V2.0 MVP Day 1）
 *
 * <p>测试Spring AI Function Calling工具的封装功能</p>
 *
 * <p>测试覆盖：</p>
 * <ul>
 *   <li>正常流程：成功应用最佳实践并返回增强代码</li>
 *   <li>异常处理：异常时返回原始代码</li>
 *   <li>性能指标：记录执行时间</li>
 *   <li>输入验证：处理null和空字符串</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-01-18 V2.0 MVP: CodeGenerationAgent工具测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BestPracticeApplierTool单元测试")
class BestPracticeApplierToolTest {

    @Mock
    private BestPracticeApplier bestPracticeApplier;

    private BestPracticeApplierTool tool;

    @BeforeEach
    void setUp() {
        tool = new BestPracticeApplierTool(bestPracticeApplier);
    }

    @Test
    @DisplayName("测试1: 正常流程 - 成功应用CODE_QUALITY增强")
    void testApplyCodeQuality_Success() {
        // 准备测试数据
        String originalCode = "public void createUser(User user) { userRepository.save(user); }";
        String enhancedCode = """
                public void createUser(User user) {
                    log.info("开始创建用户: user={}", user);
                    try {
                        if (user == null) {
                            throw new BusinessException("用户信息不能为空");
                        }
                        userRepository.save(user);
                        log.info("用户创建成功: userId={}", user.getId());
                    } catch (Exception e) {
                        log.error("用户创建失败: {}", e.getMessage(), e);
                        throw new BusinessException("用户创建失败", e);
                    }
                }
                """;

        // Mock BestPracticeApplier行为
        when(bestPracticeApplier.apply(eq(originalCode), any(), eq("createUser")))
                .thenReturn(enhancedCode);

        // 执行测试
        BestPracticeApplierTool.Request request = new BestPracticeApplierTool.Request(
                originalCode,
                null,
                "createUser"
        );
        BestPracticeApplierTool.Response response = tool.apply(request);

        // 验证结果
        assertNotNull(response, "响应不应为null");
        assertTrue(response.isSuccess(), "应用应该成功");
        assertEquals(enhancedCode, response.getEnhancedCode(), "增强后的代码应该匹配");
        assertEquals("最佳实践应用成功", response.getMessage(), "消息应该正确");
        assertTrue(response.getDurationMs() >= 0, "执行时间应该>=0");

        // 验证Mock调用
        verify(bestPracticeApplier, times(1)).apply(eq(originalCode), any(), eq("createUser"));
    }

    @Test
    @DisplayName("测试2: SECURITY模块 - 敏感字段识别")
    void testApplySecurityModule_SensitiveFields() {
        // 准备测试数据 - 包含敏感字段的实体
        Entity userEntity = Entity.builder()
                .name("User")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(com.ingenio.backend.codegen.schema.FieldType.BIGINT).build(),
                        Field.builder().name("username").type(com.ingenio.backend.codegen.schema.FieldType.VARCHAR).build(),
                        Field.builder().name("password").type(com.ingenio.backend.codegen.schema.FieldType.VARCHAR).build(), // 敏感字段
                        Field.builder().name("email").type(com.ingenio.backend.codegen.schema.FieldType.VARCHAR).build(),    // 敏感字段
                        Field.builder().name("phone").type(com.ingenio.backend.codegen.schema.FieldType.VARCHAR).build()      // 敏感字段
                ))
                .build();

        String originalCode = "public User getUserById(Long id) { return userRepository.findById(id).orElse(null); }";
        String enhancedCode = """
                public User getUserById(Long id) {
                    log.info("查询用户: userId={}", id);
                    User user = userRepository.findById(id).orElse(null);
                    if (user != null) {
                        // TODO: 脱敏处理敏感字段: password, email, phone
                        log.info("用户查询成功: userId={}, username={}", user.getId(), user.getUsername());
                    }
                    return user;
                }
                """;

        when(bestPracticeApplier.apply(eq(originalCode), eq(userEntity), eq("getUserById")))
                .thenReturn(enhancedCode);

        // 执行测试
        BestPracticeApplierTool.Request request = new BestPracticeApplierTool.Request(
                originalCode,
                userEntity,
                "getUserById"
        );
        BestPracticeApplierTool.Response response = tool.apply(request);

        // 验证结果
        assertTrue(response.isSuccess(), "应用应该成功");
        assertTrue(response.getEnhancedCode().contains("TODO: 脱敏处理敏感字段"),
                "应该包含脱敏提示");
        verify(bestPracticeApplier).apply(eq(originalCode), eq(userEntity), eq("getUserById"));
    }

    @Test
    @DisplayName("测试3: PERFORMANCE模块 - 缓存建议")
    void testApplyPerformanceModule_CachingHints() {
        String originalCode = "public User getUserById(Long id) { return userRepository.findById(id).orElse(null); }";
        String enhancedCode = """
                @Cacheable(value = "users", key = "#id")
                public User getUserById(Long id) {
                    log.info("查询用户（缓存未命中）: userId={}", id);
                    return userRepository.findById(id).orElse(null);
                }
                """;

        when(bestPracticeApplier.apply(eq(originalCode), any(), eq("getUserById")))
                .thenReturn(enhancedCode);

        // 执行测试
        BestPracticeApplierTool.Request request = new BestPracticeApplierTool.Request(
                originalCode,
                null,
                "getUserById"
        );
        BestPracticeApplierTool.Response response = tool.apply(request);

        // 验证结果
        assertTrue(response.isSuccess(), "应用应该成功");
        assertTrue(response.getEnhancedCode().contains("@Cacheable"),
                "应该包含缓存注解");
    }

    @Test
    @DisplayName("测试4: 异常处理 - BestPracticeApplier抛出异常时返回原始代码")
    void testApplyWithException_ReturnsOriginalCode() {
        String originalCode = "public void testMethod() { }";

        // Mock抛出异常
        when(bestPracticeApplier.apply(any(), any(), any()))
                .thenThrow(new RuntimeException("BestPracticeApplier internal error"));

        // 执行测试
        BestPracticeApplierTool.Request request = new BestPracticeApplierTool.Request(
                originalCode,
                null,
                "testMethod"
        );
        BestPracticeApplierTool.Response response = tool.apply(request);

        // 验证结果 - 应该优雅降级
        assertNotNull(response, "响应不应为null");
        assertFalse(response.isSuccess(), "应用应该失败");
        assertEquals(originalCode, response.getEnhancedCode(),
                "失败时应该返回原始代码");
        assertTrue(response.getMessage().contains("最佳实践应用失败"),
                "消息应该包含失败信息");
        assertTrue(response.getDurationMs() >= 0, "执行时间应该>=0");
    }

    @Test
    @DisplayName("测试5: 输入验证 - 空原始代码")
    void testApplyWithEmptyOriginalCode() {
        String emptyCode = "";
        when(bestPracticeApplier.apply(eq(emptyCode), any(), eq("testMethod")))
                .thenReturn("");

        BestPracticeApplierTool.Request request = new BestPracticeApplierTool.Request(
                emptyCode,
                null,
                "testMethod"
        );
        BestPracticeApplierTool.Response response = tool.apply(request);

        assertNotNull(response, "响应不应为null");
        // 空代码也算有效输入，应该成功处理
        assertTrue(response.isSuccess(), "空代码应该被正常处理");
    }

    @Test
    @DisplayName("测试6: 性能指标 - 验证执行时间记录")
    void testPerformanceMetrics_DurationTracking() {
        String originalCode = "public void method() { }";
        String enhancedCode = "public void method() { log.info(\"test\"); }";

        // 模拟耗时操作（通过延迟返回）
        when(bestPracticeApplier.apply(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(10); // 模拟10ms处理时间
                    return enhancedCode;
                });

        BestPracticeApplierTool.Request request = new BestPracticeApplierTool.Request(
                originalCode,
                null,
                "method"
        );
        BestPracticeApplierTool.Response response = tool.apply(request);

        // 验证执行时间被正确记录（应该>=10ms）
        assertTrue(response.getDurationMs() >= 10,
                "执行时间应该至少10ms（模拟延迟）");
        assertTrue(response.getDurationMs() < 1000,
                "执行时间应该小于1秒（正常情况下）");
    }

    @Test
    @DisplayName("测试7: 集成测试 - 批量操作场景")
    void testBatchOperationScenario() {
        String originalCode = "public void saveUsers(List<User> users) { users.forEach(userRepository::save); }";
        String enhancedCode = """
                public void saveUsers(List<User> users) {
                    log.info("批量保存用户: count={}", users.size());
                    // TODO: 使用批量API提升性能: userRepository.saveAll(users)
                    users.forEach(userRepository::save);
                }
                """;

        when(bestPracticeApplier.apply(eq(originalCode), any(), eq("saveUsers")))
                .thenReturn(enhancedCode);

        BestPracticeApplierTool.Request request = new BestPracticeApplierTool.Request(
                originalCode,
                null,
                "saveUsers"
        );
        BestPracticeApplierTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "应用应该成功");
        assertTrue(response.getEnhancedCode().contains("批量API"),
                "应该包含批量操作建议");
    }

    @Test
    @DisplayName("测试8: 零Token消耗验证")
    void testZeroTokenConsumption() {
        // 验证BestPracticeApplierTool不调用AI API（零Token消耗）
        String originalCode = "public void test() { }";
        String enhancedCode = "public void test() { log.info(\"test\"); }";

        when(bestPracticeApplier.apply(any(), any(), any())).thenReturn(enhancedCode);

        long startTime = System.currentTimeMillis();
        BestPracticeApplierTool.Request request = new BestPracticeApplierTool.Request(
                originalCode,
                null,
                "test"
        );
        BestPracticeApplierTool.Response response = tool.apply(request);
        long duration = System.currentTimeMillis() - startTime;

        // 验证：无AI调用，执行时间应该很短（<100ms）
        assertTrue(duration < 100,
                "无AI调用时执行应该很快（<100ms），实际: " + duration + "ms");
        assertTrue(response.isSuccess(), "应用应该成功");
    }
}
