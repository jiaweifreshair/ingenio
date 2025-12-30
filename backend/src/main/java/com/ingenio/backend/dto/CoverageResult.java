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
}
