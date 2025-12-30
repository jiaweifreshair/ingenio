package com.ingenio.backend.service.adapter;

import com.ingenio.backend.dto.CompilationResult;
import com.ingenio.backend.dto.response.validation.ValidationResponse;
import lombok.extern.slf4j.Slf4j;
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
 * - ValidationResponse: validationId, passed, status, qualityScore, errors, warnings
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 1
 */
@Slf4j
@Component
public class ValidationResultAdapter {

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
     * @param appSpecId AppSpec ID（用于关联）
     * @return 验证响应
     */
    public ValidationResponse toValidationResponse(
            CompilationResult compilationResult,
            UUID appSpecId
    ) {
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
                compilationResult.getWarningCount()
        );

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
     * @param success 是否成功
     * @param errorCount 错误数量
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
     * @param appSpecId AppSpec ID
     * @return 详情Map
     */
    private Map<String, Object> buildDetails(
            CompilationResult compilationResult,
            UUID appSpecId
    ) {
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
     * @param appSpecId AppSpec ID
     * @return 验证响应列表
     */
    public List<ValidationResponse> toValidationResponses(
            List<CompilationResult> compilationResults,
            UUID appSpecId
    ) {
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
     * @param appSpecId AppSpec ID
     * @return 汇总后的验证响应
     */
    public ValidationResponse aggregateResults(
            List<CompilationResult> compilationResults,
            UUID appSpecId
    ) {
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
        details.put("successfulCompilations", compilationResults.stream().filter(CompilationResult::getSuccess).count());
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
}
