package com.ingenio.backend.dto;

import java.util.List;

/**
 * 测试结果
 *
 * 统一的测试结果结构，支持：
 * - 单元测试（JUnit/Vitest）
 * - E2E测试（Playwright/Cypress）
 * - 集成测试（TestContainers）
 */
public class TestResult {

    /**
     * 测试类型
     * unit / integration / e2e
     */
    private String testType;

    /**
     * 是否全部通过
     */
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

    public TestResult() {
    }

    public TestResult(String testType, Boolean allPassed, Integer totalTests, Integer passedTests, Integer failedTests,
            Integer skippedTests, Double coverage, Double lineCoverage, Double branchCoverage, Double functionCoverage,
            Long durationMs, List<TestFailure> failures, String framework, String reportPath, String fullOutput) {
        this.testType = testType;
        this.allPassed = allPassed != null ? allPassed : false;
        this.totalTests = totalTests;
        this.passedTests = passedTests;
        this.failedTests = failedTests;
        this.skippedTests = skippedTests;
        this.coverage = coverage;
        this.lineCoverage = lineCoverage;
        this.branchCoverage = branchCoverage;
        this.functionCoverage = functionCoverage;
        this.durationMs = durationMs;
        this.failures = failures;
        this.framework = framework;
        this.reportPath = reportPath;
        this.fullOutput = fullOutput;
    }

    public static TestResultBuilder builder() {
        return new TestResultBuilder();
    }

    public static class TestResultBuilder {
        private String testType;
        private Boolean allPassed = false;
        private Integer totalTests;
        private Integer passedTests;
        private Integer failedTests;
        private Integer skippedTests;
        private Double coverage;
        private Double lineCoverage;
        private Double branchCoverage;
        private Double functionCoverage;
        private Long durationMs;
        private List<TestFailure> failures;
        private String framework;
        private String reportPath;
        private String fullOutput;

        public TestResultBuilder testType(String testType) {
            this.testType = testType;
            return this;
        }

        public TestResultBuilder allPassed(Boolean allPassed) {
            this.allPassed = allPassed;
            return this;
        }

        public TestResultBuilder totalTests(Integer totalTests) {
            this.totalTests = totalTests;
            return this;
        }

        public TestResultBuilder passedTests(Integer passedTests) {
            this.passedTests = passedTests;
            return this;
        }

        public TestResultBuilder failedTests(Integer failedTests) {
            this.failedTests = failedTests;
            return this;
        }

        public TestResultBuilder skippedTests(Integer skippedTests) {
            this.skippedTests = skippedTests;
            return this;
        }

        public TestResultBuilder coverage(Double coverage) {
            this.coverage = coverage;
            return this;
        }

        public TestResultBuilder lineCoverage(Double lineCoverage) {
            this.lineCoverage = lineCoverage;
            return this;
        }

        public TestResultBuilder branchCoverage(Double branchCoverage) {
            this.branchCoverage = branchCoverage;
            return this;
        }

        public TestResultBuilder functionCoverage(Double functionCoverage) {
            this.functionCoverage = functionCoverage;
            return this;
        }

        public TestResultBuilder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public TestResultBuilder failures(List<TestFailure> failures) {
            this.failures = failures;
            return this;
        }

        public TestResultBuilder framework(String framework) {
            this.framework = framework;
            return this;
        }

        public TestResultBuilder reportPath(String reportPath) {
            this.reportPath = reportPath;
            return this;
        }

        public TestResultBuilder fullOutput(String fullOutput) {
            this.fullOutput = fullOutput;
            return this;
        }

        public TestResult build() {
            return new TestResult(testType, allPassed, totalTests, passedTests, failedTests, skippedTests, coverage,
                    lineCoverage, branchCoverage, functionCoverage, durationMs, failures, framework, reportPath,
                    fullOutput);
        }
    }

    // Getters and Setters
    public String getTestType() {
        return testType;
    }

    public void setTestType(String testType) {
        this.testType = testType;
    }

    public Boolean getAllPassed() {
        return allPassed;
    }

    public void setAllPassed(Boolean allPassed) {
        this.allPassed = allPassed;
    }

    public Integer getTotalTests() {
        return totalTests;
    }

