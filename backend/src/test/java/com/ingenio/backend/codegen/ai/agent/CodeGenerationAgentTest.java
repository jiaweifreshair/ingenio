package com.ingenio.backend.codegen.ai.agent;

import com.ingenio.backend.codegen.ai.tool.BestPracticeApplierTool;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import com.ingenio.backend.codegen.schema.FieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CodeGenerationAgent单元测试（V2.0 MVP Day 1 Phase 1.2）
 *
 * <p>测试核心代码生成Agent的完整流程</p>
 *
 * <p>测试覆盖：</p>
 * <ul>
 *   <li>复杂度分析逻辑（0-100分评分）</li>
 *   <li>策略决策逻辑（三层策略选择）</li>
 *   <li>代码生成流程（Template/Optimizer/Complete）</li>
 *   <li>最佳实践应用集成</li>
 *   <li>验证逻辑</li>
 *   <li>完整6步生成流程</li>
 *   <li>异常处理和降级策略</li>
 *   <li>性能指标记录</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-01-18 V2.0 MVP Day 1: CodeGenerationAgent测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CodeGenerationAgent单元测试")
class CodeGenerationAgentTest {

    @Mock
    private BestPracticeApplierTool bestPracticeApplierTool;

    private CodeGenerationAgent agent;

    @BeforeEach
    void setUp() {
        agent = new CodeGenerationAgent(bestPracticeApplierTool);
    }

    @Test
    @DisplayName("测试1: 纯CRUD操作 - 复杂度应为低分（<20）")
    void testAnalyzeComplexity_PureCRUD_LowScore() {
        // 测试get方法
        Entity entity = createSimpleEntity();
        CodeGenerationAgent.GenerationRequest request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("getUserById")
                .entity(entity)
                .build();

        // Mock BestPracticeApplierTool返回增强代码
        when(bestPracticeApplierTool.apply(any()))
                .thenReturn(new BestPracticeApplierTool.Response(
                        "enhanced code",
                        true,
                        "Success",
                        50L
                ));

        CodeGenerationAgent.GenerationResult result = agent.generate(request);

        assertTrue(result.isSuccess(), "代码生成应该成功");
        assertEquals(15, result.getComplexityScore(), "get方法复杂度应为15分");
        assertEquals(CodeGenerationAgent.GenerationStrategy.TEMPLATE,
                result.getStrategy(), "应该使用Template策略");
    }

    @Test
    @DisplayName("测试2: 简单创建/更新操作 - 复杂度应为中低分（21-40）")
    void testAnalyzeComplexity_SimpleCreateUpdate_MediumLowScore() {
        Entity entity = createSimpleEntity();
        CodeGenerationAgent.GenerationRequest request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("createUser")
                .entity(entity)
                .build();

        when(bestPracticeApplierTool.apply(any()))
                .thenReturn(createSuccessResponse("enhanced code"));

        CodeGenerationAgent.GenerationResult result = agent.generate(request);

        assertTrue(result.isSuccess());
        assertEquals(35, result.getComplexityScore(), "create方法复杂度应为35分");
        assertEquals(CodeGenerationAgent.GenerationStrategy.TEMPLATE, result.getStrategy());
    }

    @Test
    @DisplayName("测试3: 批量操作 - 复杂度应为中分（41-60）")
    void testAnalyzeComplexity_BatchOperation_MediumScore() {
        Entity entity = createSimpleEntity();
        CodeGenerationAgent.GenerationRequest request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("batchCreateUsers")
                .entity(entity)
                .build();

        when(bestPracticeApplierTool.apply(any()))
                .thenReturn(createSuccessResponse("enhanced code"));

        CodeGenerationAgent.GenerationResult result = agent.generate(request);

        assertTrue(result.isSuccess());
        assertEquals(55, result.getComplexityScore(), "batch方法复杂度应为55分");
        assertEquals(CodeGenerationAgent.GenerationStrategy.TEMPLATE, result.getStrategy(),
                "55分应该使用Template策略（<60）");
    }

