package com.ingenio.backend.module.g3.controller;

import com.ingenio.backend.common.Result;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.service.g3.G3OrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * G3引擎API控制器
 *
 * 提供G3红蓝博弈引擎的REST API接口：
 * - 提交任务
 * - 查询状态
 * - 订阅日志流（SSE）
 * - 获取产物
 *
 * @author Ingenio Team
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/g3")
@RequiredArgsConstructor
@Tag(name = "G3 Engine API", description = "G3 红蓝博弈引擎接口")
public class G3Controller {

    private final G3OrchestratorService orchestratorService;

    /**
     * 提交G3任务
     *
     * @param request 任务请求（包含需求描述）
     * @return 任务ID
     */
    @PostMapping("/jobs")
    @Operation(summary = "提交G3任务")
    public Result<SubmitJobResponse> submitJob(@RequestBody SubmitJobRequest request) {
        log.info("[G3 API] 提交任务: requirement={}", truncate(request.getRequirement(), 50));

        UUID jobId = orchestratorService.submitJob(
                request.getRequirement(),
                null,  // userId - 后续从认证上下文获取
                null,  // tenantId - 后续从认证上下文获取
                request.getAppSpecId(),
                request.getTemplateId(),
                request.getMaxRounds()
        );

        return Result.success(new SubmitJobResponse(jobId.toString()));
    }

