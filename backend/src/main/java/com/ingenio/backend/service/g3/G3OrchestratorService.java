package com.ingenio.backend.service.g3;

import com.ingenio.backend.agent.g3.IArchitectAgent;
import com.ingenio.backend.agent.g3.ICoachAgent;
import com.ingenio.backend.agent.g3.ICoderAgent;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.entity.GenerationTaskEntity;
import com.ingenio.backend.entity.GenerationVersionEntity;
import com.ingenio.backend.entity.IndustryTemplateEntity;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3ErrorSignature;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.entity.g3.G3PlanningFileEntity;
import com.ingenio.backend.entity.g3.G3SessionMemory;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import com.ingenio.backend.mapper.IndustryTemplateMapper;
import com.ingenio.backend.mapper.GenerationTaskMapper;
import com.ingenio.backend.mapper.GenerationVersionMapper;
import com.ingenio.backend.mapper.g3.G3ArtifactMapper;
import com.ingenio.backend.mapper.g3.G3JobMapper;
import com.ingenio.backend.mapper.g3.G3ValidationResultMapper;
import com.ingenio.backend.dto.VersionType;
import com.ingenio.backend.service.VersionSnapshotService;
import com.ingenio.backend.service.blueprint.BlueprintComplianceResult;
import com.ingenio.backend.service.blueprint.BlueprintValidator;
import com.ingenio.backend.websocket.G3WebSocketBroadcaster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
 * ↑ ↓
 * ← COACH修复 ←
 */