    @Test
    @DisplayName("测试4: 业务流程处理 - 复杂度应为高分（61-80）")
    void testAnalyzeComplexity_BusinessProcess_HighScore() {
        Entity entity = createSimpleEntity();
        CodeGenerationAgent.GenerationRequest request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("processOrderPayment")
                .entity(entity)
                .build();

        when(bestPracticeApplierTool.apply(any()))
                .thenReturn(createSuccessResponse("enhanced code"));

        CodeGenerationAgent.GenerationResult result = agent.generate(request);

        assertTrue(result.isSuccess());
        assertEquals(70, result.getComplexityScore(), "process方法复杂度应为70分");
        assertEquals(CodeGenerationAgent.GenerationStrategy.AI_OPTIMIZER, result.getStrategy(),
                "70分应该使用AI Optimizer策略（60-90）");
    }

    @Test
    @DisplayName("测试5: 策略决策 - Layer 1: Template（<60分）")
    void testStrategyDecision_Layer1_Template() {
        Entity entity = createSimpleEntity();
        CodeGenerationAgent.GenerationRequest request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("listUsers")  // 列表查询，复杂度15分
                .entity(entity)
                .build();

        // Mock返回包含Repository的代码
        when(bestPracticeApplierTool.apply(any()))
                .thenReturn(createSuccessResponse("public User listUsers(Long id) { return userRepository.findById(id).orElse(null); }"));

        CodeGenerationAgent.GenerationResult result = agent.generate(request);

        assertEquals(CodeGenerationAgent.GenerationStrategy.TEMPLATE, result.getStrategy());
        assertTrue(result.getGeneratedCode().contains("Repository"),
                "Template策略应该生成包含Repository的代码");
    }

    @Test
    @DisplayName("测试6: 策略决策 - Layer 2: AI Optimizer（60-90分）")
    void testStrategyDecision_Layer2_AIOptimizer() {
        Entity entity = createSimpleEntity();
        CodeGenerationAgent.GenerationRequest request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("handleOrderRefund")  // handle方法，复杂度70分
                .entity(entity)
                .build();

        // Mock返回包含参数校验的代码
        when(bestPracticeApplierTool.apply(any()))
                .thenReturn(createSuccessResponse(
                        "public User handleOrderRefund(Long id) { " +
                        "if (id == null) { throw new IllegalArgumentException(\"ID不能为空\"); } " +
                        "return userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException(\"User不存在\")); }"));

        CodeGenerationAgent.GenerationResult result = agent.generate(request);

        assertEquals(CodeGenerationAgent.GenerationStrategy.AI_OPTIMIZER, result.getStrategy());
        assertTrue(result.getGeneratedCode().contains("IllegalArgumentException"),
                "AI Optimizer策略应该包含参数校验");
    }

    @Test
    @DisplayName("测试7: BestPracticeApplierTool集成 - 成功应用增强")
    void testBestPracticeIntegration_Success() {
        Entity entity = createSimpleEntity();
        String originalCode = "public User getUser(Long id) { return userRepository.findById(id).orElse(null); }";
        String enhancedCode = originalCode + "\n// Enhanced with logging and validation";

        CodeGenerationAgent.GenerationRequest request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("getUserById")
                .entity(entity)
                .build();

        when(bestPracticeApplierTool.apply(any()))
                .thenReturn(new BestPracticeApplierTool.Response(
                        enhancedCode,
                        true,
                        "最佳实践应用成功",
                        50L
                ));

        CodeGenerationAgent.GenerationResult result = agent.generate(request);

        assertTrue(result.isSuccess());
        assertTrue(result.getGeneratedCode().contains("Enhanced with logging"),
                "代码应该包含最佳实践增强");
        verify(bestPracticeApplierTool, times(1)).apply(any());
    }

    @Test
    @DisplayName("测试8: BestPracticeApplierTool集成 - 失败时优雅降级")
    void testBestPracticeIntegration_GracefulDegradation() {
        Entity entity = createSimpleEntity();
        CodeGenerationAgent.GenerationRequest request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("getUserById")
                .entity(entity)
                .build();

        // Mock BestPracticeApplierTool失败
        when(bestPracticeApplierTool.apply(any()))
                .thenReturn(new BestPracticeApplierTool.Response(
                        "original code",  // 返回原始代码
                        false,
                        "最佳实践应用失败",
                        50L
                ));

        CodeGenerationAgent.GenerationResult result = agent.generate(request);

        assertTrue(result.isSuccess(), "即使最佳实践失败，代码生成仍应成功");
        assertNotNull(result.getGeneratedCode(), "应该返回原始生成的代码");
    }

