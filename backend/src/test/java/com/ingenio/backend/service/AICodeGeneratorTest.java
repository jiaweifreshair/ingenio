package com.ingenio.backend.service;

import com.ingenio.backend.agent.dto.AICapabilityRequirement;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AICodeGenerator单元测试
 *
 * 测试覆盖：
 * 1. 正常场景：生成完整的AI代码
 * 2. 参数验证：各种参数错误场景
 * 3. 模板加载：验证模板文件完整性
 * 4. 占位符替换：验证变量替换正确性
 * 5. 支持性检查：AI能力复杂度判断
 * 6. 新增8种AI能力：VIDEO_ANALYSIS、KNOWLEDGE_GRAPH等
 */
@SpringBootTest
@ActiveProfiles("test")
class AICodeGeneratorTest {

    @Autowired
    private AICodeGenerator aiCodeGenerator;

    private AICapabilityRequirement simpleAICapability;
    private AICapabilityRequirement mediumAICapability;
    private AICapabilityRequirement complexAICapability;

    @BeforeEach
    void setUp() {
        // 准备SIMPLE复杂度的AI能力需求
        AICapabilityRequirement.AICapability chatbotCapability =
                AICapabilityRequirement.AICapability.builder()
                        .type(AICapabilityRequirement.AICapabilityType.CHATBOT)
                        .description("智能聊天机器人")
                        .useCase("用户可以与AI进行多轮对话")
                        .estimatedTokens(1500)
                        .apiEndpoint("/api/chat")
                        .requiredConfigs(List.of("QINIU_API_KEY", "AI_MODEL"))
                        .build();

        simpleAICapability = AICapabilityRequirement.builder()
                .needsAI(true)
                .capabilities(List.of(chatbotCapability))
                .complexity(AICapabilityRequirement.AIComplexity.SIMPLE)
                .recommendedApproach(AICapabilityRequirement.AIApproach.DIRECT_API)
                .reasoning("单一聊天功能，直接调用七牛云API即可")
                .confidence(0.95)
                .build();

        // 准备MEDIUM复杂度的AI能力需求
        mediumAICapability = AICapabilityRequirement.builder()
                .needsAI(true)
                .capabilities(List.of(chatbotCapability))
                .complexity(AICapabilityRequirement.AIComplexity.MEDIUM)
                .recommendedApproach(AICapabilityRequirement.AIApproach.DIFY_WORKFLOW)
                .reasoning("需要多个AI能力组合")
                .confidence(0.90)
                .build();

        // 准备COMPLEX复杂度的AI能力需求
        complexAICapability = AICapabilityRequirement.builder()
                .needsAI(true)
                .capabilities(List.of(chatbotCapability))
                .complexity(AICapabilityRequirement.AIComplexity.COMPLEX)
                .recommendedApproach(AICapabilityRequirement.AIApproach.LANGCHAIN)
                .reasoning("需要复杂的Agent系统")
                .confidence(0.85)
                .build();
    }

