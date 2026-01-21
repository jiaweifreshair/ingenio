package com.ingenio.backend.dto.request.validation;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 三环集成验证请求
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
public class FullValidationRequest {

    /**
     * AppSpec ID
     */
    @NotNull(message = "AppSpec ID不能为空")
    private UUID appSpecId;

    /**
     * 租户ID
     */
    @NotNull(message = "租户ID不能为空")
    private UUID tenantId;

    /**
     * 验证阶段列表
     * - compile: 编译验证
     * - test: 测试验证
     * - business: 业务验证
     */
    @NotNull(message = "验证阶段不能为空")
    private List<String> stages;

    /**
     * 代码内容（可选，用于编译验证）
     */
    private String code;

    /**
     * 编程语言（可选，用于编译验证）
     */
    private String language;

    /**
     * 项目根目录（可选，用于覆盖率验证）
     * 示例: /tmp/test-project
     */
    private String projectRoot;

    /**
     * 项目类型（可选，用于覆盖率验证）
     * 支持: nextjs, spring-boot, kmp
     */
    private String projectType;

    /**
     * 测试文件列表（可选，用于测试验证）
     */
    private List<String> testFiles;

    /**
     * 是否并行验证（默认串行）
     */
    private Boolean parallel = false;

    /**
     * 失败快速返回（默认true）
     * true: 某个阶段失败立即返回
     * false: 执行所有阶段后返回
     */
    private Boolean failFast = true;

    public FullValidationRequest() {
    }

    public FullValidationRequest(UUID appSpecId, UUID tenantId, List<String> stages, String code, String language,
            String projectRoot, String projectType, List<String> testFiles, Boolean parallel, Boolean failFast) {
        this.appSpecId = appSpecId;
        this.tenantId = tenantId;
        this.stages = stages;
        this.code = code;
        this.language = language;
        this.projectRoot = projectRoot;
        this.projectType = projectType;
        this.testFiles = testFiles;
        this.parallel = parallel != null ? parallel : false;
        this.failFast = failFast != null ? failFast : true;
    }

    public static FullValidationRequestBuilder builder() {
        return new FullValidationRequestBuilder();
    }

    public static class FullValidationRequestBuilder {
        private UUID appSpecId;
        private UUID tenantId;
        private List<String> stages;
        private String code;
        private String language;
        private String projectRoot;
        private String projectType;
        private List<String> testFiles;
        private Boolean parallel = false;
        private Boolean failFast = true;

        public FullValidationRequestBuilder appSpecId(UUID appSpecId) {
            this.appSpecId = appSpecId;
            return this;
        }

        public FullValidationRequestBuilder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public FullValidationRequestBuilder stages(List<String> stages) {
            this.stages = stages;
            return this;
        }

        public FullValidationRequestBuilder code(String code) {
            this.code = code;
            return this;
        }

        public FullValidationRequestBuilder language(String language) {
            this.language = language;
            return this;
        }

        public FullValidationRequestBuilder projectRoot(String projectRoot) {
            this.projectRoot = projectRoot;
            return this;
        }

        public FullValidationRequestBuilder projectType(String projectType) {
            this.projectType = projectType;
            return this;
        }

        public FullValidationRequestBuilder testFiles(List<String> testFiles) {
            this.testFiles = testFiles;
            return this;
        }

        public FullValidationRequestBuilder parallel(Boolean parallel) {
            this.parallel = parallel;
            return this;
        }

        public FullValidationRequestBuilder failFast(Boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        public FullValidationRequest build() {
            return new FullValidationRequest(appSpecId, tenantId, stages, code, language, projectRoot, projectType,
                    testFiles, parallel, failFast);
        }
    }

    // Getters and Setters
    public UUID getAppSpecId() {
        return appSpecId;
    }

    public void setAppSpecId(UUID appSpecId) {
        this.appSpecId = appSpecId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public List<String> getStages() {
        return stages;
    }

    public void setStages(List<String> stages) {
        this.stages = stages;
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

    public String getProjectRoot() {
        return projectRoot;
    }

    public void setProjectRoot(String projectRoot) {
        this.projectRoot = projectRoot;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public List<String> getTestFiles() {
        return testFiles;
    }

    public void setTestFiles(List<String> testFiles) {
        this.testFiles = testFiles;
    }

    public Boolean getParallel() {
        return parallel;
    }

    public void setParallel(Boolean parallel) {
        this.parallel = parallel;
    }

    public Boolean getFailFast() {
        return failFast;
    }

    public void setFailFast(Boolean failFast) {
        this.failFast = failFast;
    }
}
