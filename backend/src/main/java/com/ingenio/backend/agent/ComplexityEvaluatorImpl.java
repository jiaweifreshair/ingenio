package com.ingenio.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.dto.ComplexityLevel;
import com.ingenio.backend.agent.dto.ComplexityScore;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 需求复杂度评估器实现类 - P3增强功能
 * 使用Spring AI调用Qwen-Max模型，分析用户需求的四维度复杂度
 *
 * 技术实现：
 * - 使用Spring AI OpenAI框架（spring-ai-openai-spring-boot-starter）
 * - 通过@RequiredArgsConstructor注入ChatModel（自动配置的OpenAI模型，连接UniAix）
 * - AI模型配置：gpt-4o (via UniAix), temperature=0.3（提升准确率）, max-tokens=4096
 *
 * <p>四维度评分体系：</p>
 * <ul>
 *   <li>实体数量评分（30%权重）：基于核心实体数量</li>
 *   <li>关系复杂度评分（30%权重）：基于实体间关系复杂度</li>
 *   <li>AI能力需求评分（20%权重）：基于所需AI能力类型</li>
 *   <li>技术风险评分（20%权重）：基于技术选型风险</li>
 * </ul>
 *
 * <p>性能指标（目标）：</p>
 * <ul>
 *   <li>复杂度评估准确率：≥85%（目标≥90%）</li>
 *   <li>响应时间P95：<3秒</li>
 *   <li>Token消耗：<2000 tokens/次</li>
 * </ul>
 *
 * @author Ingenio Team
 * @version 2.0.1
 * @since 2025-11-20 P3 Phase 3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplexityEvaluatorImpl implements ComplexityEvaluator {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    /**
     * 系统提示词：定义ComplexityEvaluator的角色和输出格式
     */
    private static final String SYSTEM_PROMPT = """
        你是一个专业的软件需求复杂度评估专家，擅长快速准确地评估用户需求的技术复杂度。

        你的任务是分析用户需求，从以下四个维度进行评分（0-100分）：

        **维度1：实体数量评分（entityCountScore）**
        评估需求中涉及的核心业务实体数量：
        - 1-3个实体：10-30分（简单）
        - 4-8个实体：40-70分（中等）
        - 9个以上实体：75-100分（复杂）

        **维度2：关系复杂度评分（relationshipComplexityScore）**
        评估实体之间的关系复杂程度：
        - 简单CRUD，无关联：10-30分
        - 一对多/多对一关系：40-60分
        - 多对多/图状/递归/自引用关系：70-100分

        **维度3：AI能力需求评分（aiCapabilityScore）**
        评估所需的AI能力类型和复杂度：
        - 无AI需求：0-10分
        - 简单文本处理/搜索：20-40分
        - NLP/推荐/分类：50-70分
        - 多模态AI（视觉+语音+NLP组合）：80-100分

        **维度4：技术风险评分（technicalRiskScore）**
        评估技术选型和实现的风险程度：
        - 成熟技术栈（Spring Boot + PostgreSQL）：10-30分
        - 常规第三方集成（支付/地图/短信）：40-60分
        - 实时通讯/大数据/创新技术：70-100分

        **输出格式要求（严格JSON格式）**：
        {
          "entityCountScore": 60,
          "relationshipComplexityScore": 50,
          "aiCapabilityScore": 30,
          "technicalRiskScore": 40,
          "extractedEntities": ["用户", "商品", "订单"],
          "keyTechnologies": ["支付集成", "搜索引擎"],
          "aiCapabilities": ["智能推荐"],
          "riskFactors": ["第三方支付API依赖"],
          "reasoning": "详细的评估理由说明",
          "suggestedArchitecture": "建议的技术架构",
          "suggestedDevelopmentStrategy": "建议的开发策略"
        }

        **注意事项**：
        - 必须严格按照JSON格式输出，不要包含任何其他文字
        - 所有评分必须在0-100之间
        - extractedEntities数组必须包含所有识别的核心实体
        - reasoning必须详细说明每个维度的评分依据
        - 如果无AI需求，aiCapabilities可以为空数组
        - 如果无风险因素，riskFactors可以为空数组
        """;

    /**
     * 技术关键词列表（用于预处理识别技术需求）
     */
    private static final List<String> TECH_KEYWORDS = List.of(
            // 实时通讯
            "实时", "WebSocket", "即时通讯", "聊天", "消息推送",
            // AI能力
            "AI", "人工智能", "机器学习", "NLP", "自然语言", "图像识别", "语音", "推荐", "智能",
            // 大数据
            "大数据", "数据分析", "统计", "报表", "可视化",
            // 第三方集成
            "支付", "微信", "支付宝", "短信", "邮件", "地图", "定位",
            // 高并发
            "高并发", "秒杀", "抢购", "限流",
            // 复杂关系
            "权限", "角色", "组织架构", "审批流程", "工作流", "多租户"
    );

    /**
     * AI能力关键词列表
     */
    private static final List<String> AI_KEYWORDS = List.of(
            "AI", "人工智能", "机器学习", "深度学习", "NLP", "自然语言处理",
            "图像识别", "视觉", "语音识别", "语音合成", "TTS", "STT",
            "推荐系统", "智能推荐", "个性化推荐",
            "分类", "聚类", "预测", "智能", "自动化"
    );

    @Override
    public ComplexityScore evaluateComplexity(String userRequirement) throws BusinessException {
        if (userRequirement == null || userRequirement.trim().isEmpty()) {
            log.error("ComplexityEvaluator执行失败: 用户需求不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户需求不能为空");
        }

        try {
            log.info("ComplexityEvaluator开始执行: userRequirement={}", userRequirement);

            // 预处理：提取关键信息
            List<String> detectedTechKeywords = detectTechKeywords(userRequirement);
            List<String> detectedAiKeywords = detectAiKeywords(userRequirement);
            int estimatedEntityCount = estimateEntityCount(userRequirement);

            log.debug("预处理完成: techKeywords={}, aiKeywords={}, estimatedEntities={}",
                    detectedTechKeywords, detectedAiKeywords, estimatedEntityCount);

            // 构建ChatClient
            ChatClient chatClient = ChatClient.builder(chatModel).build();

            // 构建用户提示词
            String userPrompt = buildUserPrompt(userRequirement, detectedTechKeywords,
                    detectedAiKeywords, estimatedEntityCount);

            // 调用AI模型
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();

            log.debug("ComplexityEvaluator AI响应: {}", response);

            // 解析JSON响应
            ComplexityScore score = parseResponse(response, userPrompt, response);

            // 后处理：计算综合评分和确定等级
            postProcessScore(score, detectedTechKeywords, detectedAiKeywords);

            log.info("ComplexityEvaluator执行成功: level={}, finalScore={}, entities={}",
                    score.getLevel(), score.getFinalScore(), score.getExtractedEntities());

            return score;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("ComplexityEvaluator执行失败: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "复杂度评估失败: " + e.getMessage());
        }
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String userRequirement, List<String> techKeywords,
                                   List<String> aiKeywords, int estimatedEntityCount) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请分析以下用户需求，评估其技术复杂度：\n\n");
        prompt.append("用户需求：\n");
        prompt.append(userRequirement);
        prompt.append("\n\n");

        // 添加预处理结果作为参考
        prompt.append("【系统预分析结果（仅供参考）】\n");

        if (!techKeywords.isEmpty()) {
            prompt.append("检测到的技术关键词：").append(String.join(", ", techKeywords)).append("\n");
        }

        if (!aiKeywords.isEmpty()) {
            prompt.append("检测到的AI能力关键词：").append(String.join(", ", aiKeywords)).append("\n");
        }

        prompt.append("预估实体数量：约").append(estimatedEntityCount).append("个\n\n");

        prompt.append("请根据四维度评分体系进行详细评估，严格按照JSON格式输出结果。");

        return prompt.toString();
    }

    /**
     * 检测技术关键词
     */
    private List<String> detectTechKeywords(String userRequirement) {
        List<String> detected = new ArrayList<>();
        String lowerRequirement = userRequirement.toLowerCase();

        for (String keyword : TECH_KEYWORDS) {
            if (lowerRequirement.contains(keyword.toLowerCase())) {
                detected.add(keyword);
            }
        }

        return detected;
    }

    /**
     * 检测AI能力关键词
     */
    private List<String> detectAiKeywords(String userRequirement) {
        List<String> detected = new ArrayList<>();
        String lowerRequirement = userRequirement.toLowerCase();

        for (String keyword : AI_KEYWORDS) {
            if (lowerRequirement.contains(keyword.toLowerCase())) {
                detected.add(keyword);
            }
        }

        return detected;
    }

    /**
     * 预估实体数量
     * 基于需求文本中的名词数量进行粗略估计
     */
    private int estimateEntityCount(String userRequirement) {
        // 简单的实体关键词列表
        List<String> entityKeywords = List.of(
                "用户", "商品", "订单", "评论", "分类", "标签", "文章", "帖子",
                "课程", "学生", "教师", "作业", "考试", "成绩",
                "项目", "任务", "成员", "团队", "文档", "文件",
                "客户", "供应商", "合同", "发票", "账单",
                "房源", "预订", "房东", "租客", "评价",
                "医生", "患者", "预约", "处方", "病历"
        );

        int count = 0;
        String lowerRequirement = userRequirement.toLowerCase();

        for (String entity : entityKeywords) {
            if (lowerRequirement.contains(entity.toLowerCase())) {
                count++;
            }
        }

        // 最少返回1，最多返回15
        return Math.max(1, Math.min(count, 15));
    }

    /**
     * 解析AI响应为ComplexityScore对象
     */
    private ComplexityScore parseResponse(String response, String promptUsed, String rawResponse) {
        try {
            // 提取JSON部分
            String jsonContent = extractJson(response);

            // 使用ObjectMapper解析JSON
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> jsonMap = objectMapper.readValue(jsonContent, java.util.Map.class);

            // 构建ComplexityScore
            ComplexityScore.ComplexityScoreBuilder builder = ComplexityScore.builder();

            // 解析四维度评分
            builder.entityCountScore(parseIntValue(jsonMap.get("entityCountScore")));
            builder.relationshipComplexityScore(parseIntValue(jsonMap.get("relationshipComplexityScore")));
            builder.aiCapabilityScore(parseIntValue(jsonMap.get("aiCapabilityScore")));
            builder.technicalRiskScore(parseIntValue(jsonMap.get("technicalRiskScore")));

            // 解析文本字段
            builder.reasoning((String) jsonMap.get("reasoning"));
            builder.suggestedArchitecture((String) jsonMap.get("suggestedArchitecture"));
            builder.suggestedDevelopmentStrategy((String) jsonMap.get("suggestedDevelopmentStrategy"));

            // 解析数组字段
            @SuppressWarnings("unchecked")
            List<String> extractedEntities = (List<String>) jsonMap.get("extractedEntities");
            builder.extractedEntities(extractedEntities != null ? extractedEntities : new ArrayList<>());

            @SuppressWarnings("unchecked")
            List<String> keyTechnologies = (List<String>) jsonMap.get("keyTechnologies");
            builder.keyTechnologies(keyTechnologies != null ? keyTechnologies : new ArrayList<>());

            @SuppressWarnings("unchecked")
            List<String> aiCapabilities = (List<String>) jsonMap.get("aiCapabilities");
            builder.aiCapabilities(aiCapabilities != null ? aiCapabilities : new ArrayList<>());

            @SuppressWarnings("unchecked")
            List<String> riskFactors = (List<String>) jsonMap.get("riskFactors");
            builder.riskFactors(riskFactors != null ? riskFactors : new ArrayList<>());

            // 保存提示词和原始响应（用于调试）
            builder.promptUsed(promptUsed);
            builder.rawResponse(rawResponse);

            // 初始化警告列表
            builder.warnings(new ArrayList<>());

            return builder.build();

        } catch (Exception e) {
            log.error("解析ComplexityEvaluator响应失败: response={}, error={}", response, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解析AI响应失败: " + e.getMessage());
        }
    }

    /**
     * 解析整数值
     */
    private Integer parseIntValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 后处理评分结果
     */
    private void postProcessScore(ComplexityScore score, List<String> techKeywords,
                                  List<String> aiKeywords) {
        // 1. 验证评分范围
        validateScoreRange(score);

        // 2. 计算综合评分
        score.calculateFinalScore();

        // 3. 确定复杂度等级
        score.determineLevelFromScore();

        // 4. 添加预处理检测但AI未识别的关键词作为警告
        if (!techKeywords.isEmpty() &&
            (score.getKeyTechnologies() == null || score.getKeyTechnologies().isEmpty())) {
            score.addWarning("系统检测到技术关键词但AI未识别：" + String.join(", ", techKeywords));
        }

        if (!aiKeywords.isEmpty() && score.getAiCapabilityScore() < 20) {
            score.addWarning("系统检测到AI关键词但评分较低：" + String.join(", ", aiKeywords));
        }

        // 5. 高风险警告
        if (score.isHighRisk()) {
            score.addWarning("检测到高技术风险（评分≥70），建议进行架构评审");
        }

        // 6. 复杂需求警告
        if (score.getLevel() == ComplexityLevel.COMPLEX) {
            score.addWarning("检测到复杂需求（综合评分>" + score.getFinalScore() +
                    "），建议分期开发，预估" + score.getLevel().getEstimatedTimeRange());
        }
    }

    /**
     * 验证评分范围（0-100）
     */
    private void validateScoreRange(ComplexityScore score) {
        // 修正超出范围的评分
        if (score.getEntityCountScore() != null) {
            score.setEntityCountScore(Math.max(0, Math.min(100, score.getEntityCountScore())));
        }
        if (score.getRelationshipComplexityScore() != null) {
            score.setRelationshipComplexityScore(Math.max(0, Math.min(100, score.getRelationshipComplexityScore())));
        }
        if (score.getAiCapabilityScore() != null) {
            score.setAiCapabilityScore(Math.max(0, Math.min(100, score.getAiCapabilityScore())));
        }
        if (score.getTechnicalRiskScore() != null) {
            score.setTechnicalRiskScore(Math.max(0, Math.min(100, score.getTechnicalRiskScore())));
        }
    }

    /**
     * 从响应文本中提取JSON内容
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
