package com.ingenio.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 创建AppSpec请求DTO
 * 用于接收创建AppSpec时提交的数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAppSpecRequest {

    /**
     * AppSpec JSON内容
     * 必填，包含pages、dataModels、flows、permissions等结构
     */
    @NotNull(message = "AppSpec内容不能为空")
    private Map<String, Object> specContent;

    /**
     * 父版本ID
     * 可选，用于创建新版本时指定父版本
     */
    private String parentVersionId;

    /**
     * 元数据
     * 可选，用于存储额外信息
     */
    private Map<String, Object> metadata;
}
