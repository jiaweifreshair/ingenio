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

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 创建AI沙箱
     *
     * POST /v1/openlovable/sandbox/create
     *
     * 响应示例：
     * {
     *   "success": true,
     *   "sandboxId": "sb_xxxxx",
     *   "url": "https://xxxxx.vercel.app",
     *   "provider": "vercel",
     *   "message": "Sandbox created and Vite React app initialized"
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
     *   "userMessage": "创建一个待办事项应用",
     *   "model": "deepseek-v3.2"
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

            // 1.5 处理 Scout 模版上下文 (Phase 7 Integration)
            if (adaptedRequest.containsKey("templateContext")) {
                String templateContext = (String) adaptedRequest.remove("templateContext");
                if (originalPrompt != null && !templateContext.isBlank()) {
                    originalPrompt = originalPrompt + "\n\n" + templateContext;
                    log.info("已注入 Scout 模版上下文");
                }
            }

            // 2. 【方案A核心】增强提示词 - 添加结构化思维要求
            if (originalPrompt != null && !originalPrompt.isEmpty()) {
                String enhancedPrompt = enhancePromptWithStructuredThinking(originalPrompt);
                adaptedRequest.put("prompt", enhancedPrompt);
                log.info("提示词增强: 原长度={}, 增强后长度={}", originalPrompt.length(), enhancedPrompt.length());
            }

            // 2.1 默认模型：优先走 Gemini 3 Pro（OpenLovable-CN 的 gemini- 前缀模型）
            // 若前端未指定 model，这里不强制设置默认值，交由 OpenLovable-CN 依据自身配置决定（避免代理层默认值与上游可用模型不一致）。
            Object modelObj = adaptedRequest.get("model");
            boolean hasValidModel = modelObj instanceof String && !((String) modelObj).isBlank();
            if (!hasValidModel) {
                adaptedRequest.remove("model");
                log.debug("参数适配: 未指定model，交由 OpenLovable-CN 选择默认模型");
            }

            // 3. 将sandboxId包装到context对象中
            if (adaptedRequest.containsKey("sandboxId")) {
                Object sandboxIdObj = adaptedRequest.remove("sandboxId");
                if (sandboxIdObj instanceof String sandboxId && !sandboxId.isBlank() && !"pending".equalsIgnoreCase(sandboxId)) {
                    Map<String, Object> context = new HashMap<>();
                    context.put("sandboxId", sandboxId);
                    adaptedRequest.put("context", context);
                    log.debug("参数适配: sandboxId -> context.sandboxId ({})", sandboxId);
                } else {
                    log.debug("参数适配: sandboxId为空或pending，已跳过向上游传递（避免误用占位ID）");
                }
            }

            log.debug("适配后请求体: {}", adaptedRequest);

            StreamingResponseBody stream = outputStream -> {
                try {
                    // 第一次转发：按原始请求走上游
                    ForwardSseResult first = forwardGenerateSse(url, adaptedRequest, outputStream, true);

                    // 🔧 修复：上游偶发返回 complete 但 generatedCode 为空，且没有任何 stream/content 增量
                    // 这种情况下前端会判定“AI生成的代码为空”。这里在代理层做一次自动重试（模型回退），避免用户手动重试。
                    if (!first.hasAnyCode()) {
                        String notice = "data: {\"type\":\"status\",\"message\":\"⚠️ 上游返回空代码，正在自动重试（模型回退: deepseek-r1）...\"}\n\n";
                        outputStream.write(notice.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();

                        Map<String, Object> retryRequest = new HashMap<>(adaptedRequest);
                        retryRequest.put("model", "deepseek-r1");

                        ForwardSseResult second = forwardGenerateSse(url, retryRequest, outputStream, false);
                        if (!second.hasAnyCode()) {
                            log.warn("OpenLovable 两次生成均返回空代码，请检查上游模型/密钥配置或 OpenLovable 日志");
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
     *   "sandboxId": "sb_xxxxx",
     *   "status": "running",
     *   "url": "https://xxxxx.vercel.app"
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
     *   "success": true,
     *   "message": "Sandbox terminated"
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
     *   "sandboxId": "sb_xxxxx"
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
     *   "sandboxId": "sb_xxxxx"
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
                    headers
            );

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
     *   "status": "ok",
     *   "service": "open-lovable",
     *   "baseUrl": "http://localhost:3001"
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
                    "upstream", response.getBody()
            )));

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
     *   "sandboxId": "idk3msrrff9vnoboa9e34",
     *   "response": "<file path=\"src/App.jsx\">...</file>..."
     * }
     *
     * 响应示例：
     * {
     *   "success": true,
     *   "filesWritten": 5,
     *   "packagesInstalled": ["react-router-dom"],
     *   "message": "代码已成功应用到沙箱"
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
            OpenLovableResponseSanitizer.SanitizeResult sanitizeResult =
                    OpenLovableResponseSanitizer.sanitizeForSandboxApply(aiResponse);
            if (sanitizeResult.removedPaths() != null && !sanitizeResult.removedPaths().isEmpty()) {
                log.info("已过滤 {} 个高风险配置文件，防止破坏沙箱模板: {}",
                        sanitizeResult.removedPaths().size(),
                        sanitizeResult.removedPaths());
                aiResponse = sanitizeResult.sanitizedResponse();
            }

            // 基础校验：OpenLovable apply 依赖 <file path="...">...</file> 结构
            if (!aiResponse.contains("<file")) {
                log.warn("apply请求的AI代码不含<file>标签，无法应用到Sandbox");
                return ResponseEntity.badRequest()
                        .body(Result.error(400, "AI代码格式异常：缺少 <file path=\"...\"> 标签，无法应用到Sandbox"));
            }

            // 更新请求体
            request.put("response", aiResponse);

            // 从AI响应中解析文件数量（作为备用）
            int parsedFilesCount = countFilesInResponse(aiResponse);
            log.info("从AI响应中解析到 {} 个文件", parsedFilesCount);

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
            connection.setConnectTimeout(30000);   // 连接超时30秒
            connection.setReadTimeout(120000);     // 读取超时2分钟（120秒）- V2.0优化

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
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Result.error(502, "OpenLovable apply失败: " + upstreamStatus));
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
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

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
                                if (eventData.get("replacedSandboxId") instanceof String replaced && !replaced.isBlank()) {
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
                                String path = (filePath != null) ? filePath.toString() : (filePathAlt != null ? filePathAlt.toString() : "unknown");
                                String err = (errorMsg != null) ? errorMsg.toString() : (errorMsgAlt != null ? errorMsgAlt.toString() : "未知错误");
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
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Result.error(502, "OpenLovable apply失败: " + upstreamErrorMessage));
            }

            if (!receivedComplete) {
                log.error("OpenLovable apply 未返回 complete 事件，已拒绝返回假成功");
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Result.error(502, "OpenLovable apply未返回complete事件，请稍后重试"));
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
                    errorDetail.append("警告信息:\n");
                    for (String w : warnings) {
                        errorDetail.append("  - ").append(w).append("\n");
                    }
                    log.warn("OpenLovable apply warnings: {}", warnings);
                }
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Result.error(502, errorDetail.toString().trim()));
            }

            finalResult.put("filesWritten", filesWritten);

            log.info("代码应用成功: 写入{}个文件 (receivedComplete={})", filesWritten, receivedComplete);
            return ResponseEntity.ok(Result.success(finalResult));

        } catch (Exception e) {
            log.error("应用AI代码失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("应用代码失败: " + e.getMessage()));
        }
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
     *   "sandboxId": "imvbokfo0hay4na5cxqrq"  // 指定sandbox ID，确保重启正确的sandbox
     * }
     *
     * 响应示例：
     * {
     *   "success": true,
     *   "message": "Vite server restarted successfully"
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
        Pattern filePattern = Pattern.compile("(<file\\s+path=['\"]([^'\"]+)['\"][^>]*>)([\\s\\S]*?)(</file>)", Pattern.CASE_INSENSITIVE);
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
        Pattern reactImportPattern = Pattern.compile("^import\\s+[^;]*\\s+from\\s+['\"]react['\"];?\\s*$", Pattern.MULTILINE);
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
                updatedImportLine = importLine.replace("from 'react'", ", { " + String.join(", ", required) + " } from 'react'");
                updatedImportLine = updatedImportLine.replace("from \"react\"", ", { " + String.join(", ", required) + " } from \"react\"");
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
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<file\\s+path=['\"]([^'\"]+)['\"]", java.util.regex.Pattern.CASE_INSENSITIVE);
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
        Pattern mainJsxPattern = Pattern.compile("<file\\s+path=['\"]src/main\\.jsx['\"][^>]*>([\\s\\S]*?)</file>", Pattern.CASE_INSENSITIVE);
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
                    Matcher.quoteReplacement("<file path=\"src/main.jsx\">\n" + standardMainJsx + "\n</file>")
                );
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
        Pattern emptyFilePattern = Pattern.compile("<file\\s+path=['\"]([^'\"]+)['\"][^>]*>\\s*</file>", Pattern.CASE_INSENSITIVE);
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
            Pattern mainJsxPattern = Pattern.compile("<file\\s+path=['\"]src/main\\.jsx['\"][^>]*>([\\s\\S]*?)</file>", Pattern.CASE_INSENSITIVE);
            Matcher mainJsxMatcher = mainJsxPattern.matcher(generatedCode);

            if (mainJsxMatcher.find()) {
                String content = mainJsxMatcher.group(1);
                if (content == null || content.trim().isEmpty()) {
                    // main.jsx为空，替换为完整内容
                    String standardMainJsx = generateStandardMainJsx();
                    String fixedCode = mainJsxMatcher.replaceFirst(
                        Matcher.quoteReplacement("<file path=\"src/main.jsx\">\n" + standardMainJsx + "\n</file>")
                    );
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

        private ForwardSseResult(boolean hasDelta, boolean hasCompleteCode) {
            this.hasDelta = hasDelta;
            this.hasCompleteCode = hasCompleteCode;
        }

        /** 是否已拿到任何可用于 apply 的代码输出。 */
        private boolean hasAnyCode() {
            return hasDelta || hasCompleteCode;
        }
    }

    /**
     * 转发 OpenLovable 的 generate SSE，并在转发过程中统计是否出现“可部署代码”。
     *
     * 统计规则：
     * - 只要出现过 type=stream/type=content 的增量事件，即认为上游输出了代码（hasDelta=true）。
     * - 若 type=complete 的 generatedCode 非空且包含 <file 标签，则认为上游输出了最终代码（hasCompleteCode=true）。
     *
     * @param url OpenLovable generate SSE 上游地址
     * @param requestBody 适配后的请求体
     * @param outputStream 代理输出流（返回给前端的 SSE）
     * @param suppressEmptyComplete 是否在“无增量且 complete.generatedCode 为空”时抑制该 complete 事件（避免前端误判已完成）
     * @return 转发统计结果
     */
    private ForwardSseResult forwardGenerateSse(
            String url,
            Map<String, Object> requestBody,
            OutputStream outputStream,
            boolean suppressEmptyComplete
    ) throws IOException {
        boolean hasDelta = false;
        boolean hasCompleteCode = false;

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
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                boolean shouldForward = true;

                if (line.startsWith("data: ")) {
                    // 轻量统计：避免对每个 chunk 做 JSON 解析
                    if (line.contains("\"type\":\"stream\"") || line.contains("\"type\":\"content\"")) {
                        hasDelta = true;
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
                            if (generatedCode != null && !generatedCode.trim().isEmpty() && generatedCode.contains("<file")) {
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
        }

        log.info("AI代码生成流式响应完成");
        return new ForwardSseResult(hasDelta, hasCompleteCode);
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
    private String enhancePromptWithStructuredThinking(String originalPrompt) {
        return String.format("""
## 🎯 代码生成任务

### 用户需求
%s

---

## 🎨 UI设计规范（强制遵守）

### 1. 视觉风格
- **现代简约设计**：干净、留白充足、视觉层次清晰
- **配色方案**：使用专业的渐变色（如 from-indigo-500 to-purple-600），禁止使用单调的纯色背景
- **卡片设计**：使用 rounded-xl shadow-lg 圆角阴影，hover时添加 hover:shadow-xl transition-all
- **背景**：主背景使用 bg-gradient-to-br from-slate-50 to-slate-100，深色模式用 dark:from-slate-900 dark:to-slate-800

### 2. 排版规范
- **标题**：使用 text-2xl md:text-4xl font-bold，搭配渐变色 bg-gradient-to-r from-indigo-600 to-purple-600 bg-clip-text text-transparent
- **正文**：text-gray-600 dark:text-gray-300，行高 leading-relaxed
- **间距**：组件之间使用 space-y-6 或 gap-6，页面边距 px-4 md:px-8 py-8

### 3. 交互动效
- **按钮**：主按钮使用渐变色 bg-gradient-to-r from-indigo-500 to-purple-500 hover:from-indigo-600 hover:to-purple-600 text-white rounded-lg shadow-md hover:shadow-lg transition-all
- **卡片悬停**：hover:scale-[1.02] hover:shadow-xl transition-all duration-300
- **输入框**：focus:ring-2 focus:ring-indigo-500 focus:border-transparent rounded-lg border-gray-300

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
<section className="relative overflow-hidden bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 text-white py-20 md:py-32">
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
""", originalPrompt);
    }
}
