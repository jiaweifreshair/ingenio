package com.ingenio.backend.agent;

import com.ingenio.backend.agent.dto.ComplexityLevel;
import com.ingenio.backend.agent.dto.IntentClassificationResult;
import com.ingenio.backend.agent.dto.RefinedRequirement;
import com.ingenio.backend.agent.dto.RequirementIntent;
import com.ingenio.backend.e2e.BaseE2ETest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P3 Pipeline简化集成测试 - 验证核心流程
 *
 * <p>测试目标：</p>
 * <ul>
 *   <li>验证IntentClassifier → RequirementRefiner管道能够正常执行</li>
 *   <li>验证不同意图类型的基本流转</li>
 *   <li>验证返回的数据结构完整性</li>
 * </ul>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>仅测试核心流程，不深入验证每个字段</li>
 *   <li>Mock AI API调用（避免账单问题和网络依赖）</li>
 *   <li>使用TestContainers PostgreSQL数据库</li>
 * </ul>
 *
 * @author Ingenio Team
 * @version 1.0.1
 * @since 2025-11-20 P3 Phase 3.5.1
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("P3 Pipeline简化集成测试")
@SpringBootTest(properties = {
    "spring.ai.openai.api-key=sk-UJEaOXPIxjRVEOQZpqlIj0MaOKGUaC5AClk6TrzuOuBwaj6f",
    "spring.ai.openai.base-url=https://www.uniaix.com/v1",
    "spring.ai.openai.chat.enabled=true",
    "spring.ai.openai.embedding.enabled=false"
})
class P3PipelineSimpleIntegrationTest extends BaseE2ETest {

    @Autowired
    private IntentClassifier intentClassifier;

    @Autowired
    private RequirementRefiner requirementRefiner;

    @MockBean
    private ChatModel chatModel;

    /**
     * 场景1: 简单需求 - Todo应用
     *
     * <p>验证SIMPLE复杂度需求的基本流转</p>
     */
    @Test
    @Order(1)
    @DisplayName("场景1: 简单需求 - Todo应用")
    void testSimpleRequirement_TodoApp() {
        // Mock AI Responses
        mockChatResponse(
            // 1. Intent Response
            """
            {
              "intent": "design",
              "confidence": 0.95,
              "reasoning": "简单的Todo应用",
              "referenceUrls": [],
              "extractedKeywords": ["Todo", "待办事项"]
            }
            """,
            // 2. Complexity Response
            """
            {
              "entityCountScore": 20,
              "relationshipComplexityScore": 20,
              "aiCapabilityScore": 10,
              "technicalRiskScore": 10,
              "finalScore": 18,
              "level": "SIMPLE",
              "reasoning": "基础CRUD应用",
              "extractedEntities": ["任务", "用户"],
              "keyTechnologies": ["Spring Boot"],
              "suggestedArchitecture": "单体应用",
              "suggestedDevelopmentStrategy": "快速开发"
            }
            """,
            // 3. Refinement Response
            """
            {
              "refinedText": "创建一个待办事项管理应用，包含任务创建、删除、标记完成功能。",
              "coreEntities": ["任务", "用户"],
              "mvpFeatures": ["任务列表", "添加任务", "删除任务"],
              "futureFeatures": ["多端同步"],
              "technicalConstraints": ["无"],
              "entityRelationships": ["用户-任务:一对多"],
              "refiningReasoning": "补充了基本功能细节",
              "needsUserConfirmation": false,
              "refineType": "DETAIL_ENHANCEMENT"
            }
            """
        );

        // Given: 简单的Todo应用需求
        String userInput = "我想做一个Todo待办事项管理app";

        // When: 执行意图分类
        log.info("=== 场景1开始：简单需求 - Todo应用 ===");
        IntentClassificationResult classifyResult = intentClassifier.classifyIntent(userInput);

        // Then: 验证意图识别基本结果
        log.info("意图识别结果: intent={}, level={}",
            classifyResult.getIntent(),
            classifyResult.getComplexityScore().getLevel());

        assertThat(classifyResult).isNotNull();
        assertThat(classifyResult.getIntent()).isNotNull();
        assertThat(classifyResult.getComplexityScore()).isNotNull();
        assertThat(classifyResult.getComplexityScore().getLevel())
            .as("Todo应用应为简单或中等复杂度")
            .isIn(ComplexityLevel.SIMPLE, ComplexityLevel.MEDIUM);

        // When: 执行需求改写
        RefinedRequirement refined = requirementRefiner.refine(
            userInput,
            classifyResult.getIntent(),
            classifyResult.getComplexityScore()
        );

        // Then: 验证改写结果基本结构
        log.info("改写结果: type={}, hasText={}, entities={}, mvpFeatures={}",
            refined.getRefineType(),
            refined.getRefinedText() != null,
            refined.getCoreEntities() != null ? refined.getCoreEntities().size() : 0,
            refined.getMvpFeatures() != null ? refined.getMvpFeatures().size() : 0);

        assertThat(refined).isNotNull();
        assertThat(refined.isSuccessful())
            .as("改写应成功")
            .isTrue();
        assertThat(refined.getRefineType())
            .as("应有改写类型")
            .isNotNull();
    }

