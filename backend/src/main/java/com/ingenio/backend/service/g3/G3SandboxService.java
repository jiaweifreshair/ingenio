package com.ingenio.backend.service.g3;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * G3引擎沙箱服务
 * 负责与E2B沙箱交互，执行代码编译和验证
 *
 * 核心职责：
 * 1. 创建/复用E2B沙箱实例
 * 2. 同步代码文件到沙箱
 * 3. 执行Maven编译命令
 * 4. 解析编译输出，提取错误信息
 * 5. 管理沙箱生命周期
 *
 * 技术方案：
 * - 通过Open-Lovable-CN中间层调用E2B API
 * - 使用Maven 3.9 + JDK 17环境
 * - 编译超时：300秒
 */
@Slf4j
@Service
public class G3SandboxService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final G3ValidationAdapter g3ValidationAdapter;

    // Phase 5: ValidationService集成（可选，用于统一结果存储）
    private final com.ingenio.backend.service.ValidationService validationService;

    /**
     * Open-Lovable-CN服务基础URL
     */
    @Value("${ingenio.openlovable.base-url:http://localhost:3001}")
    private String openLovableBaseUrl;

    /**
     * E2B API Key（可选，如果需要直接调用E2B）
     */
    @Value("${ingenio.g3.sandbox.e2b-api-key:}")
    private String e2bApiKey;

    /**
     * 沙箱编译超时时间（秒）
     */
    @Value("${ingenio.g3.sandbox.compile-timeout:300}")
    private int compileTimeout;

    /**
     * 沙箱提供商
     */
    @Value("${ingenio.g3.sandbox.provider:e2b}")
    private String sandboxProvider;

    /**
     * Maven镜像/环境模板
     */
    private static final String MAVEN_TEMPLATE = "maven-jdk17";

    /**
     * 工作目录
     */
    private static final String WORKING_DIR = "/home/user/app";

    /**
     * 沙箱信息缓存
     */
    private final Map<UUID, SandboxInfo> sandboxCache = new HashMap<>();

    public G3SandboxService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            G3ValidationAdapter g3ValidationAdapter,
            com.ingenio.backend.service.ValidationService validationService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.g3ValidationAdapter = g3ValidationAdapter;
        this.validationService = validationService;
    }

    /**
     * 沙箱信息记录
     */
    public record SandboxInfo(
            String sandboxId,
            String provider,
            String url,
            Instant createdAt
    ) {}

    /**
     * 编译结果记录
     */
    public record CompileResult(
            boolean success,
            int exitCode,
            String stdout,
            String stderr,
            int durationMs,
            List<CompileError> errors
    ) {
        public static CompileResult success(String stdout, int durationMs) {
            return new CompileResult(true, 0, stdout, "", durationMs, List.of());
        }

        public static CompileResult failure(int exitCode, String stdout, String stderr, int durationMs, List<CompileError> errors) {
            return new CompileResult(false, exitCode, stdout, stderr, durationMs, errors);
        }
    }

    /**
     * 编译错误记录
     */
    public record CompileError(
            String file,
            int line,
            int column,
            String message,
            String severity
    ) {}

    /**
     * 创建新的E2B沙箱
     *
     * @param job         G3任务实体
     * @param logConsumer 日志回调
     * @return 沙箱信息
     */
    public SandboxInfo createSandbox(G3JobEntity job, Consumer<G3LogEntry> logConsumer) {
        logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.EXECUTOR, "正在创建编译沙箱..."));

        try {
            // 调用Open-Lovable-CN创建沙箱
            String url = openLovableBaseUrl + "/api/create-ai-sandbox-v2";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("template", MAVEN_TEMPLATE);
            requestBody.put("timeout", compileTimeout);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {}
                );

                String sandboxId = (String) result.get("sandboxId");
                String sandboxUrl = (String) result.get("url");
                String provider = (String) result.getOrDefault("provider", sandboxProvider);

                SandboxInfo info = new SandboxInfo(sandboxId, provider, sandboxUrl, Instant.now());

                // 缓存沙箱信息
                sandboxCache.put(job.getId(), info);

                // 更新Job的沙箱信息
                job.setSandboxId(sandboxId);
                job.setSandboxUrl(sandboxUrl);
                job.setSandboxProvider(provider);

                logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR,
                        "沙箱创建成功: " + sandboxId));

                return info;
            } else {
                throw new RuntimeException("沙箱创建失败: HTTP " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("[G3SandboxService] 创建沙箱失败: {}", e.getMessage(), e);
            logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.EXECUTOR, "沙箱创建失败: " + e.getMessage()));
            throw new RuntimeException("沙箱创建失败: " + e.getMessage(), e);
        }
    }

    /**
     * 同步代码文件到沙箱
     *
     * @param sandboxId   沙箱ID
     * @param artifacts   代码产物列表
     * @param logConsumer 日志回调
     */
    public void syncFiles(String sandboxId, List<G3ArtifactEntity> artifacts, Consumer<G3LogEntry> logConsumer) {
        logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.EXECUTOR,
                "正在同步 " + artifacts.size() + " 个文件到沙箱..."));

        try {
            // 构建文件列表
            List<Map<String, String>> files = new ArrayList<>();
            for (G3ArtifactEntity artifact : artifacts) {
                Map<String, String> file = new HashMap<>();
                file.put("path", WORKING_DIR + "/" + artifact.getFilePath());
                file.put("content", artifact.getContent());
                files.add(file);
            }

            // 调用API同步文件
            String url = openLovableBaseUrl + "/api/sandbox/write-files";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sandboxId", sandboxId);
            requestBody.put("files", files);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR,
                        "文件同步完成: " + artifacts.size() + " 个文件"));
            } else {
                throw new RuntimeException("文件同步失败: HTTP " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("[G3SandboxService] 文件同步失败: {}", e.getMessage(), e);
            logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.EXECUTOR, "文件同步失败: " + e.getMessage()));
            throw new RuntimeException("文件同步失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行Maven编译
     *
     * @param sandboxId   沙箱ID
     * @param logConsumer 日志回调
     * @return 编译结果
     */
    public CompileResult runMavenBuild(String sandboxId, Consumer<G3LogEntry> logConsumer) {
        logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.EXECUTOR, "正在执行Maven编译..."));

        long startTime = System.currentTimeMillis();

        try {
            // 调用API执行命令
            String url = openLovableBaseUrl + "/api/sandbox/execute";

            // 构建编译命令：不使用-q静默模式，确保能捕获完整错误信息
            // 使用 -e 显示完整错误堆栈，使用 -B 批处理模式
            String compileCommand = String.format(
                    "cd %s && mvn compile -e -B --no-transfer-progress 2>&1",
                    WORKING_DIR
            );

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sandboxId", sandboxId);
            requestBody.put("command", compileCommand);
            requestBody.put("timeout", compileTimeout);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            int durationMs = (int) (System.currentTimeMillis() - startTime);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {}
                );

                int exitCode = (Integer) result.getOrDefault("exitCode", -1);
                String stdout = (String) result.getOrDefault("stdout", "");
                String stderr = (String) result.getOrDefault("stderr", "");

                if (exitCode == 0) {
                    logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR,
                            "Maven编译成功，耗时 " + durationMs + "ms"));
                    return CompileResult.success(stdout, durationMs);
                } else {
                    // 解析编译错误
                    String output = stdout + "\n" + stderr;
                    List<CompileError> errors = parseCompilerErrors(output);

                    logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.EXECUTOR,
                            "Maven编译失败: " + errors.size() + " 个错误"));

                    return CompileResult.failure(exitCode, stdout, stderr, durationMs, errors);
                }
            } else {
                throw new RuntimeException("命令执行失败: HTTP " + response.getStatusCode());
            }

        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            log.error("[G3SandboxService] Maven编译异常: {}", e.getMessage(), e);
            logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.EXECUTOR, "Maven编译异常: " + e.getMessage()));

            return CompileResult.failure(-1, "", e.getMessage(), durationMs, List.of());
        }
    }

    /**
     * 执行完整的编译验证流程
     *
     * @param job         G3任务实体
     * @param artifacts   代码产物列表
     * @param logConsumer 日志回调
     * @return 验证结果实体
     */
    public G3ValidationResultEntity validate(
            G3JobEntity job,
            List<G3ArtifactEntity> artifacts,
            Consumer<G3LogEntry> logConsumer) {

        // 1. 确保沙箱已创建
        String sandboxId = job.getSandboxId();
        if (sandboxId == null || sandboxId.isBlank()) {
            SandboxInfo info = createSandbox(job, logConsumer);
            sandboxId = info.sandboxId();
        }

        // 2. 准备完整的文件列表（包括自动生成的pom.xml）
        List<G3ArtifactEntity> allArtifacts = prepareArtifactsWithPom(job, artifacts, logConsumer);

        // 3. 同步文件
        syncFiles(sandboxId, allArtifacts, logConsumer);

        // 4. 执行编译
        CompileResult compileResult = runMavenBuild(sandboxId, logConsumer);

        // 5. 构建验证结果
        G3ValidationResultEntity validationResult = G3ValidationResultEntity.createCompileResult(
                job.getId(),
                job.getCurrentRound(),
                compileResult.success(),
                "mvn compile -e -B",
                compileResult.exitCode(),
                compileResult.stdout(),
                compileResult.stderr(),
                compileResult.durationMs()
        );

        // 添加解析后的错误
        for (CompileError error : compileResult.errors()) {
            validationResult.addParsedError(
                    error.file(),
                    error.line(),
                    error.column(),
                    error.message(),
                    error.severity()
            );
        }

        // 5. 更新产物的编译状态
        if (!compileResult.success()) {
            updateArtifactErrors(artifacts, compileResult);
        }

        // 6. Phase 5: 可选地保存结果到ValidationService（统一结果存储）
        try {
            // 使用G3ValidationAdapter将CompileResult转换为ValidationResponse
            com.ingenio.backend.dto.response.validation.ValidationResponse validationResponse =
                    g3ValidationAdapter.toValidationResponse(compileResult, job.getId());

            // 保存到ValidationService的统一结果表
            validationService.saveExternalValidationResult(
                    job.getId(),
                    validationResponse,
                    com.ingenio.backend.entity.ValidationResultEntity.ValidationType.COMPILE
            );

            log.debug("G3验证结果已同步到ValidationService - jobId: {}, passed: {}",
                    job.getId(), validationResponse.getPassed());

        } catch (Exception e) {
            // 不影响G3主流程 - ValidationService集成失败只记录日志
            log.warn("同步G3验证结果到ValidationService失败（不影响G3流程） - jobId: {}, error: {}",
                    job.getId(), e.getMessage());
        }

        return validationResult;
    }

    /**
     * 解析Maven编译器错误输出
     *
     * @param output 编译器输出
     * @return 错误列表
     */
    public List<CompileError> parseCompilerErrors(String output) {
        List<CompileError> errors = new ArrayList<>();

        if (output == null || output.isBlank()) {
            return errors;
        }

        // Maven/javac错误格式：[ERROR] /path/to/File.java:[line,column] error: message
        Pattern mavenPattern = Pattern.compile(
                "\\[ERROR\\]\\s+([^:]+\\.java):\\[(\\d+),(\\d+)\\]\\s*(error|warning)?:?\\s*(.+)"
        );

        // javac直接输出格式：File.java:line: error: message
        Pattern javacPattern = Pattern.compile(
                "([^:]+\\.java):(\\d+):\\s*(error|warning)?:?\\s*(.+)"
        );

        String[] lines = output.split("\n");

        for (String line : lines) {
            Matcher mavenMatcher = mavenPattern.matcher(line);
            if (mavenMatcher.find()) {
                errors.add(new CompileError(
                        mavenMatcher.group(1),
                        Integer.parseInt(mavenMatcher.group(2)),
                        Integer.parseInt(mavenMatcher.group(3)),
                        mavenMatcher.group(5).trim(),
                        mavenMatcher.group(4) != null ? mavenMatcher.group(4) : "error"
                ));
                continue;
            }

            Matcher javacMatcher = javacPattern.matcher(line);
            if (javacMatcher.find()) {
                errors.add(new CompileError(
                        javacMatcher.group(1),
                        Integer.parseInt(javacMatcher.group(2)),
                        0,
                        javacMatcher.group(4).trim(),
                        javacMatcher.group(3) != null ? javacMatcher.group(3) : "error"
                ));
            }
        }

        return errors;
    }

    /**
     * 更新产物的编译错误状态
     */
    private void updateArtifactErrors(List<G3ArtifactEntity> artifacts, CompileResult compileResult) {
        String combinedOutput = compileResult.stdout() + "\n" + compileResult.stderr();

        for (G3ArtifactEntity artifact : artifacts) {
            // 检查编译输出是否包含该文件的错误
            if (combinedOutput.contains(artifact.getFileName())) {
                // 提取该文件相关的错误信息
                StringBuilder fileErrors = new StringBuilder();
                for (CompileError error : compileResult.errors()) {
                    if (error.file().contains(artifact.getFileName())) {
                        fileErrors.append(String.format("%s:%d:%d: %s: %s\n",
                                error.file(), error.line(), error.column(),
                                error.severity(), error.message()));
                    }
                }

                if (fileErrors.length() > 0) {
                    artifact.markError(fileErrors.toString());
                }
            }
        }
    }

    /**
     * 销毁沙箱
     *
     * @param sandboxId 沙箱ID
     */
    public void destroySandbox(String sandboxId) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return;
        }

        try {
            String url = openLovableBaseUrl + "/api/sandbox/kill";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sandboxId", sandboxId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            log.info("[G3SandboxService] 沙箱已销毁: {}", sandboxId);

        } catch (Exception e) {
            log.warn("[G3SandboxService] 销毁沙箱失败: {}", e.getMessage());
        }
    }

    /**
     * 检查沙箱是否可用
     *
     * @param sandboxId 沙箱ID
     * @return 是否可用
     */
    public boolean isSandboxAlive(String sandboxId) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return false;
        }

        try {
            String url = openLovableBaseUrl + "/api/sandbox-status?sandboxId=" + sandboxId;

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {}
                );
                return Boolean.TRUE.equals(result.get("alive"));
            }

        } catch (Exception e) {
            log.debug("[G3SandboxService] 检查沙箱状态失败: {}", e.getMessage());
        }

        return false;
    }

    /**
     * 准备完整的文件列表（包括自动生成的pom.xml）
     * 确保沙箱中有完整的Maven项目结构
     *
     * @param job         G3任务实体
     * @param artifacts   原始代码产物列表
     * @param logConsumer 日志回调
     * @return 包含pom.xml的完整文件列表
     */
    private List<G3ArtifactEntity> prepareArtifactsWithPom(
            G3JobEntity job,
            List<G3ArtifactEntity> artifacts,
            Consumer<G3LogEntry> logConsumer) {

        List<G3ArtifactEntity> allArtifacts = new ArrayList<>(artifacts);

        // 检查是否已存在pom.xml
        boolean hasPom = artifacts.stream()
                .anyMatch(a -> "pom.xml".equals(a.getFileName()));

        if (!hasPom) {
            logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.EXECUTOR, "正在生成Maven pom.xml..."));

            // 生成完整的pom.xml
            String pomContent = generatePomXml("com.ingenio.generated", "g3-generated-app", "1.0.0-SNAPSHOT");

            G3ArtifactEntity pomArtifact = G3ArtifactEntity.create(
                    job.getId(),
                    "pom.xml",  // 放在项目根目录
                    pomContent,
                    G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                    job.getCurrentRound()
            );

            allArtifacts.add(pomArtifact);
            logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR, "pom.xml生成完成"));
        }

        // 同时生成UUIDv8TypeHandler（生成的代码依赖它）
        boolean hasTypeHandler = artifacts.stream()
                .anyMatch(a -> a.getFileName().contains("UUIDv8TypeHandler"));

        if (!hasTypeHandler) {
            String typeHandlerContent = generateUUIDv8TypeHandler();
            G3ArtifactEntity typeHandlerArtifact = G3ArtifactEntity.create(
                    job.getId(),
                    "src/main/java/com/ingenio/backend/config/UUIDv8TypeHandler.java",
                    typeHandlerContent,
                    G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                    job.getCurrentRound()
            );
            allArtifacts.add(typeHandlerArtifact);
        }

        return allArtifacts;
    }

    /**
     * 生成UUIDv8TypeHandler类
     */
    private String generateUUIDv8TypeHandler() {
        return """
                package com.ingenio.backend.config;

                import org.apache.ibatis.type.BaseTypeHandler;
                import org.apache.ibatis.type.JdbcType;
                import org.apache.ibatis.type.MappedTypes;

                import java.sql.*;
                import java.util.UUID;

                /**
                 * UUID类型处理器
                 * 用于MyBatis-Plus中UUID字段的数据库映射
                 */
                @MappedTypes(UUID.class)
                public class UUIDv8TypeHandler extends BaseTypeHandler<UUID> {

                    @Override
                    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException {
                        ps.setObject(i, parameter);
                    }

                    @Override
                    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
                        Object obj = rs.getObject(columnName);
                        return obj instanceof UUID ? (UUID) obj : null;
                    }

                    @Override
                    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
                        Object obj = rs.getObject(columnIndex);
                        return obj instanceof UUID ? (UUID) obj : null;
                    }

                    @Override
                    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
                        Object obj = cs.getObject(columnIndex);
                        return obj instanceof UUID ? (UUID) obj : null;
                    }
                }
                """;
    }

    /**
     * 生成Maven pom.xml模板
     */
    public String generatePomXml(String groupId, String artifactId, String version) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                         https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.4.0</version>
                        <relativePath/>
                    </parent>

                    <groupId>%s</groupId>
                    <artifactId>%s</artifactId>
                    <version>%s</version>
                    <packaging>jar</packaging>

                    <properties>
                        <java.version>17</java.version>
                        <mybatis-plus.version>3.5.8</mybatis-plus.version>
                    </properties>

                    <dependencies>
                        <!-- Spring Boot Starter -->
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>

                        <!-- MyBatis-Plus -->
                        <dependency>
                            <groupId>com.baomidou</groupId>
                            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                            <version>${mybatis-plus.version}</version>
                        </dependency>

                        <!-- PostgreSQL -->
                        <dependency>
                            <groupId>org.postgresql</groupId>
                            <artifactId>postgresql</artifactId>
                            <scope>runtime</scope>
                        </dependency>

                        <!-- Lombok -->
                        <dependency>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <optional>true</optional>
                        </dependency>

                        <!-- Validation -->
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-validation</artifactId>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                                <configuration>
                                    <excludes>
                                        <exclude>
                                            <groupId>org.projectlombok</groupId>
                                            <artifactId>lombok</artifactId>
                                        </exclude>
                                    </excludes>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(groupId, artifactId, version);
    }
}
