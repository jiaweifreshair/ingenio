package com.ingenio.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 完整生成流程请求DTO
 * 用于从自然语言需求生成完整的AppSpec和代码
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateFullRequest {

    /**
     * 用户需求（自然语言）
     * 必填，长度10-5000字符
     */
    @NotBlank(message = "用户需求不能为空")
    @Size(min = 10, max = 5000, message = "用户需求长度必须在10-5000字符之间")
    private String userRequirement;

    /**
     * 年龄分组：elementary/middle_school/high_school/university
     * 可选，用于调整UI复杂度和交互方式
     */
    private String ageGroup;

    /**
     * 是否跳过验证
     * 可选，默认为false
     * 如果为true，即使验证未通过也会生成代码
     */
    @Builder.Default
    private Boolean skipValidation = false;

    /**
     * 质量评分阈值
     * 可选，默认为60
     * 验证时低于此阈值会拒绝生成代码
     */
    @Builder.Default
    private Integer qualityThreshold = 60;

    /**
     * 是否生成预览
     * 可选，默认为true
     * 如果为false，仅生成AppSpec，不生成代码
     */
    @Builder.Default
    private Boolean generatePreview = true;

    /**
     * 包名（如com.example.myapp）
     * 可选，用于Kotlin代码生成
     * 如果不提供，将根据应用名称自动生成
     */
    private String packageName;

    /**
     * 应用名称（如"我的AI助手"）
     * 可选，用于AI代码生成和项目命名
     * 如果不提供，将从AppSpec中提取或使用默认值
     */
    private String appName;

    /**
     * 额外配置
     * 可选，用于传递额外的生成参数
     */
    private Map<String, Object> options;

    /**
     * 目标平台
     * 可选，默认WEB
     * 支持: WEB, ANDROID, IOS
     */
    private String platform;
}
