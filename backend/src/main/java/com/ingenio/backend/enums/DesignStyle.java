package com.ingenio.backend.enums;

/**
 * 设计风格枚举
 * 定义SuperDesign支持的7种设计风格
 *
 * 对应前端类型: src/types/design-style.ts#DesignStyle
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */
public enum DesignStyle {

    /**
     * A: 现代极简 - 大留白、卡片式、简洁图标
     */
    MODERN_MINIMAL("modern_minimal", "A", "现代极简", "大留白、卡片式布局、简洁图标，强调内容本身"),

    /**
     * B: 活力时尚 - 渐变色彩、圆角设计、网格布局
     */
    VIBRANT_FASHION("vibrant_fashion", "B", "活力时尚", "渐变色彩、圆角设计、网格布局，充满活力和现代感"),

    /**
     * C: 经典专业 - 传统布局、信息密集、列表式
     */
    CLASSIC_PROFESSIONAL("classic_professional", "C", "经典专业", "传统布局、信息密集、列表式展示，强调专业性和信息量"),

    /**
     * D: 未来科技 - 深色主题、霓虹色彩、3D元素
     */
    FUTURE_TECH("future_tech", "D", "未来科技", "深色主题、霓虹色彩、3D元素，展现科技感和未来感"),

    /**
     * E: 沉浸式3D - 毛玻璃、深度阴影、视差滚动
     */
    IMMERSIVE_3D("immersive_3d", "E", "沉浸式3D", "毛玻璃效果、深度阴影、视差滚动，打造沉浸式体验"),

    /**
     * F: 游戏化设计 - 卡通风格、奖励反馈、成就系统
     */
    GAMIFIED("gamified", "F", "游戏化设计", "卡通风格、奖励反馈、成就系统，趣味性和互动性强"),

    /**
     * G: 自然流动 - 有机曲线、自然配色、流体动画
     */
    NATURAL_FLOW("natural_flow", "G", "自然流动", "有机曲线、自然配色、流体动画，展现自然和谐之美");

    /**
     * 风格代码（用于API传输）
     * 例如: modern_minimal
     */
    private final String code;

    /**
     * 风格标识符 (A-G)
     * 用于简短引用
     */
    private final String identifier;

    /**
     * 显示名称（中文）
     * 用于UI展示
     */
    private final String displayName;

    /**
     * 详细描述
     * 风格特征说明
     */
    private final String description;

    DesignStyle(String code, String identifier, String displayName, String description) {
        this.code = code;
        this.identifier = identifier;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码获取设计风格
     *
     * @param code 风格代码 (例如: "modern_minimal")
     * @return 对应的DesignStyle枚举，如果不存在则返回null
     */
    public static DesignStyle fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (DesignStyle style : values()) {
            if (style.code.equals(code)) {
                return style;
            }
        }
        return null;
    }

    /**
     * 根据标识符获取设计风格
     *
     * @param identifier 风格标识符 (例如: "A")
     * @return 对应的DesignStyle枚举，如果不存在则返回null
     */
    public static DesignStyle fromIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return null;
        }
        for (DesignStyle style : values()) {
            if (style.identifier.equals(identifier)) {
                return style;
            }
        }
        return null;
    }

    /**
     * 获取所有风格代码列表
     *
     * @return 风格代码数组
     */
    public static String[] getAllCodes() {
        DesignStyle[] styles = values();
        String[] codes = new String[styles.length];
        for (int i = 0; i < styles.length; i++) {
            codes[i] = styles[i].code;
        }
        return codes;
    }
}
