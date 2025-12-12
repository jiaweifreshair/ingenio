package com.ingenio.backend.dto.response;

import com.ingenio.backend.entity.IndustryTemplateEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 模板匹配响应DTO
 *
 * Phase X.4: 行业模板库功能 - API响应对象
 *
 * 封装模板匹配结果，包含匹配的模板及详细评分信息
 *
 * 响应示例：
 * <pre>
 * {
 *   "template": {
 *     "id": "uuid",
 *     "name": "民宿预订平台模板",
 *     "category": "生活服务",
 *     "keywords": ["民宿", "预订", "airbnb"],
 *     "referenceUrl": "https://www.airbnb.com",
 *     ...
 *   },
 *   "totalScore": 0.82,
 *   "keywordScore": 0.75,
 *   "categoryScore": 1.0,
 *   "urlScore": 1.0,
 *   "complexityPenalty": 0.6
 * }
 * </pre>
 *
 * @author Claude
 * @since 2025-11-16 (Phase X.4 行业模板库开发)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateMatchResponse {

    /**
     * 匹配的模板实体
     *
     * 包含模板的完整信息：
     * - 基础信息：名称、描述、分类
     * - 关键词列表
     * - 预定义实体和功能
     * - 参考网站URL
     * - 复杂度评分
     * - 使用统计等
     */
    private IndustryTemplateEntity template;

    /**
     * 总分（加权后）
     *
     * 计算公式：
     * totalScore = keywordScore * 0.6
     *            + categoryScore * 0.2
     *            + urlScore * 0.15
     *            - complexityPenalty * 0.05
     *
     * 取值范围：0.0 - 1.0
     *
     * 分数解读：
     * - 0.8 - 1.0：高度匹配（强烈推荐）
     * - 0.6 - 0.8：较高匹配（推荐）
     * - 0.4 - 0.6：中等匹配（可参考）
     * - 0.2 - 0.4：低度匹配（不推荐）
     * - 0.0 - 0.2：极低匹配（不推荐）
     */
    private Double totalScore;

    /**
     * 关键词匹配分数（Jaccard相似度）
     *
     * 计算方式：|A ∩ B| / |A ∪ B|
     * - A：用户输入的关键词集合
     * - B：模板的关键词集合
     *
     * 取值范围：0.0 - 1.0
     * 权重：60%
     *
     * 示例：
     * - 用户输入：["民宿", "预订", "airbnb"]
     * - 模板关键词：["民宿", "预订", "住宿", "airbnb", "短租"]
     * - 交集：["民宿", "预订", "airbnb"]（3个）
     * - 并集：["民宿", "预订", "住宿", "airbnb", "短租"]（5个）
     * - 相似度：3 / 5 = 0.6
     */
    private Double keywordScore;

    /**
     * 分类匹配分数
     *
     * 匹配策略（V2.0 Task 4将集成IntentClassifier提供分类）：
     * - 一级分类匹配：1.0
     * - 二级分类匹配：0.5
     * - 不匹配：0.0
     *
     * 取值范围：0.0 - 1.0
     * 权重：20%
     *
     * 当前版本：
     * - 暂时固定为0.0（待Task 4集成）
     */
    private Double categoryScore;

    /**
     * URL相似度分数
     *
     * 匹配策略：
     * - 域名完全匹配：1.0（如 airbnb.com == airbnb.com）
     * - 主域名匹配：0.5（如 www.airbnb.com == cn.airbnb.com）
     * - 域名不匹配：0.0
     * - 用户未提供URL：0.0
     *
     * 取值范围：0.0 - 1.0
     * 权重：15%
     */
    private Double urlScore;

    /**
     * 复杂度惩罚值
     *
     * 计算方式：complexityScore / 10.0
     * - complexityScore取值1-10
     * - 归一化后惩罚值：0.1 - 1.0
     *
     * 权重：5%（负向）
     *
     * 说明：
     * - 复杂度越高，惩罚越大
     * - 鼓励推荐简单易用的模板
     * - 帮助用户快速上手
     */
    private Double complexityPenalty;

    /**
     * 从IndustryTemplateMatchingService.TemplateMatchResult转换为DTO
     *
     * @param matchResult 服务层的匹配结果对象
     * @return TemplateMatchResponse DTO对象
     */
    public static TemplateMatchResponse fromServiceResult(
            com.ingenio.backend.service.IndustryTemplateMatchingService.TemplateMatchResult matchResult
    ) {
        return TemplateMatchResponse.builder()
                .template(matchResult.getTemplate())
                .totalScore(matchResult.getTotalScore())
                .keywordScore(matchResult.getKeywordScore())
                .categoryScore(matchResult.getCategoryScore())
                .urlScore(matchResult.getUrlScore())
                .complexityPenalty(matchResult.getComplexityPenalty())
                .build();
    }

    /**
     * 批量转换
     *
     * @param matchResults 服务层匹配结果列表
     * @return TemplateMatchResponse列表
     */
    public static List<TemplateMatchResponse> fromServiceResults(
            List<com.ingenio.backend.service.IndustryTemplateMatchingService.TemplateMatchResult> matchResults
    ) {
        return matchResults.stream()
                .map(TemplateMatchResponse::fromServiceResult)
                .toList();
    }
}
