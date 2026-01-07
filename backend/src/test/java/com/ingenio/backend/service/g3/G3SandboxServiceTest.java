package com.ingenio.backend.service.g3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.mockito.Mockito;

/**
 * G3SandboxService 集成测试
 * 测试与E2B沙箱的交互逻辑
 *
 * 测试覆盖：
 * 1. 沙箱创建和管理
 * 2. 文件同步
 * 3. Maven编译执行
 * 4. 编译错误解析
 * 5. 完整验证流程
 */
class G3SandboxServiceTest {

    private G3SandboxService sandboxService;
    private MockRestServiceServer mockServer;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    private G3JobEntity testJob;
    private UUID testJobId;
    private List<G3LogEntry> capturedLogs;
    private Consumer<G3LogEntry> logConsumer;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();

        // Phase 5: Mock G3ValidationAdapter和ValidationService（用于构造函数注入）
        G3ValidationAdapter mockAdapter = Mockito.mock(G3ValidationAdapter.class);
        com.ingenio.backend.service.ValidationService mockValidationService =
                Mockito.mock(com.ingenio.backend.service.ValidationService.class);

        sandboxService = new G3SandboxService(
                restTemplate,
                objectMapper,
                mockAdapter,
                mockValidationService
        );

        // 设置配置
        ReflectionTestUtils.setField(sandboxService, "openLovableBaseUrl", "http://localhost:3001");
        ReflectionTestUtils.setField(sandboxService, "compileTimeout", 300);
        ReflectionTestUtils.setField(sandboxService, "sandboxProvider", "e2b");

        // 初始化MockRestServiceServer
        mockServer = MockRestServiceServer.createServer(restTemplate);

        // 测试数据
        testJobId = UUID.randomUUID();
        testJob = G3JobEntity.builder()
                .id(testJobId)
                .requirement("创建用户管理系统")
                .currentRound(0)
                .build();

