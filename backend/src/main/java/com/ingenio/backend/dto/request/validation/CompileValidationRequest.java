package com.ingenio.backend.dto.request.validation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 编译验证请求
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompileValidationRequest {

    /**
     * AppSpec ID
     */
    @NotNull(message = "AppSpec ID不能为空")
    private UUID appSpecId;

    /**
     * 要验证的代码
     */
    @NotBlank(message = "代码内容不能为空")
    private String code;

    /**
     * 编程语言
     * - typescript
     * - java
     * - kotlin
     */
    @NotBlank(message = "编程语言不能为空")
    private String language;

    /**
     * 编译器版本（可选）
     */
    private String compilerVersion;
}
