package com.ingenio.backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 需求复杂度评分结果 - P3增强功能
 * 封装ComplexityEvaluator的分析结果，包含四维度评分和综合复杂度等级
 *
 * <p>四维度评分体系：</p>
 * <ul>
 *   <li>实体数量评分（Entity Count Score）：基于需求中的核心实体数量</li>
 *   <li>关系复杂度评分（Relationship Complexity Score）：基于实体间关系复杂度</li>
 *   <li>AI能力需求评分（AI Capability Score）：基于所需AI能力的复杂度</li>
 *   <li>技术风险评分（Technical Risk Score）：基于技术选型和实现风险</li>
 * </ul>
 *
 * <p>评分规则：</p>
 * <ul>
 *   <li>每个维度评分范围：0-100分</li>
 *   <li>综合评分：四维度加权平均（权重可调）</li>
 *   <li>复杂度等级映射：
 *     <ul>
 *       <li>0-40分 → SIMPLE</li>
 *       <li>41-70分 → MEDIUM</li>
 *       <li>71-100分 → COMPLEX</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @author Ingenio Team
 * @version 2.0.1
 * @since 2025-11-20 P3 Phase 3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplexityScore {

    /**
     * 实体数量评分（0-100分）
     *
     * <p>评分标准：</p>
     * <ul>
     *   <li>1-3个实体：10-30分</li>
     *   <li>4-8个实体：40-70分</li>
     *   <li>9+个实体：75-100分</li>
     * </ul>
     */
    private Integer entityCountScore;

    /**
     * 关系复杂度评分（0-100分）
     *
     * <p>评分标准：</p>
     * <ul>
     *   <li>简单CRUD，无关联：10-30分</li>
     *   <li>一对多/多对一关系：40-60分</li>
     *   <li>多对多/图状/递归关系：70-100分</li>
     * </ul>
     */
    private Integer relationshipComplexityScore;

    /**
     * AI能力需求评分（0-100分）
     *
     * <p>评分标准：</p>
     * <ul>
     *   <li>无AI需求：0-10分</li>
     *   <li>简单文本处理/搜索：20-40分</li>
     *   <li>NLP/推荐/分类：50-70分</li>
     *   <li>多模态AI（视觉+语音+NLP）：80-100分</li>
     * </ul>
     */
    private Integer aiCapabilityScore;

    /**
     * 技术风险评分（0-100分）
     *
     * <p>评分标准：</p>
     * <ul>
     *   <li>成熟技术栈（Spring Boot + PostgreSQL）：10-30分</li>
     *   <li>常规集成（支付/地图/短信）：40-60分</li>
     *   <li>实时通讯/大数据/创新技术：70-100分</li>
     * </ul>
     */
    private Integer technicalRiskScore;

    /**
     * 综合评分（0-100分）
     *
     * <p>计算公式（加权平均）：</p>
     * <pre>
     * finalScore = entityCountScore * 0.3
     *            + relationshipComplexityScore * 0.3
     *            + aiCapabilityScore * 0.2
     *            + technicalRiskScore * 0.2
     * </pre>
     */
    private Integer finalScore;

    /**
     * 复杂度等级
     *
     * <p>根据综合评分自动映射：</p>
     * <ul>
     *   <li>0-40分 → SIMPLE</li>
     *   <li>41-70分 → MEDIUM</li>
     *   <li>71-100分 → COMPLEX</li>
     * </ul>
     */
    private ComplexityLevel level;

    /**
     * AI推理过程说明
     * 解释为什么评估为该复杂度等级
     */
    private String reasoning;

    /**
     * 提取的核心实体列表
     * 示例：["用户", "商品", "订单", "评论"]
     */
    private List<String> extractedEntities;

    /**
     * 识别的关键技术需求
     * 示例：["实时WebSocket", "支付集成", "AI推荐"]
     */
    private List<String> keyTechnologies;

    /**
     * AI能力需求列表
     * 示例：["NLP文本分析", "图像识别", "语音转文字"]
     */
    private List<String> aiCapabilities;

    /**
     * 技术风险点列表
     * 示例：["高并发实时通讯", "多租户数据隔离", "第三方API依赖"]
     */
    private List<String> riskFactors;

    /**
     * 建议的技术架构
     * 根据复杂度等级给出的架构建议
     */
    private String suggestedArchitecture;

    /**
     * 建议的开发策略
     * 根据复杂度等级给出的开发建议
     */
    private String suggestedDevelopmentStrategy;

    /**
     * AI模型使用的提示词（用于调试）
     */
    private String promptUsed;

    /**
     * AI原始响应（用于调试）
     */
    private String rawResponse;

    /**
     * 警告信息列表
     * 当评估结果存在疑问或需要人工确认时的警告
     */
    private List<String> warnings;

    /**
     * 计算并设置综合评分
     *
     * <p>使用标准权重计算：</p>
     * <ul>
     *   <li>实体数量：30%</li>
     *   <li>关系复杂度：30%</li>
     *   <li>AI能力需求：20%</li>
     *   <li>技术风险：20%</li>
     * </ul>
     *
     * @return 综合评分（0-100分）
     */
    public int calculateFinalScore() {
        if (entityCountScore == null || relationshipComplexityScore == null ||
            aiCapabilityScore == null || technicalRiskScore == null) {
            this.finalScore = 0;
            return 0;
        }

        this.finalScore = (int) Math.round(
            entityCountScore * 0.3 +
            relationshipComplexityScore * 0.3 +
            aiCapabilityScore * 0.2 +
            technicalRiskScore * 0.2
        );

        return this.finalScore;
    }

    /**
     * 根据综合评分自动映射复杂度等级
     *
     * @return 复杂度等级
     */
    public ComplexityLevel determineLevelFromScore() {
        if (finalScore == null) {
            calculateFinalScore();
        }

        if (finalScore <= 40) {
            this.level = ComplexityLevel.SIMPLE;
        } else if (finalScore <= 70) {
            this.level = ComplexityLevel.MEDIUM;
        } else {
            this.level = ComplexityLevel.COMPLEX;
        }

        return this.level;
    }

    /**
     * 判断评估是否成功
     *
     * @return true 如果所有评分字段都有效
     */
    public boolean isSuccessful() {
        return entityCountScore != null &&
               relationshipComplexityScore != null &&
               aiCapabilityScore != null &&
               technicalRiskScore != null &&
               level != null &&
               finalScore != null;
    }

    /**
     * 判断是否为高风险需求
     *
     * @return true 如果技术风险评分≥70分
     */
    public boolean isHighRisk() {
        return technicalRiskScore != null && technicalRiskScore >= 70;
    }

    /**
     * 判断是否需要AI能力
     *
     * @return true 如果AI能力需求评分≥20分
     */
    public boolean needsAiCapability() {
        return aiCapabilityScore != null && aiCapabilityScore >= 20;
    }

    /**
     * 获取格式化的评分报告（用于日志和展示）
     *
     * @return 格式化的报告字符串
     */
    public String getFormattedReport() {
        if (!isSuccessful()) {
            return "评估失败：缺少必要的评分数据";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【复杂度评估报告】\n");
        sb.append(String.format("复杂度等级：%s（综合评分：%d/100分）\n", level.getDisplayName(), finalScore));
        sb.append(String.format("  - 实体数量评分：%d/100分\n", entityCountScore));
        sb.append(String.format("  - 关系复杂度评分：%d/100分\n", relationshipComplexityScore));
        sb.append(String.format("  - AI能力需求评分：%d/100分\n", aiCapabilityScore));
        sb.append(String.format("  - 技术风险评分：%d/100分\n", technicalRiskScore));

        if (extractedEntities != null && !extractedEntities.isEmpty()) {
            sb.append(String.format("核心实体：%s\n", String.join(", ", extractedEntities)));
        }

        if (keyTechnologies != null && !keyTechnologies.isEmpty()) {
            sb.append(String.format("关键技术：%s\n", String.join(", ", keyTechnologies)));
        }

        if (isHighRisk() && riskFactors != null && !riskFactors.isEmpty()) {
            sb.append(String.format("⚠️ 风险点：%s\n", String.join(", ", riskFactors)));
        }

        if (reasoning != null && !reasoning.isEmpty()) {
            sb.append(String.format("评估理由：%s\n", reasoning));
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
            this.warnings = new ArrayList<>();
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
        return getFormattedReport();
    }
}
