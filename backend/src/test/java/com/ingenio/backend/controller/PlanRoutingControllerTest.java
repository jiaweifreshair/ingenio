package com.ingenio.backend.controller;

import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.dto.request.OpenLovableGenerateRequest;
import com.ingenio.backend.dto.request.PlanRoutingRequest;
import com.ingenio.backend.dto.response.OpenLovableGenerateResponse;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.entity.GenerationTaskEntity;
import com.ingenio.backend.entity.ProjectEntity;
import com.ingenio.backend.enums.TechStackType;
import com.ingenio.backend.mapper.UserMapper;
import com.ingenio.backend.mapper.GenerationTaskMapper;
import com.ingenio.backend.service.AppSpecService;
import com.ingenio.backend.service.BillingService;
import com.ingenio.backend.service.OpenLovableService;
import com.ingenio.backend.service.ProjectService;
import com.ingenio.backend.service.VersionSnapshotService;
import com.ingenio.backend.service.g3.G3OrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PlanRoutingController 单元测试
 *
 * 覆盖点：
 * - /v2/plan-routing/route 支持复用 appSpecId（需求修改不新建记录）
 * - /v2/plan-routing/{id}/update-requirement 支持在原型确认前持续更新需求
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanRoutingControllerTest {

    @Mock
    private AppSpecService appSpecService;

    @Mock
    private BillingService billingService;

    /**
     * OpenLovable 服务 Mock。
     *
     * 是什么：用于替代原型生成服务的测试替身。
     * 做什么：满足控制器构造依赖，避免真实外部调用。
     * 为什么：保证单测仅验证路由逻辑，不受外部服务影响。
     */
    @Mock
    private OpenLovableService openLovableService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ProjectService projectService;

    @Mock
    private GenerationTaskMapper generationTaskMapper;

    @Mock
    private VersionSnapshotService versionSnapshotService;

    @Mock
    private com.ingenio.backend.service.NLRequirementAnalyzer nlRequirementAnalyzer;

    @Mock
    private G3OrchestratorService g3OrchestratorService;

    private PlanRoutingController controller;

    @BeforeEach
    void setUp() {
        controller = new PlanRoutingController(appSpecService, billingService, openLovableService, userMapper,
                projectService, generationTaskMapper, versionSnapshotService, nlRequirementAnalyzer,
                g3OrchestratorService);
    }

    @Test
    void routeRequirement_createsNewAppSpec_whenAppSpecIdMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID appSpecId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(appSpecService.createAppSpec(eq(tenantId), eq(userId), any())).thenAnswer(invocation -> {
            Map<String, Object> specContent = invocation.getArgument(2);
            return AppSpecEntity.builder()
                    .id(appSpecId)
                    .tenantId(tenantId)
                    .createdByUserId(userId)
                    .specContent(specContent)
                    .metadata(new HashMap<>())
                    .build();
        });
        when(appSpecService.updateById(any())).thenReturn(true);
        when(projectService.findByAppSpecId(appSpecId)).thenReturn(null);
        when(projectService.createProject(any())).thenReturn(ProjectEntity.builder()
                .id(projectId)
                .appSpecId(appSpecId)
                .build());

        PlanRoutingRequest req = PlanRoutingRequest.builder()
                .userRequirement("创建一个用于安全事故管理的企业后台系统，包含上报/审核/整改闭环/统计看板")
                .tenantId(tenantId)
                .userId(userId)
                .build();

        Result<PlanRoutingController.PlanRoutingResult> resp = controller.routeRequirement(req);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals(appSpecId.toString(), resp.getData().getAppSpecId());
        verify(appSpecService, times(1)).createAppSpec(eq(tenantId), eq(userId), any());
        verify(appSpecService, atLeastOnce()).updateById(any(AppSpecEntity.class));
    }

    @Test
    void routeRequirement_reusesExistingAppSpec_whenAppSpecIdProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID appSpecId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        AppSpecEntity existing = AppSpecEntity.builder()
                .id(appSpecId)
                .tenantId(tenantId)
                .createdByUserId(userId)
                .specContent(new HashMap<>(Map.of("userRequirement", "旧需求")))
                .metadata(new HashMap<>())
                .build();

        when(appSpecService.getById(appSpecId)).thenReturn(existing);
        when(appSpecService.updateById(any())).thenReturn(true);
        when(projectService.findByAppSpecId(appSpecId)).thenReturn(ProjectEntity.builder()
                .id(projectId)
                .appSpecId(appSpecId)
                .description("旧需求")
                .build());
        when(projectService.updateById(any())).thenReturn(true);

        PlanRoutingRequest req = PlanRoutingRequest.builder()
                .appSpecId(appSpecId)
                .userRequirement("新需求：增加整改闭环超时提醒与权限控制")
                .tenantId(tenantId)
                .userId(userId)
                .build();

        Result<PlanRoutingController.PlanRoutingResult> resp = controller.routeRequirement(req);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals(appSpecId.toString(), resp.getData().getAppSpecId());
        verify(appSpecService, never()).createAppSpec(any(), any(), any());

        ArgumentCaptor<AppSpecEntity> captor = ArgumentCaptor.forClass(AppSpecEntity.class);
        verify(appSpecService, atLeastOnce()).updateById(captor.capture());
        AppSpecEntity updated = captor.getValue();
        assertNotNull(updated.getSpecContent());
        assertEquals("新需求：增加整改闭环超时提醒与权限控制", updated.getSpecContent().get("userRequirement"));
    }

    /**
     * 技术栈推断：包含 Spring Boot 的需求应路由至 React + Spring Boot。
     *
     * 是什么：验证需求明确提到 Spring Boot 时的技术栈选择。
     * 做什么：调用路由接口并断言 techStackType 为 REACT_SPRING_BOOT。
     * 为什么：避免高复杂场景误判为 Supabase 导致服务端能力缺失。
     */
    @Test
    void routeRequirement_infersSpringBoot_whenRequirementMentionsSpringBoot() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID appSpecId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(appSpecService.createAppSpec(eq(tenantId), eq(userId), any())).thenAnswer(invocation -> {
            Map<String, Object> specContent = invocation.getArgument(2);
            return AppSpecEntity.builder()
                    .id(appSpecId)
                    .tenantId(tenantId)
                    .createdByUserId(userId)
                    .specContent(specContent)
                    .metadata(new HashMap<>())
                    .build();
        });
        when(appSpecService.updateById(any())).thenReturn(true);
        when(projectService.findByAppSpecId(appSpecId)).thenReturn(null);
        when(projectService.createProject(any())).thenReturn(ProjectEntity.builder()
                .id(projectId)
                .appSpecId(appSpecId)
                .build());

        PlanRoutingRequest req = PlanRoutingRequest.builder()
                .userRequirement("城市大脑指挥中枢，前端 React + Spring Boot 后端，强调多智能体调度")
                .tenantId(tenantId)
                .userId(userId)
                .build();

        Result<PlanRoutingController.PlanRoutingResult> resp = controller.routeRequirement(req);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals(TechStackType.REACT_SPRING_BOOT.name(), resp.getData().getTechStackType());
        assertEquals(TechStackType.REACT_SPRING_BOOT.getCode(), resp.getData().getTechStackCode());
    }

    /**
     * 技术栈推断：海报生成工具应路由至 React + Spring Boot。
     *
     * 是什么：验证“生成类工具”需求的技术栈选择。
     * 做什么：调用路由接口并断言 techStackType 为 REACT_SPRING_BOOT。
     * 为什么：生成类场景通常涉及文件/异步任务，需后端能力支撑。
     */
    @Test
    void routeRequirement_infersSpringBoot_whenRequirementMentionsPosterGenerator() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID appSpecId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(appSpecService.createAppSpec(eq(tenantId), eq(userId), any())).thenAnswer(invocation -> {
            Map<String, Object> specContent = invocation.getArgument(2);
            return AppSpecEntity.builder()
                    .id(appSpecId)
                    .tenantId(tenantId)
                    .createdByUserId(userId)
                    .specContent(specContent)
                    .metadata(new HashMap<>())
                    .build();
        });
        when(appSpecService.updateById(any())).thenReturn(true);
        when(projectService.findByAppSpecId(appSpecId)).thenReturn(null);
        when(projectService.createProject(any())).thenReturn(ProjectEntity.builder()
                .id(projectId)
                .appSpecId(appSpecId)
                .build());

        PlanRoutingRequest req = PlanRoutingRequest.builder()
                .userRequirement("打造一款能够一键生成创意海报的工具，支持模板与下载")
                .tenantId(tenantId)
                .userId(userId)
                .build();

        Result<PlanRoutingController.PlanRoutingResult> resp = controller.routeRequirement(req);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals(TechStackType.REACT_SPRING_BOOT.name(), resp.getData().getTechStackType());
        assertEquals(TechStackType.REACT_SPRING_BOOT.getCode(), resp.getData().getTechStackCode());
    }

    /**
     * 技术栈推断：城市级指挥中枢应路由至 React + Spring Boot。
     *
     * 是什么：验证“城市大脑/指挥中枢”场景的技术栈选择。
     * 做什么：调用路由接口并断言 techStackType 为 REACT_SPRING_BOOT。
     * 为什么：该类场景涉及复杂调度与多智能体协同。
     */
    @Test
    void routeRequirement_infersSpringBoot_whenRequirementMentionsCommandCenter() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID appSpecId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(appSpecService.createAppSpec(eq(tenantId), eq(userId), any())).thenAnswer(invocation -> {
            Map<String, Object> specContent = invocation.getArgument(2);
            return AppSpecEntity.builder()
                    .id(appSpecId)
                    .tenantId(tenantId)
                    .createdByUserId(userId)
                    .specContent(specContent)
                    .metadata(new HashMap<>())
                    .build();
        });
        when(appSpecService.updateById(any())).thenReturn(true);
        when(projectService.findByAppSpecId(appSpecId)).thenReturn(null);
        when(projectService.createProject(any())).thenReturn(ProjectEntity.builder()
                .id(projectId)
                .appSpecId(appSpecId)
                .build());

        PlanRoutingRequest req = PlanRoutingRequest.builder()
                .userRequirement("城市级指挥中枢，强调多智能体调度与态势监控")
                .tenantId(tenantId)
                .userId(userId)
                .build();

        Result<PlanRoutingController.PlanRoutingResult> resp = controller.routeRequirement(req);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals(TechStackType.REACT_SPRING_BOOT.name(), resp.getData().getTechStackType());
        assertEquals(TechStackType.REACT_SPRING_BOOT.getCode(), resp.getData().getTechStackCode());
    }

    /**
     * 技术栈推断：需求明确提到 Spring Boot 时，应覆盖用户预选的 Supabase 提示。
     *
     * 是什么：验证显式技术栈关键词优先级高于前端提示。
     * 做什么：传入 techStackHint=React+Supabase，但需求包含 Spring Boot/JeecgBoot。
     * 为什么：避免需求明确企业级技术栈时仍误判为 Supabase。
     */
    @Test
    void routeRequirement_prefersExplicitSpringBoot_overTechStackHint() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID appSpecId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(appSpecService.createAppSpec(eq(tenantId), eq(userId), any())).thenAnswer(invocation -> {
            Map<String, Object> specContent = invocation.getArgument(2);
            return AppSpecEntity.builder()
                    .id(appSpecId)
                    .tenantId(tenantId)
                    .createdByUserId(userId)
                    .specContent(specContent)
                    .metadata(new HashMap<>())
                    .build();
        });
        when(appSpecService.updateById(any())).thenReturn(true);
        when(projectService.findByAppSpecId(appSpecId)).thenReturn(null);
        when(projectService.createProject(any())).thenReturn(ProjectEntity.builder()
                .id(projectId)
                .appSpecId(appSpecId)
                .build());

        PlanRoutingRequest req = PlanRoutingRequest.builder()
                .userRequirement("城市大脑指挥中枢，Spring Boot/JeecgBoot 后端，强调多智能体调度")
                .tenantId(tenantId)
                .userId(userId)
                .techStackHint("React+Supabase")
                .build();

        Result<PlanRoutingController.PlanRoutingResult> resp = controller.routeRequirement(req);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals(TechStackType.REACT_SPRING_BOOT.name(), resp.getData().getTechStackType());
        assertEquals(TechStackType.REACT_SPRING_BOOT.getCode(), resp.getData().getTechStackCode());
    }

    /**
     * 技术栈推断：包含 H5/WebView/游戏化 时应路由至 H5 + WebView。
     *
     * 是什么：验证轻量游戏化流程场景的技术栈选择。
     * 做什么：调用路由接口并断言 techStackType 为 H5_WEBVIEW。
     * 为什么：保证小学组 H5 场景不误判为复杂后端栈。
     */
    @Test
    void routeRequirement_infersH5WebView_whenRequirementMentionsH5GameFlow() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID appSpecId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(appSpecService.createAppSpec(eq(tenantId), eq(userId), any())).thenAnswer(invocation -> {
            Map<String, Object> specContent = invocation.getArgument(2);
            return AppSpecEntity.builder()
                    .id(appSpecId)
                    .tenantId(tenantId)
                    .createdByUserId(userId)
                    .specContent(specContent)
                    .metadata(new HashMap<>())
                    .build();
        });
        when(appSpecService.updateById(any())).thenReturn(true);
        when(projectService.findByAppSpecId(appSpecId)).thenReturn(null);
        when(projectService.createProject(any())).thenReturn(ProjectEntity.builder()
                .id(projectId)
                .appSpecId(appSpecId)
                .build());

        PlanRoutingRequest req = PlanRoutingRequest.builder()
                .userRequirement("我的安全小卫士：游戏化四步流程（扫描→分析→发现隐患→修复方案），H5 WebView 形态")
                .tenantId(tenantId)
                .userId(userId)
                .build();

        Result<PlanRoutingController.PlanRoutingResult> resp = controller.routeRequirement(req);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals(TechStackType.H5_WEBVIEW.name(), resp.getData().getTechStackType());
        assertEquals(TechStackType.H5_WEBVIEW.getCode(), resp.getData().getTechStackCode());
    }

    /**
     * 技术栈推断：普通逻辑流演示应默认路由至 React + Supabase。
     *
     * 是什么：验证无复杂后端关键词时的默认技术栈。
     * 做什么：调用路由接口并断言 techStackType 为 REACT_SUPABASE。
     * 为什么：保证中学组逻辑流场景保持轻量 BaaS 架构。
     */
    @Test
    void routeRequirement_defaultsToReactSupabase_whenNoComplexKeywordsPresent() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID appSpecId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(appSpecService.createAppSpec(eq(tenantId), eq(userId), any())).thenAnswer(invocation -> {
            Map<String, Object> specContent = invocation.getArgument(2);
            return AppSpecEntity.builder()
                    .id(appSpecId)
                    .tenantId(tenantId)
                    .createdByUserId(userId)
                    .specContent(specContent)
                    .metadata(new HashMap<>())
                    .build();
        });
        when(appSpecService.updateById(any())).thenReturn(true);
        when(projectService.findByAppSpecId(appSpecId)).thenReturn(null);
        when(projectService.createProject(any())).thenReturn(ProjectEntity.builder()
                .id(projectId)
                .appSpecId(appSpecId)
                .build());

        PlanRoutingRequest req = PlanRoutingRequest.builder()
                .userRequirement("校园逻辑哨兵：输入→处理→判断→输出的逻辑流演示，按钮触发模拟运行")
                .tenantId(tenantId)
                .userId(userId)
                .build();

        Result<PlanRoutingController.PlanRoutingResult> resp = controller.routeRequirement(req);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals(TechStackType.REACT_SUPABASE.name(), resp.getData().getTechStackType());
        assertEquals(TechStackType.REACT_SUPABASE.getCode(), resp.getData().getTechStackCode());
    }

    /**
     * 原型生成：当 AppSpec 缺失 sandboxId/previewUrl 时应触发 OpenLovable 并写回元数据。
     *
     * 是什么：验证 /v2/plan-routing/{id}/generate-prototype 的写回逻辑。
     * 做什么：mock OpenLovableService 返回成功响应，并断言 AppSpec 被更新（sandboxId + previewUrl）。
     * 为什么：确保“下载前端页面”所需的 sandboxId 能自动补齐，避免用户手动介入。
     */
    @Test
    void generatePrototype_updatesSandboxMetadata_whenPrototypeMissing() {
        UUID appSpecId = UUID.randomUUID();
        AppSpecEntity appSpec = AppSpecEntity.builder()
                .id(appSpecId)
                .specContent(new HashMap<>(Map.of("userRequirement", "测试需求：生成一个简易待办应用并支持导出")))
                .metadata(new HashMap<>())
                .build();

        when(appSpecService.getById(appSpecId)).thenReturn(appSpec);
        when(appSpecService.updateById(any(AppSpecEntity.class))).thenReturn(true);
        when(openLovableService.generatePrototype(any(OpenLovableGenerateRequest.class)))
                .thenReturn(OpenLovableGenerateResponse.success("sb_test", "https://5173-sb_test.e2b.app", "e2b"));

        Result<Map<String, Object>> resp = controller.generatePrototype(appSpecId.toString());

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals("sb_test", resp.getData().get("sandboxId"));
        assertEquals("https://5173-sb_test.e2b.app", resp.getData().get("previewUrl"));

        ArgumentCaptor<AppSpecEntity> captor = ArgumentCaptor.forClass(AppSpecEntity.class);
        verify(appSpecService, atLeastOnce()).updateById(captor.capture());
        AppSpecEntity updated = captor.getValue();
        assertEquals("https://5173-sb_test.e2b.app", updated.getFrontendPrototypeUrl());
        assertNotNull(updated.getMetadata());
        assertEquals("sb_test", updated.getMetadata().get("sandboxId"));
        assertEquals("e2b", updated.getMetadata().get("sandboxProvider"));
    }

    /**
     * 原型生成：当原型已存在时应直接返回，且不再调用 OpenLovable。
     *
     * 是什么：验证重复调用 generate-prototype 的幂等性。
     * 做什么：构造已包含 sandboxId 与 previewUrl 的 AppSpec，断言不会触发 OpenLovableService。
     * 为什么：避免重复生成导致资源浪费与不必要的等待。
     */
    @Test
    void generatePrototype_returnsExisting_whenPrototypeAlreadyExists() {
        UUID appSpecId = UUID.randomUUID();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sandboxId", "sb_exists");
        metadata.put("sandboxProvider", "e2b");

        AppSpecEntity appSpec = AppSpecEntity.builder()
                .id(appSpecId)
                .specContent(new HashMap<>(Map.of("userRequirement", "测试需求：已有原型无需重复生成")))
                .metadata(metadata)
                .frontendPrototypeUrl("https://5173-sb_exists.e2b.app")
                .build();

        when(appSpecService.getById(appSpecId)).thenReturn(appSpec);
        when(openLovableService.isSandboxHealthy("sb_exists")).thenReturn(true);

        Result<Map<String, Object>> resp = controller.generatePrototype(appSpecId.toString());

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals("sb_exists", resp.getData().get("sandboxId"));
        assertEquals("https://5173-sb_exists.e2b.app", resp.getData().get("previewUrl"));

        verify(openLovableService, never()).generatePrototype(any(OpenLovableGenerateRequest.class));
    }

    /**
     * 原型生成：当原型已存在但沙箱不健康时，应销毁旧沙箱并重新触发生成。
     *
     * 是什么：验证“沙箱不可达导致下载/执行失败”时的自动重建逻辑。
     * 做什么：mock isSandboxHealthy=false，断言会调用 killSandbox + generatePrototype，并写回新 sandboxId。
     * 为什么：避免 AppSpec 记录复用一个“已存在但不响应”的沙箱，导致下载前端页面永远 500。
     */
    @Test
    void generatePrototype_regenerates_whenSandboxUnhealthy_evenIfPrototypeExists() {
        UUID appSpecId = UUID.randomUUID();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sandboxId", "sb_unhealthy");
        metadata.put("sandboxProvider", "e2b");

        AppSpecEntity appSpec = AppSpecEntity.builder()
                .id(appSpecId)
                .specContent(new HashMap<>(Map.of("userRequirement", "测试需求：沙箱不健康时应自动重建原型")))
                .metadata(metadata)
                .frontendPrototypeUrl("https://5173-sb_unhealthy.e2b.app")
                .build();

        when(appSpecService.getById(appSpecId)).thenReturn(appSpec);
        when(appSpecService.updateById(any(AppSpecEntity.class))).thenReturn(true);
        when(openLovableService.isSandboxHealthy("sb_unhealthy")).thenReturn(false);
        doNothing().when(openLovableService).killSandbox("sb_unhealthy");
        when(openLovableService.generatePrototype(any(OpenLovableGenerateRequest.class)))
                .thenReturn(OpenLovableGenerateResponse.success("sb_new", "https://5173-sb_new.e2b.app", "e2b"));

        Result<Map<String, Object>> resp = controller.generatePrototype(appSpecId.toString());

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals("sb_new", resp.getData().get("sandboxId"));
        assertEquals("https://5173-sb_new.e2b.app", resp.getData().get("previewUrl"));

        verify(openLovableService).killSandbox("sb_unhealthy");
        verify(openLovableService).generatePrototype(any(OpenLovableGenerateRequest.class));

        ArgumentCaptor<AppSpecEntity> captor = ArgumentCaptor.forClass(AppSpecEntity.class);
        verify(appSpecService, atLeastOnce()).updateById(captor.capture());
        AppSpecEntity updated = captor.getValue();
        assertEquals("https://5173-sb_new.e2b.app", updated.getFrontendPrototypeUrl());
        assertNotNull(updated.getMetadata());
        assertEquals("sb_new", updated.getMetadata().get("sandboxId"));
    }

    @Test
    void executeCodeGeneration_returnsJobId_whenDesignConfirmed() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID appSpecId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        AppSpecEntity appSpec = AppSpecEntity.builder()
                .id(appSpecId)
                .tenantId(tenantId)
                .createdByUserId(userId)
                .specContent(new HashMap<>(Map.of("userRequirement", "测试需求：生成订单管理系统")))
                .metadata(new HashMap<>())
                .designConfirmed(true)
                .build();

        when(appSpecService.getById(appSpecId)).thenReturn(appSpec);
        when(appSpecService.updateById(any())).thenReturn(true);
        when(generationTaskMapper.insert(any(GenerationTaskEntity.class))).thenReturn(1);
        when(generationTaskMapper.updateById(any(GenerationTaskEntity.class))).thenReturn(1);
        when(g3OrchestratorService.submitJob(anyString(), any(), any(), any(), any(), any())).thenReturn(jobId);

        Result<Map<String, Object>> resp = controller.executeCodeGeneration(appSpecId.toString(), Map.of(
                "entities", List.of("Order", "Payment")
        ));

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals(jobId.toString(), resp.getData().get("jobId"));
    }

    @Test
    void completeCodeGeneration_fillsUserId_whenMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID appSpecId = UUID.randomUUID();

        AppSpecEntity appSpec = AppSpecEntity.builder()
                .id(appSpecId)
                .tenantId(tenantId)
                .createdByUserId(null)
                .specContent(new HashMap<>(Map.of("userRequirement", "测试需求")))
                .metadata(new HashMap<>())
                .build();

        when(appSpecService.getById(appSpecId)).thenReturn(appSpec);
        when(generationTaskMapper.selectOne(any())).thenReturn(null);
        when(generationTaskMapper.insert(any(GenerationTaskEntity.class))).thenReturn(1);
        when(generationTaskMapper.updateById(any(GenerationTaskEntity.class))).thenReturn(1);

        Result<Map<String, Object>> resp = controller.completeCodeGeneration(appSpecId.toString(), null);

        assertTrue(resp.isSuccess());
        ArgumentCaptor<GenerationTaskEntity> taskCaptor = ArgumentCaptor.forClass(GenerationTaskEntity.class);
        verify(generationTaskMapper).insert(taskCaptor.capture());
        GenerationTaskEntity inserted = taskCaptor.getValue();
        assertNotNull(inserted.getUserId());
        assertEquals(tenantId, inserted.getUserId());
    }

    @Test
    void updateRequirement_updatesSpecContentOnly() {
        UUID appSpecId = UUID.randomUUID();
        AppSpecEntity existing = AppSpecEntity.builder()
                .id(appSpecId)
                .tenantId(UUID.randomUUID())
                .createdByUserId(UUID.randomUUID())
                .specContent(new HashMap<>(Map.of("userRequirement", "旧需求", "stage", "planning")))
                .metadata(new HashMap<>())
                .build();

        when(appSpecService.getById(appSpecId)).thenReturn(existing);
        when(appSpecService.updateById(any())).thenReturn(true);

        PlanRoutingController.UpdateRequirementRequest req = new PlanRoutingController.UpdateRequirementRequest();
        req.setUserRequirement("更新后的需求（通过原型阶段Chat修改）");

        Result<Map<String, Object>> resp = controller.updateRequirement(appSpecId.toString(), req);

        assertTrue(resp.isSuccess());
        verify(appSpecService).updateById(any(AppSpecEntity.class));
        assertEquals("更新后的需求（通过原型阶段Chat修改）", existing.getSpecContent().get("userRequirement"));
    }
}
