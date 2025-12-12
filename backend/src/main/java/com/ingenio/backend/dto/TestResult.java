package com.ingenio.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 测试结果
 *
 * 统一的测试结果结构，支持：
 * - 单元测试（JUnit/Vitest）
 * - E2E测试（Playwright/Cypress）
 * - 集成测试（TestContainers）
 */
@Data
@Builder
public class TestResult {

    /**
     * 测试类型
     * unit / integration / e2e
     */
    private String testType;

    /**
     * 是否全部通过
     */
    @Builder.Default
    private Boolean allPassed = false;

    /**
     * 总测试数
     */
    private Integer totalTests;

    /**
     * 通过的测试数
     */
    private Integer passedTests;

    /**
     * 失败的测试数
     */
    private Integer failedTests;

    /**
     * 跳过的测试数
     */
    private Integer skippedTests;

    /**
     * 测试覆盖率（0-1之间）
     */
    private Double coverage;

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
     * 测试耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 失败的测试用例列表
     */
    private List<TestFailure> failures;

    /**
     * 测试框架
     * JUnit / Vitest / Playwright
     */
    private String framework;

    /**
     * 测试报告路径
     */
    private String reportPath;

    /**
     * 完整输出日志
     */
    private String fullOutput;

    /**
     * 测试失败信息
     */
    @Data
    @Builder
    public static class TestFailure {
        /**
         * 测试套件名称
         */
        private String suiteName;

        /**
         * 测试用例名称
         */
        private String testName;

        /**
         * 失败消息
         */
        private String message;

        /**
         * 堆栈跟踪
         */
        private String stackTrace;

        /**
         * 预期值
         */
        private String expected;

        /**
         * 实际值
         */
        private String actual;

        /**
         * 测试耗时（毫秒）
         */
        private Long durationMs;
    }

    /**
     * 计算通过率
     */
    public double getPassRate() {
        if (totalTests == null || totalTests == 0) {
            return 0.0;
        }
        return (double) passedTests / totalTests;
    }

    /**
     * 是否达到覆盖率目标（85%）
     */
    public boolean meetsC​overageGoal() {
        return coverage != null && coverage >= 0.85;
    }
}
