package com.ingenio.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新需求请求DTO
 */
@Data
public class UpdateRequirementRequest {

    /**
     * 新的需求描述
     */
    @NotBlank(message = "需求描述不能为空")
    private String description;

    /**
     * 是否重新识别意图
     * 默认true，如果只是修改了非关键描述，可以选择不重新识别
     */
    private boolean reanalyzeIntent = true;
}
