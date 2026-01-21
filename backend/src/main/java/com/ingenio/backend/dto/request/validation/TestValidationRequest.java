package com.ingenio.backend.dto.request.validation;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 测试验证请求
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
public class TestValidationRequest {

    /**
     * AppSpec ID
     */
    @NotNull(message = "AppSpec ID不能为空")
    private UUID appSpecId;

    /**
     * 测试类型
     * - unit: 单元测试
     * - integration: 集成测试
     * - e2e: 端到端测试
     */
    @NotNull(message = "测试类型不能为空")
    private String testType;

    /**
     * 测试文件列表
     */
    private List<String> testFiles;

    /**
     * 是否生成覆盖率报告
     */
    private Boolean generateCoverage = true;

    public TestValidationRequest() {
    }

    public TestValidationRequest(UUID appSpecId, String testType, List<String> testFiles, Boolean generateCoverage) {
        this.appSpecId = appSpecId;
        this.testType = testType;
        this.testFiles = testFiles;
        this.generateCoverage = generateCoverage != null ? generateCoverage : true;
    }

    public static TestValidationRequestBuilder builder() {
        return new TestValidationRequestBuilder();
    }

    public static class TestValidationRequestBuilder {
        private UUID appSpecId;
        private String testType;
        private List<String> testFiles;
        private Boolean generateCoverage = true;

        public TestValidationRequestBuilder appSpecId(UUID appSpecId) {
            this.appSpecId = appSpecId;
            return this;
        }

        public TestValidationRequestBuilder testType(String testType) {
            this.testType = testType;
            return this;
        }

        public TestValidationRequestBuilder testFiles(List<String> testFiles) {
            this.testFiles = testFiles;
            return this;
        }

        public TestValidationRequestBuilder generateCoverage(Boolean generateCoverage) {
            this.generateCoverage = generateCoverage;
            return this;
        }

        public TestValidationRequest build() {
            return new TestValidationRequest(appSpecId, testType, testFiles, generateCoverage);
        }
    }

    // Getters and Setters
    public UUID getAppSpecId() {
        return appSpecId;
    }

    public void setAppSpecId(UUID appSpecId) {
        this.appSpecId = appSpecId;
    }

    public String getTestType() {
        return testType;
    }

    public void setTestType(String testType) {
        this.testType = testType;
    }

    public List<String> getTestFiles() {
        return testFiles;
    }

    public void setTestFiles(List<String> testFiles) {
        this.testFiles = testFiles;
    }

    public Boolean getGenerateCoverage() {
        return generateCoverage;
    }

    public void setGenerateCoverage(Boolean generateCoverage) {
        this.generateCoverage = generateCoverage;
    }
}
