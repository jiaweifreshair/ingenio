package com.ingenio.backend.service.g3;

import com.ingenio.backend.agent.g3.IArchitectAgent;
import com.ingenio.backend.agent.g3.ICoachAgent;
import com.ingenio.backend.agent.g3.ICoderAgent;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.entity.g3.G3SessionMemory;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import com.ingenio.backend.entity.GenerationVersionEntity;
import com.ingenio.backend.entity.ProjectCapabilityConfigEntity;
import com.ingenio.backend.entity.ProjectEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import com.ingenio.backend.mapper.GenerationTaskMapper;
import com.ingenio.backend.mapper.GenerationVersionMapper;
import com.ingenio.backend.mapper.IndustryTemplateMapper;
import com.ingenio.backend.mapper.g3.G3ArtifactMapper;
import com.ingenio.backend.mapper.g3.G3JobMapper;
import com.ingenio.backend.mapper.g3.G3ValidationResultMapper;
import com.ingenio.backend.service.NLRequirementAnalyzer;
import com.ingenio.backend.service.ProjectCapabilityConfigService;
import com.ingenio.backend.service.ProjectService;
import com.ingenio.backend.service.VersionSnapshotService;
import com.ingenio.backend.service.blueprint.BlueprintValidator;
import com.ingenio.backend.websocket.G3WebSocketBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * G3OrchestratorService 单元测试
 * 测试编排服务的核心逻辑：
 * 1. 任务提交和状态管理
 * 2. Agent 协调执行
 * 3. 自修复循环控制
 * 4. 日志流管理
 */
@ExtendWith(MockitoExtension.class)
class G3OrchestratorServiceTest {
    /**
     * 默认租户ID（与后端兜底保持一致）
     */
    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private G3JobMapper jobMapper;

    @Mock
    private G3ArtifactMapper artifactMapper;

    @Mock
    private G3ValidationResultMapper validationResultMapper;

    /**
     * 新增依赖Mock：用于覆盖G3归档/快照/上下文同步，避免单测NPE
     */
    @Mock
    private AppSpecMapper appSpecMapper;

    @Mock
    private IndustryTemplateMapper industryTemplateMapper;

    @Mock
    private GenerationTaskMapper generationTaskMapper;

    @Mock
    private GenerationVersionMapper generationVersionMapper;

    @Mock
    private VersionSnapshotService snapshotService;

    @Mock
    private G3CodeArchiveService codeArchiveService;

    /**
     * BlueprintValidator Mock：用于跳过真实合规校验流程，避免单测受规则影响。
     */
    @Mock
    private BlueprintValidator blueprintValidator;

    /**
     * 依赖分析器 Mock：用于避免真实 SQL 解析影响单测。
     */
    @Mock
    private G3DependencyAnalyzer dependencyAnalyzer;

    /**
     * 阶段验证器 Mock：用于复用沙箱结果构造统一验证响应。
     */
    @Mock
    private G3PhaseValidator phaseValidator;

    /**
     * G3 知识库 Mock：避免单测触发向量索引流程。
     */
    @Mock
    private G3KnowledgeStore knowledgeStore;

    /**
     * 仓库索引 Mock：避免单测触发索引构建。
     */
    @Mock
    private G3RepoIndexService repoIndexService;

    /**
     * 规划文件服务 Mock：用于拦截任务计划写入。
     */
    @Mock
    private G3PlanningFileService planningFileService;

    /**
     * WebSocket广播 Mock：避免单测触发真实推送。
     */
    @Mock
    private G3WebSocketBroadcaster g3WebSocketBroadcaster;

    /**
     * SessionMemory 持久化 Mock：避免单测依赖 Redis。
     */
    @Mock
    private G3MemoryPersistenceService memoryPersistenceService;

    /**
     * 日志流服务 Mock：用于避免测试触发真实 SSE 广播。
     */
    @Mock
    private G3LogStreamService g3LogStreamService;

    /**
     * 前端 API 生成器 Mock：避免测试触发前端协议生成逻辑。
     */
    @Mock
    private FrontendApiClientGenerator frontendApiClientGenerator;

