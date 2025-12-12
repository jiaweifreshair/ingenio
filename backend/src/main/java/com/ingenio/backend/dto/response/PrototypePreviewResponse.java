package com.ingenio.backend.dto.response;

import com.ingenio.backend.agent.dto.RequirementIntent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 原型预览响应
 * 面向前端的精简数据模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrototypePreviewResponse {

    /** 是否成功生成 */
    @Builder.Default
    private Boolean success = Boolean.FALSE;

    /** E2B/VerceI沙箱预览URL */
    private String sandboxUrl;

    /** 沙箱ID */
    private String sandboxId;

    /** 沙箱提供商 */
    private String provider;

    /** 生成耗时（毫秒） */
    private Long generationTime;

    /** 判定的意图类型 */
    private RequirementIntent intentType;

    /** 是否需要爬取参考网站 */
    @Builder.Default
    private Boolean needsCrawling = Boolean.FALSE;

    /** 使用到的参考网站列表 */
    @Builder.Default
    private List<String> referenceUrls = new ArrayList<>();

    /** 设计风格代码 */
    private String designStyle;

    /** 错误信息 */
    private String error;

    /** 警告信息 */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /** V2.0新增：AppSpec ID，用于后续代码生成 */
    private String appSpecId;
}
