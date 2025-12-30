package com.ingenio.backend.agent.g3.impl;

import com.ingenio.backend.agent.g3.ICoderAgent;
import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.ai.AIProviderFactory;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BackendCoderAgentImpl 单元测试
 *
 * 测试后端编码器Agent的核心功能：
 * 1. 生成Spring Boot完整后端代码（Entity、Mapper、Service、Controller）
 * 2. 解析AI返回的多文件Java代码
 * 3. 处理各种异常情况
 * 4. 验证生成的产物数量和类型
 */
@ExtendWith(MockitoExtension.class)
class BackendCoderAgentImplTest {

    @Mock
    private AIProviderFactory aiProviderFactory;

    @Mock
    private AIProvider aiProvider;

    @Mock
    private Consumer<G3LogEntry> logConsumer;

    @InjectMocks
    private BackendCoderAgentImpl backendCoderAgent;

    private G3JobEntity testJob;
    private UUID testJobId;
    private String validContract;
    private String validSchema;

    @BeforeEach
    void setUp() {
        testJobId = UUID.randomUUID();

        validContract = """
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
                    post:
                      summary: 创建用户
                      responses:
                        '201':
                          description: Created
                  /users/{id}:
                    get:
                      summary: 获取用户详情
                      parameters:
                        - name: id
                          in: path
                          required: true
                          schema:
                            type: string
                            format: uuid
                      responses:
                        '200':
                          description: Success
                """;

        validSchema = """
                -- DDL for User Management
                CREATE TABLE IF NOT EXISTS users (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    username VARCHAR(100) NOT NULL UNIQUE,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    password VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                CREATE INDEX idx_users_username ON users(username);
                CREATE INDEX idx_users_email ON users(email);
                """;

        testJob = G3JobEntity.builder()
                .id(testJobId)
                .requirement("创建一个用户管理系统")
                .contractYaml(validContract)
                .dbSchemaSql(validSchema)
                .currentRound(0)
                .build();
    }

    /**
     * 测试：成功生成完整的Spring Boot后端代码
     * 期望：生成Entity、Mapper、Service、Controller和pom.xml片段
     */
    @Test
    void generate_shouldGenerateCompleteBackendCode() throws AIProvider.AIException {
        // GIVEN
        String entityResponse = """
                ```java
                // === 文件: User.java ===
                package com.ingenio.backend.entity.generated;

                import com.baomidou.mybatisplus.annotation.*;
                import lombok.*;
                import java.time.LocalDateTime;
                import java.util.UUID;

                /**
                 * 用户实体类
                 */
                @Data
                @Builder
                @NoArgsConstructor
                @AllArgsConstructor
                @TableName("users")
                public class User {
                    @TableId(type = IdType.ASSIGN_UUID)
                    private UUID id;

                    private String username;
                    private String email;
                    private String password;

                    @TableField("created_at")
                    private LocalDateTime createdAt;

                    @TableField("updated_at")
                    private LocalDateTime updatedAt;
                }
                ```
                """;

        String mapperResponse = """
                ```java
                // === 文件: UserMapper.java ===
                package com.ingenio.backend.mapper.generated;

                import com.baomidou.mybatisplus.core.mapper.BaseMapper;
                import com.ingenio.backend.entity.generated.User;
                import org.apache.ibatis.annotations.Mapper;

                /**
                 * 用户Mapper接口
                 */
                @Mapper
                public interface UserMapper extends BaseMapper<User> {
                }
                ```
                """;

        String serviceResponse = """
                ```java
                // === 文件: IUserService.java ===
                package com.ingenio.backend.service.generated;

                import com.ingenio.backend.entity.generated.User;
                import java.util.List;
                import java.util.UUID;

                public interface IUserService {
                    List<User> list();
                    User getById(UUID id);
                    User create(User user);
                }

                // === 文件: UserServiceImpl.java ===
                package com.ingenio.backend.service.generated;

                import com.ingenio.backend.entity.generated.User;
                import com.ingenio.backend.mapper.generated.UserMapper;
                import lombok.RequiredArgsConstructor;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                import java.util.List;
                import java.util.UUID;

                @Service
                @RequiredArgsConstructor
                @Transactional
                public class UserServiceImpl implements IUserService {
                    private final UserMapper userMapper;

                    @Override
                    public List<User> list() {
                        return userMapper.selectList(null);
                    }

                    @Override
                    public User getById(UUID id) {
                        return userMapper.selectById(id);
                    }

                    @Override
                    public User create(User user) {
                        userMapper.insert(user);
                        return user;
                    }
                }
                ```
                """;

        String controllerResponse = """
                ```java
                // === 文件: UserController.java ===
                package com.ingenio.backend.controller.generated;

                import com.ingenio.backend.entity.generated.User;
                import com.ingenio.backend.service.generated.IUserService;
                import lombok.RequiredArgsConstructor;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import java.util.List;
                import java.util.UUID;

                @RestController
                @RequestMapping("/users")
                @RequiredArgsConstructor
                public class UserController {
                    private final IUserService userService;

                    @GetMapping
                    public ResponseEntity<List<User>> list() {
                        return ResponseEntity.ok(userService.list());
                    }

                    @GetMapping("/{id}")
                    public ResponseEntity<User> getById(@PathVariable UUID id) {
                        return ResponseEntity.ok(userService.getById(id));
                    }

                    @PostMapping
                    public ResponseEntity<User> create(@RequestBody User user) {
                        return ResponseEntity.status(201).body(userService.create(user));
                    }
                }
                ```
                """;

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);