    /**
     * 需求分析器 Mock：避免测试触发真实 NLP 解析。
     */
    @Mock
    private NLRequirementAnalyzer nlRequirementAnalyzer;

    /**
     * 项目服务 Mock：用于能力配置读取链路。
     */
    @Mock
    private ProjectService projectService;

    /**
     * 项目能力配置 Mock：用于注入能力清单。
     */
    @Mock
    private ProjectCapabilityConfigService projectCapabilityConfigService;

    /**
     * 失败诊断服务 Mock：用于生成行动项提示。
     */
    @Mock
    private G3FailureDiagnosisService failureDiagnosisService;

    @Mock
    private IArchitectAgent architectAgent;

    @Mock
    private List<ICoderAgent> coderAgents;

    @Mock
    private ICoachAgent coachAgent;

    @Mock
    private G3SandboxService sandboxService;

    @InjectMocks
    private G3OrchestratorService orchestratorService;

    private G3JobEntity testJob;
    private UUID testJobId;

    @BeforeEach
    void setUp() {
        testJobId = UUID.randomUUID();
        testJob = G3JobEntity.builder()
                .id(testJobId)
                .requirement("创建一个用户管理系统")
                .status(G3JobEntity.Status.QUEUED.getValue())
                .currentRound(0)
                .maxRounds(3)
                .logs(new ArrayList<>())
                .build();

        // 设置 maxRounds 配置
        ReflectionTestUtils.setField(orchestratorService, "maxRounds", 3);

        // 单元测试环境没有 Spring 代理注入 self，这里手动补齐，避免 submitJob 触发 NPE
        ReflectionTestUtils.setField(orchestratorService, "self", orchestratorService);

        // 日志订阅默认返回空流，避免 subscribeToLogs 测试出现空指针
        lenient().when(g3LogStreamService.subscribeLog(any())).thenReturn(Flux.empty());

        // 仅在 runJob 流程测试中才需要的 Mock 放到专用方法中，避免未使用的 stubbing 报错
    }

    /**
     * 测试：成功提交新任务
     * 期望：返回任务ID，任务状态为 QUEUED
     */
    @Test
    void submitJob_shouldCreateJobAndReturnId() {
        // GIVEN
        String requirement = "创建一个用户管理系统";

        // WHEN
        doAnswer(invocation -> {
            G3JobEntity job = invocation.getArgument(0);
            job.setId(testJobId);
            return 1;
        }).when(jobMapper).insert((G3JobEntity) any());

        UUID jobId = orchestratorService.submitJob(requirement, null, null);

        // THEN
        assertNotNull(jobId);
        verify(jobMapper).insert(argThat((G3JobEntity job) ->
                job.getRequirement().equals(requirement) &&
                job.getStatus().equals(G3JobEntity.Status.QUEUED.getValue()) &&
                job.getCurrentRound() == 0
        ));
    }

    /**
     * 需求中识别到鉴权/支付能力时应注入到蓝图与分析上下文。
     *
     * 是什么：模拟需求文本包含鉴权与支付关键词的场景。
     * 做什么：断言提交任务后蓝图与分析上下文均包含能力清单。
     * 为什么：保证能力注入进入 G3 上下文，便于生成鉴权/支付集成代码。
     */
    @Test
    void submitJob_injectsDetectedCapabilitiesIntoBlueprintAndAnalysisContext() {
        String requirement = "需要用户登录鉴权与支付功能";
        List<String> detectedCapabilities = List.of("auth", "payment_alipay");

        when(nlRequirementAnalyzer.detectProjectCapabilities(requirement)).thenReturn(detectedCapabilities);

        doAnswer(invocation -> {
            G3JobEntity job = invocation.getArgument(0);
            job.setId(testJobId);
            return 1;
        }).when(jobMapper).insert((G3JobEntity) any());

        UUID jobId = orchestratorService.submitJob(requirement, null, null);

        assertNotNull(jobId);
        verify(jobMapper).insert(argThat((G3JobEntity job) -> {
            List<String> blueprintCaps = readCapabilities(job.getBlueprintSpec());
            List<String> analysisCaps = readCapabilities(job.getAnalysisContextJson());
            return blueprintCaps.containsAll(detectedCapabilities) && analysisCaps.containsAll(detectedCapabilities);
        }));
    }

