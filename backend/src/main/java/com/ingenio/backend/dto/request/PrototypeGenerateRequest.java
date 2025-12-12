package com.ingenio.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 原型生成请求DTO
 * 对应首页向导/创建页在风格选择后触发的OpenLovable-CN预览生成
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrototypeGenerateRequest {

    /**
     * 用户自然语言需求描述
     */
    @NotBlank(message = "需求描述不能为空")
    @Size(min = 10, max = 2000, message = "需求描述需在10-2000个字符之间")
    private String userRequirement;

    /**
     * 设计风格代码（对应DesignStyle枚举的code）
     */
    @NotBlank(message = "设计风格不能为空")
    private String designStyle;

    /**
     * 已识别的意图类型（可选，若为空则服务端重新识别）
     */
    private String intentType;

    /**
     * 是否强制重新进行意图识别
     */
    @Builder.Default
    private Boolean forceReclassify = Boolean.FALSE;

    /**
     * 来自前端的参考网站URL列表
     */
    private List<String> referenceUrls;

    /**
     * 用户补充的定制化需求（混合模式）
     */
    private String customizationRequirement;

    /**
     * 选中的行业模板ID（可选）
     */
    private String selectedTemplateId;

    /**
     * 选中的行业模板名称（用于提示词构造）
     */
    private String selectedTemplateName;

    /**
     * 行业模板的参考网站（如果有）
     */
    private String selectedTemplateReferenceUrl;

    /**
     * 复用已有沙箱ID（可选）
     */
    private String sandboxId;
}
