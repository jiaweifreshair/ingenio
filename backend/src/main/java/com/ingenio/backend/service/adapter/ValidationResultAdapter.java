package com.ingenio.backend.service.adapter;

import com.ingenio.backend.dto.CompilationResult;
import com.ingenio.backend.dto.TestResult;
import com.ingenio.backend.dto.response.validation.ValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 验证结果适配器
 *
 * 将CompilationResult适配为ValidationResponse
 * 解决DTO不兼容问题：
 * - CompilationResult: success, compiler, errors, warnings, durationMs
 * - ValidationResponse: validationId, passed, status, qualityScore, errors,
 * warnings
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 1
 */
@Component
public class ValidationResultAdapter {

    private static final Logger log = LoggerFactory.getLogger(ValidationResultAdapter.class);

    /**
     * 将CompilationResult转换为ValidationResponse
     *
     * 转换逻辑：
     * 1. success → passed
     * 2. success → status (PASSED/FAILED)
     * 3. errors/warnings → qualityScore (100 - errors*10 - warnings*2)
     * 4. CompilationError列表 → String错误消息列表
     * 5. CompilationWarning列表 → String警告消息列表
     * 6. 构建详情Map（包含compiler、compilerVersion、outputDirectory等）
     *
     * @param compilationResult 编译结果
     * @param appSpecId         AppSpec ID（用于关联）
     * @return 验证响应
     */
    public ValidationResponse toValidationResponse(
            CompilationResult compilationResult,
            UUID appSpecId) {
        log.info("开始适配CompilationResult → ValidationResponse: success={}, errors={}, warnings={}",
                compilationResult.getSuccess(),
                compilationResult.getErrorCount(),
                compilationResult.getWarningCount());

        // 1. 生成验证ID和时间戳
        UUID validationId = UUID.randomUUID();
        Instant completedAt = Instant.now();

        // 2. 确定验证状态
        String status = compilationResult.getSuccess() ? "PASSED" : "FAILED";

        // 3. 计算质量评分（0-100）
        int qualityScore = calculateQualityScore(
                compilationResult.getSuccess(),
                compilationResult.getErrorCount(),
                compilationResult.getWarningCount());

        // 4. 转换错误消息
        List<String> errors = convertErrors(compilationResult.getErrors());

        // 5. 转换警告消息
        List<String> warnings = convertWarnings(compilationResult.getWarnings());

        // 6. 构建详情Map
        Map<String, Object> details = buildDetails(compilationResult, appSpecId);

        // 7. 构建ValidationResponse
        ValidationResponse response = ValidationResponse.builder()
                .validationId(validationId)
                .validationType("compile")
                .passed(compilationResult.getSuccess())
                .status(status)
                .qualityScore(qualityScore)
                .details(details)
                .errors(errors)
                .warnings(warnings)
                .durationMs(compilationResult.getDurationMs())
                .completedAt(completedAt)
                .build();

        log.info("适配完成: validationId={}, passed={}, qualityScore={}, errors={}, warnings={}",
                validationId, response.getPassed(), qualityScore, errors.size(), warnings.size());

        return response;
    }

    /**
     * 计算质量评分
     *
     * 评分规则：
     * - 基础分: 100分
     * - 每个错误: -10分
     * - 每个警告: -2分
     * - 最低分: 0分
     * - 编译失败但无错误: 50分（兜底）
     *
     * @param success      是否成功
     * @param errorCount   错误数量
     * @param warningCount 警告数量
     * @return 质量评分（0-100）
     */
    private int calculateQualityScore(boolean success, int errorCount, int warningCount) {
        if (!success && errorCount == 0) {
            // 编译失败但没有解析到具体错误（兜底情况）
            log.warn("编译失败但errorCount=0，使用兜底评分50");
            return 50;
        }

        int score = 100;

        // 每个错误扣10分
        score -= errorCount * 10;

        // 每个警告扣2分
        score -= warningCount * 2;

        // 确保分数不低于0
        score = Math.max(0, score);

        log.debug("质量评分计算: errors={}, warnings={}, score={}",
                errorCount, warningCount, score);

        return score;
    }

    /**
     * 转换编译错误为简洁的错误消息列表
     *
     * 格式: "文件路径:行号:列号 - 错误消息 (错误代码)"
     * 示例: "src/Main.java:10:5 - Cannot find symbol 'foo' (TS2304)"
     *
     * @param compilationErrors 编译错误列表
     * @return 错误消息列表
     */
    private List<String> convertErrors(List<CompilationResult.CompilationError> compilationErrors) {
        if (compilationErrors == null || compilationErrors.isEmpty()) {
            return new ArrayList<>();
        }

        return compilationErrors.stream()
                .map(this::formatCompilationError)
                .collect(Collectors.toList());
    }

