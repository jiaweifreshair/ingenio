package com.ingenio.backend.agent.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.RequirementRefiner;
import com.ingenio.backend.agent.dto.*;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 需求改写器实现 - P3增强功能
 * 根据意图和复杂度评估结果，对原始需求进行边界优化和细化
 * 使其更加明确、可执行，便于后续代码生成
 *
 * <p>核心价值：</p>
 * <ul>
 *   <li>弥合用户自然语言需求与可执行代码生成之间的鸿沟</li>
 *   <li>补充缺失的核心实体和功能定义</li>
 *   <li>拆分复杂需求为MVP + 扩展功能</li>
 *   <li>明确技术约束和实现边界</li>
 *   <li>优化模糊表述为可执行描述</li>
 * </ul>
 *
 * <p>改写策略矩阵：</p>
 * <ul>
 *   <li>SIMPLE复杂度 → DETAIL_ENHANCEMENT (补充细节)</li>
 *   <li>MEDIUM复杂度 → PRIORITY_SPLIT (拆分MVP和扩展功能)</li>
 *   <li>COMPLEX复杂度 → PHASED_DEVELOPMENT (分期建议)</li>
 *   <li>不切实际需求 → COMPLEXITY_REDUCTION (降低复杂度)</li>
 *   <li>HYBRID意图 → BOUNDARY_CLARIFICATION (明确边界)</li>
 * </ul>
 *
 * <p>技术实现：</p>
 * <ul>
 *   <li>使用Spring AI Alibaba框架（Qwen-Max模型）</li>
 *   <li>temperature=0.5（平衡创造力和准确性）</li>
 *   <li>max-tokens=3000（足够生成详细的改写结果）</li>
 *   <li>结构化JSON输出，便于解析和验证</li>
 * </ul>
 *
 * <p>性能指标：</p>
 * <ul>
 *   <li>准确性：改写后需求与原始意图一致性≥90%</li>
 *   <li>响应时间：P95 < 3秒</li>
 *   <li>Token消耗：< 2500 tokens/次</li>
 * </ul>
 *
 * @author Ingenio Team
 * @version 2.0.2
 * @since 2025-11-20 P3 Phase 3.4
 * @see RequirementRefiner
 * @see RefinedRequirement
 * @see ComplexityScore
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementRefinerImpl implements RequirementRefiner {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    /**
     * 系统提示词：定义RequirementRefiner的角色和输出格式
     *
     * <p>设计原则：</p>
     * <ul>
     *   <li>明确AI角色定位（需求优化专家）</li>
     *   <li>详细定义输出格式（结构化JSON）</li>
     *   <li>提供改写策略指引（基于复杂度和意图）</li>
     *   <li>包含典型示例（提升准确率）</li>
     * </ul>
     */
    private static final String SYSTEM_PROMPT = """
        你是需求优化专家，将模糊需求改写为可执行的明确需求。

        **改写策略**：

        1. **SIMPLE复杂度** → DETAIL_ENHANCEMENT：补充核心实体、CRUD操作、基本交互流程

        2. **MEDIUM复杂度** → PRIORITY_SPLIT：拆分MVP核心功能和Future扩展功能

        3. **COMPLEX复杂度** → PHASED_DEVELOPMENT：拆分为多个独立可交付的开发阶段

        **意图策略**：

        1. **CLONE意图**：提取核心功能，标注不包含的复杂特性
        2. **DESIGN意图**：明确实体关系和业务流程
        3. **HYBRID意图**：区分参考部分和定制部分

        **典型问题及改写**：

        - **过于宽泛**（如"做社交平台"）→ 明确核心实体和功能清单
        - **不切实际**（如"支持百万并发"）→ 拆分MVP和扩展功能，降低首期复杂度

        **输出格式（JSON）**：

        {
          "refinedText": "改写后的明确需求",
          "coreEntities": ["实体1", "实体2"],
          "mvpFeatures": ["核心功能1", "核心功能2"],
          "futureFeatures": ["扩展功能1", "扩展功能2"],
          "technicalConstraints": ["技术约束1"],
          "entityRelationships": ["实体关系1"],
          "refiningReasoning": "改写理由",
          "needsUserConfirmation": false,
          "refineType": "DETAIL_ENHANCEMENT"
        }

        **refineType枚举**：DETAIL_ENHANCEMENT | PRIORITY_SPLIT | PHASED_DEVELOPMENT | COMPLEXITY_REDUCTION | BOUNDARY_CLARIFICATION | NO_REFINE_NEEDED

        **needsUserConfirmation标准**：
        - true：重大改写（改变功能范围/拆分分期/降低复杂度）
        - false：轻微优化（补充细节/明确表述）

        **要求**：输出valid JSON，refinedText更明确，MVP/Future边界清晰，实体关系明确，与原始意图一致性≥90%
        """;

    /**
     * 改写需求，使其更加明确和可执行
     *
     * <p>改写流程：</p>
     * <ol>
     *   <li>分析原始需求：提取关键信息、识别模糊点</li>
     *   <li>结合意图和复杂度：确定改写策略（补充细节/拆分优先级/分期建议）</li>
     *   <li>AI辅助改写：调用ChatModel生成结构化需求</li>
     *   <li>后处理验证：确保改写结果符合标准，生成警告信息</li>
     * </ol>
     *
     * @param originalRequirement 原始用户需求（自然语言描述）
     * @param intent 意图类型（CLONE/DESIGN/HYBRID）
     * @param complexityScore 复杂度评估结果
     * @return 改写后的结构化需求
     * @throws BusinessException 当改写失败时抛出
     * @throws IllegalArgumentException 当参数为空时抛出
     */
    @Override
    public RefinedRequirement refine(
            String originalRequirement,
            RequirementIntent intent,
            ComplexityScore complexityScore
    ) throws BusinessException {
        // 参数验证
        if (originalRequirement == null || originalRequirement.trim().isEmpty()) {
            throw new IllegalArgumentException("原始需求不能为空");
        }
        if (intent == null) {
            throw new IllegalArgumentException("意图类型不能为空");
        }
        if (complexityScore == null) {
            throw new IllegalArgumentException("复杂度评估结果不能为空");
        }

        log.info("开始需求改写: originalRequirement={}, intent={}, complexityLevel={}",
                originalRequirement, intent, complexityScore.getLevel());

        try {
            // 步骤1: 检查是否需要改写
            if (!needsRefine(originalRequirement, complexityScore)) {
                log.info("需求已足够明确，无需改写");
                return buildNoRefineResult(originalRequirement, complexityScore);
            }

            // 步骤2: 构建用户提示词
            String userPrompt = buildUserPrompt(originalRequirement, intent, complexityScore);
            log.debug("用户提示词已构建: length={}", userPrompt.length());

            // 步骤3: 调用AI模型进行改写
            String response = callChatModel(userPrompt);
            log.debug("AI响应已接收: length={}", response.length());

            // 步骤4: 解析AI响应
            RefinedRequirement result = parseResponse(response);
            log.debug("AI响应已解析: refineType={}", result.getRefineType());

            // 步骤5: 后处理验证
            postProcessResult(result, originalRequirement, complexityScore);

            log.info("需求改写成功: refineType={}, needsUserConfirmation={}",
                    result.getRefineType(), result.isNeedsUserConfirmation());

            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("需求改写失败: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "需求改写失败: " + e.getMessage());
        }
    }

    /**
     * 构建用户提示词
     *
     * @param originalRequirement 原始需求
     * @param intent 意图类型
     * @param complexityScore 复杂度评估结果
     * @return 用户提示词
     */
    private String buildUserPrompt(
            String originalRequirement,
            RequirementIntent intent,
            ComplexityScore complexityScore
    ) {
        return String.format("""
                对以下需求进行优化改写：

                **原始需求**：%s

                **意图**：%s

                **复杂度**：
                - 等级：%s (评分：%d/100)
                - 实体：%s

                **建议策略**：%s

                按照系统提示词格式输出JSON结果。
                """,
                originalRequirement,
                intent.getDisplayName(),
                complexityScore.getLevel().name(),
                complexityScore.getFinalScore(),
                complexityScore.getExtractedEntities(),
                getRecommendedStrategy(complexityScore.getLevel(), intent)
        );
    }

    /**
     * 获取推荐的改写策略
     *
     * @param level 复杂度等级
     * @param intent 意图类型
     * @return 推荐策略名称
     */
    private String getRecommendedStrategy(ComplexityLevel level, RequirementIntent intent) {
        // 优先根据复杂度等级选择策略
        return switch (level) {
            case SIMPLE -> "补充细节 (DETAIL_ENHANCEMENT)";
            case MEDIUM -> "拆分优先级 (PRIORITY_SPLIT)";
            case COMPLEX -> "分期建议 (PHASED_DEVELOPMENT)";
        };
    }

    /**
     * 调用ChatClient进行改写
     *
     * <p>直接使用注入的ChatClient实例，便于测试时替换为Mock实现。</p>
     *
     * @param userPrompt 用户提示词
     * @return AI响应
     */
    private String callChatModel(String userPrompt) {
        // 直接使用注入的ChatClient（便于测试Mock）
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
    }

    /**
     * 解析AI响应
     *
     * @param response AI响应
     * @return 解析后的RefinedRequirement
     * @throws BusinessException 当解析失败时抛出
     */
    private RefinedRequirement parseResponse(String response) throws BusinessException {
        try {
            // 提取JSON部分（处理可能的Markdown代码块）
            String jsonContent = extractJsonContent(response);

            // 解析JSON
            Map<String, Object> map = objectMapper.readValue(jsonContent, new TypeReference<>() {});

            // 构建RefinedRequirement对象
            return RefinedRequirement.builder()
                    .refinedText((String) map.get("refinedText"))
                    .coreEntities(castToStringList(map.get("coreEntities")))
                    .mvpFeatures(castToStringList(map.get("mvpFeatures")))
                    .futureFeatures(castToStringList(map.get("futureFeatures")))
                    .technicalConstraints(castToStringList(map.get("technicalConstraints")))
                    .entityRelationships(castToStringList(map.get("entityRelationships")))
                    .refiningReasoning((String) map.get("refiningReasoning"))
                    .needsUserConfirmation((Boolean) map.getOrDefault("needsUserConfirmation", false))
                    .refineType(parseRefineType((String) map.get("refineType")))
                    .build();

        } catch (Exception e) {
            log.error("解析AI响应失败: response={}, error={}", response, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解析AI响应失败: " + e.getMessage());
        }
    }

    /**
     * 提取JSON内容（处理可能的Markdown代码块）
     *
     * @param response AI响应
     * @return JSON字符串
     */
    private String extractJsonContent(String response) {
        // 处理Markdown代码块格式：```json ... ```
        Pattern pattern = Pattern.compile("```(?:json)?\\s*\\n?([\\s\\S]*?)```", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 如果没有代码块，尝试直接提取 { ... }
        pattern = Pattern.compile("\\{[\\s\\S]*\\}", Pattern.MULTILINE);
        matcher = pattern.matcher(response);

        if (matcher.find()) {
            return matcher.group(0).trim();
        }

        // 如果都没找到，返回原始响应（可能会导致解析失败）
        return response.trim();
    }

    /**
     * 类型转换工具：将Object转为List<String>
     *
     * @param obj 待转换对象
     * @return 字符串列表
     */
    @SuppressWarnings("unchecked")
    private List<String> castToStringList(Object obj) {
        if (obj == null) {
            return new ArrayList<>();
        }
        if (obj instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .toList();
        }
        return new ArrayList<>();
    }

    /**
     * 解析RefineType枚举
     *
     * @param typeStr 类型字符串
     * @return RefineType枚举值
     */
    private RefinedRequirement.RefineType parseRefineType(String typeStr) {
        if (typeStr == null || typeStr.trim().isEmpty()) {
            return RefinedRequirement.RefineType.DETAIL_ENHANCEMENT;
        }

        try {
            return RefinedRequirement.RefineType.valueOf(typeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无效的RefineType: {}, 使用默认值DETAIL_ENHANCEMENT", typeStr);
            return RefinedRequirement.RefineType.DETAIL_ENHANCEMENT;
        }
    }

    /**
     * 后处理验证
     *
     * @param result 改写结果
     * @param originalRequirement 原始需求
     * @param complexityScore 复杂度评估结果
     */
    private void postProcessResult(
            RefinedRequirement result,
            String originalRequirement,
            ComplexityScore complexityScore
    ) {
        // 验证必填字段
        if (result.getRefinedText() == null || result.getRefinedText().trim().isEmpty()) {
            log.warn("改写后需求文本为空，使用原始需求");
            result.setRefinedText(originalRequirement);
        }

        // 验证改写类型与复杂度等级的匹配度
        validateRefineTypeMatch(result.getRefineType(), complexityScore.getLevel());

        // 如果是重大改写，强制要求用户确认
        if (result.isMajorRefine() && !result.isNeedsUserConfirmation()) {
            log.warn("检测到重大改写但未标记需要用户确认，已自动修正");
            result.setNeedsUserConfirmation(true);
        }

        // 补充默认值
        if (result.getCoreEntities() == null || result.getCoreEntities().isEmpty()) {
            result.setCoreEntities(complexityScore.getExtractedEntities());
        }
    }

    /**
     * 验证改写类型与复杂度等级的匹配度
     *
     * @param refineType 改写类型
     * @param level 复杂度等级
     */
    private void validateRefineTypeMatch(RefinedRequirement.RefineType refineType, ComplexityLevel level) {
        boolean matched = switch (level) {
            case SIMPLE -> refineType == RefinedRequirement.RefineType.DETAIL_ENHANCEMENT ||
                    refineType == RefinedRequirement.RefineType.NO_REFINE_NEEDED;
            case MEDIUM -> refineType == RefinedRequirement.RefineType.PRIORITY_SPLIT ||
                    refineType == RefinedRequirement.RefineType.BOUNDARY_CLARIFICATION;
            case COMPLEX -> refineType == RefinedRequirement.RefineType.PHASED_DEVELOPMENT ||
                    refineType == RefinedRequirement.RefineType.COMPLEXITY_REDUCTION;
        };

        if (!matched) {
            log.warn("改写类型 {} 与复杂度等级 {} 不匹配，可能导致改写效果不佳",
                    refineType, level);
        }
    }

    /**
     * 构建"无需改写"结果
     *
     * @param originalRequirement 原始需求
     * @param complexityScore 复杂度评估结果
     * @return RefinedRequirement
     */
    private RefinedRequirement buildNoRefineResult(
            String originalRequirement,
            ComplexityScore complexityScore
    ) {
        return RefinedRequirement.builder()
                .refinedText(originalRequirement)
                .coreEntities(complexityScore.getExtractedEntities())
                .mvpFeatures(new ArrayList<>())
                .futureFeatures(new ArrayList<>())
                .technicalConstraints(new ArrayList<>())
                .entityRelationships(new ArrayList<>())
                .refiningReasoning("原始需求已足够明确，无需优化改写")
                .needsUserConfirmation(false)
                .refineType(RefinedRequirement.RefineType.NO_REFINE_NEEDED)
                .build();
    }
}
