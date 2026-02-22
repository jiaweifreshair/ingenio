package com.ingenio.backend.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.service.openlovable.OpenLovableRepairContextBuilder;
import com.ingenio.backend.service.openlovable.OpenLovableRepairContextInput;
import com.ingenio.backend.service.openlovable.OpenLovableRepairIntentResult;
import com.ingenio.backend.service.openlovable.OpenLovableRepairIntentRouter;
import com.ingenio.backend.service.openlovable.OpenLovableRepairMemory;
import com.ingenio.backend.service.openlovable.OpenLovableRepairMemoryService;
import com.ingenio.backend.service.openlovable.OpenLovableRepairSummaryUpdate;
import com.ingenio.backend.service.openlovable.OpenLovableEndpointRouter;
import com.ingenio.backend.service.openlovable.OpenLovableSandboxLockService;
import com.ingenio.backend.service.openlovable.OpenLovableSandboxRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.ingenio.backend.service.openlovable.OpenLovableResponseSanitizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Open-Lovable集成控制器
 *
 * 功能：
 * 1. 创建AI沙箱 - 代理Open-Lovable的沙箱创建API
 * 2. 生成AI代码 - 流式代理代码生成API
 * 3. 获取沙箱状态 - 查询沙箱运行状态
 * 4. 快速Web预览 - 5-10秒生成Web应用预览
 *
 * 架构说明：
 * - Open-Lovable服务运行在3001端口（Docker部署默认3001:3000映射）
 * - Ingenio后端作为代理层统一对外暴露API
 * - 支持SSE流式响应
 * - V2.0架构：Sandbox创建是Plan阶段的快速预览功能，无需登录认证
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/openlovable")
@RequiredArgsConstructor
public class OpenLovableController {

    @Value("${ingenio.openlovable.base-url:http://localhost:3001}")
    private String openLovableBaseUrl;

    /**
     * create-zip 回退最大重试次数
     *
     * 是什么：回退到沙箱执行 zip 时的最大尝试次数。
     * 做什么：降低沙箱未就绪导致的下载失败。
     * 为什么：提升“下载前端页面”稳定性，避免偶发 500。
     */
    @Value("${ingenio.openlovable.create-zip.max-attempts:3}")
    private int createZipMaxAttempts = 3;

    /**
     * create-zip 回退重试间隔（毫秒）
     *
     * 是什么：回退失败后等待的间隔时间。
     * 做什么：给沙箱准备时间，避免频繁请求。
     * 为什么：兼顾稳定性与响应速度。
     */
    @Value("${ingenio.openlovable.create-zip.retry-delay-ms:1500}")
    private long createZipRetryDelayMs = 1500L;

    /**
     * create-zip 锁等待重试间隔（毫秒）
     *
     * 是什么：当 create-zip 首次获取沙箱锁失败时的补偿等待间隔。
     * 做什么：在高并发场景下给前序请求释放锁的时间，并执行一次快速重试。
     * 为什么：E2E 三场景并发触发下载时，避免因毫秒级抢锁失败直接返回 409。
     */
    @Value("${ingenio.openlovable.create-zip.lock-retry-delay-ms:800}")
    private long createZipLockRetryDelayMs = 800L;

    /**
     * 创建沙箱默认禁用复用
     *
     * 是什么：创建沙箱时的“默认不复用”策略开关。
     * 做什么：默认要求创建新沙箱，避免把不同用户绑定到同一活动沙箱。
     * 为什么：单活复用场景下容易出现跨用户代码串线，影响隔离性与用户体验。
     */
    @Value("${ingenio.openlovable.create.disable-reuse-by-default:true}")
    private boolean disableReuseByDefault = true;

    /**
     * apply 增量合并阈值（文件数）
     *
     * 是什么：判定“增量修复”的文件数量阈值。
     * 做什么：当 AI 输出文件数较少时触发沙箱文件合并。
     * 为什么：避免 patch apply 覆盖导致文件丢失与白屏。
     */
    @Value("${ingenio.openlovable.apply.merge-threshold:6}")
    private int applyMergeThreshold = 6;

    /**
     * apply 增量合并最大读取文件数
     *
     * 是什么：单次合并可读取的最大沙箱文件数量。
     * 做什么：限制沙箱读取规模，防止超时或内存飙升。
     * 为什么：大型项目或包含依赖目录时需要保护性能。
     */
    @Value("${ingenio.openlovable.apply.merge-max-files:200}")
    private int applyMergeMaxFiles = 200;

    /**
     * OpenLovable 沙箱工作目录
     *
     * 是什么：OpenLovable 沙箱默认项目根目录。
     * 做什么：用于拼接执行命令与文件路径。
     * 为什么：避免不同控制器重复硬编码路径。
     */
    private static final String SANDBOX_WORKDIR = "/home/user/app";

