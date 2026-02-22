package com.ingenio.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * ECA Gateway AI Provider（网宿/EdgeCloud AI 网关）
 *
 * 技术特点：
 * - 基于 OpenAI 兼容接口
 * - 支持 Gemini 3 Pro Preview 等多种模型
 * - Bearer Token 认证
 *
 * 参考实现：open-lovable-cn/lib/eca-gateway.ts
 * - 兼容多种鉴权头（Authorization Bearer）
 * - 统一解析常见错误码（401/403/404/429/5xx）
 * - 支持链路追踪（x-trace-id）
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class EcaGatewayAIProvider implements AIProvider {

    /**
     * 默认 Base URL（OpenAI 兼容网关）
     */
    private static final String DEFAULT_BASE_URL = "https://gaccodeapi.com/v1";

    /**
     * 默认模型
     */
    private static final String DEFAULT_MODEL = "gemini-3-pro-preview";

    /**
     * HTTP 客户端配置
     * - 连接超时：60秒
     * - 读取超时：300秒（5分钟，支持长时间推理）
     * - 写入超时：60秒
     */
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRIES = 3;

    /**
     * 请求体保留字段（extraParams不可覆盖）。
     *
     * 注意：top_p 不在此列表中，因为它需要根据 temperature 是否存在来动态处理。
     */
    private static final Set<String> RESERVED_REQUEST_KEYS = Set.of(
            "model",
            "messages",
            "stream",
            "temperature",
            "max_tokens",
            "stop",
            "reasoning_effort"
    );

    /**
     * 基础重试间隔（毫秒）
     */
    private static final long BASE_RETRY_DELAY_MS = 2000;

    /**
     * 最大重试间隔（毫秒）- 15秒
     */
    private static final long MAX_RETRY_DELAY_MS = 15_000;

    /**
     * ECA Gateway Base URL
     */
    @Value("${ingenio.ai.eca-gateway.base-url:" + DEFAULT_BASE_URL + "}")
    private String baseUrl;

    /**
     * ECA Gateway API Key
     * 兼容多种环境变量名
     */
    @Value("${ingenio.ai.eca-gateway.api-key:${AIGATEWAY_TOKEN:${ECA_GATEWAY_API_KEY:}}}")
    private String apiKey;

    /**
     * 默认模型
     */
    @Value("${ingenio.ai.eca-gateway.model:" + DEFAULT_MODEL + "}")
    private String defaultModel;

    /**
     * 是否启用
     */
    @Value("${ingenio.ai.eca-gateway.enabled:true}")
    private boolean enabled;

    private final ObjectMapper objectMapper;
    private final AIRateLimiter rateLimiter;

    public EcaGatewayAIProvider(ObjectMapper objectMapper, AIRateLimiter rateLimiter) {
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public String getProviderName() {
        return "eca-gateway";
    }

    @Override
    public String getProviderDisplayName() {
        return "ECA Gateway (Gemini)";
    }

    @Override
    public String getDefaultModel() {
        return defaultModel != null && !defaultModel.isBlank() ? defaultModel : DEFAULT_MODEL;
    }

    @Override
    public boolean isAvailable() {
        if (!enabled) {
            log.debug("ECA Gateway AI Provider 已禁用");
            return false;
        }

        boolean available = apiKey != null && !apiKey.isBlank();
        if (!available) {
            log.warn("ECA Gateway AI Provider 不可用：API Key 未配置");
            log.warn("请配置环境变量 AIGATEWAY_TOKEN 或 ingenio.ai.eca-gateway.api-key");
        }
        return available;
    }

    @Override
    public AIResponse generate(String prompt, AIRequest request) throws AIException {
        boolean hasMessages = request != null
                && request.messages() != null
                && !request.messages().isEmpty();
        if ((prompt == null || prompt.isBlank()) && !hasMessages) {
            throw new AIException("提示词或messages不能为空", getProviderName());
        }

        if (!isAvailable()) {
            throw new AIException("ECA Gateway AI Provider 不可用：API Key 未配置", getProviderName());
        }

        String targetModel = (request.model() != null && !request.model().isBlank())
                ? request.model()
                : getDefaultModel();

        return doGenerate(prompt, request, targetModel);
    }

    /**
     * 执行生成请求（带重试机制）
     */
    private AIResponse doGenerate(String prompt, AIRequest request, String targetModel) throws AIException {
        long startTime = System.currentTimeMillis();
        AIException lastException = null;
        String traceId = buildTraceId();

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            boolean acquired = false;
            try {
                log.debug("[ECA Gateway] 尝试获取许可... (attempt {}/{})", attempt, MAX_RETRIES);
                acquired = rateLimiter.acquire(300_000);

                if (!acquired) {
                    throw new AIException("获取速率限制许可超时", getProviderName());
                }

                // 1. 构建请求 Body
                String requestBody = buildRequestBody(prompt, request, targetModel);

                log.info("[ECA Gateway] 请求开始: model={}, attempt={}/{}, traceId={}",
                        targetModel, attempt, MAX_RETRIES, traceId);

                // 2. 执行 HTTP 请求
                String responseBody = executeHttpRequest(requestBody, traceId);

                // 3. 解析响应
                AIResponse response = parseResponse(responseBody, startTime);

                log.info("[ECA Gateway] 调用成功: model={}, duration={}ms, traceId={}",
                        response.model(), response.durationMs(), traceId);
                return response;

            } catch (AIException e) {
                lastException = e;
                boolean isRateLimit = e.getHttpStatus() == 429;
                boolean isRetryable = isRetryableError(e.getMessage()) || isRateLimit;

                if (isRetryable && attempt < MAX_RETRIES) {
                    long delay = calculateBackoff(attempt, isRateLimit);
                    log.warn("[ECA Gateway] 调用异常，{}ms后重试: {}, traceId={}", delay, e.getMessage(), traceId);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    continue;
                }
                throw e;
            } catch (Exception e) {
                lastException = new AIException("调用未知异常: " + e.getMessage(), getProviderName(), e);
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(BASE_RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw lastException;
                    }
                    continue;
                }
                throw lastException;
            } finally {
                if (acquired) {
                    rateLimiter.release();
                }
            }
        }
        // 保护性检查：确保 lastException 不为 null
        if (lastException == null) {
            lastException = new AIException("未知错误：重试耗尽", getProviderName());
        }
        throw lastException;
    }

    /**
     * 构建 OpenAI 兼容的请求 Body
     *
     * 参考 ECA Gateway 文档：
     * - model: 模型名称
     * - messages: 对话内容数组
     * - stream: 是否流式输出
     */
    private String buildRequestBody(String prompt, AIRequest request, String targetModel) throws AIException {
        try {
            Map<String, Object> requestBody = buildRequestBodyMap(prompt, request, targetModel);
            return objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            throw new AIException("构建请求 Body 失败: " + e.getMessage(), getProviderName(), e);
        }
    }

    /**
     * 构建请求体Map（单元测试可复用）。
     *
     * 是什么：生成ECA Gateway兼容的请求体Map。
     * 做什么：合并model/messages/采样参数/思考参数与扩展字段。
     * 为什么：支持网宿ECA的RAG/搜索等扩展参数。
     */
    static Map<String, Object> buildRequestBodyMap(String prompt, AIRequest request, String targetModel)
            throws AIException {
        Map<String, Object> requestBody = new LinkedHashMap<>();

        // 模型名称
        requestBody.put("model", targetModel);

        // 消息列表（OpenAI 格式）
        requestBody.put("messages", resolveMessages(prompt, request));

        // 生成参数
        if (request != null) {
            if (request.temperature() != null) {
                // ECA 网关禁止 temperature 与 top_p 同时传入，优先保留 temperature。
                requestBody.put("temperature", request.temperature());
            } else if (request.topP() != null) {
                requestBody.put("top_p", request.topP());
            }
            if (request.maxTokens() != null) {
                requestBody.put("max_tokens", request.maxTokens());
            }
            if (request.stopSequence() != null) {
                requestBody.put("stop", request.stopSequence());
            }
            if (request.reasoningEffort() != null && !request.reasoningEffort().isBlank()) {
                requestBody.put("reasoning_effort", request.reasoningEffort());
            }
            appendExtraParams(requestBody, request.extraParams());
        }

        // 非流式
        requestBody.put("stream", false);

        return requestBody;
    }

    /**
     * 合并扩展参数。
     *
     * 是什么：将extraParams写入请求体。
     * 做什么：过滤保留字段并忽略空值。
     * 为什么：避免扩展字段覆盖核心请求参数。
     */
    private static void appendExtraParams(Map<String, Object> requestBody, Map<String, Object> extraParams) {
        if (extraParams == null || extraParams.isEmpty()) {
            return;
        }

        // 检查是否已设置 temperature，如果是，则需要过滤掉 top_p
        boolean hasTemperature = requestBody.containsKey("temperature");

        extraParams.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            if (RESERVED_REQUEST_KEYS.contains(key)) {
                log.debug("[ECA Gateway] extraParams包含保留字段，已忽略: {}", key);
                return;
            }
            // 如果已设置 temperature，则忽略 top_p（ECA Gateway 不允许同时设置）
            if (hasTemperature && "top_p".equals(key)) {
                log.debug("[ECA Gateway] 已设置temperature，忽略extraParams中的top_p");
                return;
            }
            requestBody.put(key, value);
        });
    }

    /**
     * 解析请求消息列表。
     *
     * 是什么：将prompt或messages转换为OpenAI兼容的messages结构。
     * 做什么：优先使用AIRequest.messages，缺失时回退prompt。
     * 为什么：保持ECA Gateway与多模态请求的基本兼容性。
     */
    private static List<?> resolveMessages(String prompt, AIRequest request) throws AIException {
        if (request != null && request.messages() != null && !request.messages().isEmpty()) {
            return request.messages();
        }
        if (prompt == null || prompt.isBlank()) {
            throw new AIException("提示词或messages不能为空", "eca-gateway");
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));
        return messages;
    }

    /**
     * 执行 HTTP 请求
     *
     * 参考 eca-gateway.ts 实现：
     * - Authorization: Bearer ${AIGATEWAY_TOKEN}
     * - Content-Type: application/json
     * - x-trace-id: 链路追踪
     */
    private String executeHttpRequest(String requestBody, String traceId) throws AIException {
        try {
            // 构建请求 URL（ECA Gateway 不需要追加 /chat/completions）
            String url = normalizeUrl(baseUrl);

            Request httpRequest = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("x-trace-id", traceId)
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    log.error("[ECA Gateway] 调用失败: code={}, message={}, traceId={}, body={}",
                            response.code(), response.message(), traceId,
                            truncateText(responseBody, 500));

                    String errorMessage = formatErrorMessage(response.code(), responseBody);
                    throw new AIException(errorMessage, getProviderName(),
                            extractErrorCode(responseBody), response.code());
                }

                return responseBody;
            }

        } catch (AIException e) {
            throw e;
        } catch (IOException e) {
            throw new AIException("HTTP 请求失败: " + e.getMessage(), getProviderName(), e);
        }
    }

    /**
     * 解析 OpenAI 格式的响应
     */
    private AIResponse parseResponse(String responseBody, long startTime) throws AIException {
        try {
            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            // 提取生成的内容
            String content = jsonResponse
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

            if (content == null || content.isBlank()) {
                // 检查是否有 delta 格式（流式残留）
                content = jsonResponse
                        .path("choices")
                        .get(0)
                        .path("delta")
                        .path("content")
                        .asText();
            }

            if (content == null || content.isBlank()) {
                throw new AIException("响应内容为空", getProviderName());
            }

            // 提取模型名称
            String model = jsonResponse.path("model").asText(getDefaultModel());

            // 提取 token 统计信息
            JsonNode usage = jsonResponse.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            int totalTokens = usage.path("total_tokens").asInt(0);

            // 计算耗时
            long durationMs = System.currentTimeMillis() - startTime;

            return AIResponse.builder()
                    .content(content)
                    .model(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .durationMs(durationMs)
                    .provider(getProviderName())
                    .rawResponse(responseBody)
                    .build();

        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            throw new AIException("解析响应失败: " + e.getMessage(), getProviderName(), e);
        }
    }

    /**
     * 生成 Trace ID
     */
    private String buildTraceId() {
        return "eca_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 标准化 URL 并兼容 OpenAI Chat Completions 路径。
     *
     * 是什么：网关请求地址规范化方法。
     * 做什么：自动补齐 /v1/chat/completions，兼容 OpenAI 标准网关；同时保留 ECA /v1/{project}/{app} 直连地址。
     * 为什么：同一 Provider 需同时支持历史 ECA 网关和新的 OpenAPI 网关，避免因路径格式差异导致 404。
     */
    private String normalizeUrl(String url) {
        String normalized = (url == null || url.isBlank()) ? DEFAULT_BASE_URL : url;
        normalized = normalized.replaceAll("/+$", "");

        String lower = normalized.toLowerCase();
        if (lower.endsWith("/chat/completions")) {
            return normalized;
        }

        // OpenAI 标准 base-url（.../v1）
        if (lower.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }

        // 纯域名或非 v1 地址，按 OpenAI 兼容规范补齐 /v1/chat/completions
        if (!lower.contains("/v1/")) {
            return normalized + "/v1/chat/completions";
        }

        // 兼容历史 ECA 路径（.../v1/{project}/{app}）
        return normalized;
    }

    /**
     * 判断是否为可重试错误
     */
    private boolean isRetryableError(String msg) {
        if (msg == null)
            return false;
        String lower = msg.toLowerCase();
        return lower.contains("empty") || lower.contains("timeout") ||
                lower.contains("reset") || lower.contains("502") ||
                lower.contains("503") || lower.contains("504");
    }

    /**
     * 计算指数退避延迟
     */
    private long calculateBackoff(int attempt, boolean isRateLimit) {
        long baseDelay = isRateLimit ? BASE_RETRY_DELAY_MS : BASE_RETRY_DELAY_MS * attempt;
        long delay = (long) (baseDelay * Math.pow(2, attempt - 1));
        return Math.min(delay, MAX_RETRY_DELAY_MS);
    }

    /**
     * 格式化错误消息
     *
     * 参考 eca-gateway.ts 的 formatErrorMessage
     */
    private String formatErrorMessage(int status, String responseBody) {
        StringBuilder sb = new StringBuilder();
        sb.append("ECA 网关请求失败 (HTTP ").append(status).append(")");

        String message = parseErrorMessageFromBody(responseBody);
        if (message != null && !message.isBlank()) {
            sb.append(": ").append(message);
        }

        // 追加常见场景提示
        switch (status) {
            case 401 -> sb.append(" [可能原因：缺少 Authorization 请求头，或 token 错误/过期]");
            case 403 -> sb.append(" [可能原因：token 无权限/错误，或请求体格式不符合文档]");
            case 404 -> sb.append(" [请检查 ECA Gateway Base URL 是否正确]");
            case 429 -> sb.append(" [触发限流，请稍后重试或降低并发]");
            default -> {
                if (status >= 500) {
                    sb.append(" [网关服务异常，可稍后重试或切换模型]");
                }
            }
        }

        return sb.toString();
    }

    /**
     * 从错误响应中提取错误信息
     */
    private String parseErrorMessageFromBody(String responseBody) {
        try {
            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            // OpenAI 风格：{ error: { message: "..." } }
            String message = jsonResponse.path("error").path("message").asText();
            if (message != null && !message.isBlank()) {
                return truncateText(message, 200);
            }

            // 另一种风格：{ message: "..." }
            message = jsonResponse.path("message").asText();
            if (message != null && !message.isBlank()) {
                return truncateText(message, 200);
            }
        } catch (Exception e) {
            log.debug("无法解析错误信息: {}", e.getMessage());
        }
        return truncateText(responseBody, 200);
    }

    /**
     * 从错误响应中提取错误代码
     */
    private String extractErrorCode(String responseBody) {
        try {
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            String code = jsonResponse.path("error").path("code").asText();
            if (code != null && !code.isBlank()) {
                return code;
            }
        } catch (Exception e) {
            log.debug("无法提取错误代码: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 截断文本
     */
    private String truncateText(String text, int maxLength) {
        if (text == null)
            return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength) + "...";
    }
}
