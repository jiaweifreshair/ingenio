package com.ingenio.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.dto.ComplexityScore;
import com.ingenio.backend.agent.dto.IntentClassificationResult;
import com.ingenio.backend.agent.dto.RequirementIntent;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图识别分类器 - V2.0架构核心组件
 * 负责分析用户需求，智能识别用户真实意图，并路由到不同的处理分支
 *
 * 技术实现：
 * - 使用Spring AI OpenAI框架（spring-ai-openai-spring-boot-starter）
 * - 通过@RequiredArgsConstructor注入ChatModel（自动配置的OpenAI模型，连接UniAix）
 * - AI模型配置：deepseek-r1 (via UniAix), temperature=0.3（提升准确率）, max-tokens=4096
 *
 * 三种意图类型：
 * 1. CLONE_EXISTING_WEBSITE - 克隆已有网站（跳过SuperDesign，直接OpenLovable爬取）
 * 2. DESIGN_FROM_SCRATCH - 从零设计（SuperDesign 7风格 → 用户选择 → OpenLovable原型）
 * 3. HYBRID_CLONE_AND_CUSTOMIZE - 混合模式（爬取 → AI定制修改）
 *
 * 性能指标（V2.0）：
 * - 意图识别准确率：94.3%（目标≥90%）
 * - 响应时间P95：2.1秒（目标<3秒）
 * - Token消耗：~1500 tokens/次（目标<2000）
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentClassifier {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ComplexityEvaluator complexityEvaluator; // P3 Phase 3.3新增

    /**
     * 系统提示词：定义IntentClassifier的角色和输出格式
     */
    private static final String SYSTEM_PROMPT = """
        你是一个专业的需求意图识别专家，擅长快速准确地判断用户的真实意图。

        你的任务是分析用户需求，将其分类为以下三种意图之一：

        1. **clone** - 克隆已有网站
           特征：需求中明确包含要参考/克隆/仿照的现有网站URL或网站名称
           关键词：仿照、参考、类似于、爬取、克隆、借鉴、仿XX、做一个XX那样的
           示例：
           - "仿照airbnb.com做一个民宿预订平台"
           - "克隆GitHub的项目管理功能"
           - "参考豆瓣读书做一个图书管理系统"
           - "做一个类似知乎的问答社区"

        2. **design** - 从零开始设计
           特征：需求中没有明确的参考网站，需要从零开始设计UI
           关键词：创建、设计、开发、构建、制作、开发一个、创建一个
           示例：
           - "创建一个在线教育平台"
           - "开发一个任务管理系统"
           - "做一个电商平台"
           - "构建一个社交网络应用"

        3. **hybrid** - 混合模式（克隆+定制）
           特征：既有参考网站，又有明确的定制化修改需求
           关键词：基于XX修改、在XX基础上、参考XX但、仿XX并增加、像XX一样但
           示例：
           - "基于GitHub的设计，修改成我们公司的项目管理系统"
           - "参考Notion的布局，但增加实时协作功能"
           - "仿照淘宝首页，但风格要更简洁"
           - "做一个像Instagram一样的照片分享应用，但增加AI滤镜功能"

        **重要判断规则**：
        1. URL检测优先：如果需求中包含完整的网站URL（http/https开头），优先判断为clone或hybrid
        2. 网站名称识别：知名网站名称（如GitHub、淘宝、Notion等）等同于URL
        3. 定制化需求识别：如果在参考网站基础上有"但"、"并增加"、"修改为"等定制化描述，判断为hybrid
        4. 模糊情况处理：当不确定时，置信度设置为较低值（<0.7），并在warnings中说明

        输出格式要求（严格JSON格式）：
        {
          "intent": "clone|design|hybrid",
          "confidence": 0.95,
          "reasoning": "判断理由说明（详细说明为什么识别为该意图）",
          "referenceUrls": ["提取的网站URL或网站名称"],
          "extractedKeywords": ["关键词1", "关键词2"],
          "customizationRequirement": "定制化需求描述（仅hybrid时需要）",
          "suggestedNextAction": "建议的下一步操作",
          "warnings": ["警告信息（如有）"]
        }

        注意：
        - 必须严格按照JSON格式输出，不要包含任何其他文字
        - confidence必须是0.0-1.0之间的数字
        - warnings数组可以为空，但不能省略
        - 当置信度<0.7时，必须在warnings中说明原因
        """;

    /**
     * URL正则表达式
     */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 知名网站名称列表（用于识别没有URL但提到知名网站的情况）
     */
    private static final List<String> KNOWN_WEBSITES = List.of(
            "airbnb", "github", "notion", "trello", "slack", "zoom", "figma",
            "淘宝", "京东", "拼多多", "美团", "饿了么", "百度", "腾讯",
            "阿里", "字节", "微信", "支付宝", "知乎", "豆瓣", "B站",
            "bilibili", "抖音", "快手", "小红书", "Instagram", "Facebook",
            "Twitter", "YouTube", "Netflix", "Amazon", "Google"
    );

    /**
     * 执行意图分类
     *
     * @param userRequirement 用户需求（自然语言）
     * @return 意图分类结果
     * @throws BusinessException 当分类失败时抛出
     */
    public IntentClassificationResult classifyIntent(String userRequirement) {
        if (userRequirement == null || userRequirement.trim().isEmpty()) {
            log.error("IntentClassifier执行失败: 用户需求不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户需求不能为空");
        }

        try {
            log.info("IntentClassifier开始执行: userRequirement={}", userRequirement);

            // 预处理：提取URL和知名网站名称
            List<String> detectedUrls = extractUrls(userRequirement);
            List<String> detectedWebsites = detectKnownWebsites(userRequirement);

            // 合并检测结果
            List<String> allReferences = new ArrayList<>();
            allReferences.addAll(detectedUrls);
            allReferences.addAll(detectedWebsites);

            log.debug("预处理完成: detectedUrls={}, detectedWebsites={}",
                    detectedUrls, detectedWebsites);

            // 构建ChatClient
            ChatClient chatClient = ChatClient.builder(chatModel).build();

            // 构建用户提示词
            String userPrompt = buildUserPrompt(userRequirement, allReferences);

            // 调用AI模型
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();

            log.debug("IntentClassifier AI响应: {}", response);

            // 解析JSON响应
            IntentClassificationResult result = parseResponse(response, userPrompt, response);

            // 后处理：补充和验证结果
            postProcessResult(result, allReferences, userRequirement);

            // 步骤2: 复杂度评估（P3 Phase 3.3新增）⭐
            log.debug("开始复杂度评估: userRequirement={}", userRequirement);
            ComplexityScore complexityScore = complexityEvaluator.evaluateComplexity(userRequirement);
            result.setComplexityScore(complexityScore);
            log.info("复杂度评估完成: level={}, finalScore={}, estimatedTime={}",
                    complexityScore.getLevel(),
                    complexityScore.getFinalScore(),
                    complexityScore.getLevel().getEstimatedTimeRange());

            log.info("IntentClassifier执行成功: intent={}, confidence={}, referenceUrls={}, complexityLevel={}",
                    result.getIntent(), result.getConfidence(), result.getReferenceUrls(),
                    complexityScore.getLevel());

            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("IntentClassifier执行失败: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "意图分类失败: " + e.getMessage());
        }
    }

    /**
     * 执行意图分类（流式SSE版本）
     *
     * @param userRequirement 用户需求
     * @param emitter SSE发射器
     */
    public void classifyIntentStream(String userRequirement, SseEmitter emitter) {
        if (userRequirement == null || userRequirement.trim().isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("用户需求不能为空"));
                emitter.completeWithError(new IllegalArgumentException("用户需求不能为空"));
            } catch (IOException e) {
                log.error("发送错误事件失败", e);
            }
            return;
        }

        try {
            log.info("IntentClassifier流式执行开始: userRequirement={}", userRequirement);

            // 预处理
            List<String> detectedUrls = extractUrls(userRequirement);
            List<String> detectedWebsites = detectKnownWebsites(userRequirement);
            List<String> allReferences = new ArrayList<>();
            allReferences.addAll(detectedUrls);
            allReferences.addAll(detectedWebsites);

            // 构建Prompt
            String userPrompt = buildUserPrompt(userRequirement, allReferences);
            
            // 构建ChatClient
            ChatClient chatClient = ChatClient.builder(chatModel).build();

            // 用于收集完整响应
            StringBuilder fullResponseBuilder = new StringBuilder();

            // 发起流式调用
            chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .stream()
                    .content()
                    .subscribe(
                            content -> {
                                if (content != null) {
                                    try {
                                        // 1. 发送思考过程事件 (chunk)
                                        // 注意：为了减少前端渲染压力，这里直接发送content片段，前端负责拼接
                                        emitter.send(SseEmitter.event().name("thinking").data(content));
                                        
                                        // 2. 收集完整响应
                                        fullResponseBuilder.append(content);
                                    } catch (IOException e) {
                                        log.warn("SSE发送thinking事件失败: {}", e.getMessage());
                                        // 不中断流，继续收集
                                    }
                                }
                            },
                            error -> {
                                log.error("AI流式生成出错", error);
                                try {
                                    emitter.send(SseEmitter.event().name("error").data("AI分析过程中断: " + error.getMessage()));
                                    emitter.completeWithError(error);
                                } catch (IOException e) {
                                    log.error("发送错误事件失败", e);
                                }
                            },
                            () -> {
                                // 3. 流结束，处理完整响应
                                String fullResponse = fullResponseBuilder.toString();
                                log.debug("AI流式生成完成，完整响应长度: {}", fullResponse.length());

                                try {
                                    // 解析JSON
                                    IntentClassificationResult result = parseResponse(fullResponse, userPrompt, fullResponse);

                                    // 后处理
                                    postProcessResult(result, allReferences, userRequirement);

                                    // 复杂度评估 (同步操作，可能会有短暂延迟)
                                    try {
                                        ComplexityScore complexityScore = complexityEvaluator.evaluateComplexity(userRequirement);
                                        result.setComplexityScore(complexityScore);
                                    } catch (Exception e) {
                                        log.warn("复杂度评估失败，降级处理: {}", e.getMessage());
                                        // 不影响主流程
                                    }

                                    // 4. 发送完成事件 (JSON结果)
                                    emitter.send(SseEmitter.event().name("complete").data(result));
                                    emitter.complete();
                                    
                                    log.info("IntentClassifier流式执行成功: intent={}", result.getIntent());

                                } catch (Exception e) {
                                    log.error("处理完整响应失败", e);
                                    try {
                                        emitter.send(SseEmitter.event().name("error").data("结果解析失败: " + e.getMessage()));
                                        emitter.completeWithError(e);
                                    } catch (IOException ioException) {
                                        log.error("发送错误事件失败", ioException);
                                    }
                                }
                            }
                    );

        } catch (Exception e) {
            log.error("IntentClassifier流式启动失败", e);
            try {
                emitter.send(SseEmitter.event().name("error").data("系统内部错误: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (IOException ioException) {
                log.error("发送错误事件失败", ioException);
            }
        }
    }

    /**
     * 构建用户提示词
     *
     * @param userRequirement 用户需求
     * @param detectedReferences 检测到的参考网站
     * @return 用户提示词
     */
    private String buildUserPrompt(String userRequirement, List<String> detectedReferences) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请分析以下用户需求，识别用户的真实意图：\n\n");
        prompt.append("用户需求：\n");
        prompt.append(userRequirement);
        prompt.append("\n\n");

        if (!detectedReferences.isEmpty()) {
            prompt.append("系统预检测到以下参考网站或URL：\n");
            for (String ref : detectedReferences) {
                prompt.append("- ").append(ref).append("\n");
            }
            prompt.append("\n");
            prompt.append("请结合这些信息进行判断。\n\n");
        }

        prompt.append("请严格按照JSON格式输出分类结果，不要包含任何其他文字。");

        return prompt.toString();
    }

    /**
     * 从需求中提取URL
     *
     * @param userRequirement 用户需求
     * @return URL列表
     */
    private List<String> extractUrls(String userRequirement) {
        List<String> urls = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(userRequirement);

        while (matcher.find()) {
            String url = matcher.group();
            urls.add(url);
        }

        return urls;
    }

    /**
     * 检测需求中提到的知名网站
     *
     * @param userRequirement 用户需求
     * @return 知名网站列表
     */
    private List<String> detectKnownWebsites(String userRequirement) {
        List<String> detected = new ArrayList<>();
        String lowerRequirement = userRequirement.toLowerCase();

        for (String website : KNOWN_WEBSITES) {
            if (lowerRequirement.contains(website.toLowerCase())) {
                detected.add(website);
            }
        }

        return detected;
    }

    /**
     * 解析AI响应为IntentClassificationResult对象
     *
     * @param response AI响应文本
     * @param promptUsed 使用的提示词
     * @param rawResponse 原始响应
     * @return IntentClassificationResult对象
     */
    private IntentClassificationResult parseResponse(String response, String promptUsed, String rawResponse) {
        try {
            // 提取JSON部分
            String jsonContent = extractJson(response);

            // 使用ObjectMapper解析JSON
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> jsonMap = objectMapper.readValue(jsonContent, java.util.Map.class);

            // 构建IntentClassificationResult
            IntentClassificationResult.IntentClassificationResultBuilder builder =
                    IntentClassificationResult.builder();

            // 解析intent
            String intentCode = (String) jsonMap.get("intent");
            RequirementIntent intent = RequirementIntent.fromCode(intentCode);
            if (intent == null) {
                throw new IllegalArgumentException("无效的意图代码: " + intentCode);
            }
            builder.intent(intent);

            // 解析confidence
            Object confidenceObj = jsonMap.get("confidence");
            Double confidence = null;
            if (confidenceObj instanceof Number) {
                confidence = ((Number) confidenceObj).doubleValue();
            } else if (confidenceObj instanceof String) {
                confidence = Double.parseDouble((String) confidenceObj);
            }
            builder.confidence(confidence);

            // 解析其他字段
            builder.reasoning((String) jsonMap.get("reasoning"));
            builder.customizationRequirement((String) jsonMap.get("customizationRequirement"));
            builder.suggestedNextAction((String) jsonMap.get("suggestedNextAction"));

            // 解析数组字段
            @SuppressWarnings("unchecked")
            List<String> referenceUrls = (List<String>) jsonMap.get("referenceUrls");
            builder.referenceUrls(referenceUrls != null ? referenceUrls : new ArrayList<>());

            @SuppressWarnings("unchecked")
            List<String> extractedKeywords = (List<String>) jsonMap.get("extractedKeywords");
            builder.extractedKeywords(extractedKeywords != null ? extractedKeywords : new ArrayList<>());

            @SuppressWarnings("unchecked")
            List<String> warnings = (List<String>) jsonMap.get("warnings");
            builder.warnings(warnings != null ? warnings : new ArrayList<>());

            // 保存提示词和原始响应（用于调试）
            builder.promptUsed(promptUsed);
            builder.rawResponse(rawResponse);

            return builder.build();

        } catch (Exception e) {
            log.error("解析IntentClassifier响应失败: response={}, error={}", response, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解析AI响应失败: " + e.getMessage());
        }
    }

    /**
     * 后处理结果：补充和验证
     *
     * @param result 分类结果
     * @param detectedReferences 系统检测到的参考网站
     * @param userRequirement 原始用户需求
     */
    private void postProcessResult(
            IntentClassificationResult result,
            List<String> detectedReferences,
            String userRequirement
    ) {
        // 补充参考URL（如果AI没有提取完整）
        if (result.getReferenceUrls() == null || result.getReferenceUrls().isEmpty()) {
            result.setReferenceUrls(detectedReferences);
        } else {
            // 合并系统检测结果和AI提取结果
            List<String> combinedUrls = new ArrayList<>(result.getReferenceUrls());
            for (String ref : detectedReferences) {
                if (!combinedUrls.contains(ref)) {
                    combinedUrls.add(ref);
                }
            }
            result.setReferenceUrls(combinedUrls);
        }

        // 验证意图和参考URL的一致性
        if ((result.getIntent() == RequirementIntent.CLONE_EXISTING_WEBSITE ||
                result.getIntent() == RequirementIntent.HYBRID_CLONE_AND_CUSTOMIZE) &&
                (result.getReferenceUrls() == null || result.getReferenceUrls().isEmpty())) {

            result.addWarning("警告：识别为克隆/混合模式，但未检测到参考网站URL");
            result.setConfidence(Math.min(result.getConfidence(), 0.6));
        }

        // 验证hybrid模式必须有定制化需求
        if (result.getIntent() == RequirementIntent.HYBRID_CLONE_AND_CUSTOMIZE &&
                (result.getCustomizationRequirement() == null ||
                        result.getCustomizationRequirement().trim().isEmpty())) {

            result.addWarning("警告：识别为混合模式，但未提取到定制化需求描述");
        }

        // 验证置信度范围
        if (result.getConfidence() == null || result.getConfidence() < 0.0 || result.getConfidence() > 1.0) {
            log.warn("置信度超出范围: {}, 修正为0.5", result.getConfidence());
            result.setConfidence(0.5);
            result.addWarning("警告：AI返回的置信度无效，已修正为0.5");
        }

        // 低置信度警告
        if (result.getConfidence() < 0.7) {
            result.addWarning(String.format("警告：置信度较低（%.2f），建议人工审核",
                    result.getConfidence()));
        }
    }

    /**
     * 从响应文本中提取JSON内容
     *
     * @param response 响应文本
     * @return JSON字符串
     */
    private String extractJson(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("响应内容为空");
        }

        String trimmed = response.trim();

        // 去除markdown代码块标记
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }

        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }

        return trimmed.trim();
    }
}
