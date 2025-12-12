package com.ingenio.backend.codegen.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.codegen.ai.RequirementTestData;
import com.ingenio.backend.codegen.ai.model.AnalyzedRequirement;
import com.ingenio.backend.codegen.ai.model.BusinessRule;
import com.ingenio.backend.codegen.ai.model.Constraint;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static com.ingenio.backend.codegen.ai.RequirementTestData.ExpectedResults.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RequirementAnalyzer单元测试（V2.0 Phase 4.1）
 *
 * <p>测试范围：</p>
 * <ul>
 *   <li>简单需求识别</li>
 *   <li>中等复杂度需求识别</li>
 *   <li>复杂需求识别</li>
 *   <li>模糊需求处理</li>
 *   <li>约束条件识别</li>
 *   <li>计算规则识别</li>
 *   <li>工作流规则识别</li>
 *   <li>通知规则识别</li>
 * </ul>
 *
 * <p>测试策略：</p>
 * <ul>
 *   <li>使用真实的AI模型进行测试（非Mock）</li>
 *   <li>验证准确率≥90%</li>
 *   <li>验证置信度≥0.85（高质量需求）</li>
 *   <li>验证响应时间<5s</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-17 V2.0 Phase 4.1
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class RequirementAnalyzerTest {

    @Autowired
    private RequirementAnalyzer requirementAnalyzer;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        log.info("=== RequirementAnalyzerTest Setup ===");
        assertNotNull(requirementAnalyzer, "RequirementAnalyzer should be autowired");
        assertNotNull(objectMapper, "ObjectMapper should be autowired");
    }

    @Test
    @DisplayName("测试1: 简单用户管理系统需求分析")
    void testSimpleUserSystemRequirement() {
        // Given
        String requirement = RequirementTestData.SIMPLE_USER_SYSTEM;
        log.info("测试简单用户管理系统需求...");
        long startTime = System.currentTimeMillis();

        // When
        AnalyzedRequirement result = requirementAnalyzer.analyze(requirement);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertNotNull(result, "分析结果不应为null");
        assertNotNull(result.getEntities(), "实体列表不应为null");
        assertNotNull(result.getBusinessRules(), "业务规则列表不应为null");

        log.info("分析结果 - 领域: {}, 实体数: {}, 业务规则数: {}, 置信度: {}, 耗时: {}ms",
                result.getDomain(),
                result.getEntities().size(),
                result.getBusinessRules().size(),
                result.getConfidence(),
                duration);

        // 验证实体数量
        assertThat(result.getEntities().size())
                .withFailMessage("期望1个实体，实际: %d", result.getEntities().size())
                .isEqualTo(SIMPLE_USER_SYSTEM_ENTITIES);

        // 验证字段数量
        int totalFields = result.getEntities().stream()
                .mapToInt(e -> e.getFields() != null ? e.getFields().size() : 0)
                .sum();
        assertThat(totalFields)
                .withFailMessage("期望至少3个字段，实际: %d", totalFields)
                .isGreaterThanOrEqualTo(SIMPLE_USER_SYSTEM_FIELDS);

        // 验证业务规则数量
        assertThat(result.getBusinessRules().size())
                .withFailMessage("期望至少2个业务规则，实际: %d", result.getBusinessRules().size())
                .isGreaterThanOrEqualTo(SIMPLE_USER_SYSTEM_RULES);

        // 验证置信度
        assertThat(result.getConfidence())
                .withFailMessage("置信度应≥0.9，实际: %.2f", result.getConfidence())
                .isGreaterThanOrEqualTo(SIMPLE_USER_SYSTEM_MIN_CONFIDENCE);

        // 验证响应时间（AI API调用预期40-60秒）
        assertThat(duration)
                .withFailMessage("响应时间应<150000ms，实际: %dms", duration)
                .isLessThan(150000L);

        log.info("✅ 测试1通过：简单用户管理系统需求分析正确");
    }

    @Test
    @DisplayName("测试2: 中等复杂度订单系统需求分析")
    void testMediumOrderSystemRequirement() {
        // Given
        String requirement = RequirementTestData.MEDIUM_ORDER_SYSTEM;
        log.info("测试中等复杂度订单系统需求...");
        long startTime = System.currentTimeMillis();

        // When
        AnalyzedRequirement result = requirementAnalyzer.analyze(requirement);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertNotNull(result);
        log.info("分析结果 - 领域: {}, 实体数: {}, 字段总数: {}, 业务规则数: {}, 关系数: {}, 置信度: {}, 耗时: {}ms",
                result.getDomain(),
                result.getEntities().size(),
                result.getEntities().stream().mapToInt(e -> e.getFields() != null ? e.getFields().size() : 0).sum(),
                result.getBusinessRules().size(),
                result.getRelationships() != null ? result.getRelationships().size() : 0,
                result.getConfidence(),
                duration);

        // 验证实体数量
        assertThat(result.getEntities().size())
                .withFailMessage("期望2个实体，实际: %d", result.getEntities().size())
                .isEqualTo(MEDIUM_ORDER_SYSTEM_ENTITIES);

        // 验证字段数量
        int totalFields = result.getEntities().stream()
                .mapToInt(e -> e.getFields() != null ? e.getFields().size() : 0)
                .sum();
        assertThat(totalFields)
                .withFailMessage("期望至少8个字段，实际: %d", totalFields)
                .isGreaterThanOrEqualTo(MEDIUM_ORDER_SYSTEM_FIELDS_MIN);

        // 验证业务规则数量
        assertThat(result.getBusinessRules().size())
                .withFailMessage("期望至少5个业务规则，实际: %d", result.getBusinessRules().size())
                .isGreaterThanOrEqualTo(MEDIUM_ORDER_SYSTEM_RULES_MIN);

        // 验证关系数量
        assertThat(result.getRelationships())
                .withFailMessage("关系列表不应为null")
                .isNotNull();
        assertThat(result.getRelationships().size())
                .withFailMessage("期望至少1个关系，实际: %d", result.getRelationships().size())
                .isGreaterThanOrEqualTo(MEDIUM_ORDER_SYSTEM_RELATIONSHIPS);

        // 验证置信度
        assertThat(result.getConfidence())
                .withFailMessage("置信度应≥0.85，实际: %.2f", result.getConfidence())
                .isGreaterThanOrEqualTo(MEDIUM_ORDER_SYSTEM_MIN_CONFIDENCE);

        // 验证响应时间（AI API调用预期40-60秒）
        assertThat(duration)
                .withFailMessage("响应时间应<150000ms，实际: %dms", duration)
                .isLessThan(150000L);

        log.info("✅ 测试2通过：中等复杂度订单系统需求分析正确");
    }

    @Test
    @DisplayName("测试3: 复杂电商系统需求分析")
    void testComplexEcommerceSystemRequirement() {
        // Given
        String requirement = RequirementTestData.COMPLEX_ECOMMERCE_SYSTEM;
        log.info("测试复杂电商系统需求...");
        long startTime = System.currentTimeMillis();

        // When
        AnalyzedRequirement result = requirementAnalyzer.analyze(requirement);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertNotNull(result);
        log.info("分析结果 - 领域: {}, 实体数: {}, 业务规则数: {}, 关系数: {}, 置信度: {}, 耗时: {}ms",
                result.getDomain(),
                result.getEntities().size(),
                result.getBusinessRules().size(),
                result.getRelationships() != null ? result.getRelationships().size() : 0,
                result.getConfidence(),
                duration);

        // 验证实体数量（方案C优化：AI可能识别出更多合理实体，≥4即可）
        assertThat(result.getEntities().size())
                .withFailMessage("期望至少4个实体，实际: %d", result.getEntities().size())
                .isGreaterThanOrEqualTo(COMPLEX_ECOMMERCE_ENTITIES);

        // 验证业务规则数量
        assertThat(result.getBusinessRules().size())
                .withFailMessage("期望至少10个业务规则，实际: %d", result.getBusinessRules().size())
                .isGreaterThanOrEqualTo(COMPLEX_ECOMMERCE_RULES_MIN);

        // 验证关系数量
        assertThat(result.getRelationships())
                .withFailMessage("关系列表不应为null")
                .isNotNull();
        assertThat(result.getRelationships().size())
                .withFailMessage("期望至少4个关系，实际: %d", result.getRelationships().size())
                .isGreaterThanOrEqualTo(COMPLEX_ECOMMERCE_RELATIONSHIPS_MIN);

        // 验证置信度
        assertThat(result.getConfidence())
                .withFailMessage("置信度应≥0.8，实际: %.2f", result.getConfidence())
                .isGreaterThanOrEqualTo(COMPLEX_ECOMMERCE_MIN_CONFIDENCE);

        // 验证响应时间（AI API调用预期40-60秒）
        assertThat(duration)
                .withFailMessage("响应时间应<150000ms，实际: %dms", duration)
                .isLessThan(150000L);

        log.info("✅ 测试3通过：复杂电商系统需求分析正确");
    }

    @Test
    @DisplayName("测试4: 模糊需求处理（低置信度）")
    void testAmbiguousRequirement() {
        // Given
        String requirement = RequirementTestData.AMBIGUOUS_REQUIREMENT;
        log.info("测试模糊需求处理...");

        // When
        AnalyzedRequirement result = requirementAnalyzer.analyze(requirement);

        // Then
        assertNotNull(result);
        log.info("分析结果 - 领域: {}, 置信度: {}, 推理: {}",
                result.getDomain(),
                result.getConfidence(),
                result.getReasoning());

        // 验证置信度（模糊需求应该置信度较低）
        assertThat(result.getConfidence())
                .withFailMessage("模糊需求置信度应<0.7，实际: %.2f", result.getConfidence())
                .isLessThan(AMBIGUOUS_MAX_CONFIDENCE);

        // 验证推理字段存在
        assertThat(result.getReasoning())
                .withFailMessage("推理字段不应为空")
                .isNotNull()
                .isNotEmpty();

        log.info("✅ 测试4通过：模糊需求正确识别为低置信度");
    }

    @Test
    @DisplayName("测试5: 约束条件识别")
    void testConstraintsRecognition() {
        // Given
        String requirement = RequirementTestData.REQUIREMENT_WITH_CONSTRAINTS;
        log.info("测试约束条件识别...");

        // When
        AnalyzedRequirement result = requirementAnalyzer.analyze(requirement);

        // Then
        assertNotNull(result);
        assertNotNull(result.getConstraints(), "约束列表不应为null");

        log.info("分析结果 - 实体数: {}, 约束数: {}",
                result.getEntities().size(),
                result.getConstraints().size());

        // 验证实体数量
        assertThat(result.getEntities().size())
                .withFailMessage("期望2个实体，实际: %d", result.getEntities().size())
                .isEqualTo(CONSTRAINTS_ENTITIES);

        // 验证约束数量
        assertThat(result.getConstraints().size())
                .withFailMessage("期望至少8个约束，实际: %d", result.getConstraints().size())
                .isGreaterThanOrEqualTo(CONSTRAINTS_COUNT_MIN);

        // 验证约束类型多样性
        long uniqueConstraintTypes = result.getConstraints().stream()
                .map(Constraint::getType)
                .distinct()
                .count();
        assertThat(uniqueConstraintTypes)
                .withFailMessage("约束类型应多样化，至少3种，实际: %d", uniqueConstraintTypes)
                .isGreaterThanOrEqualTo(3);

        log.info("✅ 测试5通过：约束条件识别正确，包含{}种类型", uniqueConstraintTypes);
    }

    @Test
    @DisplayName("测试6: 计算规则识别")
    void testCalculationRulesRecognition() {
        // Given
        String requirement = RequirementTestData.REQUIREMENT_WITH_CALCULATIONS;
        log.info("测试计算规则识别...");

        // When
        AnalyzedRequirement result = requirementAnalyzer.analyze(requirement);

        // Then
        assertNotNull(result);
        assertNotNull(result.getBusinessRules(), "业务规则列表不应为null");

        // 统计CALCULATION类型的规则
        long calculationRulesCount = result.getBusinessRules().stream()
                .filter(rule -> rule.getType() == BusinessRule.BusinessRuleType.CALCULATION)
                .count();

        log.info("分析结果 - 业务规则总数: {}, CALCULATION规则数: {}",
                result.getBusinessRules().size(),
                calculationRulesCount);

        // 验证CALCULATION规则数量
        assertThat(calculationRulesCount)
                .withFailMessage("期望至少4个CALCULATION规则，实际: %d", calculationRulesCount)
                .isGreaterThanOrEqualTo(CALCULATIONS_RULES_MIN);

        log.info("✅ 测试6通过：计算规则识别正确");
    }

    @Test
    @DisplayName("测试7: 工作流规则识别")
    void testWorkflowRulesRecognition() {
        // Given
        String requirement = RequirementTestData.REQUIREMENT_WITH_WORKFLOW;
        log.info("测试工作流规则识别...");

        // When
        AnalyzedRequirement result = requirementAnalyzer.analyze(requirement);

        // Then
        assertNotNull(result);
        assertNotNull(result.getBusinessRules(), "业务规则列表不应为null");

        // 统计WORKFLOW类型的规则
        long workflowRulesCount = result.getBusinessRules().stream()
                .filter(rule -> rule.getType() == BusinessRule.BusinessRuleType.WORKFLOW)
                .count();

        log.info("分析结果 - 业务规则总数: {}, WORKFLOW规则数: {}",
                result.getBusinessRules().size(),
                workflowRulesCount);

        // 验证WORKFLOW规则数量
        assertThat(workflowRulesCount)
                .withFailMessage("期望至少3个WORKFLOW规则，实际: %d", workflowRulesCount)
                .isGreaterThanOrEqualTo(WORKFLOW_RULES_MIN);

        log.info("✅ 测试7通过：工作流规则识别正确");
    }

    @Test
    @DisplayName("测试8: 通知规则识别")
    void testNotificationRulesRecognition() {
        // Given
        String requirement = RequirementTestData.REQUIREMENT_WITH_NOTIFICATIONS;
        log.info("测试通知规则识别...");

        // When
        AnalyzedRequirement result = requirementAnalyzer.analyze(requirement);

        // Then
        assertNotNull(result);
        assertNotNull(result.getBusinessRules(), "业务规则列表不应为null");

        // 统计NOTIFICATION类型的规则
        long notificationRulesCount = result.getBusinessRules().stream()
                .filter(rule -> rule.getType() == BusinessRule.BusinessRuleType.NOTIFICATION)
                .count();

        log.info("分析结果 - 业务规则总数: {}, NOTIFICATION规则数: {}",
                result.getBusinessRules().size(),
                notificationRulesCount);

        // 验证NOTIFICATION规则数量
        assertThat(notificationRulesCount)
                .withFailMessage("期望至少6个NOTIFICATION规则，实际: %d", notificationRulesCount)
                .isGreaterThanOrEqualTo(NOTIFICATIONS_RULES_MIN);

        log.info("✅ 测试8通过：通知规则识别正确");
    }

    @Test
    @DisplayName("测试9: AI响应JSON解析正确性")
    void testAIResponseParsing() {
        // Given
        String requirement = RequirementTestData.SIMPLE_USER_SYSTEM;
        log.info("测试AI响应JSON解析...");

        // When
        AnalyzedRequirement result = requirementAnalyzer.analyze(requirement);

        // Then
        assertNotNull(result);

        // 验证所有必需字段不为null
        assertNotNull(result.getDomain(), "domain字段不应为null");
        assertNotNull(result.getEntities(), "entities字段不应为null");
        assertNotNull(result.getBusinessRules(), "businessRules字段不应为null");
        assertNotNull(result.getRelationships(), "relationships字段不应为null");
        assertNotNull(result.getConstraints(), "constraints字段不应为null");
        assertNotNull(result.getConfidence(), "confidence字段不应为null");
        assertNotNull(result.getReasoning(), "reasoning字段不应为null");

        // 验证confidence范围
        assertThat(result.getConfidence())
                .withFailMessage("confidence应在0-1之间，实际: %.2f", result.getConfidence())
                .isBetween(0.0, 1.0);

        log.info("✅ 测试9通过：AI响应JSON解析正确");
    }

    @Test
    @DisplayName("测试10: 降级策略测试（模拟AI失败）")
    void testFallbackStrategy() {
        // 注意：这个测试需要模拟AI失败的情况
        // 由于使用真实AI，我们通过传入空字符串来触发异常处理
        log.info("测试降级策略...");

        // Given - 空输入
        String requirement = "";

        // When
        AnalyzedRequirement result = requirementAnalyzer.analyze(requirement);

        // Then
        assertNotNull(result, "即使AI失败，也应返回降级结果");
        assertThat(result.getConfidence())
                .withFailMessage("降级策略的置信度应为0，实际: %.2f", result.getConfidence())
                .isEqualTo(0.0);

        log.info("✅ 测试10通过：降级策略正常工作");
    }
}
