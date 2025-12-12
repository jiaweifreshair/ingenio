package com.ingenio.backend.service;

import com.ingenio.backend.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 验证编排器
 *
 * 完整的验证流程：
 * 1. 编译验证 - CompilationValidator
 * 2. 单元测试 - TestExecutor (覆盖率≥85%)
 * 3. E2E测试 - TestExecutor (全部通过)
 * 4. 性能验证 - PerformanceValidator (P95<3000ms)
 *
 * 验证结果保存到时光机版本快照中
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationOrchestrator {

    private final CompilationValidator compilationValidator;
    private final TestExecutor testExecutor;
    private final PerformanceValidator performanceValidator;
    private final VersionSnapshotService snapshotService;

    /**
     * 完整验证流程
     *
     * @param codeResult 代码生成结果
     * @return 验证结果（包含编译、测试、性能的完整信息）
     */
    public ValidationResult validate(CodeGenerationResult codeResult) {
        UUID taskId = codeResult.getTaskId();
        UUID tenantId = codeResult.getTenantId();

        log.info("开始完整验证流程: taskId={}", taskId);

        Instant startTime = Instant.now();

        ValidationResult.ValidationResultBuilder resultBuilder = ValidationResult.builder()
                .taskId(taskId)
                .startTime(startTime);

        // Step 1: 编译验证
        log.info("Step 1/4: 编译验证");
        CompilationResult compilation = compilationValidator.compile(codeResult);
        resultBuilder.compilationResult(compilation);

        if (!compilation.getSuccess()) {
            log.error("编译失败，终止验证流程: errors={}", compilation.getErrorCount());
            ValidationResult failedResult = resultBuilder
                    .success(false)
                    .failureReason("编译失败: " + compilation.getErrorCount() + "个错误")
                    .endTime(Instant.now())
                    .build();
            failedResult.calculateDuration();
            saveFailedSnapshot(taskId, tenantId, failedResult);
            return failedResult;
        }

        // Step 2: 单元测试
        log.info("Step 2/4: 单元测试验证");
        TestResult unitTest = testExecutor.runUnitTests(codeResult);
        resultBuilder.unitTestResult(unitTest);

        if (!unitTest.getAllPassed() || !unitTest.meetsCoverageGoal()) {
            log.error("单元测试未通过: passed={}/{}, coverage={}",
                    unitTest.getPassedTests(), unitTest.getTotalTests(), unitTest.getCoverage());
            String reason = !unitTest.getAllPassed()
                    ? "单元测试失败: " + unitTest.getFailedTests() + "个用例失败"
                    : "测试覆盖率不足: " + (unitTest.getCoverage() * 100) + "%（要求≥85%）";

            ValidationResult failedResult = resultBuilder
                    .success(false)
                    .failureReason(reason)
                    .endTime(Instant.now())
                    .build();
            failedResult.calculateDuration();
            saveFailedSnapshot(taskId, tenantId, failedResult);
            return failedResult;
        }

        // Step 3: E2E测试
        log.info("Step 3/4: E2E测试验证");
        TestResult e2eTest = testExecutor.runE2ETests(codeResult);
        resultBuilder.e2eTestResult(e2eTest);

        if (!e2eTest.getAllPassed()) {
            log.error("E2E测试失败: passed={}/{}",
                    e2eTest.getPassedTests(), e2eTest.getTotalTests());
            ValidationResult failedResult = resultBuilder
                    .success(false)
                    .failureReason("E2E测试失败: " + e2eTest.getFailedTests() + "个场景失败")
                    .endTime(Instant.now())
                    .build();
            failedResult.calculateDuration();
            saveFailedSnapshot(taskId, tenantId, failedResult);
            return failedResult;
        }

        // Step 4: 性能验证
        log.info("Step 4/4: 性能验证");
        PerformanceResult performance = performanceValidator.validate(codeResult);
        resultBuilder.performanceResult(performance);

        if (!performance.getPassed()) {
            log.error("性能验证失败: p95={}ms, reason={}",
                    performance.getP95ResponseTime(), performance.getFailureReason());
            ValidationResult failedResult = resultBuilder
                    .success(false)
                    .failureReason("性能不达标: " + performance.getFailureReason())
                    .endTime(Instant.now())
                    .build();
            failedResult.calculateDuration();
            saveFailedSnapshot(taskId, tenantId, failedResult);
            return failedResult;
        }

        // 全部通过
        log.info("验证全部通过: taskId={}", taskId);
        ValidationResult successResult = resultBuilder
                .success(true)
                .endTime(Instant.now())
                .build();
        successResult.calculateDuration();
        saveSuccessSnapshot(taskId, tenantId, successResult);

        return successResult;
    }

    /**
     * 保存失败的验证快照
     */
    private void saveFailedSnapshot(UUID taskId, UUID tenantId, ValidationResult result) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("validation_result", result);
        snapshot.put("status", "failed");
        snapshot.put("failure_reason", result.getFailureReason());
        snapshot.put("timestamp", Instant.now());

        // 详细的失败信息
        if (result.getCompilationResult() != null) {
            snapshot.put("compilation_errors", result.getCompilationResult().getErrorCount());
        }
        if (result.getUnitTestResult() != null) {
            snapshot.put("unit_test_coverage", result.getUnitTestResult().getCoverage());
            snapshot.put("unit_test_failures", result.getUnitTestResult().getFailedTests());
        }
        if (result.getE2eTestResult() != null) {
            snapshot.put("e2e_test_failures", result.getE2eTestResult().getFailedTests());
        }
        if (result.getPerformanceResult() != null) {
            snapshot.put("p95_response_time", result.getPerformanceResult().getP95ResponseTime());
        }

        snapshotService.createSnapshot(
                taskId,
                tenantId,
                VersionType.VALIDATION_FAILED,
                snapshot
        );

        log.info("已保存失败验证快照: taskId={}, reason={}", taskId, result.getFailureReason());
    }

    /**
     * 保存成功的验证快照
     */
    private void saveSuccessSnapshot(UUID taskId, UUID tenantId, ValidationResult result) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("validation_result", result);
        snapshot.put("status", "success");
        snapshot.put("timestamp", Instant.now());

        // 成功的统计信息
        snapshot.put("compilation_success", true);
        snapshot.put("unit_test_coverage", result.getUnitTestResult().getCoverage());
        snapshot.put("unit_test_passed", result.getUnitTestResult().getPassedTests());
        snapshot.put("e2e_test_passed", result.getE2eTestResult().getPassedTests());
        snapshot.put("p95_response_time", result.getPerformanceResult().getP95ResponseTime());
        snapshot.put("memory_usage_mb", result.getPerformanceResult().getMemoryUsageMb());
        snapshot.put("total_duration_ms", result.getTotalDurationMs());

        snapshotService.createSnapshot(
                taskId,
                tenantId,
                VersionType.VALIDATION_SUCCESS,
                snapshot
        );

        log.info("已保存成功验证快照: taskId={}, duration={}ms",
                taskId, result.getTotalDurationMs());
    }
}