    @Test
    @DisplayName("测试9: 验证逻辑 - 基础语法验证")
    void testValidation_BasicSyntax() {
        Entity entity = createSimpleEntity();
        CodeGenerationAgent.GenerationRequest request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("getUserById")
                .entity(entity)
                .build();

        when(bestPracticeApplierTool.apply(any()))
                .thenReturn(createSuccessResponse("public User getUserById(Long id) { return userRepository.findById(id).orElse(null); }"));

        CodeGenerationAgent.GenerationResult result = agent.generate(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.getValidationResult());
        assertTrue(result.getValidationResult().isSuccess(), "验证应该通过");
        assertTrue(result.getValidationResult().getQualityScore() >= 95,
                "质量评分应该≥95分");
    }

    @Test
    @DisplayName("测试10: 完整6步生成流程")
    void testCompleteGenerationPipeline_SixSteps() {
        Entity entity = createComplexEntity();
        CodeGenerationAgent.GenerationRequest request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("processUserRegistration")
                .entity(entity)
                .requirement("用户注册需要包含邮箱验证、密码加密、欢迎邮件发送")
                .build();

        when(bestPracticeApplierTool.apply(any()))
                .thenReturn(createSuccessResponse("enhanced registration code"));

        long startTime = System.currentTimeMillis();
        CodeGenerationAgent.GenerationResult result = agent.generate(request);
        long duration = System.currentTimeMillis() - startTime;

        // 验证6步流程执行结果
        assertTrue(result.isSuccess(), "代码生成应该成功");
        assertNotNull(result.getGeneratedCode(), "应该返回生成的代码");
        assertTrue(result.getComplexityScore() >= 0 && result.getComplexityScore() <= 100,
                "复杂度评分应该在0-100之间");
        assertNotNull(result.getStrategy(), "应该返回生成策略");
        assertNotNull(result.getValidationResult(), "应该返回验证结果");
        assertTrue(result.getDurationMs() >= 0, "执行时间应该>=0");
        assertTrue(duration < 5000, "完整流程应该在5秒内完成（MVP简化版本）");

        verify(bestPracticeApplierTool, times(1)).apply(any());
    }

    @Test
    @DisplayName("测试11: 异常处理 - BestPracticeApplierTool抛出异常")
    void testExceptionHandling_BestPracticeApplierThrowsException() {
        Entity entity = createSimpleEntity();
        CodeGenerationAgent.GenerationRequest request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("getUserById")
                .entity(entity)
                .build();

        // Mock抛出异常
        when(bestPracticeApplierTool.apply(any()))
                .thenThrow(new RuntimeException("Internal error"));

        CodeGenerationAgent.GenerationResult result = agent.generate(request);

        // 应该捕获异常并返回失败结果
        assertFalse(result.isSuccess(), "遇到异常时应该返回失败");
        assertTrue(result.getMessage().contains("代码生成失败"),
                "消息应该包含失败信息");
        assertTrue(result.getGeneratedCode().contains("代码生成失败"),
                "生成的代码应该包含错误信息");
    }

    @Test
    @DisplayName("测试12: 性能指标 - 执行时间记录")
    void testPerformanceMetrics_DurationTracking() {
        Entity entity = createSimpleEntity();
        CodeGenerationAgent.GenerationRequest request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("getUserById")
                .entity(entity)
                .build();

        // 模拟耗时操作
        when(bestPracticeApplierTool.apply(any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(10);  // 模拟10ms处理时间
                    return createSuccessResponse("enhanced code");
                });

        CodeGenerationAgent.GenerationResult result = agent.generate(request);

        assertTrue(result.isSuccess());
        assertTrue(result.getDurationMs() >= 10,
                "执行时间应该至少10ms（模拟延迟）");
        assertTrue(result.getDurationMs() < 1000,
                "执行时间应该小于1秒（正常情况下）");
    }