    /**
     * 格式化单个编译错误
     */
    private String formatCompilationError(CompilationResult.CompilationError error) {
        StringBuilder sb = new StringBuilder();

        // 文件路径:行号:列号
        if (error.getFilePath() != null) {
            sb.append(error.getFilePath());

            if (error.getLineNumber() != null) {
                sb.append(":").append(error.getLineNumber());

                if (error.getColumnNumber() != null) {
                    sb.append(":").append(error.getColumnNumber());
                }
            }

            sb.append(" - ");
        }

        // 错误消息
        sb.append(error.getMessage());

        // 错误代码（可选）
        if (error.getErrorCode() != null) {
            sb.append(" (").append(error.getErrorCode()).append(")");
        }

        return sb.toString();
    }

    /**
     * 转换编译警告为简洁的警告消息列表
     *
     * 格式: "文件路径:行号 - 警告消息 (警告代码)"
     * 示例: "src/Main.java:15 - Unused variable 'bar' (WARN001)"
     *
     * @param compilationWarnings 编译警告列表
     * @return 警告消息列表
     */
    private List<String> convertWarnings(List<CompilationResult.CompilationWarning> compilationWarnings) {
        if (compilationWarnings == null || compilationWarnings.isEmpty()) {
            return new ArrayList<>();
        }

        return compilationWarnings.stream()
                .map(this::formatCompilationWarning)
                .collect(Collectors.toList());
    }

    /**
     * 格式化单个编译警告
     */
    private String formatCompilationWarning(CompilationResult.CompilationWarning warning) {
        StringBuilder sb = new StringBuilder();

        // 文件路径:行号
        if (warning.getFilePath() != null) {
            sb.append(warning.getFilePath());

            if (warning.getLineNumber() != null) {
                sb.append(":").append(warning.getLineNumber());
            }

            sb.append(" - ");
        }

        // 警告消息
        sb.append(warning.getMessage());

        // 警告代码（可选）
        if (warning.getWarningCode() != null) {
            sb.append(" (").append(warning.getWarningCode()).append(")");
        }

        return sb.toString();
    }

    /**
     * 构建验证详情Map
     *
     * 包含的信息：
     * - appSpecId: 应用规格ID
     * - compiler: 编译器类型
     * - compilerVersion: 编译器版本
     * - outputDirectory: 编译输出目录
     * - command: 执行的编译命令
     * - errorCount: 错误数量
     * - warningCount: 警告数量
     * - fullOutput: 完整编译输出（可选，用于调试）
     *
     * @param compilationResult 编译结果
     * @param appSpecId         AppSpec ID
     * @return 详情Map
     */
    private Map<String, Object> buildDetails(
            CompilationResult compilationResult,
            UUID appSpecId) {
        Map<String, Object> details = new HashMap<>();

        details.put("appSpecId", appSpecId.toString());
        details.put("compiler", compilationResult.getCompiler());
        details.put("compilerVersion", compilationResult.getCompilerVersion());
        details.put("outputDirectory", compilationResult.getOutputDirectory());
        details.put("command", compilationResult.getCommand());
        details.put("errorCount", compilationResult.getErrorCount());
        details.put("warningCount", compilationResult.getWarningCount());

        // 仅在有错误时包含完整输出（避免响应过大）
        if (!compilationResult.getSuccess() && compilationResult.getFullOutput() != null) {
            // 截断过长的输出（最多保留5000字符）
            String fullOutput = compilationResult.getFullOutput();
            if (fullOutput.length() > 5000) {
                fullOutput = fullOutput.substring(0, 5000) + "\n... (输出被截断)";
            }
            details.put("fullOutput", fullOutput);
        }

        return details;
    }

    /**
     * 批量转换编译结果（用于批量验证场景）
     *
     * @param compilationResults 编译结果列表
     * @param appSpecId          AppSpec ID
     * @return 验证响应列表
     */
    public List<ValidationResponse> toValidationResponses(
            List<CompilationResult> compilationResults,
            UUID appSpecId) {
        log.info("批量适配CompilationResult → ValidationResponse: count={}",
                compilationResults.size());

        return compilationResults.stream()
                .map(result -> toValidationResponse(result, appSpecId))
                .collect(Collectors.toList());
    }

