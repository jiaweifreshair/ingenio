package com.ingenio.backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 改写后的结构化需求 - P3增强功能
 * 根据意图和复杂度评估结果，对原始需求进行边界优化和细化
 * 使其更加明确、可执行，便于后续代码生成
 *
 * <p>核心价值：</p>
 * <ul>
 *   <li>补充缺失的核心实体和功能</li>
 *   <li>拆分复杂需求为MVP + 扩展功能</li>
 *   <li>明确技术约束和实现边界</li>
 *   <li>优化模糊表述为可执行描述</li>
 * </ul>
 *
 * <p>改写策略矩阵：</p>
 * <table>
 *   <tr>
 *     <th>复杂度等级</th>
 *     <th>改写策略</th>
 *   </tr>
 *   <tr>
 *     <td>SIMPLE</td>
 *     <td>补充细节 - 明确实体、CRUD操作、基本交互流程</td>
 *   </tr>
 *   <tr>
 *     <td>MEDIUM</td>
 *     <td>拆分优先级 - 标注MVP核心功能和后续扩展功能</td>
 *   </tr>
 *   <tr>
 *     <td>COMPLEX</td>
 *     <td>分期建议 - 明确第一期范围，定义分期边界</td>
 *   </tr>
 * </table>
 *
 * @author Ingenio Team
 * @version 2.0.2
 * @since 2025-11-20 P3 Phase 3.4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefinedRequirement {

    /**
     * 改写后的需求文本（结构化、明确、可执行）
     *
     * <p>示例：</p>
     * <pre>
     * 原始需求："做一个博客"
     * 改写后："创建一个个人博客系统，包括文章管理（发布、编辑、删除）、
     *          分类标签、评论功能、Markdown编辑器、代码高亮显示。"
     * </pre>
     */
    private String refinedText;

    /**
     * 提取的核心实体列表
     *
     * <p>示例：</p>
     * <pre>["用户", "文章", "评论", "分类", "标签"]</pre>
     */
    private List<String> coreEntities;

    /**
     * MVP核心功能清单（第一期必须实现）
     *
     * <p>优先级最高的功能，构成最小可用产品</p>
     *
     * <p>示例：</p>
     * <pre>
     * [
     *   "用户注册登录",
     *   "发布文章（支持Markdown）",
     *   "文章列表和详情浏览",
     *   "基础评论功能"
     * ]
     * </pre>
     */
    private List<String> mvpFeatures;

    /**
     * 后续扩展功能（第二期或更晚实现）
     *
     * <p>非核心功能，可以后续迭代添加</p>
     *
     * <p>示例：</p>
     * <pre>
     * [
     *   "文章搜索功能",
     *   "社交分享集成",
     *   "文章点赞收藏",
     *   "用户关注系统"
     * ]
     * </pre>
     */
    private List<String> futureFeatures;

    /**
     * 技术约束声明
     *
     * <p>明确当前生成能力的限制，避免用户产生不切实际的期望</p>
     *
     * <p>示例：</p>
     * <pre>
     * [
     *   "单次生成不支持百万级并发，建议使用云服务扩容",
     *   "暂不支持音视频实时通讯，仅支持文字和图片",
     *   "支付功能需要对接第三方服务，需要额外配置"
     * ]
     * </pre>
     */
    private List<String> technicalConstraints;

    /**
     * 实体关系定义
     *
     * <p>明确实体间的关联关系，便于数据库设计</p>
     *
     * <p>示例：</p>
     * <pre>
     * [
     *   "用户-文章: 一对多（一个用户可以发布多篇文章）",
     *   "文章-评论: 一对多（一篇文章可以有多条评论）",
     *   "文章-标签: 多对多（一篇文章可以有多个标签）"
     * ]
     * </pre>
     */
    private List<String> entityRelationships;

    /**
     * 改写理由（用于向用户解释为何改写）
     *
     * <p>说明为什么对原始需求进行了改写，帮助用户理解优化逻辑</p>
     *
     * <p>示例：</p>
     * <pre>
     * "原始需求'做一个社交平台'过于宽泛，缺少核心功能定义。
     *  已明确为'图片分享社交平台'，参考Instagram核心功能，
     *  将功能拆分为MVP（发布图片、关注、点赞）和扩展功能（私信、Story）。"
     * </pre>
     */
    private String refiningReasoning;

    /**
     * 是否需要用户确认改写结果
     *
     * <p>评估标准：</p>
     * <ul>
     *   <li>true: 重大改写（改变核心功能范围、拆分分期、降低复杂度），需要用户确认</li>
     *   <li>false: 轻微优化（补充细节、明确表述），无需用户确认</li>
     * </ul>
     */
    private boolean needsUserConfirmation;

    /**
     * 改写类型
     *
     * <p>用于分类和统计不同类型的改写</p>
     */
    private RefineType refineType;

    /**
     * 改写类型枚举
     */
    public enum RefineType {
        /** 补充细节 - SIMPLE需求常用 */
        DETAIL_ENHANCEMENT("补充细节", "为简单需求补充缺失的实体和功能细节"),

        /** 拆分优先级 - MEDIUM需求常用 */
        PRIORITY_SPLIT("拆分优先级", "将中等复杂度需求拆分为MVP和扩展功能"),

        /** 分期建议 - COMPLEX需求常用 */
        PHASED_DEVELOPMENT("分期建议", "为复杂需求规划多个开发阶段"),

        /** 降低复杂度 - 不切实际需求 */
        COMPLEXITY_REDUCTION("降低复杂度", "移除不切实际或超出能力范围的功能"),

        /** 明确边界 - 混合意图需求 */
        BOUNDARY_CLARIFICATION("明确边界", "区分参考部分和定制部分，明确功能边界"),

        /** 无需改写 - 需求已足够明确 */
        NO_REFINE_NEEDED("无需改写", "原始需求已足够明确，无需优化");

        private final String displayName;
        private final String description;

        RefineType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 判断改写是否成功
     *
     * @return true 如果改写成功（有改写文本）
     */
    public boolean isSuccessful() {
        return refinedText != null && !refinedText.isEmpty();
    }

    /**
     * 判断是否为重大改写
     *
     * @return true 如果是重大改写
     */
    public boolean isMajorRefine() {
        return refineType == RefineType.PRIORITY_SPLIT
                || refineType == RefineType.PHASED_DEVELOPMENT
                || refineType == RefineType.COMPLEXITY_REDUCTION
                || refineType == RefineType.BOUNDARY_CLARIFICATION;
    }

    /**
     * 添加MVP核心功能
     *
     * @param feature 功能描述
     */
    public void addMvpFeature(String feature) {
        if (this.mvpFeatures == null) {
            this.mvpFeatures = new ArrayList<>();
        }
        this.mvpFeatures.add(feature);
    }

    /**
     * 添加扩展功能
     *
     * @param feature 功能描述
     */
    public void addFutureFeature(String feature) {
        if (this.futureFeatures == null) {
            this.futureFeatures = new ArrayList<>();
        }
        this.futureFeatures.add(feature);
    }

    /**
     * 添加技术约束
     *
     * @param constraint 约束描述
     */
    public void addTechnicalConstraint(String constraint) {
        if (this.technicalConstraints == null) {
            this.technicalConstraints = new ArrayList<>();
        }
        this.technicalConstraints.add(constraint);
    }

    /**
     * 添加核心实体
     *
     * @param entity 实体名称
     */
    public void addCoreEntity(String entity) {
        if (this.coreEntities == null) {
            this.coreEntities = new ArrayList<>();
        }
        this.coreEntities.add(entity);
    }

    /**
     * 添加实体关系
     *
     * @param relationship 关系描述
     */
    public void addEntityRelationship(String relationship) {
        if (this.entityRelationships == null) {
            this.entityRelationships = new ArrayList<>();
        }
        this.entityRelationships.add(relationship);
    }

    /**
     * 获取格式化的改写报告（用于日志和展示）
     *
     * @return 格式化的报告字符串
     */
    public String getFormattedReport() {
        if (!isSuccessful()) {
            return "需求改写失败：缺少改写文本";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【需求改写报告】\n");
        sb.append(String.format("改写类型：%s\n", refineType != null ? refineType.getDisplayName() : "未指定"));
        sb.append(String.format("改写后需求：%s\n", refinedText));

        if (coreEntities != null && !coreEntities.isEmpty()) {
            sb.append(String.format("核心实体：%s\n", String.join(", ", coreEntities)));
        }

        if (mvpFeatures != null && !mvpFeatures.isEmpty()) {
            sb.append("MVP核心功能：\n");
            for (String feature : mvpFeatures) {
                sb.append(String.format("  - %s\n", feature));
            }
        }

        if (futureFeatures != null && !futureFeatures.isEmpty()) {
            sb.append("扩展功能：\n");
            for (String feature : futureFeatures) {
                sb.append(String.format("  - %s\n", feature));
            }
        }

        if (technicalConstraints != null && !technicalConstraints.isEmpty()) {
            sb.append("技术约束：\n");
            for (String constraint : technicalConstraints) {
                sb.append(String.format("  ⚠️ %s\n", constraint));
            }
        }

        if (refiningReasoning != null && !refiningReasoning.isEmpty()) {
            sb.append(String.format("\n改写理由：%s\n", refiningReasoning));
        }

        if (needsUserConfirmation) {
            sb.append("\n⚠️ 该改写为重大调整，建议用户确认后再继续。\n");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return getFormattedReport();
    }
}
