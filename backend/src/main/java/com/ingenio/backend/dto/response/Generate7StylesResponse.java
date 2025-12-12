package com.ingenio.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 7风格生成响应
 * 包含7种设计风格的完整预览列表
 *
 * 对应前端类型: src/types/design-style.ts#Generate7StylesResponse
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Generate7StylesResponse {

    /**
     * 是否成功
     */
    @Builder.Default
    private Boolean success = true;

    /**
     * 7种风格的预览列表
     * 包含: modern_minimal, vibrant_fashion, classic_professional, future_tech,
     *      immersive_3d, gamified, natural_flow
     */
    @Builder.Default
    private List<StylePreviewResponse> styles = new ArrayList<>();

    /**
     * 总生成时间（毫秒）
     * 包含所有7个风格的生成耗时总和
     */
    private Long totalGenerationTime;

    /**
     * 错误信息（如果失败）
     * 仅当success=false时有值
     */
    private String error;

    /**
     * 警告信息列表
     * 例如: 某个风格生成失败但不影响整体结果
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