        // 捕获日志
        capturedLogs = new ArrayList<>();
        logConsumer = capturedLogs::add;
    }

    /**
     * 测试：成功创建沙箱
     * 期望：返回沙箱信息并更新Job
     */
    @Test
    void createSandbox_shouldCreateAndReturnInfo() {
        // GIVEN
        String expectedSandboxId = "sbx_test_123";
        String expectedUrl = "https://test.e2b.dev";
        String responseBody = """
                {
                    "sandboxId": "%s",
                    "url": "%s",
                    "provider": "e2b"
                }
                """.formatted(expectedSandboxId, expectedUrl);

        mockServer.expect(requestTo("http://localhost:3001/api/create-ai-sandbox-v2"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // WHEN
        G3SandboxService.SandboxInfo info = sandboxService.createSandbox(testJob, logConsumer);

        // THEN
        assertNotNull(info);
        assertEquals(expectedSandboxId, info.sandboxId());
        assertEquals(expectedUrl, info.url());
        assertEquals("e2b", info.provider());

        // 验证Job已更新
        assertEquals(expectedSandboxId, testJob.getSandboxId());
        assertEquals(expectedUrl, testJob.getSandboxUrl());

        // 验证日志
        assertTrue(capturedLogs.stream().anyMatch(log -> log.getMessage().contains("沙箱创建成功")));

        mockServer.verify();
    }

    /**
     * 测试：创建沙箱失败
     * 期望：抛出异常
     */
    @Test
    void createSandbox_whenApiFails_shouldThrowException() {
        // GIVEN
        mockServer.expect(requestTo("http://localhost:3001/api/create-ai-sandbox-v2"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        // WHEN & THEN
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> sandboxService.createSandbox(testJob, logConsumer));

        assertTrue(exception.getMessage().contains("沙箱创建失败"));
        mockServer.verify();
    }

    /**
     * 测试：同步文件到沙箱
     * 期望：调用API并记录日志
     */
    @Test
    void syncFiles_shouldSyncAllFiles() {
        // GIVEN
        String sandboxId = "sbx_test_123";
        List<G3ArtifactEntity> artifacts = List.of(
                createArtifact("User.java", "public class User {}"),
                createArtifact("UserService.java", "public class UserService {}")
        );

        mockServer.expect(requestTo("http://localhost:3001/api/sandbox/write-files"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.sandboxId").value(sandboxId))
                .andExpect(jsonPath("$.files.length()").value(2))
                .andRespond(withSuccess());

        // WHEN
        sandboxService.syncFiles(sandboxId, artifacts, logConsumer);

        // THEN
        assertTrue(capturedLogs.stream().anyMatch(log -> log.getMessage().contains("文件同步完成")));
        mockServer.verify();
    }

    /**
     * 测试：同步文件失败
     * 期望：抛出异常
     */
    @Test
    void syncFiles_whenApiFails_shouldThrowException() {
        // GIVEN
        String sandboxId = "sbx_test_123";
        List<G3ArtifactEntity> artifacts = List.of(createArtifact("User.java", "code"));

        mockServer.expect(requestTo("http://localhost:3001/api/sandbox/write-files"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        // WHEN & THEN
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> sandboxService.syncFiles(sandboxId, artifacts, logConsumer));

        assertTrue(exception.getMessage().contains("文件同步失败"));
        mockServer.verify();
    }

    /**
     * 测试：Maven编译成功
     * 期望：返回成功结果
     */
    @Test
    void runMavenBuild_whenCompileSucceeds_shouldReturnSuccessResult() {
        // GIVEN
        String sandboxId = "sbx_test_123";
        String responseBody = """
                {
                    "exitCode": 0,
                    "stdout": "BUILD SUCCESS",
                    "stderr": ""
                }
                """;

        mockServer.expect(requestTo("http://localhost:3001/api/sandbox/execute"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.sandboxId").value(sandboxId))
                .andExpect(jsonPath("$.command").value("cd /home/user/app && mvn compile -e -B --no-transfer-progress 2>&1"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // WHEN
        G3SandboxService.CompileResult result = sandboxService.runMavenBuild(sandboxId, logConsumer);

        // THEN
        assertTrue(result.success());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("BUILD SUCCESS"));
        assertTrue(result.errors().isEmpty());

        // 验证日志
        assertTrue(capturedLogs.stream().anyMatch(log -> log.getMessage().contains("Maven编译成功")));

        mockServer.verify();
    }

    /**
     * 测试：Maven编译失败
     * 期望：返回失败结果并解析错误
     */
    @Test
    void runMavenBuild_whenCompileFails_shouldReturnFailureWithErrors() {
        // GIVEN
        String sandboxId = "sbx_test_123";
        String stderr = """
                [ERROR] /home/user/app/User.java:[10,5] error: cannot find symbol
                  symbol:   class UnknownType
                  location: class User
                [ERROR] /home/user/app/User.java:[15,10] error: ';' expected
                """;

        // JSON转义stderr字符串
        String stderrJson;
        try {
            stderrJson = objectMapper.writeValueAsString(stderr);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String responseBody = """
                {
                    "exitCode": 1,
                    "stdout": "",
                    "stderr": %s
                }
                """.formatted(stderrJson);

        mockServer.expect(requestTo("http://localhost:3001/api/sandbox/execute"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // WHEN
        G3SandboxService.CompileResult result = sandboxService.runMavenBuild(sandboxId, logConsumer);

        // THEN
        assertFalse(result.success());
        assertEquals(1, result.exitCode());
        assertEquals(2, result.errors().size());

        // 验证第一个错误
        G3SandboxService.CompileError error1 = result.errors().get(0);
        assertTrue(error1.file().contains("User.java"));
        assertEquals(10, error1.line());
        assertEquals(5, error1.column());
        assertTrue(error1.message().contains("cannot find symbol"));

        mockServer.verify();
    }

    /**
     * 测试：完整验证流程（创建沙箱 → 同步文件 → 编译）
     * 期望：执行完整流程并返回验证结果
     */
    @Test
    void validate_shouldExecuteFullWorkflow() {
        // GIVEN: Job没有沙箱ID，需要创建
        List<G3ArtifactEntity> artifacts = List.of(createArtifact("User.java", "code"));

        // Mock 1: 创建沙箱
        String sandboxId = "sbx_test_123";
        String createResponse = """
                {
                    "sandboxId": "%s",
                    "url": "https://test.e2b.dev",
                    "provider": "e2b"
                }
                """.formatted(sandboxId);

        mockServer.expect(requestTo("http://localhost:3001/api/create-ai-sandbox-v2"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(createResponse, MediaType.APPLICATION_JSON));

        // Mock 2: 同步文件
        mockServer.expect(requestTo("http://localhost:3001/api/sandbox/write-files"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        // Mock 3: 执行编译（成功）
        String compileResponse = """
                {
                    "exitCode": 0,
                    "stdout": "BUILD SUCCESS",
                    "stderr": ""
                }
                """;

        mockServer.expect(requestTo("http://localhost:3001/api/sandbox/execute"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(compileResponse, MediaType.APPLICATION_JSON));

        // WHEN
        G3ValidationResultEntity result = sandboxService.validate(testJob, artifacts, logConsumer);

        // THEN
        assertNotNull(result);
        assertTrue(result.getPassed());
        assertEquals(testJobId, result.getJobId());
        assertEquals(0, result.getRound());
        assertEquals("mvn compile -e -B", result.getCommand());
        assertEquals(0, result.getExitCode());

        // 验证沙箱已创建并关联到Job
        assertEquals(sandboxId, testJob.getSandboxId());

        mockServer.verify();
    }

    /**
     * 测试：验证流程（已有沙箱ID）
     * 期望：直接使用现有沙箱
     */
    @Test
    void validate_withExistingSandbox_shouldReuseIt() {
        // GIVEN: Job已有沙箱ID
        String existingSandboxId = "sbx_existing_456";
        testJob.setSandboxId(existingSandboxId);

        List<G3ArtifactEntity> artifacts = List.of(createArtifact("User.java", "code"));

        // Mock 1: 同步文件（不需要创建沙箱）
        mockServer.expect(requestTo("http://localhost:3001/api/sandbox/write-files"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.sandboxId").value(existingSandboxId))
                .andRespond(withSuccess());

        // Mock 2: 执行编译
        String compileResponse = """
                {
                    "exitCode": 0,
                    "stdout": "BUILD SUCCESS",
                    "stderr": ""
                }
                """;

        mockServer.expect(requestTo("http://localhost:3001/api/sandbox/execute"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(compileResponse, MediaType.APPLICATION_JSON));

        // WHEN
        G3ValidationResultEntity result = sandboxService.validate(testJob, artifacts, logConsumer);

        // THEN
        assertTrue(result.getPassed());

        // 验证没有调用创建沙箱API（只调用了2次API：sync + compile）
        mockServer.verify();
    }

    /**
     * 测试：解析Maven编译错误
     * 期望：正确提取文件、行号、列号、消息
     */
    @Test
    void parseCompilerErrors_shouldParseAllFormats() {
        // GIVEN: Maven格式和javac格式的混合输出
        String output = """
                [ERROR] /app/User.java:[10,5] error: cannot find symbol
                User.java:15: error: ';' expected
                [ERROR] /app/Service.java:[20,15] warning: unchecked cast
                Service.java:25: warning: deprecated API
                """;

        // WHEN
        List<G3SandboxService.CompileError> errors = sandboxService.parseCompilerErrors(output);

        // THEN
        assertEquals(4, errors.size());

        // Maven格式错误
        G3SandboxService.CompileError error1 = errors.get(0);
        assertTrue(error1.file().contains("User.java"));
        assertEquals(10, error1.line());
        assertEquals(5, error1.column());
        assertTrue(error1.message().contains("cannot find symbol"));
        assertEquals("error", error1.severity());

        // javac格式错误
        G3SandboxService.CompileError error2 = errors.get(1);
        assertTrue(error2.file().contains("User.java"));
        assertEquals(15, error2.line());
        assertEquals(0, error2.column()); // javac格式没有列号
        assertTrue(error2.message().contains("';' expected"));

        // Maven格式警告
        G3SandboxService.CompileError error3 = errors.get(2);
        assertEquals("warning", error3.severity());
        assertEquals(20, error3.line());
    }

    /**
     * 测试：解析空输出
     * 期望：返回空列表
     */
    @Test
    void parseCompilerErrors_withEmptyOutput_shouldReturnEmptyList() {
        // WHEN
        List<G3SandboxService.CompileError> errors1 = sandboxService.parseCompilerErrors(null);
        List<G3SandboxService.CompileError> errors2 = sandboxService.parseCompilerErrors("");
        List<G3SandboxService.CompileError> errors3 = sandboxService.parseCompilerErrors("   ");

        // THEN
        assertTrue(errors1.isEmpty());
        assertTrue(errors2.isEmpty());
        assertTrue(errors3.isEmpty());
    }

    /**
     * 测试：销毁沙箱
     * 期望：调用API销毁
     */
    @Test
    void destroySandbox_shouldCallApi() {
        // GIVEN
        String sandboxId = "sbx_test_123";

        mockServer.expect(requestTo("http://localhost:3001/api/sandbox/kill"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.sandboxId").value(sandboxId))
                .andRespond(withSuccess());

        // WHEN
        sandboxService.destroySandbox(sandboxId);

        // THEN
        mockServer.verify();
    }

    /**
     * 测试：销毁null沙箱
     * 期望：不调用API
     */
    @Test
    void destroySandbox_withNullId_shouldDoNothing() {
        // WHEN
        sandboxService.destroySandbox(null);
        sandboxService.destroySandbox("");
        sandboxService.destroySandbox("   ");

        // THEN: 不应调用任何API
        mockServer.verify(); // 验证没有未匹配的请求
    }

    /**
     * 测试：检查沙箱存活状态（存活）
     * 期望：返回true
     */
    @Test
    void isSandboxAlive_whenAlive_shouldReturnTrue() {
        // GIVEN
        String sandboxId = "sbx_test_123";
        String responseBody = """
                {
                    "alive": true
                }
                """;

        mockServer.expect(requestTo("http://localhost:3001/api/sandbox-status?sandboxId=" + sandboxId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // WHEN
        boolean alive = sandboxService.isSandboxAlive(sandboxId);

        // THEN
        assertTrue(alive);
        mockServer.verify();
    }

    /**
     * 测试：检查沙箱存活状态（已死亡）
     * 期望：返回false
     */
    @Test
    void isSandboxAlive_whenDead_shouldReturnFalse() {
        // GIVEN
        String sandboxId = "sbx_test_123";
        String responseBody = """
                {
                    "alive": false
                }
                """;

        mockServer.expect(requestTo("http://localhost:3001/api/sandbox-status?sandboxId=" + sandboxId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // WHEN
        boolean alive = sandboxService.isSandboxAlive(sandboxId);

        // THEN
        assertFalse(alive);
        mockServer.verify();
    }

    /**
     * 测试：检查沙箱存活状态（API失败）
     * 期望：返回false
     */
    @Test
    void isSandboxAlive_whenApiFails_shouldReturnFalse() {
        // GIVEN
        String sandboxId = "sbx_test_123";

        mockServer.expect(requestTo("http://localhost:3001/api/sandbox-status?sandboxId=" + sandboxId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        // WHEN
        boolean alive = sandboxService.isSandboxAlive(sandboxId);

        // THEN
        assertFalse(alive);
        mockServer.verify();
    }

    /**
     * 测试：生成pom.xml模板
     * 期望：生成有效的Maven配置
     */
    @Test
    void generatePomXml_shouldGenerateValidTemplate() {
        // WHEN
        String pomXml = sandboxService.generatePomXml("com.test", "test-app", "1.0.0");

        // THEN
        assertNotNull(pomXml);
        assertTrue(pomXml.contains("com.test"));
        assertTrue(pomXml.contains("test-app"));
        assertTrue(pomXml.contains("1.0.0"));
        assertTrue(pomXml.contains("spring-boot-starter-web"));
        assertTrue(pomXml.contains("mybatis-plus-spring-boot3-starter"));
        assertTrue(pomXml.contains("postgresql"));
        assertTrue(pomXml.contains("<java.version>17</java.version>"));
    }

    // ========== Helper Methods ==========

    private G3ArtifactEntity createArtifact(String fileName, String content) {
        return G3ArtifactEntity.builder()
                .id(UUID.randomUUID())
                .jobId(testJobId)
                .fileName(fileName)
                .filePath("src/main/java/com/test/" + fileName)
                .content(content)
                .language("java")
                .artifactType("SERVICE")
                .version(1)
                .hasErrors(false)
                .generatedBy(G3ArtifactEntity.GeneratedBy.BACKEND_CODER.getValue())
                .generationRound(0)
                .build();
    }
}