    /**
     * 测试正常场景：生成完整的AI代码
     */
    @Test
    @DisplayName("生成AI代码 - 成功场景（SIMPLE复杂度）")
    void testGenerateAICode_Success() {
        // 准备参数
        String packageName = "com.example.myapp";
        String appName = "我的AI助手";

        // 执行生成
        Map<String, String> generatedFiles = aiCodeGenerator.generateAICode(
                simpleAICapability, packageName, appName);

        // 验证生成的文件数量
        assertNotNull(generatedFiles, "生成的文件Map不能为空");
        assertEquals(6, generatedFiles.size(), "应该生成6个文件");

        // 验证生成的文件路径和内容
        assertTrue(generatedFiles.containsKey(
                "core/src/commonMain/kotlin/com/example/myapp/pages/AIServicePager.kt"),
                "应该包含AIServicePager.kt");

        assertTrue(generatedFiles.containsKey(
                "core/src/commonMain/kotlin/com/example/myapp/ai/AIService.kt"),
                "应该包含AIService.kt");

        assertTrue(generatedFiles.containsKey(
                "core/src/commonMain/kotlin/com/example/myapp/config/AIConfig.kt"),
                "应该包含AIConfig.kt");

        assertTrue(generatedFiles.containsKey("local.properties.template"),
                "应该包含local.properties.template");

        assertTrue(generatedFiles.containsKey(".env.template"),
                "应该包含.env.template");

        assertTrue(generatedFiles.containsKey("AI_README.md"),
                "应该包含AI_README.md");

        // 验证包名替换
        String aiServiceContent = generatedFiles.get(
                "core/src/commonMain/kotlin/com/example/myapp/ai/AIService.kt");
        assertNotNull(aiServiceContent, "AIService.kt内容不能为空");
        assertTrue(aiServiceContent.contains("package com.example.myapp.ai"),
                "应该包含正确的包名");
        assertFalse(aiServiceContent.contains("{{PACKAGE_NAME}}"),
                "不应该包含未替换的占位符");

        // 验证应用名称替换
        String readmeContent = generatedFiles.get("AI_README.md");
        assertNotNull(readmeContent, "AI_README.md内容不能为空");
        assertTrue(readmeContent.contains("我的AI助手"),
                "应该包含应用名称");
        assertFalse(readmeContent.contains("{{APP_NAME}}"),
                "不应该包含未替换的占位符");

        // 验证生成日期替换
        assertTrue(aiServiceContent.contains("Generated by Ingenio Platform"),
                "应该包含生成标识");
        assertFalse(aiServiceContent.contains("{{GENERATION_DATE}}"),
                "不应该包含未替换的日期占位符");

        // 验证代码质量：应该包含完整的KuiklyUI DSL语法
        String pagerContent = generatedFiles.get(
                "core/src/commonMain/kotlin/com/example/myapp/pages/AIServicePager.kt");
        assertNotNull(pagerContent, "AIServicePager.kt内容不能为空");
        assertTrue(pagerContent.contains("@Page(\"ai_chat\")"),
                "应该包含@Page注解");
        assertTrue(pagerContent.contains("class AIServicePager : Pager()"),
                "应该继承Pager");
        assertTrue(pagerContent.contains("override fun body(): ViewBuilder"),
                "应该实现body方法");
        assertTrue(pagerContent.contains("Column {"), "应该使用Column组件");
        assertTrue(pagerContent.contains("attr {"), "应该使用attr块");
        assertTrue(pagerContent.contains("event {"), "应该使用event块");

        // 验证AI配置代码
        String configContent = generatedFiles.get(
                "core/src/commonMain/kotlin/com/example/myapp/config/AIConfig.kt");
        assertNotNull(configContent, "AIConfig.kt内容不能为空");
        assertTrue(configContent.contains("object AIConfig"),
                "应该是object单例");
        assertTrue(configContent.contains("val apiKey: String by lazy"),
                "应该有apiKey属性");
        assertTrue(configContent.contains("QINIU_API_KEY"),
                "应该配置七牛云API密钥");
        assertTrue(configContent.contains("getEnvOrProperty"),
                "应该有配置读取方法");
        assertTrue(configContent.contains("maskApiKey"),
                "应该有密钥脱敏方法");
    }

    // ==================== 8种新AI能力测试 ====================

    /**
     * 测试VIDEO_ANALYSIS代码生成
     */
    @Test
    @DisplayName("生成VIDEO_ANALYSIS代码 - 成功")
    public void testGenerateVideoAnalysisCode_Success() {
        // Arrange
        AICapabilityRequirement.AICapability videoCapability =
                AICapabilityRequirement.AICapability.builder()
                        .type(AICapabilityRequirement.AICapabilityType.VIDEO_ANALYSIS)
                        .description("视频分析")
                        .useCase("分析视频内容，识别物体、场景、动作")
                        .estimatedTokens(3000)
                        .apiEndpoint("/api/video/analyze")
                        .requiredConfigs(List.of("QWEN_API_KEY"))
                        .build();

        AICapabilityRequirement aiCapability = AICapabilityRequirement.builder()
                .needsAI(true)
                .capabilities(List.of(videoCapability))
                .complexity(AICapabilityRequirement.AIComplexity.SIMPLE)
                .build();

        // Act
        Map<String, String> result = aiCodeGenerator.generateAICode(
                aiCapability,
                "com.example.videoapp",
                "VideoApp"
        );

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size(), "应该生成3个文件");

        // 验证Service文件
        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/videoapp/ai/VideoAnalysisService.kt"));
        String serviceCode = result.get("core/src/commonMain/kotlin/com/example/videoapp/ai/VideoAnalysisService.kt");
        assertTrue(serviceCode.contains("class VideoAnalysisService"));
        assertTrue(serviceCode.contains("qwen-vl-max"));
        assertTrue(serviceCode.contains("suspend fun analyzeVideo"));
        assertTrue(serviceCode.contains("VideoAnalysisResult"));

