package com.ingenio.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.dto.*;
import com.ingenio.backend.agent.impl.RequirementRefinerImpl;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * RequirementRefiner错误处理单元测试
 *
 * <p>目标：补充未覆盖的错误场景，提升错误处理测试覆盖率从25%到85%+</p>
 *
 * <p>测试策略：</p>
 * <ul>
 *   <li>使用Mockito Mock ChatClient的Fluent API链</li>
 *   <li>聚焦错误场景：AI超时、无效JSON、缺失字段</li>
 *   <li>验证错误处理逻辑的健壮性</li>
 * </ul>
 *
 * <p>架构设计：</p>
 * <ul>
 *   <li>使用Mockito扩展简化Mock配置</li>
 *   <li>Mock ChatClient的完整Fluent API调用链（Spring AI 1.1.0-M4）</li>
 *   <li>专注测试业务逻辑而非Spring AI框架</li>
 *   <li>使用LENIENT模式支持不同测试场景（有些测试在调用ChatClient前就抛异常）</li>
 * </ul>
 *
 * @author Ingenio Team
 * @since 2025-11-20
 * @see RequirementRefinerImpl
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RequirementRefiner错误处理单元测试")
class RequirementRefinerErrorHandlingTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private RequirementRefiner requirementRefiner;
    private ObjectMapper objectMapper;

    private String testRequirement;
    private RequirementIntent testIntent;
    private ComplexityScore testComplexityScore;

    @BeforeEach
    void setUp() {
        // 创建ObjectMapper
        objectMapper = new ObjectMapper();

        // 手动创建RequirementRefinerImpl并注入Mock的ChatClient
        requirementRefiner = new RequirementRefinerImpl(chatClient, objectMapper);

        // 准备测试数据
        testRequirement = "开发一个类似Airbnb的民宿预订平台";
        testIntent = RequirementIntent.CLONE_EXISTING_WEBSITE;

        testComplexityScore = ComplexityScore.builder()
                .level(ComplexityLevel.MEDIUM)
                .finalScore(65)
                .entityCountScore(60)
                .relationshipComplexityScore(70)
                .aiCapabilityScore(50)
                .technicalRiskScore(75)
                .extractedEntities(List.of("房东", "房客", "房源", "订单"))
                .keyTechnologies(List.of("地图服务", "支付接口"))
                .build();

        // 设置ChatClient的Fluent API调用链（Spring AI 1.1.0-M4）
        // 实际调用链: chatClient.prompt().system(...).user(...).call().content()
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.system(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
    }

    /**
     * 错误场景1：AI调用超时处理
     *
     * <p>测试目标：验证当ChatClient调用超时时，系统能够正确捕获并转换为BusinessException</p>
     *
     * <p>模拟场景：网络延迟或AI服务响应缓慢导致超时</p>
     * <p>预期结果：抛出BusinessException，错误码为SYSTEM_ERROR，错误信息包含"需求改写失败"</p>
     */
    @Test
    @DisplayName("错误场景1：AI调用超时处理")
    void testAICallTimeout() {
        // Given: 设置ChatClient抛出超时异常
        when(callResponseSpec.content())
                .thenThrow(new RuntimeException("Read timed out after 30000ms"));

        // When & Then: 调用refine方法应该抛出BusinessException
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> requirementRefiner.refine(testRequirement, testIntent, testComplexityScore)
        );

        // Then: 验证异常信息和错误码
        assertThat(exception.getMessage()).contains("需求改写失败");
        assertThat(exception.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
        assertThat(exception.getMessage()).contains("Read timed out");
    }

    /**
     * 错误场景2：AI返回无效JSON格式
     *
     * <p>测试目标：验证当AI返回纯文本（无JSON）时，系统能够正确处理</p>
     *
     * <p>模拟场景：AI返回普通文字描述而非结构化JSON</p>
     * <p>预期结果：抛出BusinessException，错误码为SYSTEM_ERROR，错误信息包含"解析AI响应失败"</p>
     */
    @Test
    @DisplayName("错误场景2：AI返回纯文本无JSON")
    void testInvalidJSONResponse() {
        // Given: 设置AI返回纯文本
        String pureTextResponse = "这是一段纯文本回复，没有任何JSON格式的内容。建议补充更多细节信息。";
        when(callResponseSpec.content()).thenReturn(pureTextResponse);

        // When & Then: 调用refine方法应该抛出BusinessException
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> requirementRefiner.refine(testRequirement, testIntent, testComplexityScore)
        );

        // Then: 验证JSON解析错误被正确处理
        assertThat(exception.getMessage()).contains("解析AI响应失败");
        assertThat(exception.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
    }

    /**
     * 错误场景3：JSON缺少必填字段
     *
     * <p>测试目标：验证当AI返回的JSON缺少必填字段时，系统能够正确处理</p>
     *
     * <p>模拟场景：AI返回不完整的JSON结构（仅包含部分字段）</p>
     * <p>预期结果：系统能够解析JSON并为缺失字段填充默认值（空列表）</p>
     *
     * <p>注意：这个场景测试的是"优雅降级"而非错误抛出</p>
     */
    @Test
    @DisplayName("错误场景3：JSON缺少必填字段的优雅降级")
    void testMissingRequiredFields() {
        // Given: 设置AI返回不完整JSON（只有refinedText，缺少lists）
        String incompleteJSON = """
                {
                  "refinedText": "开发民宿预订平台，包含房源管理、预订流程、支付功能",
                  "refiningReasoning": "补充了核心功能描述",
                  "needsUserConfirmation": false,
                  "refineType": "DETAIL_ENHANCEMENT"
                }
                """;
        when(callResponseSpec.content()).thenReturn(incompleteJSON);

        // When: 调用refine方法
        RefinedRequirement result = requirementRefiner.refine(
                testRequirement,
                testIntent,
                testComplexityScore
        );

        // Then: 验证能够正常解析，缺失字段使用默认值
        assertThat(result).isNotNull();
        assertThat(result.getRefinedText()).isEqualTo("开发民宿预订平台，包含房源管理、预订流程、支付功能");
        assertThat(result.getRefineType()).isEqualTo(RefinedRequirement.RefineType.DETAIL_ENHANCEMENT);
        assertThat(result.isNeedsUserConfirmation()).isFalse();

        // 验证缺失字段填充为空列表（Jackson默认行为）或通过postProcess补充
        assertThat(result.getCoreEntities()).isNotNull();
        assertThat(result.getMvpFeatures()).isNotNull();
        assertThat(result.getFutureFeatures()).isNotNull();
        assertThat(result.getTechnicalConstraints()).isNotNull();
        assertThat(result.getEntityRelationships()).isNotNull();
    }

    /**
     * 错误场景4：AI返回格式错误的JSON（语法错误）
     *
     * <p>测试目标：验证当AI返回无效JSON语法时，系统能够正确处理</p>
     *
     * <p>模拟场景：AI返回的JSON包含语法错误（缺少引号、逗号等）</p>
     * <p>预期结果：抛出BusinessException，错误码为SYSTEM_ERROR</p>
     */
    @Test
    @DisplayName("错误场景4：JSON语法错误")
    void testMalformedJSON() {
        // Given: 设置AI返回格式错误的JSON
        String malformedJSON = """
                {
                  "refinedText": "测试需求",
                  "coreEntities": ["实体1" "实体2"],
                  "refineType": DETAIL_ENHANCEMENT
                }
                """;
        when(callResponseSpec.content()).thenReturn(malformedJSON);

        // When & Then: 调用refine方法应该抛出BusinessException
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> requirementRefiner.refine(testRequirement, testIntent, testComplexityScore)
        );

        // Then: 验证JSON解析错误
        assertThat(exception.getMessage()).contains("解析AI响应失败");
        assertThat(exception.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
    }

    /**
     * 错误场景5：空输入验证
     *
     * <p>测试目标：验证参数验证逻辑</p>
     *
     * <p>注意：这个场景在RequirementRefinerE2ETest Scene 1已经覆盖，
     * 这里再次验证是为了确保单元测试的完整性</p>
     */
    @Test
    @DisplayName("错误场景5：空输入验证")
    void testEmptyInputValidation() {
        // When & Then: 空字符串应该抛出IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> requirementRefiner.refine("", testIntent, testComplexityScore)
        );

        assertThat(exception.getMessage()).contains("原始需求不能为空");

        // When & Then: null应该抛出IllegalArgumentException
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> requirementRefiner.refine(null, testIntent, testComplexityScore)
        );

        assertThat(exception.getMessage()).contains("原始需求不能为空");
    }
}
