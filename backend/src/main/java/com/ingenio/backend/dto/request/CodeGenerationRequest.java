package com.ingenio.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 代码生成请求DTO
 * 用于流式代码生成API的请求参数
 *
 * @author Ingenio Team
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeGenerationRequest {

    /**
     * AppSpec ID
     * 必填,用于指定要生成代码的应用规范
     */
    @NotNull(message = "appSpecId不能为空")
    private UUID appSpecId;

    /**
     * 是否重新生成
     * 默认为false,如果为true则忽略缓存重新生成
     */
    private Boolean regenerate;

    /**
     * 是否流式输出
     * 默认为true,使用SSE流式推送
     */
    private Boolean streaming;
}