@Service
public class G3OrchestratorService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(G3OrchestratorService.class);

    /**
     * 匿名任务默认用户ID。
     *
     * 是什么：匿名任务的占位用户ID。
     * 做什么：在 userId 为空时填充 generation_tasks.user_id。
     * 为什么：generation_tasks 表 user_id 不允许为空。
     */
    private static final UUID DEFAULT_ANONYMOUS_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

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
    private final GenerationTaskMapper generationTaskMapper;
    private final GenerationVersionMapper generationVersionMapper;
    private final VersionSnapshotService snapshotService;
    private final BlueprintValidator blueprintValidator;
    private final G3DependencyAnalyzer dependencyAnalyzer;
    private final G3PhaseValidator phaseValidator;
    private final G3KnowledgeStorePort knowledgeStore;
    private final G3RepoIndexService repoIndexService;

    private final IArchitectAgent architectAgent;
    private final List<ICoderAgent> coderAgents;
    private final ICoachAgent coachAgent;
    private final G3SandboxService sandboxService;
    private final G3PlanningFileService planningFileService;
    private final G3WebSocketBroadcaster g3WebSocketBroadcaster;
    private final G3MemoryPersistenceService memoryPersistenceService;
    private final G3CodeArchiveService codeArchiveService;
    private final FrontendApiClientGenerator frontendApiClientGenerator;
    private final com.ingenio.backend.service.NLRequirementAnalyzer nlRequirementAnalyzer;
    private final G3LogStreamService g3LogStreamService;

    /**
     * 最大修复轮次
     */
    @Value("${ingenio.g3.sandbox.max-rounds:3}")
    private int maxRounds;

    // Manual Constructor to replace @RequiredArgsConstructor
    public G3OrchestratorService(
            G3JobMapper jobMapper,
            G3ArtifactMapper artifactMapper,
            G3ValidationResultMapper validationResultMapper,
            AppSpecMapper appSpecMapper,
            IndustryTemplateMapper industryTemplateMapper,
            GenerationTaskMapper generationTaskMapper,
            GenerationVersionMapper generationVersionMapper,
            VersionSnapshotService snapshotService,
            BlueprintValidator blueprintValidator,
            G3DependencyAnalyzer dependencyAnalyzer,
            G3PhaseValidator phaseValidator,
            G3KnowledgeStorePort knowledgeStore,
            G3RepoIndexService repoIndexService,
            IArchitectAgent architectAgent,
            List<ICoderAgent> coderAgents,
            ICoachAgent coachAgent,
            G3SandboxService sandboxService,
            G3PlanningFileService planningFileService,
            G3WebSocketBroadcaster g3WebSocketBroadcaster,
            G3MemoryPersistenceService memoryPersistenceService,
            G3CodeArchiveService codeArchiveService,
            FrontendApiClientGenerator frontendApiClientGenerator,
            com.ingenio.backend.service.NLRequirementAnalyzer nlRequirementAnalyzer,
            G3LogStreamService g3LogStreamService) {
        this.jobMapper = jobMapper;
        this.artifactMapper = artifactMapper;
        this.validationResultMapper = validationResultMapper;
        this.appSpecMapper = appSpecMapper;
        this.industryTemplateMapper = industryTemplateMapper;
        this.generationTaskMapper = generationTaskMapper;
        this.generationVersionMapper = generationVersionMapper;
        this.snapshotService = snapshotService;
        this.blueprintValidator = blueprintValidator;
        this.dependencyAnalyzer = dependencyAnalyzer;
        this.phaseValidator = phaseValidator;
        this.knowledgeStore = knowledgeStore;
        this.repoIndexService = repoIndexService;
        this.architectAgent = architectAgent;
        this.coderAgents = coderAgents;
        this.coachAgent = coachAgent;
        this.sandboxService = sandboxService;
        this.planningFileService = planningFileService;
        this.g3WebSocketBroadcaster = g3WebSocketBroadcaster;
        this.memoryPersistenceService = memoryPersistenceService;
        this.codeArchiveService = codeArchiveService;
        this.frontendApiClientGenerator = frontendApiClientGenerator;
        this.nlRequirementAnalyzer = nlRequirementAnalyzer;
        this.g3LogStreamService = g3LogStreamService;
    }

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
     * @param requirement       需求文本
     * @param userId            用户ID（可选）
     * @param tenantId          租户ID（可选）
     * @param appSpecId         AppSpec ID（可选）
     * @param templateId        行业模板ID（可选）
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

        log.info("[G3] 提交新任务: requirement={}, appSpecId={}, templateId={}, maxRoundsConfig={}",
                truncate(requirement, 50), appSpecId, templateId, this.maxRounds);

        ResolvedJobContext resolvedContext = resolveJobContext(requirement, userId, tenantId, appSpecId, templateId);

        // 创建任务实体（显式生成UUID以兼容MyBatis-Plus）
        G3JobEntity job = G3JobEntity.builder()
                .id(UUID.randomUUID()) // 显式生成UUID，避免MyBatis-Plus insert时传入null
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

        // 同步创建generation_tasks记录，确保时光机版本链可用
        ensureGenerationTask(job);

        // 初始化规划文件（失败不影响主流程，但会影响前端“规划文件”视图）
        safeInitializePlanningFiles(job);

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
                    Object ur = appSpec.getSpecContent() != null ? appSpec.getSpecContent().get("userRequirement")
                            : null;
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

        // M3: 增强 - 检测并注入 AI 能力（防止分析阶段未正确传递）
        if (resolvedBlueprintSpec != null && !resolvedBlueprintSpec.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<String> caps = (List<String>) resolvedBlueprintSpec.get("aiCapabilities");
            if (caps == null || caps.isEmpty()) {
                List<String> detected = nlRequirementAnalyzer.detectAiCapabilities(resolvedRequirement);
                if (!detected.isEmpty()) {
                    // 必须使用可变Map
                    if (!(resolvedBlueprintSpec instanceof java.util.HashMap)) {
                        resolvedBlueprintSpec = new java.util.HashMap<>(resolvedBlueprintSpec);
                    }
                    resolvedBlueprintSpec.put("aiCapabilities", detected);
                    log.info("[G3] 自动注入 AI 能力: {}", detected);
                }
            }
        }

        // 3) 推导 blueprintModeEnabled
        boolean inferredBlueprintModeEnabled = (resolvedBlueprintModeEnabled != null
                && Boolean.TRUE.equals(resolvedBlueprintModeEnabled))
                || (resolvedBlueprintModeEnabled == null && resolvedBlueprintSpec != null
                        && !resolvedBlueprintSpec.isEmpty());

        return new ResolvedJobContext(
                resolvedRequirement,
                resolvedUserId,
                resolvedTenantId,
                resolvedAppSpecId,
                resolvedTemplateId,
                resolvedBlueprintSpec,
                inferredBlueprintModeEnabled);
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
            boolean blueprintModeEnabled) {
    }

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

        // M1增强：从 Redis 加载或创建 Session Memory
        G3SessionMemory sessionMemory = memoryPersistenceService.getOrCreate(jobId);
        log.debug("[G3] SessionMemory 已加载/创建: jobId={}, 历史轮次={}", jobId, sessionMemory.getRepairAttemptCount());

        try {
            // RAG: 尝试构建仓库级索引（best-effort）
            try {
                repoIndexService.ensureIndexed(job, logConsumer);
            } catch (Exception e) {
                logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                        "Repo Index 构建失败（忽略）: " + e.getMessage()));
            }

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
            // 规划文件：阶段1完成
            safeUpdatePhase(job.getId(), 1, true, G3PlanningFileEntity.UPDATER_ARCHITECT);

            // 分析任务依赖并更新详细计划
            safeUpdateDetailedTaskPlan(job);

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

            // 规划文件：根据产物粗略标记阶段2-5，并写入 context.md 已生成文件清单
            safeUpdatePlanningFromArtifacts(job.getId(), artifacts);

            // M6: 构建向量索引 (RAG)
            knowledgeStore.ingest(job, artifacts);

            // M2: 生成前端 API Client (基于 OpenAPI 契约)
            if (job.getContractYaml() != null && !job.getContractYaml().isBlank()) {
                try {
                    logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.PLAYER,
                            "正在生成前端 API Client (TypeScript)..."));
                    List<G3ArtifactEntity> apiClientArtifacts = frontendApiClientGenerator.generate(job, "/api");
                    for (G3ArtifactEntity artifact : apiClientArtifacts) {
                        artifactMapper.insert(artifact);
                    }
                    artifacts.addAll(apiClientArtifacts);
                    logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.PLAYER,
                            "前端 API Client 生成完成: " + apiClientArtifacts.size() + " 个文件"));
                } catch (Exception e) {
                    logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.PLAYER,
                            "前端 API Client 生成失败 (非阻塞): " + e.getMessage()));
                    log.warn("FrontendApiClientGenerator failed for job {}: {}", job.getId(), e.getMessage());
                }
            }

            // ========== Phase 3: 编译验证 + 自修复循环 (M3 & M5 Refactored) ==========
            updateJobStatus(job, G3JobEntity.Status.TESTING);

            boolean validated = false;
            int round = 0;
            int fixAttempts = 0;
            int validationAttempts = 0;
            boolean hasEnvironmentError = false;

            // M5: 动态轮次配置
            int baseMaxRounds = job.getMaxRounds() != null ? job.getMaxRounds() : maxRounds;
            int currentMaxRounds = baseMaxRounds;
            int MAX_DYNAMIC_EXTENSION = 3;

            while (!validated && round < currentMaxRounds) {
                validationAttempts++;
                logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.EXECUTOR,
                        "开始第 " + (round + 1) + " 轮全流程验证..."));

                // M3 & M4: 统一验证阶段 (编译 -> Blueprint -> 静态分析)
                G3PhaseValidator.ValidationResult phaseResult = phaseValidator.validateAll(job, artifacts, logConsumer);
                G3ValidationResultEntity validationResult = phaseResult.sandboxResult();

                if (validationResult != null) {
                    validationResultMapper.insert(validationResult);
                } else {
                    logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.EXECUTOR, "验证系统异常：无验证结果"));
                    break;
                }

                if (phaseResult.passed()) {
                    logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR, "全流程验证通过！"));
                    // 规划文件：阶段7完成
                    safeUpdatePhase(job.getId(), 7, true, G3PlanningFileEntity.UPDATER_SYSTEM);
                    validated = true;
                } else {
                    // 验证失败处理

                    // 1. 如果编译通过但其他阶段失败
                    if (Boolean.TRUE.equals(validationResult.getPassed())) {
                        logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.COACH,
                                "代码编译通过，但未通过静态分析或规范检查，Coach 暂不支持自动修复此类问题。"));
                        // 这里可以选择直接 failJob 或者尝试修复（如果 Coach 升级后）
                        // 当前 MVP 策略：停止修复
                        break;
                    }

                    // 2. 编译失败
                    logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                            "验证失败: " + validationResult.getErrorCount() + " 个编译错误"));

                    // M5: 检查是否需要动态延长轮次 (先计算签名再决定，这里只是日志提示)
                    if (round + 1 >= currentMaxRounds && currentMaxRounds < baseMaxRounds + MAX_DYNAMIC_EXTENSION) {
                        log.debug("[M5] 接近轮次上限，将根据修复进展判断是否延长...");
                    } else if (round + 1 >= currentMaxRounds) {
                        logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.COACH,
                                "已达到最大修复轮次，停止修复"));
                        break;
                    }

                    // 3. 环境错误检查
                    if (isEnvironmentError(validationResult)) {
                        hasEnvironmentError = true;
                        logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                                "检测到环境类错误，Coach Agent无法修复此类问题，直接进入下一轮验证..."));
                        round++;
                        job.setCurrentRound(round);
                        jobMapper.updateById(job);
                        continue;
                    }

                    // 4. 定位错误产物
                    List<G3ArtifactEntity> errorArtifacts = artifacts.stream()
                            .filter(a -> Boolean.TRUE.equals(a.getHasErrors()))
                            .collect(Collectors.toList());

                    if (errorArtifacts.isEmpty()) {
                        errorArtifacts = buildFallbackErrorArtifacts(job, artifacts, validationResult, logConsumer);
                    }

                    if (errorArtifacts.isEmpty()) {
                        logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.COACH,
                                "无法定位错误文件，停止修复（请查看 Maven 输出片段）"));
                        break;
                    }

                    // 5. 准备修复
                    fixAttempts++;
                    logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.COACH,
                            "开始修复 " + errorArtifacts.size() + " 个错误文件...（attempt " + fixAttempts + "/"
                                    + currentMaxRounds
                                    + ")"));
                    safeUpdatePhase(job.getId(), 8, false, G3PlanningFileEntity.UPDATER_COACH);

                    // 计算错误签名
                    String errorSignature = G3ErrorSignature.computeCombined(
                            validationResult.getStderr() + validationResult.getStdout(),
                            validationResult.getParsedErrors());

                    // 检测重复错误
                    if (sessionMemory.isSameErrorRepeated(errorSignature)) {
                        logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.COACH,
                                "检测到连续相同错误，提前终止修复循环以避免无效重试"));
                        break;
                    }

                    // M5: 动态轮次调整判定
                    // 如果错误签名发生变化（在进展中），且快到轮次上限，且未达动态上限，则延长
                    if (!sessionMemory.isSameErrorRepeated(errorSignature)
                            && round + 1 >= currentMaxRounds
                            && currentMaxRounds < baseMaxRounds + MAX_DYNAMIC_EXTENSION) {
                        currentMaxRounds++;
                        logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.COACH,
                                "检测到修复进展（错误签名变化），动态延长最大轮次至 " + currentMaxRounds));
                    }

                    // 再次检查轮次（虽然前面检查过，但可能未触发延长）
                    if (round + 1 >= currentMaxRounds) {
                        break;
                    }

                    sessionMemory.recordErrorSignature(errorSignature);

                    // 6. 执行修复
                    ICoachAgent.CoachResult coachResult = coachAgent.fix(
                            job,
                            errorArtifacts,
                            List.of(validationResult),
                            sessionMemory,
                            logConsumer);

                    List<String> repairedFileNames = errorArtifacts.stream()
                            .map(G3ArtifactEntity::getFileName)
                            .toList();
                    sessionMemory.addRepairAttempt(
                            round,
                            repairedFileNames,
                            coachResult.success(),
                            errorSignature,
                            coachResult.errorMessage());

                    memoryPersistenceService.saveMemory(sessionMemory);

                    if (!coachResult.success() || coachResult.fixedArtifacts().isEmpty()) {
                        logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.COACH,
                                "本轮修复未成功: " + (coachResult.errorMessage() != null ? coachResult.errorMessage()
                                        : "无法生成修复代码")));

                        if (sessionMemory.shouldTerminate()) {
                            logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.COACH,
                                    "Session Memory 建议提前终止：多次修复失败或连续相同错误"));
                            break;
                        }

                        round++;
                        job.setCurrentRound(round);
                        jobMapper.updateById(job);
                        continue;
                    }

                    // 7. 更新产物
                    for (G3ArtifactEntity fixed : coachResult.fixedArtifacts()) {
                        artifactMapper.insert(fixed);
                    }
                    // M6: 更新向量索引
                    knowledgeStore.ingest(job, coachResult.fixedArtifacts());

                    // 替换错误产物
                    artifacts = mergeArtifacts(artifacts, coachResult.fixedArtifacts());

                    round++;
                    job.setCurrentRound(round);
                    jobMapper.updateById(job);

                    logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.COACH,
                            "修复完成，准备第 " + (round + 1) + " 轮全流程验证"));
                }
            }

            // ========== Phase 4: 完成 ==========
            if (validated) {
                completeJob(job, logConsumer);
            } else {
                if (hasEnvironmentError && fixAttempts == 0) {
                    failJob(job, "编译验证未通过（验证 " + validationAttempts + " 轮，检测到环境类错误，未触发修复）", logConsumer);
                } else {
                    failJob(job, "编译验证未通过（验证 " + validationAttempts + " 轮，修复 " + fixAttempts + " 次）", logConsumer);
                }
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
            Consumer<G3LogEntry> logConsumer) {
        List<G3ArtifactEntity> result = new ArrayList<>();

        String buildSummary = extractBuildFailureSummary(
                validationResult.getStdout(),
                validationResult.getStderr());

        // 1) parsedErrors → 关联产物
        if (validationResult.getParsedErrors() != null && !validationResult.getParsedErrors().isEmpty()) {
            for (G3ValidationResultEntity.ParsedError err : validationResult.getParsedErrors()) {
                if (err == null || err.getFile() == null)
                    continue;
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
                String pomContent = sandboxService.generatePomXml("com.ingenio.generated", "g3-generated-app",
                        "1.0.0-SNAPSHOT");
                pom = G3ArtifactEntity.create(
                        job.getId(),
                        "pom.xml",
                        pomContent,
                        G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                        job.getCurrentRound());
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
        logConsumer.accept(
                G3LogEntry.warn(G3LogEntry.Role.COACH, "构建失败摘要: " + truncate(buildSummary.replace("\n", " | "), 220)));

        return result;
    }

    /**
     * 提取 Maven 构建失败摘要（控制长度，避免日志过大）
     */
    private String extractBuildFailureSummary(String stdout, String stderr) {
        String output = (stdout == null ? "" : stdout) + "\n" + (stderr == null ? "" : stderr);
        output = output.replace("\r", "");
        if (output.isBlank())
            return "构建失败（输出为空）";

        String[] lines = output.split("\n");
        StringBuilder sb = new StringBuilder();
        int limit = 80;
        int count = 0;
        for (String raw : lines) {
            if (raw == null)
                continue;
            String line = raw.trim();
            if (line.isEmpty())
                continue;
            if (line.startsWith("[ERROR]") || line.contains("BUILD FAILURE")
                    || line.contains("Failed to execute goal")) {
                sb.append(line).append("\n");
                count++;
                if (count >= limit)
                    break;
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
     * 检测验证结果是否包含环境类错误
     *
     * 环境类错误特征：
     * - Maven依赖下载失败：Could not resolve dependencies / Could not transfer artifact
     * - 网络超时：Connection timed out / Read timed out
     * - 仓库不可用：Failed to read artifact descriptor
     *
     * 注意：Coach Agent无法修复环境类错误，应该跳过Coach直接重试验证
     *
     * @param validationResult 验证结果
     * @return true表示是环境错误
     */
    private boolean isEnvironmentError(G3ValidationResultEntity validationResult) {
        if (validationResult == null) {
            return false;
        }

        // 检查parsedErrors中是否有"environment"类型的错误
        if (validationResult.getParsedErrors() != null) {
            for (G3ValidationResultEntity.ParsedError error : validationResult.getParsedErrors()) {
                if (error != null && "environment".equals(error.getSeverity())) {
                    return true;
                }
            }
        }

        // 检查stdout/stderr中的环境错误关键词
        String output = (validationResult.getStdout() == null ? "" : validationResult.getStdout())
                + (validationResult.getStderr() == null ? "" : validationResult.getStderr());

        if (output.isBlank()) {
            return false;
        }

        String normalized = output.toLowerCase();

        // 依赖下载失败
        if (normalized.contains("could not resolve dependencies") ||
                normalized.contains("could not transfer artifact") ||
                normalized.contains("failed to read artifact descriptor") ||
                normalized.contains("cannot access central") ||
                normalized.contains("could not find artifact")) {
            return true;
        }

        // 网络超时
        if (normalized.contains("connection timed out") ||
                normalized.contains("read timed out") ||
                normalized.contains("connect timed out") ||
                normalized.contains("sockettimeoutexception")) {
            return true;
        }

        // 沙箱问题
        if (normalized.contains("sandbox") && normalized.contains("timeout")) {
            return true;
        }

        return false;
    }

    /**
     * 确保构建脚手架文件存在并纳入产物列表
     *
     * 说明：
     * - 之前 pom.xml/UUIDv8TypeHandler 由 SandboxService 在校验阶段临时生成，仅用于沙箱编译，
     * 但不会入库，导致前端无法展示“生成结果”，也会造成编译失败时无法定位错误文件。
     * - 这里将其提前固化为产物，纳入 G3 的可追溯与可修复闭环。
     */
    private List<G3ArtifactEntity> ensureBuildScaffoldArtifacts(
            G3JobEntity job,
            List<G3ArtifactEntity> artifacts,
            Consumer<G3LogEntry> logConsumer) {
        List<G3ArtifactEntity> enriched = new ArrayList<>(artifacts);

        boolean hasPom = enriched.stream().anyMatch(a -> "pom.xml".equals(a.getFileName()));
        if (!hasPom) {
            String pomContent = sandboxService.generatePomXml("com.ingenio.generated", "g3-generated-app",
                    "1.0.0-SNAPSHOT");
            G3ArtifactEntity pomArtifact = G3ArtifactEntity.create(
                    job.getId(),
                    "pom.xml",
                    pomContent,
                    G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                    job.getCurrentRound());
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
                    job.getCurrentRound());
            artifactMapper.insert(typeHandlerArtifact);
            enriched.add(typeHandlerArtifact);
            logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR, "UUIDv8TypeHandler已纳入产物列表"));
        }

        // 兜底：若生成代码引用了 BeanConverter，则补齐最小实现，避免“引用不存在工具类”导致一次性编译失败
        boolean usesBeanConverter = enriched.stream().anyMatch(a -> {
            String c = a.getContent();
            if (c == null || c.isBlank())
                return false;
            return c.contains("BeanConverter") || c.contains("beanConverter");
        });
        boolean hasBeanConverter = enriched.stream().anyMatch(a -> "BeanConverter.java".equals(a.getFileName()));
        if (usesBeanConverter && !hasBeanConverter) {
            String beanConverterContent = sandboxService.generateBeanConverterContent();
            G3ArtifactEntity beanConverterArtifact = G3ArtifactEntity.create(
                    job.getId(),
                    "src/main/java/com/ingenio/backend/util/BeanConverter.java",
                    beanConverterContent,
                    G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                    job.getCurrentRound());
            artifactMapper.insert(beanConverterArtifact);
            enriched.add(beanConverterArtifact);
            logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR, "BeanConverter已纳入产物列表（兜底）"));
        }

        return enriched;
    }

    /**
     * Blueprint 合规性验证（F4）
     *
     * 说明：
     * - 当前 Blueprint JSON 主要约束 schema/features，因此最小版校验聚焦：
     * 1) DDL 是否覆盖 schema（Architect 输出）
     * 2) Entity 是否覆盖 schema（Coder 输出）
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

        BlueprintComplianceResult ddlCompliance = blueprintValidator.validateSchemaCompliance(job.getDbSchemaSql(),
                job.getBlueprintSpec());

        BlueprintComplianceResult artifactCompliance = blueprintValidator.validateBackendArtifactsCompliance(artifacts,
                job.getBlueprintSpec());

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
        if (artifact == null)
            return null;
        if (!jobId.equals(artifact.getJobId()))
            return null;
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
     * 4. 使用 Redis Pub/Sub 支持分布式节点订阅
     */
    public Flux<G3LogEntry> subscribeToLogs(UUID jobId) {
        // 创建心跳 Flux（每 15 秒发送一次）
        Flux<G3LogEntry> heartbeatFlux = Flux.interval(Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS))
                .map(tick -> G3LogEntry.heartbeat())
                .takeUntil(entry -> isJobCompleted(jobId));

        // 历史日志：直接从数据库读取并输出到当前订阅者
        Flux<G3LogEntry> historyFlux = Flux.defer(() -> {
            G3JobEntity job = jobMapper.selectById(jobId);
            if (job == null || job.getLogs() == null || job.getLogs().isEmpty()) {
                return Flux.empty();
            }
            return Flux.fromIterable(job.getLogs());
        });

        // 实时日志：通过 Redis Pub/Sub 订阅（支持分布式）
        Flux<G3LogEntry> liveFlux = g3LogStreamService.subscribeLog(jobId);

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
     * 发送日志到SSE流（通过 Redis Pub/Sub 分布式广播）
     */
    private void emitLog(UUID jobId, G3LogEntry entry) {
        // 通过 Redis Pub/Sub 发布（支持分布式节点）
        g3LogStreamService.publishLog(jobId, entry);
        // WebSocket 广播（MVP：先推日志，后续升级为结构化事件）
        g3WebSocketBroadcaster.broadcast(jobId, "log", entry);
    }

    /**
     * 关闭日志流（清理 Redis 订阅资源）
     */
    private void closeLogStream(UUID jobId) {
        g3LogStreamService.cleanup(jobId);
    }

    /**
     * 更新任务状态
     */
    private void updateJobStatus(G3JobEntity job, G3JobEntity.Status status) {
        job.setStatus(status.getValue());
        job.setUpdatedAt(Instant.now());
        jobMapper.updateById(job);

        // 同步生成任务状态（保持TimeMachine链路一致）
        syncGenerationTaskStatus(job, status);

        // 规划文件：同步“当前状态”到 task_plan.md（失败不影响主流程）
        safeUpdateTaskPlanStatus(job.getId(), status);
    }

    /**
     * 标记任务完成
     */
    private void completeJob(G3JobEntity job, Consumer<G3LogEntry> logConsumer) {
        job.setStatus(G3JobEntity.Status.COMPLETED.getValue());
        job.setCompletedAt(Instant.now());
        jobMapper.updateById(job);

        // 同步生成任务完成状态
        syncGenerationTaskStatus(job, G3JobEntity.Status.COMPLETED);

        // 归档产物并写入时光机快照（失败不阻断主流程）
        archiveAndSnapshot(job, logConsumer);

        // 规划文件：阶段8完成（若未触发修复则保持未勾选也无妨）、整体完成
        safeUpdateTaskPlanFinal(job.getId(), true, null);

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

        // 同步生成任务失败状态
        syncGenerationTaskFailure(job, error);

        // 规划文件：记录错误（避免触发数据库约束，默认按 system 写入）
        safeUpdateTaskPlanFinal(job.getId(), false, error);

        logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.PLAYER, "❌ G3任务失败: " + error));
    }

    /**
     * 确保generation_tasks存在
     *
     * 说明：
     * - G3任务需要写入TimeMachine版本链；
     * - generation_versions依赖generation_tasks外键，因此必须保证记录存在。
     */
    private void ensureGenerationTask(G3JobEntity job) {
        if (job == null || job.getId() == null) {
            return;
        }

        GenerationTaskEntity existing = generationTaskMapper.selectById(job.getId());
        if (existing != null) {
            if (job.getAppSpecId() != null && existing.getAppSpecId() == null) {
                existing.setAppSpecId(job.getAppSpecId());
                generationTaskMapper.updateById(existing);
            }
            return;
        }

        GenerationTaskEntity task = new GenerationTaskEntity();
        task.setId(job.getId());
        task.setTenantId(job.getTenantId());
        // 匿名任务 userId 为空时使用 tenantId 或默认占位，避免落库失败
        UUID resolvedUserId = job.getUserId() != null
                ? job.getUserId()
                : (job.getTenantId() != null ? job.getTenantId() : DEFAULT_ANONYMOUS_USER_ID);
        task.setUserId(resolvedUserId);

        String requirement = job.getRequirement() != null ? job.getRequirement().trim() : "";
        String taskName = requirement.isEmpty()
                ? "G3代码生成任务"
                : (requirement.length() > 24 ? requirement.substring(0, 24) + "..." : requirement);
        task.setTaskName(taskName);
        task.setUserRequirement(requirement.isEmpty() ? "G3任务需求" : requirement);
        task.setStatus(GenerationTaskEntity.Status.PENDING.getValue());
        task.setProgress(0);
        task.setAppSpecId(job.getAppSpecId());
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "g3");
        metadata.put("job_id", job.getId().toString());
        task.setMetadata(metadata);

        generationTaskMapper.insert(task);
    }

    /**
     * 同步generation_tasks状态
     *
     * @param job    G3任务
     * @param status G3状态
     */
    private void syncGenerationTaskStatus(G3JobEntity job, G3JobEntity.Status status) {
        if (job == null || job.getId() == null) {
            return;
        }

        GenerationTaskEntity task = generationTaskMapper.selectById(job.getId());
        if (task == null) {
            ensureGenerationTask(job);
            task = generationTaskMapper.selectById(job.getId());
        }
        if (task == null) {
            return;
        }

        GenerationTaskEntity.Status mappedStatus = mapGenerationTaskStatus(status);
        GenerationTaskEntity.AgentType agentType = mapGenerationTaskAgent(status);
        int progress = mapGenerationTaskProgress(status);

        task.updateStatus(mappedStatus, agentType, progress);
        task.setUpdatedAt(Instant.now());

        if (job.getAppSpecId() != null && task.getAppSpecId() == null) {
            task.setAppSpecId(job.getAppSpecId());
        }

        generationTaskMapper.updateById(task);
    }

    /**
     * 同步generation_tasks失败信息
     *
     * @param job          G3任务
     * @param errorMessage 错误信息
     */
    private void syncGenerationTaskFailure(G3JobEntity job, String errorMessage) {
        if (job == null || job.getId() == null) {
            return;
        }

        GenerationTaskEntity task = generationTaskMapper.selectById(job.getId());
        if (task == null) {
            ensureGenerationTask(job);
            task = generationTaskMapper.selectById(job.getId());
        }
        if (task == null) {
            return;
        }

        task.setErrorMessage(errorMessage);
        task.updateStatus(GenerationTaskEntity.Status.FAILED, null, task.getProgress());
        task.setUpdatedAt(Instant.now());
        generationTaskMapper.updateById(task);
    }

    /**
     * 映射G3状态为generation_tasks状态
     *
     * @param status G3状态
     * @return generation_tasks状态
     */
    private GenerationTaskEntity.Status mapGenerationTaskStatus(G3JobEntity.Status status) {
        if (status == null) {
            return GenerationTaskEntity.Status.PENDING;
        }
        return switch (status) {
            case QUEUED -> GenerationTaskEntity.Status.PENDING;
            case PLANNING -> GenerationTaskEntity.Status.PLANNING;
            case CODING -> GenerationTaskEntity.Status.GENERATING;
            case TESTING -> GenerationTaskEntity.Status.VALIDATING;
            case COMPLETED -> GenerationTaskEntity.Status.COMPLETED;
            case FAILED -> GenerationTaskEntity.Status.FAILED;
        };
    }

    /**
     * 映射G3状态为当前Agent
     *
     * @param status G3状态
     * @return generation_tasks当前Agent
     */
    private GenerationTaskEntity.AgentType mapGenerationTaskAgent(G3JobEntity.Status status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PLANNING -> GenerationTaskEntity.AgentType.PLAN;
            case CODING -> GenerationTaskEntity.AgentType.GENERATE;
            case TESTING -> GenerationTaskEntity.AgentType.VALIDATE;
            default -> null;
        };
    }

    /**
     * 映射G3状态为任务进度
     *
     * @param status G3状态
     * @return 进度值（0-100）
     */
    private int mapGenerationTaskProgress(G3JobEntity.Status status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case QUEUED -> 0;
            case PLANNING -> 10;
            case CODING -> 60;
            case TESTING -> 80;
            case COMPLETED, FAILED -> 100;
        };
    }

    /**
     * 归档产物并写入时光机快照
     *
     * @param job         G3任务
     * @param logConsumer 日志回调
     */
    private void archiveAndSnapshot(G3JobEntity job, Consumer<G3LogEntry> logConsumer) {
        if (job == null || job.getId() == null || job.getTenantId() == null) {
            logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR, "归档跳过：缺少任务或租户信息"));
            return;
        }

        List<G3ArtifactEntity> artifacts = artifactMapper.selectLatestByJobId(job.getId());
        if (artifacts == null || artifacts.isEmpty()) {
            logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR, "归档跳过：产物为空"));
            return;
        }

        long totalSizeBytes = 0L;
        for (G3ArtifactEntity artifact : artifacts) {
            String content = artifact.getContent() == null ? "" : artifact.getContent();
            totalSizeBytes += content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("file_count", artifacts.size());
        snapshot.put("total_size_bytes", totalSizeBytes);
        snapshot.put("archive", new HashMap<>(Map.of(
                "status", "uploading",
                "started_at", Instant.now().toString())));

        GenerationVersionEntity version = snapshotService.createSnapshot(
                job.getId(),
                job.getTenantId(),
                VersionType.CODE,
                snapshot);

        G3CodeArchiveService.ArchiveBuildResult archiveResult = codeArchiveService.buildAndUploadArchive(
                job.getTenantId(),
                job.getUserId(),
                job.getId(),
                version.getVersionNumber(),
                artifacts);

        Map<String, Object> updatedSnapshot = version.getSnapshot() != null
                ? new HashMap<>(version.getSnapshot())
                : new HashMap<>();
        updatedSnapshot.put("archive", archiveResult.toArchiveMetadata());
        updatedSnapshot.put("code_manifest", archiveResult.getManifest().toSnapshotMap());
        updatedSnapshot.put("file_count", archiveResult.getManifest().getFileCount());
        updatedSnapshot.put("total_size_bytes", archiveResult.getManifest().getTotalSizeBytes());

        version.setSnapshot(updatedSnapshot);
        generationVersionMapper.updateById(version);

        updateAppSpecArchiveMetadata(job, version, archiveResult);

        if (archiveResult.isSuccess()) {
            logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR, "G3产物归档成功"));
        } else {
            logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                    "G3产物归档失败: " + archiveResult.getErrorMessage()));
        }
    }

    /**
     * 更新AppSpec的最新归档元数据
     *
     * @param job           G3任务
     * @param version       版本快照
     * @param archiveResult 归档结果
     */
    private void updateAppSpecArchiveMetadata(G3JobEntity job, GenerationVersionEntity version,
            G3CodeArchiveService.ArchiveBuildResult archiveResult) {
        if (job.getAppSpecId() == null) {
            return;
        }

        AppSpecEntity appSpec = appSpecMapper.selectById(job.getAppSpecId());
        if (appSpec == null) {
            return;
        }

        Map<String, Object> metadata = appSpec.getMetadata() != null
                ? new HashMap<>(appSpec.getMetadata())
                : new HashMap<>();

        metadata.put("latestGenerationTaskId", job.getId().toString());
        metadata.put("latestGenerationVersionId", version.getId().toString());
        metadata.put("latestVersionNumber", version.getVersionNumber());
        metadata.put("latestArchiveStatus", archiveResult.isSuccess() ? "success" : "failed");

        if (archiveResult.isSuccess() && archiveResult.getStorageKey() != null) {
            metadata.put("latestCodeStorageKey", archiveResult.getStorageKey());
        } else if (archiveResult.getErrorMessage() != null) {
            metadata.put("latestArchiveError", archiveResult.getErrorMessage());
        }

        appSpec.setMetadata(metadata);
        appSpecMapper.updateById(appSpec);
    }

    // ==================== 规划文件集成（best-effort） ====================

    /**
     * 初始化三文件规划文件（task_plan/notes/context）。
     *
     * 说明：
     * - 规划文件用于“文件系统作为外部记忆”的工作流落地，也是前端控制台展示的基础。
     * - 初始化失败不应阻断主流程（例如数据库尚未迁移时），因此采用 best-effort。
     */
    private void safeInitializePlanningFiles(G3JobEntity job) {
        try {
            String projectName = inferProjectName(job);
            String basePackage = inferBasePackage(job);
            List<String> capabilities = inferCapabilities(job);
            planningFileService.initializePlanningFiles(
                    job.getId(),
                    projectName,
                    job.getRequirement(),
                    capabilities,
                    basePackage);
        } catch (Exception e) {
            log.warn("[G3] 初始化规划文件失败（忽略，不影响主流程）: jobId={}, err={}", job.getId(), e.getMessage());
        }
    }

    /**
     * 同步 G3 状态机到 task_plan.md 的“当前状态”。
     */
    private void safeUpdateTaskPlanStatus(UUID jobId, G3JobEntity.Status status) {
        try {
            PlanningStatus mapped = mapPlanningStatus(status);
            planningFileService.updateStatus(
                    jobId,
                    mapped.phaseLabel(),
                    mapped.progress(),
                    mapped.statusText(),
                    G3PlanningFileEntity.UPDATER_SYSTEM);

            // 进入关键阶段时，顺手勾选/取消对应阶段（让前端更直观）
            if (status == G3JobEntity.Status.PLANNING) {
                safeUpdatePhase(jobId, 1, false, G3PlanningFileEntity.UPDATER_SYSTEM);
            } else if (status == G3JobEntity.Status.CODING) {
                safeUpdatePhase(jobId, 2, false, G3PlanningFileEntity.UPDATER_CODER);
            } else if (status == G3JobEntity.Status.TESTING) {
                safeUpdatePhase(jobId, 7, false, G3PlanningFileEntity.UPDATER_SYSTEM);
            }
        } catch (Exception e) {
            log.warn("[G3] 同步 task_plan 状态失败（忽略）: jobId={}, err={}", jobId, e.getMessage());
        }
    }

    /**
     * 任务结束时写入最终状态，并尽可能追加错误记录。
     */
    private void safeUpdateTaskPlanFinal(UUID jobId, boolean completed, String error) {
        try {
            planningFileService.updateStatus(
                    jobId,
                    completed ? "完成" : "失败",
                    completed ? 100 : 0,
                    completed ? "已完成" : "失败",
                    G3PlanningFileEntity.UPDATER_SYSTEM);
            if (!completed && error != null && !error.isBlank()) {
                planningFileService.appendError(
                        jobId,
                        error,
                        "查看控制台日志与产物列表定位问题",
                        G3PlanningFileEntity.UPDATER_SYSTEM);
            }
        } catch (Exception e) {
            log.warn("[G3] 写入最终 task_plan 状态失败（忽略）: jobId={}, err={}", jobId, e.getMessage());
        }
    }

    /**
     * 更新阶段勾选状态（best-effort）。
     */
    private void safeUpdatePhase(UUID jobId, int phase, boolean completed, String updatedBy) {
        try {
            planningFileService.updatePhaseStatus(jobId, phase, completed, updatedBy);
        } catch (Exception e) {
            log.warn("[G3] 更新 task_plan 阶段状态失败（忽略）: jobId={}, phase={}, err={}", jobId, phase, e.getMessage());
        }
    }

    /**
     * 分析依赖并更新详细的任务计划
     */
    private void safeUpdateDetailedTaskPlan(G3JobEntity job) {
        try {
            if (job.getDbSchemaSql() == null)
                return;

            G3TaskDependencyGraph graph = dependencyAnalyzer.analyzeFromSchema(job.getDbSchemaSql());
            if (graph.getNodes().isEmpty())
                return;

            StringBuilder sb = new StringBuilder();
            sb.append("\n\n## 详细任务分解 (自动生成)\n");

            // 按优先级排序
            List<G3TaskDependencyGraph.G3TaskNode> sorted = dependencyAnalyzer.topologicalSort(graph);

            String currentType = "";
            for (G3TaskDependencyGraph.G3TaskNode node : sorted) {
                if (!node.getType().equals(currentType)) {
                    currentType = node.getType();
                    sb.append("\n### ").append(currentType.toUpperCase()).append(" 生成任务\n");
                }
                sb.append("- [ ] ").append(node.getName())
                        .append(" (").append(node.getRelatedEntity()).append(")\n");
            }

            planningFileService.appendContent(
                    job.getId(),
                    G3PlanningFileEntity.FILE_TYPE_TASK_PLAN,
                    sb.toString(),
                    G3PlanningFileEntity.UPDATER_SYSTEM);

        } catch (Exception e) {
            log.warn("[G3] 更新详细任务计划失败: {}", e.getMessage());
        }
    }

    /**
     * 根据产物粗粒度推断阶段完成情况，并写入 context.md 已生成文件清单。
     *
     * 规则（可扩展）：
     * - 含 Entity.java / /entity/ → 阶段2
     * - 含 Mapper.java / /mapper/ → 阶段3
     * - 含 Service.java / /service/ → 阶段4
     * - 含 Controller.java / /controller/ → 阶段5
     */
    private void safeUpdatePlanningFromArtifacts(UUID jobId, List<G3ArtifactEntity> artifacts) {
        try {
            boolean hasEntity = artifacts.stream().anyMatch(a -> looksLikeJavaType(a, "entity", "Entity"));
            boolean hasMapper = artifacts.stream().anyMatch(a -> looksLikeJavaType(a, "mapper", "Mapper"));
            boolean hasService = artifacts.stream().anyMatch(a -> looksLikeJavaType(a, "service", "Service"));
            boolean hasController = artifacts.stream().anyMatch(a -> looksLikeJavaType(a, "controller", "Controller"));

            safeUpdatePhase(jobId, 2, hasEntity, G3PlanningFileEntity.UPDATER_CODER);
            safeUpdatePhase(jobId, 3, hasMapper, G3PlanningFileEntity.UPDATER_CODER);
            safeUpdatePhase(jobId, 4, hasService, G3PlanningFileEntity.UPDATER_CODER);
            safeUpdatePhase(jobId, 5, hasController, G3PlanningFileEntity.UPDATER_CODER);

            // 尽量填充 context.md 的“已生成文件”表格，便于前端查看与后续 Agent 复用
            // 限制写入量，避免大项目导致频繁更新带来额外开销
            int limit = 80;
            int count = 0;
            for (G3ArtifactEntity artifact : artifacts) {
                if (count >= limit)
                    break;
                if (artifact == null || artifact.getFilePath() == null)
                    continue;
                String filePath = artifact.getFilePath();
                if (!filePath.endsWith(".java"))
                    continue;

                InferredJavaType inferred = inferJavaType(filePath);
                if (inferred == null)
                    continue;

                String className = filePath.substring(filePath.lastIndexOf('/') + 1).replace(".java", "");

                // 1. 更新文件清单
                planningFileService.addGeneratedFile(
                        jobId,
                        filePath,
                        className,
                        inferred.type(),
                        "已生成",
                        G3PlanningFileEntity.UPDATER_CODER);

                // 2. 提取并更新 Import 索引 (简单正则)
                extractAndSaveImports(jobId, artifact, inferred.type());

                // 3. 提取并更新类签名 (简单正则)
                extractAndSaveSignature(jobId, artifact, inferred.type(), className);

                count++;
            }
        } catch (Exception e) {
            log.warn("[G3] 基于产物更新规划文件失败（忽略）: jobId={}, err={}", jobId, e.getMessage());
        }
    }

    private void extractAndSaveImports(UUID jobId, G3ArtifactEntity artifact, String type) {
        try {
            String content = artifact.getContent();
            if (content == null)
                return;

            List<String> imports = new ArrayList<>();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("import\\s+([\\w.]+);").matcher(content);
            while (m.find()) {
                imports.add(m.group(1));
            }
            if (!imports.isEmpty()) {
                planningFileService.updateImportIndex(jobId, type, imports, G3PlanningFileEntity.UPDATER_CODER);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void extractAndSaveSignature(UUID jobId, G3ArtifactEntity artifact, String type, String className) {
        try {
            String content = artifact.getContent();
            if (content == null)
                return;

            // 提取类签名：public class Xxx ... {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("public\\s+(?:class|interface|enum)\\s+" + className + "[^{]+").matcher(content);
            if (m.find()) {
                String signature = m.group().trim();
                planningFileService.addClassSignature(jobId, type, className, signature,
                        G3PlanningFileEntity.UPDATER_CODER);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private record PlanningStatus(String phaseLabel, int progress, String statusText) {
    }

    /**
     * 将 G3 状态机映射到 task_plan.md 的展示语义。
     */
    private PlanningStatus mapPlanningStatus(G3JobEntity.Status status) {
        return switch (status) {
            case QUEUED -> new PlanningStatus("排队中", 0, "等待中");
            case PLANNING -> new PlanningStatus("阶段1 - 架构设计", 5, "进行中");
            case CODING -> new PlanningStatus("阶段2 - 代码生成", 55, "进行中");
            case TESTING -> new PlanningStatus("阶段7 - 编译验证", 85, "进行中");
            case COMPLETED -> new PlanningStatus("完成", 100, "已完成");
            case FAILED -> new PlanningStatus("失败", 0, "失败");
            default -> new PlanningStatus("未知", 0, "未知");
        };
    }

    private static boolean looksLikeJavaType(G3ArtifactEntity artifact, String folderHint, String suffixHint) {
        if (artifact == null)
            return false;
        String p = artifact.getFilePath();
        if (p == null)
            return false;
        String lower = p.toLowerCase();
        return lower.contains("/" + folderHint.toLowerCase() + "/") || p.endsWith(suffixHint + ".java");
    }

    private record InferredJavaType(String type) {
    }

    private static InferredJavaType inferJavaType(String filePath) {
        String lower = filePath.toLowerCase();
        if (lower.contains("/entity/") || filePath.endsWith("Entity.java"))
            return new InferredJavaType("entity");
        if (lower.contains("/mapper/") || filePath.endsWith("Mapper.java"))
            return new InferredJavaType("mapper");
        if (lower.contains("/service/") || filePath.endsWith("Service.java"))
            return new InferredJavaType("service");
        if (lower.contains("/controller/") || filePath.endsWith("Controller.java"))
            return new InferredJavaType("controller");
        return null;
    }

    /**
     * 推断项目名称：优先 blueprintSpec.projectName/appName/name；其次使用任务ID短码。
     */
    private static String inferProjectName(G3JobEntity job) {
        if (job == null)
            return "G3任务";
        Map<String, Object> spec = job.getBlueprintSpec();
        if (spec != null && !spec.isEmpty()) {
            for (String key : List.of("projectName", "appName", "name", "title")) {
                Object v = spec.get(key);
                if (v != null && !v.toString().isBlank()) {
                    return v.toString().trim();
                }
            }
        }
        UUID id = job.getId();
        String suffix = id != null ? id.toString().substring(0, 8) : "unknown";
        return "G3任务-" + suffix;
    }

    /**
     * 推断基础包名：优先 blueprintSpec.basePackage；否则使用默认值。
     */
    private static String inferBasePackage(G3JobEntity job) {
        if (job == null)
            return "com.ingenio.generated";
        Map<String, Object> spec = job.getBlueprintSpec();
        if (spec != null) {
            Object v = spec.get("basePackage");
            if (v != null && !v.toString().isBlank()) {
                return v.toString().trim();
            }
        }
        return "com.ingenio.generated";
    }

    /**
     * 推断能力清单：优先 blueprintSpec.capabilities（字符串数组）；否则为空列表。
     */
    private static List<String> inferCapabilities(G3JobEntity job) {
        Map<String, Object> spec = job != null ? job.getBlueprintSpec() : null;
        if (spec == null)
            return List.of();

        Object raw = spec.get("capabilities");
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();
        }
        if (raw instanceof String s && !s.isBlank()) {
            return List.of(s.trim());
        }
        return List.of();
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
                    0);
            artifactMapper.insert(contractArtifact);
        }

        // 保存DB Schema
        if (result.dbSchemaSql() != null) {
            G3ArtifactEntity schemaArtifact = G3ArtifactEntity.create(
                    job.getId(),
                    "db/schema.sql",
                    result.dbSchemaSql(),
                    G3ArtifactEntity.GeneratedBy.ARCHITECT,
                    0);
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
        if (text == null)
            return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength) + "...";
    }
}
