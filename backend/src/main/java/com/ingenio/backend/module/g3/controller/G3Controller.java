package com.ingenio.backend.module.g3.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.common.context.TenantContextHolder;
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
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.servlet.http.HttpServletResponse;
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
    private final ObjectMapper objectMapper;

    /**
     * 提交G3任务
     *
     * @param request 任务请求（包含需求描述）
     * @return 任务ID
     */
    @PostMapping("/jobs")
    @Operation(summary = "提交G3任务")
    public Result<SubmitJobResponse> submitJob(@RequestBody SubmitJobRequest request) {
        // 从认证上下文获取用户ID和租户ID
        UUID userId = getCurrentUserId();
        UUID tenantId = getCurrentTenantId();

        log.info("[G3 API] 提交任务: requirement={}, userId={}, tenantId={}",
                truncate(request.getRequirement(), 50), userId, tenantId);

        UUID jobId = orchestratorService.submitJob(
                request.getRequirement(),
                userId,
                tenantId,
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

        // 权限验证：确保用户只能访问自己的任务
        if (!hasJobAccess(job)) {
            return Result.error(403, "无权访问该任务");
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
    public Flux<ServerSentEvent<String>> subscribeToLogs(@PathVariable String jobId, HttpServletResponse response) {
        log.info("[G3 API] 订阅日志流: jobId={}", jobId);

        // 关键：显式禁用缓冲/压缩，避免“Network里看起来是空的”或长时间不刷新的情况
        // - nginx: X-Accel-Buffering=no
        // - 浏览器/代理：Cache-Control=no-transform/no-cache
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Content-Encoding", "identity");

        // 权限验证：确保用户只能订阅自己任务的日志
        G3JobEntity job = orchestratorService.getJob(UUID.fromString(jobId));
        if (job == null || !hasJobAccess(job)) {
            log.warn("[G3 API] SSE 权限拒绝: jobId={}", jobId);
            return Flux.just(ServerSentEvent.<String>builder("connected").event("open").build())
                    .concatWith(Flux.just(ServerSentEvent.<String>builder("无权访问该任务的日志流").event("error").build()))
                    .concatWith(Mono.just(ServerSentEvent.<String>builder("done").event("complete").build()));
        }

        UUID id = UUID.fromString(jobId);
        // 先发送一个轻量的 open + padding，确保连接立即有可见输出并触发代理/浏览器 flush
        Flux<ServerSentEvent<String>> openFlux = Flux.just(
                ServerSentEvent.<String>builder("connected").event("open").build(),
                ServerSentEvent.<String>builder("").comment(" ".repeat(2048)).build()
        );

        Flux<ServerSentEvent<String>> logFlux = orchestratorService.subscribeToLogs(id)
                .map(entry -> {
                    String eventName = "heartbeat".equalsIgnoreCase(entry.getLevel()) ? "heartbeat" : "log";
                    try {
                        return ServerSentEvent.<String>builder(objectMapper.writeValueAsString(entry))
                                .event(eventName)
                                .build();
                    } catch (Exception e) {
                        return ServerSentEvent.<String>builder("{\"level\":\"error\",\"message\":\"日志序列化失败\"}")
                                .event("error")
                                .build();
                    }
                })
                // SSE 流正常结束时补一个 complete 事件，便于前端做“结果拉取/展示”
                .concatWith(Mono.just(ServerSentEvent.<String>builder("done").event("complete").build()))
                .onErrorResume(e -> Flux.just(ServerSentEvent.<String>builder(String.valueOf(e.getMessage())).event("error").build())
                        .concatWith(Mono.just(ServerSentEvent.<String>builder("done").event("complete").build())));

        return openFlux.concatWith(logFlux);
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

        // 权限验证：确保用户只能访问自己任务的产物
        G3JobEntity job = orchestratorService.getJob(UUID.fromString(jobId));
        if (job == null) {
            return Result.error(404, "任务不存在");
        }
        if (!hasJobAccess(job)) {
            return Result.error(403, "无权访问该任务的产物");
        }

        List<G3ArtifactEntity> artifacts = orchestratorService.getArtifacts(UUID.fromString(jobId));
        List<ArtifactResponse> response = artifacts.stream()
                .map(ArtifactResponse::fromEntity)
                .toList();

        return Result.success(response);
    }

    /**
     * 获取单个产物的详细内容（包含完整代码/配置内容）
     *
     * @param jobId      任务ID
     * @param artifactId 产物ID
     * @return 产物内容
     */
    @GetMapping("/jobs/{jobId}/artifacts/{artifactId}/content")
    @Operation(summary = "获取产物内容")
    public Result<ArtifactContentResponse> getArtifactContent(
            @PathVariable String jobId,
            @PathVariable String artifactId
    ) {
        log.debug("[G3 API] 获取产物内容: jobId={}, artifactId={}", jobId, artifactId);

        UUID jobUuid = UUID.fromString(jobId);
        UUID artifactUuid = UUID.fromString(artifactId);

        G3JobEntity job = orchestratorService.getJob(jobUuid);
        if (job == null) {
            return Result.error(404, "任务不存在");
        }
        if (!hasJobAccess(job)) {
            return Result.error(403, "无权访问该任务的产物");
        }

        G3ArtifactEntity artifact = orchestratorService.getArtifact(jobUuid, artifactUuid);
        if (artifact == null) {
            return Result.error(404, "产物不存在");
        }

        return Result.success(ArtifactContentResponse.fromEntity(artifact));
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

        // 权限验证：确保用户只能访问自己任务的契约
        if (!hasJobAccess(job)) {
            return Result.error(403, "无权访问该任务的契约");
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
        UUID userId = getCurrentUserId();
        UUID tenantId = getCurrentTenantId();

        log.info("[G3 API] 旧API启动任务: requirement={}, userId={}, tenantId={}",
                truncate(requirement, 50), userId, tenantId);

        UUID jobId = orchestratorService.submitJob(requirement, userId, tenantId);
        return Result.success(new SubmitJobResponse(jobId.toString()));
    }

    /**
     * 日志流（旧API路径，兼容前端）
     */
    @GetMapping(value = "/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "日志流（兼容旧API）")
    public Flux<ServerSentEvent<String>> streamLogs(@PathVariable String taskId, HttpServletResponse response) {
        log.info("[G3 API] 旧API日志流: taskId={}", taskId);
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Content-Encoding", "identity");
        UUID id = UUID.fromString(taskId);
        Flux<ServerSentEvent<String>> openFlux = Flux.just(
                ServerSentEvent.<String>builder("connected").event("open").build(),
                ServerSentEvent.<String>builder("").comment(" ".repeat(2048)).build()
        );
        Flux<ServerSentEvent<String>> logFlux = orchestratorService.subscribeToLogs(id)
                .map(entry -> {
                    String eventName = "heartbeat".equalsIgnoreCase(entry.getLevel()) ? "heartbeat" : "log";
                    try {
                        return ServerSentEvent.<String>builder(objectMapper.writeValueAsString(entry))
                                .event(eventName)
                                .build();
                    } catch (Exception e) {
                        return ServerSentEvent.<String>builder("{\"level\":\"error\",\"message\":\"日志序列化失败\"}")
                                .event("error")
                                .build();
                    }
                })
                .concatWith(Mono.just(ServerSentEvent.<String>builder("done").event("complete").build()));

        return openFlux.concatWith(logFlux);
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
    public static class ArtifactContentResponse {
        private String id;
        private String filePath;
        private String fileName;
        private String generatedBy;
        private int round;
        private boolean hasErrors;
        private String compilerOutput;
        private String content;
        private String createdAt;

        public static ArtifactContentResponse fromEntity(G3ArtifactEntity artifact) {
            ArtifactContentResponse resp = new ArtifactContentResponse();
            resp.setId(artifact.getId().toString());
            resp.setFilePath(artifact.getFilePath());
            resp.setFileName(artifact.getFileName());
            resp.setGeneratedBy(artifact.getGeneratedBy());
            resp.setRound(artifact.getGenerationRound() != null ? artifact.getGenerationRound() : 0);
            resp.setHasErrors(Boolean.TRUE.equals(artifact.getHasErrors()));
            resp.setCompilerOutput(artifact.getCompilerOutput());
            resp.setContent(artifact.getContent());
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

    /**
     * 从认证上下文获取当前用户ID
     *
     * @return 用户ID（如果用户已登录），否则返回 null
     */
    private UUID getCurrentUserId() {
        try {
            if (StpUtil.isLogin()) {
                String userIdStr = StpUtil.getLoginIdAsString();
                if (StringUtils.hasText(userIdStr)) {
                    return UUID.fromString(userIdStr);
                }
            }
        } catch (Exception e) {
            log.debug("获取当前用户ID失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从 TenantContextHolder 获取当前租户ID
     *
     * @return 租户ID（如果已设置），否则返回 null
     */
    private UUID getCurrentTenantId() {
        try {
            String tenantIdStr = TenantContextHolder.getTenantId();
            if (StringUtils.hasText(tenantIdStr)) {
                return UUID.fromString(tenantIdStr);
            }
        } catch (Exception e) {
            log.debug("获取当前租户ID失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 验证当前用户是否有权访问指定任务
     *
     * 权限规则：
     * 1. 用户可以访问自己创建的任务（userId 匹配）
     * 2. 用户可以访问同一租户下的任务（tenantId 匹配）
     * 3. 匿名任务（userId=null 且 tenantId=null）允许任何人访问
     *
     * @param job 任务实体
     * @return 是否有权访问
     */
    private boolean hasJobAccess(G3JobEntity job) {
        if (job == null) {
            return false;
        }

        UUID currentUserId = getCurrentUserId();
        UUID currentTenantId = getCurrentTenantId();
        UUID jobUserId = job.getUserId();
        UUID jobTenantId = job.getTenantId();

        // 匿名任务：允许任何人访问（用于未登录的 Lab 测试场景）
        if (jobUserId == null && jobTenantId == null) {
            return true;
        }

        // 同一用户创建的任务
        if (currentUserId != null && currentUserId.equals(jobUserId)) {
            return true;
        }

        // 同一租户下的任务
        if (currentTenantId != null && currentTenantId.equals(jobTenantId)) {
            return true;
        }

        log.warn("[G3 API] 权限拒绝: currentUser={}, currentTenant={}, jobUser={}, jobTenant={}",
                currentUserId, currentTenantId, jobUserId, jobTenantId);
        return false;
    }
}
