package com.ingenio.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.dto.ComplexityScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ComplexityEvaluatorImpl单元测试
 *
 * <p>目标：验证复杂度评估的JSON解析具备容错能力，避免AI返回对象结构导致ClassCastException。</p>
 *
 * <p>说明：该测试不依赖Mockito inline mock maker（避免在受限环境下Attach失败）。</p>
 */
@DisplayName("ComplexityEvaluatorImpl复杂度评估单元测试")
class ComplexityEvaluatorImplTest {

    @Mock
    private ChatModel chatModel;

    private ComplexityEvaluatorImpl complexityEvaluator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        complexityEvaluator = new ComplexityEvaluatorImpl(chatModel, new ObjectMapper());
    }

    @Test
    @DisplayName("解析AI返回对象结构字段（避免LinkedHashMap强转String）")
    void testParseJsonWithObjectFields() {
        mockAIResponse("""
            {
              "entityCountScore": { "value": 60 },
              "relationshipComplexityScore": { "score": 50 },
              "aiCapabilityScore": "30",
              "technicalRiskScore": 40,
              "extractedEntities": [{ "name": "用户" }, { "name": "订单" }],
              "keyTechnologies": [{ "value": "支付集成" }],
              "aiCapabilities": [],
              "riskFactors": [{ "message": "第三方支付依赖" }],
              "reasoning": { "text": "对象结构的reasoning" },
              "suggestedArchitecture": { "content": "Spring Boot + PostgreSQL" },
              "suggestedDevelopmentStrategy": { "message": "先做MVP再迭代" }
            }
            """);

        ComplexityScore score = complexityEvaluator.evaluateComplexity("做一个电商平台，包含用户、订单、支付集成");

        assertNotNull(score);
        assertEquals(60, score.getEntityCountScore());
        assertEquals(50, score.getRelationshipComplexityScore());
        assertEquals(30, score.getAiCapabilityScore());
        assertEquals(40, score.getTechnicalRiskScore());
        assertTrue(score.getExtractedEntities().contains("用户"));
        assertEquals("对象结构的reasoning", score.getReasoning());
        assertEquals("Spring Boot + PostgreSQL", score.getSuggestedArchitecture());
        assertEquals("先做MVP再迭代", score.getSuggestedDevelopmentStrategy());
    }

    /**
     * Mock AI模型返回指定的JSON响应
     *
     * @param jsonResponse JSON格式的响应字符串
     */
    private void mockAIResponse(String jsonResponse) {
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage assistantMessage = new AssistantMessage(jsonResponse);

        when(mockGeneration.getOutput()).thenReturn(assistantMessage);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);
    }
}

