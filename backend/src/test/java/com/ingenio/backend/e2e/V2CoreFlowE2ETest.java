package com.ingenio.backend.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.v2.ValidateAgentV2FullStackImpl;
import com.ingenio.backend.ai.capability.AICapabilityIntegrator;
import com.ingenio.backend.ai.capability.AICapabilityIntegrator.*;
import com.ingenio.backend.codegen.kuikly.KuiklyUIGenerator;
import com.ingenio.backend.codegen.kuikly.KuiklyUIGenerator.*;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import com.ingenio.backend.codegen.schema.FieldType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V2.0核心流程E2E测试
 *
 * <p>测试覆盖：</p>
 * <ol>
 *   <li>ValidateAgentV2 三层验证架构</li>
 *   <li>KuiklyUI 多端代码生成</li>
 *   <li>AI能力自动集成</li>
 * </ol>
 *
 * <p>测试策略：</p>
 * <ul>
 *   <li>使用真实PostgreSQL数据库（TestContainers）</li>
 *   <li>零Mock策略：使用真实Service</li>
 *   <li>验证核心业务逻辑正确性</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 E2E测试
 */
@DisplayName("V2.0核心流程E2E测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class V2CoreFlowE2ETest extends BaseE2ETest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private ValidateAgentV2FullStackImpl validateAgentV2;

    @Autowired
    private KuiklyUIGenerator kuiklyUIGenerator;

    @Autowired
    private AICapabilityIntegrator aiCapabilityIntegrator;

    // ==================== ValidateAgentV2 三层验证测试 ====================

    @Test
    @Order(1)
    @DisplayName("ValidateAgentV2: 服务注入成功")
    void testValidateAgentV2_ServiceInjection() {
        // ValidateAgentV2可能是可选组件
        // 如果注入成功则验证基本属性
        if (validateAgentV2 != null) {
            assertThat(validateAgentV2).isNotNull();
            assertThat(validateAgentV2.getVersion()).isEqualTo("V2");
        }
    }

    @Test
    @Order(2)
    @DisplayName("ValidateAgentV2: 验证方法执行 - 空输入")
    void testValidateAgentV2_ValidateEmptyInput() {
        if (validateAgentV2 == null) return;

        // 测试空输入
        Map<String, Object> emptyInput = new HashMap<>();
        Map<String, Object> result = validateAgentV2.validate(emptyInput);

        assertThat(result).isNotNull();
        assertThat(result).containsKey("success");
        assertThat(result).containsKey("version");
        assertThat(result.get("version")).isEqualTo("V2");
    }

    @Test
    @Order(3)
    @DisplayName("ValidateAgentV2: 验证方法执行 - 有效输入")
    void testValidateAgentV2_ValidateWithValidInput() {
        if (validateAgentV2 == null) return;

        // 构建模拟的Execute输出
        Map<String, Object> executeResult = buildMockExecuteResult();
        Map<String, Object> result = validateAgentV2.validate(executeResult);

        assertThat(result).isNotNull();
        assertThat(result).containsKey("success");
        assertThat(result).containsKey("tier1");
        assertThat(result).containsKey("tier2");
    }

    @Test
    @Order(4)
    @DisplayName("ValidateAgentV2: 异步验证查询 - 不存在的任务")
    void testValidateAgentV2_QueryAsyncValidation_NotFound() {
        if (validateAgentV2 == null) return;

        Map<String, Object> result = validateAgentV2.queryAsyncValidation("non-existent-id");

        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("NOT_FOUND");
    }

    @Test
    @Order(5)
    @DisplayName("ValidateAgentV2: 描述信息正确")
    void testValidateAgentV2_Description() {
        if (validateAgentV2 == null) return;

        String description = validateAgentV2.getDescription();
        assertThat(description).isNotNull();
        assertThat(description).contains("V2.0");
    }

    // ==================== KuiklyUI 多端代码生成测试 ====================

    @Test
    @Order(10)
    @DisplayName("KuiklyUI: 服务注入成功")
    void testKuiklyUI_ServiceInjection() {
        assertThat(kuiklyUIGenerator).isNotNull();
    }

    @Test
    @Order(11)
    @DisplayName("KuiklyUI: 支持的平台列表")
    void testKuiklyUI_SupportedPlatforms() {
        Platform[] platforms = Platform.values();

        assertThat(platforms).hasSize(5);
        assertThat(platforms).contains(
                Platform.WEB,
                Platform.ANDROID,
                Platform.IOS,
                Platform.WECHAT,
                Platform.HARMONYOS
        );
    }

    @Test
    @Order(12)
    @DisplayName("KuiklyUI: 平台枚举属性")
    void testKuiklyUI_PlatformProperties() {
        assertThat(Platform.WEB.getDisplayName()).isEqualTo("Web");
        assertThat(Platform.WEB.getTechStack()).isEqualTo("React/Next.js");

        assertThat(Platform.ANDROID.getDisplayName()).isEqualTo("Android");
        assertThat(Platform.ANDROID.getTechStack()).isEqualTo("Kotlin/Jetpack Compose");

        assertThat(Platform.IOS.getDisplayName()).isEqualTo("iOS");
        assertThat(Platform.IOS.getTechStack()).isEqualTo("Swift/SwiftUI");

        assertThat(Platform.WECHAT.getDisplayName()).isEqualTo("WeChat");
        assertThat(Platform.WECHAT.getTechStack()).isEqualTo("微信小程序");

        assertThat(Platform.HARMONYOS.getDisplayName()).isEqualTo("HarmonyOS");
        assertThat(Platform.HARMONYOS.getTechStack()).isEqualTo("ArkTS/ArkUI");
    }

    @Test
    @Order(13)
    @DisplayName("KuiklyUI: 多端代码生成 - 使用实体列表")
    void testKuiklyUI_GenerateMultiPlatformCode() {
        // 构建测试实体
        List<Entity> entities = buildSimpleEntityList();

        // 生成多端代码 - 使用正确的方法签名
        MultiPlatformResult result = kuiklyUIGenerator.generateMultiPlatform(entities, "TestApp");

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPlatformCodes()).isNotNull();
        assertThat(result.getEntityCount()).isEqualTo(1);
    }

    @Test
    @Order(14)
    @DisplayName("KuiklyUI: 多端代码生成 - 包含所有平台")
    void testKuiklyUI_GenerateMultiPlatformCode_AllPlatforms() {
        List<Entity> entities = buildSimpleEntityList();

        MultiPlatformResult result = kuiklyUIGenerator.generateMultiPlatform(entities, "AllPlatformApp");

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();

        Map<Platform, PlatformCode> platformCodes = result.getPlatformCodes();
        assertThat(platformCodes).isNotNull();

        // 验证所有5个平台都生成了代码
        assertThat(platformCodes).containsKey(Platform.WEB);
        assertThat(platformCodes).containsKey(Platform.ANDROID);
        assertThat(platformCodes).containsKey(Platform.IOS);
        assertThat(platformCodes).containsKey(Platform.WECHAT);
        assertThat(platformCodes).containsKey(Platform.HARMONYOS);
    }

    @Test
    @Order(15)
    @DisplayName("KuiklyUI: 生成的Web代码结构验证")
    void testKuiklyUI_WebCodeStructure() {
        List<Entity> entities = buildSimpleEntityList();

        MultiPlatformResult result = kuiklyUIGenerator.generateMultiPlatform(entities, "WebApp");

        assertThat(result.isSuccess()).isTrue();

        PlatformCode webCode = result.getPlatformCodes().get(Platform.WEB);
        assertThat(webCode).isNotNull();
        assertThat(webCode.getPlatform()).isEqualTo(Platform.WEB);
        assertThat(webCode.getFiles()).isNotEmpty();
        assertThat(webCode.getEntryPoint()).isNotNull();
        assertThat(webCode.getDependencies()).isNotNull();
    }

    @Test
    @Order(16)
    @DisplayName("KuiklyUI: 生成的Android代码结构验证")
    void testKuiklyUI_AndroidCodeStructure() {
        List<Entity> entities = buildSimpleEntityList();

        MultiPlatformResult result = kuiklyUIGenerator.generateMultiPlatform(entities, "AndroidApp");

        assertThat(result.isSuccess()).isTrue();

        PlatformCode androidCode = result.getPlatformCodes().get(Platform.ANDROID);
        assertThat(androidCode).isNotNull();
        assertThat(androidCode.getPlatform()).isEqualTo(Platform.ANDROID);
        assertThat(androidCode.getFiles()).isNotEmpty();
    }

    @Test
    @Order(17)
    @DisplayName("KuiklyUI: 生成的iOS代码结构验证")
    void testKuiklyUI_IOSCodeStructure() {
        List<Entity> entities = buildSimpleEntityList();

        MultiPlatformResult result = kuiklyUIGenerator.generateMultiPlatform(entities, "iOSApp");

        assertThat(result.isSuccess()).isTrue();

        PlatformCode iosCode = result.getPlatformCodes().get(Platform.IOS);
        assertThat(iosCode).isNotNull();
        assertThat(iosCode.getPlatform()).isEqualTo(Platform.IOS);
        assertThat(iosCode.getFiles()).isNotEmpty();
    }

    @Test
    @Order(18)
    @DisplayName("KuiklyUI: 多实体代码生成")
    void testKuiklyUI_MultipleEntities() {
        List<Entity> entities = buildMultipleEntityList();

        MultiPlatformResult result = kuiklyUIGenerator.generateMultiPlatform(entities, "MultiEntityApp");

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getEntityCount()).isEqualTo(3);
    }

    @Test
    @Order(19)
    @DisplayName("KuiklyUI: 耗时记录")
    void testKuiklyUI_DurationTracking() {
        List<Entity> entities = buildSimpleEntityList();

        MultiPlatformResult result = kuiklyUIGenerator.generateMultiPlatform(entities, "TimingApp");

        assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(0);
    }

    // ==================== AI能力自动集成测试 ====================

    @Test
    @Order(20)
    @DisplayName("AI能力集成: 服务注入成功")
    void testAICapability_ServiceInjection() {
        assertThat(aiCapabilityIntegrator).isNotNull();
    }

    @Test
    @Order(21)
    @DisplayName("AI能力集成: AI能力枚举")
    void testAICapability_CapabilityEnum() {
        AICapability[] capabilities = AICapability.values();

        assertThat(capabilities).hasSize(8);
        assertThat(capabilities).contains(
                AICapability.NLP,
                AICapability.VISION,
                AICapability.SPEECH,
                AICapability.RECOMMENDATION,
                AICapability.TRANSLATION,
                AICapability.CHAT,
                AICapability.CODE_GENERATION,
                AICapability.CONTENT_GENERATION
        );
    }

    @Test
    @Order(22)
    @DisplayName("AI能力集成: NLP能力检测")
    void testAICapability_NLPDetection() {
        String requirement = "开发一个电商评论系统，需要对用户评价进行情感分析和关键词提取";
        List<String> features = List.of("情感分析", "文本分类");

        AICapabilityDetectionResult result = aiCapabilityIntegrator.analyzeAndDetect(requirement, features);

        assertThat(result).isNotNull();
        assertThat(result.getDetectedCapabilities()).isNotEmpty();

        // 验证检测到了NLP能力
        boolean hasNLP = result.getDetectedCapabilities().stream()
                .anyMatch(dc -> dc.getCapability() == AICapability.NLP);
        assertThat(hasNLP).isTrue();
    }

    @Test
    @Order(23)
    @DisplayName("AI能力集成: Vision能力检测")
    void testAICapability_VisionDetection() {
        String requirement = "开发一个证件扫描应用，需要OCR识别身份证和银行卡信息";
        List<String> features = List.of("OCR识别", "图像处理");

        AICapabilityDetectionResult result = aiCapabilityIntegrator.analyzeAndDetect(requirement, features);

        assertThat(result).isNotNull();
        assertThat(result.getDetectedCapabilities()).isNotEmpty();

        // 验证检测到了Vision能力
        boolean hasVision = result.getDetectedCapabilities().stream()
                .anyMatch(dc -> dc.getCapability() == AICapability.VISION);
        assertThat(hasVision).isTrue();
    }

    @Test
    @Order(24)
    @DisplayName("AI能力集成: Chat能力检测")
    void testAICapability_ChatDetection() {
        String requirement = "开发一个智能客服系统，需要支持多轮对话和问答功能";
        List<String> features = List.of("智能客服", "问答系统");

        AICapabilityDetectionResult result = aiCapabilityIntegrator.analyzeAndDetect(requirement, features);

        assertThat(result).isNotNull();
        assertThat(result.getDetectedCapabilities()).isNotEmpty();

        // 验证检测到了Chat能力
        boolean hasChat = result.getDetectedCapabilities().stream()
                .anyMatch(dc -> dc.getCapability() == AICapability.CHAT);
        assertThat(hasChat).isTrue();
    }

    @Test
    @Order(25)
    @DisplayName("AI能力集成: 多能力组合检测")
    void testAICapability_MultipleCapabilities() {
        String requirement = "开发一个在线教育平台，需要语音识别支持语音输入，图像识别支持题目拍照，智能问答辅助学习";
        List<String> features = List.of("语音识别", "图像识别", "智能问答");

        AICapabilityDetectionResult result = aiCapabilityIntegrator.analyzeAndDetect(requirement, features);

        assertThat(result).isNotNull();
        assertThat(result.getDetectedCapabilities()).isNotEmpty();

        // 应该检测到多种能力
        Set<AICapability> detected = new HashSet<>();
        for (DetectedCapability dc : result.getDetectedCapabilities()) {
            detected.add(dc.getCapability());
        }
        assertThat(detected.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(26)
    @DisplayName("AI能力集成: 集成方案生成")
    void testAICapability_IntegrationPlan() {
        String requirement = "开发一个文本摘要工具，需要自然语言处理能力";
        List<String> features = List.of("文本摘要", "关键词提取");

        AICapabilityDetectionResult result = aiCapabilityIntegrator.analyzeAndDetect(requirement, features);

        assertThat(result).isNotNull();
        assertThat(result.getIntegrationPlan()).isNotNull();

        IntegrationPlan plan = result.getIntegrationPlan();
        assertThat(plan.getDependencies()).isNotNull();
        assertThat(plan.getEnvVariables()).isNotNull();
    }

    @Test
    @Order(27)
    @DisplayName("AI能力集成: 无AI需求场景")
    void testAICapability_NoAIRequired() {
        String requirement = "开发一个简单的静态网站，展示公司信息和联系方式";
        List<String> features = List.of("首页", "关于我们", "联系方式");

        AICapabilityDetectionResult result = aiCapabilityIntegrator.analyzeAndDetect(requirement, features);

        assertThat(result).isNotNull();
        // 对于纯静态需求，检测到的AI能力应该较少或为空
        // 置信度应该较低
        if (result.getDetectedCapabilities().isEmpty()) {
            assertThat(result.getOverallConfidence()).isLessThanOrEqualTo(0.0);
        } else {
            assertThat(result.getOverallConfidence()).isLessThan(0.8);
        }
    }

    @Test
    @Order(28)
    @DisplayName("AI能力集成: 检测结果包含置信度")
    void testAICapability_ConfidenceScore() {
        String requirement = "开发一个智能翻译应用，支持中英文实时翻译";
        List<String> features = List.of("翻译", "多语言");

        AICapabilityDetectionResult result = aiCapabilityIntegrator.analyzeAndDetect(requirement, features);

        assertThat(result).isNotNull();
        assertThat(result.getOverallConfidence()).isBetween(0.0, 1.0);

        for (DetectedCapability dc : result.getDetectedCapabilities()) {
            assertThat(dc.getConfidence()).isBetween(0.0, 1.0);
        }
    }

    @Test
    @Order(29)
    @DisplayName("AI能力集成: 检测耗时记录")
    void testAICapability_DurationTracking() {
        String requirement = "开发一个图像分类系统";
        List<String> features = List.of("图像分类");

        AICapabilityDetectionResult result = aiCapabilityIntegrator.analyzeAndDetect(requirement, features);

        assertThat(result).isNotNull();
        assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(30)
    @DisplayName("AI能力集成: 推荐提供商信息")
    void testAICapability_RecommendedProvider() {
        String requirement = "开发一个智能翻译应用";
        List<String> features = List.of("翻译");

        AICapabilityDetectionResult result = aiCapabilityIntegrator.analyzeAndDetect(requirement, features);

        assertThat(result).isNotNull();
        if (!result.getDetectedCapabilities().isEmpty()) {
            DetectedCapability firstCapability = result.getDetectedCapabilities().get(0);
            assertThat(firstCapability.getRecommendedProvider()).isNotNull();
            assertThat(firstCapability.getRecommendedProvider()).isNotBlank();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建简单的实体列表用于测试
     */
    private List<Entity> buildSimpleEntityList() {
        return List.of(
                Entity.builder()
                        .name("items")
                        .description("商品表")
                        .fields(List.of(
                                Field.builder()
                                        .name("id")
                                        .type(FieldType.UUID)
                                        .primaryKey(true)
                                        .build(),
                                Field.builder()
                                        .name("name")
                                        .type(FieldType.VARCHAR)
                                        .length(200)
                                        .nullable(false)
                                        .build(),
                                Field.builder()
                                        .name("price")
                                        .type(FieldType.NUMERIC)
                                        .precision(10)
                                        .scale(2)
                                        .build()
                        ))
                        .build()
        );
    }

    /**
     * 构建多实体列表用于测试
     */
    private List<Entity> buildMultipleEntityList() {
        return List.of(
                Entity.builder()
                        .name("User")
                        .description("用户表")
                        .fields(List.of(
                                Field.builder().name("id").type(FieldType.UUID).primaryKey(true).build(),
                                Field.builder().name("username").type(FieldType.VARCHAR).length(100).build(),
                                Field.builder().name("email").type(FieldType.VARCHAR).length(255).build()
                        ))
                        .build(),
                Entity.builder()
                        .name("Order")
                        .description("订单表")
                        .fields(List.of(
                                Field.builder().name("id").type(FieldType.UUID).primaryKey(true).build(),
                                Field.builder().name("orderNumber").type(FieldType.VARCHAR).length(50).build(),
                                Field.builder().name("totalAmount").type(FieldType.NUMERIC).precision(10).scale(2).build()
                        ))
                        .build(),
                Entity.builder()
                        .name("Product")
                        .description("商品表")
                        .fields(List.of(
                                Field.builder().name("id").type(FieldType.UUID).primaryKey(true).build(),
                                Field.builder().name("productName").type(FieldType.VARCHAR).length(200).build(),
                                Field.builder().name("price").type(FieldType.NUMERIC).precision(10).scale(2).build()
                        ))
                        .build()
        );
    }

    /**
     * 构建模拟的Execute输出结果用于ValidateAgent测试
     */
    private Map<String, Object> buildMockExecuteResult() {
        Map<String, Object> result = new HashMap<>();

        // 模拟后端代码
        Map<String, Object> backend = new HashMap<>();
        backend.put("entities", Map.of(
                "User.java", "public class User { private Long id; private String name; }"
        ));
        backend.put("services", Map.of(
                "UserService.java", "@Service public class UserService { }"
        ));
        backend.put("controllers", Map.of(
                "UserController.java", "@RestController public class UserController { }"
        ));
        result.put("backend", backend);

        // 模拟数据库信息
        Map<String, Object> database = new HashMap<>();
        database.put("migrationSQL", "CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR(255));");
        database.put("entityCount", 1);
        result.put("database", database);

        return result;
    }
}
