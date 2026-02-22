package com.ingenio.backend.service.g3;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import com.ingenio.backend.service.openlovable.OpenLovableEndpointRouter;
import com.ingenio.backend.service.openlovable.OpenLovableSandboxRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

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
    private final RedisTemplate<String, String> redisTemplate;

    // Phase 5: ValidationService集成（可选，用于统一结果存储）
    private final com.ingenio.backend.service.ValidationService validationService;

    /**
     * Redis Key 前缀（沙箱缓存）
     */
    private static final String SANDBOX_CACHE_PREFIX = "g3:sandbox:";

    /**
     * Open-Lovable-CN服务基础URL
     */
    @Value("${ingenio.openlovable.base-url:http://localhost:3001}")
    private String openLovableBaseUrl;

    /**
     * OpenLovable 多实例路由器（可选）
     */
    @Autowired(required = false)
    private OpenLovableEndpointRouter endpointRouter;

    /**
     * OpenLovable 沙箱状态注册中心（可选）
     *
     * 是什么：多活沙箱状态中心。
     * 做什么：记录 G3 创建的 sandbox 状态与心跳。
     * 为什么：让回收器与并发控制可以感知 G3 沙箱。
     */
    @Autowired(required = false)
    private OpenLovableSandboxRegistry sandboxRegistry;

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
     * 是否允许本地编译回退
     *
     * 说明：
     * - true 时允许在远程沙箱失败后回退到本地编译；
     * - false 时严格禁止本地落盘编译，以满足“产物不落本地盘”的约束。
     */
    @Value("${ingenio.g3.sandbox.allow-local-fallback:false}")
    private boolean allowLocalFallback;

    /**
     * Goose 编译命令（本地执行）。
     *
     * <p>
     * 说明：
     * </p>
     * <ul>
     * <li>当 `ingenio.g3.sandbox.provider=goose` 时生效；</li>
     * <li>用于将“编译/验证执行器”替换为外部 Goose 执行器（或任意兼容命令），以便在不改动核心逻辑的前提下
     * 复用 Goose 的沙箱/执行/观测能力；</li>
     * <li>若为空，将自动回退到本地 Maven 编译（保持可用性）。</li>
     * </ul>
     *
     * <p>
     * 示例：
     * </p>
     * 
     * <pre>{@code
     * ingenio:
     *   g3:
     *     sandbox:
     *       provider: goose
     *       goose:
     *         compile-command: "goose run -- mvn -e -B -DskipTests compile"
     * }</pre>
     */
    @Value("${ingenio.g3.sandbox.goose.compile-command:}")
    private String gooseCompileCommand;

    /**
     * Maven镜像/环境模板
     */
    private static final String MAVEN_TEMPLATE = "maven-jdk17";

    /**
     * 工作目录
     */
    private static final String WORKING_DIR = "/home/user/app";

    /**
     * 本地沙箱根目录（用于 OpenLovable 不可用时的回退验证）
     *
     * 说明：
     * - 采用 backend/target 下的目录，避免写入系统目录引发权限问题
     * - 保留文件便于定位编译失败原因（可按需手动清理）
     */
    private static final Path LOCAL_SANDBOX_ROOT = Paths.get(System.getProperty("user.dir"), "target",
            "g3-local-sandbox");

    public G3SandboxService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            G3ValidationAdapter g3ValidationAdapter,
            com.ingenio.backend.service.ValidationService validationService,
            RedisTemplate<String, String> redisTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.g3ValidationAdapter = g3ValidationAdapter;
        this.validationService = validationService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 沙箱信息记录
     */
    public record SandboxInfo(
            String sandboxId,
            String provider,
            String url,
            Instant createdAt) {
    }

    /**
     * 错误类型枚举
     * 用于区分环境错误（可重试）和代码错误（需要Coach修复）
     */
    public enum ErrorType {
        /** 无错误 */
        NONE,
        /** 代码编译错误（Java语法/类型错误） - 需要Coach Agent修复 */
        CODE_ERROR,
        /** 环境错误（依赖下载失败/网络超时） - 可自动重试 */
        ENVIRONMENT_ERROR,
        /** 未知错误 */
        UNKNOWN
    }

    /**
     * 编译结果记录
     */
    public record CompileResult(
            boolean success,
            int exitCode,
            String stdout,
            String stderr,
            int durationMs,
            List<CompileError> errors,
            ErrorType errorType) {
        public static CompileResult success(String stdout, int durationMs) {
            return new CompileResult(true, 0, stdout, "", durationMs, List.of(), ErrorType.NONE);
        }

        public static CompileResult failure(int exitCode, String stdout, String stderr, int durationMs,
                List<CompileError> errors) {
            return new CompileResult(false, exitCode, stdout, stderr, durationMs, errors, ErrorType.CODE_ERROR);
        }

        public static CompileResult environmentFailure(int exitCode, String stdout, String stderr, int durationMs,
                String reason) {
            List<CompileError> errors = List.of(new CompileError("pom.xml", 0, 0, reason, "environment"));
            return new CompileResult(false, exitCode, stdout, stderr, durationMs, errors, ErrorType.ENVIRONMENT_ERROR);
        }

        /** 检查是否是环境类错误（可重试） */
        public boolean isEnvironmentError() {
            return errorType == ErrorType.ENVIRONMENT_ERROR;
        }
    }

    /**
     * 通用命令执行结果
     */
    public record SandboxCommandResult(
            int exitCode,
            String stdout,
            String stderr,
            int durationMs) {
    }

    /**
     * 编译错误记录
     */
    public record CompileError(
            String file,
            int line,
            int column,
            String message,
            String severity) {
    }

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
            String targetBaseUrl = selectOpenLovableBaseUrlForCreate();
            String url = targetBaseUrl + "/api/create-ai-sandbox-v2";

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
                    String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {
                        });

                String sandboxId = (String) result.get("sandboxId");
                String sandboxUrl = (String) result.get("url");
                String provider = (String) result.getOrDefault("provider", sandboxProvider);
                bindSandboxEndpoint(sandboxId, targetBaseUrl);
                registerSandboxReady(sandboxId, targetBaseUrl);

                SandboxInfo info = new SandboxInfo(sandboxId, provider, sandboxUrl, Instant.now());

                // 缓存沙箱信息到 Redis（支持分布式）
                saveSandboxToRedis(job.getId(), info);

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
            String url = resolveOpenLovableBaseUrlBySandboxId(sandboxId) + "/api/sandbox/write-files";

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
                    String.class);

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
            String url = resolveOpenLovableBaseUrlBySandboxId(sandboxId) + "/api/sandbox/execute";

            // 构建编译命令：不使用-q静默模式，确保能捕获完整错误信息
            // 使用 -e 显示完整错误堆栈，使用 -B 批处理模式
            //
            // 注意：
            // OpenLovable 的 E2BProvider.runCommand 使用 subprocess.run(command.split(" "),
            // shell=false) 执行，
            // 因此不能传入包含 `cd`/`&&`/`2>&1` 等 shell 语法的命令，否则会触发 “Command failed” 且无法拿到 Maven
            // 输出。
            // 其内部已固定切换到 /home/user/app（WORKING_DIR），这里直接执行 mvn 即可。
            String compileCommand = "mvn compile -e -B --no-transfer-progress";

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
                    String.class);

            int durationMs = (int) (System.currentTimeMillis() - startTime);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {
                        });

                int exitCode = toInt(result.getOrDefault("exitCode", -1), -1);
                String stdout = toString(result.getOrDefault("stdout", ""));
                String stderr = toString(result.getOrDefault("stderr", ""));

                // 兼容 Open-Lovable 返回字段差异（部分实现只返回 output/message）
                if ((stdout == null || stdout.isBlank()) && (stderr == null || stderr.isBlank())) {
                    Object fallback = result.getOrDefault("output", result.getOrDefault("message", ""));
                    stdout = toString(fallback);
                }

                // OpenLovable E2BProvider 历史实现会将命令 returncode 写入 stdout（例如 "Return code: 1"），
                // 但 JSON 中的 exitCode 可能仍为 0。为避免“编译失败被误判为成功”，需要二次解析真实 returncode。
                int effectiveExitCode = exitCode;
                String combined = combineOutput(stdout, stderr);
                if (!combined.isBlank()) {
                    Integer parsed = parseReturnCodeFromOutput(combined);
                    if (parsed != null && parsed != 0) {
                        effectiveExitCode = parsed;
                    } else if (combined.toLowerCase().contains("build failure") ||
                            combined.contains("Failed to execute goal")) {
                        // 兜底：构建失败但未给出 returncode 时，按失败处理
                        effectiveExitCode = exitCode == 0 ? 1 : exitCode;
                    }
                }

                if (effectiveExitCode == 0) {
                    logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR,
                            "Maven编译成功，耗时 " + durationMs + "ms"));
                    return CompileResult.success(stdout, durationMs);
                } else {
                    // 解析编译错误
                    String output = combineOutput(stdout, stderr);
                    List<CompileError> errors = parseCompilerErrors(output);

                    // 检查是否是环境类错误（可自动重试，不需要Coach Agent介入）
                    if (errors.isEmpty()) {
                        String envError = detectEnvironmentError(output);
                        if (envError != null) {
                            logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                                    "检测到环境类错误（可自动重试）: " + envError));
                            emitMavenFailureSnippet(output, logConsumer);
                            return CompileResult.environmentFailure(exitCode, stdout, stderr, durationMs, envError);
                        }

                        // 非环境错误但也无法解析Java编译错误
                        logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                                "Maven编译失败（未解析到Java编译错误），可能是依赖下载/构建配置/环境问题，输出关键日志片段..."));
                        emitMavenFailureSnippet(output, logConsumer);
                        errors = List.of(new CompileError("pom.xml", 0, 0,
                                "构建失败（未解析到Java编译错误），请查看日志中的 Maven 输出片段", "error"));
                    } else {
                        // 如果已解析到具体错误，仅输出错误行，避免前端被大量构建日志淹没
                        emitMavenFailureSnippet(output, logConsumer);
                    }

                    logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.EXECUTOR,
                            "Maven编译失败: " + errors.size() + " 个错误 (exitCode=" + effectiveExitCode + ")"));

                    return CompileResult.failure(effectiveExitCode, stdout, stderr, durationMs, errors);
                }
            } else {
                throw new RuntimeException("命令执行失败: HTTP " + response.getStatusCode());
            }

        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            log.error("[G3SandboxService] Maven编译异常: {}", e.getMessage(), e);
            logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.EXECUTOR, "Maven编译异常: " + e.getMessage()));
            // 说明：
            // - OpenLovable 属于外部依赖，请求异常通常属于环境问题（网络/服务崩溃/容器重启等）
            // - 若将其误判为代码错误，会触发 Coach 误修 pom.xml，浪费修复轮次
            return CompileResult.environmentFailure(
                    -1,
                    "",
                    e.getMessage() == null ? "" : e.getMessage(),
                    durationMs,
                    "沙箱执行命令异常（OpenLovable 调用失败）");
        }
    }

    /**
     * 在沙箱内执行通用命令（只读/诊断）。
     *
     * @param sandboxId  沙箱ID
     * @param command    执行命令
     * @param timeoutSec 超时（秒）
     * @return 执行结果
     */
    public SandboxCommandResult executeCommand(String sandboxId, String command, int timeoutSec) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return new SandboxCommandResult(1, "", "sandboxId为空", 0);
        }
        if (command == null || command.isBlank()) {
            return new SandboxCommandResult(1, "", "command为空", 0);
        }

        long startTime = System.currentTimeMillis();

        try {
            String url = resolveOpenLovableBaseUrlBySandboxId(sandboxId) + "/api/sandbox/execute";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sandboxId", sandboxId);
            requestBody.put("command", command);
            requestBody.put("timeout", timeoutSec);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class);

            int durationMs = (int) (System.currentTimeMillis() - startTime);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {
                        });

                int exitCode = toInt(result.getOrDefault("exitCode", -1), -1);
                String stdout = toString(result.getOrDefault("stdout", ""));
                String stderr = toString(result.getOrDefault("stderr", ""));

                if ((stdout == null || stdout.isBlank()) && (stderr == null || stderr.isBlank())) {
                    Object fallback = result.getOrDefault("output", result.getOrDefault("message", ""));
                    stdout = toString(fallback);
                }

                return new SandboxCommandResult(exitCode, stdout, stderr, durationMs);
            }

            return new SandboxCommandResult(1, "", "命令执行失败: HTTP " + response.getStatusCode(), durationMs);
        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - startTime);
            return new SandboxCommandResult(1, "", "命令执行异常: " + e.getMessage(), durationMs);
        }
    }

    /**
     * 执行Maven编译（带环境错误自动重试）
     *
     * 当检测到环境类错误（如依赖下载失败、网络超时）时，自动重试编译，
     * 而不是交给Coach Agent处理（Coach Agent无法修复环境问题）。
     *
     * @param sandboxId   沙箱ID
     * @param logConsumer 日志回调
     * @return 编译结果
     */
    public CompileResult runMavenBuildWithRetry(String sandboxId, Consumer<G3LogEntry> logConsumer) {
        CompileResult result = null;

        for (int attempt = 1; attempt <= ENV_ERROR_MAX_RETRIES; attempt++) {
            result = runMavenBuild(sandboxId, logConsumer);

            // 编译成功，直接返回
            if (result.success()) {
                return result;
            }

            // 如果是代码错误（非环境错误），不重试，交给Coach Agent处理
            if (!result.isEnvironmentError()) {
                logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.EXECUTOR,
                        "检测到代码编译错误，将交给Coach Agent处理"));
                return result;
            }

            // 环境错误，尝试重试
            if (attempt < ENV_ERROR_MAX_RETRIES) {
                logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                        String.format("环境错误，%d秒后自动重试编译 (第%d/%d次)...",
                                envErrorRetryDelayMs / 1000, attempt + 1, ENV_ERROR_MAX_RETRIES)));

                try {
                    Thread.sleep(envErrorRetryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 所有重试都失败
        logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.EXECUTOR,
                String.format("环境错误重试%d次后仍失败，可能需要检查网络/沙箱环境", ENV_ERROR_MAX_RETRIES)));

        return result;
    }

    /**
     * 本地Maven编译（带环境错误自动重试）
     *
     * @param job         G3任务
     * @param artifacts   产物列表
     * @param logConsumer 日志回调
     * @return 编译结果
     */
    private CompileResult runLocalMavenBuildWithRetry(
            G3JobEntity job,
            List<G3ArtifactEntity> artifacts,
            Consumer<G3LogEntry> logConsumer) {

        CompileResult result = null;

        for (int attempt = 1; attempt <= ENV_ERROR_MAX_RETRIES; attempt++) {
            result = runLocalMavenBuild(job, artifacts, logConsumer);

            // 编译成功，直接返回
            if (result.success()) {
                return result;
            }

            // 如果是代码错误（非环境错误），不重试
            if (!result.isEnvironmentError()) {
                return result;
            }

            // 环境错误，尝试重试
            if (attempt < ENV_ERROR_MAX_RETRIES) {
                logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                        String.format("本地编译环境错误，%d秒后重试 (第%d/%d次)...",
                                envErrorRetryDelayMs / 1000, attempt + 1, ENV_ERROR_MAX_RETRIES)));

                try {
                    Thread.sleep(envErrorRetryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return result;
    }

    /**
     * 将任意对象转换为字符串（null安全）
     */
    private static String toString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 将任意对象转换为 int（兼容 Number / String）
     */
    private static int toInt(Object value, int defaultValue) {
        if (value == null)
            return defaultValue;
        if (value instanceof Number n)
            return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    /**
     * 合并 stdout/stderr 为单一输出（避免空串导致多余换行）
     */
    private static String combineOutput(String stdout, String stderr) {
        String out = stdout == null ? "" : stdout;
        String err = stderr == null ? "" : stderr;
        if (out.isBlank())
            return err;
        if (err.isBlank())
            return out;
        return out + "\n" + err;
    }

    /**
     * 从输出中解析 "Return code: N"（OpenLovable E2BProvider 输出格式）
     *
     * @param output 组合输出
     * @return 解析到的 returncode；解析失败返回 null
     */
    private static Integer parseReturnCodeFromOutput(String output) {
        if (output == null || output.isBlank())
            return null;
        Matcher matcher = Pattern.compile("(?m)^Return\\s+code:\\s*(\\d+)\\s*$").matcher(output);
        if (!matcher.find())
            return null;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 输出 Maven 构建失败的关键日志片段（单行化，避免 SSE/UI 折叠）
     *
     * 规则：
     * 1) 优先输出 [ERROR] 行（Maven 统一前缀）
     * 2) 如无 [ERROR] 行，则输出末尾若干行（通常包含失败原因）
     * 3) 限制最大行数与单行长度，避免刷屏
     */
    private static void emitMavenFailureSnippet(String output, Consumer<G3LogEntry> logConsumer) {
        if (output == null || output.isBlank()) {
            logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR, "Maven输出为空，无法解析失败原因"));
            return;
        }

        // OpenLovable 执行器的低信息失败：只返回 "Command failed"，无法获取 Maven 真实输出
        String trimmed = output.replace("\r", "").trim();
        if (trimmed.toLowerCase().startsWith("command failed")) {
            logConsumer.accept(G3LogEntry.warn(
                    G3LogEntry.Role.EXECUTOR,
                    "Maven输出: Command failed（OpenLovable 未返回 Maven 详细日志，请检查 open-lovable-cn 服务日志/执行器实现）"));
            return;
        }

        List<String> errorLines = new ArrayList<>();
        List<String> allLines = new ArrayList<>();

        String[] lines = output.split("\n");
        for (String raw : lines) {
            if (raw == null)
                continue;
            String line = raw.replace("\r", "").trim();
            if (line.isEmpty())
                continue;

            allLines.add(line);
            if (line.startsWith("[ERROR]")) {
                errorLines.add(line);
            }
        }

        List<String> chosen;
        if (!errorLines.isEmpty()) {
            chosen = errorLines;
        } else {
            int tail = Math.min(60, allLines.size());
            chosen = allLines.subList(Math.max(0, allLines.size() - tail), allLines.size());
        }

        int maxLines = 80;
        int emitted = 0;
        for (String line : chosen) {
            if (emitted >= maxLines)
                break;
            String oneLine = line.length() > 420 ? line.substring(0, 420) + "..." : line;
            logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR, "Maven输出: " + oneLine));
            emitted++;
        }

        if (chosen.size() > maxLines) {
            logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                    "Maven输出: ... 省略 " + (chosen.size() - maxLines) + " 行"));
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
    /**
     * 环境错误自动重试最大次数
     */
    private static final int ENV_ERROR_MAX_RETRIES = 3;

    /**
     * 环境错误重试间隔（毫秒）
     */
    @Value("${ingenio.g3.sandbox.env-retry-delay-ms:2000}")
    private int envErrorRetryDelayMs;

    public G3ValidationResultEntity validate(
            G3JobEntity job,
            List<G3ArtifactEntity> artifacts,
            Consumer<G3LogEntry> logConsumer) {

        // 1. 准备完整的文件列表（包括自动生成的pom.xml）
        List<G3ArtifactEntity> allArtifacts = prepareArtifactsWithPom(job, artifacts, logConsumer);

        // 2. 执行编译（带环境错误自动重试）
        CompileResult compileResult;
        String compileCommand;

        boolean forceGoose = "goose".equalsIgnoreCase(sandboxProvider);
        boolean forceLocal = "local".equalsIgnoreCase(sandboxProvider);

        if (forceGoose) {
            logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                    "已配置使用 Goose 执行编译验证（provider=goose），跳过 OpenLovable 编译沙箱"));
            compileResult = runLocalGooseBuildWithRetry(job, allArtifacts, logConsumer);
            compileCommand = gooseCompileCommand != null && !gooseCompileCommand.isBlank()
                    ? ("goose: " + gooseCompileCommand)
                    : "mvn -e -B -DskipTests compile (local fallback)";
        } else if (!forceLocal) {
            try {
                // 2.1-2.3 远程沙箱编译（含“环境错误/沙箱状态异常”的重建重试）
                CompileResult remoteResult = runRemoteMavenBuildWithResetRetry(job, allArtifacts, logConsumer);

                // 若远程仍为环境类错误，回退本地编译以拿到可修复的 Java 错误（提升闭环可用性）
                if (remoteResult != null && remoteResult.isEnvironmentError() && !remoteResult.success()) {
                    if (allowLocalFallback) {
                        logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                                "远程沙箱编译持续出现环境类错误，回退到本地编译验证以获取可修复的编译信息"));
                        compileResult = runLocalMavenBuildWithRetry(job, allArtifacts, logConsumer);
                        compileCommand = "mvn -e -B -DskipTests compile (local)";
                    } else {
                        logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                                "远程沙箱编译持续出现环境类错误，已禁用本地回退"));
                        compileResult = remoteResult;
                        compileCommand = "mvn compile -e -B --no-transfer-progress";
                    }
                } else {
                    compileResult = remoteResult;
                    compileCommand = "mvn compile -e -B --no-transfer-progress";
                }

            } catch (Exception e) {
                if (allowLocalFallback) {
                    // OpenLovable-CN 属于外部依赖：不可用时不应直接让 G3 失败，改为本地回退验证
                    logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                            "OpenLovable 编译沙箱不可用，回退到本地编译验证: " + e.getMessage()));
                    compileResult = runLocalMavenBuildWithRetry(job, allArtifacts, logConsumer);
                    compileCommand = "mvn -e -B -DskipTests compile (local)";
                } else {
                    logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.EXECUTOR,
                            "OpenLovable 编译沙箱不可用，且已禁用本地回退: " + e.getMessage()));
                    compileResult = CompileResult.environmentFailure(
                            1,
                            "",
                            "",
                            0,
                            "OpenLovable不可用且已禁用本地回退");
                    compileCommand = "mvn compile -e -B --no-transfer-progress";
                }
            }
        } else {
            logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                    "已配置使用本地沙箱编译（provider=local），跳过 OpenLovable 编译沙箱"));
            compileResult = runLocalMavenBuildWithRetry(job, allArtifacts, logConsumer);
            compileCommand = "mvn -e -B -DskipTests compile (local)";
        }

        // 5. 构建验证结果
        G3ValidationResultEntity validationResult = G3ValidationResultEntity.createCompileResult(
                job.getId(),
                job.getCurrentRound(),
                compileResult.success(),
                compileCommand,
                compileResult.exitCode(),
                compileResult.stdout(),
                compileResult.stderr(),
                compileResult.durationMs());

        // 添加解析后的错误
        for (CompileError error : compileResult.errors()) {
            validationResult.addParsedError(
                    error.file(),
                    error.line(),
                    error.column(),
                    error.message(),
                    error.severity());
        }

        // 5. 更新产物的编译状态
        if (!compileResult.success()) {
            updateArtifactErrors(allArtifacts, compileResult);
        }

        // 6. Phase 5: 可选地保存结果到ValidationService（统一结果存储）
        // 注意：只有当 G3 任务关联了真实的 AppSpec 时才保存到 ValidationService
        // 否则会因为外键约束（validation_results_app_spec_id_fkey）失败并导致事务回滚
        UUID appSpecId = job.getAppSpecId();
        if (appSpecId != null && job.getTenantId() != null) {
            try {
                // 使用G3ValidationAdapter将CompileResult转换为ValidationResponse
                com.ingenio.backend.dto.response.validation.ValidationResponse validationResponse = g3ValidationAdapter
                        .toValidationResponse(compileResult, job.getId());

                // 保存到ValidationService的统一结果表
                validationService.saveExternalValidationResult(
                        appSpecId,
                        job.getTenantId(),
                        validationResponse,
                        com.ingenio.backend.entity.ValidationResultEntity.ValidationType.COMPILE);

                log.debug("G3验证结果已同步到ValidationService - jobId: {}, appSpecId: {}, tenantId: {}, passed: {}",
                        job.getId(), appSpecId, job.getTenantId(), validationResponse.getPassed());

            } catch (Exception e) {
                // 不影响G3主流程 - ValidationService集成失败只记录日志
                log.warn("同步G3验证结果到ValidationService失败（不影响G3流程） - jobId: {}, error: {}",
                        job.getId(), e.getMessage());
            }
        } else {
            log.debug("跳过ValidationService同步（无AppSpec关联） - jobId: {}", job.getId());
        }

        return validationResult;
    }

    /**
     * 使用 Goose（或任意外部命令）执行本地编译（带环境错误自动重试）。
     *
     * <p>
     * 当前实现定位：
     * </p>
     * <ul>
     * <li>“融合点”：复用现有本地沙箱落盘逻辑 + 编译输出解析逻辑；</li>
     * <li>“可插拔”：通过配置注入具体 Goose 命令，避免在代码中硬编码 Goose CLI 细节；</li>
     * <li>“可回退”：命令未配置或执行失败时，回退到本地 Maven 编译以保证闭环可用。</li>
     * </ul>
     */
    private CompileResult runLocalGooseBuildWithRetry(
            G3JobEntity job,
            List<G3ArtifactEntity> artifacts,
            Consumer<G3LogEntry> logConsumer) {
        CompileResult result = null;

        for (int attempt = 1; attempt <= ENV_ERROR_MAX_RETRIES; attempt++) {
            result = runLocalGooseBuild(job, artifacts, logConsumer);

            if (result.success()) {
                return result;
            }

            if (!result.isEnvironmentError()) {
                return result;
            }

            if (attempt < ENV_ERROR_MAX_RETRIES) {
                logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                        String.format("Goose 编译检测到环境错误，%d秒后重试 (第%d/%d次)...",
                                envErrorRetryDelayMs / 1000, attempt + 1, ENV_ERROR_MAX_RETRIES)));

                try {
                    Thread.sleep(envErrorRetryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return result;
    }

    /**
     * 使用 Goose（或任意外部命令）执行本地编译。
     *
     * <p>
     * 注意：
     * </p>
     * <ul>
     * <li>该方法会将 artifacts 落盘为本地 Maven 工程目录；</li>
     * <li>默认通过 `bash -lc` 执行配置的 compile-command，以支持复杂命令（如 Goose 包装执行）；</li>
     * <li>若未配置 compile-command，则直接回退到本地 Maven 编译。</li>
     * </ul>
     */
    private CompileResult runLocalGooseBuild(
            G3JobEntity job,
            List<G3ArtifactEntity> artifacts,
            Consumer<G3LogEntry> logConsumer) {
        if (gooseCompileCommand == null || gooseCompileCommand.isBlank()) {
            logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                    "未配置 ingenio.g3.sandbox.goose.compile-command，回退到本地 Maven 编译验证"));
            return runLocalMavenBuild(job, artifacts, logConsumer);
        }

        long start = System.currentTimeMillis();

        Path workDir = LOCAL_SANDBOX_ROOT
                .resolve(job.getId().toString())
                .resolve(String.valueOf(job.getCurrentRound() == null ? 0 : job.getCurrentRound()));

        try {
            Files.createDirectories(workDir);
            materializeArtifactsToLocal(workDir, artifacts);
        } catch (Exception e) {
            String msg = "Goose 本地沙箱写入文件失败: " + e.getMessage();
            logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.EXECUTOR, msg));
            return CompileResult.failure(1, "", msg, (int) (System.currentTimeMillis() - start), List.of());
        }

        logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.EXECUTOR,
                "开始 Goose 本地编译验证（目录: " + workDir + "）"));

        String output;
        int exitCode;
        boolean finished;

        StreamCollector collector = new StreamCollector();

        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-lc", gooseCompileCommand);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            collector.start(process.getInputStream());

            finished = process.waitFor(compileTimeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                exitCode = 124;
                output = collector.stopAndGet() + "\n[GOOSE_LOCAL] 编译超时（已强制终止）";
            } else {
                exitCode = process.exitValue();
                output = collector.stopAndGet();
            }

        } catch (IOException e) {
            // bash 不存在的情况：尝试用 sh 兜底
            try {
                ProcessBuilder pb = new ProcessBuilder("sh", "-lc", gooseCompileCommand);
                pb.directory(workDir.toFile());
                pb.redirectErrorStream(true);

                Process process = pb.start();
                collector.start(process.getInputStream());

                finished = process.waitFor(compileTimeout, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    exitCode = 124;
                    output = collector.stopAndGet() + "\n[GOOSE_LOCAL] 编译超时（已强制终止）";
                } else {
                    exitCode = process.exitValue();
                    output = collector.stopAndGet();
                }
            } catch (Exception ex) {
                exitCode = 1;
                finished = false;
                output = "[GOOSE_LOCAL] 编译异常: " + ex.getMessage();
                logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.EXECUTOR, output));
            }
        } catch (Exception e) {
            finished = false;
            exitCode = 1;
            output = "[GOOSE_LOCAL] 编译异常: " + e.getMessage();
            logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.EXECUTOR, output));
        }

        int durationMs = (int) (System.currentTimeMillis() - start);
        boolean success = finished && exitCode == 0;

        if (success) {
            logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR,
                    "Goose 本地编译验证通过（耗时 " + durationMs + "ms）"));
            return CompileResult.success(output, durationMs);
        }

        // 解析编译错误
        List<CompileError> errors = parseCompilerErrors(output);

        // 检查是否是环境类错误
        if (errors.isEmpty()) {
            String envError = detectEnvironmentError(output);
            if (envError != null) {
                logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                        "Goose 本地编译检测到环境错误: " + envError));
                return CompileResult.environmentFailure(exitCode, output, "", durationMs, envError);
            }
        }

        logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                "Goose 本地编译失败（exitCode=" + exitCode + "，耗时 " + durationMs + "ms）"));
        return CompileResult.failure(exitCode, output, "", durationMs, errors);
    }

    /**
     * 使用 OpenLovable 远程沙箱执行 Maven 编译，并在遇到“沙箱状态异常/低信息错误”时自动重建重试。
     *
     * 设计目标：
     * - OpenLovable 的沙箱是单例活跃态（active sandbox），服务重启/沙箱被销毁会导致 400 No active sandbox
     * - 若编译只返回 "Command failed" 且无 Maven 详细输出，Coach 无法修复，应先重建沙箱再试
     * - 若仍失败，则由上层决定是否回退本地编译
     */
    private CompileResult runRemoteMavenBuildWithResetRetry(
            G3JobEntity job,
            List<G3ArtifactEntity> artifacts,
            Consumer<G3LogEntry> logConsumer) {
        CompileResult result = null;

        for (int attempt = 1; attempt <= ENV_ERROR_MAX_RETRIES; attempt++) {
            try {
                String sandboxId = job.getSandboxId();

                // 若当前 Job 记录的 sandbox 不存在/不可用，主动重建
                if (sandboxId == null || sandboxId.isBlank() || !isSandboxAlive(sandboxId)) {
                    SandboxInfo info = createSandbox(job, logConsumer);
                    sandboxId = info.sandboxId();
                }

                // 同步文件（若出现“无活跃沙箱/ID 不匹配”，尝试通过重建解决）
                syncFiles(sandboxId, artifacts, logConsumer);

                // 执行编译（单次）
                result = runMavenBuild(sandboxId, logConsumer);

                if (result.success()) {
                    return result;
                }

                // 代码错误：交给 Coach 修复，不做重建重试
                if (!result.isEnvironmentError()) {
                    return result;
                }

                // 环境错误：根据错误特征决定是否“重建沙箱后重试”
                boolean shouldReset = shouldResetSandboxForEnvironmentError(result);
                if (!shouldReset || attempt >= ENV_ERROR_MAX_RETRIES) {
                    return result;
                }

                logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                        String.format("远程沙箱疑似异常（环境错误），将重建沙箱后重试 (第%d/%d次)...",
                                attempt + 1, ENV_ERROR_MAX_RETRIES)));

                resetRemoteSandbox(job, logConsumer);
                Thread.sleep(envErrorRetryDelayMs);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // OpenLovable 调用异常大概率是环境问题；尝试重建再重试
                String msg = e.getMessage() == null ? "" : e.getMessage();
                logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                        "远程沙箱调用异常（将尝试重建重试）: " + msg));

                if (attempt >= ENV_ERROR_MAX_RETRIES) {
                    throw e;
                }

                resetRemoteSandbox(job, logConsumer);
                try {
                    Thread.sleep(envErrorRetryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return result == null
                ? CompileResult.environmentFailure(-1, "", "", 0, "远程沙箱编译失败（未获得有效输出）")
                : result;
    }

    /**
     * 判断环境错误是否应触发“重建沙箱”。
     *
     * 说明：
     * - 仅对“沙箱状态异常/低信息错误”做重建，避免对单纯网络抖动/依赖下载慢造成不必要的重建
     */
    private boolean shouldResetSandboxForEnvironmentError(CompileResult result) {
        if (result == null || !result.isEnvironmentError()) {
            return false;
        }

        String msg = "";
        if (result.errors() != null && !result.errors().isEmpty() && result.errors().get(0) != null) {
            msg = toString(result.errors().get(0).message());
        }
        String output = combineOutput(result.stdout(), result.stderr());
        String normalized = (msg + "\n" + output).toLowerCase();

        // OpenLovable / 沙箱状态异常典型特征
        return normalized.contains("no active sandbox")
                || normalized.contains("sandbox id mismatch")
                || normalized.contains("unexpected end of file")
                || normalized.contains("i/o error on post request")
                || normalized.contains("command failed")
                || normalized.contains("openlovable") && normalized.contains("未返回");
    }

    /**
     * 重置远程沙箱（best-effort）。
     *
     * 注意：
     * - OpenLovable 当前为“单例活跃沙箱”模型，重置会影响同一 OpenLovable 实例上的其他调用
     * - 这里以“保证编译闭环可用”为目标，优先确保 G3 可继续推进
     */
    private void resetRemoteSandbox(G3JobEntity job, Consumer<G3LogEntry> logConsumer) {
        String sandboxId = job.getSandboxId();
        if (sandboxId != null && !sandboxId.isBlank()) {
            try {
                destroySandbox(sandboxId);
            } catch (Exception e) {
                log.debug("[G3SandboxService] 重置沙箱时销毁失败（忽略）: {}", e.getMessage());
            }
        }

        // 清理 Job 记录与本地缓存，确保下次 createSandbox 触发“新建”
        job.setSandboxId(null);
        job.setSandboxUrl(null);
        job.setSandboxProvider(null);
        // 从 Redis 清理沙箱缓存
        removeSandboxFromRedis(job.getId());
    }

    // ========== Redis 缓存辅助方法 ==========

    /**
     * 保存沙箱信息到 Redis
     */
    private void saveSandboxToRedis(UUID jobId, SandboxInfo info) {
        try {
            String key = SANDBOX_CACHE_PREFIX + jobId.toString();
            Map<String, String> data = new HashMap<>();
            data.put("sandboxId", info.sandboxId());
            data.put("provider", info.provider());
            data.put("url", info.url());
            data.put("createdAt", info.createdAt().toString());
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, java.time.Duration.ofHours(2));
            log.debug("[G3SandboxService] 沙箱信息已缓存到 Redis: jobId={}", jobId);
        } catch (Exception e) {
            log.warn("[G3SandboxService] 缓存沙箱信息到 Redis 失败: {}", e.getMessage());
        }
    }

    /**
     * 从 Redis 移除沙箱缓存
     */
    private void removeSandboxFromRedis(UUID jobId) {
        try {
            String key = SANDBOX_CACHE_PREFIX + jobId.toString();
            redisTemplate.delete(key);
            log.debug("[G3SandboxService] Redis 沙箱缓存已清理: jobId={}", jobId);
        } catch (Exception e) {
            log.warn("[G3SandboxService] 清理 Redis 沙箱缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 本地执行 Maven 编译（OpenLovable 不可用时的回退方案）
     *
     * 说明：
     * - 该回退用于提升本地开发/CI 的可用性，避免外部沙箱依赖导致“必然失败”
     * - 产物会写入 `backend/target/g3-local-sandbox/{jobId}/{round}/` 目录，便于失败排查
     *
     * @param job         G3任务
     * @param artifacts   产物列表（已包含 pom.xml / 构建脚手架）
     * @param logConsumer 日志回调
     * @return 编译结果
     */
    private CompileResult runLocalMavenBuild(
            G3JobEntity job,
            List<G3ArtifactEntity> artifacts,
            Consumer<G3LogEntry> logConsumer) {
        long start = System.currentTimeMillis();

        Path workDir = LOCAL_SANDBOX_ROOT
                .resolve(job.getId().toString())
                .resolve(String.valueOf(job.getCurrentRound() == null ? 0 : job.getCurrentRound()));

        try {
            Files.createDirectories(workDir);
            materializeArtifactsToLocal(workDir, artifacts);
        } catch (Exception e) {
            String msg = "本地沙箱写入文件失败: " + e.getMessage();
            logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.EXECUTOR, msg));
            return CompileResult.failure(1, "", msg, (int) (System.currentTimeMillis() - start), List.of());
        }

        logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.EXECUTOR,
                "开始本地 Maven 编译验证（目录: " + workDir + "）"));

        String output;
        int exitCode;
        boolean finished = false;

        StreamCollector collector = new StreamCollector();

        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", "-e", "-B", "-DskipTests", "compile");
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            collector.start(process.getInputStream());

            finished = process.waitFor(compileTimeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                exitCode = 124;
                output = collector.stopAndGet() + "\n[LOCAL_SANDBOX] 编译超时（已强制终止）";
            } else {
                exitCode = process.exitValue();
                output = collector.stopAndGet();
            }

        } catch (Exception e) {
            finished = false;
            exitCode = 1;
            output = "[LOCAL_SANDBOX] 编译异常: " + e.getMessage();
            logConsumer.accept(G3LogEntry.error(G3LogEntry.Role.EXECUTOR, output));
        }

        boolean success = finished && exitCode == 0;
        int durationMs = (int) (System.currentTimeMillis() - start);

        if (success) {
            logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR,
                    "本地编译验证通过（耗时 " + durationMs + "ms）"));
            return CompileResult.success(output, durationMs);
        }

        // 解析编译错误
        List<CompileError> errors = parseCompilerErrors(output);

        // 检查是否是环境类错误
        if (errors.isEmpty()) {
            String envError = detectEnvironmentError(output);
            if (envError != null) {
                logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                        "本地编译检测到环境错误: " + envError));
                return CompileResult.environmentFailure(exitCode, output, "", durationMs, envError);
            }
        }

        logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                "本地编译失败（exitCode=" + exitCode + "，耗时 " + durationMs + "ms）"));
        return CompileResult.failure(exitCode, output, "", durationMs, errors);
    }

    /**
     * 将产物落盘为本地 Maven 工程
     */
    private void materializeArtifactsToLocal(Path workDir, List<G3ArtifactEntity> artifacts) throws IOException {
        for (G3ArtifactEntity artifact : artifacts) {
            if (artifact == null || artifact.getFilePath() == null)
                continue;
            Path filePath = workDir.resolve(artifact.getFilePath());
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, artifact.getContent() == null ? "" : artifact.getContent(),
                    StandardCharsets.UTF_8);
        }
    }

    /**
     * 生成 BeanConverter 工具类（最小可用版）
     *
     * 背景：
     * - 模型在生成 Service/Controller 时，容易引用 `com.ingenio.backend.util.BeanConverter`
     * 做对象转换
     * - 该类若不存在会导致编译失败，影响“一次性生成可交付”的成功率
     *
     * 设计原则：
     * - 只提供最小能力：convert / copyProperties / convertList
     * - 依赖 Spring 自带 BeanUtils，避免额外三方依赖
     */
    public String generateBeanConverterContent() {
        return """
                package com.ingenio.backend.util;

                import org.springframework.beans.BeanUtils;
                import org.springframework.stereotype.Component;

                import java.util.ArrayList;
                import java.util.List;

                /**
                 * 对象转换/拷贝工具组件（G3 生成代码兜底）
                 *
                 * <p>用途：为生成的 Service 层提供最小可用的对象转换能力，避免引用缺失工具类导致编译失败。</p>
                 *
                 * <p>注意：该实现以“可编译、可运行”为第一目标；复杂映射建议在业务代码中显式编写。</p>
                 */
                @Component
                public class BeanConverter {

                    /**
                     * 将 source 转换为 targetClass 类型（基于属性名拷贝）
                     *
                     * @param source      源对象（可为空）
                     * @param targetClass 目标类型
                     * @param <T>         目标类型
                     * @return 目标对象（source 为空则返回 null）
                     */
                    public <T> T convert(Object source, Class<T> targetClass) {
                        if (source == null) {
                            return null;
                        }
                        try {
                            T target = targetClass.getDeclaredConstructor().newInstance();
                            BeanUtils.copyProperties(source, target);
                            return target;
                        } catch (Exception e) {
                            throw new IllegalStateException("对象转换失败: " + targetClass.getSimpleName(), e);
                        }
                    }

                    /**
                     * 将 source 的同名属性拷贝到 target
                     *
                     * @param source 源对象（可为空）
                     * @param target 目标对象（不可为空）
                     */
                    public void copyProperties(Object source, Object target) {
                        if (source == null || target == null) {
                            return;
                        }
                        BeanUtils.copyProperties(source, target);
                    }

                    /**
                     * 列表转换（逐项 convert）
                     *
                     * @param sourceList  源列表（可为空）
                     * @param targetClass 目标类型
                     * @param <S>         源类型
                     * @param <T>         目标类型
                     * @return 目标列表（sourceList 为空返回空列表）
                     */
                    public <S, T> List<T> convertList(List<S> sourceList, Class<T> targetClass) {
                        if (sourceList == null || sourceList.isEmpty()) {
                            return List.of();
                        }
                        List<T> result = new ArrayList<>(sourceList.size());
                        for (S item : sourceList) {
                            result.add(convert(item, targetClass));
                        }
                        return result;
                    }
                }
                """;
    }

    /**
     * 本地进程输出收集器（避免 stdout buffer 导致死锁）
     */
    private static class StreamCollector {
        private final StringBuilder sb = new StringBuilder();
        private Thread thread;

        public void start(InputStream inputStream) {
            thread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                } catch (IOException ignored) {
                    // ignore
                }
            }, "g3-local-sandbox-stream");
            thread.setDaemon(true);
            thread.start();
        }

        public String stopAndGet() {
            if (thread != null) {
                try {
                    thread.join(2000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            return sb.toString();
        }
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
        // 兼容 Windows 路径（C:\path\to\File.java）
        Pattern mavenPattern = Pattern.compile(
                "\\[ERROR\\]\\s+(.+?\\.java):\\[(\\d+),(\\d+)\\]\\s*(error|warning)?:?\\s*(.+)");

        // javac直接输出格式：File.java:line: error: message
        Pattern javacPattern = Pattern.compile(
                "(.+?\\.java):(\\d+):\\s*(error|warning)?:?\\s*(.+)");

        // 去重：Maven 输出通常会重复打印同一错误（例如汇总段落），避免错误数量被放大
        Set<String> seen = new LinkedHashSet<>();
        String[] lines = output.split("\n");

        for (String line : lines) {
            if (line == null)
                continue;
            line = line.replace("\r", "");
            Matcher mavenMatcher = mavenPattern.matcher(line);
            if (mavenMatcher.find()) {
                String file = mavenMatcher.group(1);
                int lineNo = Integer.parseInt(mavenMatcher.group(2));
                int colNo = Integer.parseInt(mavenMatcher.group(3));
                String severity = mavenMatcher.group(4) != null ? mavenMatcher.group(4) : "error";
                String message = mavenMatcher.group(5).trim();
                String key = file + ":" + lineNo + ":" + colNo + ":" + severity + ":" + message;
                if (seen.add(key)) {
                    errors.add(new CompileError(file, lineNo, colNo, message, severity));
                }
                continue;
            }

            Matcher javacMatcher = javacPattern.matcher(line);
            if (javacMatcher.find()) {
                String file = javacMatcher.group(1);
                int lineNo = Integer.parseInt(javacMatcher.group(2));
                String severity = javacMatcher.group(3) != null ? javacMatcher.group(3) : "error";
                String message = javacMatcher.group(4).trim();
                String key = file + ":" + lineNo + ":0:" + severity + ":" + message;
                if (seen.add(key)) {
                    errors.add(new CompileError(file, lineNo, 0, message, severity));
                }
            }
        }

        return errors;
    }

    /**
     * 检测是否是环境类错误（可自动重试）
     *
     * 环境类错误特征：
     * - Maven依赖下载失败：Could not resolve dependencies / Could not transfer artifact
     * - 网络超时：Connection timed out / Read timed out
     * - 仓库不可用：Failed to read artifact descriptor
     * - 沙箱问题：Sandbox / timeout
     *
     * @param output 编译输出
     * @return 如果是环境错误返回错误原因，否则返回null
     */
    public String detectEnvironmentError(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }

        String normalized = output.toLowerCase();

        // OpenLovable 执行器/沙箱命令执行失败（常见表现：只返回 "Command failed" 且无 Maven 详细输出）
        if (normalized.trim().startsWith("command failed")) {
            return "沙箱执行命令失败（OpenLovable 未返回 Maven 详细输出）";
        }

        // OpenLovable 沙箱状态丢失（服务重启/沙箱被销毁/并发抢占）
        if (normalized.contains("no active sandbox")) {
            return "沙箱不可用（OpenLovable 无活跃沙箱）";
        }
        if (normalized.contains("sandbox id mismatch")) {
            return "沙箱不可用（OpenLovable 活跃沙箱与请求不一致）";
        }

        // Maven 未安装/命令不可用
        if (normalized.contains("mvn: not found") ||
                normalized.contains("mvn: command not found") ||
                normalized.contains("'mvn' is not recognized") ||
                normalized.contains("mvn 命令不存在")) {
            return "Maven 未安装或不可用";
        }

        // 依赖下载失败
        if (normalized.contains("could not resolve dependencies") ||
                normalized.contains("could not transfer artifact") ||
                normalized.contains("failed to read artifact descriptor") ||
                normalized.contains("cannot access central") ||
                normalized.contains("could not find artifact")) {
            return "Maven依赖下载失败（网络问题或仓库不可用）";
        }

        // 网络超时
        if (normalized.contains("connection timed out") ||
                normalized.contains("read timed out") ||
                normalized.contains("connect timed out") ||
                normalized.contains("sockettimeoutexception")) {
            return "网络连接超时";
        }

        // 沙箱问题
        if (normalized.contains("sandbox") && normalized.contains("timeout")) {
            return "沙箱执行超时";
        }

        // Maven仓库认证/访问问题
        if (normalized.contains("not authorized") ||
                normalized.contains("access denied") ||
                normalized.contains("transfer failed")) {
            return "Maven仓库访问失败";
        }

        // 未知构建失败但无Java编译错误（很可能是环境问题）
        if (normalized.contains("build failure") &&
                !normalized.contains(".java:") &&
                !normalized.contains("error:")) {
            return "构建失败（可能是环境问题）";
        }

        return null;
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

        // 兜底：构建失败但未能定位到具体文件时，至少将错误挂到 pom.xml（便于 Coach 继续修复）
        if (!compileResult.success()) {
            boolean anyMarked = artifacts.stream().anyMatch(a -> Boolean.TRUE.equals(a.getHasErrors()));
            if (!anyMarked) {
                artifacts.stream()
                        .filter(a -> "pom.xml".equals(a.getFileName()))
                        .findFirst()
                        .ifPresent(pom -> {
                            String summary = extractBuildFailureSummary(combinedOutput);

                            // 若为环境错误且输出低信息（如 Command failed），优先展示环境错误原因
                            if (compileResult.isEnvironmentError() && !compileResult.errors().isEmpty()) {
                                CompileError first = compileResult.errors().get(0);
                                if (first != null && first.message() != null && !first.message().isBlank()) {
                                    summary = first.message();
                                }
                            }

                            pom.markError(summary);
                        });
            }
        }
    }

    /**
     * 提取构建失败摘要（控制长度，避免日志/DB 过大）
     */
    private static String extractBuildFailureSummary(String output) {
        if (output == null || output.isBlank()) {
            return "构建失败（输出为空）";
        }
        String normalized = output.replace("\r", "");
        String[] lines = normalized.split("\n");
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
            // 没有明显错误行则取末尾
            int tail = Math.min(60, lines.length);
            for (int i = lines.length - tail; i < lines.length; i++) {
                if (i < 0)
                    continue;
                String line = lines[i].trim();
                if (line.isEmpty())
                    continue;
                sb.append(line).append("\n");
            }
        }
        String summary = sb.toString();
        return summary.length() > 8000 ? summary.substring(0, 8000) + "\n... (已截断)" : summary;
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
            String url = resolveOpenLovableBaseUrlBySandboxId(sandboxId) + "/api/sandbox/kill";

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
            // 说明：
            // - OpenLovable-CN 的 /api/sandbox-status 当前返回字段为 { active, healthy, sandboxData
            // }
            // - 不支持通过 query 参数精准查询某个 sandboxId，因此这里只能“查询当前活跃沙箱”的健康状态
            String url = resolveOpenLovableBaseUrlBySandboxId(sandboxId) + "/api/sandbox-status";

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<Map<String, Object>>() {
                        });

                boolean active = Boolean.TRUE.equals(result.get("active"));
                boolean healthy = Boolean.TRUE.equals(result.get("healthy"));

                // 若 OpenLovable 当前活跃沙箱与本 Job 记录不一致，视为“不可复用”
                String activeSandboxId = null;
                Object sandboxData = result.get("sandboxData");
                if (sandboxData instanceof Map<?, ?> data) {
                    Object id = data.get("sandboxId");
                    if (id != null) {
                        activeSandboxId = String.valueOf(id);
                    }
                }

                if (activeSandboxId != null && !activeSandboxId.isBlank() && !sandboxId.equals(activeSandboxId)) {
                    return false;
                }

                return active && healthy;
            }

        } catch (Exception e) {
            log.debug("[G3SandboxService] 检查沙箱状态失败: {}", e.getMessage());
        }

        return false;
    }


    /**
     * 解析 sandbox 对应的 OpenLovable baseUrl
     */
    private String resolveOpenLovableBaseUrlBySandboxId(String sandboxId) {
        if (endpointRouter == null) {
            return normalizeOpenLovableBaseUrl(openLovableBaseUrl);
        }
        if (sandboxId != null && !sandboxId.isBlank()) {
            return endpointRouter.resolveEndpointForSandbox(sandboxId.trim());
        }
        return endpointRouter.getDefaultEndpoint();
    }

    /**
     * 选择“创建新沙箱”使用的 OpenLovable 实例
     */
    private String selectOpenLovableBaseUrlForCreate() {
        if (endpointRouter == null) {
            return normalizeOpenLovableBaseUrl(openLovableBaseUrl);
        }
        return endpointRouter.selectEndpointForCreate();
    }

    /**
     * 绑定 sandbox 与 OpenLovable 实例
     */
    private void bindSandboxEndpoint(String sandboxId, String baseUrl) {
        if (endpointRouter == null || sandboxId == null || sandboxId.isBlank()) {
            return;
        }
        endpointRouter.bindSandbox(sandboxId.trim(), baseUrl);
    }

    /**
     * 注册沙箱 READY 状态
     *
     * 是什么：G3 创建沙箱后的状态落库动作。
     * 做什么：写入状态中心，供回收器与并发组件复用。
     * 为什么：避免 G3 链路创建的沙箱脱离统一治理。
     */
    private void registerSandboxReady(String sandboxId, String baseUrl) {
        if (sandboxRegistry == null || sandboxId == null || sandboxId.isBlank()) {
            return;
        }
        try {
            sandboxRegistry.registerReady(sandboxId, baseUrl, null, null);
        } catch (Exception e) {
            log.debug("[G3SandboxService] 注册沙箱状态失败: sandboxId={}, err={}", sandboxId, e.getMessage());
        }
    }

    /**
     * 规范化 baseUrl（去除末尾斜杠）
     */
    private String normalizeOpenLovableBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "http://localhost:3001";
        }
        String trimmed = baseUrl.trim();
        if (trimmed.isBlank()) {
            return "http://localhost:3001";
        }
        return trimmed.replaceAll("/+$", "");
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
                    "pom.xml", // 放在项目根目录
                    pomContent,
                    G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                    job.getCurrentRound());

            allArtifacts.add(pomArtifact);
            logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR, "pom.xml生成完成"));
        }

        // 同时生成UUIDv8TypeHandler（生成的代码依赖它）
        boolean hasTypeHandler = artifacts.stream()
                .anyMatch(a -> a.getFileName().contains("UUIDv8TypeHandler"));

        if (!hasTypeHandler) {
            String typeHandlerContent = generateUUIDv8TypeHandlerContent();
            G3ArtifactEntity typeHandlerArtifact = G3ArtifactEntity.create(
                    job.getId(),
                    "src/main/java/com/ingenio/backend/config/UUIDv8TypeHandler.java",
                    typeHandlerContent,
                    G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                    job.getCurrentRound());
            allArtifacts.add(typeHandlerArtifact);
        }

        return allArtifacts;
    }

    /**
     * 生成UUIDv8TypeHandler类内容
     *
     * 用途：
     * - G3 生成的 MyBatis-Plus 代码会引用该 TypeHandler
     * - 将其作为“构建脚手架”与业务代码一并纳入产物/修复闭环
     */
    public String generateUUIDv8TypeHandlerContent() {
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
