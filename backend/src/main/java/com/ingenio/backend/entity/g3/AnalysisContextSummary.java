package com.ingenio.backend.entity.g3;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 需求分析上下文摘要
 *
 * 保存 Step 1-6 分析结果的压缩摘要，供 G3 代码智能体使用。
 *
 * 压缩策略：
 * - 小型项目（<8K tokens）：不压缩，使用完整上下文
 * - 中型项目（8K-32K tokens）：提取摘要 + 关键列表
 * - 大型项目（>32K tokens）：仅保留核心结论 + 统计
 *
 * @author Ingenio Team
 * @since 2.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisContextSummary {

    // ========================================================================
    // 原始需求
    // ========================================================================

    /**
     * 原始需求描述
     */
    private String requirement;

    // ========================================================================
    // Step 1: 产品拆解摘要（产品经理视角）
    // ========================================================================

    /**
     * 核心需求摘要（1-2句话）
     */
    private String productSummary;

    /**
     * 主要用户角色
     */
    private String primaryUserRole;

    /**
     * 用户痛点列表
     */
    @Builder.Default
    private List<String> userPainPoints = new ArrayList<>();

    /**
     * P0 核心功能列表
     */
    @Builder.Default
    private List<String> coreFeatures = new ArrayList<>();

    /**
     * P1/P2 增强功能列表
     */
    @Builder.Default
    private List<String> enhancedFeatures = new ArrayList<>();

    // ========================================================================
    // Step 2: 数据模型摘要（数据架构师视角）
    // ========================================================================

    /**
     * 识别的实体列表
     */
    @Builder.Default
    private List<String> entities = new ArrayList<>();

    /**
     * 实体数量
     */
    private int entitiesCount;

    /**
     * 关系数量
     */
    private int relationshipsCount;

    /**
     * 数据模型摘要（如 "5 实体, 3 关系"）
     */
    private String dataModelDigest;

    // ========================================================================
    // Step 3: API 摘要（业务分析师视角）
    // ========================================================================

    /**
     * API 操作数量
     */
    private int operationsCount;

    /**
     * 关键 API 端点列表
     */
    @Builder.Default
    private List<String> keyEndpoints = new ArrayList<>();

    /**
     * 业务规则数量
     */
    private int businessRulesCount;

    // ========================================================================
    // Step 4: 技术栈（技术负责人视角）
    // ========================================================================

    /**
     * 技术栈（如 "React + Supabase"）
     */
    private String techStack;

    /**
     * 前端框架
     */
    private String uiFramework;

    /**
     * 后端技术
     */
    private String backend;

    /**
     * 数据库
     */
    private String database;

    // ========================================================================
    // Step 5: 交互设计与体验评估 (交互设计师视角)
    // ========================================================================

    /**
     * 复杂度级别（SIMPLE/MEDIUM/COMPLEX）
     */
    private String complexityLevel;

    /**
     * 预估开发天数
     */
    private int estimatedDays;

    /**
     * 预估代码行数
     */
    private int estimatedLines;

    /**
     * 置信度（0.0-1.0）
     */
    private double confidenceScore;

    /**
     * 设计意图 (e.g. DESIGN_FROM_SCRATCH)
     */
    private String designIntent;

    /**
     * 推荐风格列表
     */
    @Builder.Default
    private List<String> recommendedStyles = new ArrayList<>();

    // ========================================================================
    // Step 6: 蓝图摘要（首席架构师视角）
    // ========================================================================

    /**
     * 技术蓝图摘要（200字以内）
     */
    private String blueprintDigest;

    /**
     * 蓝图章节数
     */
    private int blueprintSections;

    // ========================================================================
    // 元信息
    // ========================================================================

    /**
     * 原始上下文 Token 数（估算）
     */
    private long originalTokenCount;

    /**
     * 压缩后 Token 数（估算）
     */
    private long compressedTokenCount;

    /**
     * 压缩率（0.0-1.0）
     */
    private double compressionRatio;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 压缩级别（FULL/MEDIUM/MINIMAL）
     */
    private CompressionLevel compressionLevel;

    /**
     * 压缩级别枚举
     */
    public enum CompressionLevel {
        /**
         * 完整上下文（不压缩）
         */
        FULL,

        /**
         * 中等压缩（摘要 + 关键列表）
         */
        MEDIUM,

        /**
         * 最小压缩（仅核心结论）
         */
        MINIMAL
    }

    // ========================================================================
    // 格式化方法
    // ========================================================================

    /**
     * 将摘要格式化为 Markdown，供 G3 引擎使用
     */
    public String formatAsMarkdown() {
        StringBuilder sb = new StringBuilder();

        sb.append("# 需求分析上下文\n\n");

        // 原始需求
        if (requirement != null && !requirement.isBlank()) {
            sb.append("## 原始需求\n");
            sb.append(truncate(requirement, 500)).append("\n\n");
        }

        // 产品摘要
        if (productSummary != null) {
            sb.append("## 产品概述\n");
            sb.append(productSummary).append("\n\n");

            if (primaryUserRole != null) {
                sb.append("**目标用户**: ").append(primaryUserRole).append("\n\n");
            }
        }

        // 核心功能
        if (coreFeatures != null && !coreFeatures.isEmpty()) {
            sb.append("## 核心功能 (P0)\n");
            for (String feature : coreFeatures) {
                sb.append("- ").append(feature).append("\n");
            }
            sb.append("\n");
        }

        // 数据模型
        if (entities != null && !entities.isEmpty()) {
            sb.append("## 数据模型\n");
            sb.append("**实体**: ").append(String.join(", ", entities)).append("\n");
            if (dataModelDigest != null) {
                sb.append("**摘要**: ").append(dataModelDigest).append("\n");
            }
            sb.append("\n");
        }

        // 技术栈
        if (techStack != null) {
            sb.append("## 技术栈\n");
            sb.append(techStack).append("\n\n");
        }

        // 交互设计
        if (designIntent != null || !recommendedStyles.isEmpty() || complexityLevel != null) {
            sb.append("## 交互设计与体验评估\n");
            if (designIntent != null) {
                sb.append("- **设计意图**: ").append(designIntent).append("\n");
            }
            if (!recommendedStyles.isEmpty()) {
                sb.append("- **推荐风格**: ").append(String.join(", ", recommendedStyles)).append("\n");
            }

            if (complexityLevel != null) {
                sb.append("- **复杂度**: ").append(complexityLevel).append("\n");
                sb.append("- **预估天数**: ").append(estimatedDays).append("\n");
                sb.append("- **置信度**: ").append(String.format("%.0f%%", confidenceScore * 100)).append("\n");
            }
            sb.append("\n");
        }

        // 关键 API
        if (keyEndpoints != null && !keyEndpoints.isEmpty()) {
            sb.append("## 关键 API\n");
            for (String endpoint : keyEndpoints) {
                sb.append("- ").append(endpoint).append("\n");
            }
            sb.append("\n");
        }

        // 元信息
        if (compressionLevel != null) {
            sb.append("---\n");
            sb.append("*压缩级别: ").append(compressionLevel);
            if (compressionRatio > 0) {
                sb.append(String.format(", 压缩率: %.1f%%", compressionRatio * 100));
            }
            sb.append("*\n");
        }

        return sb.toString();
    }

    /**
     * 截断文本
     */
    private String truncate(String text, int maxLength) {
        if (text == null)
            return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 估算 Token 数（简单按字符数/4估算）
     */
    public static long estimateTokens(String text) {
        if (text == null || text.isBlank())
            return 0;
        // 中文按 1.5 token/字，英文按 0.25 token/字，取中间值
        return text.length() / 2;
    }
}
