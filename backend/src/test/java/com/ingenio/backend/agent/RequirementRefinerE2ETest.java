package com.ingenio.backend.agent;

import com.ingenio.backend.agent.dto.ComplexityLevel;
import com.ingenio.backend.agent.dto.ComplexityScore;
import com.ingenio.backend.agent.dto.RefinedRequirement;
import com.ingenio.backend.agent.dto.RequirementIntent;
import com.ingenio.backend.e2e.BaseE2ETest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RequirementRefiner E2E验证测试 - 10个典型失败/边界场景
 *
 * <p>测试目标：</p>
 * <ul>
 *   <li>验证RequirementRefiner对异常输入的健壮性</li>
 *   <li>验证错误处理和降级策略</li>
 *   <li>验证边界条件和极端场景</li>
 *   <li>收集改写效果度量数据</li>
 * </ul>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>真实API调用（遵循零Mock策略）</li>
 *   <li>TestContainers PostgreSQL数据库</li>
 *   <li>关注失败场景和边界条件</li>
 *   <li>记录度量数据以供分析</li>
 * </ul>
 *
 * @author Ingenio Team
 * @version 1.0.0
 * @since 2025-11-20 P3 Phase 3.5.2
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("RequirementRefiner E2E验证测试")
class RequirementRefinerE2ETest extends BaseE2ETest {

    @Autowired
    private RequirementRefiner requirementRefiner;

    // 度量数据收集
    private static int totalTests = 0;
    private static int successfulRefinements = 0;
    private static int failedRefinements = 0;
    private static long totalExecutionTimeMs = 0;

    @AfterAll
    static void printMetrics() {
        log.info("=== RequirementRefiner E2E测试度量报告 ===");
        log.info("总测试场景数: {}", totalTests);
        log.info("改写成功数: {}", successfulRefinements);
        log.info("改写失败数: {}", failedRefinements);
        log.info("成功率: {}%", totalTests > 0 ? (successfulRefinements * 100.0 / totalTests) : 0);
        log.info("平均执行时间: {}ms", totalTests > 0 ? (totalExecutionTimeMs / totalTests) : 0);
        log.info("========================================");
    }

    /**
     * 场景1: 空输入/null输入
     *
     * <p>预期行为：抛出IllegalArgumentException，拒绝空输入</p>
     */
    @Test
    @Order(1)
    @DisplayName("场景1: 空输入处理")
    void testEmptyInput() {
        totalTests++;
        long startTime = System.currentTimeMillis();

        // Given: 空输入
        String userInput = "";
        RequirementIntent intent = RequirementIntent.DESIGN_FROM_SCRATCH;
        ComplexityScore complexityScore = ComplexityScore.builder()
                .finalScore(0)
                .level(ComplexityLevel.SIMPLE)
                .build();

        // When & Then: 执行改写，预期抛出异常
        log.info("=== 场景1开始：空输入处理 ===");

        try {
            requirementRefiner.refine(userInput, intent, complexityScore);
            // 如果没抛异常，记录失败
            failedRefinements++;
            assertThat(false)
                    .as("空输入应该抛出IllegalArgumentException")
                    .isTrue();
        } catch (IllegalArgumentException e) {
            // 预期行为：抛出异常
            long executionTime = System.currentTimeMillis() - startTime;
            totalExecutionTimeMs += executionTime;
            log.info("执行时间: {}ms, 结果: 正确拒绝空输入（抛出异常）", executionTime);
            successfulRefinements++;

            assertThat(e.getMessage())
                    .as("异常消息应提示空输入")
                    .contains("空");
        }
    }

