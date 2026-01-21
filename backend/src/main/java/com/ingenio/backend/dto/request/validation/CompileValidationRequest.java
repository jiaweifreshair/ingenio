package com.ingenio.backend.dto.request.validation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 编译验证请求
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
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

    public CompileValidationRequest() {
    }

    public CompileValidationRequest(UUID appSpecId, String code, String language, String compilerVersion) {
        this.appSpecId = appSpecId;
        this.code = code;
        this.language = language;
        this.compilerVersion = compilerVersion;
    }

    public static CompileValidationRequestBuilder builder() {
        return new CompileValidationRequestBuilder();
    }

    public static class CompileValidationRequestBuilder {
        private UUID appSpecId;
        private String code;
        private String language;
        private String compilerVersion;

        public CompileValidationRequestBuilder appSpecId(UUID appSpecId) {
            this.appSpecId = appSpecId;
            return this;
        }

        public CompileValidationRequestBuilder code(String code) {
            this.code = code;
            return this;
        }

        public CompileValidationRequestBuilder language(String language) {
            this.language = language;
            return this;
        }

        public CompileValidationRequestBuilder compilerVersion(String compilerVersion) {
            this.compilerVersion = compilerVersion;
            return this;
        }

        public CompileValidationRequest build() {
            return new CompileValidationRequest(appSpecId, code, language, compilerVersion);
        }
    }

    // Getters and Setters
    public UUID getAppSpecId() {
        return appSpecId;
    }

    public void setAppSpecId(UUID appSpecId) {
        this.appSpecId = appSpecId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCompilerVersion() {
        return compilerVersion;
    }

    public void setCompilerVersion(String compilerVersion) {
        this.compilerVersion = compilerVersion;
    }
}