    /**
     * 汇总多个编译结果为单个验证响应（用于多文件编译场景）
     *
     * @param compilationResults 多个编译结果
     * @param appSpecId          AppSpec ID
     * @return 汇总后的验证响应
     */
    public ValidationResponse aggregateResults(
            List<CompilationResult> compilationResults,
            UUID appSpecId) {
        log.info("汇总多个编译结果: count={}", compilationResults.size());

        // 1. 检查是否全部成功
        boolean allSuccess = compilationResults.stream()
                .allMatch(CompilationResult::getSuccess);

        // 2. 汇总所有错误
        List<String> allErrors = compilationResults.stream()
                .flatMap(result -> convertErrors(result.getErrors()).stream())
                .collect(Collectors.toList());

        // 3. 汇总所有警告
        List<String> allWarnings = compilationResults.stream()
                .flatMap(result -> convertWarnings(result.getWarnings()).stream())
                .collect(Collectors.toList());

        // 4. 汇总耗时
        long totalDurationMs = compilationResults.stream()
                .mapToLong(result -> result.getDurationMs() != null ? result.getDurationMs() : 0L)
                .sum();

        // 5. 计算质量评分
        int qualityScore = calculateQualityScore(allSuccess, allErrors.size(), allWarnings.size());

        // 6. 构建汇总详情
        Map<String, Object> details = new HashMap<>();
        details.put("appSpecId", appSpecId.toString());
        details.put("totalCompilations", compilationResults.size());
        details.put("successfulCompilations",
                compilationResults.stream().filter(CompilationResult::getSuccess).count());
        details.put("totalErrors", allErrors.size());
        details.put("totalWarnings", allWarnings.size());
        details.put("totalDurationMs", totalDurationMs);

        return ValidationResponse.builder()
                .validationId(UUID.randomUUID())
                .validationType("compile")
                .passed(allSuccess)
                .status(allSuccess ? "PASSED" : "FAILED")
                .qualityScore(qualityScore)
                .details(details)
                .errors(allErrors)
                .warnings(allWarnings)
                .durationMs(totalDurationMs)
                .completedAt(Instant.now())
                .build();
    }

    /**
     * 将TestResult转换为ValidationResponse（Phase 2扩展）
     *
     * 转换逻辑：
     * 1. allPassed → passed
     * 2. allPassed → status (PASSED/FAILED)
     * 3. 通过率 + 覆盖率 → qualityScore
     * 4. TestFailure列表 → String错误消息列表
     * 5. 构建详情Map（包含framework、totalTests、passedTests等）
     *
     * @param testResult 测试结果
     * @param appSpecId  AppSpec ID（用于关联）
     * @return 验证响应
     */
    public ValidationResponse toValidationResponseFromTestResult(
            TestResult testResult,
            UUID appSpecId) {
        log.info("开始适配TestResult → ValidationResponse: allPassed={}, totalTests={}, coverage={}",
                testResult.getAllPassed(),
                testResult.getTotalTests(),
                testResult.getCoverage());

        // 1. 生成验证ID和时间戳
        UUID validationId = UUID.randomUUID();
        Instant completedAt = Instant.now();

        // 2. 确定验证状态
        String status = testResult.getAllPassed() ? "PASSED" : "FAILED";

        // 3. 计算质量评分（基于通过率和覆盖率）
        int qualityScore = calculateTestQualityScore(
                testResult.getAllPassed(),
                testResult.getPassRate(),
                testResult.getCoverage());

        // 4. 转换失败的测试用例为错误消息
        List<String> errors = convertTestFailures(testResult.getFailures());

        // 5. 警告消息（测试场景主要关注失败，警告较少）
        List<String> warnings = new ArrayList<>();
        if (!testResult.getAllPassed() && testResult.getCoverage() != null && testResult.getCoverage() < 0.85) {
            warnings.add(String.format("测试覆盖率不足：%.2f%% （要求≥85%%）", testResult.getCoverage() * 100));
        }

        // 6. 构建详情Map
        Map<String, Object> details = buildTestDetails(testResult, appSpecId);

        // 7. 构建ValidationResponse
        ValidationResponse response = ValidationResponse.builder()
                .validationId(validationId)
                .validationType(testResult.getTestType()) // "unit" / "integration" / "e2e"
                .passed(testResult.getAllPassed())
                .status(status)
                .qualityScore(qualityScore)
                .details(details)
                .errors(errors)
                .warnings(warnings)
                .durationMs(testResult.getDurationMs())
                .completedAt(completedAt)
                .build();

        log.info("测试结果适配完成: validationId={}, passed={}, qualityScore={}, errors={}, coverage={}",
                validationId, response.getPassed(), qualityScore, errors.size(), testResult.getCoverage());

        return response;
    }

