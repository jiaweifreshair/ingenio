package com.ingenio.backend.agent.g3.impl;

import com.ingenio.backend.agent.g3.IArchitectAgent;
import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.ai.AIProviderFactory;
import com.ingenio.backend.ai.UniaixAIProvider;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.prompt.PromptTemplateService;
import com.ingenio.backend.service.ProjectService;
import com.ingenio.backend.service.blueprint.BlueprintPromptBuilder;
import com.ingenio.backend.service.blueprint.BlueprintValidator;
import com.ingenio.backend.service.g3.hooks.G3HookPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ArchitectAgentImpl 单元测试
 *
 * 测试架构师Agent的核心功能：
 * 1. 生成OpenAPI契约文档
 * 2. 生成PostgreSQL Schema
 * 3. 验证契约和Schema格式
 * 4. AI调用异常处理
 */
@ExtendWith(MockitoExtension.class)
class ArchitectAgentImplTest {

    @Mock
    private AIProviderFactory aiProviderFactory;

    @Mock
    private AIProvider aiProvider;

    @Mock
    private UniaixAIProvider uniaixAIProvider;

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private BlueprintPromptBuilder blueprintPromptBuilder;

    @Mock
    private BlueprintValidator blueprintValidator;

    @Mock
    private G3HookPipeline hookPipeline;

    @Mock
    private ProjectService projectService;

    @Mock
    private Consumer<G3LogEntry> logConsumer;

    @InjectMocks
    private ArchitectAgentImpl architectAgent;

    private G3JobEntity testJob;
    private UUID testJobId;

