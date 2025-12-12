package com.ingenio.backend.agent.dto;

/**
 * 需求复杂度等级枚举 - P3增强功能
 * 用于评估用户需求的技术复杂度，辅助资源分配和时间估算
 *
 * <p>复杂度判断维度：</p>
 * <ul>
 *   <li>实体数量：1-3个（简单），4-8个（中等），9+个（复杂）</li>
 *   <li>关系复杂度：简单CRUD（简单），多对多关系（中等），图状关系（复杂）</li>
 *   <li>AI能力需求：无AI（简单），基础NLP（中等），多模态AI（复杂）</li>
 *   <li>技术风险：成熟技术（简单），常规集成（中等），创新技术（复杂）</li>
 * </ul>
 *
 * @author Ingenio Team
 * @version 2.0.1
 * @since 2025-11-20 P3 Phase 3.1
 */
public enum ComplexityLevel {

    /**
     * 简单需求
     * 特征：
     * - 实体数量≤3个
     * - 简单的CRUD操作
     * - 无AI能力需求或简单文本处理
     * - 使用成熟稳定的技术栈
     *
     * 示例：
     * - "创建一个简单的待办事项列表"
     * - "做一个个人博客系统"
     * - "开发一个通讯录管理应用"
     *
     * 预估开发时间：2-3天
     */
    SIMPLE("simple", "简单",
           "实体≤3，简单CRUD，无AI需求，成熟技术",
           2, 3),

    /**
     * 中等复杂度需求
     * 特征：
     * - 实体数量4-8个
     * - 包含多对多关系或复杂查询
     * - 需要基础AI能力（NLP/推荐/搜索）
     * - 需要常规第三方集成（支付/地图/短信）
     *
     * 示例：
     * - "创建一个电商平台（用户、商品、订单、评论）"
     * - "开发一个在线教育系统（课程、学生、作业、考试）"
     * - "做一个智能问答社区（带AI推荐）"
     *
     * 预估开发时间：4-6天
     */
    MEDIUM("medium", "中等",
           "实体4-8个，复杂关系，基础AI，常规集成",
           4, 6),

    /**
     * 复杂需求
     * 特征：
     * - 实体数量≥9个
     * - 复杂的图状关系或多租户架构
     * - 需要多模态AI能力（视觉/语音/NLP组合）
     * - 创新技术或高性能要求（实时通讯/大数据）
     *
     * 示例：
     * - "创建一个企业级项目管理系统（多租户+工作流）"
     * - "开发一个AI驱动的智能客服（NLP+语音+知识图谱）"
     * - "做一个实时协作白板（WebSocket+Canvas+冲突解决）"
     *
     * 预估开发时间：7+天（可能需要分期）
     */
    COMPLEX("complex", "复杂",
            "实体≥9个，图状关系，多模态AI，创新技术",
            7, 14);

    /**
     * 复杂度代码（用于数据库存储和API传输）
     */
    private final String code;

    /**
     * 复杂度名称（用于展示）
     */
    private final String displayName;

    /**
     * 复杂度描述（特征说明）
     */
    private final String description;

    /**
     * 预估最小开发天数
     */
    private final int minDays;

    /**
     * 预估最大开发天数
     */
    private final int maxDays;

    ComplexityLevel(String code, String displayName, String description,
                    int minDays, int maxDays) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.minDays = minDays;
        this.maxDays = maxDays;
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

    public int getMinDays() {
        return minDays;
    }

    public int getMaxDays() {
        return maxDays;
    }

    /**
     * 获取预估开发时间范围（格式化字符串）
     *
     * @return 时间范围字符串，如"2-3天"或"7天"
     */
    public String getEstimatedTimeRange() {
        if (minDays == maxDays) {
            return minDays + "天";
        }
        return minDays + "-" + maxDays + "天";
    }

    /**
     * 根据代码获取复杂度枚举
     *
     * @param code 复杂度代码
     * @return 复杂度枚举，如果不存在则返回null
     */
    public static ComplexityLevel fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }

        for (ComplexityLevel level : values()) {
            if (level.getCode().equalsIgnoreCase(code.trim())) {
                return level;
            }
        }

        return null;
    }

    /**
     * 判断是否建议分期开发
     *
     * @return true 如果建议分期开发（COMPLEX级别）
     */
    public boolean shouldSplitDevelopment() {
        return this == COMPLEX;
    }

    /**
     * 判断是否需要架构评审
     *
     * @return true 如果需要架构评审（MEDIUM或COMPLEX级别）
     */
    public boolean needsArchitectureReview() {
        return this == MEDIUM || this == COMPLEX;
    }

    /**
     * 判断是否为简单需求
     *
     * @return true 如果为简单需求
     */
    public boolean isSimple() {
        return this == SIMPLE;
    }

    /**
     * 判断是否为中等复杂度需求
     *
     * @return true 如果为中等复杂度需求
     */
    public boolean isMedium() {
        return this == MEDIUM;
    }

    /**
     * 判断是否为复杂需求
     *
     * @return true 如果为复杂需求
     */
    public boolean isComplex() {
        return this == COMPLEX;
    }

    @Override
    public String toString() {
        return String.format("%s(%s): %s [预估%s]",
                displayName, code, description, getEstimatedTimeRange());
    }
}
