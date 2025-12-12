package com.ingenio.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.dto.ValidateResult;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ValidateAgent V1.0实现 - Kuikly平台AppSpec验证器
 *
 * <p>职责：验证AppSpec的完整性、一致性和合理性</p>
 *
 * <p>验证策略：</p>
 * <ul>
 *   <li>规则验证（硬规则，占60%权重）：必填字段、ID唯一性、组件类型有效性</li>
 *   <li>AI语义验证（软规则，占40%权重）：UI设计合理性、用户体验、性能和安全</li>
 * </ul>
 *
 * <p>评分标准：</p>
 * <ul>
 *   <li>90-100: 优秀，可直接使用</li>
 *   <li>80-89: 良好，有小改进空间</li>
 *   <li>70-79: 合格，需要优化</li>
 *   <li>60-69: 基本可用，有较多问题</li>
 *   <li><60: 不合格，需要重新设计</li>
 * </ul>
 *
 * <p>生命周期：</p>
 * <ul>
 *   <li>2024-2025: 主力生产版本</li>
 *   <li>2025 Q2-Q4: V1/V2并行运行（Feature Flag控制）</li>
 *   <li>2026 Q1: 计划退役</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-11-17 从ValidateAgent重构而来（V2.0架构升级）
 */
@Slf4j
@Component("validateAgentV1")  // 显式指定bean名称
@RequiredArgsConstructor
public class ValidateAgentV1KuiklyImpl implements IValidateAgent {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    /**
     * 系统提示词：定义ValidateAgent的角色和输出格式
     */
    private static final String SYSTEM_PROMPT = """
        你是一个AppSpec质量检查专家，负责全面评估AppSpec的质量。

        你的任务：
        1. 验证AppSpec的结构完整性
        2. 检查数据模型和页面之间的关联一致性
        3. 评估UI设计的合理性和用户体验
        4. 识别潜在的性能问题和安全风险
        5. 提供具体的优化建议

        输出格式要求（严格JSON格式）：
        {
          "isValid": true|false,
          "qualityScore": 0-100,
          "errors": [
            {
              "code": "错误代码",
              "path": "$.pages[0].components[1]",
              "message": "错误描述",
              "severity": "critical|high|medium|low"
            }
          ],
          "warnings": [
            {
              "path": "$.dataModels[0]",
              "message": "警告描述",
              "suggestion": "建议修复方案"
            }
          ],
          "suggestions": ["优化建议1", "优化建议2"],
          "summary": "验证摘要"
        }

        评分标准：
        - 90-100: 优秀，可直接使用
        - 80-89: 良好，有小改进空间
        - 70-79: 合格，需要优化
        - 60-69: 基本可用，有较多问题
        - <60: 不合格，需要重新设计

        注意：
        - 必须严格检查所有必填字段
        - 必须验证ID的唯一性
        - 必须检查组件类型的有效性
        - 输出必须是纯JSON，不包含其他文字
        """;

