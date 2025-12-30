package com.ingenio.backend.service.g3;

import com.ingenio.backend.dto.response.validation.ValidationResponse;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * G3验证适配器
 *
 * 功能：
 * 1. 将G3SandboxService.CompileResult转换为ValidationResponse
 * 2. 将ValidationResponse转换为G3ValidationResultEntity
 * 3. 统一G3引擎和ValidationService的数据格式
 *
 * 设计理念：
 * - G3SandboxService负责执行沙箱编译（已有实现）
 * - ValidationService负责验证结果的统一管理和持久化
 * - G3ValidationAdapter负责两者之间的数据格式转换
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 5
 */
@Slf4j
@Component
public class G3ValidationAdapter {

    /**
     * 将G3编译结果转换为通用验证响应
     *
     * 转换映射：
     * - CompileResult.success → ValidationResponse.passed
     * - CompileResult.errors → ValidationResponse.errors
     * - CompileResult.durationMs → ValidationResponse.durationMs
     *
     * @param compileResult G3沙箱编译结果
     * @param jobId        G3任务ID
     * @return 通用验证响应
     */
    public ValidationResponse toValidationResponse(
            G3SandboxService.CompileResult compileResult,
            UUID jobId) {

        log.debug("开始转换G3CompileResult → ValidationResponse: success={}, errors={}",
                compileResult.success(), compileResult.errors().size());

        // 1. 转换错误信息
        List<String> errors = compileResult.errors().stream()
                .map(error -> formatCompileError(error))
                .collect(Collectors.toList());

        // 2. 计算质量评分
        int qualityScore = calculateQualityScore(compileResult);

        // 3. 确定验证状态
        String status = compileResult.success() ? "PASSED" : "FAILED";

        // 4. 构建详情Map
        Map<String, Object> details = new HashMap<>();
        details.put("exitCode", compileResult.exitCode());
        details.put("errorCount", compileResult.errors().size());
        details.put("command", "mvn compile -e -B");
        if (!compileResult.stdout().isBlank()) {
            details.put("stdoutPreview", truncate(compileResult.stdout(), 500));
        }
        if (!compileResult.stderr().isBlank()) {
            details.put("stderrPreview", truncate(compileResult.stderr(), 500));
        }

        // 5. 构建ValidationResponse
        ValidationResponse response = ValidationResponse.builder()
                .validationId(UUID.randomUUID())
                .validationType("compile")
                .passed(compileResult.success())
                .status(status)
                .qualityScore(qualityScore)
                .details(details)
                .errors(errors)
                .warnings(new ArrayList<>())  // G3暂不支持警告
                .durationMs((long) compileResult.durationMs())
                .completedAt(Instant.now())
                .build();

        log.info("G3CompileResult转换完成 - passed: {}, errors: {}, qualityScore: {}, durationMs: {}",
                response.getPassed(),
                response.getErrors().size(),
                response.getQualityScore(),
                response.getDurationMs());

        return response;
    }

    /**
     * 将通用验证响应转换为G3验证结果实体
     *
     * 用途：
     * - 将ValidationService的验证结果保存为G3格式
     * - 支持G3引擎的验证结果查询和展示
     *
     * @param validationResponse 通用验证响应
     * @param jobId             G3任务ID
     * @param round             验证轮次
     * @param compileResult     原始编译结果（可选，用于补充stdout/stderr）
     * @return G3验证结果实体
     */
    public G3ValidationResultEntity toG3ValidationResult(
            ValidationResponse validationResponse,
            UUID jobId,
            Integer round,
            G3SandboxService.CompileResult compileResult) {

        log.debug("开始转换ValidationResponse → G3ValidationResultEntity: passed={}, errors={}",
                validationResponse.getPassed(), validationResponse.getErrors().size());

        // 1. 提取执行命令和退出码
        String command = (String) validationResponse.getDetails().getOrDefault("command", "mvn compile");
        Integer exitCode = (Integer) validationResponse.getDetails().getOrDefault("exitCode",
                validationResponse.getPassed() ? 0 : 1);

        // 2. 使用原始编译结果的stdout/stderr（如果可用）
        String stdout = compileResult != null ? compileResult.stdout() : "";
        String stderr = compileResult != null ? compileResult.stderr() : "";

        // 3. 解析错误为G3格式
        List<G3ValidationResultEntity.ParsedError> parsedErrors = new ArrayList<>();
        if (compileResult != null && !compileResult.errors().isEmpty()) {
            parsedErrors = compileResult.errors().stream()
                    .map(error -> new G3ValidationResultEntity.ParsedError(
                            error.file(),
                            error.line(),
                            error.column(),
                            error.message(),
                            error.severity()
                    ))
                    .collect(Collectors.toList());
        }

        // 4. 构建G3ValidationResultEntity
        G3ValidationResultEntity entity = G3ValidationResultEntity.builder()
                .jobId(jobId)
                .round(round)
                .validationType(validationResponse.getValidationType())
                .passed(validationResponse.getPassed())
                .command(command)
                .exitCode(exitCode)
                .stdout(stdout)
                .stderr(stderr)
                .durationMs(validationResponse.getDurationMs().intValue())
                .parsedErrors(parsedErrors)
                .build();

        log.info("ValidationResponse转换完成 - passed: {}, parsedErrors: {}",
                entity.getPassed(), entity.getParsedErrors().size());

        return entity;
    }

    /**
     * 格式化编译错误为字符串
     *
     * 格式：{file}:{line}:{column}: {severity}: {message}
     * 示例：UserController.java:42:15: error: cannot find symbol
     *
     * @param error 编译错误
     * @return 格式化后的错误字符串
     */
    private String formatCompileError(G3SandboxService.CompileError error) {
        return String.format("%s:%d:%d: %s: %s",
                extractFileName(error.file()),
                error.line(),
                error.column(),
                error.severity(),
                error.message());
    }

    /**
     * 提取文件名（去除路径前缀）
     *
     * 示例：/home/user/app/src/main/java/UserController.java → UserController.java
     *
     * @param filePath 文件路径
     * @return 文件名
     */
    private String extractFileName(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "unknown";
        }
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    /**
     * 计算质量评分（0-100）
     *
     * 评分规则：
     * - 编译成功：100分
     * - 编译失败：基础分90分，每个错误扣1分，最低0分
     *
     * @param compileResult 编译结果
     * @return 质量评分
     */
    private int calculateQualityScore(G3SandboxService.CompileResult compileResult) {
        if (compileResult.success()) {
            return 100;
        }

        // 基础分90分，每个错误扣1分
        int baseScore = 90;
        int errorCount = compileResult.errors().size();
        int score = baseScore - errorCount;

        return Math.max(0, score);  // 最低0分
    }

    /**
     * 截断长文本（用于日志预览）
     *
     * @param text   原文本
     * @param maxLen 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "... (truncated)";
    }
}