    /**
     * 测试：AI 能力识别后应写入 Blueprint 与分析上下文。
     *
     * 是什么：模拟需求包含“图像识别/语音识别”等 AI 能力。
     * 做什么：断言 aiCapabilities 写入 blueprintSpec 与 analysisContext。
     * 为什么：保证 AI 能力在生成流程中可追踪、可复用。
     */
    @Test
    void submitJob_injectsAiCapabilities_whenBlueprintMissing() {
        String requirement = "需要图像识别与语音识别能力";
        List<String> detected = List.of("vision", "speech");

        when(nlRequirementAnalyzer.detectAiCapabilities(requirement)).thenReturn(detected);

        doAnswer(invocation -> {
            G3JobEntity job = invocation.getArgument(0);
            job.setId(testJobId);
            return 1;
        }).when(jobMapper).insert((G3JobEntity) any());

        UUID jobId = orchestratorService.submitJob(requirement, null, null);

        assertNotNull(jobId);
        verify(jobMapper).insert(argThat((G3JobEntity job) -> {
            List<String> blueprintCaps = readStringList(job.getBlueprintSpec(), "aiCapabilities");
            List<String> analysisCaps = readStringList(job.getAnalysisContextJson(), "aiCapabilities");
            return blueprintCaps.containsAll(detected) && analysisCaps.containsAll(detected);
        }));
    }

    /**
     * 测试：缺失租户与用户时应写入默认值。
     *
     * 是什么：模拟匿名任务提交。
     * 做什么：断言 tenantId/userId 落库不为空。
     * 为什么：避免 generation_tasks 与快照链路因空租户失败。
     */
    @Test
    void submitJob_defaultsTenantAndUser_whenMissing() {
        String requirement = "创建一个匿名任务";

        doAnswer(invocation -> {
            G3JobEntity job = invocation.getArgument(0);
            job.setId(testJobId);
            return 1;
        }).when(jobMapper).insert((G3JobEntity) any());

        UUID jobId = orchestratorService.submitJob(requirement, null, null);

        assertNotNull(jobId);
        verify(jobMapper).insert(argThat((G3JobEntity job) ->
                DEFAULT_TENANT_ID.equals(job.getTenantId()) && DEFAULT_TENANT_ID.equals(job.getUserId())));
    }

    /**
     * 测试：JeecgBoot 能力配置应注入到 Blueprint 与分析上下文。
     *
     * 是什么：模拟项目已配置鉴权与支付能力。
     * 做什么：断言 capabilities 与 capabilityConfigKeys 被注入到 G3 任务上下文。
     * 为什么：保证生成链路能读取鉴权/支付配置并落盘集成代码。
     */
    @Test
    void submitJob_injectsJeecgCapabilities_fromProjectConfigs() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID appSpecId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        /**
         * AppSpec 测试数据。
         *
         * 是什么：模拟已存在的 AppSpec 记录。
         * 做什么：避免 resolveJobContext 报告 appSpecId 不存在。
         * 为什么：减少测试日志噪音并保证上下文加载路径覆盖。
         */
        AppSpecEntity appSpec = AppSpecEntity.builder()
                .id(appSpecId)
                .tenantId(tenantId)
                .createdByUserId(userId)
                .specContent(new java.util.HashMap<>())
                .metadata(new java.util.HashMap<>())
                .build();

        ProjectEntity project = ProjectEntity.builder()
                .id(projectId)
                .appSpecId(appSpecId)
                .tenantId(tenantId)
                .userId(userId)
                .build();

        ProjectCapabilityConfigEntity authConfig = ProjectCapabilityConfigEntity.builder()
                .projectId(projectId)
                .capabilityCode("auth")
                .configValues(java.util.Map.of(
                        "jwtSecret", "test-secret",
                        "tokenExpiry", "7d"
                ))
                .build();

