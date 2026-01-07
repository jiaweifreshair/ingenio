package com.ingenio.backend.service.g3;

import com.ingenio.backend.agent.g3.IArchitectAgent;
import com.ingenio.backend.agent.g3.ICoachAgent;
import com.ingenio.backend.agent.g3.ICoderAgent;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.entity.IndustryTemplateEntity;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import com.ingenio.backend.mapper.IndustryTemplateMapper;
import com.ingenio.backend.mapper.g3.G3ArtifactMapper;
import com.ingenio.backend.mapper.g3.G3JobMapper;
import com.ingenio.backend.mapper.g3.G3ValidationResultMapper;
import com.ingenio.backend.service.blueprint.BlueprintComplianceResult;
import com.ingenio.backend.service.blueprint.BlueprintValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * G3引擎编排服务
 * 负责协调Architect、Coder、Coach三个Agent的执行流程
 *
 * 核心职责：
 * 1. 管理G3任务生命周期
 * 2. 协调Agent执行顺序：Architect → Coder → Validate → (Coach → Validate)*
 * 3. 实现自修复循环（最多N轮）
 * 4. 提供SSE日志流
 *
 * 状态机：
 * QUEUED → PLANNING → CODING → TESTING → COMPLETED/FAILED
 *                         ↑         ↓
 *                         ← COACH修复 ←
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class G3OrchestratorService {

    /**
     * 自引用，用于通过Spring代理调用@Async方法
     * 解决自调用时@Async注解不生效的问题
     */
    @Autowired
    @Lazy
    private G3OrchestratorService self;

    private final G3JobMapper jobMapper;
    private final G3ArtifactMapper artifactMapper;
    private final G3ValidationResultMapper validationResultMapper;
    private final AppSpecMapper appSpecMapper;
    private final IndustryTemplateMapper industryTemplateMapper;
    private final BlueprintValidator blueprintValidator;

    private final IArchitectAgent architectAgent;
    private final List<ICoderAgent> coderAgents;
    private final ICoachAgent coachAgent;
    private final G3SandboxService sandboxService;

    /**
     * 最大修复轮次
     */
    @Value("${ingenio.g3.sandbox.max-rounds:3}")
    private int maxRounds;

    /**
     * 活跃的日志流
     * Key: jobId, Value: Sinks.Many用于发送日志
     */
    private final Map<UUID, Sinks.Many<G3LogEntry>> activeLogStreams = new ConcurrentHashMap<>();

    /**
     * 提交新的G3任务
     *
     * @param requirement 需求文本
     * @param userId      用户ID（可选）
     * @param tenantId    租户ID（可选）
     * @return 任务ID
     */
    @Transactional
    public UUID submitJob(String requirement, UUID userId, UUID tenantId) {
        return submitJob(requirement, userId, tenantId, null, null, null);
    }

    /**
     * 提交新的G3任务（Blueprint增强版）
     *
     * 支持：
     * - appSpecId：从 AppSpec 加载 blueprintSpec/tenantId/userId 等上下文
     * - templateId：直接从行业模板加载 blueprintSpec（用于跳过 PlanRouting 选择步骤的快速试跑）
     *
     * @param requirement 需求文本
     * @param userId      用户ID（可选）
     * @param tenantId    租户ID（可选）
     * @param appSpecId   AppSpec ID（可选）
     * @param templateId  行业模板ID（可选）
     * @param maxRoundsOverride 最大修复轮次（可选）
     * @return 任务ID
     */
    @Transactional
    public UUID submitJob(
            String requirement,
            UUID userId,
            UUID tenantId,
            UUID appSpecId,
            UUID templateId,
            Integer maxRoundsOverride) {

        log.info("[G3] 提交新任务: requirement={}, appSpecId={}, templateId={}",
                truncate(requirement, 50), appSpecId, templateId);

        ResolvedJobContext resolvedContext = resolveJobContext(requirement, userId, tenantId, appSpecId, templateId);

        // 创建任务实体（显式生成UUID以兼容MyBatis-Plus）
        G3JobEntity job = G3JobEntity.builder()
                .id(UUID.randomUUID())  // 显式生成UUID，避免MyBatis-Plus insert时传入null
                .requirement(resolvedContext.requirement())
                .userId(resolvedContext.userId())
                .tenantId(resolvedContext.tenantId())
                .appSpecId(resolvedContext.appSpecId())
                .matchedTemplateId(resolvedContext.matchedTemplateId())
                .blueprintSpec(resolvedContext.blueprintSpec())
                .blueprintModeEnabled(resolvedContext.blueprintModeEnabled())
                .status(G3JobEntity.Status.QUEUED.getValue())
                .currentRound(0)
                .maxRounds(maxRoundsOverride != null ? maxRoundsOverride : maxRounds)
                .logs(new ArrayList<>())
                .build();

        // 保存到数据库
        jobMapper.insert(job);

        log.info("[G3] 任务创建成功: jobId={}", job.getId());

        // 触发异步执行（通过self调用以确保@Async生效）
        self.runJobAsync(job.getId());

        return job.getId();
    }

    /**
     * 解析G3任务的上下文信息
     *
     * 规则：
     * 1) appSpecId 优先：可补齐 tenantId/userId/blueprintSpec 等上下文
     * 2) templateId 次之：若 appSpec 未携带 blueprintSpec，则从模板加载
     * 3) blueprintModeEnabled：以显式开关优先，其次由 blueprintSpec 是否为空推导
     */
    private ResolvedJobContext resolveJobContext(
            String requirement,
            UUID userId,
            UUID tenantId,
            UUID appSpecId,
            UUID templateId) {

        String resolvedRequirement = requirement;
        UUID resolvedUserId = userId;
        UUID resolvedTenantId = tenantId;
        UUID resolvedAppSpecId = appSpecId;
        UUID resolvedTemplateId = null;
        java.util.Map<String, Object> resolvedBlueprintSpec = null;
        Boolean resolvedBlueprintModeEnabled = null;

        // 1) AppSpec 上下文
        if (appSpecId != null) {
            AppSpecEntity appSpec = appSpecMapper.selectById(appSpecId);
            if (appSpec == null) {
                log.warn("[G3] appSpecId不存在，忽略: {}", appSpecId);
            } else {
                // requirement 优先使用显式入参；若为空则回退到 specContent.userRequirement
                if (resolvedRequirement == null || resolvedRequirement.isBlank()) {
                    Object ur = appSpec.getSpecContent() != null ? appSpec.getSpecContent().get("userRequirement") : null;
                    resolvedRequirement = ur != null ? ur.toString() : resolvedRequirement;
                }

                // tenantId/userId 优先使用 appSpec
                resolvedTenantId = appSpec.getTenantId() != null ? appSpec.getTenantId() : resolvedTenantId;
                resolvedUserId = appSpec.getCreatedByUserId() != null ? appSpec.getCreatedByUserId() : resolvedUserId;

                // Blueprint
                resolvedBlueprintSpec = appSpec.getBlueprintSpec();
                resolvedBlueprintModeEnabled = appSpec.getBlueprintModeEnabled();

                // 模板来源（可选）
                resolvedTemplateId = appSpec.getSelectedTemplateId();
            }
        }

        // 2) 模板上下文（templateId 优先覆盖 matchedTemplateId；blueprintSpec 仅在为空时补齐）
        if (templateId != null) {
            IndustryTemplateEntity template = industryTemplateMapper.selectById(templateId);
            if (template == null) {
                log.warn("[G3] templateId不存在，忽略: {}", templateId);
            } else {
                resolvedTemplateId = templateId;
                if (resolvedBlueprintSpec == null || resolvedBlueprintSpec.isEmpty()) {
                    resolvedBlueprintSpec = template.getBlueprintSpec();
                }
            }
        }

        // 3) 推导 blueprintModeEnabled
        boolean inferredBlueprintModeEnabled =
                (resolvedBlueprintModeEnabled != null && Boolean.TRUE.equals(resolvedBlueprintModeEnabled))
                        || (resolvedBlueprintModeEnabled == null && resolvedBlueprintSpec != null && !resolvedBlueprintSpec.isEmpty());

        return new ResolvedJobContext(
                resolvedRequirement,
                resolvedUserId,
                resolvedTenantId,
                resolvedAppSpecId,
                resolvedTemplateId,
                resolvedBlueprintSpec,
                inferredBlueprintModeEnabled
        );
    }

    /**
     * submitJob 期间解析出的上下文信息
     */
    private record ResolvedJobContext(
            String requirement,
            UUID userId,
            UUID tenantId,
            UUID appSpecId,
            UUID matchedTemplateId,
            java.util.Map<String, Object> blueprintSpec,
            boolean blueprintModeEnabled
    ) {}

    /**
     * 异步执行任务
     */
    @Async("g3TaskExecutor")
    public void runJobAsync(UUID jobId) {
        log.info("[G3] 开始异步执行任务: jobId={}", jobId);
        runJob(jobId);
    }

    /**
     * 执行G3任务主流程
     *
     * @param jobId 任务ID
     */
    public void runJob(UUID jobId) {
        G3JobEntity job = jobMapper.selectById(jobId);
        if (job == null) {
            log.error("[G3] 任务不存在: jobId={}", jobId);
            return;
        }

        // 创建日志消费者
        Consumer<G3LogEntry> logConsumer = entry -> {
            addLog(job, entry);
            emitLog(jobId, entry);
        };

        try {
            // 标记任务开始
            job.setStartedAt(Instant.now());
            updateJobStatus(job, G3JobEntity.Status.PLANNING);

            logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.PLAYER, "G3引擎启动，开始处理需求"));

            // ========== Phase 1: 架构设计 ==========
            logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.ARCHITECT, "开始架构设计阶段..."));

            IArchitectAgent.ArchitectResult architectResult = architectAgent.design(job, logConsumer);

            if (!architectResult.success()) {
                failJob(job, "架构设计失败: " + architectResult.errorMessage(), logConsumer);
                return;
            }

            // 锁定契约
            job.setContractYaml(architectResult.contractYaml());
            job.setDbSchemaSql(architectResult.dbSchemaSql());
            job.lockContract();
            jobMapper.updateById(job);

            // 保存契约产物
            saveContractArtifacts(job, architectResult);

            logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.ARCHITECT, "架构设计完成，契约已锁定"));

            // ========== Phase 2: 代码生成 ==========
            updateJobStatus(job, G3JobEntity.Status.CODING);
            logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.PLAYER, "开始代码生成阶段..."));

            // 获取后端编码器
            ICoderAgent backendCoder = coderAgents.stream()
                    .filter(c -> "backend".equals(c.getTargetType()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("未找到后端编码器"));

            ICoderAgent.CoderResult coderResult = backendCoder.generate(job, 0, logConsumer);

            if (!coderResult.success()) {
                failJob(job, "代码生成失败: " + coderResult.errorMessage(), logConsumer);
                return;
            }

            // 保存代码产物
            List<G3ArtifactEntity> artifacts = coderResult.artifacts();
            for (G3ArtifactEntity artifact : artifacts) {
                artifactMapper.insert(artifact);
            }

            // 生成/补齐构建脚手架产物（pom.xml、UUIDv8TypeHandler），确保：
            // 1) 前端“结果展示”能看到完整工程文件
            // 2) 编译失败时能定位到可修复文件（尤其是 pom.xml）
            artifacts = ensureBuildScaffoldArtifacts(job, artifacts, logConsumer);

            logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.PLAYER,
                    "代码生成完成: " + artifacts.size() + " 个文件"));

            // ========== Phase 3: 编译验证 + 自修复循环 ==========
            updateJobStatus(job, G3JobEntity.Status.TESTING);

            boolean validated = false;
            int round = 0;
            int fixAttempts = 0;

            while (!validated && round < maxRounds) {
                logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.EXECUTOR,
                        "开始第 " + (round + 1) + " 轮编译验证..."));

                // 执行沙箱验证
                G3ValidationResultEntity validationResult = sandboxService.validate(job, artifacts, logConsumer);
                validationResultMapper.insert(validationResult);

                if (validationResult.getPassed()) {
                    logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR, "编译验证通过！"));

                    // Blueprint 合规性验证（F4）
                    BlueprintComplianceResult blueprintCompliance =
                            validateBlueprintCompliance(job, artifacts, logConsumer);

                    if (!blueprintCompliance.passed()) {
                        logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.COACH,
                                "Blueprint 合规性验证失败，发现 " + blueprintCompliance.violations().size() + " 项违规"));

                        // 逐条输出违规项，确保前端日志视图可读（避免换行被折叠）
                        for (String violation : blueprintCompliance.violations()) {
                            logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.COACH, "Blueprint 违规项: " + violation));
                        }

                        failJob(job, "Blueprint 合规性验证失败", logConsumer);
                        return;
                    }

                    logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.COACH, "Blueprint 合规性验证通过 ✅"));
                    validated = true;
                } else {
                    logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                            "编译失败: " + validationResult.getErrorCount() + " 个错误"));

                    // 检查是否还有修复机会
                    if (round + 1 >= maxRounds) {
                        logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.COACH,
                                "已达到最大修复轮次，停止修复"));
                        break;
                    }

                    // 获取有错误的产物
                    List<G3ArtifactEntity> errorArtifacts = artifacts.stream()
                            .filter(a -> Boolean.TRUE.equals(a.getHasErrors()))
                            .collect(Collectors.toList());

                    if (errorArtifacts.isEmpty()) {
                        // 兜底：根据 ValidationResult 的 parsedErrors/stdout/stderr 构造可修复目标（至少包含 pom.xml）
                        errorArtifacts = buildFallbackErrorArtifacts(job, artifacts, validationResult, logConsumer);
                    }

                    if (errorArtifacts.isEmpty()) {
                        logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.COACH,
                                "无法定位错误文件，停止修复（请查看 Maven 输出片段）"));
                        break;
                    }

                    // 调用Coach修复
                    fixAttempts++;
                    logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.COACH,
                            "开始修复 " + errorArtifacts.size() + " 个错误文件...（attempt " + fixAttempts + "/" + maxRounds + "）"));

                    ICoachAgent.CoachResult coachResult = coachAgent.fix(
                            job,
                            errorArtifacts,
                            List.of(validationResult),
                            logConsumer
                    );

                    if (!coachResult.success() || coachResult.fixedArtifacts().isEmpty()) {
                        logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.COACH,
                                "修复失败: " + (coachResult.errorMessage() != null ?
                                        coachResult.errorMessage() : "无法生成修复代码")));
                        break;
                    }

                    // 更新产物列表
                    for (G3ArtifactEntity fixed : coachResult.fixedArtifacts()) {
                        artifactMapper.insert(fixed);
                    }

                    // 替换错误产物
                    artifacts = mergeArtifacts(artifacts, coachResult.fixedArtifacts());

                    round++;
                    job.setCurrentRound(round);
                    jobMapper.updateById(job);

                    logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.COACH,
                            "修复完成，准备重新验证"));
                }
            }

            // ========== Phase 4: 完成 ==========
            if (validated) {
                completeJob(job, logConsumer);
            } else {
                int attempts = Math.max(fixAttempts, round);
                failJob(job, "编译验证未通过，已尝试 " + attempts + " 次修复", logConsumer);
            }

        } catch (Exception e) {
            log.error("[G3] 任务执行异常: jobId={}", jobId, e);
            failJob(job, "任务执行异常: " + e.getMessage(), logConsumer);
        } finally {
            // 清理沙箱
            if (job.getSandboxId() != null) {
                sandboxService.destroySandbox(job.getSandboxId());
            }

            // 关闭日志流
            closeLogStream(jobId);
        }
    }

    /**
     * 当无法从产物标记中定位错误文件时，尝试根据验证结果构造“可修复目标”。
     *
     * 优先级：
     * 1) parsedErrors 指向的文件（若存在对应产物）
     * 2) pom.xml（构建失败但无 Java 编译错误时的常见入口）
     *
     * 目的：
     * - 避免出现“编译失败: 0 个错误 → 无法定位错误文件 → 已尝试 0 轮修复”的体验断裂
     * - 让 Coach 至少拿到构建失败摘要，从而尝试修复 pom/repository/插件配置
     */
    private List<G3ArtifactEntity> buildFallbackErrorArtifacts(
            G3JobEntity job,
            List<G3ArtifactEntity> artifacts,
            G3ValidationResultEntity validationResult,
            Consumer<G3LogEntry> logConsumer
    ) {
        List<G3ArtifactEntity> result = new ArrayList<>();

        String buildSummary = extractBuildFailureSummary(
                validationResult.getStdout(),
                validationResult.getStderr()
        );

        // 1) parsedErrors → 关联产物
        if (validationResult.getParsedErrors() != null && !validationResult.getParsedErrors().isEmpty()) {
            for (G3ValidationResultEntity.ParsedError err : validationResult.getParsedErrors()) {
                if (err == null || err.getFile() == null) continue;
                String file = err.getFile();
                String fileName = file.contains("/") ? file.substring(file.lastIndexOf('/') + 1) : file;

                artifacts.stream()
                        .filter(a -> file.equals(a.getFilePath()) || fileName.equals(a.getFileName()))
                        .findFirst()
                        .ifPresent(a -> {
                            if (!Boolean.TRUE.equals(a.getHasErrors())) {
                                a.markError(buildSummary);
                            }
                            result.add(a);
                        });
            }
        }

        // 2) 兜底：pom.xml
        if (result.isEmpty()) {
            G3ArtifactEntity pom = artifacts.stream()
                    .filter(a -> "pom.xml".equals(a.getFileName()))
                    .findFirst()
                    .orElse(null);

            if (pom == null) {
                // 若尚未纳入产物（历史版本兼容），临时生成一份 pom 并标记错误
                String pomContent = sandboxService.generatePomXml("com.ingenio.generated", "g3-generated-app", "1.0.0-SNAPSHOT");
                pom = G3ArtifactEntity.create(
                        job.getId(),
                        "pom.xml",
                        pomContent,
                        G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                        job.getCurrentRound()
                );
                pom.markError(buildSummary);
                artifactMapper.insert(pom);
                artifacts.add(pom);
                logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.COACH, "兜底：已生成 pom.xml 作为修复目标"));
            } else if (!Boolean.TRUE.equals(pom.getHasErrors())) {
                pom.markError(buildSummary);
            }

            result.add(pom);
        }

        // 输出摘要，帮助前端快速定位（避免需要打开对话框）
        logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.COACH, "构建失败摘要: " + truncate(buildSummary.replace("\n", " | "), 220)));

        return result;
    }

    /**
     * 提取 Maven 构建失败摘要（控制长度，避免日志过大）
     */
    private String extractBuildFailureSummary(String stdout, String stderr) {
        String output = (stdout == null ? "" : stdout) + "\n" + (stderr == null ? "" : stderr);
        output = output.replace("\r", "");
        if (output.isBlank()) return "构建失败（输出为空）";

        String[] lines = output.split("\n");
        StringBuilder sb = new StringBuilder();
        int limit = 80;
        int count = 0;
        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("[ERROR]") || line.contains("BUILD FAILURE") || line.contains("Failed to execute goal")) {
                sb.append(line).append("\n");
                count++;
                if (count >= limit) break;
            }
        }
        if (sb.isEmpty()) {
            int tail = Math.min(60, lines.length);
            for (int i = Math.max(0, lines.length - tail); i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    sb.append(line).append("\n");
                }
            }
        }
        String summary = sb.toString();
        return summary.length() > 8000 ? summary.substring(0, 8000) + "\n... (已截断)" : summary;
    }

    /**
     * 确保构建脚手架文件存在并纳入产物列表
     *
     * 说明：
     * - 之前 pom.xml/UUIDv8TypeHandler 由 SandboxService 在校验阶段临时生成，仅用于沙箱编译，
     *   但不会入库，导致前端无法展示“生成结果”，也会造成编译失败时无法定位错误文件。
     * - 这里将其提前固化为产物，纳入 G3 的可追溯与可修复闭环。
     */
    private List<G3ArtifactEntity> ensureBuildScaffoldArtifacts(
            G3JobEntity job,
            List<G3ArtifactEntity> artifacts,
            Consumer<G3LogEntry> logConsumer
    ) {
        List<G3ArtifactEntity> enriched = new ArrayList<>(artifacts);

        boolean hasPom = enriched.stream().anyMatch(a -> "pom.xml".equals(a.getFileName()));
        if (!hasPom) {
            String pomContent = sandboxService.generatePomXml("com.ingenio.generated", "g3-generated-app", "1.0.0-SNAPSHOT");
            G3ArtifactEntity pomArtifact = G3ArtifactEntity.create(
                    job.getId(),
                    "pom.xml",
                    pomContent,
                    G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                    job.getCurrentRound()
            );
            artifactMapper.insert(pomArtifact);
            enriched.add(pomArtifact);
            logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR, "pom.xml已纳入产物列表"));
        }

        boolean hasTypeHandler = enriched.stream().anyMatch(a -> a.getFileName().contains("UUIDv8TypeHandler"));
        if (!hasTypeHandler) {
            String typeHandlerContent = sandboxService.generateUUIDv8TypeHandlerContent();
            G3ArtifactEntity typeHandlerArtifact = G3ArtifactEntity.create(
                    job.getId(),
                    "src/main/java/com/ingenio/backend/config/UUIDv8TypeHandler.java",
                    typeHandlerContent,
                    G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                    job.getCurrentRound()
            );
            artifactMapper.insert(typeHandlerArtifact);
            enriched.add(typeHandlerArtifact);
            logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR, "UUIDv8TypeHandler已纳入产物列表"));
        }

        return enriched;
    }

    /**
     * Blueprint 合规性验证（F4）
     *
     * 说明：
     * - 当前 Blueprint JSON 主要约束 schema/features，因此最小版校验聚焦：
     *   1) DDL 是否覆盖 schema（Architect 输出）
     *   2) Entity 是否覆盖 schema（Coder 输出）
     */
    private BlueprintComplianceResult validateBlueprintCompliance(
            G3JobEntity job,
            List<G3ArtifactEntity> artifacts,
            Consumer<G3LogEntry> logConsumer) {

        boolean blueprintModeEnabled = Boolean.TRUE.equals(job.getBlueprintModeEnabled())
                && job.getBlueprintSpec() != null
                && !job.getBlueprintSpec().isEmpty();

        if (!blueprintModeEnabled) {
            return BlueprintComplianceResult.passedResult();
        }

        BlueprintComplianceResult ddlCompliance =
                blueprintValidator.validateSchemaCompliance(job.getDbSchemaSql(), job.getBlueprintSpec());

        BlueprintComplianceResult artifactCompliance =
                blueprintValidator.validateBackendArtifactsCompliance(artifacts, job.getBlueprintSpec());

        if (ddlCompliance.passed() && artifactCompliance.passed()) {
            return BlueprintComplianceResult.passedResult();
        }

        List<String> merged = new ArrayList<>();
        if (!ddlCompliance.passed()) {
            merged.addAll(ddlCompliance.violations());
        }
        if (!artifactCompliance.passed()) {
            merged.addAll(artifactCompliance.violations());
        }

        // 兜底：避免返回空 violations 但 passed=false 的异常情况
        if (merged.isEmpty()) {
            merged.add("Blueprint 合规性验证失败（原因未知）");
        }

        return BlueprintComplianceResult.failedResult(merged);
    }

    /**
     * 获取任务状态
     */
    public G3JobEntity getJob(UUID jobId) {
        return jobMapper.selectById(jobId);
    }

    /**
     * 获取任务产物
     */
    public List<G3ArtifactEntity> getArtifacts(UUID jobId) {
        return artifactMapper.selectLatestByJobId(jobId);
    }

    /**
     * 获取单个产物（包含完整内容）
     *
     * @param jobId      任务ID
     * @param artifactId 产物ID
     * @return 产物实体（若不存在或不属于该任务则返回 null）
     */
    public G3ArtifactEntity getArtifact(UUID jobId, UUID artifactId) {
        G3ArtifactEntity artifact = artifactMapper.selectById(artifactId);
        if (artifact == null) return null;
        if (!jobId.equals(artifact.getJobId())) return null;
        return artifact;
    }

    /**
     * SSE 心跳间隔（秒）
     * 每隔此时间发送一次心跳事件，防止 SSE 连接因空闲而超时
     * 注意：设置为15秒以避免某些代理/网关的30秒空闲超时
     */
    private static final int HEARTBEAT_INTERVAL_SECONDS = 15;

    /**
     * 订阅任务日志流
     *
     * 实现心跳机制以防止 SSE 连接超时：
     * 1. 每 30 秒发送一次心跳事件
     * 2. 当任务完成（COMPLETED/FAILED）时停止心跳
     * 3. 前端应过滤掉心跳消息（level=heartbeat）
     */
    public Flux<G3LogEntry> subscribeToLogs(UUID jobId) {
        Sinks.Many<G3LogEntry> sink = activeLogStreams.computeIfAbsent(
                jobId,
                k -> Sinks.many().multicast().onBackpressureBuffer()
        );

        // 创建心跳 Flux（每 30 秒发送一次）
        Flux<G3LogEntry> heartbeatFlux = Flux.interval(Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS))
                .map(tick -> G3LogEntry.heartbeat())
                .takeUntil(entry -> isJobCompleted(jobId));

        // 历史日志：直接从数据库读取并输出到当前订阅者
        //
        // 注意：不能通过 multicast sink 在订阅前 tryEmitNext 历史日志：
        // - multicast sink 在无订阅者时会丢弃事件（FAIL_ZERO_SUBSCRIBER）
        // - 这会导致“页面订阅到 SSE 但看不到任何执行日志”的问题
        Flux<G3LogEntry> historyFlux = Flux.defer(() -> {
            G3JobEntity job = jobMapper.selectById(jobId);
            if (job == null || job.getLogs() == null || job.getLogs().isEmpty()) {
                return Flux.empty();
            }
            return Flux.fromIterable(job.getLogs());
        });

        // 实时日志：仅包含订阅之后产生的日志
        Flux<G3LogEntry> liveFlux = sink.asFlux();

        return Flux.merge(historyFlux, liveFlux, heartbeatFlux)
                .doOnCancel(() -> log.debug("[G3] SSE 连接已取消: jobId={}", jobId))
                .doOnComplete(() -> log.debug("[G3] SSE 流已完成: jobId={}", jobId));
    }

    /**
     * 检查任务是否已完成（COMPLETED 或 FAILED）
     */
    private boolean isJobCompleted(UUID jobId) {
        G3JobEntity job = jobMapper.selectById(jobId);
        if (job == null) {
            return true; // 任务不存在，视为完成
        }
        String status = job.getStatus();
        return G3JobEntity.Status.COMPLETED.getValue().equals(status)
                || G3JobEntity.Status.FAILED.getValue().equals(status);
    }

    /**
     * 添加日志到任务
     */
    private void addLog(G3JobEntity job, G3LogEntry entry) {
        if (job.getLogs() == null) {
            job.setLogs(new ArrayList<>());
        }
        job.getLogs().add(entry);

        // 异步更新数据库
        jobMapper.updateById(job);
    }

    /**
     * 发送日志到SSE流
     */
    private void emitLog(UUID jobId, G3LogEntry entry) {
        Sinks.Many<G3LogEntry> sink = activeLogStreams.get(jobId);
        if (sink != null) {
            sink.tryEmitNext(entry);
        }
    }

    /**
     * 关闭日志流
     */
    private void closeLogStream(UUID jobId) {
        Sinks.Many<G3LogEntry> sink = activeLogStreams.remove(jobId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    /**
     * 更新任务状态
     */
    private void updateJobStatus(G3JobEntity job, G3JobEntity.Status status) {
        job.setStatus(status.getValue());
        job.setUpdatedAt(Instant.now());
        jobMapper.updateById(job);
    }

    /**
     * 标记任务完成
     */
    private void completeJob(G3JobEntity job, Consumer<G3LogEntry> logConsumer) {
        job.setStatus(G3JobEntity.Status.COMPLETED.getValue());
        job.setCompletedAt(Instant.now());
        jobMapper.updateById(job);

        logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.PLAYER,
                "🎉 G3任务完成！共生成 " + artifactMapper.selectByJobId(job.getId()).size() + " 个文件"));
    }

    /**
     * 标记任务失败
     */
    private void failJob(G3JobEntity job, String error, Consumer<G3LogEntry> logConsumer) {
        job.setStatus(G3JobEntity.Status.FAILED.getValue());
        job.setLastError(error);
        job.setCompletedAt(Instant.now());
        jobMapper.updateById(job);

        logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.PLAYER, "❌ G3任务失败: " + error));
    }

    /**
     * 保存契约产物
     */
    private void saveContractArtifacts(G3JobEntity job, IArchitectAgent.ArchitectResult result) {
        // 保存OpenAPI契约
        if (result.contractYaml() != null) {
            G3ArtifactEntity contractArtifact = G3ArtifactEntity.create(
                    job.getId(),
                    "contracts/openapi.yaml",
                    result.contractYaml(),
                    G3ArtifactEntity.GeneratedBy.ARCHITECT,
                    0
            );
            artifactMapper.insert(contractArtifact);
        }

        // 保存DB Schema
        if (result.dbSchemaSql() != null) {
            G3ArtifactEntity schemaArtifact = G3ArtifactEntity.create(
                    job.getId(),
                    "db/schema.sql",
                    result.dbSchemaSql(),
                    G3ArtifactEntity.GeneratedBy.ARCHITECT,
                    0
            );
            artifactMapper.insert(schemaArtifact);
        }
    }

    /**
     * 合并产物列表（用修复后的替换原有的）
     */
    private List<G3ArtifactEntity> mergeArtifacts(
            List<G3ArtifactEntity> original,
            List<G3ArtifactEntity> fixed) {

        // 创建文件路径到修复产物的映射
        Map<String, G3ArtifactEntity> fixedMap = fixed.stream()
                .collect(Collectors.toMap(G3ArtifactEntity::getFilePath, a -> a, (a, b) -> b));

        // 替换原有产物
        return original.stream()
                .map(artifact -> fixedMap.getOrDefault(artifact.getFilePath(), artifact))
                .collect(Collectors.toList());
    }

    /**
     * 截断字符串
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
