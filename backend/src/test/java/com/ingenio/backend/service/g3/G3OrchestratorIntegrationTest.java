package com.ingenio.backend.service.g3;

import com.ingenio.backend.agent.g3.IArchitectAgent;
import com.ingenio.backend.agent.g3.ICoachAgent;
import com.ingenio.backend.agent.g3.ICoderAgent;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import com.ingenio.backend.entity.g3.G3SessionMemory;
import com.ingenio.backend.mapper.g3.G3ArtifactMapper;
import com.ingenio.backend.mapper.g3.G3JobMapper;
import com.ingenio.backend.mapper.g3.G3ValidationResultMapper;
import com.ingenio.backend.service.blueprint.BlueprintValidator;
import com.ingenio.backend.service.blueprint.BlueprintComplianceResult;
import com.ingenio.backend.websocket.G3WebSocketBroadcaster;
import com.ingenio.backend.mapper.AppSpecMapper;
import com.ingenio.backend.mapper.IndustryTemplateMapper;
import com.ingenio.backend.mapper.GenerationTaskMapper;
import com.ingenio.backend.mapper.GenerationVersionMapper;
import com.ingenio.backend.service.VersionSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class G3OrchestratorIntegrationTest {

    @Mock private G3JobMapper jobMapper;
    @Mock private G3ArtifactMapper artifactMapper;
    @Mock private G3ValidationResultMapper validationResultMapper;
    @Mock private AppSpecMapper appSpecMapper;
    @Mock private IndustryTemplateMapper industryTemplateMapper;
    /**
     * 生成任务Mapper Mock：用于TimeMachine链路同步。
     */
    @Mock private GenerationTaskMapper generationTaskMapper;
    /**
     * 版本Mapper Mock：用于快照归档流程。
     */
    @Mock private GenerationVersionMapper generationVersionMapper;
    /**
     * 快照服务 Mock：用于版本快照写入。
     */
    @Mock private VersionSnapshotService snapshotService;
    /**
     * 归档服务 Mock：避免集成测试触发真实上传。
     */
    @Mock private G3CodeArchiveService codeArchiveService;
    /**
     * 阶段验证器 Mock：控制验证通过/失败结果。
     */
    @Mock private G3PhaseValidator phaseValidator;
    /**
     * 知识库 Mock：避免集成测试触发向量索引。
     */
    @Mock private G3KnowledgeStore knowledgeStore;
    /**
     * 仓库索引 Mock：避免集成测试触发索引构建。
     */
    @Mock private G3RepoIndexService repoIndexService;
    /**
     * SessionMemory 持久化 Mock：避免依赖外部存储。
     */
    @Mock private G3MemoryPersistenceService memoryPersistenceService;
    @Mock private BlueprintValidator blueprintValidator;
    
    @Mock private IArchitectAgent architectAgent;
    @Mock private ICoderAgent backendCoder;
    @Mock private ICoachAgent coachAgent;
    @Mock private G3SandboxService sandboxService;
    @Mock private G3PlanningFileService planningFileService;
    @Mock private G3WebSocketBroadcaster g3WebSocketBroadcaster;
    
    // Use real analyzer to test logic
    @Spy private G3DependencyAnalyzer dependencyAnalyzer = new G3DependencyAnalyzer();

    @InjectMocks
    private G3OrchestratorService orchestratorService;

    private G3JobEntity testJob;
    private UUID testJobId;

    @BeforeEach
    void setUp() {
        testJobId = UUID.randomUUID();
        testJob = G3JobEntity.builder()
                .id(testJobId)
                .requirement("Create Safety Incident App")
                .status(G3JobEntity.Status.PLANNING.getValue())
                .currentRound(0)
                .maxRounds(3)
                .logs(new ArrayList<>())
                .build();

        // Inject dependencies
        List<ICoderAgent> coders = new ArrayList<>();
        coders.add(backendCoder);
        
        ReflectionTestUtils.setField(orchestratorService, "coderAgents", coders);
        ReflectionTestUtils.setField(orchestratorService, "maxRounds", 3);
        ReflectionTestUtils.setField(orchestratorService, "self", orchestratorService); // Mock self-call
        
        // Mock Coder Type
        when(backendCoder.getTargetType()).thenReturn("backend");
        
        // Mock Blueprint Validator default pass
        lenient().when(blueprintValidator.validateSchemaCompliance(any(), any()))
                .thenReturn(BlueprintComplianceResult.passedResult());
        lenient().when(blueprintValidator.validateBackendArtifactsCompliance(any(), any()))
                .thenReturn(BlueprintComplianceResult.passedResult());

        // SessionMemory 默认 Mock：避免 runJob 入口空指针
        when(memoryPersistenceService.getOrCreate(any()))
                .thenAnswer(invocation -> new G3SessionMemory(invocation.getArgument(0)));

        // PhaseValidator 默认返回成功，避免验证阻断集成流程
        lenient().when(phaseValidator.validateAll(any(), any(), any()))
                .thenAnswer(invocation -> new G3PhaseValidator.ValidationResult(
                        true,
                        G3ValidationResultEntity.builder()
                                .id(UUID.randomUUID())
                                .jobId(testJobId)
                                .round(1)
                                .passed(true)
                                .errorCount(0)
                                .warningCount(0)
                                .build(),
                        null,
                        List.of()
                ));
    }

    @Test
    void runJob_shouldAnalyzeDependencies_andUpdatePlan() {
        // GIVEN
        when(jobMapper.selectById(testJobId)).thenReturn(testJob);

        // 1. Mock Architect: Returns Schema with 2 tables
        String schemaSql = "CREATE TABLE incident (id UUID); CREATE TABLE reporter (id UUID);";
        when(architectAgent.design(any(), any())).thenReturn(
                new IArchitectAgent.ArchitectResult(
                        "openapi: 3.0.0", schemaSql, true, null
                )
        );

        // 2. Mock Coder: Returns artifacts with imports
        String serviceContent = """
            package com.example.service;
            import com.example.entity.Incident;
            import com.baomidou.mybatisplus.extension.service.IService;
            public class IncidentService implements IService<Incident> {}
            """;
        
        when(backendCoder.generate(any(), anyInt(), any())).thenReturn(
                new ICoderAgent.CoderResult(
                        new ArrayList<>(List.of(
                                createArtifact("IncidentService.java", serviceContent)
                        )),
                        true, null
                )
        );

        // WHEN
        orchestratorService.runJob(testJobId);

        // THEN
        
        // 1. Verify Dependency Analysis was called
        verify(dependencyAnalyzer).analyzeFromSchema(contains("CREATE TABLE incident"));
        
        // 2. Verify Task Plan Update (Detailed Breakdown)
        // Expect calls to appendContent with the breakdown
        verify(planningFileService, atLeastOnce()).appendContent(
                eq(testJobId), 
                eq("task_plan"), // G3PlanningFileEntity.FILE_TYPE_TASK_PLAN
                contains("Generate IncidentEntity"), 
                anyString()
        );
        
        // 3. Verify Context Update (Imports)
        // Expect updateImportIndex called with imports found in IncidentService.java
        verify(planningFileService).updateImportIndex(
                eq(testJobId),
                eq("service"), // inferred type
                argThat(list -> list.contains("com.example.entity.Incident")),
                anyString()
        );
        
        // 4. Verify Context Update (Signature)
        verify(planningFileService).addClassSignature(
                eq(testJobId),
                eq("service"),
                eq("IncidentService"),
                contains("public class IncidentService"),
                anyString()
        );
    }

    private G3ArtifactEntity createArtifact(String fileName, String content) {
        return G3ArtifactEntity.builder()
                .id(UUID.randomUUID())
                .jobId(testJobId)
                .fileName(fileName)
                .filePath("src/main/java/com/example/service/" + fileName)
                .content(content)
                .generatedBy("backend_coder")
                .build();
    }
}