    @BeforeEach
    void setUp() {
        testJobId = UUID.randomUUID();
        testJob = G3JobEntity.builder()
                .id(testJobId)
                .requirement("创建一个用户管理系统，包含用户注册、登录、个人信息管理功能")
                .status(G3JobEntity.Status.PLANNING.getValue())
                .build();

        // 说明：部分用例不触发 design()/execute()，这里用 lenient 避免 Strict Stubs 因“未使用桩”报错。
        lenient().when(promptTemplateService.architectContractTemplate()).thenReturn("OpenAPI %s");
        lenient().when(promptTemplateService.architectSchemaTemplate()).thenReturn("PostgreSQL %s %s");
        lenient().when(hookPipeline.wrapProvider(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(projectService.findByAppSpecId(any())).thenReturn(null);
    }

    /**
     * 测试：成功生成OpenAPI契约和PostgreSQL Schema
     * 期望：返回成功结果，包含有效的YAML和SQL
     */
    @Test
    void design_shouldGenerateValidContractAndSchema() throws AIProvider.AIException {
        // GIVEN
        String validYaml = """
                openapi: '3.0.3'
                info:
                  title: User Management API
                  version: '1.0'
                paths:
                  /users:
                    get:
                      summary: 获取用户列表
                      responses:
                        '200':
                          description: Success
                components:
                  schemas:
                    User:
                      type: object
                      properties:
                        id:
                          type: string
                          format: uuid
                        username:
                          type: string
                        created_at:
                          type: string
                          format: date-time
                """;

        String validSql = """
                -- DDL for User Management
                CREATE TABLE IF NOT EXISTS users (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    username VARCHAR(100) NOT NULL UNIQUE,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                CREATE INDEX idx_users_username ON users(username);
                CREATE INDEX idx_users_email ON users(email);
                """;

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);

        // Mock AI responses
        when(aiProvider.generate(contains("OpenAPI"), any()))
                .thenReturn(AIProvider.AIResponse.builder()
                        .content(validYaml)
                        .model("test-model")
                        .build());

        when(aiProvider.generate(contains("PostgreSQL"), any()))
                .thenReturn(AIProvider.AIResponse.builder()
                        .content(validSql)
                        .model("test-model")
                        .build());

        // WHEN
        IArchitectAgent.ArchitectResult result = architectAgent.design(testJob, logConsumer);

        // THEN
        assertTrue(result.success(), "设计应该成功");
        assertNotNull(result.contractYaml(), "契约YAML不应为空");
        assertNotNull(result.dbSchemaSql(), "数据库Schema不应为空");
        assertNull(result.errorMessage(), "不应有错误信息");

        // 验证YAML包含必需字段
        assertTrue(result.contractYaml().contains("openapi:"), "应包含openapi字段");
        assertTrue(result.contractYaml().contains("paths:"), "应包含paths字段");
        assertTrue(result.contractYaml().contains("info:"), "应包含info字段");

        // 验证SQL包含必需语句
        assertTrue(result.dbSchemaSql().contains("CREATE TABLE"), "应包含CREATE TABLE语句");
        assertTrue(result.dbSchemaSql().contains("PRIMARY KEY"), "应包含PRIMARY KEY");
        assertTrue(result.dbSchemaSql().contains("created_at"), "应包含created_at字段");
        assertTrue(result.dbSchemaSql().contains("updated_at"), "应包含updated_at字段");

        // 验证日志输出
        verify(logConsumer, atLeastOnce()).accept(argThat((G3LogEntry log) ->
                log.getRole().equals(G3LogEntry.Role.ARCHITECT.getValue())
        ));
    }

    /**
     * 测试：execute方法应创建契约和Schema产物
     * 期望：返回2个产物（openapi.yaml和schema.sql）
     */
    @Test
    void execute_shouldCreateContractAndSchemaArtifacts() throws Exception {
        // GIVEN
        String validYaml = """
                openapi: '3.0.3'
                info:
                  title: Test API
                  version: '1.0'
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
                """;

        String validSql = """
                -- DDL for Test
                CREATE TABLE IF NOT EXISTS test (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid()
                );
                """;

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenReturn(AIProvider.AIResponse.builder()
                        .content(validYaml)
                        .model("test-model")
                        .build())
                .thenReturn(AIProvider.AIResponse.builder()
                        .content(validSql)
                        .model("test-model")
                        .build());

        // WHEN
        List<G3ArtifactEntity> artifacts = architectAgent.execute(testJob, logConsumer);

        // THEN
        assertTrue(artifacts.size() >= 2, "应至少生成2个产物");

        G3ArtifactEntity contractArtifact = artifacts.stream()
                .filter(a -> a.getFilePath().contains("openapi.yaml"))
                .findFirst()
                .orElse(null);
        assertNotNull(contractArtifact, "应包含契约产物");
        assertEquals(testJobId, contractArtifact.getJobId(), "产物应关联正确的Job");
        assertEquals(G3ArtifactEntity.GeneratedBy.ARCHITECT.getValue(), contractArtifact.getGeneratedBy(), "产物应标记为ARCHITECT生成");

        G3ArtifactEntity schemaArtifact = artifacts.stream()
                .filter(a -> a.getFilePath().contains("schema.sql"))
                .findFirst()
                .orElse(null);
        assertNotNull(schemaArtifact, "应包含Schema产物");

        assertTrue(artifacts.stream().anyMatch(a -> a.getFilePath().contains("docs/task_plan.md")),
                "应包含任务计划文档");
        assertTrue(artifacts.stream().anyMatch(a -> a.getFilePath().contains("docs/findings.md")),
                "应包含分析文档");
        assertTrue(artifacts.stream().anyMatch(a -> a.getFilePath().contains("docs/progress.md")),
                "应包含进度文档");
    }

    /**
     * 测试：AI提供商不可用时应返回失败
     * 期望：返回失败结果，包含错误信息
     */
    @Test
    void design_whenAIProviderUnavailable_shouldReturnFailure() {
        // GIVEN
        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(false);

        // WHEN
        IArchitectAgent.ArchitectResult result = architectAgent.design(testJob, logConsumer);

        // THEN
        assertFalse(result.success(), "应返回失败");
        assertNotNull(result.errorMessage(), "应包含错误信息");
        assertTrue(result.errorMessage().contains("AI提供商不可用"), "错误信息应说明原因");
        assertNull(result.contractYaml(), "失败时不应返回契约");
        assertNull(result.dbSchemaSql(), "失败时不应返回Schema");
    }

    /**
     * 测试：AI调用异常时应捕获并返回失败
     * 期望：返回失败结果，记录错误日志
     */
    @Test
    void design_whenAICallFails_shouldReturnFailure() throws AIProvider.AIException {
        // GIVEN
        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenThrow(new AIProvider.AIException("API调用超时", "test-provider"));

        // WHEN
        IArchitectAgent.ArchitectResult result = architectAgent.design(testJob, logConsumer);

        // THEN
        assertFalse(result.success(), "应返回失败");
        assertNotNull(result.errorMessage(), "应包含错误信息");
        assertTrue(result.errorMessage().contains("AI调用失败"), "错误信息应说明原因");

        // 验证错误日志
        verify(logConsumer, atLeastOnce()).accept(argThat((G3LogEntry log) ->
                log.getLevel().equals(G3LogEntry.Level.ERROR.getValue())
        ));
    }

    /**
     * 测试：validateContract方法应验证OpenAPI格式
     * 期望：有效的YAML返回true，无效的返回false
     */
    @Test
    void validateContract_shouldValidateOpenAPIFormat() {
        // 有效的契约
        String validYaml = """
                openapi: '3.0.3'
                info:
                  title: Test API
                  version: '1.0'
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
                """;
        assertTrue(architectAgent.validateContract(validYaml), "有效的契约应通过验证");

        // 缺少openapi字段
        String missingOpenapi = """
                info:
                  title: Test API
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
                """;
        assertFalse(architectAgent.validateContract(missingOpenapi), "缺少openapi字段应验证失败");

        // 缺少paths字段
        String missingPaths = """
                openapi: '3.0.3'
                info:
                  title: Test API
                  version: '1.0'
                """;
        assertFalse(architectAgent.validateContract(missingPaths), "缺少paths字段应验证失败");

        // 空值或null
        assertFalse(architectAgent.validateContract(null), "null应验证失败");
        assertFalse(architectAgent.validateContract(""), "空字符串应验证失败");
        assertFalse(architectAgent.validateContract("   "), "空白字符串应验证失败");

        // 无效的YAML格式
        String invalidYaml = "this is not valid yaml: [[[";
        assertFalse(architectAgent.validateContract(invalidYaml), "无效YAML应验证失败");
    }

    /**
     * 测试：validateSchema方法应验证SQL格式
     * 期望：包含CREATE TABLE和PRIMARY KEY的SQL通过验证
     */
    @Test
    void validateSchema_shouldValidateSQLFormat() {
        // 有效的Schema
        String validSql = """
                CREATE TABLE IF NOT EXISTS users (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    username VARCHAR(100) NOT NULL
                );
                """;
        assertTrue(architectAgent.validateSchema(validSql), "有效的Schema应通过验证");

        // 缺少CREATE TABLE
        String missingCreate = """
                ALTER TABLE users ADD COLUMN email VARCHAR(255);
                """;
        assertFalse(architectAgent.validateSchema(missingCreate), "缺少CREATE TABLE应验证失败");

        // 缺少PRIMARY KEY
        String missingPrimaryKey = """
                CREATE TABLE users (
                    id UUID,
                    username VARCHAR(100)
                );
                """;
        assertFalse(architectAgent.validateSchema(missingPrimaryKey), "缺少PRIMARY KEY应验证失败");

        // 空值或null
        assertFalse(architectAgent.validateSchema(null), "null应验证失败");
        assertFalse(architectAgent.validateSchema(""), "空字符串应验证失败");
        assertFalse(architectAgent.validateSchema("   "), "空白字符串应验证失败");
    }

    /**
     * 测试：AI返回markdown包裹的内容应正确提取
     * 期望：移除markdown标记，返回纯YAML/SQL
     */
    @Test
    void design_shouldExtractContentFromMarkdown() throws AIProvider.AIException {
        // GIVEN
        String markdownYaml = """
                ```yaml
                openapi: '3.0.3'
                info:
                  title: Test API
                  version: '1.0'
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
                ```
                """;

        String markdownSql = """
                ```sql
                CREATE TABLE IF NOT EXISTS test (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid()
                );
                ```
                """;

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(contains("OpenAPI"), any()))
                .thenReturn(AIProvider.AIResponse.builder()
                        .content(markdownYaml)
                        .model("test-model")
                        .build());
        when(aiProvider.generate(contains("PostgreSQL"), any()))
                .thenReturn(AIProvider.AIResponse.builder()
                        .content(markdownSql)
                        .model("test-model")
                        .build());

        // WHEN
        IArchitectAgent.ArchitectResult result = architectAgent.design(testJob, logConsumer);

        // THEN
        assertTrue(result.success(), "应成功提取内容");
        assertFalse(result.contractYaml().contains("```"), "YAML不应包含markdown标记");
        assertFalse(result.dbSchemaSql().contains("```"), "SQL不应包含markdown标记");
        assertTrue(result.contractYaml().startsWith("openapi:"), "YAML应以openapi开头");
        assertTrue(result.dbSchemaSql().contains("CREATE TABLE"), "SQL应包含CREATE TABLE");
    }

