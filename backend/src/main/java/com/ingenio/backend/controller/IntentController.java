package com.ingenio.backend.controller;

import com.ingenio.backend.agent.IntentClassifier;
import com.ingenio.backend.agent.dto.IntentClassificationResult;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.dto.request.IntentClassifyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 意图识别Controller
 * V2.0新三Agent架构核心功能：分析用户需求，识别意图类型（克隆/设计/混合）
 *
 * <p>核心功能：
 * <ul>
 *   <li>POST /api/v1/intent/classify - 识别用户需求的意图类型</li>
 * </ul>
 *
 * <p>意图类型：
 * <ul>
 *   <li>CLONE_EXISTING_WEBSITE - 克隆已有网站（跳过SuperDesign）</li>
 *   <li>DESIGN_FROM_SCRATCH - 从零设计（使用SuperDesign 7风格）</li>
 *   <li>HYBRID_CLONE_AND_CUSTOMIZE - 混合模式（克隆+定制）</li>
 * </ul>
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-16 (Phase X.3)
 */
@Slf4j
@RestController
@RequestMapping("/v1/intent")
@RequiredArgsConstructor
@Tag(name = "意图识别", description = "V2.0意图识别API - 分析用户需求，智能路由到正确分支")
public class IntentController {

    private final IntentClassifier intentClassifier;