    /**
     * 计算测试质量评分
     *
     * 评分规则：
     * - 基础分: 通过率 × 50
     * - 覆盖率加分: 覆盖率 × 50
     * - 全部通过且覆盖率≥85%: 100分
     * - 全部通过但覆盖率<85%: 50-99分
     * - 有失败: 0-49分
     *
     * @param allPassed 是否全部通过
     * @param passRate  通过率（0-1）
     * @param coverage  覆盖率（0-1，可能为null）
     * @return 质量评分（0-100）
     */
    private int calculateTestQualityScore(boolean allPassed, double passRate, Double coverage) {
        int score = 0;

        // 基础分：通过率 × 50
        score += (int) (passRate * 50);

        // 覆盖率加分：覆盖率 × 50
        if (coverage != null) {
            score += (int) (coverage * 50);
        }

        // 特殊奖励：全部通过且覆盖率≥85% = 满分
        if (allPassed && coverage != null && coverage >= 0.85) {
            score = 100;
        }

        // 确保分数在0-100范围内
        score = Math.max(0, Math.min(100, score));

        log.debug("测试质量评分计算: allPassed={}, passRate={}, coverage={}, score={}",
                allPassed, passRate, coverage, score);

        return score;
    }

    /**
     * 转换测试失败用例为错误消息列表
     *
     * 格式: "[套件名] 测试名 - 失败消息"
     * 示例: "[UserService] should create user - Expected 201 but got 500"
     *
     * @param failures 测试失败列表
     * @return 错误消息列表
     */
    private List<String> convertTestFailures(List<TestResult.TestFailure> failures) {
        if (failures == null || failures.isEmpty()) {
            return new ArrayList<>();
        }

        return failures.stream()
                .map(this::formatTestFailure)
                .collect(Collectors.toList());
    }

    /**
     * 格式化单个测试失败
     */
    private String formatTestFailure(TestResult.TestFailure failure) {
        StringBuilder sb = new StringBuilder();

        // [套件名]
        if (failure.getSuiteName() != null) {
            sb.append("[").append(failure.getSuiteName()).append("] ");
        }

        // 测试名
        if (failure.getTestName() != null) {
            sb.append(failure.getTestName());
        }

        // 失败消息
        if (failure.getMessage() != null) {
            sb.append(" - ").append(failure.getMessage());
        }

        // 预期值 vs 实际值（如果有）
        if (failure.getExpected() != null && failure.getActual() != null) {
            sb.append(String.format(" (Expected: %s, Actual: %s)",
                    failure.getExpected(), failure.getActual()));
        }

        return sb.toString();
    }

    /**
     * 构建测试详情Map
     *
     * 包含的信息：
     * - appSpecId: 应用规格ID
     * - framework: 测试框架（JUnit/Vitest/Playwright）
     * - testType: 测试类型（unit/integration/e2e）
     * - totalTests: 总测试数
     * - passedTests: 通过的测试数
     * - failedTests: 失败的测试数
     * - skippedTests: 跳过的测试数
     * - passRate: 通过率
     * - coverage: 总覆盖率
     * - lineCoverage: 行覆盖率
     * - branchCoverage: 分支覆盖率
     * - reportPath: 测试报告路径
     *
     * @param testResult 测试结果
     * @param appSpecId  AppSpec ID
     * @return 详情Map
     */
    private Map<String, Object> buildTestDetails(TestResult testResult, UUID appSpecId) {
        Map<String, Object> details = new HashMap<>();

        details.put("appSpecId", appSpecId.toString());
        details.put("framework", testResult.getFramework());
        details.put("testType", testResult.getTestType());
        details.put("totalTests", testResult.getTotalTests());
        details.put("passedTests", testResult.getPassedTests());
        details.put("failedTests", testResult.getFailedTests());
        details.put("skippedTests", testResult.getSkippedTests());
        details.put("passRate", testResult.getPassRate());

        // 覆盖率信息（可能为null）
        if (testResult.getCoverage() != null) {
            details.put("coverage", testResult.getCoverage());
        }
        if (testResult.getLineCoverage() != null) {
            details.put("lineCoverage", testResult.getLineCoverage());
        }
        if (testResult.getBranchCoverage() != null) {
            details.put("branchCoverage", testResult.getBranchCoverage());
        }
        if (testResult.getFunctionCoverage() != null) {
            details.put("functionCoverage", testResult.getFunctionCoverage());
        }

        // 测试报告路径
        if (testResult.getReportPath() != null) {
            details.put("reportPath", testResult.getReportPath());
        }

        // 仅在有失败时包含完整输出（截断）
        if (!testResult.getAllPassed() && testResult.getFullOutput() != null) {
            String fullOutput = testResult.getFullOutput();
            if (fullOutput.length() > 5000) {
                fullOutput = fullOutput.substring(0, 5000) + "\n... (输出被截断)";
            }
            details.put("fullOutput", fullOutput);
        }

        return details;
    }

