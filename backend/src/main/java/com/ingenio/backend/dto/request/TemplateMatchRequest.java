package com.ingenio.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 模板匹配请求DTO
 *
 * Phase X.4: 行业模板库功能 - API请求对象
 *
 * 用于接收用户输入的关键词和参考URL，进行智能模板匹配
 *
 * 使用示例：
 * <pre>
 * POST /api/v1/templates/match
 * {
 *   "keywords": ["民宿", "预订", "airbnb"],
 *   "referenceUrl": "https://www.airbnb.com",
 *   "topN": 5
 * }
 * </pre>
 *
 * @author Claude
 * @since 2025-11-16 (Phase X.4 行业模板库开发)
 */
@Data
public class TemplateMatchRequest {

    /**
     * 关键词列表
     *
     * 必填字段，用于Jaccard相似度计算
     *
     * 要求：
     * - 至少包含1个关键词
     * - 建议3-5个关键词效果最佳
     * - 支持中英文混合
     *
     * 示例：
     * - ["民宿", "预订", "住宿"]
     * - ["电商", "商城", "购物"]
     * - ["在线教育", "课程", "直播"]
     */
    @NotEmpty(message = "关键词列表不能为空")
    private List<String> keywords;

    /**
     * 参考网站URL（可选）
     *
     * 用于URL相似度匹配，提升匹配准确度
     *
     * 格式：
     * - 完整URL：https://www.airbnb.com
     * - 简化URL：airbnb.com
     * - 带路径URL：https://www.airbnb.com/s/homes
     *
     * 匹配策略：
     * - 域名完全匹配：相似度=1.0
     * - 主域名匹配：相似度=0.5（如 www.airbnb.com 与 cn.airbnb.com）
     * - 域名不匹配：相似度=0.0
     */
    private String referenceUrl;

    /**
     * 返回Top N个模板
     *
     * 默认值：5
     * 最小值：1
     * 建议值：3-10
     *
     * 说明：
     * - 返回的模板按总分降序排序
     * - 只返回总分 > 0.1 的模板
     * - 如果匹配结果少于topN，返回实际数量
     */
    @Min(value = 1, message = "topN必须大于0")
    private Integer topN = 5;
}