    /**
     * 测试：契约格式验证失败时应尝试修复
     * 期望：记录警告日志，尝试自动修复
     */
    @Test
    void design_whenContractValidationFails_shouldAttemptFix() throws AIProvider.AIException {
        // GIVEN: AI返回缺少openapi字段的YAML
        String invalidYaml = """
                info:
                  title: Test API
                  version: '1.0'
                paths:
                  /test:
                    get:
                      responses:
                        '200':
                          description: OK
                """;

        String validSql = """
                CREATE TABLE IF NOT EXISTS test (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid()
                );
                """;

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(contains("OpenAPI"), any()))
                .thenReturn(AIProvider.AIResponse.builder()
                        .content(invalidYaml)
                        .model("test-model")
                        .build());
        when(aiProvider.generate(contains("PostgreSQL"), any()))
                .thenReturn(AIProvider.AIResponse.builder()
                        .content(validSql)
                        .model("test-model")
                        .build());

        // WHEN
        IArchitectAgent.ArchitectResult result = architectAgent.design(testJob, logConsumer);

        // THEN
        assertTrue(result.success(), "修复后应成功");
        assertTrue(result.contractYaml().toLowerCase().startsWith("openapi:"), "修复后应包含openapi字段");

        // 验证警告日志
        verify(logConsumer, atLeastOnce()).accept(argThat((G3LogEntry log) ->
                log.getLevel().equals(G3LogEntry.Level.WARN.getValue()) &&
                        log.getMessage().contains("契约格式验证失败")
        ));
    }

    /**
     * 测试：Agent基础属性
     * 期望：返回正确的名称、角色、描述
     */
    @Test
    void agentProperties_shouldReturnCorrectValues() {
        assertEquals("ArchitectAgent", architectAgent.getName(), "应返回正确的名称");
        assertEquals(G3LogEntry.Role.ARCHITECT, architectAgent.getRole(), "应返回ARCHITECT角色");
        assertNotNull(architectAgent.getDescription(), "应有描述信息");
        assertTrue(architectAgent.getDescription().contains("架构师"), "描述应提到架构师");
    }

    /**
     * 测试：execute失败时应抛出G3AgentException
     * 期望：抛出异常，包含正确的错误信息
     */
    @Test
    void execute_whenDesignFails_shouldThrowException() {
        // GIVEN
        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(false);

        // WHEN & THEN
        IArchitectAgent.G3AgentException exception = assertThrows(
                IArchitectAgent.G3AgentException.class,
                () -> architectAgent.execute(testJob, logConsumer),
                "execute失败应抛出G3AgentException"
        );

        assertEquals("ArchitectAgent", exception.getAgentName(), "异常应包含Agent名称");
        assertEquals(G3LogEntry.Role.ARCHITECT, exception.getRole(), "异常应包含Agent角色");
        assertNotNull(exception.getMessage(), "异常应包含错误信息");
    }

    @Test
    void resolveProvider_shouldPreferUniaixWhenG3ProviderIsClaude() {
        // GIVEN
        ReflectionTestUtils.setField(architectAgent, "g3Provider", "claude");
        when(uniaixAIProvider.isAvailable()).thenReturn(true);

        // WHEN
        AIProvider provider = ReflectionTestUtils.invokeMethod(architectAgent, "resolveProvider", testJob);

        // THEN
        assertSame(uniaixAIProvider, provider);
        verifyNoInteractions(aiProviderFactory, projectService);
    }

    /**
     * 测试：G3配置为ECA Gateway时应命中对应Provider
     * 期望：返回ECA Gateway Provider
     */
    @Test
    void resolveProvider_shouldPreferEcaGatewayWhenG3ProviderIsEcaGateway() {
        // GIVEN
        ReflectionTestUtils.setField(architectAgent, "g3Provider", "eca-gateway");
        when(aiProviderFactory.getProviderByName("eca-gateway")).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);

        // WHEN
        AIProvider provider = ReflectionTestUtils.invokeMethod(architectAgent, "resolveProvider", testJob);

        // THEN
        assertSame(aiProvider, provider);
        verify(aiProviderFactory).getProviderByName("eca-gateway");
        verifyNoInteractions(projectService);
    }
}
