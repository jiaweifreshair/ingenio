package com.ingenio.backend.service;

import com.ingenio.backend.entity.IndustryTemplateEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ingenio.backend.mapper.IndustryTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 行业模板匹配服务
 *
 * Phase X.4: 行业模板库功能 - 模板匹配算法
 *
 * 核心功能：
 * 1. Jaccard相似度计算：评估关键词集合的重叠度
 * 2. 多维度匹配策略：关键词(60%) + 分类(20%) + URL(15%) + 复杂度(5%)
 * 3. Top-N排序返回：返回最匹配的N个模板
 *
 * 算法原理：
 * - Jaccard相似度 = |A ∩ B| / |A ∪ B|
 * - 总分 = 关键词分 * 0.6 + 分类分 * 0.2 + URL分 * 0.15 - 复杂度惩罚 * 0.05
 *
 * 使用示例：
 * <pre>
 * List<String> userKeywords = Arrays.asList("民宿", "预订", "airbnb");
 * String referenceUrl = "airbnb.com";
 *
 * List<TemplateMatchResult> matches = matchingService.matchTemplates(
 *     userKeywords,
 *     referenceUrl,
 *     5  // Top 5
 * );
 * </pre>
 *
 * @author Claude
 * @since 2025-11-16 (Phase X.4 行业模板库开发)
 */
@Slf4j
@Service
public class IndustryTemplateMatchingService {

    @Autowired
    private IndustryTemplateMapper templateMapper;

    /**
     * 模板匹配结果类
     *
     * 包含匹配的模板及其得分详情
     */
    public static class TemplateMatchResult {
        private final IndustryTemplateEntity template;
        private final double totalScore;
        private final double keywordScore;
        private final double categoryScore;
        private final double urlScore;
        private final double complexityPenalty;

        public TemplateMatchResult(
            IndustryTemplateEntity template,
            double keywordScore,
            double categoryScore,
            double urlScore,
            double complexityPenalty
        ) {
            this.template = template;
            this.keywordScore = keywordScore;
            this.categoryScore = categoryScore;
            this.urlScore = urlScore;
            this.complexityPenalty = complexityPenalty;

            // 总分计算：加权求和
            this.totalScore = keywordScore * 0.6
                            + categoryScore * 0.2
                            + urlScore * 0.15
                            - complexityPenalty * 0.05;
        }

        public IndustryTemplateEntity getTemplate() { return template; }
        public double getTotalScore() { return totalScore; }
        public double getKeywordScore() { return keywordScore; }
        public double getCategoryScore() { return categoryScore; }
        public double getUrlScore() { return urlScore; }
        public double getComplexityPenalty() { return complexityPenalty; }
    }

    /**
     * 匹配模板（核心方法）
     *
     * @param userKeywords 用户输入的关键词列表
     * @param referenceUrl 参考网站URL（可选，用于URL相似度匹配）
     * @param topN 返回Top N个最匹配的模板
     * @return 匹配结果列表，按总分降序排列
     */
    public List<TemplateMatchResult> matchTemplates(
        List<String> userKeywords,
        String referenceUrl,
        int topN
    ) {
        log.info("开始模板匹配 - userKeywords: {}, referenceUrl: {}, topN: {}",
                 userKeywords, referenceUrl, topN);

        // 1. 查询所有启用的模板
        LambdaQueryWrapper<IndustryTemplateEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IndustryTemplateEntity::getIsActive, true);
        List<IndustryTemplateEntity> activeTemplates = templateMapper.selectList(wrapper);

        log.info("找到 {} 个启用的模板", activeTemplates.size());

        // 2. 对每个模板计算匹配分数
        List<TemplateMatchResult> results = activeTemplates.stream()
            .map(template -> calculateMatchScore(template, userKeywords, referenceUrl))
            .filter(result -> result.getTotalScore() > 0.1)  // 过滤掉得分过低的模板
            .sorted((r1, r2) -> Double.compare(r2.getTotalScore(), r1.getTotalScore()))  // 降序排序
            .limit(topN)
            .collect(Collectors.toList());

        log.info("匹配完成，返回 {} 个结果", results.size());

