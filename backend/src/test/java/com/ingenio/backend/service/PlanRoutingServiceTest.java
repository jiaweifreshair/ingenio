package com.ingenio.backend.service;

import com.ingenio.backend.agent.IntentClassifier;
import com.ingenio.backend.agent.dto.IntentClassificationResult;
import com.ingenio.backend.agent.dto.RequirementIntent;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.dto.request.Generate7StylesRequest;
import com.ingenio.backend.dto.request.OpenLovableGenerateRequest;
import com.ingenio.backend.dto.response.Generate7StylesResponse;
import com.ingenio.backend.dto.response.OpenLovableGenerateResponse;
import com.ingenio.backend.dto.response.StylePreviewResponse;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.entity.IndustryTemplateEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PlanRoutingService单元测试
 *
 * 测试覆盖：
 * 1. 三分支路由（CLONE/DESIGN/HYBRID）
 * 2. 意图识别集成
 * 3. 模板匹配
 * 4. 风格选择
 * 5. 设计确认
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanRoutingService - Plan阶段智能路由服务测试")
class PlanRoutingServiceTest {

    @Mock
    private IntentClassifier intentClassifier;

    @Mock
    private IndustryTemplateMatchingService templateMatchingService;

    @Mock
    private SuperDesignService superDesignService;

    @Mock
    private OpenLovableService openLovableService;

    @Mock
    private AppSpecMapper appSpecMapper;

    @InjectMocks
    private PlanRoutingService planRoutingService;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("克隆分支路由测试")
    class CloneBranchRouting {

        @Test
        @DisplayName("当用户需求为克隆网站时应路由到克隆分支")
        void shouldRouteToCloneBranchWhenIntentIsClone() {
            // Given
            String userRequirement = "我想做一个类似airbnb.com的民宿预订网站";
            List<String> urls = Arrays.asList("airbnb.com");
            List<String> keywords = Arrays.asList("民宿", "预订", "airbnb");

            IntentClassificationResult intentResult = IntentClassificationResult.builder()
                    .intent(RequirementIntent.CLONE_EXISTING_WEBSITE)
                    .confidence(0.95)
                    .reasoning("用户明确提到参考airbnb网站")
                    .referenceUrls(urls)
                    .extractedKeywords(keywords)
                    .build();

            OpenLovableGenerateResponse openLovableResponse = OpenLovableGenerateResponse.builder()
                    .previewUrl("https://e2b.example.com/preview/123")
                    .sandboxId("sandbox-123")
                    .provider("e2b")
                    .completedAt(Instant.now())
                    .success(true)
                    .build();

            when(intentClassifier.classifyIntent(userRequirement)).thenReturn(intentResult);
            when(templateMatchingService.matchTemplates(eq(keywords), eq("airbnb.com"), eq(3)))
                    .thenReturn(Collections.emptyList());
            when(openLovableService.generatePrototype(any(OpenLovableGenerateRequest.class)))
                    .thenReturn(openLovableResponse);
            doReturn(1).when(appSpecMapper).insert(any(AppSpecEntity.class));
            doReturn(1).when(appSpecMapper).updateById(any(AppSpecEntity.class));

            // When
            PlanRoutingResult result = planRoutingService.route(userRequirement, tenantId, userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getIntent()).isEqualTo(RequirementIntent.CLONE_EXISTING_WEBSITE);
            assertThat(result.getBranch()).isEqualTo(PlanRoutingService.RoutingBranch.CLONE);
            assertThat(result.isPrototypeGenerated()).isTrue();
            assertThat(result.getPrototypeUrl()).isEqualTo("https://e2b.example.com/preview/123");
            assertThat(result.getNextAction()).contains("确认设计");
            assertThat(result.isRequiresUserConfirmation()).isTrue();
            assertThat(result.getConfidence()).isEqualTo(0.95);

            // 验证OpenLovable请求参数
            ArgumentCaptor<OpenLovableGenerateRequest> requestCaptor =
                    ArgumentCaptor.forClass(OpenLovableGenerateRequest.class);
            verify(openLovableService).generatePrototype(requestCaptor.capture());
            OpenLovableGenerateRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getNeedsCrawling()).isTrue();
            assertThat(capturedRequest.getReferenceUrls()).contains("airbnb.com");
        }

