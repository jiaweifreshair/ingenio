package com.ingenio.backend.agent.dto;

/**
 * 需求意图枚举 - V2.0架构核心
 * 用于分类用户需求的真实意图，并智能路由到不同的处理分支
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */
public enum RequirementIntent {

    /**
     * 克隆已有网站
     * 场景：用户需求中明确包含要参考/克隆/仿照的现有网站URL
     * 关键词：仿照、参考、类似于、爬取、克隆、借鉴
     * 示例：
     * - "仿照airbnb.com做一个民宿预订平台"
     * - "克隆GitHub的项目管理功能"
     * - "参考豆瓣读书做一个图书管理系统"
     *
     * 处理流程：
     * 1. 跳过SuperDesign（无需AI设计）
     * 2. 直接使用OpenLovable-CN爬取目标网站
     * 3. 生成可交互的前端原型
     * 4. 等待用户确认后进入Execute阶段
     */
    CLONE_EXISTING_WEBSITE("clone", "克隆已有网站",
            "直接爬取并生成，跳过SuperDesign设计环节"),

    /**
     * 从零开始设计
     * 场景：用户需求中没有参考网站，需要从零开始设计UI
     * 关键词：创建、设计、开发、构建、制作
     * 示例：
     * - "创建一个在线教育平台"
     * - "开发一个任务管理系统"
     * - "做一个电商平台"
     *
     * 处理流程：
     * 1. 调用SuperDesign生成3种风格方案（现代极简、活力时尚、经典专业）
     * 2. 用户选择最满意的方案
     * 3. 使用OpenLovable-CN快速生成选中方案的原型
     * 4. 等待用户确认后进入Execute阶段
     */
    DESIGN_FROM_SCRATCH("design", "从零开始设计",
            "SuperDesign 3风格方案 → 用户选择 → OpenLovable快速原型"),

    /**
     * 混合模式（克隆+定制）
     * 场景：用户需求中既有参考网站，又有定制化修改要求
     * 关键词：基于XX修改、在XX基础上、参考XX但...
     * 示例：
     * - "基于GitHub的设计，修改成我们公司的项目管理系统"
     * - "参考Notion的布局，但增加实时协作功能"
     * - "仿照淘宝首页，但风格要更简洁"
     *
     * 处理流程：
     * 1. 使用OpenLovable-CN爬取参考网站
     * 2. AI分析用户的定制化需求
     * 3. 在爬取结果基础上进行定制化修改
     * 4. 等待用户确认后进入Execute阶段
     */
    HYBRID_CLONE_AND_CUSTOMIZE("hybrid", "混合模式（克隆+定制）",
            "爬取参考网站 → AI定制化修改 → 生成原型");

    /**
     * 意图代码（用于数据库存储和API传输）
     */
    private final String code;

    /**
     * 意图名称（用于展示）
     */
    private final String displayName;

    /**
     * 意图描述（用于说明处理流程）
     */
    private final String description;

    RequirementIntent(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码获取意图枚举
     *
     * @param code 意图代码
     * @return 意图枚举，如果不存在则返回null
     */
    public static RequirementIntent fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }

        for (RequirementIntent intent : values()) {
            if (intent.getCode().equalsIgnoreCase(code.trim())) {
                return intent;
            }
        }

        return null;
    }

    /**
     * 判断是否需要调用SuperDesign
     *
     * @return true 如果需要SuperDesign，false 如果不需要
     */
    public boolean needsSuperDesign() {
        return this == DESIGN_FROM_SCRATCH;
    }

    /**
     * 判断是否需要调用OpenLovable-CN爬取
     *
     * @return true 如果需要爬取，false 如果不需要
     */
    public boolean needsWebCrawling() {
        return this == CLONE_EXISTING_WEBSITE || this == HYBRID_CLONE_AND_CUSTOMIZE;
    }

    /**
     * 判断是否需要AI定制化修改
     *
     * @return true 如果需要定制化，false 如果不需要
     */
    public boolean needsCustomization() {
        return this == HYBRID_CLONE_AND_CUSTOMIZE;
    }

    @Override
    public String toString() {
        return String.format("%s(%s): %s", displayName, code, description);
    }
}