    public void setTotalTests(Integer totalTests) {
        this.totalTests = totalTests;
    }

    public Integer getPassedTests() {
        return passedTests;
    }

    public void setPassedTests(Integer passedTests) {
        this.passedTests = passedTests;
    }

    public Integer getFailedTests() {
        return failedTests;
    }

    public void setFailedTests(Integer failedTests) {
        this.failedTests = failedTests;
    }

    public Integer getSkippedTests() {
        return skippedTests;
    }

    public void setSkippedTests(Integer skippedTests) {
        this.skippedTests = skippedTests;
    }

    public Double getCoverage() {
        return coverage;
    }

    public void setCoverage(Double coverage) {
        this.coverage = coverage;
    }

    public Double getLineCoverage() {
        return lineCoverage;
    }

    public void setLineCoverage(Double lineCoverage) {
        this.lineCoverage = lineCoverage;
    }

    public Double getBranchCoverage() {
        return branchCoverage;
    }

    public void setBranchCoverage(Double branchCoverage) {
        this.branchCoverage = branchCoverage;
    }

    public Double getFunctionCoverage() {
        return functionCoverage;
    }

    public void setFunctionCoverage(Double functionCoverage) {
        this.functionCoverage = functionCoverage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public List<TestFailure> getFailures() {
        return failures;
    }

    public void setFailures(List<TestFailure> failures) {
        this.failures = failures;
    }

    public String getFramework() {
        return framework;
    }

    public void setFramework(String framework) {
        this.framework = framework;
    }

    public String getReportPath() {
        return reportPath;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }

    public String getFullOutput() {
        return fullOutput;
    }

    public void setFullOutput(String fullOutput) {
        this.fullOutput = fullOutput;
    }

    /**
     * 测试失败信息
     */
    public static class TestFailure {
        private String suiteName;
        private String testName;
        private String message;
        private String stackTrace;
        private String expected;
        private String actual;
        private Long durationMs;

        public TestFailure() {
        }

        public TestFailure(String suiteName, String testName, String message, String stackTrace, String expected,
                String actual, Long durationMs) {
            this.suiteName = suiteName;
            this.testName = testName;
            this.message = message;
            this.stackTrace = stackTrace;
            this.expected = expected;
            this.actual = actual;
            this.durationMs = durationMs;
        }

        public static TestFailureBuilder builder() {
            return new TestFailureBuilder();
        }

        public static class TestFailureBuilder {
            private String suiteName;
            private String testName;
            private String message;
            private String stackTrace;
            private String expected;
            private String actual;
            private Long durationMs;

            public TestFailureBuilder suiteName(String suiteName) {
                this.suiteName = suiteName;
                return this;
            }

            public TestFailureBuilder testName(String testName) {
                this.testName = testName;
                return this;
            }

            public TestFailureBuilder message(String message) {
                this.message = message;
                return this;
            }

            public TestFailureBuilder stackTrace(String stackTrace) {
                this.stackTrace = stackTrace;
                return this;
            }

            public TestFailureBuilder expected(String expected) {
                this.expected = expected;
                return this;
            }

            public TestFailureBuilder actual(String actual) {
                this.actual = actual;
                return this;
            }

            public TestFailureBuilder durationMs(Long durationMs) {
                this.durationMs = durationMs;
                return this;
            }

            public TestFailure build() {
                return new TestFailure(suiteName, testName, message, stackTrace, expected, actual, durationMs);
            }
        }

        public String getSuiteName() {
            return suiteName;
        }

        public void setSuiteName(String suiteName) {
            this.suiteName = suiteName;
        }

        public String getTestName() {
            return testName;
        }

        public void setTestName(String testName) {
            this.testName = testName;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getStackTrace() {
            return stackTrace;
        }

        public void setStackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
        }

        public String getExpected() {
            return expected;
        }

        public void setExpected(String expected) {
            this.expected = expected;
        }

        public String getActual() {
            return actual;
        }

        public void setActual(String actual) {
            this.actual = actual;
        }

        public Long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(Long durationMs) {
            this.durationMs = durationMs;
        }
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
    public boolean meetsCoverageGoal() {
        return coverage != null && coverage >= 0.85;
    }
}