    /**
     * 查询任务状态
     *
     * @param jobId 任务ID
     * @return 任务状态详情
     */
    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "查询任务状态")
    public Result<JobStatusResponse> getJobStatus(@PathVariable String jobId) {
        log.debug("[G3 API] 查询任务状态: jobId={}", jobId);

        G3JobEntity job = orchestratorService.getJob(UUID.fromString(jobId));
        if (job == null) {
            return Result.error(404, "任务不存在");
        }

        return Result.success(JobStatusResponse.fromEntity(job));
    }

    /**
     * 订阅任务日志流（SSE）
     *
     * @param jobId 任务ID
     * @return SSE日志流
     */
    @GetMapping(value = "/jobs/{jobId}/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "订阅任务日志流（SSE）")
    public Flux<G3LogEntry> subscribeToLogs(@PathVariable String jobId) {
        log.info("[G3 API] 订阅日志流: jobId={}", jobId);
        return orchestratorService.subscribeToLogs(UUID.fromString(jobId));
    }

    /**
     * 获取任务产物列表
     *
     * @param jobId 任务ID
     * @return 产物列表
     */
    @GetMapping("/jobs/{jobId}/artifacts")
    @Operation(summary = "获取任务产物列表")
    public Result<List<ArtifactResponse>> getArtifacts(@PathVariable String jobId) {
        log.debug("[G3 API] 获取产物列表: jobId={}", jobId);

        List<G3ArtifactEntity> artifacts = orchestratorService.getArtifacts(UUID.fromString(jobId));
        List<ArtifactResponse> response = artifacts.stream()
                .map(ArtifactResponse::fromEntity)
                .toList();

        return Result.success(response);
    }

    /**
     * 获取契约内容
     *
     * @param jobId 任务ID
     * @return 契约（OpenAPI + DB Schema）
     */
    @GetMapping("/jobs/{jobId}/contract")
    @Operation(summary = "获取契约内容")
    public Result<ContractResponse> getContract(@PathVariable String jobId) {
        log.debug("[G3 API] 获取契约: jobId={}", jobId);

        G3JobEntity job = orchestratorService.getJob(UUID.fromString(jobId));
        if (job == null) {
            return Result.error(404, "任务不存在");
        }

        return Result.success(new ContractResponse(
                job.getContractYaml(),
                job.getDbSchemaSql(),
                Boolean.TRUE.equals(job.getContractLocked())
        ));
    }

    /**
     * 健康检查
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public Result<HealthResponse> health() {
        return Result.success(new HealthResponse("ok", "G3 Engine is running"));
    }

    // ========== 兼容旧API（保持向后兼容） ==========

    /**
     * 启动G3任务（旧API，兼容前端）
     */
    @PostMapping("/start")
    @Operation(summary = "启动G3任务（兼容旧API）")
    public Result<SubmitJobResponse> start(@RequestParam String requirement) {
        log.info("[G3 API] 旧API启动任务: requirement={}", truncate(requirement, 50));

        UUID jobId = orchestratorService.submitJob(requirement, null, null);
        return Result.success(new SubmitJobResponse(jobId.toString()));
    }

    /**
     * 日志流（旧API路径，兼容前端）
     */
    @GetMapping(value = "/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "日志流（兼容旧API）")
    public Flux<G3LogEntry> streamLogs(@PathVariable String taskId) {
        log.info("[G3 API] 旧API日志流: taskId={}", taskId);
        return orchestratorService.subscribeToLogs(UUID.fromString(taskId));
    }

    // ========== DTO 类 ==========

    @Data
    public static class SubmitJobRequest {
        private String requirement;
        /**
         * AppSpec ID（可选）
         * 传入后端将自动从 AppSpec 加载 Blueprint/tenantId/userId 等上下文
         */
        private UUID appSpecId;

        /**
         * 行业模板ID（可选）
         * 传入后端将自动从模板加载 Blueprint（用于快速试跑）
         */
        private UUID templateId;

        /**
         * 最大修复轮次（可选）
         * 覆盖默认的 ingenio.g3.sandbox.max-rounds 配置
         */
        private Integer maxRounds;
    }

    @Data
    public static class SubmitJobResponse {
        private final String jobId;

        public SubmitJobResponse(String jobId) {
            this.jobId = jobId;
        }
    }

    @Data
    public static class JobStatusResponse {
        private String id;
        private String status;
        private int currentRound;
        private int maxRounds;
        private boolean contractLocked;
        private String sandboxId;
        private String sandboxUrl;
        private String lastError;
        private String startedAt;
        private String completedAt;

        public static JobStatusResponse fromEntity(G3JobEntity job) {
            JobStatusResponse resp = new JobStatusResponse();
            resp.setId(job.getId().toString());
            resp.setStatus(job.getStatus());
            resp.setCurrentRound(job.getCurrentRound());
            resp.setMaxRounds(job.getMaxRounds());
            resp.setContractLocked(Boolean.TRUE.equals(job.getContractLocked()));
            resp.setSandboxId(job.getSandboxId());
            resp.setSandboxUrl(job.getSandboxUrl());
            resp.setLastError(job.getLastError());
            resp.setStartedAt(job.getStartedAt() != null ? job.getStartedAt().toString() : null);
            resp.setCompletedAt(job.getCompletedAt() != null ? job.getCompletedAt().toString() : null);
            return resp;
        }
    }

    @Data
    public static class ArtifactResponse {
        private String id;
        private String filePath;
        private String generatedBy;
        private int round;
        private boolean hasErrors;
        private String createdAt;

        public static ArtifactResponse fromEntity(G3ArtifactEntity artifact) {
            ArtifactResponse resp = new ArtifactResponse();
            resp.setId(artifact.getId().toString());
            resp.setFilePath(artifact.getFilePath());
            resp.setGeneratedBy(artifact.getGeneratedBy());
            resp.setRound(artifact.getGenerationRound() != null ? artifact.getGenerationRound() : 0);
            resp.setHasErrors(Boolean.TRUE.equals(artifact.getHasErrors()));
            resp.setCreatedAt(artifact.getCreatedAt() != null ? artifact.getCreatedAt().toString() : null);
            return resp;
        }
    }

    @Data
    public static class ContractResponse {
        private final String openApiYaml;
        private final String dbSchemaSql;
        private final boolean locked;

        public ContractResponse(String openApiYaml, String dbSchemaSql, boolean locked) {
            this.openApiYaml = openApiYaml;
            this.dbSchemaSql = dbSchemaSql;
            this.locked = locked;
        }
    }

    @Data
    public static class HealthResponse {
        private final String status;
        private final String message;

        public HealthResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
