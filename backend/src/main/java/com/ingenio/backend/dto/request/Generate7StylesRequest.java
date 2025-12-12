package com.ingenio.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 7风格生成请求
 * 用于请求生成7种不同设计风格的HTML预览
 *
 * 对应前端类型: src/types/design-style.ts#Generate7StylesRequest
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Generate7StylesRequest {

    /**
     * 用户需求描述
     * 例如: "创建一个民宿预订平台，支持房源浏览、在线预订、评价管理"
     */
    @NotBlank(message = "用户需求描述不能为空")
    @Size(min = 10, max = 1000, message = "需求描述长度应在10-1000字符之间")
    private String userRequirement;

    /**
     * 应用类型（可选）
     * 例如: "电商平台", "社交应用", "内容管理系统"
     */
    private String appType;

    /**
     * 目标平台（可选）
     * 例如: "web", "mobile", "desktop"
     */
    private String targetPlatform;

    /**
     * 是否使用AI定制
     * 默认false，仅使用模板生成
     * true时会调用AI对每个风格进行定制化调整
     */
    @Builder.Default
    private Boolean useAICustomization = false;
}