        // 验证ViewModel文件
        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/videoapp/presentation/viewmodel/VideoAnalysisViewModel.kt"));
        String viewModelCode = result.get("core/src/commonMain/kotlin/com/example/videoapp/presentation/viewmodel/VideoAnalysisViewModel.kt");
        assertTrue(viewModelCode.contains("class VideoAnalysisViewModel"));
        assertTrue(viewModelCode.contains("sealed class AnalysisState"));

        // 验证README文件
        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/videoapp/ai/README_VIDEO_ANALYSIS.md"));
        String readmeCode = result.get("core/src/commonMain/kotlin/com/example/videoapp/ai/README_VIDEO_ANALYSIS.md");
        assertTrue(readmeCode.contains("视频分析功能使用指南"));
        assertTrue(readmeCode.contains("VideoApp"));
    }

    /**
     * 测试KNOWLEDGE_GRAPH代码生成
     */
    @Test
    @DisplayName("生成KNOWLEDGE_GRAPH代码 - 成功")
    public void testGenerateKnowledgeGraphCode_Success() {
        // Arrange
        AICapabilityRequirement.AICapability graphCapability =
                AICapabilityRequirement.AICapability.builder()
                        .type(AICapabilityRequirement.AICapabilityType.KNOWLEDGE_GRAPH)
                        .description("知识图谱")
                        .useCase("从文本中提取实体和关系")
                        .estimatedTokens(1500)
                        .build();

        AICapabilityRequirement aiCapability = AICapabilityRequirement.builder()
                .needsAI(true)
                .capabilities(List.of(graphCapability))
                .complexity(AICapabilityRequirement.AIComplexity.SIMPLE)
                .build();

        // Act
        Map<String, String> result = aiCodeGenerator.generateAICode(
                aiCapability,
                "com.example.graphapp",
                "GraphApp"
        );

        // Assert
        assertNotNull(result);
        assertEquals(4, result.size(), "应该生成4个文件");

        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/graphapp/ai/KnowledgeGraphService.kt"));
        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/graphapp/ai/GraphBuilder.kt"));
        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/graphapp/presentation/viewmodel/GraphViewModel.kt"));
        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/graphapp/ai/README_KNOWLEDGE_GRAPH.md"));

        String serviceCode = result.get("core/src/commonMain/kotlin/com/example/graphapp/ai/KnowledgeGraphService.kt");
        assertTrue(serviceCode.contains("class KnowledgeGraphService"));
        assertTrue(serviceCode.contains("extractEntitiesAndRelations"));
        assertTrue(serviceCode.contains("KnowledgeGraph"));
    }

    /**
     * 测试OCR_DOCUMENT代码生成
     */
    @Test
    @DisplayName("生成OCR_DOCUMENT代码 - 成功")
    public void testGenerateOCRDocumentCode_Success() {
        // Arrange
        AICapabilityRequirement.AICapability ocrCapability =
                AICapabilityRequirement.AICapability.builder()
                        .type(AICapabilityRequirement.AICapabilityType.OCR_DOCUMENT)
                        .description("智能文档识别")
                        .useCase("识别身份证、发票、合同等文档")
                        .estimatedTokens(800)
                        .build();

        AICapabilityRequirement aiCapability = AICapabilityRequirement.builder()
                .needsAI(true)
                .capabilities(List.of(ocrCapability))
                .complexity(AICapabilityRequirement.AIComplexity.SIMPLE)
                .build();

        // Act
        Map<String, String> result = aiCodeGenerator.generateAICode(
                aiCapability,
                "com.example.ocrapp",
                "OCRApp"
        );

        // Assert
        assertNotNull(result);
        assertEquals(4, result.size(), "应该生成4个文件");

        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/ocrapp/ai/OCRService.kt"));
        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/ocrapp/ai/DocumentProcessor.kt"));

        String serviceCode = result.get("core/src/commonMain/kotlin/com/example/ocrapp/ai/OCRService.kt");
        assertTrue(serviceCode.contains("class OCRService"));
        assertTrue(serviceCode.contains("recognizeDocument"));
        assertTrue(serviceCode.contains("enum class DocumentType"));
    }

    /**
     * 测试REALTIME_STREAM代码生成
     */
    @Test
    @DisplayName("生成REALTIME_STREAM代码 - 成功")
    public void testGenerateRealtimeStreamCode_Success() {
        // Arrange
        AICapabilityRequirement.AICapability streamCapability =
                AICapabilityRequirement.AICapability.builder()
                        .type(AICapabilityRequirement.AICapabilityType.REALTIME_STREAM)
                        .description("实时流分析")
                        .useCase("实时处理音视频流")
                        .estimatedTokens(2000)
                        .build();

        AICapabilityRequirement aiCapability = AICapabilityRequirement.builder()
                .needsAI(true)
                .capabilities(List.of(streamCapability))
                .complexity(AICapabilityRequirement.AIComplexity.SIMPLE)
                .build();

        // Act
        Map<String, String> result = aiCodeGenerator.generateAICode(
                aiCapability,
                "com.example.streamapp",
                "StreamApp"
        );

        // Assert
        assertNotNull(result);
        assertEquals(4, result.size(), "应该生成4个文件");

        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/streamapp/ai/RealtimeStreamService.kt"));
        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/streamapp/ai/StreamProcessor.kt"));

        String serviceCode = result.get("core/src/commonMain/kotlin/com/example/streamapp/ai/RealtimeStreamService.kt");
        assertTrue(serviceCode.contains("class RealtimeStreamService"));
        assertTrue(serviceCode.contains("startRealtimeSession"));
        assertTrue(serviceCode.contains("WebSockets"));
    }

    /**
     * 测试HYPER_PERSONALIZATION代码生成
     */
    @Test
    @DisplayName("生成HYPER_PERSONALIZATION代码 - 成功")
    public void testGenerateHyperPersonalizationCode_Success() {
        // Arrange
        AICapabilityRequirement.AICapability personalizationCapability =
                AICapabilityRequirement.AICapability.builder()
                        .type(AICapabilityRequirement.AICapabilityType.HYPER_PERSONALIZATION)
                        .description("超个性化引擎")
                        .useCase("个性化推荐和内容生成")
                        .estimatedTokens(1800)
                        .build();

        AICapabilityRequirement aiCapability = AICapabilityRequirement.builder()
                .needsAI(true)
                .capabilities(List.of(personalizationCapability))
                .complexity(AICapabilityRequirement.AIComplexity.SIMPLE)
                .build();

        // Act
        Map<String, String> result = aiCodeGenerator.generateAICode(
                aiCapability,
                "com.example.personalapp",
                "PersonalApp"
        );

        // Assert
        assertNotNull(result);
        assertEquals(4, result.size(), "应该生成4个文件");

        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/personalapp/ai/PersonalizationService.kt"));
        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/personalapp/ai/UserProfiler.kt"));

        String serviceCode = result.get("core/src/commonMain/kotlin/com/example/personalapp/ai/PersonalizationService.kt");
        assertTrue(serviceCode.contains("class PersonalizationService"));
        assertTrue(serviceCode.contains("generateRecommendations"));
        assertTrue(serviceCode.contains("UserProfile"));
    }

    /**
     * 测试PREDICTIVE_ANALYTICS代码生成
     */
    @Test
    @DisplayName("生成PREDICTIVE_ANALYTICS代码 - 成功")
    public void testGeneratePredictiveAnalyticsCode_Success() {
        // Arrange
        AICapabilityRequirement.AICapability predictiveCapability =
                AICapabilityRequirement.AICapability.builder()
                        .type(AICapabilityRequirement.AICapabilityType.PREDICTIVE_ANALYTICS)
                        .description("预测分析")
                        .useCase("时间序列预测和趋势分析")
                        .estimatedTokens(2500)
                        .build();

        AICapabilityRequirement aiCapability = AICapabilityRequirement.builder()
                .needsAI(true)
                .capabilities(List.of(predictiveCapability))
                .complexity(AICapabilityRequirement.AIComplexity.SIMPLE)
                .build();

        // Act
        Map<String, String> result = aiCodeGenerator.generateAICode(
                aiCapability,
                "com.example.predictapp",
                "PredictApp"
        );

        // Assert
        assertNotNull(result);
        assertEquals(4, result.size(), "应该生成4个文件");

        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/predictapp/ai/PredictiveService.kt"));
        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/predictapp/ai/DataAnalyzer.kt"));

        String serviceCode = result.get("core/src/commonMain/kotlin/com/example/predictapp/ai/PredictiveService.kt");
        assertTrue(serviceCode.contains("class PredictiveService"));
        assertTrue(serviceCode.contains("predictFuture"));
        assertTrue(serviceCode.contains("PredictionResult"));
    }

    /**
     * 测试MULTIMODAL_GENERATION代码生成
     */
    @Test
    @DisplayName("生成MULTIMODAL_GENERATION代码 - 成功")
    public void testGenerateMultimodalGenerationCode_Success() {
        // Arrange
        AICapabilityRequirement.AICapability multimodalCapability =
                AICapabilityRequirement.AICapability.builder()
                        .type(AICapabilityRequirement.AICapabilityType.MULTIMODAL_GENERATION)
                        .description("多模态生成")
                        .useCase("文生图、图生文等多模态生成")
                        .estimatedTokens(2000)
                        .build();

        AICapabilityRequirement aiCapability = AICapabilityRequirement.builder()
                .needsAI(true)
                .capabilities(List.of(multimodalCapability))
                .complexity(AICapabilityRequirement.AIComplexity.SIMPLE)
                .build();

        // Act
        Map<String, String> result = aiCodeGenerator.generateAICode(
                aiCapability,
                "com.example.multiapp",
                "MultiApp"
        );

        // Assert
        assertNotNull(result);
        assertEquals(4, result.size(), "应该生成4个文件");

        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/multiapp/ai/MultimodalService.kt"));
        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/multiapp/ai/ContentGenerator.kt"));

        String serviceCode = result.get("core/src/commonMain/kotlin/com/example/multiapp/ai/MultimodalService.kt");
        assertTrue(serviceCode.contains("class MultimodalService"));
        assertTrue(serviceCode.contains("textToImage"));
        assertTrue(serviceCode.contains("imageToText"));
        assertTrue(serviceCode.contains("enum class ImageStyle"));
    }

    /**
     * 测试ANOMALY_DETECTION代码生成
     */
    @Test
    @DisplayName("生成ANOMALY_DETECTION代码 - 成功")
    public void testGenerateAnomalyDetectionCode_Success() {
        // Arrange
        AICapabilityRequirement.AICapability anomalyCapability =
                AICapabilityRequirement.AICapability.builder()
                        .type(AICapabilityRequirement.AICapabilityType.ANOMALY_DETECTION)
                        .description("异常检测")
                        .useCase("识别数据中的异常模式")
                        .estimatedTokens(1500)
                        .build();

        AICapabilityRequirement aiCapability = AICapabilityRequirement.builder()
                .needsAI(true)
                .capabilities(List.of(anomalyCapability))
                .complexity(AICapabilityRequirement.AIComplexity.SIMPLE)
                .build();

        // Act
        Map<String, String> result = aiCodeGenerator.generateAICode(
                aiCapability,
                "com.example.anomalyapp",
                "AnomalyApp"
        );

        // Assert
        assertNotNull(result);
        assertEquals(4, result.size(), "应该生成4个文件");

        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/anomalyapp/ai/AnomalyDetectionService.kt"));
        assertTrue(result.containsKey("core/src/commonMain/kotlin/com/example/anomalyapp/ai/PatternAnalyzer.kt"));

        String serviceCode = result.get("core/src/commonMain/kotlin/com/example/anomalyapp/ai/AnomalyDetectionService.kt");
        assertTrue(serviceCode.contains("class AnomalyDetectionService"));
        assertTrue(serviceCode.contains("detectAnomalies"));
        assertTrue(serviceCode.contains("AnomalyDetectionResult"));
    }

    // ==================== 原有参数验证测试 ====================

    /**
     * 测试参数验证 - AI能力需求为空
     */
    @Test
    @DisplayName("生成AI代码 - AI能力需求为空")
    void testGenerateAICode_NullAICapability() {
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiCodeGenerator.generateAICode(null, "com.example.myapp", "测试应用");
        });

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("AI能力需求不能为空"));
    }

    /**
     * 测试参数验证 - 不需要AI能力
     */
    @Test
    @DisplayName("生成AI代码 - 不需要AI能力")
    void testGenerateAICode_NoAINeeded() {
        AICapabilityRequirement noAI = AICapabilityRequirement.builder()
                .needsAI(false)
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiCodeGenerator.generateAICode(noAI, "com.example.myapp", "测试应用");
        });

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("不需要AI能力"));
    }

    /**
     * 测试参数验证 - 包名为空
     */
    @Test
    @DisplayName("生成AI代码 - 包名为空")
    void testGenerateAICode_EmptyPackageName() {
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiCodeGenerator.generateAICode(simpleAICapability, "", "测试应用");
        });

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("包名不能为空"));
    }

    /**
     * 测试参数验证 - 包名格式错误
     */
    @Test
    @DisplayName("生成AI代码 - 包名格式错误")
    void testGenerateAICode_InvalidPackageNameFormat() {
        String[] invalidPackageNames = {
                "Com.Example",      // 大写字母
                "com.123example",   // 数字开头
                "com.example-app",  // 包含连字符
                "com",              // 单层包名
                "com.example..app"  // 双点
        };

        for (String invalidName : invalidPackageNames) {
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                aiCodeGenerator.generateAICode(simpleAICapability, invalidName, "测试应用");
            }, "包名 " + invalidName + " 应该被拒绝");

            assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
            assertTrue(exception.getMessage().contains("包名格式不正确"),
                    "错误消息应该提示包名格式不正确");
        }
    }

    /**
     * 测试参数验证 - 应用名称为空
     */
    @Test
    @DisplayName("生成AI代码 - 应用名称为空")
    void testGenerateAICode_EmptyAppName() {
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiCodeGenerator.generateAICode(simpleAICapability, "com.example.myapp", "");
        });

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("应用名称不能为空"));
    }

    /**
     * 测试参数验证 - 不支持的AI复杂度（MEDIUM）
     */
    @Test
    @DisplayName("生成AI代码 - MEDIUM复杂度不支持")
    void testGenerateAICode_MediumComplexityNotSupported() {
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiCodeGenerator.generateAICode(mediumAICapability, "com.example.myapp", "测试应用");
        });

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("SIMPLE复杂度"));
        assertTrue(exception.getMessage().contains("MEDIUM"));
    }

    /**
     * 测试参数验证 - 不支持的AI复杂度（COMPLEX）
     */
    @Test
    @DisplayName("生成AI代码 - COMPLEX复杂度不支持")
    void testGenerateAICode_ComplexComplexityNotSupported() {
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiCodeGenerator.generateAICode(complexAICapability, "com.example.myapp", "测试应用");
        });

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("COMPLEX"));
    }

    /**
     * 测试支持性检查
     */
    @Test
    @DisplayName("检查AI能力是否受支持")
    void testIsSupported() {
        // SIMPLE复杂度应该受支持
        assertTrue(aiCodeGenerator.isSupported(simpleAICapability),
                "SIMPLE复杂度应该受支持");

        // MEDIUM复杂度不受支持
        assertFalse(aiCodeGenerator.isSupported(mediumAICapability),
                "MEDIUM复杂度不应该受支持");

        // COMPLEX复杂度不受支持
        assertFalse(aiCodeGenerator.isSupported(complexAICapability),
                "COMPLEX复杂度不应该受支持");

        // null不受支持
        assertFalse(aiCodeGenerator.isSupported(null),
                "null不应该受支持");

        // needsAI=false不受支持
        AICapabilityRequirement noAI = AICapabilityRequirement.builder()
                .needsAI(false)
                .build();
        assertFalse(aiCodeGenerator.isSupported(noAI),
                "needsAI=false不应该受支持");
    }

    /**
     * 测试获取不支持的原因
     */
    @Test
    @DisplayName("获取AI能力不支持的原因")
    void testGetUnsupportedReason() {
        // null原因
        String reason1 = aiCodeGenerator.getUnsupportedReason(null);
        assertTrue(reason1.contains("为空"), "应该提示为空");

        // needsAI=false原因
        AICapabilityRequirement noAI = AICapabilityRequirement.builder()
                .needsAI(false)
                .build();
        String reason2 = aiCodeGenerator.getUnsupportedReason(noAI);
        assertTrue(reason2.contains("不需要AI能力"), "应该提示不需要AI能力");

        // MEDIUM复杂度原因
        String reason3 = aiCodeGenerator.getUnsupportedReason(mediumAICapability);
        assertTrue(reason3.contains("MEDIUM"), "应该提示MEDIUM复杂度");
        assertTrue(reason3.contains("SIMPLE"), "应该提示仅支持SIMPLE");

        // COMPLEX复杂度原因
        String reason4 = aiCodeGenerator.getUnsupportedReason(complexAICapability);
        assertTrue(reason4.contains("COMPLEX"), "应该提示COMPLEX复杂度");
    }

    /**
     * 测试获取支持的AI能力列表
     */
    @Test
    @DisplayName("获取支持的AI能力类型列表")
    void testGetSupportedCapabilities() {
        String supportedCapabilities = aiCodeGenerator.getSupportedCapabilities();

        assertNotNull(supportedCapabilities, "支持的能力列表不应为空");

        // 验证原有11种能力
        assertTrue(supportedCapabilities.contains("CHATBOT"), "应该包含CHATBOT");
        assertTrue(supportedCapabilities.contains("QA_SYSTEM"), "应该包含QA_SYSTEM");
        assertTrue(supportedCapabilities.contains("RAG"), "应该包含RAG");

        // 验证新增8种能力
        assertTrue(supportedCapabilities.contains("VIDEO_ANALYSIS"), "应该包含VIDEO_ANALYSIS");
        assertTrue(supportedCapabilities.contains("KNOWLEDGE_GRAPH"), "应该包含KNOWLEDGE_GRAPH");
        assertTrue(supportedCapabilities.contains("OCR_DOCUMENT"), "应该包含OCR_DOCUMENT");
        assertTrue(supportedCapabilities.contains("REALTIME_STREAM"), "应该包含REALTIME_STREAM");
        assertTrue(supportedCapabilities.contains("HYPER_PERSONALIZATION"), "应该包含HYPER_PERSONALIZATION");
        assertTrue(supportedCapabilities.contains("PREDICTIVE_ANALYTICS"), "应该包含PREDICTIVE_ANALYTICS");
        assertTrue(supportedCapabilities.contains("MULTIMODAL_GENERATION"), "应该包含MULTIMODAL_GENERATION");
        assertTrue(supportedCapabilities.contains("ANOMALY_DETECTION"), "应该包含ANOMALY_DETECTION");
    }

    /**
     * 测试多种包名格式
     */
    @Test
    @DisplayName("测试多种有效的包名格式")
    void testVariousValidPackageNames() {
        String[] validPackageNames = {
                "com.example.myapp",
                "com.ingenio.ai.assistant",
                "com.company.product.module",
                "com.test123.app456",
                "com.under_score.test_app"
        };

        for (String packageName : validPackageNames) {
            assertDoesNotThrow(() -> {
                Map<String, String> files = aiCodeGenerator.generateAICode(
                        simpleAICapability, packageName, "测试应用");
                assertNotNull(files, "包名 " + packageName + " 应该生成成功");
            }, "包名 " + packageName + " 应该是有效的");
        }
    }

    /**
     * 测试生成的Kotlin代码语法完整性
     */
    @Test
    @DisplayName("验证生成的Kotlin代码语法完整性")
    void testGeneratedKotlinCodeSyntax() {
        Map<String, String> generatedFiles = aiCodeGenerator.generateAICode(
                simpleAICapability, "com.example.test", "测试应用");

        // 验证AIService.kt语法
        String aiServiceCode = generatedFiles.get(
                "core/src/commonMain/kotlin/com/example/test/ai/AIService.kt");
        assertNotNull(aiServiceCode);

        // 验证类定义
        assertTrue(aiServiceCode.contains("class AIService("), "应该有类定义");
        assertTrue(aiServiceCode.contains("private val apiKey: String"), "应该有apiKey参数");

        // 验证方法定义
        assertTrue(aiServiceCode.contains("suspend fun chat("), "应该有chat方法");
        assertTrue(aiServiceCode.contains("suspend fun chatStream("), "应该有chatStream方法");
        assertTrue(aiServiceCode.contains("fun close()"), "应该有close方法");

        // 验证导入语句
        assertTrue(aiServiceCode.contains("import io.ktor.client.*"), "应该导入Ktor");
        assertTrue(aiServiceCode.contains("import kotlinx.coroutines.flow.Flow"), "应该导入Flow");

        // 验证数据类定义
        assertTrue(aiServiceCode.contains("data class ChatMessage("), "应该有ChatMessage");
        assertTrue(aiServiceCode.contains("@Serializable"), "应该有序列化注解");
    }
}
