package com.ingenio.backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 意图识别结果DTO
 * 封装IntentClassifier的分析结果，包含识别的意图、置信度、参考URL等信息
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentClassificationResult {

    /**
     * 识别出的意图类型
     */
    private RequirementIntent intent;

    /**
     * 置信度分数（0.0 - 1.0）
     * - 0.9-1.0: 非常确定
     * - 0.7-0.9: 比较确定
     * - 0.5-0.7: 中等确定
     * - <0.5: 不确定，建议人工审核
     */
    private Double confidence;

    /**
     * AI推理过程说明
     * 解释为什么识别为该意图
     */
    private String reasoning;

    /**
     * 提取的参考网站URL列表
     * 仅当intent为CLONE_EXISTING_WEBSITE或HYBRID_CLONE_AND_CUSTOMIZE时有值
     */
    private List<String> referenceUrls;

    /**
     * 提取的关键词列表
     * 用于辅助意图判断和后续处理
     */
    private List<String> extractedKeywords;

    /**
     * 定制化需求描述
     * 仅当intent为HYBRID_CLONE_AND_CUSTOMIZE时有值
     * 描述用户在参考网站基础上想要的定制化修改
     */
    private String customizationRequirement;

    /**
     * AI模型使用的提示词（用于调试和审计）
     */
    private String promptUsed;

    /**
     * AI原始响应（用于调试）
     */
    private String rawResponse;

    /**
     * 建议的下一步操作
     * 根据识别的意图给出明确的操作建议
     */
    private String suggestedNextAction;

    /**
     * 警告信息列表
     * 当识别结果存在疑问或需要人工确认时的警告
     */
    private List<String> warnings;

    /**
     * 复杂度评估结果（P3 Phase 3.3新增）
     * 包含四维度评分、综合评分、复杂度等级、开发时间估算等信息
     *
     * @since V2.0.2 (P3增强功能)
     */
    private ComplexityScore complexityScore;

    /**
     * 改写后的结构化需求（P3 Phase 3.4实现）
     * 根据意图和复杂度评估结果，对原始需求进行边界优化和细化
     * 使其更加明确、可执行，便于后续代码生成
     *
     * @since V2.0.2 (P3增强功能)
     */
    private RefinedRequirement refinedRequirement;

    /**
     * 判断意图识别是否成功
     *
     * @return true 如果成功识别，false 如果失败
     */
    public boolean isSuccessful() {
        return intent != null && confidence != null && confidence >= 0.5;
    }

    /**
     * 判断置信度是否足够高
     *
     * @param threshold 置信度阈值（默认0.7）
     * @return true 如果置信度高于阈值
     */
    public boolean isHighConfidence(double threshold) {
        return confidence != null && confidence >= threshold;
    }

    /**
     * 判断置信度是否足够高（使用默认阈值0.7）
     *
     * @return true 如果置信度高于0.7
     */
    public boolean isHighConfidence() {
        return isHighConfidence(0.7);
    }

    /**
     * 获取格式化的意图描述（用于日志和展示）
     *
     * @return 格式化的描述字符串
     */
    public String getFormattedDescription() {
        if (intent == null) {
            return "未识别到明确意图";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("意图: ").append(intent.getDisplayName());
        sb.append(", 置信度: ").append(String.format("%.2f%%", confidence * 100));

        if (referenceUrls != null && !referenceUrls.isEmpty()) {
            sb.append(", 参考网站: ").append(String.join(", ", referenceUrls));
        }

        if (customizationRequirement != null && !customizationRequirement.isEmpty()) {
            sb.append(", 定制需求: ").append(customizationRequirement);
        }

        return sb.toString();
    }

    /**
     * 添加警告信息
     *
     * @param warning 警告信息
     */
    public void addWarning(String warning) {
        if (this.warnings == null) {
            this.warnings = new java.util.ArrayList<>();
        }
        this.warnings.add(warning);
    }

    /**
     * 判断是否有警告信息
     *
     * @return true 如果存在警告
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    @Override
    public String toString() {
        return getFormattedDescription();
    }
}
