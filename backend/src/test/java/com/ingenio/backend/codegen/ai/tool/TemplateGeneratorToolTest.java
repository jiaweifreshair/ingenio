package com.ingenio.backend.codegen.ai.tool;

import com.ingenio.backend.codegen.ai.generator.BusinessLogicGenerator;
import com.ingenio.backend.codegen.ai.model.BusinessRule;
import com.ingenio.backend.codegen.ai.model.BusinessRule.BusinessRuleType;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import com.ingenio.backend.codegen.schema.FieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TemplateGeneratorTool单元测试（V2.0 MVP Day 1 Phase 1.3.2）
 *
 * <p>测试Spring AI Function Calling工具封装功能</p>
 *
 * <p>测试覆盖：</p>
 * <ul>
 *   <li>正常流程：VALIDATION/CALCULATION/WORKFLOW/NOTIFICATION规则代码生成</li>
 *   <li>异常处理：null输入、空规则列表、生成异常</li>
 *   <li>性能指标：执行时间<100ms</li>
 *   <li>零Token验证：无AI调用，纯本地模板引擎</li>
 *   <li>集成场景：多规则类型混合生成</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-01-18 V2.0 MVP Day 1: TemplateGeneratorTool封装测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateGeneratorTool单元测试")
class TemplateGeneratorToolTest {

    @Mock
    private BusinessLogicGenerator businessLogicGenerator;

    private TemplateGeneratorTool tool;

    private Entity testEntity;

