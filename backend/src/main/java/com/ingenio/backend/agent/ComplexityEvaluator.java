package com.ingenio.backend.agent;

import com.ingenio.backend.agent.dto.ComplexityScore;
import com.ingenio.backend.common.exception.BusinessException;

/**
 * 需求复杂度评估器接口 - P3增强功能
 * 负责分析用户需求，评估技术复杂度，辅助资源分配和开发时间估算
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>实体数量分析：识别核心实体及其数量</li>
 *   <li>关系复杂度分析：评估实体间关系的复杂程度</li>
 *   <li>AI能力需求分析：识别所需的AI能力类型和复杂度</li>
 *   <li>技术风险评估：评估技术选型和实现的风险</li>
 *   <li>综合复杂度判定：基于四维度评分确定整体复杂度等级</li>
 * </ul>
 *
 * <p>设计模式：</p>
 * <ul>
 *   <li>策略模式：不同复杂度等级使用不同的评估策略</li>
 *   <li>模板方法：统一的评估流程，具体评分逻辑由实现类完成</li>
 *   <li>依赖注入：通过Spring容器注入AI模型（ChatModel）</li>
 * </ul>
 *
 * <p>使用场景：</p>
 * <ul>
 *   <li>Plan阶段：在IntentClassifier之后，进行复杂度评估</li>
 *   <li>资源规划：根据复杂度等级分配开发资源和时间</li>
 *   <li>风险预警：识别高风险需求，提前制定应对策略</li>
 *   <li>架构决策：根据复杂度等级选择合适的技术架构</li>
 * </ul>
 *
 * <p>实现要求：</p>
 * <ul>
 *   <li>准确性：复杂度评估准确率≥85%（目标≥90%）</li>
 *   <li>响应时间：P95 < 3秒（与IntentClassifier相同）</li>
 *   <li>Token消耗：< 2000 tokens/次（控制成本）</li>
 *   <li>可解释性：提供详细的评估理由和依据</li>
 * </ul>
 *
 * <p>预期产出（ComplexityScore）：</p>
 * <pre>{@code
 * ComplexityScore {
 *   entityCountScore: 60,              // 实体数量评分（0-100）
 *   relationshipComplexityScore: 70,   // 关系复杂度评分（0-100）
 *   aiCapabilityScore: 50,             // AI能力需求评分（0-100）
 *   technicalRiskScore: 40,            // 技术风险评分（0-100）
 *   finalScore: 57,                    // 综合评分（加权平均）
 *   level: MEDIUM,                     // 复杂度等级
 *   reasoning: "电商平台包含4个核心实体...",
 *   extractedEntities: ["用户", "商品", "订单", "评论"],
 *   keyTechnologies: ["支付集成", "搜索引擎", "推荐算法"],
 *   suggestedArchitecture: "微服务架构（订单服务+商品服务+用户服务）",
 *   suggestedDevelopmentStrategy: "分期开发，先实现核心电商功能，再扩展推荐和营销"
 * }
 * }</pre>
 *
 * @author Ingenio Team
 * @version 2.0.1
 * @since 2025-11-20 P3 Phase 3.1
 * @see ComplexityScore
 * @see com.ingenio.backend.agent.dto.ComplexityLevel
 * @see IntentClassifier
 */
public interface ComplexityEvaluator {

