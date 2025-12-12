package com.ingenio.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 风格预览响应
 * 后端返回的单个风格预览数据
 *
 * 对应前端类型: src/types/design-style.ts#StylePreviewResponse
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StylePreviewResponse {

    /**
     * 风格代码
     * 例如: modern_minimal, vibrant_fashion, classic_professional, future_tech,
     *      immersive_3d, gamified, natural_flow
     */
    private String style;

    /**
     * 预览HTML内容
     * 完整的可直接展示的HTML页面
     */
    private String htmlContent;

    /**
     * 预览CSS内容
     * 提取的CSS样式代码（可选，如需单独展示）
     */
    private String cssContent;

    /**
     * 生成时间（毫秒）
     * 单个风格预览的生成耗时
     */
    private Long generationTime;

    /**
     * 是否AI生成
     * false表示使用模板生成
     * true表示使用AI定制化生成
     */
    @Builder.Default
    private Boolean aiGenerated = false;

    /**
     * 预览图URL（可选）
     * 用于快速展示的缩略图URL
     */
    private String thumbnailUrl;

    /**
     * 设计规范（AI生成时提供）
     * 包含颜色主题、排版、布局、组件信息
     *
     * 结构示例：
     * {
     *   "colorTheme": {
     *     "primary": "#6200EE",
     *     "secondary": "#03DAC6",
     *     "background": "#FFFFFF",
     *     "text": "#1a1a1a",
     *     "accent": "#8b5cf6"
     *   },
     *   "typography": {
     *     "fontFamily": "Inter, sans-serif",
     *     "fontSize": "16px",
     *     "lineHeight": "1.6"
     *   },
     *   "layout": {
     *     "type": "card-based",
     *     "spacing": "large",
     *     "borderRadius": "8px"
     *   },
     *   "components": {
     *     "Navbar": {...},
     *     "Hero": {...},
     *     "FeatureGrid": {...},
     *     "CTA": {...},
     *     "Footer": {...}
     *   }
     * }
     */
    private Map<String, Object> designSpec;
}
