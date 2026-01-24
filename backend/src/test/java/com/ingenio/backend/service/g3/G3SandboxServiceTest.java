package com.ingenio.backend.service.g3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.config.G3HookProperties;
import com.ingenio.backend.config.G3ToolsetProperties;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import com.ingenio.backend.mapper.g3.G3JobMapper;
import com.ingenio.backend.service.g3.hooks.G3HookPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
                com.ingenio.backend.service.ValidationService mockValidationService = Mockito
                                .mock(com.ingenio.backend.service.ValidationService.class);

                sandboxService = new G3SandboxService(
                                restTemplate,
                                objectMapper,
                                mockAdapter,
                                mockValidationService,
                                Mockito.mock(org.springframework.data.redis.core.RedisTemplate.class));

                // 设置配置
                ReflectionTestUtils.setField(sandboxService, "openLovableBaseUrl", "http://localhost:3001");
                ReflectionTestUtils.setField(sandboxService, "compileTimeout", 300);
                ReflectionTestUtils.setField(sandboxService, "sandboxProvider", "e2b");
                // 单测中避免真实 sleep，提升运行速度
                ReflectionTestUtils.setField(sandboxService, "envErrorRetryDelayMs", 0);

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
                                createArtifact("UserService.java", "public class UserService {}"));

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
                                .andExpect(jsonPath("$.command").value("mvn compile -e -B --no-transfer-progress"))
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
         * 测试：OpenLovable 执行器只返回低信息错误（Command failed）
         * 期望：识别为环境类错误，避免误交给 Coach 修复
         */
        @Test
        void runMavenBuild_whenCommandFailedWithoutMavenOutput_shouldClassifyAsEnvironmentError() {
                // GIVEN
                String sandboxId = "sbx_test_123";
                String responseBody = """
                                {
                                    "exitCode": 1,
                                    "stdout": "",
                                    "stderr": "",
                                    "message": "Command failed"
                                }
                                """;

                mockServer.expect(requestTo("http://localhost:3001/api/sandbox/execute"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

                // WHEN
                G3SandboxService.CompileResult result = sandboxService.runMavenBuild(sandboxId, logConsumer);

                // THEN
                assertFalse(result.success());
                assertTrue(result.isEnvironmentError());
                assertEquals(1, result.errors().size());
                assertEquals("environment", result.errors().get(0).severity());
                assertTrue(result.errors().get(0).message().contains("OpenLovable"));

                // 验证日志中明确提示环境错误
                assertTrue(capturedLogs.stream().anyMatch(log -> log.getMessage().contains("检测到环境类错误")));

                mockServer.verify();
        }

        /**
         * 测试：OpenLovable E2BProvider 可能返回 exitCode=0，但在 stdout 中输出真实 returncode
         * 期望：能从 "Return code: N" 解析真实退出码，并按失败处理
         */
        @Test
        void runMavenBuild_whenExitCodeZeroButReturnCodeNonZeroInOutput_shouldTreatAsFailure() {
                // GIVEN
                String sandboxId = "sbx_test_123";
                String stdout = """
                                STDOUT:
                                [ERROR] /home/user/app/User.java:[10,5] error: cannot find symbol
                                  symbol:   class UnknownType
                                  location: class User

                                Return code: 1
                                """;

                // JSON转义stdout字符串
                String stdoutJson;
                try {
                        stdoutJson = objectMapper.writeValueAsString(stdout);
                } catch (Exception e) {
                        throw new RuntimeException(e);
                }

                String responseBody = """
                                {
                                    "exitCode": 0,
                                    "stdout": %s,
                                    "stderr": ""
                                }
                                """.formatted(stdoutJson);

                mockServer.expect(requestTo("http://localhost:3001/api/sandbox/execute"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

                // WHEN
                G3SandboxService.CompileResult result = sandboxService.runMavenBuild(sandboxId, logConsumer);

                // THEN
                assertFalse(result.success());
                assertEquals(1, result.exitCode());
                assertFalse(result.errors().isEmpty());
                assertTrue(result.errors().get(0).file().contains("User.java"));

                mockServer.verify();
        }

        /**
         * 测试：OpenLovable 调用异常（例如 500 / 连接中断）
         * 期望：判定为环境类错误，避免触发 Coach 修复逻辑
         */
        @Test
        void runMavenBuild_whenOpenLovableRequestFails_shouldClassifyAsEnvironmentError() {
                // GIVEN
                String sandboxId = "sbx_test_123";

                mockServer.expect(requestTo("http://localhost:3001/api/sandbox/execute"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withServerError());

                // WHEN
                G3SandboxService.CompileResult result = sandboxService.runMavenBuild(sandboxId, logConsumer);

                // THEN
                assertFalse(result.success());
                assertTrue(result.isEnvironmentError());
                assertEquals(1, result.errors().size());
                assertEquals("environment", result.errors().get(0).severity());
                assertTrue(result.errors().get(0).message().contains("OpenLovable"));

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
                assertEquals("mvn compile -e -B --no-transfer-progress", result.getCommand());
                assertEquals(0, result.getExitCode());

                // 验证沙箱已创建并关联到Job
                assertEquals(sandboxId, testJob.getSandboxId());

                mockServer.verify();
        }

        /**
         * 测试：远程沙箱编译返回低信息环境错误（Command failed）时，自动重建沙箱并重试
         *
         * 期望：
         * - 首次编译失败被识别为环境错误
         * - 调用 /api/sandbox/kill 重置远程沙箱
         * - 重新创建沙箱并再次编译成功
         */
        @Test
        void validate_whenRemoteCommandFailed_shouldResetSandboxAndRetry() {
                // GIVEN
                List<G3ArtifactEntity> artifacts = List.of(createArtifact("User.java", "code"));

                String sandboxId1 = "sbx_test_1";
                String sandboxId2 = "sbx_test_2";

                String createResponse1 = """
                                {
                                    "sandboxId": "%s",
                                    "url": "https://test.e2b.dev/1",
                                    "provider": "e2b"
                                }
                                """.formatted(sandboxId1);
                String createResponse2 = """
                                {
                                    "sandboxId": "%s",
                                    "url": "https://test.e2b.dev/2",
                                    "provider": "e2b"
                                }
                                """.formatted(sandboxId2);

                // 1) 创建沙箱 #1
                mockServer.expect(requestTo("http://localhost:3001/api/create-ai-sandbox-v2"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withSuccess(createResponse1, MediaType.APPLICATION_JSON));

                // 2) 同步文件 #1
                mockServer.expect(requestTo("http://localhost:3001/api/sandbox/write-files"))
                                .andExpect(method(HttpMethod.POST))
                                .andExpect(jsonPath("$.sandboxId").value(sandboxId1))
                                .andRespond(withSuccess());

                // 3) 编译失败（Command failed，无 Maven 详细输出）
                String commandFailedResponse = """
                                {
                                    "exitCode": 1,
                                    "stdout": "",
                                    "stderr": "",
                                    "message": "Command failed"
                                }
                                """;
                mockServer.expect(requestTo("http://localhost:3001/api/sandbox/execute"))
                                .andExpect(method(HttpMethod.POST))
                                .andExpect(jsonPath("$.sandboxId").value(sandboxId1))
                                .andRespond(withSuccess(commandFailedResponse, MediaType.APPLICATION_JSON));

                // 4) 重置远程沙箱（kill）
                mockServer.expect(requestTo("http://localhost:3001/api/sandbox/kill"))
                                .andExpect(method(HttpMethod.POST))
                                .andExpect(jsonPath("$.sandboxId").value(sandboxId1))
                                .andRespond(withSuccess());

                // 5) 创建沙箱 #2
                mockServer.expect(requestTo("http://localhost:3001/api/create-ai-sandbox-v2"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withSuccess(createResponse2, MediaType.APPLICATION_JSON));

                // 6) 同步文件 #2
                mockServer.expect(requestTo("http://localhost:3001/api/sandbox/write-files"))
                                .andExpect(method(HttpMethod.POST))
                                .andExpect(jsonPath("$.sandboxId").value(sandboxId2))
                                .andRespond(withSuccess());

                // 7) 编译成功
                String compileOk = """
                                {
                                    "exitCode": 0,
                                    "stdout": "BUILD SUCCESS",
                                    "stderr": ""
                                }
                                """;
                mockServer.expect(requestTo("http://localhost:3001/api/sandbox/execute"))
                                .andExpect(method(HttpMethod.POST))
                                .andExpect(jsonPath("$.sandboxId").value(sandboxId2))
                                .andRespond(withSuccess(compileOk, MediaType.APPLICATION_JSON));

                // WHEN
                G3ValidationResultEntity result = sandboxService.validate(testJob, artifacts, logConsumer);

                // THEN
                assertNotNull(result);
                assertTrue(result.getPassed());
                assertEquals(0, result.getExitCode());
                assertEquals(sandboxId2, testJob.getSandboxId());

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

                // Mock 0: 检查沙箱存活
                String statusResponse = """
                                {
                                    "active": true,
                                    "healthy": true,
                                    "sandboxData": {
                                        "sandboxId": "%s"
                                    }
                                }
                                """.formatted(existingSandboxId);

                mockServer.expect(requestTo("http://localhost:3001/api/sandbox-status"))
                                .andExpect(method(HttpMethod.GET))
                                .andRespond(withSuccess(statusResponse, MediaType.APPLICATION_JSON));

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

                // 验证没有调用创建沙箱API（仅检查状态 + 同步 + 编译）
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
                                    "active": true,
                                    "healthy": true,
                                    "sandboxData": {
                                        "sandboxId": "%s"
                                    }
                                }
                                """.formatted(sandboxId);

                mockServer.expect(requestTo("http://localhost:3001/api/sandbox-status"))
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
                                    "active": true,
                                    "healthy": false,
                                    "sandboxData": {
                                        "sandboxId": "%s"
                                    }
                                }
                                """.formatted(sandboxId);

                mockServer.expect(requestTo("http://localhost:3001/api/sandbox-status"))
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

                mockServer.expect(requestTo("http://localhost:3001/api/sandbox-status"))
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

/**
 * G3ToolsetService 单元测试
 * 覆盖：命令策略、文件读取上限与搜索过滤。
 */
class G3ToolsetServiceTest {

        private G3ToolsetService toolsetService;
        private G3ToolsetProperties toolsetProperties;
        private G3JobMapper jobMapper;
        private G3SandboxService sandboxService;
        private G3HookPipeline hookPipeline;

        @TempDir
        Path tempDir;

        @BeforeEach
        void setUp() {
                toolsetProperties = new G3ToolsetProperties();
                jobMapper = Mockito.mock(G3JobMapper.class);
                sandboxService = Mockito.mock(G3SandboxService.class);
                hookPipeline = new G3HookPipeline(new G3HookProperties(), List.of());
                toolsetService = new G3ToolsetService(toolsetProperties, jobMapper, sandboxService, hookPipeline);
                toolsetProperties.setWorkspaceRoot(tempDir.toString());
        }

        @Test
        void runSandboxCommand_whenCommandDenied_shouldBlock() {
                UUID jobId = UUID.randomUUID();
                G3ToolsetService.ToolCommandResult result = toolsetService.runSandboxCommand(
                                jobId,
                                "rm -rf /",
                                5,
                                entry -> {
                                });

                assertFalse(result.isSuccess());
                assertEquals("BLOCK", result.getPolicyDecision());
                assertTrue(result.getMessage().contains("命令被策略拒绝"));
        }

        @Test
        void runSandboxCommand_whenAllowed_shouldExecute() {
                UUID jobId = UUID.randomUUID();
                G3JobEntity job = G3JobEntity.builder()
                                .id(jobId)
                                .sandboxId("sbx_test")
                                .build();

                when(jobMapper.selectById(jobId)).thenReturn(job);
                when(sandboxService.executeCommand(eq("sbx_test"), eq("ls"), anyInt()))
                                .thenReturn(new G3SandboxService.SandboxCommandResult(0, "ok", "", 12));

                G3ToolsetService.ToolCommandResult result = toolsetService.runSandboxCommand(
                                jobId,
                                "ls",
                                5,
                                entry -> {
                                });

                assertTrue(result.isSuccess());
                assertEquals("ALLOW", result.getPolicyDecision());
                assertEquals("ok", result.getStdout());
        }

        @Test
        void readWorkspaceFile_whenFileTooLarge_shouldReject() throws Exception {
                toolsetProperties.setMaxFileSizeBytes(10);
                Path file = tempDir.resolve("demo.md");
                Files.writeString(file, "01234567890", StandardCharsets.UTF_8);

                G3ToolsetService.FileReadResult result = toolsetService.readWorkspaceFile("demo.md", 20);

                assertFalse(result.isSuccess());
                assertTrue(result.getMessage().contains("文件过大"));
        }

        @Test
        void searchWorkspace_shouldSkipExcludedAndDisallowedFiles() throws Exception {
                Path srcDir = tempDir.resolve("src");
                Files.createDirectories(srcDir);
                Files.writeString(srcDir.resolve("Main.java"), "hello Query", StandardCharsets.UTF_8);

                Path excludedDir = tempDir.resolve("node_modules");
                Files.createDirectories(excludedDir);
                Files.writeString(excludedDir.resolve("Ignore.java"), "hello Query", StandardCharsets.UTF_8);

                Files.writeString(tempDir.resolve("notes.txt"), "hello Query", StandardCharsets.UTF_8);

                toolsetProperties.setAllowFileExtensions(List.of(".java"));
                toolsetProperties.setExcludePathContains(List.of("/node_modules/"));

                G3ToolsetService.SearchResult result = toolsetService.searchWorkspace("Query", 10);

                assertTrue(result.isSuccess());
                assertEquals(1, result.getMatches().size());
                assertEquals("src/Main.java", result.getMatches().get(0).getFilePath());
        }

        @Test
        void summarizeWorkspaceFiles_shouldReturnSummary() throws Exception {
                Path srcDir = tempDir.resolve("src");
                Files.createDirectories(srcDir);
                Files.writeString(srcDir.resolve("Demo.java"), "package demo;\npublic class Demo {}\n",
                                StandardCharsets.UTF_8);

                var summary = toolsetService.summarizeWorkspaceFiles(
                                List.of("src/Demo.java"),
                                30,
                                500,
                                null,
                                null);

                assertTrue(summary.isSuccess());
                assertNotNull(summary.getSummary());
                assertTrue(summary.getSummary().contains("class Demo"));
        }
}