    /**
     * 场景2: 克隆需求 - 仿小红书
     *
     * <p>验证CLONE意图的基本流转</p>
     */
    @Test
    @Order(2)
    @DisplayName("场景2: 克隆需求 - 仿小红书")
    void testCloneRequirement_Xiaohongshu() {
        // Mock AI Responses
        mockChatResponse(
            // 1. Intent Response
            """
            {
              "intent": "clone",
              "confidence": 0.92,
              "reasoning": "仿照小红书",
              "referenceUrls": ["小红书"],
              "extractedKeywords": ["小红书"]
            }
            """,
            // 2. Complexity Response
            """
            {
              "entityCountScore": 60,
              "relationshipComplexityScore": 60,
              "aiCapabilityScore": 40,
              "technicalRiskScore": 30,
              "finalScore": 50,
              "level": "MEDIUM",
              "reasoning": "社交应用有一定的复杂度",
              "extractedEntities": ["帖子", "用户", "评论"],
              "keyTechnologies": ["图片存储", "推荐"],
              "suggestedArchitecture": "前后端分离",
              "suggestedDevelopmentStrategy": "MVP先行"
            }
            """,
            // 3. Refinement Response
            """
            {
              "refinedText": "仿照小红书的核心功能，实现图片分享社区。",
              "coreEntities": ["帖子", "用户", "评论"],
              "mvpFeatures": ["发布帖子", "浏览瀑布流", "点赞评论"],
              "futureFeatures": ["直播", "商城"],
              "technicalConstraints": ["图片需审核"],
              "entityRelationships": ["用户-帖子:一对多"],
              "refiningReasoning": "聚焦核心社交功能",
              "needsUserConfirmation": false,
              "refineType": "BOUNDARY_CLARIFICATION"
            }
            """
        );

        // Given: 克隆小红书的需求
        String userInput = "做一个像小红书一样的app";

        // When: 执行意图分类
        log.info("=== 场景2开始：克隆需求 - 仿小红书 ===");
        IntentClassificationResult classifyResult = intentClassifier.classifyIntent(userInput);

        // Then: 验证意图识别
        log.info("意图识别结果: intent={}, level={}",
            classifyResult.getIntent(),
            classifyResult.getComplexityScore().getLevel());

        assertThat(classifyResult.getIntent())
            .as("明确提到'像小红书一样'应识别为克隆或混合意图")
            .isIn(RequirementIntent.CLONE_EXISTING_WEBSITE, RequirementIntent.HYBRID_CLONE_AND_CUSTOMIZE);

        // When: 执行需求改写
        RefinedRequirement refined = requirementRefiner.refine(
            userInput,
            classifyResult.getIntent(),
            classifyResult.getComplexityScore()
        );

        // Then: 验证改写结果
        log.info("改写结果: type={}, needsConfirm={}",
            refined.getRefineType(),
            refined.isNeedsUserConfirmation());

        assertThat(refined.isSuccessful()).isTrue();
        assertThat(refined.getCoreEntities())
            .as("应提取核心实体")
            .isNotNull()
            .isNotEmpty();
        assertThat(refined.getMvpFeatures())
            .as("应定义MVP功能")
            .isNotNull()
            .isNotEmpty();
    }