    /**
     * 识别用户需求的意图类型
     *
     * <p>用途：V2.0 Plan Agent的第一步，根据意图类型路由到不同分支
     *
     * <p>请求示例：
     * <pre>
     * POST /api/v1/intent/classify
     * Content-Type: application/json
     *
     * {
     *   "userRequirement": "创建一个类似airbnb的民宿预订平台"
     * }
     * </pre>
     *
     * <p>响应示例（CLONE意图）：
     * <pre>
     * {
     *   "code": "200",
     *   "message": "success",
     *   "data": {
     *     "intent": "CLONE_EXISTING_WEBSITE",
     *     "confidence": 0.95,
     *     "reasoning": "用户明确提到'类似airbnb'，属于克隆已有网站场景",
     *     "referenceUrls": ["https://airbnb.com"],
     *     "extractedKeywords": ["民宿", "预订", "airbnb"],
     *     "customizationRequirement": null,
     *     "suggestedNextAction": "跳过SuperDesign，直接使用OpenLovable-CN爬取airbnb.com生成原型",
     *     "warnings": []
     *   }
     * }
     * </pre>
     *
     * <p>响应示例（DESIGN意图）：
     * <pre>
     * {
     *   "code": "200",
     *   "message": "success",
     *   "data": {
     *     "intent": "DESIGN_FROM_SCRATCH",
     *     "confidence": 0.92,
     *     "reasoning": "用户需求明确但无参考对象，需要从零设计",
     *     "referenceUrls": [],
     *     "extractedKeywords": ["电商", "商品展示", "购物车", "支付"],
     *     "customizationRequirement": null,
     *     "suggestedNextAction": "使用SuperDesign生成7种风格的设计方案供用户选择",
     *     "warnings": []
     *   }
     * }
     * </pre>
     *
     * @param request 意图识别请求（包含userRequirement字段）
     * @return 意图识别结果（包含intent、confidence、referenceUrls等）
     * @throws BusinessException 当用户需求为空或AI识别失败时抛出
     */
    @PostMapping("/classify")
    @Operation(summary = "识别用户需求意图", description = "使用Qwen-Max模型分析用户需求，识别意图类型（克隆/设计/混合）")
    public Result<IntentClassificationResult> classifyIntent(
            @Valid @RequestBody IntentClassifyRequest request
    ) {
        String userRequirement = request.getUserRequirement();
        String complexityHint = request.getComplexityHint();
        String techStackHint = request.getTechStackHint();

        log.info("接收意图识别请求: userRequirement=[{}], complexityHint=[{}], techStackHint=[{}]",
                userRequirement, complexityHint, techStackHint);

        // V2.0增强：如果用户提供了复杂度提示，增强需求描述
        String enhancedRequirement = enhanceRequirementWithHint(userRequirement, complexityHint, techStackHint);

        try {
            // 调用IntentClassifier进行意图识别（使用增强后的需求）
            IntentClassificationResult result = intentClassifier.classifyIntent(enhancedRequirement);

            // 检查识别是否成功
            if (!result.isSuccessful()) {
                log.warn("意图识别失败或置信度过低: intent={}, confidence={}",
                        result.getIntent(), result.getConfidence());

                // 记录警告信息
                if (result.hasWarnings()) {
                    log.warn("意图识别警告: {}", result.getWarnings());
                }

                // 置信度过低，建议人工审核
                if (result.getConfidence() != null && result.getConfidence() < 0.5) {
                    result.addWarning("置信度低于50%，建议人工审核确认意图");
                }
            }

            // 如果请求不包含调试信息，清除调试字段
            if (request.getIncludeDebugInfo() == null || !request.getIncludeDebugInfo()) {
                result.setPromptUsed(null);
                result.setRawResponse(null);
            }

            log.info("意图识别成功: intent={}, confidence={:.2f}%, referenceUrls={}",
                    result.getIntent(),
                    result.getConfidence() * 100,
                    result.getReferenceUrls());

            return Result.success(result);

        } catch (BusinessException e) {
            // 业务异常直接抛出
            log.error("意图识别业务异常: {}", e.getMessage());
            return Result.error(e.getCode(), e.getMessage());

        } catch (Exception e) {
            // 其他异常转换为系统错误
            log.error("意图识别系统异常", e);
            return Result.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 识别用户需求的意图类型（流式SSE版本）
     *
     * <p>用途：V2.0 Plan Agent的第一步，流式返回分析过程和最终结果
     * <p>SSE事件：
     * <ul>
     *   <li>thinking: AI思考过程片段（string）</li>
     *   <li>complete: 最终结果（IntentClassificationResult JSON）</li>
     *   <li>error: 错误信息（string）</li>
     * </ul>
     *
     * @param request 意图识别请求
     * @return SseEmitter对象
     */
    @PostMapping(value = "/classify/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "识别用户需求意图(流式)", description = "使用SSE流式返回AI分析过程和最终结果")
    public SseEmitter classifyIntentStream(@Valid @RequestBody IntentClassifyRequest request) {
        String userRequirement = request.getUserRequirement();
        String complexityHint = request.getComplexityHint();
        String techStackHint = request.getTechStackHint();

        log.info("接收流式意图识别请求: userRequirement=[{}], complexityHint=[{}], techStackHint=[{}]",
                userRequirement, complexityHint, techStackHint);

        // 增强需求描述
        String enhancedRequirement = enhanceRequirementWithHint(userRequirement, complexityHint, techStackHint);

        // 创建SseEmitter (超时时间设置为120秒)
        SseEmitter emitter = new SseEmitter(120000L);

        // 设置回调
        emitter.onCompletion(() -> log.debug("SSE连接完成"));
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时");
            emitter.complete();
        });
        emitter.onError((e) -> log.error("SSE连接错误", e));

        // 异步调用IntentClassifier
        try {
            intentClassifier.classifyIntentStream(enhancedRequirement, emitter);
        } catch (Exception e) {
            log.error("启动流式识别失败", e);
            try {
                emitter.send(SseEmitter.event().name("error").data("启动失败: " + e.getMessage()));
                // SSE场景避免 completeWithError 触发异常派发，使用正常complete关闭连接
                emitter.complete();
            } catch (Exception ex) {
                // ignore
            }
        }

        return emitter;
    }

    /**
     * 健康检查接口
     * 用于验证IntentController和IntentClassifier是否正常工作
     *
     * @return 健康状态信息
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "验证意图识别服务是否正常")
    public Result<String> health() {
        log.debug("执行健康检查");
        return Result.success("IntentController is healthy", null);
    }

    /**
     * 根据用户预选的复杂度和技术栈提示增强需求描述
     *
     * V2.0新增：将用户在首页选择的分类信息融入需求描述，
     * 帮助AI更准确地进行意图识别和技术栈推荐
     *
     * @param userRequirement 原始用户需求
     * @param complexityHint 用户预选的复杂度分类（可选）
     * @param techStackHint 用户预选的技术栈提示（可选）
     * @return 增强后的需求描述
     */
    private String enhanceRequirementWithHint(String userRequirement, String complexityHint, String techStackHint) {
        if ((complexityHint == null || complexityHint.isEmpty()) &&
            (techStackHint == null || techStackHint.isEmpty())) {
            // 没有任何提示，直接返回原始需求
            return userRequirement;
        }

        StringBuilder enhanced = new StringBuilder(userRequirement);
        enhanced.append("\n\n[用户预选信息]");

        // 添加复杂度分类提示
        if (complexityHint != null && !complexityHint.isEmpty()) {
            String complexityDescription = switch (complexityHint) {
                case "SIMPLE" -> "多端套壳应用（H5+WebView），适用于内容展示、表单、列表类应用，无原生功能需求";
                case "MEDIUM" -> "纯Web应用（React+Supabase），仅在浏览器运行，适用于SaaS或Dashboard类应用";
                case "COMPLEX" -> "企业级应用（React+Spring Boot），复杂业务逻辑，多实体关联系统";
                case "NEEDS_CONFIRMATION" -> "原生跨端应用（Kuikly），需要相机/GPS/蓝牙等原生能力";
                default -> complexityHint;
            };
            enhanced.append("\n- 复杂度分类: ").append(complexityDescription);
        }

        // 添加技术栈提示
        if (techStackHint != null && !techStackHint.isEmpty()) {
            enhanced.append("\n- 技术栈偏好: ").append(techStackHint);
        }

        enhanced.append("\n请在分析时优先考虑用户预选的分类和技术栈。");

        String result = enhanced.toString();
        log.debug("需求增强完成 - 原始长度: {}, 增强后长度: {}", userRequirement.length(), result.length());

        return result;
    }
}
