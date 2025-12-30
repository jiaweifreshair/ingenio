package com.ingenio.backend.service.g3;

import com.ingenio.backend.agent.g3.IArchitectAgent;
import com.ingenio.backend.agent.g3.ICoachAgent;
import com.ingenio.backend.agent.g3.ICoderAgent;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import com.ingenio.backend.mapper.g3.G3ArtifactMapper;
import com.ingenio.backend.mapper.g3.G3JobMapper;
import com.ingenio.backend.mapper.g3.G3ValidationResultMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    @Mock
    private G3JobMapper jobMapper;

    @Mock
    private G3ArtifactMapper artifactMapper;

    @Mock
    private G3ValidationResultMapper validationResultMapper;

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
     * 测试：架构设计阶段成功
     * 期望：生成契约并锁定
     */
    @Test
    void runJob_architectPhase_shouldGenerateAndLockContract() {
        // GIVEN
        when(jobMapper.selectById(testJobId)).thenReturn(testJob);
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

        when(coachAgent.fix(any(), any(), any(), any())).thenReturn(
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
        verify(coachAgent).fix(eq(testJob), any(), any(), any());
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

        when(coachAgent.fix(any(), any(), any(), any())).thenReturn(
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