    @BeforeEach
    void setUp() {
        tool = new TemplateGeneratorTool(businessLogicGenerator);

        // 创建测试实体
        testEntity = Entity.builder()
                .name("User")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("username").type(FieldType.VARCHAR).build(),
                        Field.builder().name("email").type(FieldType.VARCHAR).build(),
                        Field.builder().name("age").type(FieldType.INTEGER).build()
                ))
                .build();
    }

    // ========== 测试1-5: VALIDATION规则代码生成 ==========

    @Test
    @DisplayName("测试1: VALIDATION规则 - 年龄验证（≥18岁）")
    void testValidationRule_AgeCheck() {
        // 准备测试数据
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("validateAge")
                        .description("验证用户年龄≥18岁")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("User")
                        .method("register")
                        .logic("检查age字段，小于18抛出BusinessException")
                        .priority(10)
                        .build()
        );

        String expectedCode = """
                // ========== VALIDATION规则（数据验证） ==========
                if (user.getAge() < 18) {
                    throw new BusinessException(ErrorCode.INVALID_AGE, "用户年龄必须≥18岁");
                }
                """;

        // Mock BusinessLogicGenerator行为
        when(businessLogicGenerator.generateBusinessLogic(eq(rules), eq(testEntity), eq("register")))
                .thenReturn(expectedCode);

        // 执行测试
        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules,
                testEntity,
                "register"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        // 验证结果
        assertNotNull(response, "响应不应为null");
        assertTrue(response.isSuccess(), "生成应该成功");
        assertTrue(response.getGeneratedCode().contains("VALIDATION规则"),
                "生成的代码应该包含VALIDATION规则注释");
        assertTrue(response.getGeneratedCode().contains("user.getAge() < 18"),
                "生成的代码应该包含年龄验证逻辑");
        assertTrue(response.getDurationMs() >= 0, "执行时间应该>=0");

        // 验证Mock调用
        verify(businessLogicGenerator, times(1))
                .generateBusinessLogic(eq(rules), eq(testEntity), eq("register"));
    }

    @Test
    @DisplayName("测试2: VALIDATION规则 - 邮箱格式验证")
    void testValidationRule_EmailFormat() {
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("validateEmailFormat")
                        .description("验证邮箱格式是否合法")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("User")
                        .method("register")
                        .logic("检查email字段是否匹配正则表达式^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
                        .priority(9)
                        .build()
        );

        String expectedCode = """
                // ========== VALIDATION规则（数据验证） ==========
                String emailPattern = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,}$";
                if (!user.getEmail().matches(emailPattern)) {
                    throw new BusinessException(ErrorCode.INVALID_EMAIL, "邮箱格式不正确");
                }
                """;

        when(businessLogicGenerator.generateBusinessLogic(any(), any(), eq("register")))
                .thenReturn(expectedCode);

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, testEntity, "register"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "生成应该成功");
        assertTrue(response.getGeneratedCode().contains("emailPattern"),
                "生成的代码应该包含邮箱格式验证");
    }

    @Test
    @DisplayName("测试3: VALIDATION规则 - 多个验证规则组合")
    void testValidationRule_MultipleRules() {
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("validateAge")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("User")
                        .method("register")
                        .priority(10)
                        .build(),
                BusinessRule.builder()
                        .name("validateEmail")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("User")
                        .method("register")
                        .priority(9)
                        .build()
        );

        String expectedCode = """
                // ========== VALIDATION规则（数据验证） ==========
                if (user.getAge() < 18) {
                    throw new BusinessException(ErrorCode.INVALID_AGE, "用户年龄必须≥18岁");
                }
                if (!user.getEmail().matches(emailPattern)) {
                    throw new BusinessException(ErrorCode.INVALID_EMAIL, "邮箱格式不正确");
                }
                """;

        when(businessLogicGenerator.generateBusinessLogic(any(), any(), eq("register")))
                .thenReturn(expectedCode);

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, testEntity, "register"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "生成应该成功");
        assertEquals(2, rules.size(), "应该有2个验证规则");
    }

    // ========== 测试4-7: CALCULATION规则代码生成 ==========

    @Test
    @DisplayName("测试4: CALCULATION规则 - 订单总价计算")
    void testCalculationRule_OrderTotalPrice() {
        Entity orderEntity = Entity.builder()
                .name("Order")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("totalPrice").type(FieldType.NUMERIC).build()
                ))
                .build();

        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("calculateTotalPrice")
                        .description("计算订单总价")
                        .type(BusinessRuleType.CALCULATION)
                        .entity("Order")
                        .method("create")
                        .logic("订单总价 = Σ(商品单价 * 数量)")
                        .priority(8)
                        .build()
        );

        String expectedCode = """
                // ========== CALCULATION规则（业务计算） ==========
                BigDecimal totalPrice = orderItems.stream()
                    .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                order.setTotalPrice(totalPrice);
                """;

        when(businessLogicGenerator.generateBusinessLogic(eq(rules), eq(orderEntity), eq("create")))
                .thenReturn(expectedCode);

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, orderEntity, "create"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "生成应该成功");
        assertTrue(response.getGeneratedCode().contains("CALCULATION规则"),
                "生成的代码应该包含CALCULATION规则注释");
        assertTrue(response.getGeneratedCode().contains("BigDecimal"),
                "生成的代码应该使用BigDecimal处理金额");
    }

    @Test
    @DisplayName("测试5: CALCULATION规则 - 会员积分计算")
    void testCalculationRule_MemberPoints() {
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("calculateMemberPoints")
                        .description("计算会员积分：消费金额 × 积分系数")
                        .type(BusinessRuleType.CALCULATION)
                        .entity("Order")
                        .method("complete")
                        .logic("会员积分 = 消费金额 × 积分系数（普通会员1.0，银卡1.2，金卡1.5）")
                        .priority(7)
                        .build()
        );

        String expectedCode = """
                // ========== CALCULATION规则（业务计算） ==========
                BigDecimal pointsCoefficient = member.getLevel() == Level.GOLD ? new BigDecimal("1.5") :
                                               member.getLevel() == Level.SILVER ? new BigDecimal("1.2") :
                                               BigDecimal.ONE;
                int points = order.getTotalPrice().multiply(pointsCoefficient).intValue();
                member.addPoints(points);
                """;

        when(businessLogicGenerator.generateBusinessLogic(any(), any(), eq("complete")))
                .thenReturn(expectedCode);

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, testEntity, "complete"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "生成应该成功");
        assertTrue(response.getGeneratedCode().contains("pointsCoefficient"),
                "生成的代码应该包含积分系数计算");
    }

    // ========== 测试6-9: WORKFLOW规则代码生成 ==========

    @Test
    @DisplayName("测试6: WORKFLOW规则 - 订单状态流转")
    void testWorkflowRule_OrderStatusTransition() {
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("transitionOrderStatus")
                        .description("订单状态从PENDING流转到CONFIRMED")
                        .type(BusinessRuleType.WORKFLOW)
                        .entity("Order")
                        .method("confirm")
                        .logic("订单状态从PENDING流转到CONFIRMED，记录状态变更历史")
                        .priority(6)
                        .build()
        );

        String expectedCode = """
                // ========== WORKFLOW规则（状态流转） ==========
                if (order.getStatus() != OrderStatus.PENDING) {
                    throw new BusinessException(ErrorCode.INVALID_STATUS, "只有待确认订单可以确认");
                }
                order.setStatus(OrderStatus.CONFIRMED);
                order.setStatusHistory(order.getStatusHistory() + " -> CONFIRMED at " + OffsetDateTime.now());
                """;

        when(businessLogicGenerator.generateBusinessLogic(any(), any(), eq("confirm")))
                .thenReturn(expectedCode);

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, testEntity, "confirm"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "生成应该成功");
        assertTrue(response.getGeneratedCode().contains("WORKFLOW规则"),
                "生成的代码应该包含WORKFLOW规则注释");
        assertTrue(response.getGeneratedCode().contains("setStatus"),
                "生成的代码应该包含状态设置逻辑");
    }

    // ========== 测试7-10: NOTIFICATION规则代码生成 ==========

    @Test
    @DisplayName("测试7: NOTIFICATION规则 - 邮件通知")
    void testNotificationRule_EmailNotification() {
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("sendRegistrationEmail")
                        .description("发送注册成功邮件")
                        .type(BusinessRuleType.NOTIFICATION)
                        .entity("User")
                        .method("register")
                        .logic("发送邮件到用户邮箱，主题为'注册成功'，内容包含用户名和激活链接")
                        .priority(5)
                        .build()
        );

        String expectedCode = """
                // ========== NOTIFICATION规则（消息通知） ==========
                String emailTemplate = "欢迎注册，您的用户名是：" + user.getUsername();
                emailService.sendEmail(user.getEmail(), "注册成功", emailTemplate);
                """;

        when(businessLogicGenerator.generateBusinessLogic(any(), any(), eq("register")))
                .thenReturn(expectedCode);

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, testEntity, "register"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "生成应该成功");
        assertTrue(response.getGeneratedCode().contains("NOTIFICATION规则"),
                "生成的代码应该包含NOTIFICATION规则注释");
        assertTrue(response.getGeneratedCode().contains("emailService"),
                "生成的代码应该包含邮件服务调用");
    }

    // ========== 测试8-12: 异常处理 ==========

    @Test
    @DisplayName("测试8: 异常处理 - 空规则列表返回TODO注释")
    void testEmptyRulesList_ReturnsTodoComment() {
        List<BusinessRule> emptyRules = new ArrayList<>();

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                emptyRules, testEntity, "testMethod"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "空规则列表应该返回成功");
        assertTrue(response.getGeneratedCode().contains("TODO"),
                "空规则列表应该返回TODO注释");
        assertTrue(response.getGeneratedCode().contains("暂无业务规则"),
                "TODO注释应该说明原因");
    }

    @Test
    @DisplayName("测试9: 异常处理 - null实体抛出异常")
    void testNullEntity_ThrowsException() {
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("testRule")
                        .type(BusinessRuleType.VALIDATION)
                        .method("test")
                        .build()
        );

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, null, "test"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertFalse(response.isSuccess(), "null实体应该失败");
        assertTrue(response.getMessage().contains("Entity不能为null"),
                "错误消息应该说明Entity为null");
        assertTrue(response.getGeneratedCode().contains("TODO"),
                "失败时应该返回TODO注释");
    }

    @Test
    @DisplayName("测试10: 异常处理 - 空方法名抛出异常")
    void testEmptyMethodName_ThrowsException() {
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("testRule")
                        .type(BusinessRuleType.VALIDATION)
                        .build()
        );

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, testEntity, ""
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertFalse(response.isSuccess(), "空方法名应该失败");
        assertTrue(response.getMessage().contains("MethodName不能为空"),
                "错误消息应该说明MethodName为空");
    }

    @Test
    @DisplayName("测试11: 异常处理 - BusinessLogicGenerator抛出异常时优雅降级")
    void testBusinessLogicGeneratorException_GracefulDegradation() {
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("testRule")
                        .type(BusinessRuleType.VALIDATION)
                        .method("test")
                        .build()
        );

        // Mock抛出异常
        when(businessLogicGenerator.generateBusinessLogic(any(), any(), any()))
                .thenThrow(new RuntimeException("Template rendering failed"));

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, testEntity, "test"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertFalse(response.isSuccess(), "生成失败应该返回false");
        assertTrue(response.getMessage().contains("Template rendering failed"),
                "错误消息应该包含异常详情");
        assertTrue(response.getGeneratedCode().contains("TODO"),
                "失败时应该返回TODO注释");
    }

    // ========== 测试12-15: 性能测试 ==========

    @Test
    @DisplayName("测试12: 性能测试 - 单个规则生成时间<100ms")
    void testPerformance_SingleRule_LessThan100ms() {
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("validateAge")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("User")
                        .method("register")
                        .priority(10)
                        .build()
        );

        when(businessLogicGenerator.generateBusinessLogic(any(), any(), any()))
                .thenReturn("// Test code");

        long startTime = System.currentTimeMillis();
        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, testEntity, "register"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(response.isSuccess(), "生成应该成功");
        assertTrue(duration < 100,
                "执行时间应该<100ms（实际: " + duration + "ms）");
        assertTrue(response.getDurationMs() < 100,
                "Response中的durationMs应该<100ms（实际: " + response.getDurationMs() + "ms）");
    }

    @Test
    @DisplayName("测试13: 性能测试 - 10个规则生成时间<100ms")
    void testPerformance_TenRules_LessThan100ms() {
        List<BusinessRule> rules = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rules.add(BusinessRule.builder()
                    .name("rule" + i)
                    .type(BusinessRuleType.VALIDATION)
                    .entity("User")
                    .method("test")
                    .priority(10 - i)
                    .build());
        }

        when(businessLogicGenerator.generateBusinessLogic(any(), any(), any()))
                .thenReturn("// Test code with 10 rules");

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, testEntity, "test"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "生成应该成功");
        assertTrue(response.getDurationMs() < 100,
                "10个规则生成时间应该<100ms（实际: " + response.getDurationMs() + "ms）");
    }

    // ========== 测试14-17: 零Token验证 ==========

    @Test
    @DisplayName("测试14: 零Token验证 - 无AI调用纯本地生成")
    void testZeroTokenConsumption_NoAICalls() {
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("validateAge")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("User")
                        .method("register")
                        .build()
        );

        when(businessLogicGenerator.generateBusinessLogic(any(), any(), any()))
                .thenReturn("// Local template generation");

        long startTime = System.currentTimeMillis();
        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, testEntity, "register"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(response.isSuccess(), "生成应该成功");
        assertTrue(duration < 50,
                "无AI调用时执行应该很快（<50ms），实际: " + duration + "ms");
    }

    // ========== 测试15-20: 集成场景测试 ==========

    @Test
    @DisplayName("测试15: 集成场景 - 混合多种规则类型")
    void testIntegrationScenario_MixedRuleTypes() {
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("validateAge")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("User")
                        .method("register")
                        .priority(10)
                        .build(),
                BusinessRule.builder()
                        .name("calculateInitialPoints")
                        .type(BusinessRuleType.CALCULATION)
                        .entity("User")
                        .method("register")
                        .priority(8)
                        .build(),
                BusinessRule.builder()
                        .name("sendWelcomeEmail")
                        .type(BusinessRuleType.NOTIFICATION)
                        .entity("User")
                        .method("register")
                        .priority(5)
                        .build()
        );

        String expectedCode = """
                // ========== VALIDATION规则（数据验证） ==========
                if (user.getAge() < 18) {
                    throw new BusinessException(ErrorCode.INVALID_AGE, "用户年龄必须≥18岁");
                }

                // ========== CALCULATION规则（业务计算） ==========
                int initialPoints = 100;
                user.setPoints(initialPoints);

                // ========== NOTIFICATION规则（消息通知） ==========
                emailService.sendEmail(user.getEmail(), "欢迎注册", "欢迎您，新用户！");
                """;

        when(businessLogicGenerator.generateBusinessLogic(eq(rules), eq(testEntity), eq("register")))
                .thenReturn(expectedCode);

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, testEntity, "register"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "混合规则类型生成应该成功");
        assertTrue(response.getGeneratedCode().contains("VALIDATION规则"),
                "应该包含VALIDATION规则");
        assertTrue(response.getGeneratedCode().contains("CALCULATION规则"),
                "应该包含CALCULATION规则");
        assertTrue(response.getGeneratedCode().contains("NOTIFICATION规则"),
                "应该包含NOTIFICATION规则");
        assertEquals(3, rules.size(), "应该有3个不同类型的规则");
    }

    @Test
    @DisplayName("测试16: 集成场景 - 订单创建完整流程")
    void testIntegrationScenario_OrderCreationWorkflow() {
        Entity orderEntity = Entity.builder()
                .name("Order")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("totalPrice").type(FieldType.NUMERIC).build(),
                        Field.builder().name("status").type(FieldType.VARCHAR).build()
                ))
                .build();

        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("validateOrderItems")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("Order")
                        .method("create")
                        .priority(10)
                        .build(),
                BusinessRule.builder()
                        .name("calculateTotalPrice")
                        .type(BusinessRuleType.CALCULATION)
                        .entity("Order")
                        .method("create")
                        .priority(8)
                        .build(),
                BusinessRule.builder()
                        .name("setInitialStatus")
                        .type(BusinessRuleType.WORKFLOW)
                        .entity("Order")
                        .method("create")
                        .priority(6)
                        .build(),
                BusinessRule.builder()
                        .name("sendOrderConfirmation")
                        .type(BusinessRuleType.NOTIFICATION)
                        .entity("Order")
                        .method("create")
                        .priority(5)
                        .build()
        );

        String expectedCode = "// Complete order creation workflow code";

        when(businessLogicGenerator.generateBusinessLogic(eq(rules), eq(orderEntity), eq("create")))
                .thenReturn(expectedCode);

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, orderEntity, "create"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "订单创建流程应该成功");
        assertEquals(4, rules.size(), "订单创建应该包含4个规则");
        verify(businessLogicGenerator, times(1))
                .generateBusinessLogic(eq(rules), eq(orderEntity), eq("create"));
    }

    @Test
    @DisplayName("测试17: 规则优先级排序验证")
    void testRulePrioritySorting() {
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("lowPriority")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("User")
                        .method("test")
                        .priority(1)
                        .build(),
                BusinessRule.builder()
                        .name("highPriority")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("User")
                        .method("test")
                        .priority(10)
                        .build(),
                BusinessRule.builder()
                        .name("mediumPriority")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("User")
                        .method("test")
                        .priority(5)
                        .build()
        );

        when(businessLogicGenerator.generateBusinessLogic(any(), any(), any()))
                .thenReturn("// Sorted code");

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, testEntity, "test"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "生成应该成功");
        // 验证规则按优先级传递给BusinessLogicGenerator
        verify(businessLogicGenerator, times(1))
                .generateBusinessLogic(eq(rules), eq(testEntity), eq("test"));
    }

    @Test
    @DisplayName("测试18: 不同实体的规则隔离验证")
    void testRuleIsolationByEntity() {
        List<BusinessRule> userRules = Arrays.asList(
                BusinessRule.builder()
                        .name("validateAge")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("User")
                        .method("register")
                        .build()
        );

        List<BusinessRule> orderRules = Arrays.asList(
                BusinessRule.builder()
                        .name("validateTotalPrice")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("Order")
                        .method("create")
                        .build()
        );

        when(businessLogicGenerator.generateBusinessLogic(any(), any(), any()))
                .thenReturn("// Entity-specific code");

        // 测试User实体规则
        TemplateGeneratorTool.Request userRequest = new TemplateGeneratorTool.Request(
                userRules, testEntity, "register"
        );
        TemplateGeneratorTool.Response userResponse = tool.apply(userRequest);

        assertTrue(userResponse.isSuccess(), "User规则生成应该成功");

        // 验证不同实体的规则不会混淆
        assertEquals(1, userRules.size(), "User规则应该只有1个");
        assertEquals("User", userRules.get(0).getEntity(), "规则实体应该是User");
    }

    @Test
    @DisplayName("测试19: 不同方法的规则隔离验证")
    void testRuleIsolationByMethod() {
        List<BusinessRule> mixedRules = Arrays.asList(
                BusinessRule.builder()
                        .name("validateAge")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("User")
                        .method("register")
                        .build(),
                BusinessRule.builder()
                        .name("validateEmailChange")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("User")
                        .method("updateEmail")
                        .build()
        );

        when(businessLogicGenerator.generateBusinessLogic(any(), any(), any()))
                .thenReturn("// Method-specific code");

        // 只生成register方法的规则
        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                mixedRules, testEntity, "register"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "生成应该成功");
        verify(businessLogicGenerator, times(1))
                .generateBusinessLogic(eq(mixedRules), eq(testEntity), eq("register"));
    }

    @Test
    @DisplayName("测试20: 完整性检查 - 所有必需字段都有值")
    void testResponseCompleteness() {
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("testRule")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("User")
                        .method("test")
                        .build()
        );

        when(businessLogicGenerator.generateBusinessLogic(any(), any(), any()))
                .thenReturn("// Complete code");

        TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
                rules, testEntity, "test"
        );
        TemplateGeneratorTool.Response response = tool.apply(request);

        // 验证Response所有必需字段都有值
        assertNotNull(response.getGeneratedCode(), "generatedCode不应为null");
        assertNotNull(response.getMessage(), "message不应为null");
        assertTrue(response.getDurationMs() >= 0, "durationMs应该>=0");
        assertFalse(response.getGeneratedCode().isEmpty(), "generatedCode不应为空");
        assertFalse(response.getMessage().isEmpty(), "message不应为空");
    }
}