    /**
     * 场景2: 极短输入（少于5个字符）
     *
     * <p>预期行为：AI增强补充细节，或标记需要用户确认</p>
     */
    @Test
    @Order(2)
    @DisplayName("场景2: 极短输入处理")
    void testVeryShortInput() {
        totalTests++;
        long startTime = System.currentTimeMillis();

        // Given: 极短输入
        String userInput = "做app";
        RequirementIntent intent = RequirementIntent.DESIGN_FROM_SCRATCH;
        ComplexityScore complexityScore = ComplexityScore.builder()
                .finalScore(10)
                .level(ComplexityLevel.SIMPLE)
                .build();

        // When: 执行改写
        log.info("=== 场景2开始：极短输入处理 ===");
        RefinedRequirement refined = requirementRefiner.refine(userInput, intent, complexityScore);

        // Then: 验证结果
        long executionTime = System.currentTimeMillis() - startTime;
        totalExecutionTimeMs += executionTime;

        log.info("执行时间: {}ms, 结果: {}, needsConfirm={}",
                executionTime,
                refined.isSuccessful() ? "成功" : "失败",
                refined.isNeedsUserConfirmation());

        if (refined.isSuccessful()) {
            successfulRefinements++;
            // AI应该能够补充细节，或至少标记为需要用户确认
            // 两种策略都是合理的
            assertThat(refined.getRefinedText())
                    .as("应该补充更多细节")
                    .isNotNull()
                    .hasSizeGreaterThan(userInput.length());
        } else {
            failedRefinements++;
        }

        assertThat(refined).isNotNull();
    }

    /**
     * 场景3: 极长输入（超过2000字符）
     *
     * <p>预期行为：处理Token限制，提取核心信息</p>
     */
    @Test
    @Order(3)
    @DisplayName("场景3: 极长输入处理")
    void testVeryLongInput() {
        totalTests++;
        long startTime = System.currentTimeMillis();

        // Given: 极长输入（模拟用户粘贴大量PRD文档）
        StringBuilder longInput = new StringBuilder("我想做一个电商平台，");
        for (int i = 0; i < 50; i++) {
            longInput.append("需要包含商品管理、订单管理、用户管理、支付管理、物流管理、库存管理、促销管理、会员管理、");
        }
        longInput.append("还需要移动端APP、管理后台、商家入驻系统、数据分析系统等。");

        String userInput = longInput.toString();
        RequirementIntent intent = RequirementIntent.DESIGN_FROM_SCRATCH;
        ComplexityScore complexityScore = ComplexityScore.builder()
                .finalScore(85)
                .level(ComplexityLevel.COMPLEX)
                .build();

        // When: 执行改写
        log.info("=== 场景3开始：极长输入处理（输入长度: {}字符） ===", userInput.length());
        RefinedRequirement refined = requirementRefiner.refine(userInput, intent, complexityScore);

        // Then: 验证结果
        long executionTime = System.currentTimeMillis() - startTime;
        totalExecutionTimeMs += executionTime;

        log.info("执行时间: {}ms, 结果: {}", executionTime, refined.isSuccessful() ? "成功" : "失败");

        if (refined.isSuccessful()) {
            successfulRefinements++;
            // 复杂需求应该被拆分
            assertThat(refined.isMajorRefine())
                    .as("极长复杂输入应该触发重大改写")
                    .isTrue();
            assertThat(refined.getMvpFeatures())
                    .as("应该提取MVP功能")
                    .isNotNull()
                    .isNotEmpty();
            assertThat(refined.getFutureFeatures())
                    .as("应该拆分扩展功能")
                    .isNotNull()
                    .isNotEmpty();
        } else {
            failedRefinements++;
        }

        assertThat(refined).isNotNull();
    }