    /**
     * 场景3: 复杂需求 - 企业ERP
     *
     * <p>验证COMPLEX复杂度需求的基本流转</p>
     */
    @Test
    @Order(3)
    @DisplayName("场景3: 复杂需求 - 企业ERP系统")
    void testComplexRequirement_EnterpriseERP() {
        // Mock AI Responses
        mockChatResponse(
            // 1. Intent Response
            """
            {
              "intent": "design",
              "confidence": 0.90,
              "reasoning": "复杂企业系统",
              "referenceUrls": [],
              "extractedKeywords": ["ERP", "进销存"]
            }
            """,
            // 2. Complexity Response
            """
            {
              "entityCountScore": 90,
              "relationshipComplexityScore": 85,
              "aiCapabilityScore": 20,
              "technicalRiskScore": 60,
              "finalScore": 75,
              "level": "COMPLEX",
              "reasoning": "多模块集成，实体众多",
              "extractedEntities": ["订单", "库存", "员工", "财务凭证"],
              "keyTechnologies": ["工作流", "报表"],
              "suggestedArchitecture": "微服务",
              "suggestedDevelopmentStrategy": "分期开发"
            }
            """,
            // 3. Refinement Response
            """
            {
              "refinedText": "企业级ERP系统，包含进销存核心流程。",
              "coreEntities": ["订单", "库存", "员工"],
              "mvpFeatures": ["采购管理", "销售管理", "库存查询"],
              "futureFeatures": ["财务核算", "人力资源"],
              "technicalConstraints": ["数据一致性要求高"],
              "entityRelationships": ["复杂关联"],
              "refiningReasoning": "需求过于庞大，建议分期建设",
              "needsUserConfirmation": true,
              "refineType": "PHASED_DEVELOPMENT"
            }
            """
        );

        // Given: 复杂的企业ERP需求
        String userInput = "做一个企业级ERP系统，包括进销存、财务、人力资源、CRM等模块";

        // When: 执行意图分类
        log.info("=== 场景3开始：复杂需求 - 企业ERP系统 ===");
        IntentClassificationResult classifyResult = intentClassifier.classifyIntent(userInput);

        // Then: 验证复杂度评估
        log.info("意图识别结果: intent={}, level={}, finalScore={}",
            classifyResult.getIntent(),
            classifyResult.getComplexityScore().getLevel(),
            classifyResult.getComplexityScore().getFinalScore());

        assertThat(classifyResult.getComplexityScore().getLevel())
            .as("ERP系统应为高复杂度")
            .isIn(ComplexityLevel.MEDIUM, ComplexityLevel.COMPLEX);

        // When: 执行需求改写
        RefinedRequirement refined = requirementRefiner.refine(
            userInput,
            classifyResult.getIntent(),
            classifyResult.getComplexityScore()
        );

        // Then: 验证改写结果
        log.info("改写结果: type={}, isMajor={}, needsConfirm={}",
            refined.getRefineType(),
            refined.isMajorRefine(),
            refined.isNeedsUserConfirmation());

        assertThat(refined.isSuccessful()).isTrue();
        assertThat(refined.isNeedsUserConfirmation())
            .as("复杂需求改写应需要用户确认")
            .isTrue();
        assertThat(refined.isMajorRefine())
            .as("应为重大改写")
            .isTrue();
        assertThat(refined.getFutureFeatures())
            .as("复杂需求应拆分扩展功能")
            .isNotNull();
    }