        ProjectCapabilityConfigEntity paymentConfig = ProjectCapabilityConfigEntity.builder()
                .projectId(projectId)
                .capabilityCode("payment_alipay")
                .configValues(java.util.Map.of(
                        "appId", "test-app-id"
                ))
                .build();

        when(appSpecMapper.selectById(appSpecId)).thenReturn(appSpec);
        when(projectService.findByAppSpecId(appSpecId)).thenReturn(project);
        when(projectCapabilityConfigService.listByProject(projectId)).thenReturn(List.of(authConfig, paymentConfig));
        when(nlRequirementAnalyzer.detectProjectCapabilities(anyString())).thenReturn(List.of());

        /**
         * 异步执行替身。
         *
         * 是什么：用于替代 self.runJobAsync 的 Mock。
         * 做什么：避免提交任务时触发异步执行链路。
         * 为什么：防止单测产生无关错误日志与副作用。
         */
        G3OrchestratorService selfMock = mock(G3OrchestratorService.class);
        doNothing().when(selfMock).runJobAsync(any());
        ReflectionTestUtils.setField(orchestratorService, "self", selfMock);

        doAnswer(invocation -> {
            G3JobEntity job = invocation.getArgument(0);
            job.setId(testJobId);
            return 1;
        }).when(jobMapper).insert((G3JobEntity) any());

        UUID jobId = orchestratorService.submitJob("需要登录和支付宝支付能力", userId, tenantId, appSpecId, null, null);

