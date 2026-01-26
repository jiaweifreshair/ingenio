package com.ingenio.backend.dto.request;

import lombok.Data;

/**
 * 重新生成请求DTO
 */
@Data
public class RegenerateRequest {

    /**
     * 选择的模板ID（可选）
     */
    private String selectedTemplateId;

    /**
     * 选择的设计风格（可选）
     */
    private String selectedStyle;

    /**
     * 是否重新生成原型
     * 默认true，如果只想重新生成后端代码，可设置为false（暂未支持）
     */
    private boolean regeneratePrototype = true;

    /**
     * 更新的需求描述（可选）
     * 如果提供，将更新新版本的需求描述
     */
    private String userRequirement;
}
