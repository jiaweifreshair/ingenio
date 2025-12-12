package com.ingenio.backend.codegen.ai.tool;

import com.ingenio.backend.codegen.ai.model.BusinessRule;
import com.ingenio.backend.codegen.ai.model.BusinessRule.BusinessRuleType;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.ai.tool.ServiceClassGeneratorTool;
import com.ingenio.backend.codegen.schema.Field;
import com.ingenio.backend.codegen.schema.FieldType;
import com.ingenio.backend.e2e.BaseE2ETest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeGeneration E2E测试 - 完整代码生成管道验证（V2.0 MVP Day 2 Phase 2.3）
 *
 * <p>测试5个真实业务场景的端到端代码生成流程：</p>
 * <ol>
 *   <li>用户管理CRUD（基础场景）</li>
 *   <li>订单管理（复杂业务逻辑）</li>
 *   <li>商品目录（多条件查询）</li>
 *   <li>认证授权（安全敏感）</li>
 *   <li>支付处理（金融级要求）</li>
 * </ol>
 *
 * <p>测试管道：ComplexityAnalyzer → TemplateGenerator → BestPracticeApplier → ValidationTool</p>
 *
 * <p>继承BaseE2ETest获得TestContainers支持：</p>
 * <ul>
 *   <li>真实PostgreSQL数据库（通过TestContainers启动）</li>
 *   <li>零Mock策略：使用真实数据库和服务</li>
 *   <li>数据库初始化：自动执行schema.sql</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-19 V2.0 MVP Day 2 Phase 2.3: E2E场景测试
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CodeGenerationE2ETest extends BaseE2ETest {

    @Autowired
    private ComplexityAnalyzerTool complexityAnalyzerTool;

    @Autowired
    private TemplateGeneratorTool templateGeneratorTool;

    @Autowired
    private BestPracticeApplierTool bestPracticeApplierTool;

    @Autowired
    private ValidationTool validationTool;

    @Autowired
    private ServiceClassGeneratorTool serviceClassGeneratorTool;

    @Autowired
    private com.ingenio.backend.codegen.ai.autofix.AutoFixOrchestrator autoFixOrchestrator;

    // ==================== 场景1：用户管理CRUD ====================

    @Test
    @Order(1)
    @DisplayName("场景1: 用户CRUD - 完整E2E管道")
    void testUserCrud_CompleteE2EPipeline() {
        // Given
        Entity userEntity = createUserEntity();
        String methodName = "createUser";
        String requirement = "创建用户，验证邮箱唯一性";

        // Step 1: 复杂度分析
        ComplexityAnalyzerTool.Response complexityResponse = complexityAnalyzerTool.apply(
                new ComplexityAnalyzerTool.Request(methodName, userEntity, requirement)
        );

        assertTrue(complexityResponse.isSuccess(), "复杂度分析应成功");
        assertTrue(complexityResponse.getComplexityScore() >= 10 && complexityResponse.getComplexityScore() <= 50,
                "用户CRUD复杂度应在10-50");

        // Step 2: 模板生成
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("validateEmail")
                        .description("验证邮箱格式")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("users")
                        .method("createUser")
                        .logic("检查email字段格式")
                        .priority(10)
                        .build()
        );

        TemplateGeneratorTool.Response templateResponse = templateGeneratorTool.apply(
                new TemplateGeneratorTool.Request(rules, userEntity, methodName)
        );

        assertTrue(templateResponse.isSuccess(), "模板生成应成功");
        assertNotNull(templateResponse.getGeneratedCode(), "代码不应为空");

        // Step 3: 最佳实践应用
        BestPracticeApplierTool.Response practiceResponse = bestPracticeApplierTool.apply(
                new BestPracticeApplierTool.Request(templateResponse.getGeneratedCode(), userEntity, methodName)
        );

        assertTrue(practiceResponse.isSuccess(), "最佳实践应用应成功");

        // Step 4: 生成完整Service类
        ServiceClassGeneratorTool.Response serviceClassResponse = serviceClassGeneratorTool.apply(
                ServiceClassGeneratorTool.Request.builder()
                        .businessLogicCode(practiceResponse.getEnhancedCode())
                        .entity(userEntity)
                        .methodName(methodName)
                        .methodDescription("创建用户")
                        .build()
        );

        assertTrue(serviceClassResponse.isSuccess(), "Service类生成应成功");
        assertNotNull(serviceClassResponse.getCompleteCode(), "完整Service类代码不应为空");

        // Step 5: 代码验证 + 自动修复（集成AutoFixOrchestrator）
        String finalCode = validateAndAutoFix(
                serviceClassResponse.getCompleteCode(),
                userEntity,
                methodName,
                "场景1-用户CRUD"
        );

        assertNotNull(finalCode, "最终代码不应为空");
    }

    // ==================== 场景2：订单管理 ====================

    @Test
    @Order(2)
    @DisplayName("场景2: 订单创建 - 复杂业务逻辑")
    void testOrderCreate_ComplexBusinessLogic() {
        Entity orderEntity = createOrderEntity();
        String methodName = "createOrder";

        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("validateStock")
                        .description("验证库存")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("orders")
                        .method("createOrder")
                        .logic("检查库存充足")
                        .priority(10)
                        .build(),
                BusinessRule.builder()
                        .name("calculateTotal")
                        .description("计算总价")
                        .type(BusinessRuleType.CALCULATION)
                        .entity("orders")
                        .method("createOrder")
                        .logic("计算订单总金额")
                        .priority(8)
                        .build()
        );

        // 执行完整流程
        ComplexityAnalyzerTool.Response complexityResponse = complexityAnalyzerTool.apply(
                new ComplexityAnalyzerTool.Request(methodName, orderEntity, "创建订单验证库存计算总价")
        );

        TemplateGeneratorTool.Response templateResponse = templateGeneratorTool.apply(
                new TemplateGeneratorTool.Request(rules, orderEntity, methodName)
        );

        BestPracticeApplierTool.Response practiceResponse = bestPracticeApplierTool.apply(
                new BestPracticeApplierTool.Request(templateResponse.getGeneratedCode(), orderEntity, methodName)
        );

        // 生成完整Service类
        ServiceClassGeneratorTool.Response serviceClassResponse = serviceClassGeneratorTool.apply(
                ServiceClassGeneratorTool.Request.builder()
                        .businessLogicCode(practiceResponse.getEnhancedCode())
                        .entity(orderEntity)
                        .methodName(methodName)
                        .methodDescription("创建订单")
                        .build()
        );

        assertTrue(serviceClassResponse.isSuccess(), "Service类生成应成功");

        // 代码验证 + 自动修复
        String finalCode = validateAndAutoFix(
                serviceClassResponse.getCompleteCode(),
                orderEntity,
                methodName,
                "场景2-订单创建"
        );

        assertNotNull(finalCode, "最终代码不应为空");
    }

    // ==================== 场景3：商品目录 ====================

    @Test
    @Order(3)
    @DisplayName("场景3: 商品搜索 - 多条件筛选")
    void testProductSearch_MultiFilter() {
        Entity productEntity = createProductEntity();
        String methodName = "searchProducts";

        // Step 1: 复杂度分析
        ComplexityAnalyzerTool.Response complexityResponse = complexityAnalyzerTool.apply(
                new ComplexityAnalyzerTool.Request(methodName, productEntity, "商品搜索多条件筛选")
        );

        assertTrue(complexityResponse.isSuccess(), "复杂度分析应成功");

        // Step 2: 模板生成
        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("filterProducts")
                        .description("多条件筛选商品")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("products")
                        .method("searchProducts")
                        .logic("根据条件筛选商品")
                        .priority(8)
                        .build()
        );

        TemplateGeneratorTool.Response templateResponse = templateGeneratorTool.apply(
                new TemplateGeneratorTool.Request(rules, productEntity, methodName)
        );

        assertTrue(templateResponse.isSuccess(), "模板生成应成功");

        // Step 3: 最佳实践应用
        BestPracticeApplierTool.Response practiceResponse = bestPracticeApplierTool.apply(
                new BestPracticeApplierTool.Request(templateResponse.getGeneratedCode(), productEntity, methodName)
        );

        assertTrue(practiceResponse.isSuccess(), "最佳实践应用应成功");

        // Step 4: 生成完整Service类
        ServiceClassGeneratorTool.Response serviceClassResponse = serviceClassGeneratorTool.apply(
                ServiceClassGeneratorTool.Request.builder()
                        .businessLogicCode(practiceResponse.getEnhancedCode())
                        .entity(productEntity)
                        .methodName(methodName)
                        .methodDescription("商品搜索")
                        .build()
        );

        assertTrue(serviceClassResponse.isSuccess(), "Service类生成应成功");

        // Step 5: 代码验证 + 自动修复
        String finalCode = validateAndAutoFix(
                serviceClassResponse.getCompleteCode(),
                productEntity,
                methodName,
                "场景3-商品搜索"
        );

        assertNotNull(finalCode, "最终代码不应为空");
    }

    // ==================== 场景4：认证授权 ====================

    @Test
    @Order(4)
    @DisplayName("场景4: 用户登录 - Token生成")
    void testUserLogin_TokenGeneration() {
        Entity authEntity = createAuthEntity();
        String methodName = "login";

        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("validatePassword")
                        .description("验证密码")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("auth")
                        .method("login")
                        .logic("密码验证")
                        .priority(10)
                        .build()
        );

        TemplateGeneratorTool.Response templateResponse = templateGeneratorTool.apply(
                new TemplateGeneratorTool.Request(rules, authEntity, methodName)
        );

        BestPracticeApplierTool.Response practiceResponse = bestPracticeApplierTool.apply(
                new BestPracticeApplierTool.Request(templateResponse.getGeneratedCode(), authEntity, methodName)
        );

        // 生成完整Service类
        ServiceClassGeneratorTool.Response serviceClassResponse = serviceClassGeneratorTool.apply(
                ServiceClassGeneratorTool.Request.builder()
                        .businessLogicCode(practiceResponse.getEnhancedCode())
                        .entity(authEntity)
                        .methodName(methodName)
                        .methodDescription("用户登录")
                        .build()
        );

        assertTrue(serviceClassResponse.isSuccess(), "Service类生成应成功");

        // 代码验证 + 自动修复
        String finalCode = validateAndAutoFix(
                serviceClassResponse.getCompleteCode(),
                authEntity,
                methodName,
                "场景4-用户登录"
        );

        assertNotNull(finalCode, "最终代码不应为空");
    }

    // ==================== 场景5：支付处理 ====================

    @Test
    @Order(5)
    @DisplayName("场景5: 支付处理 - 复杂流程")
    void testPaymentCreate_ComplexWorkflow() {
        Entity paymentEntity = createPaymentEntity();
        String methodName = "processPayment";

        List<BusinessRule> rules = Arrays.asList(
                BusinessRule.builder()
                        .name("validateAmount")
                        .description("验证金额")
                        .type(BusinessRuleType.VALIDATION)
                        .entity("payments")
                        .method("processPayment")
                        .logic("金额验证")
                        .priority(10)
                        .build(),
                BusinessRule.builder()
                        .name("callGateway")
                        .description("调用支付网关")
                        .type(BusinessRuleType.WORKFLOW)
                        .entity("payments")
                        .method("processPayment")
                        .logic("调用第三方支付API")
                        .priority(8)
                        .build()
        );

        ComplexityAnalyzerTool.Response complexityResponse = complexityAnalyzerTool.apply(
                new ComplexityAnalyzerTool.Request(methodName, paymentEntity, "支付处理第三方API")
        );

        TemplateGeneratorTool.Response templateResponse = templateGeneratorTool.apply(
                new TemplateGeneratorTool.Request(rules, paymentEntity, methodName)
        );

        BestPracticeApplierTool.Response practiceResponse = bestPracticeApplierTool.apply(
                new BestPracticeApplierTool.Request(templateResponse.getGeneratedCode(), paymentEntity, methodName)
        );

        // 生成完整Service类
        ServiceClassGeneratorTool.Response serviceClassResponse = serviceClassGeneratorTool.apply(
                ServiceClassGeneratorTool.Request.builder()
                        .businessLogicCode(practiceResponse.getEnhancedCode())
                        .entity(paymentEntity)
                        .methodName(methodName)
                        .methodDescription("支付处理")
                        .build()
        );

        assertTrue(serviceClassResponse.isSuccess(), "Service类生成应成功");

        // 代码验证 + 自动修复
        String finalCode = validateAndAutoFix(
                serviceClassResponse.getCompleteCode(),
                paymentEntity,
                methodName,
                "场景5-支付处理"
        );

        assertNotNull(finalCode, "最终代码不应为空");
        assertTrue(complexityResponse.getComplexityScore() >= 30,
                "支付处理复杂度应较高");
    }

    // ==================== 实体创建辅助方法 ====================

    private Entity createUserEntity() {
        return Entity.builder()
                .name("users")
                .description("用户表")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).build(),
                        Field.builder().name("username").type(FieldType.VARCHAR).length(100).nullable(false).build(),
                        Field.builder().name("email").type(FieldType.VARCHAR).length(255).nullable(false).build(),
                        Field.builder().name("password").type(FieldType.VARCHAR).length(255).nullable(false).build(),
                        Field.builder().name("status").type(FieldType.INTEGER).defaultValue("1").build(),
                        Field.builder().name("created_at").type(FieldType.TIMESTAMPTZ).build()
                ))
                .build();
    }

    private Entity createOrderEntity() {
        return Entity.builder()
                .name("orders")
                .description("订单表")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).build(),
                        Field.builder().name("order_no").type(FieldType.VARCHAR).length(50).nullable(false).build(),
                        Field.builder().name("total_amount").type(FieldType.NUMERIC).precision(10).scale(2).build(),
                        Field.builder().name("status").type(FieldType.INTEGER).build()
                ))
                .build();
    }

    private Entity createProductEntity() {
        return Entity.builder()
                .name("products")
                .description("商品表")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).build(),
                        Field.builder().name("name").type(FieldType.VARCHAR).length(200).build(),
                        Field.builder().name("price").type(FieldType.NUMERIC).precision(10).scale(2).build(),
                        Field.builder().name("tags").type(FieldType.TEXT_ARRAY).build()
                ))
                .build();
    }

    private Entity createAuthEntity() {
        return Entity.builder()
                .name("auth")
                .description("认证表")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).build(),
                        Field.builder().name("user_id").type(FieldType.UUID).build(),
                        Field.builder().name("token").type(FieldType.TEXT).build(),
                        Field.builder().name("permissions").type(FieldType.JSONB).build()
                ))
                .build();
    }

    private Entity createPaymentEntity() {
        return Entity.builder()
                .name("payments")
                .description("支付表")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).build(),
                        Field.builder().name("payment_no").type(FieldType.VARCHAR).length(50).build(),
                        Field.builder().name("amount").type(FieldType.NUMERIC).precision(10).scale(2).build(),
                        Field.builder().name("status").type(FieldType.INTEGER).build(),
                        Field.builder().name("gateway_response").type(FieldType.JSONB).build()
                ))
                .build();
    }

    // ==================== 自动修复集成辅助方法 ====================

    /**
     * 验证代码并在需要时自动修复
     *
     * <p>集成流程：</p>
     * <ol>
     *   <li>首次验证：使用ValidationTool评分</li>
     *   <li>评分<70：触发AutoFixOrchestrator修复（最多3次迭代）</li>
     *   <li>二次验证：验证修复后的代码</li>
     *   <li>断言：最终评分必须≥80</li>
     * </ol>
     *
     * @param code 待验证的代码
     * @param entity 实体定义
     * @param methodName 方法名
     * @param sceneName 场景名称（用于日志）
     * @return 最终代码（原始代码或修复后的代码）
     */
    private String validateAndAutoFix(String code, Entity entity, String methodName, String sceneName) {
        // Step 1: 首次验证
        ValidationTool.Response initialValidation = validationTool.apply(
                new ValidationTool.Request(code, entity, methodName)
        );

        log.info("{} - 初始质量分:{}/100, 问题数:{}",
                sceneName, initialValidation.getQualityScore(), initialValidation.getIssues().size());

        String finalCode = code;
        ValidationTool.Response finalValidation = initialValidation;

        // Step 2: 如果质量分 < 70，触发自动修复
        if (initialValidation.getQualityScore() < 70) {
            log.warn("{} - 代码质量不达标（评分<70），启动AutoFixOrchestrator自动修复", sceneName);

            com.ingenio.backend.codegen.ai.autofix.AutoFixResult autoFixResult =
                    autoFixOrchestrator.attemptAutoFix(code, entity, methodName);

            if (autoFixResult.isSuccess()) {
                finalCode = autoFixResult.getFinalCode();
                log.info("{} - ✅ 自动修复成功！最终质量分:{}/100, 迭代次数:{}",
                        sceneName, autoFixResult.getFinalQualityScore(), autoFixResult.getIterations());

                // 打印修复历史
                autoFixResult.getFixHistory().forEach(history ->
                        log.debug("  迭代{}: 应用策略={}, 评分={}/100",
                                history.getIteration(), history.getAppliedStrategy(), history.getScoreAfterFix())
                );
            } else {
                log.error("{} - ❌ 自动修复失败: {}, 迭代次数:{}",
                        sceneName, autoFixResult.getFailureReason(), autoFixResult.getIterations());
            }

            // Step 3: 重新验证修复后的代码
            finalValidation = validationTool.apply(
                    new ValidationTool.Request(finalCode, entity, methodName)
            );

            log.info("{} - 修复后质量分:{}/100", sceneName, finalValidation.getQualityScore());
        }

        // Step 4: 断言最终质量分必须≥80
        assertTrue(finalValidation.getQualityScore() >= 80,
                sceneName + " - 代码质量分应≥80，实际=" + finalValidation.getQualityScore() +
                        "，问题=" + String.join(", ", finalValidation.getIssues()));

        return finalCode;
    }
}