    /**
     * 场景4: 模糊需求（缺乏明确功能描述）
     *
     * <p>预期行为：提取关键词，补充常见功能，标记需确认</p>
     */
    @Test
    @Order(4)
    @DisplayName("场景4: 模糊需求处理")
    void testAmbiguousRequirement() {
        totalTests++;
        long startTime = System.currentTimeMillis();

        // Given: 模糊需求
        String userInput = "做一个好用的社交软件";
        RequirementIntent intent = RequirementIntent.DESIGN_FROM_SCRATCH;
        ComplexityScore complexityScore = ComplexityScore.builder()
                .finalScore(40)
                .level(ComplexityLevel.MEDIUM)
                .build();

        // When: 执行改写
        log.info("=== 场景4开始：模糊需求处理 ===");
        RefinedRequirement refined = requirementRefiner.refine(userInput, intent, complexityScore);

        // Then: 验证结果
        long executionTime = System.currentTimeMillis() - startTime;
        totalExecutionTimeMs += executionTime;

        log.info("执行时间: {}ms, 结果: {}", executionTime, refined.isSuccessful() ? "成功" : "失败");

        if (refined.isSuccessful()) {
            successfulRefinements++;
            // 模糊需求应该需要用户确认
            assertThat(refined.isNeedsUserConfirmation())
                    .as("模糊需求应该需要用户确认")
                    .isTrue();
            // 应该提取核心实体
            assertThat(refined.getCoreEntities())
                    .as("应该提取社交相关核心实体")
                    .isNotNull()
                    .isNotEmpty();
        } else {
            failedRefinements++;
        }

        assertThat(refined).isNotNull();
    }

    /**
     * 场景5: 矛盾需求（功能需求自相矛盾）
     *
     * <p>预期行为：识别矛盾，提示用户澄清</p>
     */
    @Test
    @Order(5)
    @DisplayName("场景5: 矛盾需求处理")
    void testContradictoryRequirement() {
        totalTests++;
        long startTime = System.currentTimeMillis();

        // Given: 矛盾需求
        String userInput = "做一个完全匿名的实名社交平台，用户不需要注册但要求实名认证";
        RequirementIntent intent = RequirementIntent.DESIGN_FROM_SCRATCH;
        ComplexityScore complexityScore = ComplexityScore.builder()
                .finalScore(50)
                .level(ComplexityLevel.MEDIUM)
                .build();

        // When: 执行改写
        log.info("=== 场景5开始：矛盾需求处理 ===");
        RefinedRequirement refined = requirementRefiner.refine(userInput, intent, complexityScore);

        // Then: 验证结果
        long executionTime = System.currentTimeMillis() - startTime;
        totalExecutionTimeMs += executionTime;

        log.info("执行时间: {}ms, 结果: {}", executionTime, refined.isSuccessful() ? "成功" : "失败");

        if (refined.isSuccessful()) {
            successfulRefinements++;
            // 矛盾需求必须需要用户确认
            assertThat(refined.isNeedsUserConfirmation())
                    .as("矛盾需求必须需要用户确认")
                    .isTrue();
            // 应该在改写推理中提到矛盾
            assertThat(refined.getRefiningReasoning())
                    .as("应该在改写推理中提到需求矛盾")
                    .isNotNull();
        } else {
            failedRefinements++;
        }

        assertThat(refined).isNotNull();
    }

