package com.ingenio.backend.controller;

import com.ingenio.backend.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
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
import java.util.Enumeration;
import java.util.HashMap;
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
    public ResponseEntity<?> createSandbox() {
        try {
            log.info("创建Open-Lovable沙箱: {}/api/create-ai-sandbox-v2", openLovableBaseUrl);

            String url = openLovableBaseUrl + "/api/create-ai-sandbox-v2";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);

            log.info("沙箱创建响应: status={}, body={}", response.getStatusCode(), response.getBody());
            return ResponseEntity.ok(Result.success(response.getBody()));

        } catch (Exception e) {
            log.error("创建沙箱失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("创建沙箱失败: " + e.getMessage()));
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
            String url = openLovableBaseUrl + "/api/generate-ai-code-stream";
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

            // 3. 将sandboxId包装到context对象中
            if (adaptedRequest.containsKey("sandboxId")) {
                Object sandboxIdObj = adaptedRequest.remove("sandboxId");
                if (sandboxIdObj instanceof String sandboxId && !sandboxId.isBlank()
                        && !"pending".equalsIgnoreCase(sandboxId)) {
                    Map<String, Object> context = new HashMap<>();
                    context.put("sandboxId", sandboxId);
                    adaptedRequest.put("context", context);
                    log.debug("参数适配: sandboxId -> context.sandboxId ({})", sandboxId);
                } else {
                    log.debug("参数适配: sandboxId为空或pending，已跳过向上游传递（避免误用占位ID）");
                }
            }

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

                        ForwardSseResult result = forwardGenerateSse(url, attemptRequest, outputStream,
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
                    .fromHttpUrl(openLovableBaseUrl + "/api/sandbox-status");
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
    public ResponseEntity<?> killSandbox() {
        try {
            String url = openLovableBaseUrl + "/api/kill-sandbox";
            log.info("终止沙箱: {}", url);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);

            return ResponseEntity.ok(Result.success(response.getBody()));

        } catch (Exception e) {
            log.error("终止沙箱失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("终止沙箱失败: " + e.getMessage()));
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
                    .fromHttpUrl(openLovableBaseUrl + "/api/sandbox-status")
                    .queryParam("sandboxId", sandboxId);
            String url = builder.toUriString();

            log.info("Sandbox心跳: sandboxId={}, url={}", sandboxId, url);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
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
        try {
            Object sandboxIdObj = request.get("sandboxId");
            if (!(sandboxIdObj instanceof String sandboxId) || sandboxId.isBlank()) {
                return ResponseEntity.badRequest().body(Result.error(400, "缺少必需参数: sandboxId"));
            }

            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(openLovableBaseUrl + "/api/kill-sandbox")
                    .queryParam("sandboxId", sandboxId);
            String url = builder.toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(
                    Map.of("sandboxId", sandboxId),
                    headers);

            log.info("清理Sandbox: sandboxId={}, url={}", sandboxId, url);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            return ResponseEntity.ok(Result.success(response.getBody()));
        } catch (Exception e) {
            log.error("清理Sandbox失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("清理Sandbox失败: " + e.getMessage()));
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
            String url = openLovableBaseUrl + "/api/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            return ResponseEntity.ok(Result.success(Map.of(
                    "status", "ok",
                    "service", "open-lovable",
                    "baseUrl", openLovableBaseUrl,
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
        try {
            log.info("开始应用AI代码到沙箱");
            log.debug("请求参数: {}", request);

            String url = openLovableBaseUrl + "/api/apply-ai-code-stream";

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
            if (sanitizeResult.truncatedPaths() != null && !sanitizeResult.truncatedPaths().isEmpty()) {
                log.error("检测到 {} 个截断文件，拒绝写入: {}",
                        sanitizeResult.truncatedPaths().size(),
                        sanitizeResult.truncatedPaths());
                return ResponseEntity.badRequest()
                        .body(Result.error(400, String.format(
                                "AI代码生成不完整：检测到 %d 个截断文件（%s）。请重新生成以获取完整代码。",
                                sanitizeResult.truncatedPaths().size(),
                                String.join(", ", sanitizeResult.truncatedPaths()))));
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
        String url = openLovableBaseUrl + "/api/apply-ai-code-stream";

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
                                }
                                if (urlObj instanceof String urlStr && !urlStr.isBlank()) {
                                    finalResult.put("sandboxUrl", urlStr);
                                    // 兼容前端通用字段
                                    finalResult.put("url", urlStr);
                                }
                                if (eventData.get("replacedSandboxId") instanceof String replaced
                                        && !replaced.isBlank()) {
                                    finalResult.put("replacedSandboxId", replaced);
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
        try {
            String url = openLovableBaseUrl + "/api/restart-vite";
            String sandboxId = (requestBody != null) ? requestBody.get("sandboxId") : null;
            log.info("重启Vite开发服务器: url={}, sandboxId={}", url, sandboxId);

            // 构建请求体，传递sandboxId给Open-Lovable
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            Map<String, String> body = new java.util.HashMap<>();
            if (sandboxId != null && !sandboxId.isEmpty()) {
                body.put("sandboxId", sandboxId);
            }

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            log.info("Vite重启响应: status={}, body={}", response.getStatusCode(), response.getBody());
            return ResponseEntity.ok(Result.success(response.getBody()));

        } catch (Exception e) {
            log.error("重启Vite服务器失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("重启Vite服务器失败: " + e.getMessage()));
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
     * 自动修复空文件或缺失的关键入口文件（main.jsx）
     *
     * 问题场景：
     * - AI截断恢复后可能生成空的 main.jsx
     * - 导致沙箱无法正常渲染应用
     *
     * 修复策略：
     * 1. 检测 main.jsx 是否存在且非空
     * 2. 如果为空或缺失，自动生成标准入口文件
     * 3. 确保导入的 App 组件路径正确
     *
     * @param response AI 原始输出
     * @return 修复后的输出
     */
    private String autoFixEmptyMainJsx(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }

        // 检查是否有 App.jsx 文件（确定是否需要修复main.jsx）
        boolean hasAppJsx = response.contains("path=\"src/App.jsx\"") || response.contains("path='src/App.jsx'");
        if (!hasAppJsx) {
            // 没有App.jsx，不需要修复main.jsx
            return response;
        }

        // 检查 main.jsx 是否存在且非空
        Pattern mainJsxPattern = Pattern.compile("<file\\s+path=['\"]src/main\\.jsx['\"][^>]*>([\\s\\S]*?)</file>",
                Pattern.CASE_INSENSITIVE);
        Matcher mainJsxMatcher = mainJsxPattern.matcher(response);

        boolean hasMainJsx = false;
        boolean mainJsxIsEmpty = true;

        if (mainJsxMatcher.find()) {
            hasMainJsx = true;
            String content = mainJsxMatcher.group(1);
            mainJsxIsEmpty = content == null || content.trim().isEmpty();
        }

        // 如果main.jsx不存在或为空，自动生成
        if (!hasMainJsx || mainJsxIsEmpty) {
            String standardMainJsx = generateStandardMainJsx();
            log.info("自动修复: main.jsx {} -> 生成标准入口文件", hasMainJsx ? "为空" : "缺失");

            if (hasMainJsx && mainJsxIsEmpty) {
                // 替换空的main.jsx
                response = mainJsxMatcher.replaceFirst(
                        Matcher.quoteReplacement("<file path=\"src/main.jsx\">\n" + standardMainJsx + "\n</file>"));
            } else {
                // 追加main.jsx
                // 在最后一个 </file> 后面追加
                int lastFileEndIndex = response.lastIndexOf("</file>");
                if (lastFileEndIndex != -1) {
                    String before = response.substring(0, lastFileEndIndex + 7); // 包含 </file>
                    String after = response.substring(lastFileEndIndex + 7);
                    response = before + "\n\n<file path=\"src/main.jsx\">\n" + standardMainJsx + "\n</file>" + after;
                }
            }
        }

        return response;
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
            // 保留main.jsx（由autoFixEmptyMainJsx处理）
            if (!"src/main.jsx".equals(filePath)) {
                log.warn("移除空文件: {}", filePath);
                emptyFileMatcher.appendReplacement(sb, "");
                removedCount++;
            }
        }

        emptyFileMatcher.appendTail(sb);

        if (removedCount > 0) {
            log.info("移除了 {} 个空文件", removedCount);
        }

        return sb.toString();
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

                if (line.startsWith("data: ")) {
                    // 轻量统计：避免对每个 chunk 做 JSON 解析
                    if (line.contains("\"type\":\"stream\"") || line.contains("\"type\":\"content\"")) {
                        hasDelta = true;
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
                            if (generatedCode != null && !generatedCode.trim().isEmpty()
                                    && generatedCode.contains("<file")) {
                                hasCompleteCode = true;
                            } else if (!hasDelta && suppressEmptyComplete) {
                                // 无增量且 complete 无有效代码：抑制该 complete，后续在外层触发自动重试
                                shouldForward = false;
                            }
                        } catch (Exception parseError) {
                            // 解析失败时不影响转发，但也不将其计为“有效完整代码”
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
        try {
            String url = openLovableBaseUrl + "/api/create-zip";
            log.info("创建项目ZIP包: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 传递 sandboxId（如果有）
            Map<String, Object> requestBody = request != null ? new HashMap<>(request) : new HashMap<>();

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            if (response.getBody() == null) {
                log.error("创建ZIP包响应为空");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Result.error("创建ZIP包失败: 响应为空"));
            }

            Map<String, Object> responseBody = response.getBody();

            // 检查上游响应是否成功
            Object successObj = responseBody.get("success");
            if (successObj instanceof Boolean success && !success) {
                String error = responseBody.get("error") instanceof String e ? e : "未知错误";
                log.error("创建ZIP包失败: {}", error);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Result.error("创建ZIP包失败: " + error));
            }

            log.info("ZIP包创建成功: fileName={}", responseBody.get("fileName"));
            return ResponseEntity.ok(Result.success(responseBody));

        } catch (Exception e) {
            log.error("创建ZIP包失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("创建ZIP包失败: " + e.getMessage()));
        }
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

            String url = openLovableBaseUrl + "/api/smart-refresh-preview";
            log.info("智能刷新预览（自动修复）: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 传递模型和其他配置
            Map<String, Object> requestBody = request != null ? new HashMap<>(request) : new HashMap<>();

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

        log.info("正在分析运行时错误: sandboxId={}, model={}", sandboxId, modelName);

        // 1. 构建 Prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert React/Frontend Coach.\n");
        prompt.append("The user's application crashed with the following Runtime Error in the browser:\n\n");
        prompt.append("```json\n").append(new com.google.gson.Gson().toJson(errorLog)).append("\n```\n\n");
        prompt.append("Please analyze the error and provide a fix.\n");

        // 尝试提取相关文件内容
        String relatedFile = extractFileFromError(errorLog);
        String fileContent = null;
        if (relatedFile != null && sandboxId != null) {
            // 构造沙箱URL (简单推断，实际应用中可能需要从数据库查询或由前端传递)
            // 尝试从 request 中获取 url，如果没有则尝试构造
            String sandboxUrl = (String) request.get("url");
            if (sandboxUrl == null) {
                // 尝试通过 status 接口查询 (会比较慢，且为了一个 url 调两次有点重)
                // V2.6 优化：前端 smart-refresh 应该传 url
            }

            if (sandboxUrl != null && !sandboxUrl.isBlank()) {
                log.info("尝试读取沙箱文件以辅助修复: {}", relatedFile);
                fileContent = fetchSandboxFileContent(sandboxUrl, relatedFile);

                if (fileContent != null) {
                    prompt.append("Here is the content of the file `").append(relatedFile)
                            .append("` where the error might have occurred:\n");
                    prompt.append("```javascript\n").append(fileContent).append("\n```\n\n");
                }
            }
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

            return applyCode(applyRequest);

        } catch (Exception e) {
            log.error("智能修复失败", e);
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
    private String extractFileFromError(Object errorLog) {
        if (errorLog == null)
            return null;

        try {
            // 将 errorLog 转为 String 方便正则
            String logStr = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(errorLog);

            // 匹配 src/xxx.jsx 或 src/xxx.tsx
            // 浏览器堆栈通常是: at App (http://localhost:5173/src/App.jsx?t=123:25:9)
            Pattern p = Pattern.compile("src/([a-zA-Z0-9_/\\-\\.]+\\.(?:jsx|tsx|js|ts))");
            Matcher m = p.matcher(logStr);
            if (m.find()) {
                return "src/" + m.group(1);
            }
        } catch (Exception e) {
            log.warn("提取文件名失败", e);
        }

        // 默认兜底：大多数预览错误发生在 App.jsx 或 main.jsx
        return "src/App.jsx";
    }
}
