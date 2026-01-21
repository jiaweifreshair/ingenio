package com.ingenio.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 覆盖率计算结果
 *
 * 统一封装Istanbul（JavaScript/TypeScript）和JaCoCo（Java）的覆盖率数据
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
@Data
@Builder
public class CoverageResult {

    /**
     * 项目类型（nextjs/spring-boot/kmp）
     */
    private String projectType;

    /**
     * 覆盖率工具（istanbul/jacoco/kover）
     */
    private String tool;

    /**
     * 总体覆盖率（0.0 - 1.0）
     */
    private Double overallCoverage;

    /**
     * 行覆盖率（0.0 - 1.0）
     */
    private Double lineCoverage;

    /**
     * 分支覆盖率（0.0 - 1.0）
     */
    private Double branchCoverage;

    /**
     * 函数覆盖率（0.0 - 1.0）
     */
    private Double functionCoverage;

    /**
     * 语句覆盖率（0.0 - 1.0）- 仅JavaScript/TypeScript
     */
    private Double statementCoverage;

    /**
     * 是否满足质量门禁（覆盖率≥85%）
     */
    private Boolean meetsQualityGate;

    /**
     * 覆盖率报告路径
     */
    private String reportPath;

    /**
     * 详细覆盖率数据（按文件）
     */
    private Map<String, FileCoverage> filesCoverage;

    /**
     * 构建器静态工厂方法 - 覆盖率为0的结果（报告缺失场景）
     */
    public static CoverageResult zero(String projectType, String tool) {
        return CoverageResult.builder()
                .projectType(projectType)
                .tool(tool)
                .overallCoverage(0.0)
                .lineCoverage(0.0)
                .branchCoverage(0.0)
                .functionCoverage(0.0)
                .statementCoverage(0.0)
                .meetsQualityGate(false)
                .reportPath(null)
                .filesCoverage(Map.of())
                .build();
    }

    /**
     * 单个文件的覆盖率数据
     */
    @Data
    @Builder
    public static class FileCoverage {
        /**
         * 文件路径
         */
        private String path;

        /**
         * 行覆盖率
         */
        private Double lineCoverage;

        /**
         * 分支覆盖率
         */
        private Double branchCoverage;

        /**
         * 函数覆盖率
         */
        private Double functionCoverage;

        /**
         * 未覆盖的行号列表
         */
        private java.util.List<Integer> uncoveredLines;
    }

    // Manual Boilerplate for Lombok Failure
    public CoverageResult(String projectType, String tool, Double overallCoverage, Double lineCoverage,
            Double branchCoverage, Double functionCoverage, Double statementCoverage, Boolean meetsQualityGate,
            String reportPath, Map<String, FileCoverage> filesCoverage) {
        this.projectType = projectType;
        this.tool = tool;
        this.overallCoverage = overallCoverage;
        this.lineCoverage = lineCoverage;
        this.branchCoverage = branchCoverage;
        this.functionCoverage = functionCoverage;
        this.statementCoverage = statementCoverage;
        this.meetsQualityGate = meetsQualityGate;
        this.reportPath = reportPath;
        this.filesCoverage = filesCoverage;
    }

    public CoverageResult() {
    }

    public static CoverageResultBuilder builder() {
        return new CoverageResultBuilder();
    }

    public String getProjectType() {
        return projectType;
    }

    public String getTool() {
        return tool;
    }

    public Double getOverallCoverage() {
        return overallCoverage;
    }

    public Double getLineCoverage() {
        return lineCoverage;
    }

    public Double getBranchCoverage() {
        return branchCoverage;
    }

    public Double getFunctionCoverage() {
        return functionCoverage;
    }

    public Double getStatementCoverage() {
        return statementCoverage;
    }

    public Boolean getMeetsQualityGate() {
        return meetsQualityGate;
    }

    public String getReportPath() {
        return reportPath;
    }

    public Map<String, FileCoverage> getFilesCoverage() {
        return filesCoverage;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public void setOverallCoverage(Double overallCoverage) {
        this.overallCoverage = overallCoverage;
    }

    public void setLineCoverage(Double lineCoverage) {
        this.lineCoverage = lineCoverage;
    }

    public void setBranchCoverage(Double branchCoverage) {
        this.branchCoverage = branchCoverage;
    }

    public void setFunctionCoverage(Double functionCoverage) {
        this.functionCoverage = functionCoverage;
    }

    public void setStatementCoverage(Double statementCoverage) {
        this.statementCoverage = statementCoverage;
    }

    public void setMeetsQualityGate(Boolean meetsQualityGate) {
        this.meetsQualityGate = meetsQualityGate;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }

    public void setFilesCoverage(Map<String, FileCoverage> filesCoverage) {
        this.filesCoverage = filesCoverage;
    }

    public static class CoverageResultBuilder {
        private String projectType;
        private String tool;
        private Double overallCoverage;
        private Double lineCoverage;
        private Double branchCoverage;
        private Double functionCoverage;
        private Double statementCoverage;
        private Boolean meetsQualityGate;
        private String reportPath;
        private Map<String, FileCoverage> filesCoverage;

        CoverageResultBuilder() {}

        public CoverageResultBuilder projectType(String projectType) {
            this.projectType = projectType;
            return this;
        }

        public CoverageResultBuilder tool(String tool) {
            this.tool = tool;
            return this;
        }

        public CoverageResultBuilder overallCoverage(Double overallCoverage) {
            this.overallCoverage = overallCoverage;
            return this;
        }

        public CoverageResultBuilder lineCoverage(Double lineCoverage) {
            this.lineCoverage = lineCoverage;
            return this;
        }

        public CoverageResultBuilder branchCoverage(Double branchCoverage) {
            this.branchCoverage = branchCoverage;
            return this;
        }

        public CoverageResultBuilder functionCoverage(Double functionCoverage) {
            this.functionCoverage = functionCoverage;
            return this;
        }

        public CoverageResultBuilder statementCoverage(Double statementCoverage) {
            this.statementCoverage = statementCoverage;
            return this;
        }

        public CoverageResultBuilder meetsQualityGate(Boolean meetsQualityGate) {
            this.meetsQualityGate = meetsQualityGate;
            return this;
        }

        public CoverageResultBuilder reportPath(String reportPath) {
            this.reportPath = reportPath;
            return this;
        }

        public CoverageResultBuilder filesCoverage(Map<String, FileCoverage> filesCoverage) {
            this.filesCoverage = filesCoverage;
            return this;
        }

        public CoverageResult build() {
            return new CoverageResult(projectType, tool, overallCoverage, lineCoverage, branchCoverage,
                    functionCoverage, statementCoverage, meetsQualityGate, reportPath, filesCoverage);
        }
    }

}