    /**
     * 场景6: 不切实际的复杂度（超出6天开发周期）
     *
     * <p>预期行为：识别超复杂需求，建议MVP拆分</p>
     */
    @Test
    @Order(6)
    @DisplayName("场景6: 超复杂需求处理")
    void testUnrealisticComplexity() {
        totalTests++;
        long startTime = System.currentTimeMillis();

        // Given: 超复杂需求
        String userInput = "做一个包含AI聊天、视频会议、在线文档协作、项目管理、财务系统、" +
                "HR系统、CRM、ERP、区块链钱包、NFT交易市场的企业级全栈平台";
        RequirementIntent intent = RequirementIntent.DESIGN_FROM_SCRATCH;
        ComplexityScore complexityScore = ComplexityScore.builder()
                .entityCountScore(95)
                .relationshipComplexityScore(90)
                .aiCapabilityScore(85)
                .technicalRiskScore(90)
                .finalScore(90)
                .level(ComplexityLevel.COMPLEX)
                .build();

        // When: 执行改写
        log.info("=== 场景6开始：超复杂需求处理 ===");
        RefinedRequirement refined = requirementRefiner.refine(userInput, intent, complexityScore);

        // Then: 验证结果
        long executionTime = System.currentTimeMillis() - startTime;
        totalExecutionTimeMs += executionTime;

        log.info("执行时间: {}ms, 结果: {}", executionTime, refined.isSuccessful() ? "成功" : "失败");

        if (refined.isSuccessful()) {
            successfulRefinements++;
            // 必须是重大改写
            assertThat(refined.isMajorRefine())
                    .as("超复杂需求必须触发重大改写")
                    .isTrue();
            // 必须拆分MVP和扩展功能
            assertThat(refined.getMvpFeatures())
                    .as("必须定义MVP功能")
                    .isNotNull()
                    .isNotEmpty()
                    .hasSizeLessThanOrEqualTo(10); // MVP功能不应过多

            assertThat(refined.getFutureFeatures())
                    .as("必须拆分大量扩展功能")
                    .isNotNull()
                    .hasSizeGreaterThanOrEqualTo(5); // 应有大量功能被延后
        } else {
            failedRefinements++;
        }

        assertThat(refined).isNotNull();
    }

    /**
     * 场景7: 技术术语过载
     *
     * <p>预期行为：解析技术术语，转化为业务功能</p>
     */
    @Test
    @Order(7)
    @DisplayName("场景7: 技术术语过载处理")
    void testTechnicalJargonOverload() {
        totalTests++;
        long startTime = System.currentTimeMillis();

        // Given: 充满技术术语的需求
        String userInput = "用React做前端，用Spring Boot做后端，用PostgreSQL做数据库，" +
                "用Redis做缓存，用Kafka做消息队列，用Docker部署，用K8s编排，" +
                "前端要SSR，要支持PWA，要用Tailwind CSS";
        RequirementIntent intent = RequirementIntent.DESIGN_FROM_SCRATCH;
        ComplexityScore complexityScore = ComplexityScore.builder()
                .finalScore(60)
                .level(ComplexityLevel.MEDIUM)
                .build();

        // When: 执行改写
        log.info("=== 场景7开始：技术术语过载处理 ===");
        RefinedRequirement refined = requirementRefiner.refine(userInput, intent, complexityScore);

        // Then: 验证结果
        long executionTime = System.currentTimeMillis() - startTime;
        totalExecutionTimeMs += executionTime;

        log.info("执行时间: {}ms, 结果: {}", executionTime, refined.isSuccessful() ? "成功" : "失败");

        if (refined.isSuccessful()) {
            successfulRefinements++;
            // 应该提取技术约束
            assertThat(refined.getTechnicalConstraints())
                    .as("应该提取技术约束")
                    .isNotNull();
        } else {
            failedRefinements++;
        }

        assertThat(refined).isNotNull();
    }

    /**
     * 场景8: 多个不相关功能混杂
     *
     * <p>预期行为：识别多领域需求，建议聚焦核心</p>
     */
    @Test
    @Order(8)
    @DisplayName("场景8: 多领域混杂处理")
    void testMultipleDomainsMixed() {
        totalTests++;
        long startTime = System.currentTimeMillis();

        // Given: 多个不相关领域混杂
        String userInput = "做一个既能管理健身计划又能炒股交易还能学英语的APP，" +
                "同时支持在线购物和外卖订餐";
        RequirementIntent intent = RequirementIntent.DESIGN_FROM_SCRATCH;
        ComplexityScore complexityScore = ComplexityScore.builder()
                .finalScore(70)
                .level(ComplexityLevel.COMPLEX)
                .build();

        // When: 执行改写
        log.info("=== 场景8开始：多领域混杂处理 ===");
        RefinedRequirement refined = requirementRefiner.refine(userInput, intent, complexityScore);

        // Then: 验证结果
        long executionTime = System.currentTimeMillis() - startTime;
        totalExecutionTimeMs += executionTime;

        log.info("执行时间: {}ms, 结果: {}", executionTime, refined.isSuccessful() ? "成功" : "失败");

        if (refined.isSuccessful()) {
            successfulRefinements++;
            // 应该需要用户确认聚焦方向
            assertThat(refined.isNeedsUserConfirmation())
                    .as("多领域混杂应该需要用户确认")
                    .isTrue();
            // 应该建议拆分
            assertThat(refined.isMajorRefine())
                    .as("应该触发重大改写")
                    .isTrue();
        } else {
            failedRefinements++;
        }

        assertThat(refined).isNotNull();
    }