    /**
     * 评估用户需求的复杂度
     *
     * <p>评估流程：</p>
     * <ol>
     *   <li>预处理：提取关键信息（实体、关系、技术关键词）</li>
     *   <li>AI分析：调用ChatModel（Qwen-Max）进行四维度评分</li>
     *   <li>后处理：验证评分合理性，计算综合评分，确定复杂度等级</li>
     *   <li>生成建议：提供架构建议和开发策略建议</li>
     * </ol>
     *
     * <p>四维度评分标准：</p>
     * <ul>
     *   <li><strong>实体数量评分（30%权重）</strong>：
     *     <ul>
     *       <li>1-3个实体：10-30分（简单）</li>
     *       <li>4-8个实体：40-70分（中等）</li>
     *       <li>9+个实体：75-100分（复杂）</li>
     *     </ul>
     *   </li>
     *   <li><strong>关系复杂度评分（30%权重）</strong>：
     *     <ul>
     *       <li>简单CRUD，无关联：10-30分</li>
     *       <li>一对多/多对一关系：40-60分</li>
     *       <li>多对多/图状/递归关系：70-100分</li>
     *     </ul>
     *   </li>
     *   <li><strong>AI能力需求评分（20%权重）</strong>：
     *     <ul>
     *       <li>无AI需求：0-10分</li>
     *       <li>简单文本处理/搜索：20-40分</li>
     *       <li>NLP/推荐/分类：50-70分</li>
     *       <li>多模态AI（视觉+语音+NLP）：80-100分</li>
     *     </ul>
     *   </li>
     *   <li><strong>技术风险评分（20%权重）</strong>：
     *     <ul>
     *       <li>成熟技术栈（Spring Boot + PostgreSQL）：10-30分</li>
     *       <li>常规集成（支付/地图/短信）：40-60分</li>
     *       <li>实时通讯/大数据/创新技术：70-100分</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p>复杂度等级映射：</p>
     * <ul>
     *   <li>综合评分 0-40分 → SIMPLE（2-3天）</li>
     *   <li>综合评分 41-70分 → MEDIUM（4-6天）</li>
     *   <li>综合评分 71-100分 → COMPLEX（7+天，建议分期）</li>
     * </ul>
     *
     * @param userRequirement 用户需求（自然语言描述）
     * @return 复杂度评分结果（包含四维度评分、综合评分、等级、建议）
     * @throws BusinessException 当评估失败时抛出
     * @throws IllegalArgumentException 当userRequirement为空时抛出
     *
     * <p>使用示例：</p>
     * <pre>{@code
     * @Autowired
     * private ComplexityEvaluator complexityEvaluator;
     *
     * public void analyzRequirement(String userRequirement) {
     *     // 评估复杂度
     *     ComplexityScore score = complexityEvaluator.evaluateComplexity(userRequirement);
     *
     *     // 根据复杂度等级决策
     *     if (score.getLevel() == ComplexityLevel.COMPLEX) {
     *         log.warn("检测到复杂需求：{}", score.getReasoning());
     *         // 建议分期开发
     *         suggestPhasedDevelopment(score.getSuggestedDevelopmentStrategy());
     *     } else if (score.isHighRisk()) {
     *         log.warn("检测到高风险技术需求：{}", score.getRiskFactors());
     *         // 需要架构评审
     *         scheduleArchitectureReview(score);
     *     } else {
     *         log.info("需求复杂度评估：{}", score.getFormattedReport());
     *         // 直接进入开发流程
     *         proceedToExecution(score);
     *     }
     * }
     * }</pre>
     */
    ComplexityScore evaluateComplexity(String userRequirement) throws BusinessException;

    /**
     * 批量评估多个需求的复杂度
     *
     * <p>用于批量分析和优先级排序场景</p>
     *
     * @param userRequirements 用户需求列表
     * @return 复杂度评分结果列表
     * @throws BusinessException 当评估失败时抛出
     */
    default java.util.List<ComplexityScore> evaluateBatch(java.util.List<String> userRequirements) throws BusinessException {
        if (userRequirements == null || userRequirements.isEmpty()) {
            throw new IllegalArgumentException("用户需求列表不能为空");
        }

        java.util.List<ComplexityScore> scores = new java.util.ArrayList<>();
        for (String requirement : userRequirements) {
            scores.add(evaluateComplexity(requirement));
        }

        return scores;
    }

    /**
     * 快速评估（简化版）
     *
     * <p>仅返回复杂度等级，不进行详细的四维度评分</p>
     * <p>响应时间：P95 < 1秒</p>
     *
     * @param userRequirement 用户需求
     * @return 复杂度等级
     * @throws BusinessException 当评估失败时抛出
     */
    default com.ingenio.backend.agent.dto.ComplexityLevel evaluateQuick(String userRequirement) throws BusinessException {
        ComplexityScore score = evaluateComplexity(userRequirement);
        return score.getLevel();
    }
}