        // Mock AI responses in sequence
        when(aiProvider.generate(contains("实体类"), any()))
                .thenReturn(AIProvider.AIResponse.builder().content(entityResponse).model("test").build());
        when(aiProvider.generate(contains("Mapper"), any()))
                .thenReturn(AIProvider.AIResponse.builder().content(mapperResponse).model("test").build());
        when(aiProvider.generate(contains("Service"), any()))
                .thenReturn(AIProvider.AIResponse.builder().content(serviceResponse).model("test").build());
        when(aiProvider.generate(contains("Controller"), any()))
                .thenReturn(AIProvider.AIResponse.builder().content(controllerResponse).model("test").build());

        // WHEN
        ICoderAgent.CoderResult result = backendCoderAgent.generate(testJob, 0, logConsumer);

        // THEN
        assertTrue(result.success(), "代码生成应该成功");
        assertNull(result.errorMessage(), "不应有错误信息");
        assertNotNull(result.artifacts(), "应包含产物列表");

        List<G3ArtifactEntity> artifacts = result.artifacts();

        // 验证产物数量：1 Entity + 1 Mapper + 2 Service (接口+实现) + 1 Controller + 1 pom = 6个
        assertTrue(artifacts.size() >= 6, "应至少生成6个文件，实际生成: " + artifacts.size());

        // 验证Entity
        assertTrue(artifacts.stream().anyMatch(a ->
                a.getFilePath().contains("entity/generated/User.java")),
                "应包含User实体类");

        // 验证Mapper
        assertTrue(artifacts.stream().anyMatch(a ->
                a.getFilePath().contains("mapper/generated/UserMapper.java")),
                "应包含UserMapper接口");

        // 验证Service
        assertTrue(artifacts.stream().anyMatch(a ->
                a.getFilePath().contains("service/generated/IUserService.java")),
                "应包含IUserService接口");
        assertTrue(artifacts.stream().anyMatch(a ->
                a.getFilePath().contains("service/generated/UserServiceImpl.java")),
                "应包含UserServiceImpl实现类");

        // 验证Controller
        assertTrue(artifacts.stream().anyMatch(a ->
                a.getFilePath().contains("controller/generated/UserController.java")),
                "应包含UserController");

        // 验证pom.xml片段
        assertTrue(artifacts.stream().anyMatch(a ->
                a.getFilePath().contains("pom-fragment.xml")),
                "应包含pom-fragment.xml");

        // 验证所有产物的generatedBy标记
        artifacts.forEach(artifact ->
                assertEquals(G3ArtifactEntity.GeneratedBy.BACKEND_CODER.getValue(),
                        artifact.getGeneratedBy(),
                        "产物应标记为BACKEND_CODER生成")
        );