        return results;
    }

    /**
     * 计算单个模板的匹配分数
     *
     * @param template 待匹配的模板
     * @param userKeywords 用户关键词
     * @param referenceUrl 参考URL
     * @return 匹配结果
     */
    private TemplateMatchResult calculateMatchScore(
        IndustryTemplateEntity template,
        List<String> userKeywords,
        String referenceUrl
    ) {
        // 1. 关键词匹配分数（Jaccard相似度）
        double keywordScore = calculateKeywordSimilarity(
            userKeywords,
            template.getKeywords()
        );

        // 2. 分类匹配分数（基于关键词推断行业分类）
        double categoryScore = calculateCategoryScore(
            userKeywords,
            template.getCategory(),
            template.getSubcategory()
        );

        // 3. URL相似度分数
        double urlScore = calculateUrlSimilarity(
            referenceUrl,
            template.getReferenceUrl()
        );

        // 4. 复杂度惩罚（复杂度越高惩罚越大，鼓励简单模板）
        double complexityPenalty = calculateComplexityPenalty(
            template.getComplexityScore()
        );

        return new TemplateMatchResult(
            template,
            keywordScore,
            categoryScore,
            urlScore,
            complexityPenalty
        );
    }

    /**
     * 计算Jaccard相似度
     *
     * 原理：Jaccard(A, B) = |A ∩ B| / |A ∪ B|
     *
     * @param userKeywords 用户关键词集合
     * @param templateKeywords 模板关键词集合
     * @return 相似度（0-1）
     */
    private double calculateKeywordSimilarity(
        List<String> userKeywords,
        List<String> templateKeywords
    ) {
        if (userKeywords == null || userKeywords.isEmpty() ||
            templateKeywords == null || templateKeywords.isEmpty()) {
            return 0.0;
        }

        // 转换为小写集合（忽略大小写）
        Set<String> set1 = userKeywords.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

        Set<String> set2 = templateKeywords.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

        // 计算交集
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        // 计算并集
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        // Jaccard相似度
        double similarity = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();

        log.debug("关键词相似度计算 - 交集: {}, 并集: {}, 相似度: {}",
                  intersection.size(), union.size(), similarity);

        return similarity;
    }

    /**
     * 计算URL相似度
     *
     * 策略：
     * 1. 如果用户未提供URL，返回0
     * 2. 如果模板未提供URL，返回0
     * 3. 如果域名完全匹配，返回1.0
     * 4. 如果域名部分匹配，返回0.5
     *
     * @param userUrl 用户提供的URL
     * @param templateUrl 模板参考URL
     * @return 相似度（0-1）
     */
    private double calculateUrlSimilarity(String userUrl, String templateUrl) {
        if (userUrl == null || userUrl.trim().isEmpty() ||
            templateUrl == null || templateUrl.trim().isEmpty()) {
            return 0.0;
        }

        // 提取域名（简化处理）
        String userDomain = extractDomain(userUrl.toLowerCase());
        String templateDomain = extractDomain(templateUrl.toLowerCase());

        // 完全匹配
        if (userDomain.equals(templateDomain)) {
            log.debug("URL完全匹配: {} == {}", userDomain, templateDomain);
            return 1.0;
        }

        // 部分匹配（去除子域名后比较）
        String userMainDomain = extractMainDomain(userDomain);
        String templateMainDomain = extractMainDomain(templateDomain);

        if (userMainDomain.equals(templateMainDomain)) {
            log.debug("URL部分匹配: {} == {}", userMainDomain, templateMainDomain);
            return 0.5;
        }

        return 0.0;
    }

    /**
     * 从URL中提取域名
     *
     * @param url 完整URL
     * @return 域名
     */
    private String extractDomain(String url) {
        // 移除协议
        String domain = url.replaceAll("^(https?://)?", "");
        // 移除路径
        domain = domain.split("/")[0];
        // 移除端口
        domain = domain.split(":")[0];
        return domain;
    }

    /**
     * 提取主域名（去除子域名）
     *
     * 例如：www.airbnb.com → airbnb.com
     *
     * @param domain 完整域名
     * @return 主域名
     */
    private String extractMainDomain(String domain) {
        String[] parts = domain.split("\\.");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return domain;
    }

    /**
     * 计算复杂度惩罚
     *
     * 策略：复杂度越高，惩罚越大
     *
     * @param complexityScore 复杂度评分（1-10）
     * @return 惩罚值（0-1）
     */
    private double calculateComplexityPenalty(Integer complexityScore) {
        if (complexityScore == null) {
            return 0.0;
        }

        // 归一化到0-1区间
        // 复杂度1: 惩罚0.1
        // 复杂度5: 惩罚0.5
        // 复杂度10: 惩罚1.0
        double penalty = complexityScore / 10.0;

        log.debug("复杂度惩罚: {} → {}", complexityScore, penalty);

        return penalty;
    }

    /**
     * 计算分类匹配分数
     *
     * 策略：基于用户关键词推断行业分类，与模板分类进行匹配
     * - 一级分类匹配：1.0
     * - 二级分类匹配：0.5
     * - 不匹配：0.0
     *
     * @param userKeywords 用户关键词列表
     * @param templateCategory 模板一级分类
     * @param templateSubcategory 模板二级分类
     * @return 分类匹配分数（0-1）
     */
    private double calculateCategoryScore(
        List<String> userKeywords,
        String templateCategory,
        String templateSubcategory
    ) {
        if (userKeywords == null || userKeywords.isEmpty() ||
            templateCategory == null || templateCategory.trim().isEmpty()) {
            return 0.0;
        }

        // 转换为小写集合便于匹配
        Set<String> keywords = userKeywords.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

        // 定义行业关键词映射（基于常见业务词汇）
        Map<String, Set<String>> categoryKeywords = new java.util.HashMap<>();

        // 生活服务类
        categoryKeywords.put("生活服务", Set.of(
            "民宿", "预订", "住宿", "酒店", "airbnb", "短租", "房源",
            "美食", "外卖", "餐饮", "饿了么", "美团",
            "出行", "打车", "租车", "滴滴", "uber",
            "家政", "保洁", "维修", "装修"
        ));

        // 电商类
        categoryKeywords.put("电商", Set.of(
            "商城", "购物", "电商", "淘宝", "京东", "拼多多",
            "商品", "订单", "支付", "物流", "快递",
            "店铺", "卖家", "买家", "交易"
        ));

        // 教育类
        categoryKeywords.put("教育", Set.of(
            "教育", "课程", "学习", "培训", "在线教育",
            "老师", "学生", "教学", "考试", "作业",
            "直播", "视频课", "知识付费"
        ));

        // 社交类
        categoryKeywords.put("社交", Set.of(
            "社交", "聊天", "消息", "好友", "群组",
            "微信", "qq", "钉钉", "slack",
            "朋友圈", "动态", "评论", "点赞", "分享"
        ));

        // 企业管理类
        categoryKeywords.put("企业管理", Set.of(
            "crm", "erp", "oa", "协同", "办公",
            "客户", "销售", "项目", "任务", "文档",
            "审批", "工单", "报表", "统计"
        ));

        // 金融科技类
        categoryKeywords.put("金融科技", Set.of(
            "支付", "转账", "理财", "投资", "贷款",
            "钱包", "余额", "银行卡", "信用卡",
            "股票", "基金", "保险", "征信"
        ));

        // 内容媒体类
        categoryKeywords.put("内容媒体", Set.of(
            "博客", "文章", "新闻", "资讯", "阅读",
            "视频", "直播", "音频", "播客",
            "图片", "相册", "社区", "论坛", "知识库"
        ));

        // 计算每个类别的匹配度
        double maxScore = 0.0;
        String matchedCategory = null;

        for (Map.Entry<String, Set<String>> entry : categoryKeywords.entrySet()) {
            String category = entry.getKey();

            // 计算交集数量
            long matchCount = keywords.stream()
                .filter(entry.getValue()::contains)
                .count();

            if (matchCount > 0) {
                // 匹配度 = 匹配的关键词数 / 类别关键词总数
                double score = (double) matchCount / entry.getValue().size();
                if (score > maxScore) {
                    maxScore = score;
                    matchedCategory = category;
                }
            }
        }

        // 根据匹配结果计算最终分数
        if (matchedCategory == null) {
            log.debug("分类匹配: 未找到匹配的类别");
            return 0.0;
        }

        // 一级分类匹配
        if (matchedCategory.equals(templateCategory)) {
            log.debug("分类匹配: 一级分类完全匹配 {} (匹配度: {})",
                    matchedCategory, maxScore);
            return 1.0;
        }

        // 二级分类匹配（如果提供了subcategory）
        if (templateSubcategory != null && !templateSubcategory.trim().isEmpty()) {
            // 检查是否有关键词与subcategory相关
            Set<String> subcategoryWords = Set.of(templateSubcategory.toLowerCase().split(" "));
            long subcategoryMatchCount = keywords.stream()
                .filter(subcategoryWords::contains)
                .count();

            if (subcategoryMatchCount > 0) {
                log.debug("分类匹配: 二级分类匹配 {} (匹配度: {})",
                        templateSubcategory, maxScore);
                return 0.5;
            }
        }

        log.debug("分类匹配: 类别不匹配 (推断: {}, 模板: {})",
                matchedCategory, templateCategory);
        return 0.0;
    }
}