    @Test
    @DisplayName("测试13: null实体处理")
    void testNullEntityHandling() {
        CodeGenerationAgent.GenerationRequest request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("getUserById")
                .entity(null)  // null实体
                .build();

        when(bestPracticeApplierTool.apply(any()))
                .thenReturn(createSuccessResponse("public Entity getUserById(Long id) { return entityRepository.findById(id).orElse(null); }"));

        CodeGenerationAgent.GenerationResult result = agent.generate(request);

        // 应该优雅处理null实体
        assertTrue(result.isSuccess(), "null实体应该被优雅处理");
        assertTrue(result.getGeneratedCode().contains("Entity"),
                "null实体时应该使用默认实体名");
    }

    @Test
    @DisplayName("测试14: 三层策略代码差异验证")
    void testThreeLayerStrategyCodeDifferences() {
        Entity entity = createSimpleEntity();

        // Layer 1: Template (15分)
        CodeGenerationAgent.GenerationRequest layer1Request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("getUserById")
                .entity(entity)
                .build();

        // Layer 2: AI Optimizer (70分)
        CodeGenerationAgent.GenerationRequest layer2Request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("processUserPayment")
                .entity(entity)
                .build();

        // Mock返回不同策略的代码
        when(bestPracticeApplierTool.apply(any()))
                .thenAnswer(invocation -> {
                    BestPracticeApplierTool.Request req = invocation.getArgument(0);
                    String originalCode = req.getOriginalCode();
                    // 根据原始代码的不同，返回不同的增强代码
                    if (originalCode.contains("IllegalArgumentException")) {
                        // AI Optimizer生成的代码包含参数校验
                        return createSuccessResponse(originalCode);
                    } else {
                        // Template生成的代码不包含参数校验
                        return createSuccessResponse(originalCode);
                    }
                });

        CodeGenerationAgent.GenerationResult layer1Result = agent.generate(layer1Request);
        CodeGenerationAgent.GenerationResult layer2Result = agent.generate(layer2Request);

        // Layer 1应该是Template策略
        assertEquals(CodeGenerationAgent.GenerationStrategy.TEMPLATE, layer1Result.getStrategy());
        assertFalse(layer1Result.getGeneratedCode().contains("IllegalArgumentException"),
                "Template策略不应该包含参数校验");

        // Layer 2应该是AI Optimizer策略
        assertEquals(CodeGenerationAgent.GenerationStrategy.AI_OPTIMIZER, layer2Result.getStrategy());
        assertTrue(layer2Result.getGeneratedCode().contains("IllegalArgumentException"),
                "AI Optimizer策略应该包含参数校验");
    }

    @Test
    @DisplayName("测试15: 复杂实体场景 - 多字段敏感数据")
    void testComplexEntityScenario_SensitiveFields() {
        Entity complexEntity = createComplexEntity();
        CodeGenerationAgent.GenerationRequest request = CodeGenerationAgent.GenerationRequest.builder()
                .methodName("getUserWithSensitiveData")
                .entity(complexEntity)
                .build();

        when(bestPracticeApplierTool.apply(any()))
                .thenReturn(createSuccessResponse("enhanced code with security"));

        CodeGenerationAgent.GenerationResult result = agent.generate(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.getGeneratedCode());
        // 验证复杂实体被正确传递给BestPracticeApplierTool
        verify(bestPracticeApplierTool).apply(
                argThat(req ->
                        req.getEntity() != null &&
                        req.getEntity().getName().equals("User")
                )
        );
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建简单实体（用于大部分测试）
     */
    private Entity createSimpleEntity() {
        return Entity.builder()
                .name("User")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("username").type(FieldType.VARCHAR).build()
                ))
                .build();
    }

    /**
     * 创建复杂实体（包含敏感字段）
     */
    private Entity createComplexEntity() {
        return Entity.builder()
                .name("User")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("username").type(FieldType.VARCHAR).build(),
                        Field.builder().name("password").type(FieldType.VARCHAR).build(),
                        Field.builder().name("email").type(FieldType.VARCHAR).build(),
                        Field.builder().name("phone").type(FieldType.VARCHAR).build(),
                        Field.builder().name("creditCard").type(FieldType.VARCHAR).build()
                ))
                .build();
    }

    /**
     * 创建成功的BestPracticeApplierTool响应
     */
    private BestPracticeApplierTool.Response createSuccessResponse(String enhancedCode) {
        return new BestPracticeApplierTool.Response(
                enhancedCode,
                true,
                "最佳实践应用成功",
                50L
        );
    }
}