        // 验证日志输出
        verify(logConsumer, atLeastOnce()).accept(argThat((G3LogEntry log) ->
                log.getLevel().equals(G3LogEntry.Level.SUCCESS.getValue())
        ));
    }

    /**
     * 测试：契约为空时应返回失败
     * 期望：返回失败结果，包含错误信息
     */
    @Test
    void generate_whenContractIsEmpty_shouldReturnFailure() {
        // GIVEN
        testJob.setContractYaml(null);

        // WHEN
        ICoderAgent.CoderResult result = backendCoderAgent.generate(testJob, 0, logConsumer);

        // THEN
        assertFalse(result.success(), "应返回失败");
        assertNotNull(result.errorMessage(), "应包含错误信息");
        assertTrue(result.errorMessage().contains("契约文档为空"), "错误信息应说明契约为空");
        assertTrue(result.artifacts().isEmpty(), "失败时产物列表应为空");
    }

    /**
     * 测试：Schema为空时应返回失败
     * 期望：返回失败结果，包含错误信息
     */
    @Test
    void generate_whenSchemaIsEmpty_shouldReturnFailure() {
        // GIVEN
        testJob.setDbSchemaSql(null);

        // WHEN
        ICoderAgent.CoderResult result = backendCoderAgent.generate(testJob, 0, logConsumer);

        // THEN
        assertFalse(result.success(), "应返回失败");
        assertNotNull(result.errorMessage(), "应包含错误信息");
        assertTrue(result.errorMessage().contains("数据库Schema为空"), "错误信息应说明Schema为空");
        assertTrue(result.artifacts().isEmpty(), "失败时产物列表应为空");
    }

    /**
     * 测试：AI提供商不可用时应返回失败
     * 期望：返回失败结果
     */
    @Test
    void generate_whenAIProviderUnavailable_shouldReturnFailure() {
        // GIVEN
        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(false);

        // WHEN
        ICoderAgent.CoderResult result = backendCoderAgent.generate(testJob, 0, logConsumer);

        // THEN
        assertFalse(result.success(), "应返回失败");
        assertTrue(result.errorMessage().contains("AI提供商不可用"), "错误信息应说明原因");
    }

    /**
     * 测试：AI调用异常时应捕获并返回失败
     * 期望：返回失败结果，记录错误日志
     */
    @Test
    void generate_whenAICallFails_shouldReturnFailure() throws AIProvider.AIException {
        // GIVEN
        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenThrow(new AIProvider.AIException("API调用失败", "test-provider"));

        // WHEN
        ICoderAgent.CoderResult result = backendCoderAgent.generate(testJob, 0, logConsumer);

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
     * 测试：解析带文件分隔符的Java代码
     * 期望：正确提取多个Java文件
     */
    @Test
    void parseJavaFiles_withFileSeparator_shouldParseMultipleFiles() throws AIProvider.AIException {
        // GIVEN
        String multiFileResponse = """
                // === 文件: User.java ===
                package com.test;
                public class User {
                    private String name;
                }

                // === 文件: UserDTO.java ===
                package com.test;
                public class UserDTO {
                    private String name;
                }
                """;

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenReturn(AIProvider.AIResponse.builder().content(multiFileResponse).model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build());

        // WHEN
        ICoderAgent.CoderResult result = backendCoderAgent.generate(testJob, 0, logConsumer);

        // THEN
        assertTrue(result.success(), "应成功解析");
        List<G3ArtifactEntity> artifacts = result.artifacts();

        // 验证解析出的Entity文件
        long entityCount = artifacts.stream()
                .filter(a -> a.getFilePath().contains("entity/generated/"))
                .count();
        assertEquals(2, entityCount, "应解析出2个实体类文件");

        // 验证文件名
        assertTrue(artifacts.stream().anyMatch(a -> a.getFileName().equals("User.java")), "应包含User.java");
        assertTrue(artifacts.stream().anyMatch(a -> a.getFileName().equals("UserDTO.java")), "应包含UserDTO.java");

        // 验证内容不为空
        artifacts.stream()
                .filter(a -> a.getFilePath().contains("entity/generated/"))
                .forEach(artifact -> {
                    assertNotNull(artifact.getContent(), "文件内容不应为空");
                    assertTrue(artifact.getContent().contains("package com.test"), "应包含package声明");
                });
    }

    /**
     * 测试：解析无分隔符的Java代码（按package分割）
     * 期望：能够按package语句分割多个类
     */
    @Test
    void parseJavaFiles_withoutSeparator_shouldParseByPackage() throws AIProvider.AIException {
        // GIVEN
        String noSeparatorResponse = """
                package com.test;
                public class User {
                    private String name;
                }
                package com.test;
                public class UserDTO {
                    private String name;
                }
                """;

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenReturn(AIProvider.AIResponse.builder().content(noSeparatorResponse).model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build());

        // WHEN
        ICoderAgent.CoderResult result = backendCoderAgent.generate(testJob, 0, logConsumer);

        // THEN
        assertTrue(result.success(), "应成功解析");
        List<G3ArtifactEntity> artifacts = result.artifacts();

        // 验证解析出的Entity文件
        long entityCount = artifacts.stream()
                .filter(a -> a.getFilePath().contains("entity/generated/"))
                .count();
        assertTrue(entityCount >= 2, "应至少解析出2个实体类文件");
    }

    /**
     * 测试：清理Markdown代码块标记
     * 期望：移除```java和```标记
     */
    @Test
    void cleanMarkdown_shouldRemoveCodeBlockMarkers() throws AIProvider.AIException {
        // GIVEN
        String markdownResponse = """
                ```java
                // === 文件: User.java ===
                package com.test;
                public class User {}
                ```
                """;

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenReturn(AIProvider.AIResponse.builder().content(markdownResponse).model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build());

        // WHEN
        ICoderAgent.CoderResult result = backendCoderAgent.generate(testJob, 0, logConsumer);

        // THEN
        assertTrue(result.success(), "应成功解析");
        List<G3ArtifactEntity> artifacts = result.artifacts();

        // 验证内容不包含markdown标记
        artifacts.stream()
                .filter(a -> a.getFilePath().contains("entity/generated/"))
                .forEach(artifact -> {
                    assertFalse(artifact.getContent().contains("```java"), "内容不应包含```java标记");
                    assertFalse(artifact.getContent().contains("```"), "内容不应包含```标记");
                });
    }

    /**
     * 测试：Agent基础属性
     * 期望：返回正确的名称、描述、目标类型、目标语言
     */
    @Test
    void agentProperties_shouldReturnCorrectValues() {
        assertEquals("BackendCoderAgent", backendCoderAgent.getName(), "应返回正确的名称");
        assertEquals("backend", backendCoderAgent.getTargetType(), "应返回backend类型");
        assertEquals("java", backendCoderAgent.getTargetLanguage(), "应返回java语言");
        assertEquals(G3LogEntry.Role.PLAYER, backendCoderAgent.getRole(), "应返回PLAYER角色");
        assertNotNull(backendCoderAgent.getDescription(), "应有描述信息");
        assertTrue(backendCoderAgent.getDescription().contains("后端编码器"), "描述应提到后端编码器");
    }

    /**
     * 测试：execute方法应委托给generate方法
     * 期望：成功时返回产物列表
     */
    @Test
    void execute_shouldDelegateToGenerate() throws Exception {
        // GIVEN
        String simpleResponse = """
                // === 文件: User.java ===
                package com.test;
                public class User {}
                """;

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenReturn(AIProvider.AIResponse.builder().content(simpleResponse).model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build());

        // WHEN
        List<G3ArtifactEntity> artifacts = backendCoderAgent.execute(testJob, logConsumer);

        // THEN
        assertNotNull(artifacts, "应返回产物列表");
        assertFalse(artifacts.isEmpty(), "产物列表不应为空");
    }

    /**
     * 测试：execute失败时应抛出G3AgentException
     * 期望：抛出异常，包含正确的错误信息
     */
    @Test
    void execute_whenGenerateFails_shouldThrowException() {
        // GIVEN
        testJob.setContractYaml(null); // 触发失败

        // WHEN & THEN
        ICoderAgent.G3AgentException exception = assertThrows(
                ICoderAgent.G3AgentException.class,
                () -> backendCoderAgent.execute(testJob, logConsumer),
                "execute失败应抛出G3AgentException"
        );

        assertEquals("BackendCoderAgent", exception.getAgentName(), "异常应包含Agent名称");
        assertEquals(G3LogEntry.Role.PLAYER, exception.getRole(), "异常应包含Agent角色");
        assertNotNull(exception.getMessage(), "异常应包含错误信息");
    }

    /**
     * 测试：生成的pom.xml片段应包含必要的依赖
     * 期望：包含MyBatis-Plus、PostgreSQL、Lombok等依赖
     */
    @Test
    void generatePomFragment_shouldContainRequiredDependencies() throws AIProvider.AIException {
        // GIVEN
        String entityResponse = "// === 文件: User.java ===\npackage com.test;\npublic class User {}";

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenReturn(AIProvider.AIResponse.builder().content(entityResponse).model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build());

        // WHEN
        ICoderAgent.CoderResult result = backendCoderAgent.generate(testJob, 0, logConsumer);

        // THEN
        assertTrue(result.success(), "应成功生成");

        G3ArtifactEntity pomArtifact = result.artifacts().stream()
                .filter(a -> a.getFilePath().contains("pom-fragment.xml"))
                .findFirst()
                .orElse(null);

        assertNotNull(pomArtifact, "应包含pom-fragment.xml");

        String pomContent = pomArtifact.getContent();
        assertTrue(pomContent.contains("mybatis-plus"), "pom应包含MyBatis-Plus依赖");
        assertTrue(pomContent.contains("postgresql"), "pom应包含PostgreSQL依赖");
        assertTrue(pomContent.contains("lombok"), "pom应包含Lombok依赖");
        assertTrue(pomContent.contains("spring-boot-starter-validation"), "pom应包含Validation依赖");
    }

    /**
     * 测试：生成轮次应正确传递到产物
     * 期望：产物的generationRound应与输入一致
     */
    @Test
    void generate_shouldPassGenerationRoundToArtifacts() throws AIProvider.AIException {
        // GIVEN
        int generationRound = 2;
        String entityResponse = "// === 文件: User.java ===\npackage com.test;\npublic class User {}";

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenReturn(AIProvider.AIResponse.builder().content(entityResponse).model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content("").model("test").build());

        // WHEN
        ICoderAgent.CoderResult result = backendCoderAgent.generate(testJob, generationRound, logConsumer);

        // THEN
        assertTrue(result.success(), "应成功生成");

        // 验证所有产物的generationRound
        result.artifacts().forEach(artifact ->
                assertEquals(generationRound, artifact.getGenerationRound(),
                        "产物的generationRound应为" + generationRound)
        );
    }
}
