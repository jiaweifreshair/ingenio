package com.ingenio.backend.service.g3;

import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.service.blueprint.BlueprintComplianceResult;
import com.ingenio.backend.service.blueprint.BlueprintValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * G3 Phased Validator
 *
 * Encapsulates the multi-stage validation logic:
 * Phase 1: Compilation (Sandbox)
 * Phase 2: Blueprint Compliance
 * Phase 3: Static Analysis (Future M4)
 *
 * @author Ingenio Team
 * @since 2.2.0 (M3)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class G3PhaseValidator {

    private final G3SandboxService sandboxService;
    private final BlueprintValidator blueprintValidator;
    private final G3StaticAnalyzer staticAnalyzer;

    /**
     * 是否启用静态分析验证（M4）
     */
    @org.springframework.beans.factory.annotation.Value("${ingenio.g3.validation.enable-static:true}")
    private boolean enableStaticAnalysis;

    /**
     * Execute all validation phases
     *
     * @param job         Current job
     * @param artifacts   Current artifacts
     * @param logConsumer Log consumer
     * @return Unified validation result
     */
    public ValidationResult validateAll(G3JobEntity job, List<G3ArtifactEntity> artifacts,
            Consumer<G3LogEntry> logConsumer) {

        // Phase 1: Compilation (Sandbox)
        logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.EXECUTOR, "Phase 1: 编译验证..."));
        G3ValidationResultEntity sandboxResult = sandboxService.validate(job, artifacts, logConsumer);

        if (!Boolean.TRUE.equals(sandboxResult.getPassed())) {
            logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR, "编译验证未通过"));
            return new ValidationResult(false, sandboxResult, null, List.of());
        }

        // Phase 2: Blueprint Compliance (if enabled)
        boolean blueprintMode = Boolean.TRUE.equals(job.getBlueprintModeEnabled());
        BlueprintComplianceResult blueprintResult = null;
        if (blueprintMode) {
            logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.COACH, "Phase 2: Blueprint 合规性验证..."));
            // Updated to access the blueprintSpec Map directly, assuming Orchestrator
            // unpacks it or we fetch it.
            // Job has getBlueprintSpec() which returns Map<String, Object>
            blueprintResult = blueprintValidator.validateBackendArtifactsCompliance(artifacts, job.getBlueprintSpec());

            if (!blueprintResult.passed()) {
                logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.COACH,
                        "Blueprint 违规: " + blueprintResult.violations().size() + " 项"));
                return new ValidationResult(false, sandboxResult, blueprintResult, List.of());
            }
        }

        // Phase 3: Static Analysis (M4)
        if (enableStaticAnalysis) {
            List<String> violations = staticAnalyzer.analyze(artifacts);
            if (violations != null && !violations.isEmpty()) {
                logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.COACH,
                        "静态分析发现问题: " + violations.size() + " 项"));
                for (String violation : violations) {
                    logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.COACH, violation));
                }
                return new ValidationResult(false, sandboxResult, blueprintResult, violations);
            }
        }

        return new ValidationResult(true, sandboxResult, blueprintResult, List.of());
    }

    public record ValidationResult(
            boolean passed,
            G3ValidationResultEntity sandboxResult,
            BlueprintComplianceResult blueprintResult,
            List<String> staticViolations) {
    }
}
