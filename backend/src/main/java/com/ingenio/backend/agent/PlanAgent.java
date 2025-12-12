package com.ingenio.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.dto.IntentClassificationResult;
import com.ingenio.backend.agent.dto.PlanResult;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * Plan Agent - 需求规划Agent
 * 负责将用户的自然语言需求转化为结构化的功能模块列表
 * 使用DeepSeek API进行需求分析和规划
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanAgent {

    private final IntentClassifier intentClassifier;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    /**
     * 系统提示词：定义PlanAgent的角色和输出格式
     */
    private static final String SYSTEM_PROMPT = """
        你是一个专业的产品需求分析师，擅长将用户的模糊需求转化为清晰的功能模块设计。
        你还精通AI技术应用，能够识别需求中是否需要AI能力。

        你的任务：
        1. 深入理解用户的业务需求和使用场景
        2. 将需求拆解为独立的功能模块
        3. 评估每个模块的复杂度和优先级
        4. 识别模块之间的依赖关系
        5. 建议合适的数据模型和页面结构
        6. **识别是否需要AI能力，以及需要哪些AI能力**

        AI能力识别指南：
        - 聊天机器人/智能客服 → CHATBOT
        - 知识库问答/智能搜索 → RAG（检索增强生成）
        - 文本摘要/内容生成 → SUMMARIZATION / CONTENT_GENERATION
        - 图片识别/物体检测 → IMAGE_RECOGNITION
        - 语音识别/语音合成 → SPEECH_TO_TEXT / TEXT_TO_SPEECH
        - 情感分析/意图识别 → SENTIMENT_ANALYSIS
        - 多语言翻译 → TRANSLATION
        - 代码补全/生成 → CODE_COMPLETION

        AI复杂度评估：
        - SIMPLE：单一AI能力，如纯聊天机器人 → 推荐 DIRECT_API（直接调用七牛云/阿里云API）
        - MEDIUM：多个AI能力组合，如RAG知识库（检索+生成） → 推荐 DIFY_WORKFLOW（Dify工作流编排）
        - COMPLEX：复杂Agent系统，如Multi-Agent协作 → 推荐 LANGCHAIN（LangChain4j自定义）

        输出格式要求（严格JSON格式）：
        {
          "modules": [
            {
              "name": "模块名称",
              "description": "模块描述",
              "priority": "high|medium|low",
              "complexity": 1-10,
              "dependencies": ["依赖的其他模块"],
              "dataModels": ["需要的数据模型"],
              "pages": ["需要的页面"]
            }
          ],
          "complexityScore": 1-10,
          "reasoning": "推理过程说明",
          "suggestedTechStack": ["Kotlin Multiplatform", "KuiklyUI", ...],
          "estimatedHours": 预估小时数,
          "recommendations": "额外建议",
          "aiCapability": {
            "needsAI": true|false,
            "capabilities": [
              {
                "type": "CHATBOT|QA_SYSTEM|RAG|...",
                "description": "能力描述",
                "useCase": "使用场景",
                "estimatedTokens": 1500,
                "apiEndpoint": "/api/chat",
                "requiredConfigs": ["QINIU_API_KEY", "AI_MODEL"]
              }
            ],
            "complexity": "SIMPLE|MEDIUM|COMPLEX",
            "recommendedApproach": "DIRECT_API|DIFY_WORKFLOW|LANGCHAIN",
            "reasoning": "为什么需要这些AI能力",
            "confidence": 0.95
          }
        }

        注意：
        - 模块拆分要遵循单一职责原则
        - 复杂度评估要考虑技术难度和工作量
        - 优先级要基于业务价值和用户体验
        - **如果需求中不涉及AI能力，设置 needsAI 为 false 或不包含 aiCapability 字段**
        - **准确识别AI能力类型，避免误判**
        """;

    /**
     * 执行需求规划
     *
     * @param userRequirement 用户需求（自然语言）
     * @return 规划结果
     * @throws BusinessException 当规划失败时抛出
     */
    public PlanResult plan(String userRequirement) {
        if (userRequirement == null || userRequirement.trim().isEmpty()) {
            log.error("PlanAgent执行失败: 用户需求不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户需求不能为空");
        }

        try {
            log.info("PlanAgent开始执行: userRequirement={}", userRequirement);

            // V2.0 新增：Step 1 - 意图识别（Plan阶段第一步）
            log.info("PlanAgent Step 1: 开始意图识别");
            IntentClassificationResult intentResult = intentClassifier.classifyIntent(userRequirement);
            log.info("PlanAgent Step 1: 意图识别完成 - intent={}, confidence={:.2f}%",
                    intentResult.getIntent(), intentResult.getConfidence() * 100);

            // 构建ChatClient
            ChatClient chatClient = ChatClient.builder(chatModel).build();

            // 构建用户提示词
            String userPrompt = String.format("""
                请分析以下用户需求，并生成结构化的功能模块规划：

                用户需求：
                %s

                请严格按照JSON格式输出，不要包含任何其他文字。
                """, userRequirement);

            // 调用AI模型
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();

            log.debug("PlanAgent AI响应: {}", response);

            // 解析JSON响应
            PlanResult planResult = parseResponse(response);

            // V2.0 新增：将意图识别结果设置到PlanResult中
            planResult.setIntentClassificationResult(intentResult);
            log.debug("PlanAgent: 意图识别结果已设置到PlanResult");

            log.info("PlanAgent执行成功: modules={}, complexityScore={}, intent={}",
                    planResult.getModules().size(), planResult.getComplexityScore(),
                    intentResult.getIntent());

            return planResult;

        } catch (Exception e) {
            log.error("PlanAgent执行失败: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.AGENT_PLAN_FAILED, "需求规划失败: " + e.getMessage());
        }
    }

    /**
     * 解析AI响应为PlanResult对象
     *
     * @param response AI响应文本
     * @return PlanResult对象
     */
    private PlanResult parseResponse(String response) {
        try {
            // 提取JSON部分（去除可能的markdown代码块标记）
            String jsonContent = extractJson(response);

            // 解析JSON
            return objectMapper.readValue(jsonContent, PlanResult.class);

        } catch (Exception e) {
            log.error("解析PlanAgent响应失败: response={}, error={}", response, e.getMessage());
            throw new BusinessException(ErrorCode.AGENT_PLAN_FAILED, "解析AI响应失败: " + e.getMessage());
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
