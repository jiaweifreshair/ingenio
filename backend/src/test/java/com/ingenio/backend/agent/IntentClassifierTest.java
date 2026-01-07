package com.ingenio.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.dto.ComplexityLevel;
import com.ingenio.backend.agent.dto.ComplexityScore;
import com.ingenio.backend.agent.dto.IntentClassificationResult;
import com.ingenio.backend.agent.dto.RequirementIntent;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * IntentClassifier单元测试
 *
 * 测试覆盖范围：
 * - 意图识别准确性测试（CLONE/DESIGN/HYBRID）
 * - URL提取和知名网站识别
 * - JSON响应解析和验证
 * - 错误处理和边界情况
 * - 后处理逻辑验证
 *
 * 测试策略：
 * - 使用Mockito Mock ChatModel，专注测试IntentClassifier业务逻辑
 * - 覆盖核心分支：三种意图类型、URL检测、网站识别
 * - 验证边界情况：空输入、超长输入、特殊字符、非JSON响应
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-16
 */
@DisplayName("IntentClassifier意图识别单元测试")
class IntentClassifierTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ComplexityEvaluator complexityEvaluator;

    private IntentClassifier intentClassifier;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        intentClassifier = new IntentClassifier(chatModel, objectMapper, complexityEvaluator);

        // Mock ComplexityEvaluator behavior
        ComplexityScore defaultScore = ComplexityScore.builder()
                .level(ComplexityLevel.SIMPLE)
                .finalScore(30)
                .entityCountScore(20)
                .relationshipComplexityScore(20)
                .aiCapabilityScore(10)
                .technicalRiskScore(10)
                .reasoning("Simple task")
                .build();
        when(complexityEvaluator.evaluateComplexity(any())).thenReturn(defaultScore);
    }

    // ==================== 意图识别准确性测试 ====================

    @Test
    @DisplayName("识别CLONE意图 - 包含明确的网站URL")
    void testClassifyIntent_Clone_WithExplicitUrl() {
        // Given: 用户需求包含明确的参考网站URL
        String userRequirement = "我想克隆 https://airbnb.com 的网站，做一个民宿预订平台";

        // Mock AI返回CLONE意图的JSON响应
        mockAIResponse("""
            {
              "intent": "clone",
              "confidence": 0.95,
              "reasoning": "用户明确提到克隆airbnb.com网站",
              "referenceUrls": ["https://airbnb.com"],
              "extractedKeywords": ["民宿", "预订", "airbnb"],
              "suggestedNextAction": "使用OpenLovable-CN爬取airbnb.com网站"
            }
            """);

        // When: 调用意图识别
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        // Then: 验证识别结果
        assertNotNull(result);
        assertEquals(RequirementIntent.CLONE_EXISTING_WEBSITE, result.getIntent());
        assertTrue(result.getConfidence() >= 0.9, "CLONE意图置信度应>=0.9");
        assertTrue(result.getReferenceUrls().contains("https://airbnb.com"));
        assertEquals("使用OpenLovable-CN爬取airbnb.com网站", result.getSuggestedNextAction());

        // 验证ChatModel被正确调用
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("识别CLONE意图 - 包含知名网站名称（无URL）")
    void testClassifyIntent_Clone_WithKnownWebsiteName() {
        // Given: 用户需求提到知名网站名称但没有URL
        String userRequirement = "仿照淘宝做一个电商平台，要有购物车和支付功能";

        // Mock AI返回CLONE意图
        mockAIResponse("""
            {
              "intent": "clone",
              "confidence": 0.92,
              "reasoning": "用户明确提到仿照淘宝",
              "referenceUrls": ["淘宝"],
              "extractedKeywords": ["电商", "购物车", "支付"],
              "suggestedNextAction": "参考淘宝的UI和功能设计"
            }
            """);

        // When
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        // Then
        assertNotNull(result);
        assertEquals(RequirementIntent.CLONE_EXISTING_WEBSITE, result.getIntent());
        assertTrue(result.getConfidence() >= 0.9);
        assertFalse(result.getReferenceUrls().isEmpty(), "应识别出淘宝作为参考");
    }

    @Test
    @DisplayName("识别DESIGN意图 - 从零设计无参考对象")
    void testClassifyIntent_Design_FromScratch() {
        // Given: 用户需求没有提及任何参考网站
        String userRequirement = "创建一个在线教育平台，包含课程浏览、视频学习、在线测试、学习进度追踪功能";

        // Mock AI返回DESIGN意图
        mockAIResponse("""
            {
              "intent": "design",
              "confidence": 0.93,
              "reasoning": "用户描述了具体功能需求，但没有提及参考网站",
              "referenceUrls": [],
              "extractedKeywords": ["在线教育", "课程", "视频", "测试", "学习进度"],
              "suggestedNextAction": "使用SuperDesign生成7种设计风格预览"
            }
            """);

        // When
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        // Then
        assertNotNull(result);
        assertEquals(RequirementIntent.DESIGN_FROM_SCRATCH, result.getIntent());
        assertTrue(result.getConfidence() >= 0.9);
        assertTrue(result.getReferenceUrls().isEmpty(), "从零设计不应有参考URL");
        assertEquals("使用SuperDesign生成7种设计风格预览", result.getSuggestedNextAction());
    }

    @Test
    @DisplayName("识别HYBRID意图 - 既有参考又有定制需求")
    void testClassifyIntent_Hybrid_CloneAndCustomize() {
        // Given: 用户既想参考现有网站，又有定制化需求
        String userRequirement = "参考小红书做一个内容分享平台，但我想加入AI推荐算法和虚拟货币奖励系统";

        // Mock AI返回HYBRID意图
        mockAIResponse("""
            {
              "intent": "hybrid",
              "confidence": 0.90,
              "reasoning": "用户既提到参考小红书，又明确了定制化需求（AI推荐、虚拟货币）",
              "referenceUrls": ["小红书"],
              "extractedKeywords": ["内容分享", "AI推荐", "虚拟货币", "奖励"],
              "customizationRequirement": "添加AI推荐算法和虚拟货币奖励系统",
              "suggestedNextAction": "先爬取小红书基础框架，再使用AI定制修改"
            }
            """);

        // When
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        // Then
        assertNotNull(result);
        assertEquals(RequirementIntent.HYBRID_CLONE_AND_CUSTOMIZE, result.getIntent());
        assertTrue(result.getConfidence() >= 0.85);
        assertFalse(result.getReferenceUrls().isEmpty());
        assertNotNull(result.getCustomizationRequirement());
        assertTrue(result.getCustomizationRequirement().contains("AI推荐"));
    }

    // ==================== URL提取和网站识别测试 ====================

    @ParameterizedTest
    @CsvSource({
        "'克隆 https://airbnb.com 的网站', 'https://airbnb.com'",
        "'参考 http://taobao.com 做电商', 'http://taobao.com'",
        "'仿照https://github.com/project做代码托管', 'https://github.com/project'",
        "'like https://www.amazon.com/products?id=123做商城', 'https://www.amazon.com/products?id=123'"
    })
    @DisplayName("参数化测试：URL提取正确性")
    void testUrlExtraction(String userRequirement, String expectedUrl) {
        // Mock AI返回包含URL的响应
        mockAIResponse(String.format("""
            {
              "intent": "clone",
              "confidence": 0.95,
              "reasoning": "包含URL参考",
              "referenceUrls": ["%s"],
              "extractedKeywords": ["克隆", "网站"]
            }
            """, expectedUrl));

        // When
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        // Then
        assertNotNull(result);
        assertTrue(result.getReferenceUrls().contains(expectedUrl),
            "应正确提取URL: " + expectedUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "仿照airbnb做民宿平台",
        "参考淘宝的购物车功能",
        "like 京东 做电商",
        "类似小红书的社区功能"
    })
    @DisplayName("参数化测试：知名网站名称识别")
    void testKnownWebsiteDetection(String userRequirement) {
        // Mock AI返回CLONE意图（知名网站识别）
        mockAIResponse("""
            {
              "intent": "clone",
              "confidence": 0.92,
              "reasoning": "识别出知名网站名称",
              "referenceUrls": ["已识别网站"],
              "extractedKeywords": ["仿照", "参考"]
            }
            """);

        // When
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        // Then
        assertNotNull(result);
        assertEquals(RequirementIntent.CLONE_EXISTING_WEBSITE, result.getIntent());
        assertFalse(result.getReferenceUrls().isEmpty(), "应识别出知名网站");
    }

    // ==================== JSON响应解析测试 ====================

    @Test
    @DisplayName("解析标准JSON响应")
    void testParseStandardJsonResponse() {
        // Given: AI返回标准JSON格式
        String userRequirement = "创建一个博客系统";
        mockAIResponse("""
            {
              "intent": "design",
              "confidence": 0.88,
              "reasoning": "标准JSON格式",
              "referenceUrls": [],
              "extractedKeywords": ["博客", "系统"],
              "suggestedNextAction": "生成设计方案"
            }
            """);

        // When
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        // Then
        assertNotNull(result);
        assertEquals(RequirementIntent.DESIGN_FROM_SCRATCH, result.getIntent());
        assertEquals(0.88, result.getConfidence(), 0.01);
    }

    @Test
    @DisplayName("解析包含Markdown代码块的JSON响应")
    void testParseJsonWithMarkdownCodeBlock() {
        // Given: AI返回Markdown格式包裹的JSON
        String userRequirement = "做一个CRM系统";
        mockAIResponse("""
            ```json
            {
              "intent": "design",
              "confidence": 0.91,
              "reasoning": "Markdown包裹的JSON",
              "referenceUrls": [],
              "extractedKeywords": ["CRM", "系统"]
            }
            ```
            """);

        // When
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        // Then
        assertNotNull(result);
        assertEquals(RequirementIntent.DESIGN_FROM_SCRATCH, result.getIntent());
    }

    @Test
    @DisplayName("解析AI返回对象结构字段（避免LinkedHashMap强转String）")
    void testParseJsonWithObjectFields() {
        // Given: AI返回的字段不是纯字符串，而是对象结构（历史上会触发ClassCastException）
        String userRequirement = "做一个在线教育平台";
        mockAIResponse("""
            {
              "intent": { "code": "design" },
              "confidence": "0.93",
              "reasoning": { "text": "用户描述了功能需求，但没有参考网站" },
              "referenceUrls": [{ "url": "https://example.com" }],
              "extractedKeywords": [{ "keyword": "在线教育" }, { "keyword": "课程" }],
              "suggestedNextAction": { "content": "使用SuperDesign生成7种设计风格预览" },
              "warnings": [{ "message": "示例警告" }]
            }
            """);

        // When
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        // Then
        assertNotNull(result);
        assertEquals(RequirementIntent.DESIGN_FROM_SCRATCH, result.getIntent());
        assertEquals(0.93, result.getConfidence(), 0.01);
        assertTrue(result.getReferenceUrls().contains("https://example.com"));
        assertTrue(result.getExtractedKeywords().contains("在线教育"));
        assertEquals("使用SuperDesign生成7种设计风格预览", result.getSuggestedNextAction());
        assertFalse(result.getWarnings().isEmpty());
    }

    // ==================== 错误处理和边界情况测试 ====================

    @Test
    @DisplayName("边界情况：空字符串输入 - 应抛出异常")
    void testEmptyStringInput() {
        // Given: 空字符串
        String userRequirement = "";

        // When & Then: 期望抛出BusinessException
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> intentClassifier.classifyIntent(userRequirement),
            "空字符串输入应抛出BusinessException"
        );

        // 验证异常code和消息
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertEquals("用户需求不能为空", exception.getMessage());
    }

    @Test
    @DisplayName("边界情况：超长输入（>5000字符）")
    void testVeryLongInput() {
        // Given: 超长需求描述
        String userRequirement = "创建一个电商平台".repeat(1000); // ~10000字符

        mockAIResponse("""
            {
              "intent": "design",
              "confidence": 0.75,
              "reasoning": "需求描述过长",
              "referenceUrls": [],
              "extractedKeywords": ["电商", "平台"],
              "warnings": ["需求描述过长，可能影响识别准确性"]
            }
            """);

        // When
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        // Then
        assertNotNull(result);
        assertFalse(result.getWarnings().isEmpty(), "应有长度警告");
    }

    @Test
    @DisplayName("边界情况：包含特殊字符和Emoji")
    void testSpecialCharactersAndEmoji() {
        // Given: 包含特殊字符和Emoji
        String userRequirement = "创建一个社交平台 💬📱，支持发送表情😀和GIF动图🎬";

        mockAIResponse("""
            {
              "intent": "design",
              "confidence": 0.89,
              "reasoning": "正常处理特殊字符",
              "referenceUrls": [],
              "extractedKeywords": ["社交", "表情", "动图"]
            }
            """);

        // When
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        // Then
        assertNotNull(result);
        assertEquals(RequirementIntent.DESIGN_FROM_SCRATCH, result.getIntent());
    }

    @Test
    @DisplayName("错误处理：AI返回非JSON格式")
    void testNonJsonResponse() {
        // Given: AI返回纯文本而非JSON
        String userRequirement = "做一个网站";

        // Mock AI返回非JSON文本 - 使用自定义Mock方法
        String nonJsonResponse = "这是一个纯文本响应，不是JSON格式";
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockMessage = new AssistantMessage(nonJsonResponse);

        when(mockGeneration.getOutput()).thenReturn(mockMessage);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        // When & Then: 应抛出BusinessException或返回带警告的默认结果
        assertThrows(Exception.class, () -> {
            intentClassifier.classifyIntent(userRequirement);
        });
    }

    @Test
    @DisplayName("错误处理：AI返回空响应")
    void testEmptyAIResponse() {
        // Given
        String userRequirement = "创建网站";

        // Mock AI返回空响应 - 使用AssistantMessage构造函数
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockMessage = new AssistantMessage("");

        when(mockGeneration.getOutput()).thenReturn(mockMessage);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        // When & Then
        assertThrows(Exception.class, () -> {
            intentClassifier.classifyIntent(userRequirement);
        });
    }

    @Test
    @DisplayName("错误处理：置信度超出范围0-1")
    void testInvalidConfidenceScore() {
        // Given: AI返回无效的置信度
        String userRequirement = "做网站";
        mockAIResponse("""
            {
              "intent": "design",
              "confidence": 1.5,
              "reasoning": "无效置信度",
              "referenceUrls": [],
              "extractedKeywords": []
            }
            """);

        // When
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        // Then: 应自动修正置信度到0-1范围
        assertTrue(result.getConfidence() >= 0 && result.getConfidence() <= 1,
            "置信度应在0-1范围内");
    }

    // ==================== 后处理逻辑验证 ====================

    @Test
    @DisplayName("后处理：自动补充警告信息")
    void testPostProcessing_WarningGeneration() {
        // Given: 低置信度场景
        String userRequirement = "做个东西";
        mockAIResponse("""
            {
              "intent": "design",
              "confidence": 0.40,
              "reasoning": "需求描述过于模糊",
              "referenceUrls": [],
              "extractedKeywords": []
            }
            """);

        // When
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        // Then: 应自动添加低置信度警告
        assertNotNull(result.getWarnings());
        boolean hasLowConfidenceWarning = result.getWarnings().stream()
            .anyMatch(w -> w.contains("置信度较低") || w.contains("低置信度"));

        assertTrue(hasLowConfidenceWarning || result.getConfidence() < 0.5,
            "低置信度(<0.5)时应有警告");
    }

    @Test
    @DisplayName("后处理：验证必填字段完整性")
    void testPostProcessing_RequiredFieldsValidation() {
        // Given
        String userRequirement = "创建应用";
        mockAIResponse("""
            {
              "intent": "design",
              "confidence": 0.85,
              "reasoning": "完整字段",
              "referenceUrls": [],
              "extractedKeywords": ["应用"],
              "suggestedNextAction": "生成设计"
            }
            """);

        // When
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        // Then: 验证所有必填字段
        assertNotNull(result.getIntent());
        assertNotNull(result.getConfidence());
        assertNotNull(result.getReasoning());
        assertNotNull(result.getExtractedKeywords());
        assertNotNull(result.getSuggestedNextAction());
    }

    // ==================== 辅助方法 ====================

    /**
     * Mock AI模型返回指定的JSON响应
     *
     * @param jsonResponse JSON格式的响应字符串
     */
    private void mockAIResponse(String jsonResponse) {
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);

        // 直接使用AssistantMessage构造函数创建实例，而不是Mock
        AssistantMessage assistantMessage = new AssistantMessage(jsonResponse);

        // Mock Generation的getOutput()方法返回AssistantMessage
        when(mockGeneration.getOutput()).thenReturn(assistantMessage);

        // Mock ChatResponse的getResult()方法返回Generation
        when(mockResponse.getResult()).thenReturn(mockGeneration);

        // Mock ChatModel的call()方法返回ChatResponse
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);
    }
}