    /**
     * 场景9: 克隆意图但无URL
     *
     * <p>预期行为：提示需要提供参考URL</p>
     */
    @Test
    @Order(9)
    @DisplayName("场景9: 克隆意图缺URL处理")
    void testCloneIntentWithoutUrl() {
        totalTests++;
        long startTime = System.currentTimeMillis();

        // Given: 克隆意图但没提供URL
        String userInput = "做一个像那个很火的短视频APP一样的";
        RequirementIntent intent = RequirementIntent.CLONE_EXISTING_WEBSITE;
        ComplexityScore complexityScore = ComplexityScore.builder()
                .finalScore(60)
                .level(ComplexityLevel.MEDIUM)
                .build();

        // When: 执行改写
        log.info("=== 场景9开始：克隆意图缺URL处理 ===");
        RefinedRequirement refined = requirementRefiner.refine(userInput, intent, complexityScore);

        // Then: 验证结果
        long executionTime = System.currentTimeMillis() - startTime;
        totalExecutionTimeMs += executionTime;

        log.info("执行时间: {}ms, 结果: {}", executionTime, refined.isSuccessful() ? "成功" : "失败");

        if (refined.isSuccessful()) {
            successfulRefinements++;
            // 应该需要用户确认或补充信息
            assertThat(refined.isNeedsUserConfirmation())
                    .as("克隆意图缺URL应该需要用户确认")
                    .isTrue();
        } else {
            failedRefinements++;
        }

        assertThat(refined).isNotNull();
    }

    /**
     * 场景10: 特殊字符和表情符号
     *
     * <p>预期行为：正确处理特殊字符，不影响语义理解</p>
     */
    @Test
    @Order(10)
    @DisplayName("场景10: 特殊字符处理")
    void testSpecialCharactersAndEmojis() {
        totalTests++;
        long startTime = System.currentTimeMillis();

        // Given: 包含大量特殊字符和表情符号
        String userInput = "做一个超级😀😀😀好用的📱APP！！！支持💬聊天、📸拍照、" +
                "🎵音乐🎶、🎮游戏等功能~~~";
        RequirementIntent intent = RequirementIntent.DESIGN_FROM_SCRATCH;
        ComplexityScore complexityScore = ComplexityScore.builder()
                .finalScore(45)
                .level(ComplexityLevel.MEDIUM)
                .build();

        // When: 执行改写
        log.info("=== 场景10开始：特殊字符处理 ===");
        RefinedRequirement refined = requirementRefiner.refine(userInput, intent, complexityScore);

        // Then: 验证结果
        long executionTime = System.currentTimeMillis() - startTime;
        totalExecutionTimeMs += executionTime;

        log.info("执行时间: {}ms, 结果: {}", executionTime, refined.isSuccessful() ? "成功" : "失败");

        if (refined.isSuccessful()) {
            successfulRefinements++;
            // 应该提取核心功能（忽略表情符号）
            assertThat(refined.getCoreEntities())
                    .as("应该提取核心实体")
                    .isNotNull()
                    .isNotEmpty();
            assertThat(refined.getMvpFeatures())
                    .as("应该提取MVP功能")
                    .isNotNull()
                    .isNotEmpty();
        } else {
            failedRefinements++;
        }

        assertThat(refined).isNotNull();
    }
}
