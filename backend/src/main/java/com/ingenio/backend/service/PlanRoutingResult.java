package com.ingenio.backend.service;

import com.ingenio.backend.agent.dto.RequirementIntent;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PlanRoutingResult - Plan阶段路由结果
 *
 * 包含路由决策的完整信息，用于：
 * 1. 前端展示路由结果和下一步指引
 * 2. 记录路由决策的完整追溯信息
 * 3. 协调后续的用户交互流程
 */
@Data
@Builder
public class PlanRoutingResult {

    /**
     * AppSpec ID
     * 新创建或更新的AppSpec实体ID
     */
    private UUID appSpecId;

    /**
     * 识别的意图类型
     * CLONE_EXISTING_WEBSITE / DESIGN_FROM_SCRATCH / HYBRID_CLONE_AND_CUSTOMIZE
     */
    private RequirementIntent intent;

    /**
     * 意图识别置信度
     * 0.0 - 1.0 范围
     */
    private double confidence;

    /**
     * 路由分支
     * CLONE / DESIGN / HYBRID
     */
    private PlanRoutingService.RoutingBranch branch;

    /**
     * 匹配的行业模板结果列表
     * 基于关键词和参考URL匹配，包含匹配分数
     */
    private List<IndustryTemplateMatchingService.TemplateMatchResult> matchedTemplateResults;

    /**
     * 7种风格变体（仅设计分支有值）
     * 包含styleId、styleName、previewHtml等
     */
    private List<Map<String, Object>> styleVariants;

    /**
     * 原型是否已生成
     * true表示可以预览原型URL
     */
    private boolean prototypeGenerated;

    /**
     * 原型预览URL
     * E2B Sandbox部署地址
     */
    private String prototypeUrl;

    /**
     * 原型预览HTML
     * 当使用风格变体时，直接包含完整的HTML代码
     */
    private String prototypeHtml;

    /**
     * 用户选择的风格ID（A-G）
     * 仅设计分支有值
     */
    private String selectedStyleId;

    /**
     * 下一步操作指引
     * 用于前端显示用户需要执行的操作
     */
    private String nextAction;

    /**
     * 是否需要用户确认
     * true表示需要等待用户交互
     */
    private boolean requiresUserConfirmation;

    /**
     * 附加元数据
     * 存储路由过程中的额外信息
     */
    private Map<String, Object> metadata;

    /**
     * 获取路由分支的显示名称
     *
     * @return 中文显示名称
     */
    public String getBranchDisplayName() {
        return branch != null ? branch.getDisplayName() : "未知分支";
    }

    /**
     * 判断是否为设计分支
     *
     * @return true如果是设计分支
     */
    public boolean isDesignBranch() {
        return branch == PlanRoutingService.RoutingBranch.DESIGN;
    }

    /**
     * 判断是否为克隆分支
     *
     * @return true如果是克隆分支
     */
    public boolean isCloneBranch() {
        return branch == PlanRoutingService.RoutingBranch.CLONE;
    }

    /**
     * 判断是否为混合分支
     *
     * @return true如果是混合分支
     */
    public boolean isHybridBranch() {
        return branch == PlanRoutingService.RoutingBranch.HYBRID;
    }

    /**
     * 获取匹配模板数量
     *
     * @return 模板数量
     */
    public int getMatchedTemplateCount() {
        return matchedTemplateResults != null ? matchedTemplateResults.size() : 0;
    }

    /**
     * 获取风格变体数量
     *
     * @return 风格数量
     */
    public int getStyleVariantCount() {
        return styleVariants != null ? styleVariants.size() : 0;
    }
}