        assertNotNull(jobId);
        verify(jobMapper).insert(argThat((G3JobEntity job) -> {
            List<String> blueprintCaps = readStringList(job.getBlueprintSpec(), "capabilities");
            List<String> analysisCaps = readStringList(job.getAnalysisContextJson(), "capabilities");

            java.util.Map<String, List<String>> blueprintKeys = readCapabilityConfigKeys(job.getBlueprintSpec());
            java.util.Map<String, List<String>> analysisKeys = readCapabilityConfigKeys(job.getAnalysisContextJson());

            boolean capsInjected = blueprintCaps.containsAll(List.of("auth", "payment_alipay"))
                    && analysisCaps.containsAll(List.of("auth", "payment_alipay"));
            boolean keysInjected = blueprintKeys.getOrDefault("auth", List.of()).containsAll(List.of("jwtSecret", "tokenExpiry"))
                    && blueprintKeys.getOrDefault("payment_alipay", List.of()).contains("appId")
                    && analysisKeys.getOrDefault("auth", List.of()).containsAll(List.of("jwtSecret", "tokenExpiry"))
                    && analysisKeys.getOrDefault("payment_alipay", List.of()).contains("appId");

            return capsInjected && keysInjected;
        }));
    }

    /**
     * 读取能力列表的测试辅助方法。
     *
     * 是什么：将 map 中的 capabilities 转成字符串列表。
     * 做什么：用于单测中断言能力注入结果。
     * 为什么：避免测试逻辑分散并提升可读性。
     */
    private List<String> readCapabilities(java.util.Map<String, Object> source) {
        return readStringList(source, "capabilities");
    }

    /**
     * 读取指定Key的字符串列表。
     *
     * 是什么：从Map读取List或String并转换为字符串列表。
     * 做什么：复用在 capabilities/aiCapabilities 等字段的断言。
     * 为什么：避免重复解析逻辑导致单测易错。
     */
    private List<String> readStringList(java.util.Map<String, Object> source, String key) {
        if (source == null || key == null) {
            return List.of();
        }
        Object raw = source.get(key);
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(Object::toString)
                    .toList();
        }
        if (raw instanceof String value && !value.isBlank()) {
            return List.of(value);
        }
        return List.of();
    }

    /**
     * 读取能力配置字段键集合。
     *
     * 是什么：从 map 中读取 capabilityConfigKeys。
     * 做什么：输出能力 -> 配置字段名列表的映射。
     * 为什么：用于单测断言能力配置被注入到上下文。
     */
    private java.util.Map<String, List<String>> readCapabilityConfigKeys(java.util.Map<String, Object> source) {
        if (source == null) {
            return java.util.Map.of();
        }
        Object raw = source.get("capabilityConfigKeys");
        if (raw instanceof java.util.Map<?, ?> map) {
            java.util.Map<String, List<String>> result = new java.util.HashMap<>();
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String key = entry.getKey().toString();
                Object value = entry.getValue();
                if (value instanceof List<?> list) {
                    List<String> keys = list.stream()
                            .filter(java.util.Objects::nonNull)
                            .map(Object::toString)
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .toList();
                    result.put(key, keys);
                }
            }
            return result;
        }
        return java.util.Map.of();
    }

    /**
     * 测试：获取任务状态
     * 期望：返回正确的任务实体
     */
    @Test
    void getJob_shouldReturnJob() {
        // GIVEN
        when(jobMapper.selectById(testJobId)).thenReturn(testJob);

        // WHEN
        G3JobEntity result = orchestratorService.getJob(testJobId);

        // THEN
        assertNotNull(result);
        assertEquals(testJobId, result.getId());
        assertEquals("创建一个用户管理系统", result.getRequirement());
    }

    /**
     * 测试：订阅日志流时应先回放历史日志
     *
     * 背景：
     * - SSE 订阅往往发生在 job 已开始执行之后（前端 submit 完再订阅）
     * - 若历史日志未回放，用户会看到“Network 200 但页面无任何流式日志”
     */
    @Test
    void subscribeToLogs_shouldReplayHistoryLogs() {
        // GIVEN
        List<G3LogEntry> history = List.of(
                G3LogEntry.info(G3LogEntry.Role.PLAYER, "G3引擎启动"),
                G3LogEntry.info(G3LogEntry.Role.ARCHITECT, "开始架构设计阶段...")
        );
        testJob.setLogs(new ArrayList<>(history));
        when(jobMapper.selectById(testJobId)).thenReturn(testJob);

        // WHEN
        Flux<G3LogEntry> flux = orchestratorService.subscribeToLogs(testJobId);

        // THEN：取前两条（历史日志），避免心跳导致测试阻塞
        List<G3LogEntry> firstTwo = flux.take(2).collectList().block(Duration.ofSeconds(1));
        assertNotNull(firstTwo);
        assertEquals(2, firstTwo.size());
        assertEquals("G3引擎启动", firstTwo.get(0).getMessage());
        assertTrue(firstTwo.get(1).getMessage().contains("架构设计"));
    }

    /**
     * 测试：架构设计阶段成功
     * 期望：生成契约并锁定
     */
    @Test
    void runJob_architectPhase_shouldGenerateAndLockContract() {
        // GIVEN
        when(jobMapper.selectById(testJobId)).thenReturn(testJob);
        prepareRunJobStubs();
        when(architectAgent.design(any(), any())).thenReturn(
                new IArchitectAgent.ArchitectResult(
                        "openapi: 3.0.0\ninfo:\n  title: User API",
                        "CREATE TABLE users (id UUID PRIMARY KEY);",
                        true,
                        null
                )
        );

        // 先创建Mock对象，避免在when()中嵌套调用
        ICoderAgent mockCoder = mockBackendCoder();
        when(coderAgents.stream()).thenReturn(List.of(mockCoder).stream());

        when(sandboxService.validate(any(), any(), any())).thenReturn(
                createSuccessValidationResult()
        );

        // WHEN
        orchestratorService.runJob(testJobId);

        // THEN
        verify(architectAgent).design(eq(testJob), any());
        verify(jobMapper, atLeastOnce()).updateById(argThat((G3JobEntity job) ->
                job.getContractYaml() != null &&
                job.getDbSchemaSql() != null &&
                Boolean.TRUE.equals(job.getContractLocked())
        ));
    }

    /**
     * 测试：代码生成阶段成功
     * 期望：调用后端编码器并保存产物
     */
    @Test
    void runJob_codingPhase_shouldGenerateArtifacts() {
        // GIVEN
        when(jobMapper.selectById(testJobId)).thenReturn(testJob);
        prepareRunJobStubs();

        // Mock架构设计阶段
        when(architectAgent.design(any(), any())).thenReturn(
                new IArchitectAgent.ArchitectResult(
                        "openapi: 3.0.0\ninfo:\n  title: Test API",
                        "CREATE TABLE users (id UUID);",
                        true,
                        null
                )
        );

        ICoderAgent mockCoder = mockBackendCoder();
        when(coderAgents.stream()).thenReturn(List.of(mockCoder).stream());
        when(sandboxService.validate(any(), any(), any())).thenReturn(
                createSuccessValidationResult()
        );

        // WHEN
        orchestratorService.runJob(testJobId);

        // THEN
        verify(mockCoder).generate(eq(testJob), eq(0), any());
        verify(artifactMapper, atLeast(2)).insert((G3ArtifactEntity) any());
    }

    /**
     * 测试：编译失败触发自修复
     * 期望：调用 Coach Agent 修复代码
     */
    @Test
    void runJob_compilationFails_shouldTriggerCoachRepair() {
        // GIVEN
        when(jobMapper.selectById(testJobId)).thenReturn(testJob);
        prepareRunJobStubs();

        // Mock架构设计阶段
        when(architectAgent.design(any(), any())).thenReturn(
                new IArchitectAgent.ArchitectResult(
                        "openapi: 3.0.0\ninfo:\n  title: Test API",
                        "CREATE TABLE users (id UUID);",
                        true,
                        null
                )
        );

        ICoderAgent mockCoder = mockBackendCoder();
        when(coderAgents.stream()).thenReturn(List.of(mockCoder).stream());

        // Mock验证失败：第一次失败（标记错误），第二次成功
        when(sandboxService.validate(any(), any(), any()))
                .thenAnswer(invocation -> {
                    // 第一次调用：标记artifacts有错误
                    List<G3ArtifactEntity> artifacts = invocation.getArgument(1);
                    if (!artifacts.isEmpty()) {
                        artifacts.get(0).markError("UserService.java:10: error: cannot find symbol");
                    }
                    return createFailedValidationResult();
                })
                .thenReturn(createSuccessValidationResult()); // 第二次成功

        when(coachAgent.fix(any(), any(), any(), any(), any())).thenReturn(
                new ICoachAgent.CoachResult(
                        List.of(createFixedArtifact()),
                        true,
                        "修复完成：修正了类型错误",
                        null
                )
        );

        // WHEN
        orchestratorService.runJob(testJobId);

        // THEN
        verify(coachAgent).fix(eq(testJob), any(), any(), any(), any());
        verify(validationResultMapper, atLeast(2)).insert((G3ValidationResultEntity) any());
        verify(jobMapper, atLeastOnce()).updateById(argThat((G3JobEntity job) ->
                job.getCurrentRound() >= 1
        ));
    }

    /**
     * 测试：达到最大修复轮次后失败
     * 期望：任务标记为 FAILED
     */
    @Test
    void runJob_maxRoundsExceeded_shouldFailJob() {
        // GIVEN
        when(jobMapper.selectById(testJobId)).thenReturn(testJob);
        prepareRunJobStubs();

        // Mock架构设计阶段
        when(architectAgent.design(any(), any())).thenReturn(
                new IArchitectAgent.ArchitectResult(
                        "openapi: 3.0.0\ninfo:\n  title: Test API",
                        "CREATE TABLE users (id UUID);",
                        true,
                        null
                )
        );

        ICoderAgent mockCoder = mockBackendCoder();
        when(coderAgents.stream()).thenReturn(List.of(mockCoder).stream());

        // Mock验证始终失败，并标记错误
        when(sandboxService.validate(any(), any(), any())).thenAnswer(invocation -> {
            List<G3ArtifactEntity> artifacts = invocation.getArgument(1);
            if (!artifacts.isEmpty()) {
                artifacts.get(0).markError("UserService.java:15: error: persistent error");
            }
            return createFailedValidationResult();
        });

        when(coachAgent.fix(any(), any(), any(), any(), any())).thenReturn(
                new ICoachAgent.CoachResult(
                        List.of(createFixedArtifact()),
                        true,
                        "尝试修复",
                        null
                )
        );

        // WHEN
        orchestratorService.runJob(testJobId);

        // THEN
        verify(jobMapper, atLeastOnce()).updateById(argThat((G3JobEntity job) ->
                job.getStatus().equals(G3JobEntity.Status.FAILED.getValue())
        ));
    }

    /**
     * 测试：获取任务产物
     * 期望：返回最新版本的产物列表
     */
    @Test
    void getArtifacts_shouldReturnLatestArtifacts() {
        // GIVEN
        List<G3ArtifactEntity> artifacts = List.of(
                createArtifact("UserService.java", 1),
                createArtifact("UserController.java", 1)
        );
        when(artifactMapper.selectLatestByJobId(testJobId)).thenReturn(artifacts);

        // WHEN
        List<G3ArtifactEntity> result = orchestratorService.getArtifacts(testJobId);

        // THEN
        assertEquals(2, result.size());
        assertEquals("UserService.java", result.get(0).getFileName());
    }

    // ========== Helper Methods ==========

    private ICoderAgent mockBackendCoder() {
        ICoderAgent coder = mock(ICoderAgent.class);
        when(coder.getTargetType()).thenReturn("backend");
        when(coder.generate(any(), anyInt(), any())).thenReturn(
                new ICoderAgent.CoderResult(
                        new ArrayList<>(List.of(
                                createArtifact("UserService.java", 1),
                                createArtifact("UserController.java", 1)
                        )),
                        true,
                        null
                )
        );
        return coder;
    }

    /**
     * 仅用于 runJob 流程的统一 Mock 准备。
     *
     * 是什么：收拢 runJob 所需的依赖桩。
     * 做什么：避免 setUp 中的未使用 stubbing 触发严格模式报错。
     * 为什么：保证测试稳定且仅在需要时启用 Mock。
     */
    private void prepareRunJobStubs() {
        // SessionMemory 默认 Mock：避免 runJob 入口空指针
        when(memoryPersistenceService.getOrCreate(any()))
                .thenAnswer(invocation -> new G3SessionMemory(invocation.getArgument(0)));

        // 依赖分析默认返回空图，避免任务分解影响测试
        when(dependencyAnalyzer.analyzeFromSchema(anyString()))
                .thenReturn(new G3TaskDependencyGraph());

        // PhaseValidator 统一走 sandboxService 的验证结果，便于复用现有测试桩
        lenient().when(phaseValidator.validateAll(any(), any(), any()))
                .thenAnswer(invocation -> {
                    G3ValidationResultEntity result = sandboxService.validate(
                            invocation.getArgument(0),
                            invocation.getArgument(1),
                            invocation.getArgument(2));
                    if (result == null) {
                        result = createSuccessValidationResult();
                    }
                    boolean passed = Boolean.TRUE.equals(result.getPassed());
                    return new G3PhaseValidator.ValidationResult(passed, result, null, List.of());
                });
    }

    private G3ArtifactEntity createArtifact(String fileName, int version) {
        return G3ArtifactEntity.builder()
                .id(UUID.randomUUID())
                .jobId(testJobId)
                .fileName(fileName)
                .filePath("src/main/java/com/example/" + fileName)
                .content("public class " + fileName.replace(".java", "") + " {}")
                .language("java")
                .artifactType("SERVICE")
                .version(version)
                .hasErrors(false)
                .generatedBy(G3ArtifactEntity.GeneratedBy.BACKEND_CODER.getValue())
                .generationRound(0)
                .build();
    }

    private G3ArtifactEntity createFixedArtifact() {
        return createArtifact("UserService.java", 2);
    }

    private G3ValidationResultEntity createSuccessValidationResult() {
        return G3ValidationResultEntity.builder()
                .id(UUID.randomUUID())
                .jobId(testJobId)
                .round(1)
                .passed(true)
                .errorCount(0)
                .warningCount(0)
                .build();
    }

    private G3ValidationResultEntity createFailedValidationResult() {
        return G3ValidationResultEntity.builder()
                .id(UUID.randomUUID())
                .jobId(testJobId)
                .round(1)
                .passed(false)
                .errorCount(5)
                .warningCount(2)
                .stderr("Cannot find symbol: User")
                .build();
    }
}