        @Test
        @DisplayName("当克隆分支缺少参考URL时应抛出异常")
        void shouldThrowExceptionWhenCloneBranchMissingUrl() {
            // Given
            String userRequirement = "帮我克隆一个网站";
            IntentClassificationResult intentResult = IntentClassificationResult.builder()
                    .intent(RequirementIntent.CLONE_EXISTING_WEBSITE)
                    .confidence(0.85)
                    .reasoning("用户提到克隆但未提供URL")
                    .referenceUrls(Collections.emptyList())
                    .extractedKeywords(Arrays.asList("克隆", "网站"))
                    .build();

            when(intentClassifier.classifyIntent(userRequirement)).thenReturn(intentResult);
            when(templateMatchingService.matchTemplates(anyList(), isNull(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(appSpecMapper.insert(any(AppSpecEntity.class))).thenReturn(1);

            // When & Then
            assertThatThrownBy(() -> planRoutingService.route(userRequirement, tenantId, userId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("参考网站URL");
        }
    }

    @Nested
    @DisplayName("设计分支路由测试")
    class DesignBranchRouting {

        @Test
        @DisplayName("当用户需求为从零设计时应路由到设计分支并生成7风格")
        void shouldRouteToDesignBranchAndGenerate7Styles() {
            // Given
            String userRequirement = "创建一个在线课程平台";
            List<String> keywords = Arrays.asList("在线课程", "平台", "教育");

            IntentClassificationResult intentResult = IntentClassificationResult.builder()
                    .intent(RequirementIntent.DESIGN_FROM_SCRATCH)
                    .confidence(0.92)
                    .reasoning("用户要求创建新平台，无参考网站")
                    .referenceUrls(Collections.emptyList())
                    .extractedKeywords(keywords)
                    .build();

            List<StylePreviewResponse> styles = Arrays.asList(
                    StylePreviewResponse.builder()
                            .style("modern_minimal")
                            .htmlContent("<html>Modern Minimal Preview</html>")
                            .generationTime(1500L)
                            .aiGenerated(true)
                            .build(),
                    StylePreviewResponse.builder()
                            .style("vibrant_fashion")
                            .htmlContent("<html>Vibrant Fashion Preview</html>")
                            .generationTime(1600L)
                            .aiGenerated(true)
                            .build()
            );

            Generate7StylesResponse stylesResponse = Generate7StylesResponse.builder()
                    .styles(styles)
                    .success(true)
                    .totalGenerationTime(10500L)
                    .build();

            when(intentClassifier.classifyIntent(userRequirement)).thenReturn(intentResult);
            when(templateMatchingService.matchTemplates(eq(keywords), isNull(), eq(3)))
                    .thenReturn(Collections.emptyList());
            when(superDesignService.generate7StyleHTMLPreviews(any(Generate7StylesRequest.class)))
                    .thenReturn(stylesResponse);
            doReturn(1).when(appSpecMapper).insert(any(AppSpecEntity.class));
            doReturn(1).when(appSpecMapper).updateById(any(AppSpecEntity.class));

            // When
            PlanRoutingResult result = planRoutingService.route(userRequirement, tenantId, userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getIntent()).isEqualTo(RequirementIntent.DESIGN_FROM_SCRATCH);
            assertThat(result.getBranch()).isEqualTo(PlanRoutingService.RoutingBranch.DESIGN);
            assertThat(result.isPrototypeGenerated()).isFalse();
            assertThat(result.getStyleVariants()).hasSize(2);
            assertThat(result.getNextAction()).contains("选择");
            assertThat(result.isRequiresUserConfirmation()).isTrue();

            // 验证风格变体内容
            Map<String, Object> firstStyle = result.getStyleVariants().get(0);
            assertThat(firstStyle.get("styleId")).isEqualTo("modern_minimal");
            assertThat(firstStyle.get("styleName")).isEqualTo("现代极简");
            assertThat(firstStyle.get("previewHtml")).isEqualTo("<html>Modern Minimal Preview</html>");
        }

        @Test
        @DisplayName("设计分支应正确转换风格代码到中文名称")
        void shouldConvertStyleCodeToChineseName() {
            // Given
            String userRequirement = "设计一个社交应用";

            IntentClassificationResult intentResult = IntentClassificationResult.builder()
                    .intent(RequirementIntent.DESIGN_FROM_SCRATCH)
                    .confidence(0.88)
                    .referenceUrls(Collections.emptyList())
                    .extractedKeywords(Arrays.asList("社交", "应用"))
                    .build();

            List<StylePreviewResponse> allStyles = Arrays.asList(
                    StylePreviewResponse.builder().style("modern_minimal").htmlContent("A").generationTime(100L).aiGenerated(true).build(),
                    StylePreviewResponse.builder().style("vibrant_fashion").htmlContent("B").generationTime(100L).aiGenerated(true).build(),
                    StylePreviewResponse.builder().style("classic_professional").htmlContent("C").generationTime(100L).aiGenerated(true).build(),
                    StylePreviewResponse.builder().style("future_tech").htmlContent("D").generationTime(100L).aiGenerated(true).build(),
                    StylePreviewResponse.builder().style("immersive_3d").htmlContent("E").generationTime(100L).aiGenerated(true).build(),
                    StylePreviewResponse.builder().style("gamified").htmlContent("F").generationTime(100L).aiGenerated(true).build(),
                    StylePreviewResponse.builder().style("natural_flow").htmlContent("G").generationTime(100L).aiGenerated(true).build()
            );

            Generate7StylesResponse response = Generate7StylesResponse.builder()
                    .styles(allStyles)
                    .success(true)
                    .build();

            when(intentClassifier.classifyIntent(userRequirement)).thenReturn(intentResult);
            when(templateMatchingService.matchTemplates(anyList(), isNull(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(superDesignService.generate7StyleHTMLPreviews(any())).thenReturn(response);
            doReturn(1).when(appSpecMapper).insert(any(AppSpecEntity.class));
            doReturn(1).when(appSpecMapper).updateById(any(AppSpecEntity.class));

            // When
            PlanRoutingResult result = planRoutingService.route(userRequirement, tenantId, userId);

            // Then
            assertThat(result.getStyleVariants()).hasSize(7);
            assertThat(result.getStyleVariants().get(0).get("styleName")).isEqualTo("现代极简");
            assertThat(result.getStyleVariants().get(1).get("styleName")).isEqualTo("活力时尚");
            assertThat(result.getStyleVariants().get(2).get("styleName")).isEqualTo("经典专业");
            assertThat(result.getStyleVariants().get(3).get("styleName")).isEqualTo("未来科技");
            assertThat(result.getStyleVariants().get(4).get("styleName")).isEqualTo("沉浸式3D");
            assertThat(result.getStyleVariants().get(5).get("styleName")).isEqualTo("游戏化设计");
            assertThat(result.getStyleVariants().get(6).get("styleName")).isEqualTo("自然流动");
        }
    }

    @Nested
    @DisplayName("混合分支路由测试")
    class HybridBranchRouting {

        @Test
        @DisplayName("当用户需求为克隆+定制时应路由到混合分支")
        void shouldRouteToHybridBranchWhenIntentIsHybrid() {
            // Given
            String userRequirement = "参考淘宝做一个电商网站，但要加入AI推荐功能";
            List<String> urls = Arrays.asList("taobao.com");

            IntentClassificationResult intentResult = IntentClassificationResult.builder()
                    .intent(RequirementIntent.HYBRID_CLONE_AND_CUSTOMIZE)
                    .confidence(0.89)
                    .reasoning("用户既有参考网站又有定制需求")
                    .referenceUrls(urls)
                    .extractedKeywords(Arrays.asList("电商", "AI推荐", "淘宝"))
                    .customizationRequirement("加入AI推荐功能")
                    .build();

            OpenLovableGenerateResponse response = OpenLovableGenerateResponse.builder()
                    .previewUrl("https://e2b.example.com/preview/456")
                    .sandboxId("sandbox-456")
                    .provider("e2b")
                    .completedAt(Instant.now())
                    .success(true)
                    .build();

            when(intentClassifier.classifyIntent(userRequirement)).thenReturn(intentResult);
            when(templateMatchingService.matchTemplates(anyList(), anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());
            when(openLovableService.generatePrototype(any())).thenReturn(response);
            doReturn(1).when(appSpecMapper).insert(any(AppSpecEntity.class));
            doReturn(1).when(appSpecMapper).updateById(any(AppSpecEntity.class));

            // When
            PlanRoutingResult result = planRoutingService.route(userRequirement, tenantId, userId);

            // Then
            assertThat(result.getBranch()).isEqualTo(PlanRoutingService.RoutingBranch.HYBRID);
            assertThat(result.isPrototypeGenerated()).isTrue();
            assertThat(result.getPrototypeUrl()).isEqualTo("https://e2b.example.com/preview/456");
            assertThat(result.getNextAction()).contains("定制化原型");

            // 验证请求包含定制化需求
            ArgumentCaptor<OpenLovableGenerateRequest> captor =
                    ArgumentCaptor.forClass(OpenLovableGenerateRequest.class);
            verify(openLovableService).generatePrototype(captor.capture());
            assertThat(captor.getValue().getUserRequirement()).contains("AI推荐");
            assertThat(captor.getValue().getTimeoutSeconds()).isEqualTo(120);
        }
    }

    @Nested
    @DisplayName("风格选择测试")
    class StyleSelectionTest {

        @Test
        @DisplayName("用户选择风格后应生成原型")
        void shouldGeneratePrototypeAfterStyleSelection() {
            // Given
            UUID appSpecId = UUID.randomUUID();
            String selectedStyleId = "modern_minimal";

            Map<String, Object> styleVariant = new HashMap<>();
            styleVariant.put("styleId", "modern_minimal");
            styleVariant.put("styleName", "现代极简");
            styleVariant.put("previewHtml", "<html>Preview</html>");

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("styleVariants", Arrays.asList(styleVariant));

            Map<String, Object> specContent = new HashMap<>();
            specContent.put("userRequirement", "创建在线课程平台");

            AppSpecEntity appSpec = AppSpecEntity.builder()
                    .id(appSpecId)
                    .intentType(RequirementIntent.DESIGN_FROM_SCRATCH.name())
                    .metadata(metadata)
                    .specContent(specContent)
                    .build();

            OpenLovableGenerateResponse response = OpenLovableGenerateResponse.builder()
                    .previewUrl("https://e2b.example.com/preview/789")
                    .sandboxId("sandbox-789")
                    .provider("e2b")
                    .completedAt(Instant.now())
                    .success(true)
                    .build();

            when(appSpecMapper.selectById(appSpecId)).thenReturn(appSpec);
            when(openLovableService.generatePrototype(any())).thenReturn(response);
            doReturn(1).when(appSpecMapper).updateById(any(AppSpecEntity.class));

            // When
            PlanRoutingResult result = planRoutingService.selectStyleAndGeneratePrototype(appSpecId, selectedStyleId);

            // Then
            assertThat(result.isPrototypeGenerated()).isTrue();
            assertThat(result.getPrototypeUrl()).isEqualTo("https://e2b.example.com/preview/789");
            assertThat(result.getSelectedStyleId()).isEqualTo("modern_minimal");
            assertThat(result.getBranch()).isEqualTo(PlanRoutingService.RoutingBranch.DESIGN);

            // 验证AppSpec更新
            ArgumentCaptor<AppSpecEntity> captor = ArgumentCaptor.forClass(AppSpecEntity.class);
            verify(appSpecMapper).updateById(captor.capture());
            AppSpecEntity updatedAppSpec = captor.getValue();
            assertThat(updatedAppSpec.getSelectedStyle()).isEqualTo("modern_minimal");
            assertThat(updatedAppSpec.getFrontendPrototypeUrl()).isEqualTo("https://e2b.example.com/preview/789");
        }

        @Test
        @DisplayName("选择无效风格ID时应抛出异常")
        void shouldThrowExceptionWhenInvalidStyleId() {
            // Given
            UUID appSpecId = UUID.randomUUID();
            Map<String, Object> styleVariant = new HashMap<>();
            styleVariant.put("styleId", "modern_minimal");

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("styleVariants", Arrays.asList(styleVariant));

            AppSpecEntity appSpec = AppSpecEntity.builder()
                    .id(appSpecId)
                    .intentType(RequirementIntent.DESIGN_FROM_SCRATCH.name())
                    .metadata(metadata)
                    .build();

            when(appSpecMapper.selectById(appSpecId)).thenReturn(appSpec);

            // When & Then
            assertThatThrownBy(() -> planRoutingService.selectStyleAndGeneratePrototype(appSpecId, "invalid_style"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无效的风格ID");
        }

        @Test
        @DisplayName("非设计分支调用风格选择时应抛出异常")
        void shouldThrowExceptionWhenNotDesignBranch() {
            // Given
            UUID appSpecId = UUID.randomUUID();
            AppSpecEntity appSpec = AppSpecEntity.builder()
                    .id(appSpecId)
                    .intentType(RequirementIntent.CLONE_EXISTING_WEBSITE.name())
                    .build();

            when(appSpecMapper.selectById(appSpecId)).thenReturn(appSpec);

            // When & Then
            assertThatThrownBy(() -> planRoutingService.selectStyleAndGeneratePrototype(appSpecId, "modern_minimal"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());
        }
    }

    @Nested
    @DisplayName("设计确认测试")
    class DesignConfirmationTest {

        @Test
        @DisplayName("确认设计时应更新AppSpec状态")
        void shouldUpdateAppSpecWhenConfirmingDesign() {
            // Given
            UUID appSpecId = UUID.randomUUID();
            Map<String, Object> prototype = new HashMap<>();
            prototype.put("previewUrl", "https://example.com");

            AppSpecEntity appSpec = AppSpecEntity.builder()
                    .id(appSpecId)
                    .frontendPrototype(prototype)
                    .designConfirmed(false)
                    .build();

            when(appSpecMapper.selectById(appSpecId)).thenReturn(appSpec);
            doReturn(1).when(appSpecMapper).updateById(any(AppSpecEntity.class));

            // When
            planRoutingService.confirmDesign(appSpecId);

            // Then
            ArgumentCaptor<AppSpecEntity> captor = ArgumentCaptor.forClass(AppSpecEntity.class);
            verify(appSpecMapper).updateById(captor.capture());
            AppSpecEntity updatedAppSpec = captor.getValue();

            assertThat(updatedAppSpec.getDesignConfirmed()).isTrue();
            assertThat(updatedAppSpec.getDesignConfirmedAt()).isNotNull();
            assertThat(updatedAppSpec.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("原型未生成时确认设计应抛出异常")
        void shouldThrowExceptionWhenPrototypeNotGenerated() {
            // Given
            UUID appSpecId = UUID.randomUUID();
            AppSpecEntity appSpec = AppSpecEntity.builder()
                    .id(appSpecId)
                    .frontendPrototype(null)
                    .build();

            when(appSpecMapper.selectById(appSpecId)).thenReturn(appSpec);

            // When & Then
            assertThatThrownBy(() -> planRoutingService.confirmDesign(appSpecId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("原型尚未生成");
        }

        @Test
        @DisplayName("AppSpec不存在时确认设计应抛出异常")
        void shouldThrowExceptionWhenAppSpecNotFound() {
            // Given
            UUID appSpecId = UUID.randomUUID();
            when(appSpecMapper.selectById(appSpecId)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> planRoutingService.confirmDesign(appSpecId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.APPSPEC_NOT_FOUND.getCode());
        }
    }

    @Nested
    @DisplayName("意图识别失败处理")
    class IntentClassificationFailure {

        @Test
        @DisplayName("意图识别置信度过低时应抛出异常")
        void shouldThrowExceptionWhenLowConfidence() {
            // Given
            String userRequirement = "模糊的需求描述";
            IntentClassificationResult intentResult = IntentClassificationResult.builder()
                    .intent(RequirementIntent.DESIGN_FROM_SCRATCH)
                    .confidence(0.45)
                    .reasoning("需求描述不清晰")
                    .extractedKeywords(Collections.emptyList())
                    .build();

            when(intentClassifier.classifyIntent(userRequirement)).thenReturn(intentResult);

            // When & Then
            assertThatThrownBy(() -> planRoutingService.route(userRequirement, tenantId, userId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("置信度过低");
        }
    }

    @Nested
    @DisplayName("路由结果辅助方法测试")
    class RoutingResultHelperMethods {

        @Test
        @DisplayName("PlanRoutingResult应正确返回分支显示名称")
        void shouldReturnCorrectBranchDisplayName() {
            // Given
            PlanRoutingResult cloneResult = PlanRoutingResult.builder()
                    .branch(PlanRoutingService.RoutingBranch.CLONE)
                    .build();

            PlanRoutingResult designResult = PlanRoutingResult.builder()
                    .branch(PlanRoutingService.RoutingBranch.DESIGN)
                    .build();

            PlanRoutingResult hybridResult = PlanRoutingResult.builder()
                    .branch(PlanRoutingService.RoutingBranch.HYBRID)
                    .build();

            // Then
            assertThat(cloneResult.getBranchDisplayName()).isEqualTo("克隆分支");
            assertThat(designResult.getBranchDisplayName()).isEqualTo("设计分支");
            assertThat(hybridResult.getBranchDisplayName()).isEqualTo("混合分支");
        }

        @Test
        @DisplayName("PlanRoutingResult应正确判断分支类型")
        void shouldCorrectlyIdentifyBranchType() {
            // Given
            PlanRoutingResult cloneResult = PlanRoutingResult.builder()
                    .branch(PlanRoutingService.RoutingBranch.CLONE)
                    .build();

            PlanRoutingResult designResult = PlanRoutingResult.builder()
                    .branch(PlanRoutingService.RoutingBranch.DESIGN)
                    .build();

            // Then
            assertThat(cloneResult.isCloneBranch()).isTrue();
            assertThat(cloneResult.isDesignBranch()).isFalse();
            assertThat(cloneResult.isHybridBranch()).isFalse();

            assertThat(designResult.isDesignBranch()).isTrue();
            assertThat(designResult.isCloneBranch()).isFalse();
        }
    }
}