    /**
     * 合并时需要排除的二进制资源后缀
     *
     * 是什么：常见二进制文件扩展名列表。
     * 做什么：防止读取二进制内容导致编码异常或响应膨胀。
     * 为什么：增量合并只需要文本文件即可修复白屏。
     */
    private static final Set<String> MERGE_BINARY_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".bmp",
            ".mp4", ".mp3", ".wav", ".ogg", ".zip", ".tar", ".gz", ".tgz",
            ".pdf", ".woff", ".woff2", ".ttf", ".eot");

    /**
     * Tailwind 颜色别名映射
     *
     * 是什么：将非官方色名映射到 Tailwind 内置色名的映射表。
     * 做什么：为 sand/seafoam/sage/coral 等别名提供可用的替代色。
     * 为什么：沙箱禁止覆盖 tailwind.config 时，这些自定义色会导致 @apply 报错白屏。
     */
    private static final Map<String, String> TAILWIND_COLOR_ALIAS_MAP = Map.of(
            "sand", "stone",
            "seafoam", "emerald",
            "sage", "green",
            "coral", "rose");

    /**
     * Tailwind 颜色别名匹配模式
     *
     * 是什么：匹配 sand/seafoam/sage/coral 等非默认色的 Tailwind 色阶写法。
     * 做什么：定位需要替换的颜色 token（如 sand-50）。
     * 为什么：保证自动替换只作用于颜色 token，避免误伤其他文本。
     */
    private static final Pattern TAILWIND_COLOR_ALIAS_PATTERN = Pattern.compile(
            "\\b(sand|seafoam|sage|coral)-(50|100|200|300|400|500|600|700|800|900|950)\\b");

    private final RestTemplate restTemplate = new RestTemplate();
    private final com.ingenio.backend.langchain4j.model.LangChain4jModelFactory modelFactory;
    private final OpenLovableRepairMemoryService repairMemoryService;
    private final OpenLovableRepairIntentRouter repairIntentRouter;
    private final OpenLovableRepairContextBuilder repairContextBuilder;

    /**
     * OpenLovable 多实例路由器（可选）
     *
     * 是什么：负责 sandboxId 到 OpenLovable 实例地址的绑定。
     * 做什么：在并发场景下把同一 sandbox 的后续操作路由到同一实例。
     * 为什么：避免单活沙箱模式下出现 Sandbox ID mismatch。
     */
    @Autowired(required = false)
    private OpenLovableEndpointRouter endpointRouter;

    /**
     * OpenLovable 沙箱状态注册中心（可选）
     *
     * 是什么：多活改造中的状态中心。
     * 做什么：维护 sandbox 状态、心跳与索引。
     * 为什么：支撑并发锁与回收器的数据一致性。
     */
    @Autowired(required = false)
    private OpenLovableSandboxRegistry sandboxRegistry;

    /**
     * OpenLovable 沙箱锁服务（可选）
     *
     * 是什么：单 sandbox 串行执行锁。
     * 做什么：保护 apply/create-zip/restart/cleanup 等关键写操作。
     * 为什么：避免并发写同一 sandbox 导致状态错乱。
     */
    @Autowired(required = false)
    private OpenLovableSandboxLockService sandboxLockService;

    /**
     * 归一化模型名（用于候选模型去重/稳定性兜底判断）
     *
     * 规则：
     * - 支持 `provider/model` 形式：取最后一个 `/` 之后的尾段作为“模型 key”
     * - 支持裸模型名：直接返回
     *
     * 示例：
     * - deepseek/deepseek-r1-0528 -> deepseek-r1-0528
     * - minimax/minimax-m2.1 -> minimax-m2.1
     * - z-ai/glm-4.7 -> glm-4.7
     */
    private static String normalizeModelKey(String model) {
        if (model == null)
            return "";
        String trimmed = model.trim();
        if (trimmed.isEmpty())
            return "";
        int idx = trimmed.lastIndexOf('/');
        return (idx >= 0 && idx + 1 < trimmed.length()) ? trimmed.substring(idx + 1) : trimmed;
    }

    /**
     * 检测提示词是否残留未替换占位符
     *
     * 是什么：判断增强后的提示词是否仍包含字面量“%s”。
     * 做什么：在进入上游生成前拦截异常提示词，避免上下文污染。
     * 为什么：残留占位符会触发模型误判，导致生成内容跑偏。
     */
    private boolean containsUnresolvedPromptPlaceholder(String prompt, String originalPrompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        if (!prompt.contains("%s")) {
            return false;
        }
        return originalPrompt == null || !originalPrompt.contains("%s");
    }

    /**
     * 创建AI沙箱
     *
     * POST /v1/openlovable/sandbox/create
     *
     * 响应示例：
     * {
     * "success": true,
     * "sandboxId": "sb_xxxxx",
     * "url": "https://xxxxx.vercel.app",
     * "provider": "vercel",
     * "message": "Sandbox created and Vite React app initialized"
     * }
     */
    @PostMapping("/sandbox/create")
    public ResponseEntity<?> createSandbox(@RequestBody(required = false) Map<String, Object> request) {
        String targetBaseUrl = null;
        OpenLovableSandboxLockService.SandboxLockLease createLockLease = null;
        try {
            Map<String, Object> requestBody = request != null ? new HashMap<>(request) : new HashMap<>();
            boolean disableReuse = parseBooleanRequestOption(requestBody, "disableReuse", disableReuseByDefault);
            String userId = getLoginUserIdOrNull();
            String tenantId = getSessionTenantIdOrNull();

            targetBaseUrl = selectOpenLovableBaseUrlForCreate();
            String createLockKey = "create:" + targetBaseUrl;
            createLockLease = acquireSandboxOperationLock(createLockKey, "create-sandbox");
            if (!createLockLease.acquired()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Result.error(409, "沙箱创建繁忙，请稍后重试"));
            }

            Map<String, Object> createPayload = buildCreateSandboxPayload(requestBody, disableReuse);
            Map<String, Object> createdBody = callOpenLovableCreateSandbox(targetBaseUrl, createPayload);

            if (disableReuse && shouldRecreateSandboxForIsolation(createdBody)) {
                String reusedSandboxId = extractSandboxIdFromCreateResponse(createdBody);
                log.warn("检测到沙箱复用，触发独立沙箱重建: sandboxId={}, baseUrl={}", reusedSandboxId, targetBaseUrl);
                tryKillSandboxBeforeCreate(targetBaseUrl, reusedSandboxId);
                createdBody = callOpenLovableCreateSandbox(targetBaseUrl, createPayload);

                if (shouldRecreateSandboxForIsolation(createdBody)) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(Result.error(503, "当前 OpenLovable 实例仍返回复用沙箱，请扩容实例后重试"));
                }
            }

            String createdSandboxId = extractSandboxIdFromCreateResponse(createdBody);
            if (createdSandboxId != null) {
                bindSandboxEndpoint(createdSandboxId, targetBaseUrl);
                registerSandboxReady(createdSandboxId, targetBaseUrl, userId, tenantId);
            }

            log.info("沙箱创建响应: baseUrl={}, userId={}, tenantId={}, body={}",
                    targetBaseUrl,
                    userId,
                    tenantId,
                    createdBody);
            return ResponseEntity.ok(Result.success(createdBody));

        } catch (Exception e) {
            log.error("创建沙箱失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("创建沙箱失败: " + e.getMessage()));
        } finally {
            releaseSandboxLock(createLockLease);
        }
    }

    /**
     * 构建创建沙箱请求体
     *
     * 是什么：将代理层输入转换为上游创建沙箱请求体。
     * 做什么：透传幂等键，并在“禁用复用”策略下显式附带 forceNew 标记。
     * 为什么：统一创建语义，减少调用方分散拼接参数导致的不一致。
     */
    private Map<String, Object> buildCreateSandboxPayload(
            Map<String, Object> requestBody,
            boolean disableReuse) {
        Map<String, Object> payload = new HashMap<>();
        if (requestBody != null) {
            Object idempotencyKey = requestBody.get("idempotencyKey");
            if (idempotencyKey instanceof String key && !key.isBlank()) {
                payload.put("idempotencyKey", key.trim());
            }
        }
        if (disableReuse) {
            payload.put("forceNew", true);
            payload.put("disableReuse", true);
        }
        return payload;
    }

    /**
     * 调用上游创建沙箱接口
     *
     * 是什么：对 `/api/create-ai-sandbox-v2` 的最小封装。
     * 做什么：发送 JSON 请求并返回响应体 Map。
     * 为什么：集中处理创建请求，便于统一日志和错误处理。
     */
    private Map<String, Object> callOpenLovableCreateSandbox(String targetBaseUrl, Map<String, Object> payload) {
        String url = targetBaseUrl + "/api/create-ai-sandbox-v2";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload != null ? payload : Map.of(), headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("创建沙箱失败: HTTP " + response.getStatusCode());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> body = response.getBody() != null
                    ? new HashMap<>(response.getBody())
                    : new HashMap<>();
            return body;
        } catch (HttpStatusCodeException statusError) {
            if (statusError.getStatusCode() == HttpStatus.CONFLICT) {
                Map<String, Object> conflictBody = parseCreateSandboxConflictBody(statusError);
                conflictBody.putIfAbsent("success", false);
                conflictBody.putIfAbsent("requiresConfirmation", true);
                return conflictBody;
            }

            String rawBody = statusError.getResponseBodyAsString();
            String compactBody = rawBody != null && !rawBody.isBlank() ? ", body=" + rawBody : "";
            throw new IllegalStateException(
                    "创建沙箱失败: HTTP " + statusError.getStatusCode() + compactBody,
                    statusError);
        }
    }

    /**
     * 解析创建冲突响应
     *
     * 是什么：`create-ai-sandbox-v2` 冲突异常的响应体解析器。
     * 做什么：将 409 的 JSON body 转为 Map，供上层判断 `requiresConfirmation`。
     * 为什么：RestTemplate 默认会对 4xx 抛异常，不解析会导致“可恢复冲突”被误判成 500。
     */
    private Map<String, Object> parseCreateSandboxConflictBody(HttpStatusCodeException statusError) {
        String rawBody = statusError.getResponseBodyAsString();
        if (rawBody == null || rawBody.isBlank()) {
            return new HashMap<>();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsedBody = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(rawBody, Map.class);
            return new HashMap<>(parsedBody);
        } catch (Exception parseError) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("message", rawBody);
            return fallback;
        }
    }

    /**
     * 判断创建结果是否触发“隔离重建”
     *
     * 是什么：对上游创建响应做复用语义识别。
     * 做什么：识别 `reused=true`、`requiresConfirmation=true`、复用提示文案。
     * 为什么：在“用户独立沙箱”策略下，复用结果必须被拦截并重建。
     */
    private boolean shouldRecreateSandboxForIsolation(Map<String, Object> createResponse) {
        if (createResponse == null || createResponse.isEmpty()) {
            return false;
        }

        Object reusedObj = createResponse.get("reused");
        if (reusedObj instanceof Boolean reused && reused) {
            return true;
        }

        Object requiresConfirmationObj = createResponse.get("requiresConfirmation");
        if (requiresConfirmationObj instanceof Boolean requiresConfirmation && requiresConfirmation) {
            return true;
        }

        Object messageObj = createResponse.get("message");
        if (messageObj instanceof String message && !message.isBlank()) {
            String normalized = message.toLowerCase();
            if (normalized.contains("复用") || normalized.contains("reuse")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从创建响应提取 sandboxId
     *
     * 是什么：创建沙箱响应字段提取器。
     * 做什么：读取并标准化 `sandboxId`。
     * 为什么：避免各调用方重复解析并遗漏空值保护。
     */
    private String extractSandboxIdFromCreateResponse(Map<String, Object> createResponse) {
        if (createResponse == null) {
            return null;
        }
        Object sandboxIdObj = createResponse.get("sandboxId");
        if (!(sandboxIdObj instanceof String sandboxId) || sandboxId.isBlank()) {
            return null;
        }
        return sandboxId.trim();
    }

    /**
     * 创建前销毁复用沙箱
     *
     * 是什么：创建独立沙箱前的预清理动作。
     * 做什么：按 sandboxId 调用上游 kill-sandbox；缺省时尝试通用释放。
     * 为什么：上游单活复用时，需要先释放旧上下文，才能拿到独立沙箱。
     */
    private void tryKillSandboxBeforeCreate(String targetBaseUrl, String sandboxId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String killTargetSandboxId = (sandboxId != null && !sandboxId.isBlank())
                    ? sandboxId.trim()
                    : queryActiveSandboxIdForCreateRecovery(targetBaseUrl);

            if (killTargetSandboxId != null && !killTargetSandboxId.isBlank()) {
                String killUrl = UriComponentsBuilder
                        .fromHttpUrl(targetBaseUrl + "/api/kill-sandbox")
                        .queryParam("sandboxId", killTargetSandboxId)
                        .toUriString();
                HttpEntity<Map<String, Object>> killEntity = new HttpEntity<>(
                        Map.of("sandboxId", killTargetSandboxId),
                        headers);
                restTemplate.postForEntity(killUrl, killEntity, Map.class);
                clearSandboxRoutingAndRegistry(killTargetSandboxId);
                return;
            }

            HttpEntity<Map<String, Object>> killEntity = new HttpEntity<>(Map.of(), headers);
            restTemplate.postForEntity(targetBaseUrl + "/api/kill-sandbox", killEntity, Map.class);
        } catch (Exception killError) {
            log.warn("创建独立沙箱前清理旧沙箱失败: sandboxId={}, err={}", sandboxId, killError.getMessage());
        }
    }

    /**
     * 查询当前活跃沙箱ID（创建恢复）
     *
     * 是什么：创建冲突恢复阶段的 active sandbox 查询。
     * 做什么：读取上游 `/api/sandbox-status`，提取当前活跃 sandboxId。
     * 为什么：上游 409 场景可能不返回 sandboxId，需要先定位活跃实例再精准销毁。
     */
    private String queryActiveSandboxIdForCreateRecovery(String targetBaseUrl) {
        try {
            String statusUrl = targetBaseUrl + "/api/sandbox-status";
            ResponseEntity<Map> statusResponse = restTemplate.getForEntity(statusUrl, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = statusResponse.getBody() != null
                    ? new HashMap<>(statusResponse.getBody())
                    : new HashMap<>();
            return extractActiveSandboxIdFromStatusBody(body);
        } catch (Exception statusError) {
            log.warn("查询活跃沙箱失败（创建恢复）: baseUrl={}, err={}", targetBaseUrl, statusError.getMessage());
            return null;
        }
    }

    /**
     * 从状态响应提取活跃 sandboxId
     *
     * 是什么：`/api/sandbox-status` 的字段兼容提取器。
     * 做什么：优先读取 `sandboxData.sandboxId`，回退读取顶层 `sandboxId`。
     * 为什么：兼容上游不同版本响应结构，保证恢复流程稳定。
     */
    private String extractActiveSandboxIdFromStatusBody(Map<String, Object> statusBody) {
        if (statusBody == null || statusBody.isEmpty()) {
            return null;
        }

        Object sandboxDataObj = statusBody.get("sandboxData");
        if (sandboxDataObj instanceof Map<?, ?> sandboxData) {
            Object activeSandboxIdObj = sandboxData.get("sandboxId");
            if (activeSandboxIdObj instanceof String activeSandboxId && !activeSandboxId.isBlank()) {
                return activeSandboxId.trim();
            }
        }

        Object sandboxIdObj = statusBody.get("sandboxId");
        if (sandboxIdObj instanceof String activeSandboxId && !activeSandboxId.isBlank()) {
            return activeSandboxId.trim();
        }
        return null;
    }

    /**
     * 解析布尔请求参数
     *
     * 是什么：请求体布尔字段容错解析器。
     * 做什么：兼容 Boolean/String/空值三类输入并回退默认值。
     * 为什么：前后端调用阶段可能存在不同序列化策略，避免误判配置开关。
     */
    private boolean parseBooleanRequestOption(Map<String, Object> request, String key, boolean defaultValue) {
        if (request == null || key == null || key.isBlank()) {
            return defaultValue;
        }
        Object value = request.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String textValue) {
            String normalized = textValue.trim();
            if (normalized.isEmpty()) {
                return defaultValue;
            }
            if ("1".equals(normalized)) {
                return true;
            }
            if ("0".equals(normalized)) {
                return false;
            }
            return Boolean.parseBoolean(normalized);
        }
        return defaultValue;
    }

    /**
     * 获取当前登录用户ID（可空）
     *
     * 是什么：白名单接口场景下的用户ID提取器。
     * 做什么：优先读当前登录态，失败时回退 token 解析。
     * 为什么：创建沙箱时记录 userId，便于后续做用户维度隔离与配额治理。
     */
    private String getLoginUserIdOrNull() {
        try {
            String loginId = StpUtil.getLoginIdAsString();
            if (loginId != null && !loginId.isBlank()) {
                return loginId.trim();
            }
        } catch (Exception ignore) {
            // ignore
        }

        try {
            String token = StpUtil.getTokenValue();
            if (token != null && !token.isBlank()) {
                Object loginId = StpUtil.getLoginIdByToken(token);
                if (loginId != null) {
                    return loginId.toString().trim();
                }
            }
        } catch (Exception e) {
            log.debug("创建沙箱时从 token 解析用户ID失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取当前会话租户ID（可空）
     *
     * 是什么：Sa-Token Session 中 tenantId 的读取封装。
     * 做什么：在存在租户上下文时写入注册中心。
     * 为什么：便于后续按租户统计活跃沙箱与限额治理。
     */
    private String getSessionTenantIdOrNull() {
        try {
            var session = StpUtil.getSession(false);
            if (session == null) {
                return null;
            }
            Object tenantId = session.get("tenantId");
            if (tenantId == null) {
                return null;
            }
            String value = tenantId.toString();
            return value.isBlank() ? null : value.trim();
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * 生成AI代码（流式响应）
     *
     * POST /v1/openlovable/generate/stream
     * Content-Type: application/json
     *
     * 请求体：
     * {
     * "userMessage": "创建一个待办事项应用",
     * "model": "deepseek-v3.2"
     * }
     *
     * 响应格式：Server-Sent Events (SSE)
     * data: {"type":"content","content":"..."}
     * data: {"type":"tool_call","name":"writeFile","args":{...}}
     */
    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> generateCodeStream(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {

        try {
            String requestSandboxId = extractSandboxIdFromRequestPayload(request);
            String targetBaseUrl = resolveOpenLovableBaseUrlForRequest(requestSandboxId);
            String url = targetBaseUrl + "/api/generate-ai-code-stream";
            log.info("转发AI代码生成请求: {} -> {}", httpRequest.getRequestURI(), url);
            log.debug("原始请求体: {}", request);

            // 参数适配：OpenLovable期望的参数格式
            Map<String, Object> adaptedRequest = new java.util.HashMap<>(request);

            // 前端透传的“策略字段”不应该直接传给上游（由代理层消费后转换为 model/prompt）
            Object promptProfileObjRaw = adaptedRequest.remove("promptProfile");
            Object modelCandidatesObjRaw = adaptedRequest.remove("modelCandidates");
            Object modelPresetObjRaw = adaptedRequest.remove("modelPreset");
            Object reasoningObjRaw = adaptedRequest.remove("reasoning");

            // 1. 将userMessage/userRequirement转换为prompt
            String originalPrompt = null;
            if (adaptedRequest.containsKey("userMessage")) {
                originalPrompt = (String) adaptedRequest.remove("userMessage");
                log.debug("参数适配: userMessage -> prompt");
            } else if (adaptedRequest.containsKey("userRequirement")) {
                originalPrompt = (String) adaptedRequest.remove("userRequirement");
                log.debug("参数适配: userRequirement -> prompt");
            } else if (adaptedRequest.containsKey("prompt")) {
                originalPrompt = (String) adaptedRequest.get("prompt");
            }

            // 快速预览生成（prototype preview）判断：
            // - 前端快速预览在生成代码时会传 sandboxId=pending（表示先生成代码，后创建沙箱并 apply）
            // - 对这类请求：优先稳定/速度，避免长提示词 + 慢模型导致上游 240s 超时
            boolean isFastPreview = false;
            Object fastPreviewObj = request.get("fastPreview");
            if (fastPreviewObj instanceof Boolean b && b) {
                isFastPreview = true;
            }
            Object sandboxIdObjForDetect = request.get("sandboxId");
            if (sandboxIdObjForDetect instanceof String s && "pending".equalsIgnoreCase(s.trim())) {
                isFastPreview = true;
            }

            // 推理模式（显式请求优先，不被 fast preview 覆盖）
            String promptProfile = null;
            if (promptProfileObjRaw instanceof String s && !s.isBlank()) {
                promptProfile = s.trim();
            } else if (modelPresetObjRaw instanceof String s && !s.isBlank()) {
                // 兼容 preset：deepseek-reasoning
                if ("deepseek-reasoning".equalsIgnoreCase(s.trim())) {
                    promptProfile = "reasoning";
                }
            } else if (reasoningObjRaw instanceof Boolean b && b) {
                promptProfile = "reasoning";
            }

            if ("fast".equalsIgnoreCase(promptProfile)) {
                isFastPreview = true;
            }

            final String effectivePromptProfile;
            if ("reasoning".equalsIgnoreCase(promptProfile)) {
                effectivePromptProfile = "reasoning";
            } else if (isFastPreview) {
                // 需求变更：快速预览默认质量优先（推理模式），仅在用户显式选择 fast 时才走快档
                effectivePromptProfile = "reasoning";
            } else {
                effectivePromptProfile = "quality";
            }

            // 1.6 提取语言设置（用于动态适配生成网站的语言）
            // - "zh" 表示中文网站（默认）
            // - "en" 表示英文网站
            String targetLanguage = "zh"; // 默认中文
            Object languageObj = adaptedRequest.remove("language");
            if (languageObj instanceof String lang && !lang.isBlank()) {
                targetLanguage = lang.trim().toLowerCase();
                log.info("目标语言设置: {}", targetLanguage);
            }
            final String effectiveLanguage = targetLanguage;

            // 1.5 处理 Scout 模版上下文 (Phase 7 Integration)
            if (adaptedRequest.containsKey("templateContext")) {
                String templateContext = (String) adaptedRequest.remove("templateContext");
                if (originalPrompt != null && !templateContext.isBlank()) {
                    originalPrompt = originalPrompt + "\n\n" + templateContext;
                    log.info("已注入 Scout 模版上下文");
                }
            }

            // M1: 处理 Blueprint Markdown（Step 6 生成的技术蓝图）
            // 前端通过 blueprintMarkdown 字段传递完整蓝图，用于约束 OpenLovable 生成
            if (adaptedRequest.containsKey("blueprintMarkdown")) {
                String blueprintMarkdown = (String) adaptedRequest.remove("blueprintMarkdown");
                if (originalPrompt != null && blueprintMarkdown != null && !blueprintMarkdown.isBlank()) {
                    // 将 Blueprint 作为设计约束注入到提示词
                    String blueprintConstraint = buildBlueprintConstraint(blueprintMarkdown);
                    originalPrompt = originalPrompt + blueprintConstraint;
                    log.info("已注入 Blueprint 约束: 蓝图长度={}", blueprintMarkdown.length());
                }
            }

            // 1.7 领域引导：需求明确时追加结构化约束，避免模板跑偏
            if (originalPrompt != null && !originalPrompt.isBlank()) {
                String domainGuidance = buildDomainGuidance(originalPrompt, effectiveLanguage);
                if (domainGuidance != null && !domainGuidance.isBlank()) {
                    originalPrompt = originalPrompt + "\n\n" + domainGuidance;
                    log.info("已注入领域引导");
                }
            }

            // 2. 增强提示词（传入语言参数）
            if (originalPrompt != null && !originalPrompt.isEmpty()) {
                String enhancedPrompt = switch (effectivePromptProfile) {
                    case "reasoning" -> enhancePromptForReasoning(originalPrompt, effectiveLanguage);
                    case "fast" -> enhancePromptForFastPreview(originalPrompt, effectiveLanguage);
                    default -> enhancePromptWithStructuredThinking(originalPrompt, effectiveLanguage);
                };
                if (containsUnresolvedPromptPlaceholder(enhancedPrompt, originalPrompt)) {
                    String message = "提示词模板包含未替换的占位符(%s)，已拒绝生成，请检查后端提示词模板。";
                    log.error("提示词校验失败: {}", message);
                    StreamingResponseBody errorStream = outputStream -> {
                        String errorEvent = "data: {\"type\":\"error\",\"error\":\"" + message + "\"}\n\n";
                        outputStream.write(errorEvent.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    };
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                            .header(HttpHeaders.CONNECTION, "keep-alive")
                            .body(errorStream);
                }
                adaptedRequest.put("prompt", enhancedPrompt);
                log.info("提示词增强: profile={}, language={}, 原长度={}, 增强后长度={}",
                        effectivePromptProfile,
                        effectiveLanguage,
                        originalPrompt.length(),
                        enhancedPrompt.length());
            }

            // 2.1 模型策略：支持候选模型切换（deepseek / deepseek-r1-0528）
            // 规则：
            // - 若请求显式传 model，则以该 model 为首选（不会强行覆盖）
            // - 若传 modelCandidates，则按候选顺序尝试
            // - 推理模式默认候选：deepseek-r1-0528 -> deepseek
            // - fast 默认候选：deepseek-v3（兼容当前稳定快路径）
            java.util.List<String> modelCandidates = new java.util.ArrayList<>();
            if (modelCandidatesObjRaw instanceof java.util.List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s && !s.isBlank()) {
                        modelCandidates.add(s.trim());
                    }
                }
            }

            Object modelObj = adaptedRequest.get("model");
            boolean hasValidModel = modelObj instanceof String && !((String) modelObj).isBlank();
            if (hasValidModel) {
                // 若已经指定了 model，并且候选为空，则仅使用该 model（避免意外切换）
                if (modelCandidates.isEmpty()) {
                    modelCandidates.add(((String) modelObj).trim());
                }
            } else {
                // 未指定 model：根据 profile 选择默认候选
                if (modelCandidates.isEmpty()) {
                    if ("reasoning".equalsIgnoreCase(effectivePromptProfile)) {
                        modelCandidates.add("deepseek-r1-0528");
                        modelCandidates.add("deepseek");
                    } else if ("fast".equalsIgnoreCase(effectivePromptProfile)) {
                        modelCandidates.add("deepseek-v3");
                    }
                }
                // 如果仍为空，则交由上游默认选择
                if (!modelCandidates.isEmpty()) {
                    adaptedRequest.put("model", modelCandidates.get(0));
                } else {
                    adaptedRequest.remove("model");
                    log.debug("参数适配: 未指定model，交由 OpenLovable-CN 选择默认模型");
                }
            }

            // 稳定性兜底：当显式指定的模型不可用/不返回代码时，追加一个已验证可工作的候选，避免用户直接失败
            // 注意：不覆盖用户候选，仅作为最后一次自动尝试
            //
            // 兼容说明：
            // - UniAix/聚合网关常用 `provider/model` 形式（如 deepseek/deepseek-r1-0528）
            // - 上游 open-lovable-cn 也可能接受“裸模型名”（如 deepseek-v3）
            // 因此这里做“尾段模型名”归一化判断，避免重复追加或追加错误前缀。
            if ("reasoning".equalsIgnoreCase(effectivePromptProfile)) {
                boolean hasStable = modelCandidates.stream()
                        .map(OpenLovableController::normalizeModelKey)
                        .anyMatch("deepseek-v3"::equalsIgnoreCase);
                if (!hasStable) {
                    boolean useDeepseekNamespace = modelCandidates.stream()
                            .anyMatch(m -> m != null && m.trim().toLowerCase().startsWith("deepseek/"));
                    modelCandidates.add(useDeepseekNamespace ? "deepseek/deepseek-v3" : "deepseek-v3");
                }
            }

            // 3. 将 sandboxId 合并到 context.sandboxId（保留已有 context，避免丢失 fileManifest）
            if (adaptedRequest.containsKey("sandboxId")) {
                Object sandboxIdObj = adaptedRequest.remove("sandboxId");
                if (sandboxIdObj instanceof String sandboxId && !sandboxId.isBlank()
                        && !"pending".equalsIgnoreCase(sandboxId)) {
                    Map<String, Object> context = new HashMap<>();
                    Object rawContext = adaptedRequest.get("context");
                    if (rawContext instanceof Map<?, ?> rawMap) {
                        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                            if (entry.getKey() instanceof String key) {
                                context.put(key, entry.getValue());
                            }
                        }
                    }
                    context.put("sandboxId", sandboxId);
                    adaptedRequest.put("context", context);
                    log.debug("参数适配: sandboxId -> context.sandboxId ({})", sandboxId);
                } else {
                    log.debug("参数适配: sandboxId为空或pending，已跳过向上游传递（避免误用占位ID）");
                }
            }

            enrichContextWithFileManifest(adaptedRequest);

            log.debug("适配后请求体: {}", adaptedRequest);
            log.info("OpenLovable生成策略: profile={}, candidates={}", effectivePromptProfile, modelCandidates);

            final String originalPromptFinal = originalPrompt;

            StreamingResponseBody stream = outputStream -> {
                try {
                    java.util.List<String> candidates = modelCandidates.isEmpty() ? java.util.List.of()
                            : java.util.List.copyOf(modelCandidates);
                    int attempts = candidates.isEmpty() ? 1 : candidates.size();

                    ForwardSseResult lastResult = null;
                    for (int attempt = 0; attempt < attempts; attempt++) {
                        boolean hasNext = attempt + 1 < attempts;

                        Map<String, Object> attemptRequest = new HashMap<>(adaptedRequest);
                        if (!candidates.isEmpty()) {
                            attemptRequest.put("model", candidates.get(attempt));
                        }

                        // 推理模式首轮可能更慢：若发生超时/空代码，后续尝试走更快提示词，降低继续超时概率
                        if (hasNext && "reasoning".equalsIgnoreCase(effectivePromptProfile)) {
                            if (originalPromptFinal != null && !originalPromptFinal.isBlank()) {
                                attemptRequest.put("prompt",
                                        enhancePromptForReasoning(originalPromptFinal, effectiveLanguage));
                            }
                        }

                        boolean suppressEmptyComplete = hasNext; // 有后续才抑制“空complete”
                        boolean suppressTimeoutError = hasNext; // 有后续才抑制“超时error”

                        String prefix = "reasoning".equalsIgnoreCase(effectivePromptProfile) ? "🧠 深度思考中" : "🤖 生成中";
                        String notice = attempt == 0
                                ? "data: {\"type\":\"status\",\"message\":\"" + prefix + "...\"}\n\n"
                                : "data: {\"type\":\"status\",\"message\":\"🔁 自动重试中...\"}\n\n";
                        outputStream.write(notice.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();

                        ForwardSseResult result = forwardGenerateSse(url, targetBaseUrl, attemptRequest, outputStream,
                                suppressEmptyComplete, suppressTimeoutError);
                        lastResult = result;

                        boolean shouldRetry = !result.hasAnyCode()
                                || (result.hasTimeoutError() && !result.hasCompleteCode());

                        if (!shouldRetry) {
                            break;
                        }
                    }

                    if (lastResult != null) {
                        boolean failed = !lastResult.hasAnyCode()
                                || (lastResult.hasTimeoutError() && !lastResult.hasCompleteCode());
                        if (failed) {
                            String msg = lastResult.hasTimeoutError()
                                    ? "OpenLovable 生成超时（240s），建议切换模型或降低生成复杂度后重试"
                                    : "OpenLovable 返回空代码，请检查上游模型/密钥配置或稍后重试";
                            log.warn(msg);
                            String errorMessage = "data: {\"type\":\"error\",\"error\":\"" + msg + "\"}\n\n";
                            outputStream.write(errorMessage.getBytes(StandardCharsets.UTF_8));
                            outputStream.flush();
                        }
                    }

                } catch (Exception e) {
                    log.error("流式响应转发失败", e);
                    String errorMessage = "data: {\"type\":\"error\",\"error\":\"" + e.getMessage() + "\"}\n\n";
                    try {
                        outputStream.write(errorMessage.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    } catch (IOException ioException) {
                        // 典型场景：客户端提前断开连接（刷新/离开页面），此时无需再向客户端写入
                        log.warn("SSE错误事件写入失败（客户端可能已断开）: {}", ioException.getMessage());
                    }
                }
            };

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .header(HttpHeaders.CONNECTION, "keep-alive")
                    .header("X-Accel-Buffering", "no") // 禁用Nginx缓冲，确保SSE实时刷新
                    .body(stream);

        } catch (Exception e) {
            log.error("生成AI代码失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 获取沙箱状态
     *
     * GET /v1/openlovable/sandbox/status
     *
     * 响应示例：
     * {
     * "sandboxId": "sb_xxxxx",
     * "status": "running",
     * "url": "https://xxxxx.vercel.app"
     * }
     */
    @GetMapping("/sandbox/status")
    public ResponseEntity<?> getSandboxStatus(@RequestParam(required = false) String sandboxId) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(resolveOpenLovableBaseUrlBySandboxId(sandboxId) + "/api/sandbox-status");
            if (sandboxId != null && !sandboxId.isBlank()) {
                builder.queryParam("sandboxId", sandboxId);
            }
            String url = builder.toUriString();
            log.info("查询沙箱状态: {} (sandboxId={})", url, sandboxId);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            return ResponseEntity.ok(Result.success(response.getBody()));

        } catch (Exception e) {
            log.error("获取沙箱状态失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("获取沙箱状态失败: " + e.getMessage()));
        }
    }

    /**
     * 终止沙箱
     *
     * POST /v1/openlovable/sandbox/kill
     *
     * 响应示例：
     * {
     * "success": true,
     * "message": "Sandbox terminated"
     * }
     */
    @PostMapping("/sandbox/kill")
    public ResponseEntity<?> killSandbox(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> requestBody = request != null ? request : Map.of();
        Object sandboxIdObj = requestBody.get("sandboxId");
        if (!(sandboxIdObj instanceof String sandboxId) || sandboxId.isBlank()) {
            return ResponseEntity.badRequest().body(Result.error(400, "缺少必需参数: sandboxId"));
        }
        sandboxId = sandboxId.trim();

        String targetBaseUrl = resolveOpenLovableBaseUrlBySandboxId(sandboxId);
        String url = UriComponentsBuilder
                .fromHttpUrl(targetBaseUrl + "/api/kill-sandbox")
                .queryParam("sandboxId", sandboxId)
                .toUriString();

        OpenLovableSandboxLockService.SandboxLockLease lockLease = acquireSandboxOperationLock(sandboxId, "kill");
        if (!lockLease.acquired()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "沙箱正忙，请稍后重试"));
        }

        boolean markedTerminating = markSandboxTerminating(sandboxId, lockLease, "kill", targetBaseUrl);
        boolean killSucceeded = false;
        try {
            if (!markedTerminating) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Result.error(409, "沙箱状态冲突，请稍后重试"));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(
                    Map.of("sandboxId", sandboxId),
                    headers);

            log.info("终止沙箱: sandboxId={}, url={}", sandboxId, url);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            markSandboxTerminated(sandboxId, lockLease);
            clearSandboxRoutingAndRegistry(sandboxId);
            killSucceeded = true;
            return ResponseEntity.ok(Result.success(response.getBody()));
        } catch (Exception e) {
            log.error("终止沙箱失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("终止沙箱失败: " + e.getMessage()));
        } finally {
            if (markedTerminating && !killSucceeded) {
                markSandboxReady(sandboxId, lockLease, targetBaseUrl);
            }
            releaseSandboxLock(lockLease);
        }
    }

    /**
     * Sandbox心跳（保活）
     *
     * POST /v1/openlovable/heartbeat
     *
     * 请求体：
     * {
     * "sandboxId": "sb_xxxxx"
     * }
     *
     * 说明：
     * - Open-Lovable 上游不一定提供专门的 heartbeat 接口
     * - 这里通过查询指定 sandbox 的状态来实现“保活/可用性探测”双重目的
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(@RequestBody Map<String, Object> request) {
        try {
            Object sandboxIdObj = request.get("sandboxId");
            if (!(sandboxIdObj instanceof String sandboxId) || sandboxId.isBlank()) {
                return ResponseEntity.badRequest().body(Result.error(400, "缺少必需参数: sandboxId"));
            }

            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(resolveOpenLovableBaseUrlBySandboxId(sandboxId) + "/api/sandbox-status")
                    .queryParam("sandboxId", sandboxId);
            String url = builder.toUriString();

            log.info("Sandbox心跳: sandboxId={}, url={}", sandboxId, url);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            renewSandboxHeartbeat(sandboxId);
            return ResponseEntity.ok(Result.success(response.getBody()));
        } catch (Exception e) {
            log.error("Sandbox心跳失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("Sandbox心跳失败: " + e.getMessage()));
        }
    }

    /**
     * Sandbox清理（释放资源）
     *
     * POST /v1/openlovable/cleanup
     *
     * 请求体：
     * {
     * "sandboxId": "sb_xxxxx"
     * }
     *
     * 说明：
     * - 优先按 sandboxId 精准清理（多实例场景）
     * - 若上游忽略 sandboxId，则等价于清理“当前沙箱”（由Open-Lovable实现决定）
     */
    @PostMapping("/cleanup")
    public ResponseEntity<?> cleanup(@RequestBody Map<String, Object> request) {
        String sandboxId = null;
        String targetBaseUrl = null;
        OpenLovableSandboxLockService.SandboxLockLease lockLease = null;
        boolean markedTerminating = false;
        boolean cleanupSucceeded = false;

        try {
            Object sandboxIdObj = request.get("sandboxId");
            if (!(sandboxIdObj instanceof String sid) || sid.isBlank()) {
                return ResponseEntity.badRequest().body(Result.error(400, "缺少必需参数: sandboxId"));
            }
            sandboxId = sid.trim();

            targetBaseUrl = resolveOpenLovableBaseUrlBySandboxId(sandboxId);
            lockLease = acquireSandboxOperationLock(sandboxId, "cleanup");
            if (!lockLease.acquired()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Result.error(409, "沙箱正忙，请稍后重试"));
            }

            markedTerminating = markSandboxTerminating(sandboxId, lockLease, "cleanup", targetBaseUrl);
            if (!markedTerminating) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Result.error(409, "沙箱状态冲突，请稍后重试"));
            }

            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(targetBaseUrl + "/api/kill-sandbox")
                    .queryParam("sandboxId", sandboxId);
            String url = builder.toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(
                    Map.of("sandboxId", sandboxId),
                    headers);

            log.info("清理Sandbox: sandboxId={}, url={}", sandboxId, url);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            markSandboxTerminated(sandboxId, lockLease);
            clearSandboxRoutingAndRegistry(sandboxId);
            cleanupSucceeded = true;
            return ResponseEntity.ok(Result.success(response.getBody()));
        } catch (Exception e) {
            log.error("清理Sandbox失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("清理Sandbox失败: " + e.getMessage()));
        } finally {
            if (sandboxId != null && markedTerminating && !cleanupSucceeded) {
                markSandboxReady(sandboxId, lockLease, targetBaseUrl);
            }
            releaseSandboxLock(lockLease);
        }
    }

    /**
     * 健康检查
     *
     * GET /v1/openlovable/health
     *
     * 响应示例：
     * {
     * "status": "ok",
     * "service": "open-lovable",
     * "baseUrl": "http://localhost:3001"
     * }
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        try {
            String url = resolveOpenLovableBaseUrlBySandboxId(null) + "/api/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            return ResponseEntity.ok(Result.success(Map.of(
                    "status", "ok",
                    "service", "open-lovable",
                    "baseUrl", resolveOpenLovableBaseUrlBySandboxId(null),
                    "upstream", response.getBody())));

        } catch (Exception e) {
            log.error("健康检查失败", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.error("Open-Lovable服务不可用: " + e.getMessage()));
        }
    }

    /**
     * 将AI生成的代码应用到Sandbox（处理SSE流式响应）
     *
     * POST /v1/openlovable/apply
     *
     * 请求体示例：
     * {
     * "sandboxId": "idk3msrrff9vnoboa9e34",
     * "response": "<file path=\"src/App.jsx\">...</file>..."
     * }
     *
     * 响应示例：
     * {
     * "success": true,
     * "filesWritten": 5,
     * "packagesInstalled": ["react-router-dom"],
     * "message": "代码已成功应用到沙箱"
     * }
     */
    @PostMapping("/apply")
    public ResponseEntity<?> applyCode(@RequestBody Map<String, Object> request) {
        String sandboxIdForLock = extractSandboxIdFromRequestPayload(request);
        OpenLovableSandboxLockService.SandboxLockLease lockLease = null;
        String targetBaseUrlForLock = null;
        boolean markedBusy = false;

        try {
            if (sandboxIdForLock == null || sandboxIdForLock.isBlank()) {
                return ResponseEntity.badRequest().body(Result.error(400, "缺少必需参数: sandboxId"));
            }
            sandboxIdForLock = sandboxIdForLock.trim();

            targetBaseUrlForLock = resolveOpenLovableBaseUrlBySandboxId(sandboxIdForLock);
            lockLease = acquireSandboxOperationLock(sandboxIdForLock, "apply");
            if (!lockLease.acquired()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Result.error(409, "沙箱正忙，请稍后重试"));
            }

            markedBusy = markSandboxBusy(sandboxIdForLock, lockLease, "apply", targetBaseUrlForLock);
            if (!markedBusy) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Result.error(409, "沙箱状态冲突，请稍后重试"));
            }

            log.info("开始应用AI代码到沙箱");
            log.debug("请求参数: {}", request);

            // 先从请求体中解析AI响应文本
            Object responseObj = request.get("response");
            if (!(responseObj instanceof String aiResponse) || aiResponse.isBlank()) {
                log.warn("apply请求缺少response或内容为空，已拒绝写入");
                return ResponseEntity.badRequest().body(Result.error(400, "缺少必需参数: response（AI代码为空）"));
            }

            // V2.0增强-1：自动补全 React Hook 导入（避免 useState/useEffect 未定义导致预览崩溃）
            String fixedResponse = autoFixReactHookImports(aiResponse);
            if (fixedResponse != null && !fixedResponse.equals(aiResponse)) {
                log.info("已自动补全React Hook导入: 原长度={} 新长度={}", aiResponse.length(), fixedResponse.length());
                aiResponse = fixedResponse;
            }

            // V2.0增强-2：自动修复空的main.jsx（截断恢复后可能生成空文件）
            fixedResponse = autoFixEmptyMainJsx(aiResponse);
            if (fixedResponse != null && !fixedResponse.equals(aiResponse)) {
                log.info("已自动修复main.jsx: 原长度={} 新长度={}", aiResponse.length(), fixedResponse.length());
                aiResponse = fixedResponse;
            }

            // V2.0增强-2.1：自动修复损坏的 JSDoc 起始注释（// /**）
            fixedResponse = autoFixBrokenJsdocCommentStart(aiResponse);
            if (fixedResponse != null && !fixedResponse.equals(aiResponse)) {
                log.info("已自动修复损坏JSDoc注释: 原长度={} 新长度={}", aiResponse.length(), fixedResponse.length());
                aiResponse = fixedResponse;
            }

            // V2.0增强-3：移除空文件（避免写入无效文件）
            fixedResponse = removeEmptyFiles(aiResponse);
            if (fixedResponse != null && !fixedResponse.equals(aiResponse)) {
                log.info("已移除空文件: 原长度={} 新长度={}", aiResponse.length(), fixedResponse.length());
                aiResponse = fixedResponse;
            }

            // V2.2增强：保护沙箱基础配置，避免 AI 覆盖关键配置导致预览白屏
            // V2.3增强：package.json 智能合并，保留模板配置并添加AI生成的依赖
            OpenLovableResponseSanitizer.SanitizeResult sanitizeResult = OpenLovableResponseSanitizer
                    .sanitizeForSandboxApply(aiResponse);
            if (sanitizeResult.removedPaths() != null && !sanitizeResult.removedPaths().isEmpty()) {
                log.info("已过滤 {} 个高风险配置文件，防止破坏沙箱模板: {}",
                        sanitizeResult.removedPaths().size(),
                        sanitizeResult.removedPaths());
            }
            if (sanitizeResult.mergedPaths() != null && !sanitizeResult.mergedPaths().isEmpty()) {
                log.info("已智能合并 {} 个配置文件（保留模板配置，添加AI依赖）: {}",
                        sanitizeResult.mergedPaths().size(),
                        sanitizeResult.mergedPaths());
            }
            // V3.0增强：检测到截断文件时自动触发续生成，而非直接拒绝
            // 支持大型企业级应用（30分钟超时），避免因代码量大而失败
            if (sanitizeResult.truncatedPaths() != null && !sanitizeResult.truncatedPaths().isEmpty()) {
                log.warn("检测到 {} 个截断文件，尝试自动续生成: {}",
                        sanitizeResult.truncatedPaths().size(),
                        sanitizeResult.truncatedPaths());

                String sandboxId = request.get("sandboxId") instanceof String sid ? sid : null;
                String originalPrompt = request.get("originalPrompt") instanceof String op ? op : null;

                if (sandboxId != null) {
                    // 尝试自动续生成（最多30分钟）
                    AutoContinueResult continueResult = attemptAutoContinueGeneration(
                            sandboxId,
                            aiResponse,
                            sanitizeResult.truncatedPaths(),
                            originalPrompt,
                            30 * 60 // 30分钟超时
                    );

                    if (continueResult.success()) {
                        log.info("自动续生成成功，继续处理完整代码");
                        aiResponse = continueResult.completeResponse();
                        // 重新进行sanitize检查
                        sanitizeResult = OpenLovableResponseSanitizer.sanitizeForSandboxApply(aiResponse);
                    } else {
                        log.error("自动续生成失败: {}", continueResult.errorMessage());
                        // 如果续生成也失败，返回原始截断错误，但带上已生成的部分
                        return ResponseEntity.badRequest()
                                .body(Result.error(400, String.format(
                                        "AI代码生成不完整：检测到 %d 个截断文件（%s）。自动续生成失败: %s",
                                        sanitizeResult.truncatedPaths().size(),
                                        String.join(", ", sanitizeResult.truncatedPaths()),
                                        continueResult.errorMessage())));
                    }
                } else {
                    // 无sandboxId时仍然返回原始错误
                    return ResponseEntity.badRequest()
                            .body(Result.error(400, String.format(
                                    "AI代码生成不完整：检测到 %d 个截断文件（%s）。请重新生成以获取完整代码。",
                                    sanitizeResult.truncatedPaths().size(),
                                    String.join(", ", sanitizeResult.truncatedPaths()))));
                }
            }
            aiResponse = sanitizeResult.sanitizedResponse();

            // V2.4增强：仅保留 <file ...>...</file>，剥离非文件文本，避免上游解析误判
            java.util.List<OpenLovableResponseSanitizer.FileBlock> fileBlocks = OpenLovableResponseSanitizer
                    .extractFileBlocks(aiResponse);
            if (fileBlocks.isEmpty()) {
                log.warn("apply请求未解析到任何文件块，已拒绝写入");
                return ResponseEntity.badRequest()
                        .body(Result.error(400, "AI代码格式异常：未解析到有效的 <file path=\"...\"> 文件块"));
            }
            fileBlocks = normalizeTailwindColorAliasesInBlocks(fileBlocks);
            if (shouldMergeWithExistingFiles(request, fileBlocks)) {
                String sandboxId = request.get("sandboxId") instanceof String sid ? sid : null;
                Map<String, String> existingFiles = loadSandboxFilesForMerge(sandboxId);
                if (existingFiles.isEmpty()) {
                    Map<String, String> fallbackFiles = extractExistingFilesFromRequest(request);
                    if (!fallbackFiles.isEmpty()) {
                        log.info("apply增量合并使用请求内 existingFiles 兜底: count={}", fallbackFiles.size());
                        existingFiles = fallbackFiles;
                    }
                }
                if (!existingFiles.isEmpty()) {
                    List<OpenLovableResponseSanitizer.FileBlock> mergedBlocks = OpenLovableResponseSanitizer
                            .mergeWithExistingFiles(fileBlocks, existingFiles);
                    if (mergedBlocks.size() > fileBlocks.size()) {
                        log.info("apply增量合并: 新增保留文件 {} 个 (原始={}, 合并后={})",
                                mergedBlocks.size() - fileBlocks.size(),
                                fileBlocks.size(),
                                mergedBlocks.size());
                    }
                    fileBlocks = mergedBlocks;
                } else {
                    log.warn("apply增量合并跳过：未读取到可用沙箱文件");
                }
            }
            String strippedResponse = OpenLovableResponseSanitizer.buildResponseFromFileBlocks(fileBlocks);
            if (!strippedResponse.equals(aiResponse)) {
                log.info("已剥离非文件文本: 原长度={} 新长度={}", aiResponse.length(), strippedResponse.length());
            }
            aiResponse = strippedResponse;

            // 基础校验：OpenLovable apply 依赖 <file path="...">...</file> 结构
            if (!aiResponse.contains("<file")) {
                log.warn("apply请求的AI代码不含<file>标签，无法应用到Sandbox");
                return ResponseEntity.badRequest()
                        .body(Result.error(400, "AI代码格式异常：缺少 <file path=\"...\"> 标签，无法应用到Sandbox"));
            }

            // 更新请求体
            request.put("response", aiResponse);
            if (shouldMarkApplyAsEdit(request)) {
                Object isEditObj = request.get("isEdit");
                if (!(isEditObj instanceof Boolean b && b)) {
                    request.put("isEdit", true);
                    log.info("apply请求已标记为编辑模式");
                }
            }

            // 从AI响应中解析文件数量（作为备用）
            int parsedFilesCount = fileBlocks.size();
            log.info("从AI响应中解析到 {} 个文件", parsedFilesCount);

            ApplyOutcome applyOutcome = executeOpenLovableApply(request);
            if (!applyOutcome.success()) {
                return ResponseEntity.status(applyOutcome.httpStatus())
                        .body(Result.error(applyOutcome.httpStatus(), applyOutcome.errorMessage()));
            }

            Map<String, Object> finalResult = applyOutcome.result();
            // 确保 Map 可变，并追加被过滤的文件列表（供前端展示差异）
            finalResult = finalResult == null ? new HashMap<>() : new HashMap<>(finalResult);
            if (sanitizeResult.removedPaths() != null && !sanitizeResult.removedPaths().isEmpty()) {
                finalResult.put("filteredFiles", sanitizeResult.removedPaths());
            }
            boolean repaired = verifyAndRepairMockDataExports(
                    fileBlocks,
                    finalResult,
                    request.get("sandboxId") instanceof String sid ? sid : null);
            if (repaired) {
                log.info("mockData 导出异常已自动修复");
            }

            return ResponseEntity.ok(Result.success(finalResult));

        } catch (Exception e) {
            log.error("应用AI代码失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("应用代码失败: " + e.getMessage()));
        } finally {
            if (sandboxIdForLock != null && markedBusy) {
                markSandboxReady(sandboxIdForLock, lockLease, targetBaseUrlForLock);
            }
            releaseSandboxLock(lockLease);
        }
    }

    /**
     * OpenLovable apply 执行结果
     *
     * 是什么：封装 apply 调用的核心结果与错误信息。
     * 做什么：让主流程与修复流程复用统一的 apply 逻辑。
     * 为什么：避免重复解析 SSE 带来的维护风险。
     */
    private record ApplyOutcome(boolean success, int httpStatus, String errorMessage,
            Map<String, Object> result, int filesWritten) {
    }

    /**
     * 执行 OpenLovable apply 并解析 SSE 响应
     *
     * 是什么：封装调用 open-lovable-cn 的 apply 流程。
     * 做什么：将请求发给上游并提取最终结果/错误信息。
     * 为什么：支撑主流程与修复流程一致性。
     */
    private ApplyOutcome executeOpenLovableApply(Map<String, Object> request) {
        String sandboxIdForRoute = extractSandboxIdFromRequestPayload(request);
        String upstreamBaseUrl = resolveOpenLovableBaseUrlBySandboxId(sandboxIdForRoute);
        String url = upstreamBaseUrl + "/api/apply-ai-code-stream";

        try {
            // 使用HttpURLConnection处理SSE流式响应
            URL targetUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            // 设置超时：apply操作需要写入文件、安装依赖 - V2.0优化
            // readTimeout是指两次read()之间的最大间隔，SSE流持续有数据时不会触发
            // 设置为2分钟作为兜底，如果操作卡住则快速失败
            connection.setConnectTimeout(30000); // 连接超时30秒
            connection.setReadTimeout(120000); // 读取超时2分钟（120秒）- V2.0优化

            // 发送请求体
            String jsonBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request);
            connection.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
            connection.getOutputStream().flush();

            // OpenLovable 上游返回非2xx时，getInputStream()会抛异常；这里提前处理并透出错误
            int upstreamStatus = connection.getResponseCode();
            if (upstreamStatus < 200 || upstreamStatus >= 300) {
                String upstreamBody = "";
                try (InputStream errorStream = connection.getErrorStream()) {
                    if (errorStream != null) {
                        upstreamBody = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))
                                .lines()
                                .collect(Collectors.joining("\n"));
                    }
                } catch (Exception readErr) {
                    log.warn("读取OpenLovable错误响应失败: {}", readErr.getMessage());
                }
                String preview = upstreamBody.length() > 500 ? upstreamBody.substring(0, 500) + "..." : upstreamBody;
                log.error("OpenLovable apply返回错误: status={}, body={}", upstreamStatus, preview);
                return new ApplyOutcome(false, HttpStatus.BAD_GATEWAY.value(),
                        "OpenLovable apply失败: " + upstreamStatus, null, 0);
            }

            // 读取SSE流式响应
            Map<String, Object> finalResult = new java.util.HashMap<>();
            finalResult.put("filesCreated", new java.util.ArrayList<>());
            finalResult.put("packagesInstalled", new java.util.ArrayList<>());
            boolean receivedComplete = false;
            String upstreamErrorMessage = null;
            // 收集 file-error 事件，用于诊断写入失败
            java.util.List<String> fileErrors = new java.util.ArrayList<>();
            java.util.List<String> warnings = new java.util.ArrayList<>();

            try (InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        try {
                            String jsonData = line.substring(6).trim();
                            Map<String, Object> eventData = new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readValue(jsonData, Map.class);

                            String type = (String) eventData.get("type");

                            // 处理 sandbox 事件：上游可能会“替换 sandboxId”（例如传入的 sandboxId 不存在时）
                            // 需要把最终实际使用的 sandboxId/url 返回给前端，避免出现 “Sandbox Not Found”
                            if ("sandbox".equals(type)) {
                                Object sandboxIdObj = eventData.get("sandboxId");
                                Object urlObj = eventData.get("url");
                                if (sandboxIdObj instanceof String sid && !sid.isBlank()) {
                                    finalResult.put("sandboxId", sid);
                                    bindSandboxEndpoint(sid, upstreamBaseUrl);
                                    registerSandboxReady(sid, upstreamBaseUrl, null, null);
                                }
                                if (urlObj instanceof String urlStr && !urlStr.isBlank()) {
                                    finalResult.put("sandboxUrl", urlStr);
                                    // 兼容前端通用字段
                                    finalResult.put("url", urlStr);
                                }
                                if (eventData.get("replacedSandboxId") instanceof String replaced
                                        && !replaced.isBlank()) {
                                    finalResult.put("replacedSandboxId", replaced);
                                    bindSandboxEndpoint(replaced, upstreamBaseUrl);
                                    registerSandboxReady(replaced, upstreamBaseUrl, null, null);
                                }
                                if (eventData.get("provider") instanceof String provider && !provider.isBlank()) {
                                    finalResult.put("provider", provider);
                                }
                                continue;
                            }

                            // 处理 error 事件：直接失败返回，避免“假成功”（文件数从 AI 响应解析）
                            if ("error".equals(type)) {
                                Object err = eventData.get("error");
                                Object msg = eventData.get("message");
                                if (err instanceof String && !((String) err).isBlank()) {
                                    upstreamErrorMessage = (String) err;
                                } else if (msg instanceof String && !((String) msg).isBlank()) {
                                    upstreamErrorMessage = (String) msg;
                                } else {
                                    upstreamErrorMessage = "OpenLovable apply 返回 error 事件";
                                }
                                break;
                            }

                            // 处理complete事件，提取最终结果
                            if ("complete".equals(type)) {
                                receivedComplete = true;
                                Map<String, Object> results = (Map<String, Object>) eventData.get("results");
                                if (results != null) {
                                    finalResult.put("filesCreated", results.get("filesCreated"));
                                    finalResult.put("filesUpdated", results.get("filesUpdated"));
                                    finalResult.put("packagesInstalled", results.get("packagesInstalled"));
                                    finalResult.put("commandsExecuted", results.get("commandsExecuted"));
                                    finalResult.put("errors", results.get("errors"));
                                }
                                finalResult.put("message", eventData.get("message"));
                                break;
                            }

                            // 处理 file-error 事件：记录详细的文件写入错误信息
                            if ("file-error".equals(type)) {
                                Object filePath = eventData.get("filePath");
                                Object filePathAlt = eventData.get("path");
                                Object errorMsg = eventData.get("error");
                                Object errorMsgAlt = eventData.get("message");
                                String path = (filePath != null) ? filePath.toString()
                                        : (filePathAlt != null ? filePathAlt.toString() : "unknown");
                                String err = (errorMsg != null) ? errorMsg.toString()
                                        : (errorMsgAlt != null ? errorMsgAlt.toString() : "未知错误");
                                String fileErrorInfo = String.format("文件 %s 写入失败: %s", path, err);
                                fileErrors.add(fileErrorInfo);
                                log.warn("SSE file-error: {}", fileErrorInfo);
                                continue;
                            }

                            // 处理 warning 事件：记录警告信息
                            if ("warning".equals(type)) {
                                Object msg = eventData.get("message");
                                String warnMsg = (msg != null) ? msg.toString() : eventData.toString();
                                warnings.add(warnMsg);
                                log.warn("SSE warning: {}", warnMsg);
                                continue;
                            }

                            log.debug("SSE事件: type={}", type);
                        } catch (Exception parseError) {
                            log.debug("解析SSE消息失败: {}", line, parseError);
                        }
                    }
                }
            }

            if (upstreamErrorMessage != null && !upstreamErrorMessage.isBlank()) {
                log.error("OpenLovable apply 失败（error事件）: {}", upstreamErrorMessage);
                return new ApplyOutcome(false, HttpStatus.BAD_GATEWAY.value(),
                        "OpenLovable apply失败: " + upstreamErrorMessage, null, 0);
            }

            if (!receivedComplete) {
                log.error("OpenLovable apply 未返回 complete 事件，已拒绝返回假成功");
                return new ApplyOutcome(false, HttpStatus.BAD_GATEWAY.value(),
                        "OpenLovable apply未返回complete事件，请稍后重试", null, 0);
            }

            // 计算写入文件数
            int filesWritten = 0;
            if (finalResult.get("filesCreated") != null) {
                filesWritten += ((java.util.List<?>) finalResult.get("filesCreated")).size();
            }
            if (finalResult.get("filesUpdated") != null) {
                filesWritten += ((java.util.List<?>) finalResult.get("filesUpdated")).size();
            }

            // 收到 complete 但文件数为 0 时，视为异常（避免写入失败却误报成功）
            if (filesWritten == 0) {
                StringBuilder errorDetail = new StringBuilder("OpenLovable apply失败：写入文件数为0");
                if (!fileErrors.isEmpty()) {
                    errorDetail.append("\n文件错误详情:\n");
                    for (String fe : fileErrors) {
                        errorDetail.append("  - ").append(fe).append("\n");
                    }
                    log.error("OpenLovable apply 完成但写入文件数为0，file-error详情: {}", fileErrors);
                } else {
                    log.error("OpenLovable apply 完成但写入文件数为0，疑似上游异常（无file-error事件）");
                }
                if (!warnings.isEmpty()) {
                    errorDetail.append("\n警告信息:\n");
                    for (String w : warnings) {
                        errorDetail.append("  - ").append(w).append("\n");
                    }
                    log.warn("OpenLovable apply warnings: {}", warnings);
                }
                return new ApplyOutcome(false, HttpStatus.BAD_GATEWAY.value(), errorDetail.toString().trim(), null, 0);
            }

            finalResult.put("filesWritten", filesWritten);
            log.info("代码应用成功: 写入{}个文件 (receivedComplete={})", filesWritten, receivedComplete);
            return new ApplyOutcome(true, HttpStatus.OK.value(), "", finalResult, filesWritten);
        } catch (Exception e) {
            log.error("OpenLovable apply 请求异常", e);
            return new ApplyOutcome(false, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "OpenLovable apply请求异常: " + e.getMessage(), null, 0);
        }
    }

    /**
     * 校验并修复 mockData 导出一致性
     *
     * 是什么：检查沙箱中的 src/data/mockData.jsx 是否包含必需导出。
     * 做什么：若缺失导出则自动重试 apply 一次进行修复。
     * 为什么：避免 import 命名导出失败导致预览白屏。
     */
    private boolean verifyAndRepairMockDataExports(
            java.util.List<OpenLovableResponseSanitizer.FileBlock> fileBlocks,
            Map<String, Object> applyResult,
            String fallbackSandboxId) {
        if (fileBlocks == null || fileBlocks.isEmpty()) {
            return false;
        }

        Map<String, String> fileContentMap = new java.util.HashMap<>();
        boolean referencesMockData = false;
        for (OpenLovableResponseSanitizer.FileBlock block : fileBlocks) {
            fileContentMap.put(block.normalizedPath(), block.content());
            if (block.content() != null && block.content().contains("mockData")) {
                referencesMockData = true;
            }
        }

        String expectedContent = fileContentMap.get("src/data/mockData.jsx");
        if (!referencesMockData && (expectedContent == null || expectedContent.isBlank())) {
            return false;
        }

        String sandboxUrl = null;
        if (applyResult != null) {
            Object urlObj = applyResult.get("sandboxUrl");
            if (!(urlObj instanceof String urlStr) || urlStr.isBlank()) {
                urlObj = applyResult.get("url");
            }
            if (urlObj instanceof String urlStr && !urlStr.isBlank()) {
                sandboxUrl = urlStr;
            }
        }

        String sandboxId = null;
        if (applyResult != null && applyResult.get("sandboxId") instanceof String sid && !sid.isBlank()) {
            sandboxId = sid;
        } else if (fallbackSandboxId != null && !fallbackSandboxId.isBlank()) {
            sandboxId = fallbackSandboxId;
        }

        if (sandboxUrl == null || sandboxId == null) {
            log.warn("mockData 校验跳过：缺少 sandboxUrl/sandboxId");
            return false;
        }

        String actualContent = fetchSandboxFileContent(sandboxUrl, "src/data/mockData.jsx");
        if (actualContent != null && hasRequiredMockDataExports(actualContent)) {
            return false;
        }

        String repairContent = expectedContent;
        if (repairContent == null || repairContent.isBlank() || !hasRequiredMockDataExports(repairContent)) {
            repairContent = buildMockDataFallbackContent();
            log.warn("mockData 原始内容缺少必要导出，已回退到最小可运行占位数据");
        }

        String patchResponse = "<file path=\"src/data/mockData.jsx\">\n" + repairContent + "\n</file>";
        Map<String, Object> patchRequest = new HashMap<>();
        patchRequest.put("sandboxId", sandboxId);
        patchRequest.put("response", patchResponse);

        ApplyOutcome patchOutcome = executeOpenLovableApply(patchRequest);
        if (!patchOutcome.success()) {
            log.warn("mockData 修复失败: {}", patchOutcome.errorMessage());
            return false;
        }

        log.info("mockData 修复成功: sandboxId={}", sandboxId);
        return true;
    }

    /**
     * 拉取沙箱中的文件内容
     *
     * 是什么：通过沙箱预览地址获取指定文件的源码。
     * 做什么：用于校验应用实际加载的文件内容。
     * 为什么：避免只靠 AI 输出判断，忽略上游覆盖导致的白屏。
     */
    private String fetchSandboxFileContent(String sandboxUrl, String filePath) {
        String normalizedBase = sandboxUrl.endsWith("/") ? sandboxUrl.substring(0, sandboxUrl.length() - 1)
                : sandboxUrl;
        String target = normalizedBase + "/" + filePath;

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                log.warn("读取沙箱文件失败: url={}, status={}", target, status);
                return null;
            }

            try (InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            log.warn("读取沙箱文件异常: url={}, err={}", target, e.getMessage());
            return null;
        }
    }

    /**
     * 判断 mockData 是否包含必需导出
     *
     * 是什么：检查 currentUser/currentRepo 等核心导出是否存在。
     * 做什么：作为“预览白屏”快速兜底的判断条件。
     * 为什么：命名导出缺失会直接导致 import 失败。
     */
    private boolean hasRequiredMockDataExports(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.contains("export const currentUser")
                && content.contains("export const currentRepo");
    }

    /**
     * 构建 mockData 最小占位数据
     *
     * 是什么：提供一个最小可运行的数据模块内容。
     * 做什么：在 AI 输出缺失导出时兜底，避免运行时崩溃。
     * 为什么：保障预览稳定性，避免白屏影响用户流程。
     */
    private String buildMockDataFallbackContent() {
        return """
                import { AlertCircle, FileCode, ShieldAlert, Zap } from 'lucide-react';

                export const currentUser = {
                  login: 'user',
                  avatarUrl: ''
                };

                export const currentRepo = {
                  owner: 'demo',
                  name: 'demo-repo',
                  isPublic: true,
                  description: '占位数据：用于保证预览可运行',
                  stars: '0',
                  forks: '0',
                  watching: '0',
                  tags: [],
                  lastUpdate: '刚刚'
                };

                export const fileStructure = [];

                export const aiReviewSummary = {
                  grade: 'N/A',
                  score: 0,
                  issuesFound: 0,
                  critical: 0,
                  warnings: 0,
                  suggestions: 0,
                  lastScan: '未扫描'
                };

                export const aiIssues = [
                  {
                    id: 1,
                    severity: 'warning',
                    type: 'placeholder',
                    title: '占位告警',
                    file: 'src/data/mockData.jsx',
                    line: 1,
                    description: '当前为占位数据，等待 AI 输出完整内容。',
                    suggestion: '请重新生成或手动补全数据。',
                    icon: AlertCircle,
                    color: 'text-yellow-600',
                    bgColor: 'bg-yellow-50'
                  },
                  {
                    id: 2,
                    severity: 'info',
                    type: 'placeholder',
                    title: '占位提示',
                    file: 'src/data/mockData.jsx',
                    line: 1,
                    description: '此文件为兜底生成，确保页面可渲染。',
                    suggestion: '请检查模型输出质量。',
                    icon: ShieldAlert,
                    color: 'text-blue-600',
                    bgColor: 'bg-blue-50'
                  },
                  {
                    id: 3,
                    severity: 'info',
                    type: 'placeholder',
                    title: '占位提示',
                    file: 'src/data/mockData.jsx',
                    line: 1,
                    description: '可在生成完成后替换为真实数据。',
                    suggestion: '保持文件导出完整。',
                    icon: FileCode,
                    color: 'text-green-600',
                    bgColor: 'bg-green-50'
                  },
                  {
                    id: 4,
                    severity: 'info',
                    type: 'placeholder',
                    title: '占位提示',
                    file: 'src/data/mockData.jsx',
                    line: 1,
                    description: '当前模块用于避免 import 失败。',
                    suggestion: '重新生成以覆盖。',
                    icon: Zap,
                    color: 'text-purple-600',
                    bgColor: 'bg-purple-50'
                  }
                ];
                """;
    }

    /**
     * 重启Vite开发服务器
     *
     * POST /v1/openlovable/restart-vite
     *
     * 用途：当预览页面显示"Closed Port Error"时，用户可以手动重启Vite服务器
     *
     * 请求体（可选）：
     * {
     * "sandboxId": "imvbokfo0hay4na5cxqrq" // 指定sandbox ID，确保重启正确的sandbox
     * }
     *
     * 响应示例：
     * {
     * "success": true,
     * "message": "Vite server restarted successfully"
     * }
     */
    @PostMapping("/restart-vite")
    public ResponseEntity<?> restartVite(@RequestBody(required = false) Map<String, String> requestBody) {
        String sandboxId = (requestBody != null) ? requestBody.get("sandboxId") : null;
        if (sandboxId == null || sandboxId.isBlank()) {
            return ResponseEntity.badRequest().body(Result.error(400, "缺少必需参数: sandboxId"));
        }
        sandboxId = sandboxId.trim();

        String targetBaseUrl = resolveOpenLovableBaseUrlBySandboxId(sandboxId);
        String url = targetBaseUrl + "/api/restart-vite";

        OpenLovableSandboxLockService.SandboxLockLease lockLease =
                acquireSandboxOperationLock(sandboxId, "restart-vite");
        if (!lockLease.acquired()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "沙箱正忙，请稍后重试"));
        }

        boolean markedBusy = markSandboxBusy(sandboxId, lockLease, "restart-vite", targetBaseUrl);
        if (!markedBusy) {
            releaseSandboxLock(lockLease);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "沙箱状态冲突，请稍后重试"));
        }

        try {
            log.info("重启Vite开发服务器: url={}, sandboxId={}", url, sandboxId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new java.util.HashMap<>();
            body.put("sandboxId", sandboxId);

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            log.info("Vite重启响应: status={}, body={}", response.getStatusCode(), response.getBody());
            return ResponseEntity.ok(Result.success(response.getBody()));
        } catch (Exception e) {
            log.error("重启Vite服务器失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("重启Vite服务器失败: " + e.getMessage()));
        } finally {
            markSandboxReady(sandboxId, lockLease, targetBaseUrl);
            releaseSandboxLock(lockLease);
        }
    }

    /**
     * 自动补全 AI 生成的 React/TSX 文件中的 Hook 导入
     *
     * 典型问题：
     * - AI 输出的组件里使用了 useState/useEffect 等 Hook，但忘记从 react 导入
     * - 运行时会出现 "useState is not defined" 之类错误，导致沙箱预览白屏
     *
     * 处理策略：
     * - 仅对 <file path="*.tsx|*.jsx"> ... </file> 片段做修复
     * - 如果没有 react import，则在文件顶部插入 `import { ... } from 'react';`
     * - 如果已有 react import 但缺少 Hook，则尽量补到命名导入中
     *
     * @param response AI 原始输出（包含多个 <file> 片段）
     * @return 修复后的输出；若无需修复则返回原字符串
     */
    private String autoFixReactHookImports(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }

        // 匹配 <file path="...">...</file> 块，支持单引号/双引号，保留原始 open tag
        Pattern filePattern = Pattern.compile("(<file\\s+path=['\"]([^'\"]+)['\"][^>]*>)([\\s\\S]*?)(</file>)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = filePattern.matcher(response);
        StringBuffer sb = new StringBuffer();
        boolean changed = false;

        while (matcher.find()) {
            String openTag = matcher.group(1);
            String path = matcher.group(2);
            String content = matcher.group(3);
            String closeTag = matcher.group(4);

            if (path != null && (path.endsWith(".tsx") || path.endsWith(".jsx"))) {
                String fixedContent = ensureReactHooksImported(content);
                if (!fixedContent.equals(content)) {
                    changed = true;
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(openTag + fixedContent + closeTag));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }

        matcher.appendTail(sb);
        return changed ? sb.toString() : response;
    }

    /**
     * 确保文件中使用到的 React Hook 已被正确导入
     *
     * @param fileContent 单个文件内容
     * @return 修复后的文件内容
     */
    private String ensureReactHooksImported(String fileContent) {
        if (fileContent == null || fileContent.isEmpty()) {
            return fileContent;
        }

        // 需要检查并补全的 Hook 列表（可按需扩展）
        String[] hooks = new String[] {
                "useState",
                "useEffect",
                "useMemo",
                "useCallback",
                "useRef",
                "useReducer",
                "useContext",
                "useLayoutEffect",
        };

        Set<String> required = new HashSet<>();
        for (String hook : hooks) {
            // 仅匹配直接调用 useXxx(...)，排除 React.useXxx(...)
            Pattern p = Pattern.compile("(?<!\\.)\\b" + hook + "\\s*\\(");
            if (p.matcher(fileContent).find()) {
                required.add(hook);
            }
        }

        if (required.isEmpty()) {
            return fileContent;
        }

        // 检查是否已经从 react 导入了这些 Hook
        Pattern reactImportPattern = Pattern.compile("^import\\s+[^;]*\\s+from\\s+['\"]react['\"];?\\s*$",
                Pattern.MULTILINE);
        Matcher reactImportMatcher = reactImportPattern.matcher(fileContent);

        if (!reactImportMatcher.find()) {
            // 没有任何 react import，直接在顶部插入命名导入
            String importLine = "import { " + String.join(", ", required) + " } from 'react';\n";
            return importLine + fileContent;
        }

        // 找到第一条 react import 行并尝试补全
        String importLine = reactImportMatcher.group();
        String updatedImportLine = importLine;

        // 已经包含命名导入的情况：import React, { useEffect } from 'react';
        Pattern namedImportPattern = Pattern.compile("\\{([^}]*)\\}");
        Matcher namedMatcher = namedImportPattern.matcher(importLine);
        if (namedMatcher.find()) {
            String inside = namedMatcher.group(1);
            Set<String> existing = new HashSet<>();
            for (String part : inside.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    existing.add(trimmed);
                }
            }
            for (String hook : required) {
                if (!existing.contains(hook)) {
                    existing.add(hook);
                }
            }
            String newInside = String.join(", ", existing);
            updatedImportLine = namedMatcher.replaceFirst("{ " + newInside + " }");
        } else {
            // 没有命名导入：import React from 'react';
            // 追加命名导入
            if (importLine.contains("* as React")) {
                // import * as React from 'react'; 追加一条新的命名导入更安全
                String extra = "\nimport { " + String.join(", ", required) + " } from 'react';";
                updatedImportLine = importLine + extra;
            } else {
                // 默认导入或无默认导入，统一改成 React + 命名导入
                updatedImportLine = importLine.replace("from 'react'",
                        ", { " + String.join(", ", required) + " } from 'react'");
                updatedImportLine = updatedImportLine.replace("from \"react\"",
                        ", { " + String.join(", ", required) + " } from \"react\"");
            }
        }

        if (updatedImportLine.equals(importLine)) {
            return fileContent;
        }

        // 替换原 import 行
        return reactImportMatcher.replaceFirst(Matcher.quoteReplacement(updatedImportLine));
    }

    /**
     * 从AI响应中计算文件数量
     * 解析 <file path="...">...</file> 格式
     */
    private int countFilesInResponse(String response) {
        if (response == null || response.isEmpty()) {
            return 0;
        }

        // 使用Set去重，防止重复文件
        java.util.Set<String> filePaths = new java.util.HashSet<>();
        // 兼容单/双引号与大小写差异
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<file\\s+path=['\"]([^'\"]+)['\"]",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(response);

        while (matcher.find()) {
            filePaths.add(matcher.group(1));
        }

        return filePaths.size();
    }

    /**
     * 自动修复空文件或缺失的关键入口文件（main.jsx/main.tsx）
     *
     * 问题场景：
     * - AI截断恢复后可能生成空的 main.jsx 或 main.tsx
     * - 导致沙箱无法正常渲染应用
     *
     * 修复策略：
     * 1. 根据 App.jsx/App.tsx 判断入口类型
     * 2. 检测 main 入口是否存在且非空
     * 3. 如果为空或缺失，自动生成标准入口文件
     *
     * @param response AI 原始输出
     * @return 修复后的输出
     */
    private String autoFixEmptyMainJsx(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }

        boolean hasAppTsx = response.contains("path=\"src/App.tsx\"") || response.contains("path='src/App.tsx'");
        boolean hasAppJsx = response.contains("path=\"src/App.jsx\"") || response.contains("path='src/App.jsx'");
        if (!hasAppTsx && !hasAppJsx) {
            return response;
        }

        if (hasAppTsx) {
            return autoFixEmptyMainFile(response, "src/main.tsx", generateStandardMainTsx());
        }

        return autoFixEmptyMainFile(response, "src/main.jsx", generateStandardMainJsx());
    }

    /**
     * 修复空/缺失 main 入口文件
     *
     * 是什么：统一处理 main.jsx/main.tsx 入口补全。
     * 做什么：为空时替换，缺失时追加标准入口内容。
     * 为什么：防止预览因为入口缺失而白屏。
     */
    private String autoFixEmptyMainFile(String response, String mainPath, String standardContent) {
        Pattern mainPattern = Pattern.compile(
                "<file\\s+path=['\"]" + Pattern.quote(mainPath) + "['\"][^>]*>([\\s\\S]*?)</file>",
                Pattern.CASE_INSENSITIVE);
        Matcher mainMatcher = mainPattern.matcher(response);

        boolean hasMain = false;
        boolean mainIsEmpty = true;

        if (mainMatcher.find()) {
            hasMain = true;
            String content = mainMatcher.group(1);
            mainIsEmpty = content == null || content.trim().isEmpty();
        }

        if (!hasMain || mainIsEmpty) {
            log.info("自动修复: {} {} -> 生成标准入口文件", mainPath, hasMain ? "为空" : "缺失");
            if (hasMain && mainIsEmpty) {
                response = mainMatcher.replaceFirst(
                        Matcher.quoteReplacement("<file path=\"" + mainPath + "\">\n" + standardContent + "\n</file>"));
            } else {
                int lastFileEndIndex = response.lastIndexOf("</file>");
                if (lastFileEndIndex != -1) {
                    String before = response.substring(0, lastFileEndIndex + 7);
                    String after = response.substring(lastFileEndIndex + 7);
                    response = before + "\n\n<file path=\"" + mainPath + "\">\n" + standardContent + "\n</file>"
                            + after;
                }
            }
        }

        return response;
    }

    /**
     * 自动修复被错误前缀为 `// /**` 的 JSDoc 起始行
     *
     * 是什么：把损坏的注释起始行归一为合法的 `/**`。
     * 做什么：修复流式截断/拼接后出现的语法错误注释。
     * 为什么：避免 Vite/Babel 抛出 Unexpected token，导致主页白屏。
     */
    private String autoFixBrokenJsdocCommentStart(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        return response.replaceAll("(?m)^(\\s*)//\\s*/\\*\\*\\s*$", "$1/**");
    }

    /**
     * 生成标准的 Vite React 入口文件内容
     */
    private String generateStandardMainJsx() {
        return "import React from 'react'\n" +
                "import ReactDOM from 'react-dom/client'\n" +
                "import App from './App'\n" +
                "import './index.css'\n" +
                "\n" +
                "ReactDOM.createRoot(document.getElementById('root')).render(\n" +
                "  <React.StrictMode>\n" +
                "    <App />\n" +
                "  </React.StrictMode>,\n" +
                ")";
    }

    /**
     * 生成标准的 Vite React TSX 入口文件内容
     */
    private String generateStandardMainTsx() {
        return "import React from 'react'\n" +
                "import ReactDOM from 'react-dom/client'\n" +
                "import App from './App'\n" +
                "import './index.css'\n" +
                "\n" +
                "ReactDOM.createRoot(document.getElementById('root')!).render(\n" +
                "  <React.StrictMode>\n" +
                "    <App />\n" +
                "  </React.StrictMode>,\n" +
                ")";
    }

    /**
     * 检测并修复空文件（内容为空的file标签）
     *
     * @param response AI 原始输出
     * @return 修复后的输出，移除空文件
     */
    private String removeEmptyFiles(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }

        // 匹配空文件：<file path="..."></file> 或内容只有空白字符
        Pattern emptyFilePattern = Pattern.compile("<file\\s+path=['\"]([^'\"]+)['\"][^>]*>\\s*</file>",
                Pattern.CASE_INSENSITIVE);
        Matcher emptyFileMatcher = emptyFilePattern.matcher(response);

        StringBuffer sb = new StringBuffer();
        int removedCount = 0;

        while (emptyFileMatcher.find()) {
            String filePath = emptyFileMatcher.group(1);
            if (shouldSkipEmptyRemoval(filePath)) {
                emptyFileMatcher.appendReplacement(sb, Matcher.quoteReplacement(emptyFileMatcher.group(0)));
                continue;
            }
            log.warn("移除空文件: {}", filePath);
            emptyFileMatcher.appendReplacement(sb, "");
            removedCount++;
        }

        emptyFileMatcher.appendTail(sb);

        if (removedCount > 0) {
            log.info("移除了 {} 个空文件", removedCount);
        }

        return sb.toString();
    }

    /**
     * 是否跳过空文件移除
     *
     * 是什么：判定空文件是否需要保留。
     * 做什么：保留脚本文件与入口文件，交给后续截断修复处理。
     * 为什么：避免空脚本被删除后无法触发续生成。
     */
    private boolean shouldSkipEmptyRemoval(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        return "src/main.jsx".equals(filePath)
                || "src/main.tsx".equals(filePath)
                || isScriptPath(filePath);
    }

    /**
     * 判断是否为脚本路径
     *
     * 是什么：识别 JS/TS/JSX/TSX 文件路径。
     * 做什么：用于空文件处理策略判断。
     * 为什么：脚本空文件需要触发续生成。
     */
    private boolean isScriptPath(String filePath) {
        String lower = filePath.toLowerCase();
        return lower.endsWith(".js")
                || lower.endsWith(".jsx")
                || lower.endsWith(".ts")
                || lower.endsWith(".tsx");
    }

    /**
     * 判定是否需要合并沙箱已有文件
     *
     * 是什么：判断当前 apply 是否属于增量修复场景。
     * 做什么：在 patch 模式或文件数较少时触发合并策略。
     * 为什么：避免 apply 覆盖导致文件丢失/白屏。
     */
    private boolean shouldMergeWithExistingFiles(
            Map<String, Object> request,
            List<OpenLovableResponseSanitizer.FileBlock> fileBlocks) {
        if (request == null || fileBlocks == null || fileBlocks.isEmpty()) {
            return false;
        }

        String sandboxId = request.get("sandboxId") instanceof String sid ? sid : null;
        if (sandboxId == null || sandboxId.isBlank() || "pending".equalsIgnoreCase(sandboxId.trim())) {
            return false;
        }

        Object mergeFlag = request.get("mergeExisting");
        if (mergeFlag instanceof Boolean flag && flag) {
            return true;
        }

        String mergeMode = safeToString(request.get("mergeMode"));
        if ("patch".equalsIgnoreCase(mergeMode) || "preserve".equalsIgnoreCase(mergeMode)) {
            return true;
        }

        return fileBlocks.size() <= Math.max(1, applyMergeThreshold);
    }

    /**
     * 读取沙箱已有文件（用于增量合并）
     *
     * 是什么：从沙箱读取文本文件内容并构建映射。
     * 做什么：为增量 apply 补齐未修改的文件。
     * 为什么：避免补丁输出过少导致已有文件丢失。
     */
    private Map<String, String> loadSandboxFilesForMerge(String sandboxId) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return Map.of();
        }

        try {
            List<String> files = listSandboxFiles(sandboxId.trim());
            if (files.isEmpty()) {
                return Map.of();
            }

            Map<String, String> contentMap = new LinkedHashMap<>();
            int maxFiles = Math.max(1, applyMergeMaxFiles);
            int loaded = 0;

            for (String filePath : files) {
                if (shouldSkipMergeFile(filePath)) {
                    continue;
                }
                if (loaded >= maxFiles) {
                    log.warn("增量合并达到最大读取文件数限制: max={}", maxFiles);
                    break;
                }
                String content = readSandboxTextFile(sandboxId, filePath);
                if (content == null) {
                    continue;
                }
                contentMap.put(filePath, content);
                loaded++;
            }

            return contentMap;
        } catch (Exception e) {
            log.warn("增量合并读取沙箱文件失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 列出沙箱内可合并的文件列表
     *
     * 是什么：从沙箱执行 find 命令获取文件列表。
     * 做什么：排除依赖目录与构建产物，保留业务代码。
     * 为什么：降低读取成本并避免二进制目录干扰。
     */
    private List<String> listSandboxFiles(String sandboxId) {
        String command = "cd " + SANDBOX_WORKDIR
                + " && find . -type f"
                + " -not -path './node_modules/*'"
                + " -not -path './.git/*'"
                + " -not -path './dist/*'"
                + " -not -path './.vite/*'"
                + " -not -path './.next/*'"
                + " -not -path './coverage/*'"
                + " -not -path './.turbo/*'"
                + " -not -path './.cache/*'"
                + " -not -path './.output/*'"
                + " -not -path './.pnpm-store/*'"
                + " -not -path './.pnpm/*'"
                + " -print";

        try {
            SandboxExecResult result = executeSandboxCommand(sandboxId, command, 15);
            if (result.exitCode() != 0) {
                log.warn("读取沙箱文件列表失败: {}", firstNonBlank(result.stderr(), result.stdout()));
                return List.of();
            }

            String output = result.stdout();
            if (output == null || output.isBlank()) {
                return List.of();
            }

            List<String> files = new ArrayList<>();
            for (String line : output.split("\\R")) {
                String normalized = normalizeSandboxFilePath(line);
                if (normalized.isBlank()) {
                    continue;
                }
                files.add(normalized);
            }
            return files;
        } catch (Exception e) {
            log.warn("列出沙箱文件失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 读取沙箱文本文件内容
     *
     * 是什么：执行 cat 读取沙箱文件内容。
     * 做什么：返回文本内容供增量合并使用。
     * 为什么：保证 apply 不会因补丁过少而丢文件。
     */
    private String readSandboxTextFile(String sandboxId, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        try {
            String command = "cd " + SANDBOX_WORKDIR + " && cat " + relativePath;
            SandboxExecResult result = executeSandboxCommand(sandboxId, command, 15);
            if (result.exitCode() != 0) {
                return null;
            }
            String output = result.stdout();
            if (output == null || output.contains("No such file") || output.contains("not found")) {
                return null;
            }
            return output;
        } catch (Exception e) {
            log.warn("读取沙箱文件失败: path={}, err={}", relativePath, e.getMessage());
            return null;
        }
    }

    /**
     * 标准化沙箱文件路径
     *
     * 是什么：将 find 输出的路径转换为相对路径。
     * 做什么：统一去除 ./ 或工作目录前缀。
     * 为什么：保证路径可与 FileBlock.normalizedPath 对齐。
     */
    private String normalizeSandboxFilePath(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        String path = rawPath.trim().replace("\\", "/");
        if (path.startsWith(SANDBOX_WORKDIR)) {
            path = path.substring(SANDBOX_WORKDIR.length());
        }
        if (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path.trim();
    }

    /**
     * 判定是否跳过合并文件
     *
     * 是什么：过滤敏感文件或二进制资源。
     * 做什么：避免读取与回写无意义内容。
     * 为什么：减少合并时的安全风险与性能开销。
     */
    private boolean shouldSkipMergeFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return true;
        }
        String normalized = filePath.replace("\\", "/");
        String lower = normalized.toLowerCase();
        if (lower.startsWith(".env") || lower.contains("/.env")) {
            return true;
        }
        return isBinaryAssetPath(lower);
    }

    /**
     * 判断是否为二进制资源路径
     *
     * 是什么：通过扩展名判断资源类型。
     * 做什么：用于过滤图片/音视频/压缩包等二进制文件。
     * 为什么：二进制内容不适合通过 <file> 文本写入。
     */
    private boolean isBinaryAssetPath(String normalizedPath) {
        for (String ext : MERGE_BINARY_EXTENSIONS) {
            if (normalizedPath.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建 Blueprint 约束提示词
     *
     * 职责：将 Step 6 生成的技术蓝图 Markdown 转换为 OpenLovable 可理解的设计约束。
     * 
     * 注入内容：
     * - UI 风格约束（颜色、字体、布局）
     * - 页面规划（核心页面与功能）
     * - 技术栈要求（Next.js/React/Tailwind）
     *
     * @param blueprintMarkdown Step 6 生成的完整蓝图 Markdown
     * @return 约束提示词（追加到用户需求后）
     */
    private String buildBlueprintConstraint(String blueprintMarkdown) {
        if (blueprintMarkdown == null || blueprintMarkdown.isBlank()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 🎨 技术蓝图约束（必须严格遵守）\n\n");
        sb.append("以下是用户确认的技术蓝图，生成的前端代码必须遵循这些约束：\n\n");

        // 提取并注入关键章节
        // 1. UI 风格与设计规范
        String uiStyleSection = extractSection(blueprintMarkdown, "UI 风格", "设计风格", "视觉设计");
        if (!uiStyleSection.isBlank()) {
            sb.append("### UI 风格约束\n");
            sb.append(uiStyleSection).append("\n\n");
        }

        // 2. 页面规划
        String pagesSection = extractSection(blueprintMarkdown, "页面规划", "核心页面", "功能页面");
        if (!pagesSection.isBlank()) {
            sb.append("### 页面规划约束\n");
            sb.append(pagesSection).append("\n\n");
        }

        // 3. 技术栈要求
        String techStackSection = extractSection(blueprintMarkdown, "技术栈", "技术选型", "前端技术");
        if (!techStackSection.isBlank()) {
            sb.append("### 技术栈约束\n");
            sb.append(techStackSection).append("\n\n");
        }

        // 如果没有提取到任何章节，则直接使用原始蓝图（截断到合理长度）
        if (sb.length() < 100) {
            sb.setLength(0);
            sb.append("\n\n## 🎨 技术蓝图约束（必须严格遵守）\n\n");
            String truncated = blueprintMarkdown.length() > 3000
                    ? blueprintMarkdown.substring(0, 3000) + "\n\n[... 蓝图内容已截断 ...]"
                    : blueprintMarkdown;
            sb.append(truncated);
        }

        return sb.toString();
    }

    /**
     * 从 Markdown 中提取指定章节内容
     *
     * @param markdown     完整 Markdown 文本
     * @param sectionNames 章节名称候选（任意匹配即返回）
     * @return 章节内容（不含标题），未找到返回空字符串
     */
    private String extractSection(String markdown, String... sectionNames) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        for (String sectionName : sectionNames) {
            // 匹配 ## 章节名 或 ### 章节名
            Pattern pattern = Pattern.compile(
                    "(?m)^#{2,3}\\s*.*?" + Pattern.quote(sectionName) + ".*?$\\n([\\s\\S]*?)(?=^#{2,3}\\s|\\z)",
                    Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(markdown);
            if (matcher.find()) {
                String content = matcher.group(1).trim();
                // 限制单个章节长度
                if (content.length() > 1000) {
                    content = content.substring(0, 1000) + "\n[... 内容已截断 ...]";
                }
                return content;
            }
        }
        return "";
    }

    // ==================== 方案A: 结构化提示词增强 ====================

    /**
     * 在流式转发时修复complete事件中的空main.jsx
     *
     * V2.1优化：在generate阶段就修复，无需等到apply阶段
     *
     * @param sseDataLine SSE数据行（data: {...}格式）
     * @return 修复后的SSE数据行
     */
    private String fixMainJsxInCompleteEvent(String sseDataLine) {
        try {
            // 提取JSON部分
            if (!sseDataLine.startsWith("data: ")) {
                return sseDataLine;
            }
            String jsonStr = sseDataLine.substring(6).trim();

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> eventData = mapper.readValue(jsonStr, Map.class);

            String generatedCode = (String) eventData.get("generatedCode");
            if (generatedCode == null || generatedCode.isEmpty()) {
                return sseDataLine;
            }

            // 检查main.jsx是否为空
            Pattern mainJsxPattern = Pattern.compile("<file\\s+path=['\"]src/main\\.jsx['\"][^>]*>([\\s\\S]*?)</file>",
                    Pattern.CASE_INSENSITIVE);
            Matcher mainJsxMatcher = mainJsxPattern.matcher(generatedCode);

            if (mainJsxMatcher.find()) {
                String content = mainJsxMatcher.group(1);
                if (content == null || content.trim().isEmpty()) {
                    // main.jsx为空，替换为完整内容
                    String standardMainJsx = generateStandardMainJsx();
                    String fixedCode = mainJsxMatcher.replaceFirst(
                            Matcher.quoteReplacement("<file path=\"src/main.jsx\">\n" + standardMainJsx + "\n</file>"));
                    eventData.put("generatedCode", fixedCode);
                    log.info("✅ generate阶段修复: main.jsx为空 -> 已注入完整入口文件");

                    // 重新构建SSE数据行
                    return "data: " + mapper.writeValueAsString(eventData);
                }
            } else {
                // main.jsx不存在，检查是否有App.jsx需要添加入口
                if (generatedCode.contains("path=\"src/App.jsx\"") || generatedCode.contains("path='src/App.jsx'")) {
                    String standardMainJsx = generateStandardMainJsx();
                    // 在最后一个</file>后追加
                    int lastFileEnd = generatedCode.lastIndexOf("</file>");
                    if (lastFileEnd != -1) {
                        String fixedCode = generatedCode.substring(0, lastFileEnd + 7) +
                                "\n\n<file path=\"src/main.jsx\">\n" + standardMainJsx + "\n</file>" +
                                generatedCode.substring(lastFileEnd + 7);
                        eventData.put("generatedCode", fixedCode);
                        log.info("✅ generate阶段修复: main.jsx缺失 -> 已追加完整入口文件");
                        return "data: " + mapper.writeValueAsString(eventData);
                    }
                }
            }

            return sseDataLine;
        } catch (Exception e) {
            log.warn("修复complete事件失败: {}", e.getMessage());
            return sseDataLine;
        }
    }

    /**
     * OpenLovable SSE 转发结果（用于判断上游是否真正产出了可部署的代码）。
     *
     * 为什么需要：
     * - 上游偶发会发送 type=complete 但 generatedCode 为空，且没有任何 stream/content 增量。
     * - 前端在 done 时会根据累积状态判断“代码为空”，导致无法部署。
     * - 代理层需要识别该场景并触发一次自动重试（模型回退），提高一次性成功率。
     */
    private static final class ForwardSseResult {
        private final boolean hasDelta;
        private final boolean hasCompleteCode;
        private final boolean hasTimeoutError;
        private final String lastError;

        private ForwardSseResult(boolean hasDelta, boolean hasCompleteCode, boolean hasTimeoutError, String lastError) {
            this.hasDelta = hasDelta;
            this.hasCompleteCode = hasCompleteCode;
            this.hasTimeoutError = hasTimeoutError;
            this.lastError = lastError;
        }

        /** 是否已拿到任何可用于 apply 的代码输出。 */
        private boolean hasAnyCode() {
            return hasDelta || hasCompleteCode;
        }

        private boolean hasCompleteCode() {
            return hasCompleteCode;
        }

        private boolean hasTimeoutError() {
            return hasTimeoutError;
        }
    }

    /**
     * 规范化上游 SSE 行，兼容“裸文本行”输出。
     *
     * 是什么：将非 data/event/id/retry/comment 的裸行包装成 data 事件。
     * 做什么：把代码片段统一成 {"type":"stream","text":...} 的 JSON 负载。
     * 为什么：避免上游直接输出代码行导致前端 SSE 解析器丢失增量。
     */
    private String normalizeRawSseLine(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        if (line.startsWith("data:") || line.startsWith(":")
                || line.startsWith("event:") || line.startsWith("id:")
                || line.startsWith("retry:")) {
            return line;
        }
        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("type", "stream");
            eventData.put("text", line);
            eventData.put("raw", true);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return "data: " + mapper.writeValueAsString(eventData);
        } catch (Exception e) {
            log.warn("包装SSE裸行失败，将转发原始内容: {}", e.getMessage());
            return line;
        }
    }

    /**
     * 判断 SSE data 行是否包含代码增量。
     *
     * 是什么：基于行内关键字段的轻量检测。
     * 做什么：识别 stream/content 的增量输出与 conversation 的文件片段。
     * 为什么：避免 hasDelta 误判导致自动重试或完成判断异常。
     */
    private boolean isCodeDeltaLine(String line) {
        if (line == null || !line.startsWith("data:")) {
            return false;
        }

        boolean isStream = line.contains("\"type\":\"stream\"");
        boolean isContent = line.contains("\"type\":\"content\"");

        if (isStream || isContent) {
            return line.contains("\"text\"")
                    || line.contains("\"content\"")
                    || line.contains("\"delta\"")
                    || line.contains("\"raw\":true");
        }

        return line.contains("\"type\":\"conversation\"") && line.contains("<file");
    }

    /**
     * 转发 OpenLovable 的 generate SSE，并在转发过程中统计是否出现“可部署代码”。
     *
     * 统计规则：
     * - 只要出现过 type=stream/type=content 的增量事件，即认为上游输出了代码（hasDelta=true）。
     * - 若 type=complete 的 generatedCode 非空且包含 <file
     * 标签，则认为上游输出了最终代码（hasCompleteCode=true）。
     *
     * @param url                   OpenLovable generate SSE 上游地址
     * @param requestBody           适配后的请求体
     * @param outputStream          代理输出流（返回给前端的 SSE）
     * @param suppressEmptyComplete 是否在“无增量且 complete.generatedCode 为空”时抑制该 complete
     *                              事件（避免前端误判已完成）
     * @return 转发统计结果
     */
    private ForwardSseResult forwardGenerateSse(
            String url,
            String upstreamBaseUrl,
            Map<String, Object> requestBody,
            OutputStream outputStream,
            boolean suppressEmptyComplete,
            boolean suppressTimeoutError) throws IOException {
        boolean hasDelta = false;
        boolean hasCompleteCode = false;
        boolean hasTimeoutError = false;
        String lastError = null;

        URL targetUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(300000);

        String jsonBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(requestBody);
        connection.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
        connection.getOutputStream().flush();

        // 读取SSE流式响应（以空行分隔事件），逐行转发给前端
        try (InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                boolean shouldForward = true;

                line = normalizeRawSseLine(line);

                if (line.startsWith("data: ")) {
                    // 轻量统计：避免对每个 chunk 做 JSON 解析
                    // 只在增量中检测到 <file 标签时才认为“产生了可部署代码”
                    // 否则可能出现：上游仅输出 status/空 stream，导致代理误判成功而前端最终拿到空代码
                    if (isCodeDeltaLine(line)) {
                        hasDelta = true;
                    }

                    if (line.contains("\"type\":\"sandbox\"")) {
                        String discoveredSandboxId = extractSandboxIdFromSseDataLine(line);
                        if (discoveredSandboxId != null && !discoveredSandboxId.isBlank()) {
                            bindSandboxEndpoint(discoveredSandboxId, upstreamBaseUrl);
                            registerSandboxReady(discoveredSandboxId, upstreamBaseUrl, null, null);
                        }
                        String replacedSandboxId = extractSseStringField(line, "replacedSandboxId");
                        if (replacedSandboxId != null && !replacedSandboxId.isBlank()) {
                            bindSandboxEndpoint(replacedSandboxId, upstreamBaseUrl);
                            registerSandboxReady(replacedSandboxId, upstreamBaseUrl, null, null);
                        }
                    }

                    if (line.contains("\"type\":\"error\"")) {
                        String error = extractErrorFromSseDataLine(line);
                        lastError = error;
                        if (error != null && error.contains("Stream total timeout")) {
                            hasTimeoutError = true;
                            if (suppressTimeoutError) {
                                shouldForward = false;
                            }
                        }

                        // error 事件一般为终止性事件：转发（或抑制）后中断读取，让外层决定是否重试
                        if (shouldForward) {
                            outputStream.write((line + "\n\n").getBytes(StandardCharsets.UTF_8));
                            outputStream.flush();
                        }
                        break;
                    }

                    if (line.contains("\"type\":\"complete\"")) {
                        // V2.1优化：在 generate 阶段修复空 main.jsx（避免写入沙箱后报错）
                        line = fixMainJsxInCompleteEvent(line);

                        // 解析 complete 事件，判断是否真正包含可部署代码
                        String jsonStr = line.substring(6).trim();
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            Map<String, Object> eventData = mapper.readValue(jsonStr, Map.class);
                            Object generatedCodeObj = eventData.get("generatedCode");
                            String generatedCode = generatedCodeObj instanceof String ? (String) generatedCodeObj : "";

                            // V2.2新增：如果AI输出不包含 <file> 标签，尝试自动转换格式
                            if (generatedCode != null && !generatedCode.trim().isEmpty()
                                    && !generatedCode.contains("<file")) {
                                log.info("检测到AI输出缺少 <file> 标签，尝试自动格式转换...");
                                String converted = OpenLovableResponseSanitizer.convertToFileFormat(generatedCode);
                                if (converted != null && converted.contains("<file")) {
                                    log.info("格式转换成功，原长度: {}, 转换后长度: {}", generatedCode.length(), converted.length());
                                    eventData.put("generatedCode", converted);
                                    // 重新序列化并更新 line
                                    String updatedJson = mapper.writeValueAsString(eventData);
                                    line = "data: " + updatedJson;
                                    generatedCode = converted;
                                } else {
                                    log.warn("格式转换失败，AI输出可能不是有效代码");
                                }
                            }

                            if (generatedCode != null && !generatedCode.trim().isEmpty()
                                    && generatedCode.contains("<file")) {
                                hasCompleteCode = true;
                            } else if (!hasDelta && suppressEmptyComplete) {
                                // 无增量且 complete 无有效代码：抑制该 complete，后续在外层触发自动重试
                                shouldForward = false;
                            }
                        } catch (Exception parseError) {
                            // 解析失败时不影响转发，但也不将其计为"有效完整代码"
                            log.warn("解析OpenLovable complete事件失败，将继续转发原始数据: {}", parseError.getMessage());
                        }
                    }
                }

                if (shouldForward) {
                    if (line.isEmpty()) {
                        outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
                    } else {
                        outputStream.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                    outputStream.flush();
                    if (!line.isEmpty() && !line.startsWith(":")) {
                        log.debug("转发SSE消息: {}", line.substring(0, Math.min(line.length(), 100)));
                    }
                } else if (line.isEmpty()) {
                    // 即使抑制 data 行，也保留空行以维持 SSE 事件边界（客户端会忽略空事件）
                    outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }
            }
        } finally {
            connection.disconnect();
        }

        log.info("AI代码生成流式响应完成");
        return new ForwardSseResult(hasDelta, hasCompleteCode, hasTimeoutError, lastError);
    }

    /**
     * 从 SSE 的 data 行中提取 error 字段（仅用于兜底重试判断）
     *
     * @param line 形如 "data: {\"type\":\"error\",\"error\":\"...\"}"
     * @return error 内容（解析失败返回 null）
     */
    private String extractErrorFromSseDataLine(String line) {
        if (line == null || !line.startsWith("data:")) {
            return null;
        }
        String jsonStr = line.substring(5).trim();
        if (jsonStr.startsWith("{")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> eventData = mapper.readValue(jsonStr, Map.class);
                Object errorObj = eventData.get("error");
                return errorObj instanceof String ? (String) errorObj : null;
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 快速预览专用提示词（强调：可运行/少文件/短输出/避免超时）
     */
    /**
     * 获取通用的UI设计规范指令
     */
    private String getCommonDesignInstructions(String langName) {
        return String.format(
                """
                        ## 🎨 UI Design Standards (Mandatory)

                        ### 1. Visual Direction
                        - **Clear Direction**: Pick a bold, domain-appropriate visual direction; avoid generic SaaS styling.
                        - **Color Palette**: Avoid purple-first palettes. Prefer calm, modern combinations using Tailwind default colors (stone + amber + emerald, or sky + amber + slate). Do not invent custom color names.
                        - **Atmosphere**: Use layered gradients, soft radial glows, and subtle noise/patterns; avoid flat single-color backgrounds.

                        ### 2. Typography
                        - **Expressive Fonts**: Avoid Inter/Roboto/system. Import two Google Fonts and define heading/body families.
                        - **Examples**: Chinese → "Noto Serif SC" + "Noto Sans SC"; English → "Space Grotesk" + "Manrope".

                        ### 3. Components (Tailwind)
                        - **Cards**: `rounded-2xl`, soft shadows, thin borders, generous padding.
                        - **Buttons**: high-contrast, `rounded-lg`, subtle lift (`hover:translate-y-[-1px] hover:shadow-lg`).
                        - **Inputs**: clear focus ring and roomy spacing.

                        ### 4. Motion
                        - **Meaningful**: Add 1-2 animations (page-load + staggered reveal). Use `motion-safe` and keep 300-600ms durations.

                        ### 5. Layout & Responsiveness
                        - **Mobile-First**: Always use `md:` `lg:` prefixes for larger screens.
                        - **Container**: `max-w-7xl mx-auto px-4 sm:px-6 lg:px-8`.
                        - **Grid**: Use `grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6`.

                        ### 6. Icons
                        - Use `lucide-react` for all icons.
                        - Example: `<Activity className="w-5 h-5 text-emerald-500" />`
                        """,
                langName);
    }

    /**
     * 构建领域引导文本
     * 用途：当需求明显属于心理健康/青少年压力场景时，追加结构约束避免跑偏。
     */
    private String buildDomainGuidance(String requirement, String language) {
        if (!isYouthStressRequirement(requirement)) {
            return "";
        }

        String langName = "en".equalsIgnoreCase(language) ? "English" : "中文";
        return String.format(
                """
                        ## Domain Guardrails (Youth Stress Management)
                        - This product is a youth stress management system. Do NOT clone or reference unrelated industries (travel/booking/e-commerce), and never mention Airbnb unless explicitly requested.
                        - Include separate surfaces for **Student** and **Teacher/Counselor**.
                        - Must cover: stress self-assessment, mood diary, personalized exercises, risk alerts, and multi-agent collaboration (assessment / advice / alert).
                        - Explicitly include data privacy and anonymized insights.
                        - Visual tone: warm, calming, trustworthy, youth-friendly.
                        - Typography: choose expressive fonts suitable for %s (e.g., "Noto Serif SC" + "Noto Sans SC").
                        - Color direction: use Tailwind default colors (stone/amber/emerald or sky/amber/slate); avoid purple-first palettes.
                        """,
                langName);
    }

    /**
     * 规范化文件块中的 Tailwind 颜色别名
     *
     * 是什么：遍历 AI 生成的文件块内容并进行颜色别名替换。
     * 做什么：将 sand/seafoam/sage/coral 等别名替换为 Tailwind 内置色名。
     * 为什么：避免 @apply 使用不存在的类导致 Vite/Tailwind 构建失败。
     */
    private java.util.List<OpenLovableResponseSanitizer.FileBlock> normalizeTailwindColorAliasesInBlocks(
            java.util.List<OpenLovableResponseSanitizer.FileBlock> fileBlocks) {
        if (fileBlocks == null || fileBlocks.isEmpty()) {
            return fileBlocks;
        }

        java.util.List<OpenLovableResponseSanitizer.FileBlock> normalizedBlocks = new java.util.ArrayList<>(
                fileBlocks.size());
        boolean changed = false;

        for (OpenLovableResponseSanitizer.FileBlock block : fileBlocks) {
            String normalizedContent = normalizeTailwindColorAliases(block.content());
            if (!java.util.Objects.equals(block.content(), normalizedContent)) {
                changed = true;
                block = new OpenLovableResponseSanitizer.FileBlock(
                        block.normalizedPath(),
                        block.rawPath(),
                        block.openTag(),
                        normalizedContent,
                        block.closeTag());
            }
            normalizedBlocks.add(block);
        }

        if (changed) {
            log.info("已自动规范化AI输出中的Tailwind颜色别名，避免自定义色名导致白屏");
        }

        return changed ? normalizedBlocks : fileBlocks;
    }

    /**
     * 规范化内容中的 Tailwind 颜色别名
     *
     * 是什么：对单个文件内容进行颜色别名替换。
     * 做什么：将 sand/seafoam/sage/coral 色阶映射为内置颜色色阶。
     * 为什么：保证 @apply/bg/text 等 Tailwind 类不会因自定义色名而崩溃。
     */
    private String normalizeTailwindColorAliases(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        Matcher matcher = TAILWIND_COLOR_ALIAS_PATTERN.matcher(content);
        StringBuffer buffer = new StringBuffer();
        boolean changed = false;

        while (matcher.find()) {
            String alias = matcher.group(1);
            String shade = matcher.group(2);
            String mapped = TAILWIND_COLOR_ALIAS_MAP.get(alias);
            if (mapped == null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String replacement = mapped + "-" + shade;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            changed = true;
        }

        if (!changed) {
            return content;
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * 判断需求是否为青少年压力管理类
     * 用途：决定是否注入心理健康领域提示，减少模型误判。
     */
    private boolean isYouthStressRequirement(String requirement) {
        if (requirement == null || requirement.isBlank()) {
            return false;
        }

        String lower = requirement.toLowerCase();
        String[] keywords = new String[] {
                "压力", "情绪", "心理", "青少年", "学生", "班主任", "心理老师", "焦虑", "抑郁",
                "stress", "mental", "mood", "emotion", "counselor", "teen", "adolescent"
        };
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 快速预览专用提示词（强调：可运行/少文件/短输出/避免超时）
     */
    private String enhancePromptForFastPreview(String originalPrompt, String language) {
        String langName = "en".equalsIgnoreCase(language) ? "English" : "中文";
        String designSpecs = getCommonDesignInstructions(langName);

        return String.format("""
                你是资深前端工程师。目标：在 180 秒内生成“可运行”的 Vite + React Web 应用预览代码，优先保证可编译/可启动。

                用户需求：
                %s

                强制要求：
                1) **语言要求**：生成的网页UI文案必须强制使用**%s**。
                2) 只输出代码，不要解释文字；输出必须使用 OpenLovable 格式：<file path="...">... </file>
                3) 文件数量尽量少（建议 ≤ 12 个）；避免引入重量依赖（不要新增 UI 框架、不要 MapStruct/后端代码）
                4) 先确保基础可运行：index.html、package.json、vite 配置、src/main.jsx、src/App.jsx
                5) 必须使用 **Tailwind CSS** 实现设计，严禁写原生 CSS 文件。
                6) 必须参考下方的 [UI Design Standards] 实现美观的界面，拒绝简陋设计。

                %s

                输出格式示例（仅示例，不要重复示例文字）：
                <file path="src/App.jsx">...</file>
                """, originalPrompt, langName, designSpecs);
    }

    /**
     * 推理模式提示词（用于深度思考/更高质量代码生成）
     *
     * 说明：
     * - “推理模式”要求模型在内部先做规划与推理，但输出中不要泄露思考过程，只输出代码
     * - 对接 deepseek / deepseek-r1-0528 这类推理/代码能力更强的模型
     * - 相比 quality 提示词：更聚焦“可运行 + 架构清晰 + 不超时”，避免过长的 UI 规范导致上游 240s 总超时
     */
    private String enhancePromptForReasoning(String originalPrompt, String language) {
        String langName = "en".equalsIgnoreCase(language) ? "English" : "中文";
        String designSpecs = getCommonDesignInstructions(langName);

        return String.format(
                """
                        你是资深全栈工程师，请使用“推理模式”在内部先完成规划（不要把推理过程输出给用户），然后输出可运行的代码。

                        用户需求：
                        %s

                        强制要求：
                        1) **语言要求**：生成的网页UI文案必须强制使用**%s**。
                        2) 只输出代码，不要输出解释/分析/清单；输出必须使用 OpenLovable 格式：<file path="...">...</file>
                        3) 先规划文件与依赖顺序（在内部完成），保证项目可直接 `pnpm install && pnpm dev` 启动
                        4) 产物必须包含：index.html、package.json、vite 配置、src/main.jsx、src/App.jsx（可再加少量组件/CSS）
                        5) 控制输出规模：文件数建议 ≤ 18，避免引入重量依赖（不要新增 UI 框架）
                        6) 生成过程中保持持续流式输出，优先写入关键入口文件（src/main.jsx / src/App.jsx）
                        7) **Visuals**: Implement a polished, professional UI using Tailwind CSS. Follow the [UI Design Standards] below strictly.

                        %s

                        输出格式示例（仅示例，不要重复示例文字）：
                        <file path="src/App.jsx">...</file>
                        """,
                originalPrompt, langName, designSpecs);
    }

    /**
     * 增强提示词 - 添加结构化思维要求（Chain-of-Thought）+ UI设计规范
     *
     * 核心原理：
     * 1. 强制AI在生成代码前先进行<thinking>分析
     * 2. main.jsx作为固定模板**第一个**生成，避免截断
     * 3. 明确文件规划、依赖关系、生成顺序
     * 4. V2.2新增：UI设计规范，让生成的页面更美观
     *
     * V2.2优化：添加UI设计规范，生成更专业的界面
     *
     * @param originalPrompt 用户原始需求
     * @return 增强后的提示词
     */
    private String enhancePromptWithStructuredThinking(String originalPrompt, String language) {
        String langName = "en".equalsIgnoreCase(language) ? "English" : "中文";
        return String.format(
                """
                        ## 🎯 代码生成任务

                        ### 用户需求
                        %s

                        ---

                        ## 🎨 UI设计规范（强制遵守）

                        ### 0. 语言与文案
                        - **语言**：所有已显示的文案必须使用**%s**。

                        ### 1. 视觉风格
                        - **现代简约设计**：干净、留白充足、视觉层次清晰
                        - **配色方案**：使用有方向性的渐变与配色（如 from-emerald-500 via-amber-400 to-rose-400 或 from-sky-500 via-teal-400 to-amber-300），避免紫色主导与单调纯色背景
                        - **卡片设计**：使用 rounded-2xl shadow-lg 圆角阴影，hover时添加 hover:shadow-xl transition-all
                        - **背景**：主背景使用渐变 + 轻量纹理/径向光斑（示例：bg-gradient-to-br from-slate-50 to-stone-100 + bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))]）

                        ### 2. 排版规范
                        - **字体**：选择两种非默认字体（避免 Inter/Roboto/system），标题可用衬线或几何风格，正文用高可读字体（示例：Noto Serif SC + Noto Sans SC）
                        - **标题**：使用 text-2xl md:text-4xl font-bold，搭配渐变色 bg-gradient-to-r from-emerald-600 via-amber-500 to-rose-500 bg-clip-text text-transparent
                        - **正文**：text-gray-600 dark:text-gray-300，行高 leading-relaxed
                        - **间距**：组件之间使用 space-y-6 或 gap-6，页面边距 px-4 md:px-8 py-8

                        ### 3. 交互动效
                        - **按钮**：主按钮使用渐变色 bg-gradient-to-r from-emerald-500 via-amber-400 to-rose-400 hover:from-emerald-600 hover:to-rose-500 text-white rounded-lg shadow-md hover:shadow-lg transition-all
                        - **卡片悬停**：hover:scale-[1.02] hover:shadow-xl transition-all duration-300
                        - **输入框**：focus:ring-2 focus:ring-emerald-500 focus:border-transparent rounded-lg border-gray-300

                        ### 4. 图标使用
                        - **图标库**：优先使用 lucide-react（安装后导入）
                        - **图标样式**：w-5 h-5 或 w-6 h-6，与文字配合时使用 inline-flex items-center gap-2

                        ### 5. 响应式设计
                        - **移动优先**：基础样式为移动端，md: 前缀用于平板，lg: 前缀用于桌面
                        - **网格布局**：grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6
                        - **最大宽度**：max-w-7xl mx-auto 居中容器

                        ### 6. 组件示例样式

                        #### 英雄区(Hero Section)
                        ```jsx
                        <section className="relative overflow-hidden bg-gradient-to-br from-emerald-500 via-amber-400 to-rose-400 text-white py-20 md:py-32">
                          <div className="absolute inset-0 bg-black/10"></div>
                          <div className="relative max-w-7xl mx-auto px-4 text-center">
                            <h1 className="text-4xl md:text-6xl font-bold mb-6">标题文字</h1>
                            <p className="text-xl md:text-2xl text-white/80 mb-8 max-w-2xl mx-auto">描述文字</p>
                            <button className="bg-white text-indigo-600 px-8 py-3 rounded-full font-semibold hover:bg-gray-100 transition-all shadow-lg hover:shadow-xl">
                              开始使用
                            </button>
                          </div>
                        </section>
                        ```

                        #### 功能卡片
                        ```jsx
                        <div className="group bg-white rounded-2xl p-6 shadow-lg hover:shadow-xl transition-all duration-300 hover:scale-[1.02] border border-gray-100">
                          <div className="w-12 h-12 bg-gradient-to-br from-indigo-500 to-purple-500 rounded-xl flex items-center justify-center mb-4">
                            <Icon className="w-6 h-6 text-white" />
                          </div>
                          <h3 className="text-xl font-semibold text-gray-900 mb-2">功能标题</h3>
                          <p className="text-gray-600">功能描述文字</p>
                        </div>
                        ```

                        ---

                        ## 📋 强制执行：结构化思维过程

                        在生成任何代码之前，你**必须**在 `<thinking>` 标签中完成以下分析：

                        ### Step 1: 需求理解
                        - 用户要构建什么应用？核心功能有哪些？

                        ### Step 2: UI设计规划
                        - 页面布局结构（Hero、Features、Footer等）
                        - 配色方案和视觉风格
                        - 关键交互效果

                        ### Step 3: 文件规划
                        列出需要创建的文件（不含main.jsx，它是固定的）

                        ### Step 4: 依赖分析
                        - 需要安装哪些第三方包？（lucide-react等）

                        ---

                        ## ⚠️ 关键要求

                        1. **main.jsx是固定模板** - 直接使用下方提供的代码，**第一个输出**
                        2. **代码必须完整** - 每个文件从第一行写到最后一行，禁止截断或省略
                        3. **使用标准Tailwind类** - 遵循上方UI设计规范（禁止bg-background等自定义类）
                        4. **视觉效果优先** - 必须使用渐变色、阴影、圆角、动画，让页面看起来专业美观

                        ---

                        ## 📤 输出格式（严格按此顺序）

                        ### 第一步：输出思考过程
                        ```xml
                        <thinking>
                        [简要分析：需求理解、UI设计规划、文件规划、依赖分析]
                        </thinking>
                        ```

                        ### 第二步：**首先输出main.jsx（固定代码，直接复制）**
                        ```xml
                        <file path="src/main.jsx">
                        import React from 'react'
                        import ReactDOM from 'react-dom/client'
                        import App from './App'
                        import './index.css'

                        ReactDOM.createRoot(document.getElementById('root')).render(
                          <React.StrictMode>
                            <App />
                          </React.StrictMode>,
                        )
                        </file>
                        ```

                        ### 第三步：输出index.css（包含自定义动画）
                        ```xml
                        <file path="src/index.css">
                        @tailwind base;
                        @tailwind components;
                        @tailwind utilities;

                        /* 自定义动画 */
                        @keyframes float {
                          0%%, 100%% { transform: translateY(0); }
                          50%% { transform: translateY(-10px); }
                        }

                        @keyframes fadeInUp {
                          from { opacity: 0; transform: translateY(20px); }
                          to { opacity: 1; transform: translateY(0); }
                        }

                        .animate-float { animation: float 3s ease-in-out infinite; }
                        .animate-fade-in-up { animation: fadeInUp 0.6s ease-out forwards; }

                        /* 渐变文字 */
                        .gradient-text {
                          @apply bg-gradient-to-r from-indigo-600 to-purple-600 bg-clip-text text-transparent;
                        }

                        /* 玻璃态效果 */
                        .glass {
                          @apply bg-white/80 backdrop-blur-lg border border-white/20;
                        }
                        </file>
                        ```

                        ### 第四步：输出组件文件
                        ```xml
                        <file path="src/components/XXX.jsx">
                        [完整组件代码 - 必须遵循UI设计规范]
                        </file>
                        ```

                        ### 第五步：输出App.jsx
                        ```xml
                        <file path="src/App.jsx">
                        [完整主组件代码 - 整合所有组件，页面布局美观]
                        </file>
                        ```

                        ---

                        ## 🚨 再次强调

                        1. **main.jsx必须第一个输出！** 它是Vite应用入口，代码固定不变
                        2. **UI必须美观！** 严格遵循上方UI设计规范，使用渐变色、阴影、动画等现代设计元素
                        3. **禁止使用丑陋的纯白背景！** 至少使用 bg-gradient-to-br from-slate-50 to-slate-100

                        现在请开始：先<thinking>，然后按顺序输出所有文件（main.jsx第一个）。
                        """,
                originalPrompt, langName);
    }

    /**
     * 创建项目ZIP包下载
     *
     * POST /v1/openlovable/sandbox/create-zip
     *
     * 说明：
     * - 代理 open-lovable-cn 的 /api/create-zip 接口
     * - 返回 base64 编码的 ZIP 文件，供前端下载
     *
     * 响应示例：
     * {
     * "success": true,
     * "dataUrl": "data:application/zip;base64,UEsDBBQAAAAI...",
     * "fileName": "sandbox-project.zip",
     * "message": "Zip file created successfully"
     * }
     */
    @PostMapping("/sandbox/create-zip")
    public ResponseEntity<?> createZip(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> requestBody = request != null ? new HashMap<>(request) : new HashMap<>();
        if (!(requestBody.get("sandboxId") instanceof String sandboxIdRaw) || sandboxIdRaw.isBlank()) {
            return ResponseEntity.badRequest().body(Result.error(400, "缺少必需参数: sandboxId"));
        }
        String sandboxId = sandboxIdRaw.trim();
        requestBody.put("sandboxId", sandboxId);

        String targetBaseUrl = resolveOpenLovableBaseUrlBySandboxId(sandboxId);
        String url = targetBaseUrl + "/api/create-zip";
        log.info("创建项目ZIP包: {}", url);

        OpenLovableSandboxLockService.SandboxLockLease lockLease =
                acquireSandboxOperationLock(sandboxId, "create-zip");
        if (!lockLease.acquired()) {
            sleepQuietly(createZipLockRetryDelayMs);
            lockLease = acquireSandboxOperationLock(sandboxId, "create-zip");
        }
        if (!lockLease.acquired()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "沙箱正忙，请稍后重试"));
        }

        boolean markedBusy = markSandboxBusy(sandboxId, lockLease, "create-zip", targetBaseUrl);
        if (!markedBusy) {
            releaseSandboxLock(lockLease);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(409, "沙箱状态冲突，请稍后重试"));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            if (response.getBody() == null) {
                log.warn("创建ZIP包响应为空，尝试回退生成: sandboxId={}", sandboxId);
                Map<String, Object> fallback = createZipWithRetry(sandboxId);
                return ResponseEntity.ok(Result.success(fallback));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

            Object successObj = responseBody.get("success");
            if (successObj instanceof Boolean success && !success) {
                String error = responseBody.get("error") instanceof String e ? e : "未知错误";
                log.warn("创建ZIP包失败（上游返回 success=false），尝试回退生成: sandboxId={}, error={}", sandboxId, error);
                Map<String, Object> fallback = createZipWithRetry(sandboxId);
                return ResponseEntity.ok(Result.success(fallback));
            }

            Object dataUrlObj = responseBody.get("dataUrl");
            if (!(dataUrlObj instanceof String dataUrl) || dataUrl.isBlank()) {
                log.warn("创建ZIP包上游缺少 dataUrl，尝试回退生成: sandboxId={}", sandboxId);
                Map<String, Object> fallback = createZipWithRetry(sandboxId);
                return ResponseEntity.ok(Result.success(fallback));
            }

            log.info("ZIP包创建成功: fileName={}", responseBody.get("fileName"));
            return ResponseEntity.ok(Result.success(responseBody));

        } catch (Exception e) {
            log.warn("创建ZIP包上游异常，尝试回退生成: sandboxId={}, reason={}", sandboxId, e.getMessage());
            try {
                Map<String, Object> fallback = createZipWithRetry(sandboxId);
                return ResponseEntity.ok(Result.success(fallback));
            } catch (Exception fallbackError) {
                log.error("创建ZIP包回退生成也失败: sandboxId={}", sandboxId, fallbackError);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Result.error("创建ZIP包失败: " + fallbackError.getMessage()));
            }
        } finally {
            markSandboxReady(sandboxId, lockLease, targetBaseUrl);
            releaseSandboxLock(lockLease);
        }
    }

    /**
     * 通过 /api/sandbox/execute 在沙箱内创建 ZIP 并 base64 读取
     *
     * 是什么：当上游 open-lovable-cn 的 /api/create-zip 不可用时的兜底实现。
     * 做什么：在沙箱工作目录中执行 zip（排除 node_modules 等大目录），再把 zip 文件 base64 编码回传。
     * 为什么：解决“上游 500/no body 导致前端无法下载”的稳定性问题。
     *
     * @param sandboxId OpenLovable 沙箱ID
     * @return 与上游 create-zip 兼容的响应体（包含 dataUrl/fileName/message）
     */
    private Map<String, Object> createZipBySandboxExecute(String sandboxId) {
        final String zipPath = "/tmp/ingenio_sandbox.zip";

        // 1) 在沙箱内创建 zip（排除 node_modules 等体积巨大的目录，避免超时/内存峰值）
        SandboxExecResult zipResult = executeSandboxCommand(
                sandboxId,
                "zip -r " + zipPath + " . -x node_modules/* -x .git/* -x dist/* -x .vite/*",
                60);
        if (zipResult.exitCode != 0) {
            throw new IllegalStateException("沙箱内 zip 失败: " + firstNonBlank(zipResult.stderr, zipResult.stdout));
        }

        // 2) base64 读取 zip 内容（不同环境可能会换行，后续在服务端清洗）
        SandboxExecResult base64Result = executeSandboxCommand(sandboxId, "base64 " + zipPath, 60);
        if (base64Result.exitCode != 0) {
            throw new IllegalStateException("沙箱内 base64 失败: " + firstNonBlank(base64Result.stderr, base64Result.stdout));
        }

        String base64 = (base64Result.stdout != null ? base64Result.stdout : "")
                .replace("\n", "")
                .replace("\r", "")
                .trim();
        if (base64.isBlank()) {
            throw new IllegalStateException("沙箱内 base64 输出为空");
        }

        return Map.of(
                "success", true,
                "dataUrl", "data:application/zip;base64," + base64,
                "fileName", "sandbox-project.zip",
                "message", "Zip file created successfully (fallback)");
    }

    /**
     * create-zip 回退重试封装
     *
     * 是什么：对沙箱内 zip + base64 的回退流程增加重试。
     * 做什么：在沙箱未就绪时自动等待并重试。
     * 为什么：提升下载稳定性，减少偶发失败。
     *
     * @param sandboxId 沙箱ID
     * @return 回退生成的 zip 信息
     */
    private Map<String, Object> createZipWithRetry(String sandboxId) {
        int attempts = Math.max(1, createZipMaxAttempts);
        Exception lastError = null;
        String effectiveSandboxId = sandboxId;
        boolean recoveredOnce = false;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return createZipBySandboxExecute(effectiveSandboxId);
            } catch (Exception e) {
                lastError = e;

                if (!recoveredOnce && shouldRecoverSandboxForZip(e)) {
                    String recoveredSandboxId = recoverSandboxForZip(effectiveSandboxId);
                    if (recoveredSandboxId != null && !recoveredSandboxId.isBlank()) {
                        effectiveSandboxId = recoveredSandboxId;
                        recoveredOnce = true;
                        log.info("create-zip 检测到无活跃沙箱，已触发恢复并重试: oldSandboxId={}, newSandboxId={}",
                                sandboxId,
                                effectiveSandboxId);
                        continue;
                    }
                }

                if (attempt >= attempts) {
                    break;
                }
                log.warn("创建ZIP包回退失败，准备重试: sandboxId={}, attempt={}/{}",
                        effectiveSandboxId,
                        attempt,
                        attempts);
                sleepQuietly(createZipRetryDelayMs);
            }
        }
        String message = lastError != null ? lastError.getMessage() : "未知错误";
        throw new IllegalStateException("沙箱内创建ZIP失败: " + message, lastError);
    }

    /**
     * 判断是否需要触发沙箱恢复
     *
     * 是什么：识别“无活跃沙箱/沙箱不存在”类错误。
     * 做什么：用于 create-zip 回退中的一次性恢复分支。
     * 为什么：当上游 active sandbox 丢失时，直接重试通常无效，需要先恢复上下文。
     */
    private boolean shouldRecoverSandboxForZip(Exception error) {
        if (error == null || error.getMessage() == null) {
            return false;
        }
        String message = error.getMessage().toLowerCase();
        return message.contains("no active sandbox")
                || message.contains("sandbox not found")
                || message.contains("sandbox does not exist");
    }

    /**
     * 尝试恢复可用于下载的沙箱上下文
     *
     * 是什么：针对 create-zip 失败时的沙箱恢复动作。
     * 做什么：请求上游重建/复用沙箱并同步路由绑定与状态中心。
     * 为什么：提升“下载前端页面”在上游单活抖动场景下的成功率。
     */
    private String recoverSandboxForZip(String sandboxId) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return null;
        }

        String normalizedSandboxId = sandboxId.trim();
        String targetBaseUrl = resolveOpenLovableBaseUrlBySandboxId(normalizedSandboxId);
        String url = targetBaseUrl + "/api/create-ai-sandbox-v2";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Step 1: 先尝试销毁旧沙箱，避免上游继续复用“无 active”的脏状态
            try {
                String killUrl = UriComponentsBuilder
                        .fromHttpUrl(targetBaseUrl + "/api/kill-sandbox")
                        .queryParam("sandboxId", normalizedSandboxId)
                        .toUriString();
                HttpEntity<Map<String, Object>> killEntity = new HttpEntity<>(
                        Map.of("sandboxId", normalizedSandboxId),
                        headers);
                restTemplate.postForEntity(killUrl, killEntity, Map.class);
            } catch (Exception killError) {
                log.warn("create-zip 恢复前销毁旧沙箱失败，将继续尝试新建: sandboxId={}, err={}",
                        normalizedSandboxId,
                        killError.getMessage());
            }

            // Step 2: 新建沙箱（不传 sandboxId，避免上游复用旧实例状态）
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(new HashMap<>(), headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<?, ?> body = response.getBody() != null ? response.getBody() : Map.of();

            String recoveredSandboxId = body.get("sandboxId") instanceof String sid && !sid.isBlank()
                    ? sid.trim()
                    : null;

            if (recoveredSandboxId == null || recoveredSandboxId.isBlank()) {
                log.warn("create-zip 沙箱恢复失败: 上游未返回 sandboxId, oldSandboxId={}", normalizedSandboxId);
                return null;
            }

            bindSandboxEndpoint(recoveredSandboxId, targetBaseUrl);
            registerSandboxReady(recoveredSandboxId, targetBaseUrl, null, null);
            renewSandboxHeartbeat(recoveredSandboxId);

            return recoveredSandboxId;
        } catch (Exception e) {
            log.warn("create-zip 沙箱恢复失败: sandboxId={}, err={}", normalizedSandboxId, e.getMessage());
            return null;
        }
    }

    /**
     * 安静睡眠
     *
     * 是什么：带中断恢复的 sleep 工具。
     * 做什么：用于回退重试间隔控制。
     * 为什么：避免 InterruptedException 被吞掉。
     *
     * @param delayMs 睡眠毫秒数
     */
    private void sleepQuietly(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 调用 open-lovable-cn 的 /api/sandbox/execute 执行命令
     *
     * 是什么：对沙箱命令执行的最小封装。
     * 做什么：把 sandboxId/command/timeout 封装为 JSON 请求，并解析 exitCode/stdout/stderr。
     * 为什么：保证 create-zip 回退逻辑无需依赖上游特定实现细节。
     */
    private SandboxExecResult executeSandboxCommand(String sandboxId, String command, int timeoutSeconds) {
        String url = resolveOpenLovableBaseUrlBySandboxId(sandboxId) + "/api/sandbox/execute";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sandboxId", sandboxId);
        requestBody.put("command", command);
        requestBody.put("timeout", timeoutSeconds);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // 注意：/api/sandbox/execute 的 stdout 可能非常大（例如 base64 的 zip 内容）。
        // RestTemplate 直接用 Map.class 反序列化会触发 Jackson 默认 maxStringLength=20M 的限制，导致解析失败。
        // 这里先按 String 接收，再用“放宽限制”的 ObjectMapper 解析，避免下载链路被长度阈值卡死。
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        Map<?, ?> body = parseSandboxExecBody(response.getBody());

        int exitCode = safeToInt(body.get("exitCode"), 0);
        Object stdoutObj = body.get("stdout");
        if (stdoutObj == null)
            stdoutObj = body.get("output");
        if (stdoutObj == null)
            stdoutObj = body.get("message");

        Object stderrObj = body.get("stderr");

        return new SandboxExecResult(exitCode, safeToString(stdoutObj), safeToString(stderrObj));
    }

    /**
     * 解析 OpenLovable /api/sandbox/execute 的 JSON 响应体
     *
     * 是什么：将 JSON 字符串解析为 Map。
     * 做什么：通过自定义 StreamReadConstraints 放宽 maxStringLength，支持大 stdout（base64）。
     * 为什么：默认 20MB 上限会导致“zip 下载”在中等体积项目上直接失败。
     */
    private Map<?, ?> parseSandboxExecBody(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return SandboxExecJsonSupport.MAPPER.readValue(body, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("解析沙箱执行响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * /api/sandbox/execute JSON 解析支持（放宽 maxStringLength）
     */
    private static final class SandboxExecJsonSupport {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = buildMapper();

        private static com.fasterxml.jackson.databind.ObjectMapper buildMapper() {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.getFactory().setStreamReadConstraints(
                    com.fasterxml.jackson.core.StreamReadConstraints.builder()
                            // 经验值：base64 输出容易超过 20MB；放宽到 80MB，仍保留上限避免无限制吃内存
                            .maxStringLength(80_000_000)
                            .build());
            return mapper;
        }

        private SandboxExecJsonSupport() {
        }
    }

    /**
     * 选择第一个非空字符串
     */
    private String firstNonBlank(String... candidates) {
        if (candidates == null)
            return "";
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank())
                return candidate;
        }
        return "";
    }

    /**
     * 安全转换为 int
     */
    private int safeToInt(Object value, int fallback) {
        if (value instanceof Number n)
            return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * 安全转换为 String
     */
    private String safeToString(Object value) {
        if (value == null)
            return "";
        return String.valueOf(value);
    }

    /**
     * 解析请求体内携带的现有文件快照
     *
     * 是什么：从 existingFiles 字段提取文件路径与内容。
     * 做什么：将前端传入的文件列表或 Map 转为 Map<Path, Content>。
     * 为什么：在沙箱读取失败时仍可进行增量合并，避免覆盖丢文件。
     */
    private Map<String, String> extractExistingFilesFromRequest(Map<String, Object> request) {
        if (request == null) {
            return Map.of();
        }
        Object raw = request.get("existingFiles");
        if (raw == null) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String path = entry.getKey() != null ? String.valueOf(entry.getKey()) : "";
                String normalizedPath = normalizeSandboxFilePath(path);
                if (normalizedPath.isBlank()) {
                    continue;
                }
                String content = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
                result.put(normalizedPath, content);
            }
            return result;
        }

        if (raw instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (!(item instanceof Map<?, ?> entry)) {
                    continue;
                }
                Object pathObj = entry.get("path");
                String normalizedPath = normalizeSandboxFilePath(pathObj != null ? String.valueOf(pathObj) : "");
                if (normalizedPath.isBlank()) {
                    continue;
                }
                Object contentObj = entry.get("content");
                String content = contentObj != null ? String.valueOf(contentObj) : "";
                result.put(normalizedPath, content);
            }
        }

        return result;
    }

    /**
     * 判断 apply 请求是否应标记为编辑模式
     *
     * 是什么：基于请求参数推断是否为增量修复。
     * 做什么：当存在合并/补丁信息时标记 isEdit=true。
     * 为什么：让上游 apply 走编辑逻辑，避免覆盖丢文件。
     */
    boolean shouldMarkApplyAsEdit(Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return false;
        }

        Object isEditObj = request.get("isEdit");
        if (isEditObj instanceof Boolean b && b) {
            return true;
        }

        Object mergeFlag = request.get("mergeExisting");
        if (mergeFlag instanceof Boolean b && b) {
            return true;
        }

        String mergeMode = safeToString(request.get("mergeMode"));
        if ("patch".equalsIgnoreCase(mergeMode) || "preserve".equalsIgnoreCase(mergeMode)) {
            return true;
        }

        Object existingFiles = request.get("existingFiles");
        if (hasNonEmptyFileSnapshot(existingFiles)) {
            return true;
        }

        Object existingFilesAlt = request.get("existing_files");
        return hasNonEmptyFileSnapshot(existingFilesAlt);
    }

    /**
     * 判断文件快照是否为空
     *
     * 是什么：通用的快照存在性判断。
     * 做什么：识别 Map/List 形式的 existingFiles 是否包含内容。
     * 为什么：避免空快照误判为编辑模式。
     */
    private boolean hasNonEmptyFileSnapshot(Object snapshot) {
        if (snapshot == null) {
            return false;
        }
        if (snapshot instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (snapshot instanceof List<?> list) {
            return !list.isEmpty();
        }
        return false;
    }

    /**
     * 从 existingFiles 快照推导 fileManifest
     *
     * 是什么：根据 existingFiles/ existing_files 结构提取路径清单。
     * 做什么：在前端仅传文件内容而未传 manifest 时自动补齐。
     * 为什么：避免上游出现“No file manifest available”而退化为全量重生成。
     */
    private List<String> deriveFileManifestFromSnapshot(Object snapshot) {
        if (snapshot == null) {
            return List.of();
        }

        java.util.LinkedHashSet<String> manifest = new java.util.LinkedHashSet<>();
        if (snapshot instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                if (key == null) {
                    continue;
                }
                String normalizedPath = normalizeSandboxFilePath(String.valueOf(key));
                if (!normalizedPath.isBlank()) {
                    manifest.add(normalizedPath);
                }
            }
        } else if (snapshot instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> entry)) {
                    continue;
                }
                Object pathObj = entry.get("path");
                String normalizedPath = normalizeSandboxFilePath(pathObj != null ? String.valueOf(pathObj) : "");
                if (!normalizedPath.isBlank()) {
                    manifest.add(normalizedPath);
                }
            }
        }

        return manifest.isEmpty() ? List.of() : new ArrayList<>(manifest);
    }

    /**
     * 补齐 OpenLovable 上游需要的 context 文件清单字段
     *
     * 是什么：把 existingFiles/fileManifest 放入 context 中。
     * 做什么：让上游能读取文件清单与已有文件快照。
     * 为什么：避免上游提示缺少 file manifest 导致全量重生成。
     */
    void enrichContextWithFileManifest(Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return;
        }
        Object existingFiles = request.get("existingFiles");
        Object existingFilesSnake = request.get("existing_files");
        Object fileManifest = request.get("fileManifest");
        Object fileManifestSnake = request.get("file_manifest");
        if (existingFiles == null && existingFilesSnake == null && fileManifest == null && fileManifestSnake == null) {
            return;
        }

        Map<String, Object> context = new HashMap<>();
        Object rawContext = request.get("context");
        if (rawContext instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    context.put(key, entry.getValue());
                }
            }
        }

        if (existingFiles != null) {
            context.put("existingFiles", existingFiles);
        }
        if (existingFilesSnake != null) {
            context.put("existing_files", existingFilesSnake);
        }

        List<String> derivedManifest = List.of();
        if (fileManifest == null && fileManifestSnake == null) {
            Object manifestSource = existingFiles != null ? existingFiles : existingFilesSnake;
            derivedManifest = deriveFileManifestFromSnapshot(manifestSource);
            if (!derivedManifest.isEmpty()) {
                fileManifest = derivedManifest;
                fileManifestSnake = derivedManifest;
                request.put("fileManifest", derivedManifest);
                request.put("file_manifest", derivedManifest);
                log.debug("参数适配: 从 existingFiles 推导 fileManifest, count={}", derivedManifest.size());
            }
        }

        if (fileManifest != null) {
            context.put("fileManifest", fileManifest);
        }
        if (fileManifestSnake != null) {
            context.put("file_manifest", fileManifestSnake);
        }

        request.put("context", context);
    }

    private OpenLovableRepairIntentRouter resolveIntentRouter() {
        return repairIntentRouter != null ? repairIntentRouter : new OpenLovableRepairIntentRouter();
    }

    private OpenLovableRepairContextBuilder resolveContextBuilder() {
        return repairContextBuilder != null ? repairContextBuilder : new OpenLovableRepairContextBuilder();
    }

    private List<String> buildFileManifest(Map<String, String> existingFiles, String sandboxId) {
        if (existingFiles != null && !existingFiles.isEmpty()) {
            return new ArrayList<>(existingFiles.keySet());
        }
        if (sandboxId != null && !sandboxId.isBlank()) {
            return listSandboxFiles(sandboxId);
        }
        return List.of();
    }

    private void updateRepairMemory(
            String sandboxId,
            OpenLovableRepairIntentResult intentResult,
            String userRequest,
            String errorLogText,
            String relatedFile,
            List<String> fileManifest,
            String outcome) {
        if (repairMemoryService == null || sandboxId == null || sandboxId.isBlank()) {
            return;
        }
        List<String> fileHints = buildFileHints(relatedFile, fileManifest);
        OpenLovableRepairSummaryUpdate update = new OpenLovableRepairSummaryUpdate(
                intentResult != null ? intentResult.intent() : null,
                userRequest,
                trimToMax(errorLogText, 200),
                fileHints,
                outcome);
        repairMemoryService.updateMemory(sandboxId, update);
    }

    private List<String> buildFileHints(String relatedFile, List<String> fileManifest) {
        java.util.LinkedHashSet<String> hints = new java.util.LinkedHashSet<>();
        if (relatedFile != null && !relatedFile.isBlank()) {
            hints.add(relatedFile);
        }
        if (fileManifest != null) {
            for (String file : fileManifest) {
                if (file == null || file.isBlank()) {
                    continue;
                }
                hints.add(file);
                if (hints.size() >= 5) {
                    break;
                }
            }
        }
        return new java.util.ArrayList<>(hints);
    }

    private String trimToMax(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, maxLength));
    }

    /**
     * 沙箱命令执行结果
     *
     * @param exitCode 命令退出码
     * @param stdout   标准输出
     * @param stderr   标准错误
     */
    private record SandboxExecResult(int exitCode, String stdout, String stderr) {
    }

    /**
     * 智能刷新预览（自动检测并修复代码错误）
     *
     * POST /v1/openlovable/sandbox/smart-refresh
     *
     * 说明：
     * - 代理 open-lovable-cn 的 /api/smart-refresh-preview 接口
     * - 读取沙箱中的源代码，校验依赖完整性
     * - 自动修复缺失文件、截断文件和入口挂载问题
     * - 使用 AI 模型进行代码补全
     *
     * 请求体示例：
     * {
     * "sandboxId": "sb_xxxxx",
     * "model": "deepseek-v3"
     * }
     *
     * 响应示例：
     * {
     * "success": true,
     * "fixed": true,
     * "filesCreated": ["src/utils.js"],
     * "filesUpdated": ["src/main.jsx"],
     * "issues": [...],
     * "message": "Auto-fixed: 1 created, 1 updated"
     * }
     */
    @PostMapping("/sandbox/smart-refresh")
    public ResponseEntity<?> smartRefreshPreview(@RequestBody(required = false) Map<String, Object> request) {
        try {
            // V2.5 Auto-Repair: 检查是否包含运行时错误日志
            if (request != null && request.containsKey("errorLog") && request.get("errorLog") != null) {
                log.info("触发智能修复 (Runtime Auto-Repair)...");
                return handleRuntimeErrorFix(request);
            }

            // 传递模型和其他配置
            Map<String, Object> requestBody = request != null ? new HashMap<>(request) : new HashMap<>();

            String sandboxId = requestBody.get("sandboxId") instanceof String sid ? sid : null;
            String url = resolveOpenLovableBaseUrlBySandboxId(sandboxId) + "/api/smart-refresh-preview";
            log.info("智能刷新预览（自动修复）: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 如果没有指定模型，使用默认的 deepseek-v3
            if (!requestBody.containsKey("model") || requestBody.get("model") == null) {
                requestBody.put("model", "deepseek-v3");
            }

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            log.info("智能刷新请求: model={}", requestBody.get("model"));

            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            if (response.getBody() == null) {
                log.error("智能刷新响应为空");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Result.error("智能刷新失败: 响应为空"));
            }

            Map<String, Object> responseBody = response.getBody();

            // 检查上游响应是否成功
            Object successObj = responseBody.get("success");
            if (successObj instanceof Boolean success && !success) {
                String error = responseBody.get("error") instanceof String e ? e : "未知错误";
                log.warn("智能刷新失败: {}", error);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Result.error("智能刷新失败: " + error));
            }

            Object fixed = responseBody.get("fixed");
            Object filesCreated = responseBody.get("filesCreated");
            Object filesUpdated = responseBody.get("filesUpdated");
            String message = responseBody.get("message") instanceof String m ? m : "";

            log.info("智能刷新完成: fixed={}, created={}, updated={}, message={}",
                    fixed, filesCreated, filesUpdated, message);

            return ResponseEntity.ok(Result.success(responseBody));

        } catch (Exception e) {
            log.error("智能刷新失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("智能刷新失败: " + e.getMessage()));
        }
    }

    /**
     * 处理运行时错误修复
     */
    private ResponseEntity<?> handleRuntimeErrorFix(Map<String, Object> request) {
        String sandboxId = (String) request.get("sandboxId");
        Object errorLog = request.get("errorLog");
        String modelName = (String) request.getOrDefault("model", "deepseek-v3");
        String userRequest = safeToString(request.get("userRequest"));
        Map<String, String> existingFiles = extractExistingFilesFromRequest(request);

        log.info("正在分析运行时错误: sandboxId={}, model={}", sandboxId, modelName);

        // 1. 构建 Prompt
        String errorLogText = formatErrorLog(errorLog);
        OpenLovableRepairIntentResult intentResult = resolveIntentRouter().classify(userRequest, errorLogText);
        OpenLovableRepairMemory memory = repairMemoryService != null && sandboxId != null
                ? repairMemoryService.loadMemory(sandboxId).orElse(null)
                : null;
        String memorySummary = memory != null ? memory.summary() : "";
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert React/Frontend Coach.\n");
        prompt.append("The user's application failed with the following runtime or compile error:\n\n");
        prompt.append("```text\n").append(errorLogText).append("\n```\n\n");
        if (userRequest != null && !userRequest.isBlank()) {
            prompt.append("User Request:\n").append(userRequest).append("\n\n");
        } else {
            prompt.append("User Request:\nPlease fix the error and keep existing features unchanged.\n\n");
        }
        prompt.append("Please analyze the error and provide a minimal fix.\n");

        // 尝试提取相关文件内容
        String relatedFile = extractFileFromError(errorLogText);
        String normalizedRelatedFile = relatedFile != null ? normalizeSandboxFilePath(relatedFile) : null;
        String fileContent = null;
        if (normalizedRelatedFile != null && !existingFiles.isEmpty()) {
            fileContent = existingFiles.get(normalizedRelatedFile);
        }
        if (fileContent == null && normalizedRelatedFile != null && sandboxId != null) {
            log.info("尝试读取沙箱文件以辅助修复: {}", normalizedRelatedFile);
            fileContent = readSandboxTextFile(sandboxId, normalizedRelatedFile);
        }
        if (fileContent == null && normalizedRelatedFile != null) {
            // 构造沙箱URL (简单推断，实际应用中可能需要从数据库查询或由前端传递)
            // 尝试从 request 中获取 url，如果没有则尝试构造
            String sandboxUrl = (String) request.get("url");
            if (sandboxUrl == null) {
                // 尝试通过 status 接口查询 (会比较慢，且为了一个 url 调两次有点重)
                // V2.6 优化：前端 smart-refresh 应该传 url
            }

            if (sandboxUrl != null && !sandboxUrl.isBlank()) {
                fileContent = fetchSandboxFileContent(sandboxUrl, normalizedRelatedFile);
            }
        }
        List<String> fileManifest = buildFileManifest(existingFiles, sandboxId);
        String contextPack = resolveContextBuilder().buildContext(new OpenLovableRepairContextInput(
                memorySummary,
                intentResult,
                userRequest,
                trimToMax(errorLogText, 800),
                normalizedRelatedFile,
                fileContent,
                fileManifest));
        if (!contextPack.isBlank()) {
            prompt.append("\n").append(contextPack).append("\n\n");
        }

        prompt.append("Return the fixed code in the following XML format (if you modify the file):\n");
        prompt.append("<file path=\"src/PathTo/File.jsx\">\n... code ...\n</file>\n\n");
        prompt.append("Only return the files that need to be changed/created. Do not provide explanations.");

        // 2. 调用 LLM
        try {
            dev.langchain4j.model.chat.ChatLanguageModel chatModel = modelFactory.chatModel("deepseek", modelName);
            String response = chatModel.generate(prompt.toString());

            // 3. 应用修复
            Map<String, Object> applyRequest = new HashMap<>();
            applyRequest.put("sandboxId", sandboxId);
            applyRequest.put("response", response);
            applyRequest.put("mergeMode", "patch");
            if (!existingFiles.isEmpty()) {
                applyRequest.put("existingFiles", existingFiles);
            }

            ResponseEntity<?> applyResponse = applyCode(applyRequest);
            String outcome = applyResponse.getStatusCode().is2xxSuccessful() ? "success" : "failed";
            updateRepairMemory(sandboxId, intentResult, userRequest, errorLogText, normalizedRelatedFile,
                    fileManifest, outcome);
            return applyResponse;

        } catch (Exception e) {
            log.error("智能修复失败", e);
            updateRepairMemory(sandboxId, intentResult, userRequest, errorLogText, normalizedRelatedFile,
                    fileManifest, "failed:" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("智能修复失败: " + e.getMessage()));
        }
    }

    /**
     * 从错误日志中提取文件名
     * 
     * 策略：
     * 1. 优先查找 stack trace 中的文件路径 (如 http://.../src/App.jsx:10:5)
     * 2. 查找 message 中的组件名 (如 "Gift is not defined" -> 盲猜 src/Gift.jsx 还是引用方? 通常引用方
     * App.jsx 概率大)
     */
    private String extractFileFromError(String errorLog) {
        if (errorLog == null || errorLog.isBlank())
            return null;

        try {
            String logStr = errorLog;

            // 匹配 src/xxx.jsx 或 src/xxx.tsx
            // 浏览器堆栈通常是: at App (http://localhost:5173/src/App.jsx?t=123:25:9)
            Pattern p = Pattern.compile("(?:/home/user/app/)?(src/[a-zA-Z0-9_/\\-\\.]+\\.(?:jsx|tsx|js|ts|css|scss|less))");
            Matcher m = p.matcher(logStr);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            log.warn("提取文件名失败", e);
        }

        // 默认兜底：大多数预览错误发生在 App.jsx 或 main.jsx
        return "src/App.jsx";
    }

    // ==================== V3.0 自动续生成支持 ====================

    /**
     * 自动续生成结果
     *
     * 是什么：封装自动续生成的执行结果。
     * 做什么：记录续生成是否成功、完整代码响应、错误信息。
     * 为什么：支持大型企业级应用的完整代码生成（30分钟超时）。
     */
    private record AutoContinueResult(boolean success, String completeResponse, String errorMessage) {
    }

    /**
     * 尝试自动续生成截断的文件
     *
     * 是什么：当检测到代码截断时，自动向 OpenLovable 发送续生成请求。
     * 做什么：
     *   1. 构建续生成提示词，包含截断文件列表和已生成的部分代码
     *   2. 循环调用 OpenLovable 直到所有文件完整或超时
     *   3. 合并原始响应和续生成响应
     * 为什么：支持大型企业级应用（30分钟超时），避免因代码量大而直接失败。
     *
     * @param sandboxId 沙箱ID
     * @param currentResponse 当前已生成的代码响应（包含截断文件）
     * @param truncatedPaths 截断文件路径列表
     * @param originalPrompt 原始生成提示词（可选，用于上下文）
     * @param maxTimeoutSeconds 最大超时时间（秒）
     * @return 续生成结果
     */
    private AutoContinueResult attemptAutoContinueGeneration(
            String sandboxId,
            String currentResponse,
            java.util.List<String> truncatedPaths,
            String originalPrompt,
            int maxTimeoutSeconds) {

        long startTime = System.currentTimeMillis();
        long maxEndTime = startTime + (maxTimeoutSeconds * 1000L);
        int maxRetries = 10; // 最多续生成10轮
        int currentRetry = 0;

        String accumulatedResponse = currentResponse;
        java.util.List<String> remainingTruncatedPaths = new java.util.ArrayList<>(truncatedPaths);

        log.info("开始自动续生成: sandboxId={}, truncatedFiles={}, maxTimeout={}s",
                sandboxId, truncatedPaths.size(), maxTimeoutSeconds);

        while (!remainingTruncatedPaths.isEmpty() && currentRetry < maxRetries) {
            // 检查超时
            if (System.currentTimeMillis() > maxEndTime) {
                log.warn("自动续生成超时: 已用时{}秒，仍有{}个文件未完成",
                        (System.currentTimeMillis() - startTime) / 1000,
                        remainingTruncatedPaths.size());
                return new AutoContinueResult(false, accumulatedResponse,
                        "续生成超时，仍有 " + remainingTruncatedPaths.size() + " 个文件未完成: " +
                                String.join(", ", remainingTruncatedPaths));
            }

            currentRetry++;
            log.info("续生成第 {} 轮: 待完成文件 {}", currentRetry, remainingTruncatedPaths);

            // 构建续生成提示词
            String continuePrompt = buildContinueGenerationPrompt(
                    accumulatedResponse,
                    remainingTruncatedPaths,
                    originalPrompt);

            // 调用 OpenLovable 续生成
            try {
                String continueResponse = triggerContinueGeneration(sandboxId, continuePrompt);

                if (continueResponse == null || continueResponse.isBlank()) {
                    log.warn("续生成返回空响应，跳过本轮");
                    continue;
                }

                // 合并响应：将续生成的文件内容合并到累积响应中
                accumulatedResponse = mergeGenerationResponses(accumulatedResponse, continueResponse);

                // 重新检查截断文件
                OpenLovableResponseSanitizer.SanitizeResult newSanitizeResult =
                        OpenLovableResponseSanitizer.sanitizeForSandboxApply(accumulatedResponse);

                if (newSanitizeResult.truncatedPaths() == null || newSanitizeResult.truncatedPaths().isEmpty()) {
                    log.info("续生成成功: 所有文件已完整，共 {} 轮", currentRetry);
                    return new AutoContinueResult(true, newSanitizeResult.sanitizedResponse(), null);
                }

                remainingTruncatedPaths = new java.util.ArrayList<>(newSanitizeResult.truncatedPaths());
                log.info("续生成进度: 仍有 {} 个文件截断", remainingTruncatedPaths.size());

            } catch (Exception e) {
                log.error("续生成第 {} 轮失败: {}", currentRetry, e.getMessage());
                // 继续尝试下一轮
            }
        }

        if (remainingTruncatedPaths.isEmpty()) {
            return new AutoContinueResult(true, accumulatedResponse, null);
        }

        return new AutoContinueResult(false, accumulatedResponse,
                "达到最大重试次数(" + maxRetries + ")，仍有 " + remainingTruncatedPaths.size() + " 个文件未完成");
    }

    /**
     * 构建续生成提示词
     *
     * 是什么：构建一个专门用于续生成截断文件的提示词。
     * 做什么：包含已生成的文件上下文和需要续写的文件列表。
     * 为什么：让 AI 理解上下文并正确续写截断的文件。
     */
    private String buildContinueGenerationPrompt(
            String currentResponse,
            java.util.List<String> truncatedPaths,
            String originalPrompt) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("## 续生成任务\n\n");
        prompt.append("之前的代码生成未完成，以下文件被截断：\n");
        for (String path : truncatedPaths) {
            prompt.append("- `").append(path).append("`\n");
        }
        prompt.append("\n");

        prompt.append("请继续完成这些截断的文件。要求：\n");
        prompt.append("1. **仅输出**截断文件的**完整内容**，使用 `<file path=\"...\">...</file>` 格式\n");
        prompt.append("2. 确保代码完整、可运行，所有括号和标签正确闭合\n");
        prompt.append("3. 保持与已生成代码的风格和结构一致\n");
        prompt.append("4. 不要重复输出已完整的文件\n\n");

        // 提取截断文件的已有内容作为上下文
        prompt.append("### 截断文件的当前内容（需要续写）\n\n");
        for (String path : truncatedPaths) {
            String partialContent = extractFileContent(currentResponse, path);
            if (partialContent != null && !partialContent.isBlank()) {
                prompt.append("**").append(path).append("** (截断):\n");
                prompt.append("```\n");
                // 只取最后 500 字符作为上下文
                if (partialContent.length() > 500) {
                    prompt.append("... [前面内容省略] ...\n");
                    prompt.append(partialContent.substring(partialContent.length() - 500));
                } else {
                    prompt.append(partialContent);
                }
                prompt.append("\n```\n\n");
            }
        }

        // 如果有原始提示词，添加简要上下文
        if (originalPrompt != null && !originalPrompt.isBlank()) {
            prompt.append("### 原始需求（参考）\n");
            String truncatedOriginal = originalPrompt.length() > 300
                    ? originalPrompt.substring(0, 300) + "..."
                    : originalPrompt;
            prompt.append(truncatedOriginal).append("\n\n");
        }

        return prompt.toString();
    }

    /**
     * 从响应中提取指定文件的内容
     */
    private String extractFileContent(String response, String filePath) {
        if (response == null || filePath == null) {
            return null;
        }

        // 匹配 <file path="...">...</file>
        String escapedPath = Pattern.quote(filePath);
        Pattern pattern = Pattern.compile(
                "<file\\s+path=['\"]" + escapedPath + "['\"][^>]*>([\\s\\S]*?)</file>",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 触发续生成请求
     *
     * 是什么：向 OpenLovable 发送续生成请求。
     * 做什么：使用 isEdit=true 模式，让 AI 基于现有代码继续生成。
     * 为什么：利用 OpenLovable 的编辑模式，让 AI 理解上下文。
     */
    private String triggerContinueGeneration(String sandboxId, String continuePrompt) {
        String url = resolveOpenLovableBaseUrlBySandboxId(sandboxId) + "/api/generate-ai-code-stream";
        log.info("[续生成] 触发: sandboxId={}, promptLength={}", sandboxId, continuePrompt.length());

        try {
            Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("prompt", continuePrompt);
            requestBody.put("model", "gemini-3-pro-preview"); // 使用同样的模型
            requestBody.put("isEdit", true); // 使用编辑模式

            Map<String, Object> context = new java.util.HashMap<>();
            context.put("sandboxId", sandboxId);
            requestBody.put("context", context);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON));
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            // 同步调用，等待生成完成
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("[续生成] 返回空响应");
                return null;
            }

            // 从 SSE 响应中提取 generatedCode
            String generatedCode = extractGeneratedCodeFromSse(body);
            if (generatedCode != null && generatedCode.contains("<file")) {
                log.info("[续生成] 成功提取代码，长度: {}", generatedCode.length());
                return generatedCode;
            }

            log.warn("[续生成] 响应不包含有效代码");
            return null;

        } catch (Exception e) {
            log.error("[续生成] 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 格式化错误日志文本。
     *
     * 是什么：将错误对象转为可读字符串。
     * 做什么：优先保留字符串输入，其他对象序列化为JSON。
     * 为什么：让修复提示更清晰并提升 LLM 解析稳定性。
     */
    private String formatErrorLog(Object errorLog) {
        if (errorLog == null) {
            return "未知错误";
        }
        if (errorLog instanceof String text) {
            return text;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(errorLog);
        } catch (Exception e) {
            return String.valueOf(errorLog);
        }
    }

    /**
     * 从 SSE 响应中提取 generatedCode
     */
    private String extractGeneratedCodeFromSse(String sseResponse) {
        if (sseResponse == null) {
            return null;
        }

        // 查找 complete 事件中的 generatedCode
        try {
            // SSE 格式: data: {"type":"complete","generatedCode":"..."}
            String[] lines = sseResponse.split("\n");
            for (String line : lines) {
                if (line.startsWith("data: ") && line.contains("\"type\":\"complete\"")) {
                    String jsonStr = line.substring(6).trim();
                    com.fasterxml.jackson.databind.ObjectMapper mapper =
                            new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<String, Object> eventData = mapper.readValue(jsonStr, Map.class);
                    Object codeObj = eventData.get("generatedCode");
                    if (codeObj instanceof String code && !code.isBlank()) {
                        return code;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 SSE 响应失败: {}", e.getMessage());
        }

        // 尝试直接匹配 <file> 标签
        if (sseResponse.contains("<file")) {
            Pattern pattern = Pattern.compile("<file[\\s\\S]*</file>", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(sseResponse);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                sb.append(matcher.group()).append("\n\n");
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }

        return null;
    }


    /**
     * 获取沙箱操作锁
     *
     * 是什么：
     * - 对单个 sandbox 的写操作锁获取封装。
     *
     * 做什么：
     * - 优先使用锁服务；未启用时返回一个“可用的空租约”。
     *
     * 为什么：
     * - 允许在未接入锁组件时保持向后兼容，同时为多活场景提供并发保护。
     */
    private OpenLovableSandboxLockService.SandboxLockLease acquireSandboxOperationLock(String sandboxId, String operation) {
        if (sandboxLockService == null) {
            return OpenLovableSandboxLockService.SandboxLockLease.success(
                    sandboxId,
                    operation,
                    0L,
                    0L,
                    null,
                    null);
        }
        return sandboxLockService.tryAcquire(sandboxId, operation);
    }

    /**
     * 释放沙箱操作锁
     */
    private void releaseSandboxLock(OpenLovableSandboxLockService.SandboxLockLease lockLease) {
        if (sandboxLockService == null || lockLease == null) {
            return;
        }
        sandboxLockService.release(lockLease);
    }

    /**
     * 注册沙箱为 READY
     */
    private void registerSandboxReady(String sandboxId, String baseUrl, String userId, String tenantId) {
        if (sandboxRegistry == null || sandboxId == null || sandboxId.isBlank()) {
            return;
        }
        try {
            sandboxRegistry.registerReady(sandboxId, baseUrl, userId, tenantId);
        } catch (Exception e) {
            log.debug("注册沙箱状态失败: sandboxId={}, err={}", sandboxId, e.getMessage());
        }
    }

    /**
     * 续期沙箱心跳
     */
    private void renewSandboxHeartbeat(String sandboxId) {
        if (sandboxRegistry == null || sandboxId == null || sandboxId.isBlank()) {
            return;
        }
        try {
            sandboxRegistry.renewHeartbeat(sandboxId);
        } catch (Exception e) {
            log.debug("续期沙箱心跳失败: sandboxId={}, err={}", sandboxId, e.getMessage());
        }
    }

    /**
     * 标记沙箱 BUSY
     */
    private boolean markSandboxBusy(
            String sandboxId,
            OpenLovableSandboxLockService.SandboxLockLease lockLease,
            String operation,
            String baseUrl) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return false;
        }
        if (sandboxRegistry == null) {
            return true;
        }

        registerSandboxReady(sandboxId, baseUrl, null, null);
        long token = lockLease != null ? lockLease.fencingToken() : 0L;
        try {
            return sandboxRegistry.markBusy(sandboxId, token, operation);
        } catch (Exception e) {
            log.debug("标记沙箱BUSY失败: sandboxId={}, err={}", sandboxId, e.getMessage());
            return false;
        }
    }

    /**
     * 标记沙箱 READY
     */
    private void markSandboxReady(
            String sandboxId,
            OpenLovableSandboxLockService.SandboxLockLease lockLease,
            String baseUrl) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return;
        }
        if (sandboxRegistry == null) {
            registerSandboxReady(sandboxId, baseUrl, null, null);
            return;
        }

        long token = lockLease != null ? lockLease.fencingToken() : 0L;
        try {
            if (!sandboxRegistry.markReady(sandboxId, token)) {
                registerSandboxReady(sandboxId, baseUrl, null, null);
            }
            renewSandboxHeartbeat(sandboxId);
        } catch (Exception e) {
            log.debug("标记沙箱READY失败: sandboxId={}, err={}", sandboxId, e.getMessage());
        }
    }

    /**
     * 标记沙箱 TERMINATING
     */
    private boolean markSandboxTerminating(
            String sandboxId,
            OpenLovableSandboxLockService.SandboxLockLease lockLease,
            String operation,
            String baseUrl) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return false;
        }
        if (sandboxRegistry == null) {
            return true;
        }

        registerSandboxReady(sandboxId, baseUrl, null, null);
        long token = lockLease != null ? lockLease.fencingToken() : 0L;
        try {
            return sandboxRegistry.markTerminating(sandboxId, token, operation);
        } catch (Exception e) {
            log.debug("标记沙箱TERMINATING失败: sandboxId={}, err={}", sandboxId, e.getMessage());
            return false;
        }
    }

    /**
     * 标记沙箱 TERMINATED
     */
    private void markSandboxTerminated(
            String sandboxId,
            OpenLovableSandboxLockService.SandboxLockLease lockLease) {
        if (sandboxRegistry == null || sandboxId == null || sandboxId.isBlank()) {
            return;
        }
        long token = lockLease != null ? lockLease.fencingToken() : 0L;
        try {
            sandboxRegistry.markTerminated(sandboxId, token);
        } catch (Exception e) {
            log.debug("标记沙箱TERMINATED失败: sandboxId={}, err={}", sandboxId, e.getMessage());
        }
    }

    /**
     * 清理沙箱路由与状态
     */
    private void clearSandboxRoutingAndRegistry(String sandboxId) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return;
        }
        if (endpointRouter != null) {
            endpointRouter.unbindSandbox(sandboxId);
        }
        if (sandboxRegistry != null) {
            sandboxRegistry.remove(sandboxId);
        }
    }


    /**
     * 从请求体中提取 sandboxId
     *
     * 支持两种结构：
     * 1) 顶层 `sandboxId`
     * 2) `context.sandboxId`
     */
    private String extractSandboxIdFromRequestPayload(Map<String, ?> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }

        Object sandboxIdObj = payload.get("sandboxId");
        if (sandboxIdObj instanceof String sid && !sid.isBlank()) {
            return sid.trim();
        }

        Object contextObj = payload.get("context");
        if (contextObj instanceof Map<?, ?> contextMap) {
            Object contextSandboxIdObj = contextMap.get("sandboxId");
            if (contextSandboxIdObj instanceof String sid && !sid.isBlank()) {
                return sid.trim();
            }
        }

        return null;
    }

    /**
     * 解析指定 sandboxId 对应的 OpenLovable baseUrl
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
     * 解析请求应路由到的 OpenLovable baseUrl
     *
     * 说明：
     * - 有 sandboxId 时走固定路由；
     * - 无 sandboxId 或 `pending` 时按创建策略选择实例（轮询）。
     */
    private String resolveOpenLovableBaseUrlForRequest(String sandboxId) {
        if (sandboxId == null || sandboxId.isBlank() || "pending".equalsIgnoreCase(sandboxId.trim())) {
            return selectOpenLovableBaseUrlForCreate();
        }
        return resolveOpenLovableBaseUrlBySandboxId(sandboxId.trim());
    }

    /**
     * 选择“新建沙箱”使用的 OpenLovable 实例
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
     * 从 SSE data 行提取 sandboxId
     */
    private String extractSandboxIdFromSseDataLine(String line) {
        return extractSseStringField(line, "sandboxId");
    }

    /**
     * 从 SSE data 行提取指定字符串字段
     */
    private String extractSseStringField(String line, String fieldName) {
        if (line == null || fieldName == null || !line.startsWith("data:")) {
            return null;
        }

        String jsonStr = line.substring(5).trim();
        if (!jsonStr.startsWith("{")) {
            return null;
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> eventData = mapper.readValue(jsonStr, Map.class);
            Object valueObj = eventData.get(fieldName);
            if (valueObj instanceof String value && !value.isBlank()) {
                return value.trim();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 规范化 OpenLovable baseUrl（去除末尾斜杠）
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
     * 合并生成响应
     *
     * 是什么：将续生成的代码合并到原始响应中。
     * 做什么：
     *   1. 如果续生成包含同名文件，用续生成的版本替换原始版本
     *   2. 如果续生成包含新文件，追加到原始响应
     * 为什么：确保最终响应包含所有完整的文件。
     */
    private String mergeGenerationResponses(String originalResponse, String continueResponse) {
        if (continueResponse == null || continueResponse.isBlank()) {
            return originalResponse;
        }

        // 提取续生成响应中的所有文件
        java.util.List<OpenLovableResponseSanitizer.FileBlock> continueBlocks =
                OpenLovableResponseSanitizer.extractFileBlocks(continueResponse);

        if (continueBlocks.isEmpty()) {
            return originalResponse;
        }

        // 提取原始响应中的所有文件
        java.util.List<OpenLovableResponseSanitizer.FileBlock> originalBlocks =
                OpenLovableResponseSanitizer.extractFileBlocks(originalResponse);

        // 构建文件路径到内容的映射（使用续生成的版本覆盖原始版本）
        java.util.Map<String, String> fileMap = new java.util.LinkedHashMap<>();
        for (OpenLovableResponseSanitizer.FileBlock block : originalBlocks) {
            fileMap.put(block.normalizedPath(), block.content());
        }
        for (OpenLovableResponseSanitizer.FileBlock block : continueBlocks) {
            fileMap.put(block.normalizedPath(), block.content());
            log.debug("合并文件: {} ({}字符)", block.normalizedPath(),
                    block.content() != null ? block.content().length() : 0);
        }

        // 重新构建响应
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> entry : fileMap.entrySet()) {
            sb.append("<file path=\"").append(entry.getKey()).append("\">\n");
            sb.append(entry.getValue()).append("\n");
            sb.append("</file>\n\n");
        }

        log.info("合并完成: 原始{}文件 + 续生成{}文件 = 最终{}文件",
                originalBlocks.size(), continueBlocks.size(), fileMap.size());

        return sb.toString();
    }
}