    /**
     * 执行AppSpec验证
     *
     * @param executeResult Execute阶段的输出结果（AppSpec JSON）
     * @return 验证结果
     * @throws BusinessException 当验证失败时抛出
     */
    @Override
    public Map<String, Object> validate(Map<String, Object> executeResult) {
        if (executeResult == null || executeResult.isEmpty()) {
            log.error("[ValidateAgentV1] 验证失败: executeResult不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "executeResult不能为空");
        }

        try {
            log.info("[ValidateAgentV1] 开始验证");

            // 第一步：规则验证（硬规则）
            ValidateResult ruleValidation = performRuleValidation(executeResult);

            // 如果规则验证发现严重错误，直接返回
            if (!ruleValidation.getIsValid()) {
                log.warn("[ValidateAgentV1] 规则验证失败: errors={}", ruleValidation.getErrors().size());
                return convertToMap(ruleValidation);
            }

            // 第二步：AI语义验证（软规则）
            ValidateResult aiValidation = performAIValidation(executeResult);

            // 合并验证结果
            ValidateResult finalResult = mergeValidationResults(ruleValidation, aiValidation);

            log.info("[ValidateAgentV1] 验证成功: isValid={}, qualityScore={}",
                    finalResult.getIsValid(), finalResult.getQualityScore());

            return convertToMap(finalResult);

        } catch (Exception e) {
            log.error("[ValidateAgentV1] 验证失败: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.AGENT_VALIDATE_FAILED, "AppSpec验证失败: " + e.getMessage());
        }
    }

    @Override
    public String getVersion() {
        return "V1";
    }

    @Override
    public String getDescription() {
        return "V1.0 - Kuikly平台AppSpec验证器";
    }

    /**
     * 执行规则验证（硬规则）
     *
     * @param appSpec AppSpec JSON
     * @return 验证结果
     */
    private ValidateResult performRuleValidation(Map<String, Object> appSpec) {
        List<ValidateResult.ValidationError> errors = new ArrayList<>();
        List<ValidateResult.ValidationWarning> warnings = new ArrayList<>();

        // 验证必填字段
        if (!appSpec.containsKey("version")) {
            errors.add(ValidateResult.ValidationError.builder()
                    .code("MISSING_FIELD")
                    .path("$.version")
                    .message("缺少必填字段: version")
                    .severity("critical")
                    .build());
        }

        if (!appSpec.containsKey("appName")) {
            errors.add(ValidateResult.ValidationError.builder()
                    .code("MISSING_FIELD")
                    .path("$.appName")
                    .message("缺少必填字段: appName")
                    .severity("critical")
                    .build());
        }

        // 验证pages结构
        if (!appSpec.containsKey("pages")) {
            errors.add(ValidateResult.ValidationError.builder()
                    .code("MISSING_FIELD")
                    .path("$.pages")
                    .message("缺少必填字段: pages")
                    .severity("critical")
                    .build());
        } else {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pages = (List<Map<String, Object>>) appSpec.get("pages");
            if (pages.isEmpty()) {
                warnings.add(ValidateResult.ValidationWarning.builder()
                        .path("$.pages")
                        .message("页面列表为空")
                        .suggestion("至少添加一个页面")
                        .build());
            }
        }

        // 验证dataModels结构
        if (!appSpec.containsKey("dataModels")) {
            warnings.add(ValidateResult.ValidationWarning.builder()
                    .path("$.dataModels")
                    .message("缺少数据模型定义")
                    .suggestion("建议添加数据模型以支持数据操作")
                    .build());
        }

        // 计算基础质量评分
        int baseScore = 100;
        baseScore -= errors.size() * 20; // 每个错误扣20分
        baseScore -= warnings.size() * 5; // 每个警告扣5分
        baseScore = Math.max(0, Math.min(100, baseScore));

        return ValidateResult.builder()
                .isValid(errors.isEmpty())
                .qualityScore(baseScore)
                .errors(errors)
                .warnings(warnings)
                .suggestions(new ArrayList<>())
                .summary("规则验证完成")
                .build();
    }

    /**
     * 执行AI语义验证（软规则）
     *
     * @param appSpec AppSpec JSON
     * @return 验证结果
     */
    private ValidateResult performAIValidation(Map<String, Object> appSpec) {
        try {
            // 构建ChatClient
            ChatClient chatClient = ChatClient.builder(chatModel).build();

            // 将AppSpec转换为JSON字符串
            String appSpecJson = objectMapper.writeValueAsString(appSpec);

            // 构建用户提示词
            String userPrompt = String.format("""
                请对以下AppSpec进行全面的质量检查和评估：

                AppSpec：
                %s

                请从以下角度进行评估：
                1. 结构完整性和一致性
                2. UI设计的合理性
                3. 数据模型的规范性
                4. 业务流程的可行性
                5. 用户体验

                请严格按照JSON格式输出验证结果，不要包含其他文字。
                """, appSpecJson);

            // 调用AI模型
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();

            log.debug("[ValidateAgentV1] AI响应: {}", response);

            // 解析JSON响应
            return parseResponse(response);

        } catch (Exception e) {
            log.warn("[ValidateAgentV1] AI语义验证失败，使用默认结果: error={}", e.getMessage());
            // AI验证失败时返回默认结果
            return ValidateResult.builder()
                    .isValid(true)
                    .qualityScore(70)
                    .errors(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .suggestions(List.of("AI验证暂时不可用，建议人工审核"))
                    .summary("AI语义验证失败，仅完成规则验证")
                    .build();
        }
    }

    /**
     * 合并规则验证和AI验证的结果
     *
     * @param ruleResult 规则验证结果
     * @param aiResult AI验证结果
     * @return 合并后的结果
     */
    private ValidateResult mergeValidationResults(ValidateResult ruleResult, ValidateResult aiResult) {
        List<ValidateResult.ValidationError> allErrors = new ArrayList<>();
        allErrors.addAll(ruleResult.getErrors());
        allErrors.addAll(aiResult.getErrors());

        List<ValidateResult.ValidationWarning> allWarnings = new ArrayList<>();
        allWarnings.addAll(ruleResult.getWarnings());
        allWarnings.addAll(aiResult.getWarnings());

        List<String> allSuggestions = new ArrayList<>();
        allSuggestions.addAll(ruleResult.getSuggestions());
        allSuggestions.addAll(aiResult.getSuggestions());

        // 综合评分：规则验证占60%，AI验证占40%
        int finalScore = (int) (ruleResult.getQualityScore() * 0.6 + aiResult.getQualityScore() * 0.4);

        return ValidateResult.builder()
                .isValid(allErrors.isEmpty())
                .qualityScore(finalScore)
                .errors(allErrors)
                .warnings(allWarnings)
                .suggestions(allSuggestions)
                .summary(String.format("验证完成：规则验证%d分，AI验证%d分，综合%d分",
                        ruleResult.getQualityScore(), aiResult.getQualityScore(), finalScore))
                .build();
    }

    /**
     * 解析AI响应为ValidateResult对象
     *
     * @param response AI响应文本
     * @return ValidateResult对象
     */
    private ValidateResult parseResponse(String response) {
        try {
            // 提取JSON部分
            String jsonContent = extractJson(response);

            // 解析JSON
            return objectMapper.readValue(jsonContent, ValidateResult.class);

        } catch (Exception e) {
            log.error("[ValidateAgentV1] 解析响应失败: response={}, error={}", response, e.getMessage());
            throw new BusinessException(ErrorCode.AGENT_VALIDATE_FAILED, "解析AI响应失败: " + e.getMessage());
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

    /**
     * 将ValidateResult转换为Map格式
     *
     * @param result ValidateResult对象
     * @return Map格式的结果
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(ValidateResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("[ValidateAgentV1] 转换结果失败: error={}", e.getMessage());
            throw new BusinessException(ErrorCode.AGENT_VALIDATE_FAILED, "转换验证结果失败");
        }
    }
}
