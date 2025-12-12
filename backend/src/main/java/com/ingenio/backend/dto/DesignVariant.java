package com.ingenio.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * UI设计方案
 *
 * SuperDesign AI生成的单个设计方案
 * 包含代码、预览、资源等完整信息
 */
@Data
@Builder
public class DesignVariant {

    /**
     * 方案标识
     * A / B / C
     */
    private String variantId;

    /**
     * 设计风格描述
     */
    private String style;

    /**
     * 风格关键词
     * 例如: ["现代", "极简", "卡片式"]
     */
    private List<String> styleKeywords;

    /**
     * 生成的Compose UI代码
     */
    private String code;

    /**
     * 代码文件路径（相对路径）
     */
    private String codePath;

    /**
     * 预览图URL或Base64
     */
    private String preview;

    /**
     * 设计资源（图标、图片等）
     */
    private Map<String, String> assets;

    /**
     * 色彩主题
     */
    private ColorTheme colorTheme;

    /**
     * 布局类型
     * card / list / grid / detail
     */
    private String layoutType;

    /**
     * 组件库
     * material3 / material2 / custom
     */
    private String componentLibrary;

    /**
     * 设计特点列表
     */
    private List<String> features;

    /**
     * AI模型生成的完整响应
     */
    private String rawResponse;

    /**
     * 生成耗时（毫秒）
     */
    private Long generationTimeMs;

    /**
     * 色彩主题
     */
    @Data
    @Builder
    public static class ColorTheme {
        /**
         * 主色调
         */
        private String primaryColor;

        /**
         * 次要色
         */
        private String secondaryColor;

        /**
         * 背景色
         */
        private String backgroundColor;

        /**
         * 文字色
         */
        private String textColor;

        /**
         * 强调色
         */
        private String accentColor;

        /**
         * 是否为深色模式
         */
        @Builder.Default
        private Boolean darkMode = false;
    }
}