    /**
     * 场景4: ComplexityScore数据完整性验证
     *
     * <p>验证复杂度评分的各个维度都有有效值</p>
     */
    @Test
    @Order(4)
    @DisplayName("场景4: ComplexityScore数据完整性验证")
    void testComplexityScoreDataIntegrity() {
        // Mock AI Responses
        mockChatResponse(
            // 1. Intent Response
            """
            {
              "intent": "design",
              "confidence": 0.88,
              "reasoning": "在线教育平台",
              "referenceUrls": [],
              "extractedKeywords": ["在线教育"]
            }
            """,
            // 2. Complexity Response
            """
            {
              "entityCountScore": 50,
              "relationshipComplexityScore": 40,
              "aiCapabilityScore": 10,
              "technicalRiskScore": 20,
              "finalScore": 35,
              "level": "SIMPLE",
              "reasoning": "标准平台",
              "extractedEntities": ["课程", "学生"],
              "keyTechnologies": ["视频播放"],
              "suggestedArchitecture": "单体",
              "suggestedDevelopmentStrategy": "敏捷开发"
            }
            """
        );

        // Given: 任意需求
        String userInput = "做一个在线教育平台";

        // When: 执行意图分类
        log.info("=== 场景4开始：ComplexityScore数据完整性验证 ===");
        IntentClassificationResult classifyResult = intentClassifier.classifyIntent(userInput);

        // Then: 验证ComplexityScore各维度数据
        var complexityScore = classifyResult.getComplexityScore();

        log.info("ComplexityScore: entity={}, relationship={}, ai={}, tech={}, final={}",
            complexityScore.getEntityCountScore(),
            complexityScore.getRelationshipComplexityScore(),
            complexityScore.getAiCapabilityScore(),
            complexityScore.getTechnicalRiskScore(),
            complexityScore.getFinalScore());

        assertThat(complexityScore.isSuccessful())
            .as("ComplexityScore应评估成功")
            .isTrue();

        // 验证四维度评分有效性
        assertThat(complexityScore.getEntityCountScore())
            .as("实体数量评分应在0-100范围内")
            .isBetween(0, 100);
        assertThat(complexityScore.getRelationshipComplexityScore())
            .as("关系复杂度评分应在0-100范围内")
            .isBetween(0, 100);
        assertThat(complexityScore.getAiCapabilityScore())
            .as("AI能力评分应在0-100范围内")
            .isBetween(0, 100);
        assertThat(complexityScore.getTechnicalRiskScore())
            .as("技术风险评分应在0-100范围内")
            .isBetween(0, 100);

        // 验证综合评分
        assertThat(complexityScore.getFinalScore())
            .as("综合评分应在0-100范围内")
            .isBetween(0, 100);

        // 验证复杂度等级映射正确
        assertThat(complexityScore.getLevel()).isNotNull();

        log.info("ComplexityScore数据完整性验证通过");
    }

    /**
     * 辅助方法：Mock ChatModel的响应序列
     *
     * @param responses JSON响应字符串数组
     */
    private void mockChatResponse(String... responses) {
        ChatResponse[] chatResponses = new ChatResponse[responses.length];
        
        // 1. 预先创建所有的Mock对象
        for (int i = 0; i < responses.length; i++) {
            String response = responses[i];
            ChatResponse mockResponse = mock(ChatResponse.class);
            Generation mockGeneration = mock(Generation.class);
            AssistantMessage mockMessage = new AssistantMessage(response);

            when(mockGeneration.getOutput()).thenReturn(mockMessage);
            when(mockResponse.getResult()).thenReturn(mockGeneration);
            chatResponses[i] = mockResponse;
        }

        // 2. 一次性配置chatModel的行为
        if (chatResponses.length > 0) {
            if (chatResponses.length == 1) {
                when(chatModel.call(any(Prompt.class))).thenReturn(chatResponses[0]);
            } else {
                ChatResponse first = chatResponses[0];
                ChatResponse[] rest = new ChatResponse[chatResponses.length - 1];
                System.arraycopy(chatResponses, 1, rest, 0, rest.length);
                when(chatModel.call(any(Prompt.class))).thenReturn(first, rest);
            }
        }
    }
}