    /**
     * 将CoverageResult转换为ValidationResponse（Phase 3扩展）
     *
     * 转换逻辑：
     * 1. meetsQualityGate → passed
     * 2. meetsQualityGate → status (PASSED/FAILED)
     * 3. overallCoverage → qualityScore（0-100分）
     * 4. 构建详情Map（包含lineCoverage、branchCoverage等）
     *
     * @param coverageResult 覆盖率结果
     * @param appSpecId      AppSpec ID（用于关联）
     * @return 验证响应
     */
    public ValidationResponse toValidationResponseFromCoverageResult(
            com.ingenio.backend.dto.CoverageResult coverageResult,
            UUID appSpecId) {
        log.info("开始适配CoverageResult → ValidationResponse: tool={}, overallCoverage={}, meetsQualityGate={}",
                coverageResult.getTool(),
                coverageResult.getOverallCoverage(),
                coverageResult.getMeetsQualityGate());

        // 1. 生成验证ID和时间戳
        UUID validationId = UUID.randomUUID();
        Instant completedAt = Instant.now();

        // 2. 确定验证状态（基于质量门禁）
        String status = coverageResult.getMeetsQualityGate() ? "PASSED" : "FAILED";

        // 3. 计算质量评分（直接使用覆盖率百分比）
        int qualityScore = (int) (coverageResult.getOverallCoverage() * 100);

        // 4. 警告消息（覆盖率不足时）
        List<String> warnings = new ArrayList<>();
        if (!coverageResult.getMeetsQualityGate()) {
            warnings.add(String.format("代码覆盖率不足：%.2f%% （要求≥85%%）",
                    coverageResult.getOverallCoverage() * 100));
        }

        // 5. 构建详情Map
        Map<String, Object> details = buildCoverageDetails(coverageResult, appSpecId);

        // 6. 构建ValidationResponse
        ValidationResponse response = ValidationResponse.builder()
                .validationId(validationId)
                .validationType("coverage")
                .passed(coverageResult.getMeetsQualityGate())
                .status(status)
                .qualityScore(qualityScore)
                .details(details)
                .errors(new ArrayList<>()) // 覆盖率验证通常不产生编译错误
                .warnings(warnings)
                .durationMs(0L) // CoverageResult不包含耗时信息
                .completedAt(completedAt)
                .build();

        log.info("覆盖率结果适配完成: validationId={}, passed={}, qualityScore={}",
                validationId, response.getPassed(), qualityScore);

        return response;
    }

    /**
     * 构建覆盖率详情Map
     *
     * 包含的信息：
     * - appSpecId: 应用规格ID
     * - projectType: 项目类型（nextjs/spring-boot/kmp）
     * - tool: 覆盖率工具（istanbul/jacoco/kover）
     * - overallCoverage: 总体覆盖率
     * - lineCoverage: 行覆盖率
     * - branchCoverage: 分支覆盖率
     * - functionCoverage: 函数覆盖率
     * - statementCoverage: 语句覆盖率（仅JavaScript/TypeScript）
     * - reportPath: 覆盖率报告路径
     *
     * @param coverageResult 覆盖率结果
     * @param appSpecId      AppSpec ID
     * @return 详情Map
     */
    private Map<String, Object> buildCoverageDetails(
            com.ingenio.backend.dto.CoverageResult coverageResult,
            UUID appSpecId) {
        Map<String, Object> details = new HashMap<>();

        details.put("appSpecId", appSpecId.toString());
        details.put("projectType", coverageResult.getProjectType());
        details.put("tool", coverageResult.getTool());
        details.put("overallCoverage", coverageResult.getOverallCoverage());
        details.put("lineCoverage", coverageResult.getLineCoverage());
        details.put("branchCoverage", coverageResult.getBranchCoverage());
        details.put("functionCoverage", coverageResult.getFunctionCoverage());

        // 语句覆盖率（仅Istanbul提供）
        if (coverageResult.getStatementCoverage() != null) {
            details.put("statementCoverage", coverageResult.getStatementCoverage());
        }

        // 覆盖率报告路径
        if (coverageResult.getReportPath() != null) {
            details.put("reportPath", coverageResult.getReportPath());
        }

        return details;
    }
}
