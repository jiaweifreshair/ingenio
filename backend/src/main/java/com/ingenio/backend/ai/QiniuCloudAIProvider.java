package com.ingenio.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 七牛云AI提供商实现（OpenAI兼容接口）
 *
 * 技术特点：
 * - API端点：https://api.qnaigc.com/v1/chat/completions
 * - 默认模型：z-ai/glm-4.7（智谱AI GLM-4.7）
 * - 兼容OpenAI Chat Completion API格式
 * - 支持流式和非流式响应（当前实现非流式）
 *
 * 速率限制处理：
 * - 集成AIRateLimiter，实现令牌桶速率限制
 * - 支持指数退避重试（针对429错误）
 * - 调用间隔控制，避免请求过于密集
 *
 * 优势：
 * - 国内访问速度快，无需翻墙
 * - 价格相对更优惠
 * - 支持多种开源大模型（Qwen系列、DeepSeek等）
 * - 与OpenAI API兼容，易于迁移
 *
 * API Key获取方式：
 * 1. 访问 https://www.qiniu.com
 * 2. 进入"AI大模型推理"产品页
 * 3. 创建API密钥
 * 4. 配置到环境变量：QINIU_CLOUD_API_KEY 或 DEEPSEEK_API_KEY
 *
 * @author Ingenio Team
 * @since 1.0.0
 * @see <a href="https://www.qiniu.com/products/qiniu-ai">七牛云AI大模型推理</a>
 */
@Slf4j
@Component
public class QiniuCloudAIProvider implements AIProvider {

    /**
     * 七牛云OpenAI兼容API默认Base URL（不包含 /v1）
     *
     * 说明：
     * - Spring AI 的 base-url 约定不包含 `/v1`（内部会自动拼接 `/v1/...`）。
     * - 本类会在构建请求URL时统一拼接 `/v1/chat/completions`，因此两种配置都兼容：
     *   - https://api.qnaigc.com
     *   - https://api.qnaigc.com/v1
     */
    private static final String DEFAULT_BASE_URL = "https://api.qnaigc.com";

    /**
     * 默认模型：从配置文件读取，回退到 deepseek-v3
     *
     * 可选模型（按推荐度排序）：
     * - deepseek-v3：代码和推理能力强，RPM限制相对宽松（推荐）
     * - qwen-max：通义千问旗舰模型
     * - qwen-plus：性价比高
     * - z-ai/glm-4.7：智谱AI最新模型
     * - qwen3-235b-a22b-instruct-2507：通义千问3-235B（RPM限制严格）
     */
    private static final String FALLBACK_MODEL = "deepseek-v3";

    /**
     * HTTP客户端配置
     * - 连接超时：60秒（考虑到大模型响应时间）
     * - 读取超时：300秒（5分钟 - 支持7个并发请求，每个请求可能需要90-150秒）
     * - 写入超时：60秒
     *
     * 超时说明：
     * - 单个AI生成请求约需87-133秒
     * - 7个并发请求时，部分请求可能排队导致更长等待
     * - 设置为300秒可确保所有请求有足够时间完成
     */
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * 最大重试次数（当响应内容为空或发生可重试错误时重试）
     * 参考 open-lovable-cn 的 geminiFetch 实现：3次重试
     */
    private static final int MAX_RETRIES = 3;

    /**
     * 基础重试间隔（毫秒）
     * 参考 open-lovable-cn：429错误使用 2秒基础延迟，其他错误 3秒
     */
    private static final long BASE_RETRY_DELAY_MS = 2000;

    /**
     * 最大重试间隔（毫秒）- 15秒
     * 参考 open-lovable-cn：Math.min(baseDelay * Math.pow(2, attempt - 1), 15000)
     */
    private static final long MAX_RETRY_DELAY_MS = 15_000;

    /**
     * 速率限制获取许可的超时时间（毫秒）- 5分钟
     */
    private static final long RATE_LIMIT_ACQUIRE_TIMEOUT_MS = 300_000;

    /**
     * 七牛云API Key
     * 优先级：
     * 1. spring.ai.openai.api-key（推荐：配置在application.yml中）
     * 2. QINIU_CLOUD_API_KEY 环境变量
     * 3. DEEPSEEK_API_KEY 环境变量（兼容旧配置）
     */
    @Value("${spring.ai.openai.api-key:${SPRING_AI_OPENAI_API_KEY:${QINIU_CLOUD_API_KEY:${QINIU_AI_API_KEY:${DEEPSEEK_API_KEY:}}}}}")
    private String apiKey;

    /**
     * OpenAI兼容Base URL（支持通过配置覆盖）
     *
     * 优先级：
     * 1. spring.ai.openai.base-url（Spring AI统一配置）
     * 2. SPRING_AI_OPENAI_BASE_URL（环境变量）
     * 3. QINIU_CLOUD_BASE_URL（环境变量）
     * 4. 默认值：https://api.qnaigc.com
     */
    @Value("${spring.ai.openai.base-url:${SPRING_AI_OPENAI_BASE_URL:${QINIU_CLOUD_BASE_URL:https://api.qnaigc.com}}}")
    private String baseUrl;

    /**
     * 默认模型（从配置文件读取，与 Spring AI ChatClient 保持一致）
     *
     * 优先级：
     * 1. spring.ai.openai.chat.options.model（Spring AI配置）
     * 2. FALLBACK_MODEL（回退到 deepseek-v3）
     */
    @Value("${spring.ai.openai.chat.options.model:" + FALLBACK_MODEL + "}")
    private String defaultModel;

    /**
     * JSON序列化工具
     */
    private final ObjectMapper objectMapper;

    /**
     * AI调用速率限制器
     */
    private final AIRateLimiter rateLimiter;

    public QiniuCloudAIProvider(ObjectMapper objectMapper, AIRateLimiter rateLimiter) {
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public String getProviderName() {
        return "qiniu";
    }

    @Override
    public String getProviderDisplayName() {
        return "七牛云Qiniu";
    }

    @Override
    public String getDefaultModel() {
        return defaultModel != null && !defaultModel.isBlank() ? defaultModel : FALLBACK_MODEL;
    }

    @Override
    public boolean isAvailable() {
        boolean available = !isPlaceholderApiKey(apiKey);
        if (!available) {
            log.warn("七牛云AI提供商不可用：API Key未配置或为占位符");
            log.warn("请配置环境变量 QINIU_CLOUD_API_KEY（或 SPRING_AI_OPENAI_API_KEY / DEEPSEEK_API_KEY）");
        }
        return available;
    }

    /**
     * 判断API Key是否为"未配置/占位符"值
     *
     * 说明：
     * - 为避免把示例值当作真实Key发起请求（导致 401 invalid api key），这里对常见占位符做拦截。
     * - 真实Key不应该包含 "your-*"、"placeholder" 等明显标识。
     */
    private boolean isPlaceholderApiKey(String value) {
        if (value == null) {
            return true;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return true;
        }

        String lower = normalized.toLowerCase();
        return lower.equals("sk-placeholder")
                || lower.startsWith("sk-placeholder")
                || lower.equals("your-api-key-here")
                || lower.contains("your-api-key")
                || lower.contains("your-deepseek-api-key")
                || lower.startsWith("sk-your-");
    }

    /**
     * 构建Chat Completions完整URL
     *
     * 兼容两种配置方式：
     * - baseUrl = https://api.qnaigc.com（与Spring AI保持一致）
     * - baseUrl = https://api.qnaigc.com/v1（历史配置也兼容，会自动裁剪 /v1）
     */
    private String buildChatCompletionsUrl() {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        if (normalized.isEmpty()) {
            normalized = DEFAULT_BASE_URL;
        }

        // 移除末尾斜杠
        normalized = normalized.replaceAll("/+$", "");

        // 移除已有的 /v1 后缀（避免重复）
        if (normalized.toLowerCase().endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }

        // 统一添加 /v1/chat/completions
        return normalized + "/v1/chat/completions";
    }

    /**
     * 生成文本（同步调用，带速率限制和指数退避重试机制）
     *
     * 执行流程：
     * 1. 验证API可用性和参数
     * 2. 获取速率限制许可
     * 3. 执行HTTP请求
     * 4. 遇到速率限制错误时进行指数退避重试
     * 5. 解析并返回响应
     *
     * @param prompt 提示词（支持多行文本）
     * @param request 请求参数配置
     * @return AI生成的响应
     * @throws AIException 当API调用失败时抛出
     */
    @Override
    public AIResponse generate(String prompt, AIRequest request) throws AIException {
        // 验证API可用性
        if (!isAvailable()) {
            throw new AIException(
                    "七牛云AI提供商不可用：API Key未配置",
                    getProviderName()
            );
        }

        // 验证提示词
        if (prompt == null || prompt.isBlank()) {
            throw new AIException(
                    "提示词不能为空",
                    getProviderName()
            );
        }

        long startTime = System.currentTimeMillis();
        AIException lastException = null;

        // 重试循环：当响应为空或发生可重试错误时自动重试
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            // 获取速率限制许可
            boolean acquired = false;
            try {
                log.debug("[QiniuCloudAI] 尝试获取速率限制许可... (attempt {}/{})", attempt, MAX_RETRIES);
                acquired = rateLimiter.acquire(RATE_LIMIT_ACQUIRE_TIMEOUT_MS);

                if (!acquired) {
                    throw new AIException(
                            "获取速率限制许可超时，请稍后重试",
                            getProviderName()
                    );
                }

                // 1. 构建请求Body（OpenAI格式）
                String requestBody = buildRequestBody(prompt, request);

                log.info("[QiniuCloudAI] API请求开始: model={}, temperature={}, maxTokens={}, attempt={}/{}, rateLimiter={}",
                        request.model() != null ? request.model() : getDefaultModel(),
                        request.temperature(),
                        request.maxTokens(),
                        attempt,
                        MAX_RETRIES,
                        rateLimiter.getStatus());

                // 2. 执行HTTP请求
                String responseBody = executeHttpRequest(requestBody);

                // 3. 解析响应
                AIResponse response = parseResponse(responseBody, startTime);

                log.info("[QiniuCloudAI] API调用成功: model={}, promptTokens={}, completionTokens={}, duration={}ms, attempt={}/{}",
                        response.model(),
                        response.promptTokens(),
                        response.completionTokens(),
                        response.durationMs(),
                        attempt,
                        MAX_RETRIES);

                return response;

            } catch (AIException e) {
                lastException = e;

                // 检查是否是速率限制错误
                boolean isRateLimitError = rateLimiter.isRateLimitError(e.getHttpStatus(), e.getMessage());

                if (isRateLimitError) {
                    // 速率限制错误：使用指数退避重试
                    if (attempt < MAX_RETRIES) {
                        long backoffDelay = rateLimiter.calculateExponentialBackoff(
                                attempt, BASE_RETRY_DELAY_MS, MAX_RETRY_DELAY_MS);

                        log.warn("[QiniuCloudAI] 触发速率限制（attempt {}/{}），{}ms后重试: {}",
                                attempt, MAX_RETRIES, backoffDelay, e.getMessage());

                        try {
                            Thread.sleep(backoffDelay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw e;
                        }
                        continue;
                    }

                    // 已达到最大重试次数
                    log.error("[QiniuCloudAI] 速率限制重试耗尽，请求失败: {}", e.getMessage());
                    throw e;
                }

                // 检查是否是可重试的其他错误（响应内容为空、超时等）
                boolean isRetryable = e.getMessage() != null &&
                        (e.getMessage().contains("响应内容为空") ||
                         e.getMessage().contains("empty") ||
                         e.getMessage().contains("timeout") ||
                         e.getMessage().contains("Connection reset"));

                if (isRetryable && attempt < MAX_RETRIES) {
                    long retryDelay = BASE_RETRY_DELAY_MS * attempt;

                    log.warn("[QiniuCloudAI] 响应异常（attempt {}/{}），{}ms后重试: {}",
                            attempt, MAX_RETRIES, retryDelay, e.getMessage());

                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    continue;
                }

                // 不可重试的错误或已达到最大重试次数
                throw e;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AIException(
                        "请求被中断: " + e.getMessage(),
                        getProviderName(),
                        e
                );

            } catch (Exception e) {
                // 包装其他异常
                long duration = System.currentTimeMillis() - startTime;
                log.error("[QiniuCloudAI] API调用异常: duration={}ms, attempt={}/{}", duration, attempt, MAX_RETRIES, e);
                lastException = new AIException(
                        "API调用失败: " + e.getMessage(),
                        getProviderName(),
                        e
                );

                // 对于一般异常也尝试重试
                if (attempt < MAX_RETRIES) {
                    long retryDelay = BASE_RETRY_DELAY_MS * attempt;

                    log.warn("[QiniuCloudAI] 异常（attempt {}/{}），{}ms后重试",
                            attempt, MAX_RETRIES, retryDelay);

                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw lastException;
                    }
                    continue;
                }

                throw lastException;

            } finally {
                // 释放速率限制许可
                if (acquired) {
                    rateLimiter.release();
                }
            }
        }

        // 理论上不会到达这里，但为了编译通过
        throw lastException != null ? lastException : new AIException(
                "API调用失败：已达到最大重试次数",
                getProviderName()
        );
    }

    /**
     * 构建OpenAI兼容的请求Body
     *
     * @param prompt 提示词
     * @param request 请求参数
     * @return JSON字符串
     */
    private String buildRequestBody(String prompt, AIRequest request) throws AIException {
        try {
            Map<String, Object> requestBody = new HashMap<>();

            // 模型名称
            requestBody.put("model", request.model() != null ? request.model() : getDefaultModel());

            // 消息列表（OpenAI格式）
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "user", "content", prompt));
            requestBody.put("messages", messages);

            // 生成参数
            if (request.temperature() != null) {
                requestBody.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                requestBody.put("max_tokens", request.maxTokens());
            }
            if (request.topP() != null) {
                requestBody.put("top_p", request.topP());
            }
            if (request.stopSequence() != null) {
                requestBody.put("stop", request.stopSequence());
            }

            // 禁用流式响应
            requestBody.put("stream", false);

            return objectMapper.writeValueAsString(requestBody);

        } catch (Exception e) {
            throw new AIException(
                    "构建请求Body失败: " + e.getMessage(),
                    getProviderName(),
                    e
            );
        }
    }

    /**
     * 执行HTTP请求
     *
     * @param requestBody JSON请求体
     * @return 响应JSON字符串
     * @throws AIException 当HTTP请求失败时抛出
     */
    private String executeHttpRequest(String requestBody) throws AIException {
        try {
            // 构建HTTP请求
            Request httpRequest = new Request.Builder()
                    .url(buildChatCompletionsUrl())
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            // 执行请求
            try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                // 检查HTTP状态码
                if (!response.isSuccessful()) {
                    log.error("[QiniuCloudAI] API调用失败: code={}, message={}, body={}",
                            response.code(), response.message(), responseBody);

                    // 尝试解析错误信息
                    String errorMessage = parseErrorMessage(responseBody);

                    throw new AIException(
                            errorMessage,
                            getProviderName(),
                            extractErrorCode(responseBody),
                            response.code()
                    );
                }

                return responseBody;
            }

        } catch (IOException e) {
            throw new AIException(
                    "HTTP请求失败: " + e.getMessage(),
                    getProviderName(),
                    e
            );
        }
    }

    /**
     * 解析OpenAI格式的响应
     *
     * @param responseBody 响应JSON字符串
     * @param startTime 请求开始时间（毫秒）
     * @return AIResponse对象
     * @throws AIException 当解析失败时抛出
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
                throw new AIException(
                        "响应内容为空",
                        getProviderName()
                );
            }

            // 提取模型名称
            String model = jsonResponse.path("model").asText(getDefaultModel());

            // 提取token统计信息
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

        } catch (Exception e) {
            throw new AIException(
                    "解析响应失败: " + e.getMessage(),
                    getProviderName(),
                    e
            );
        }
    }

    /**
     * 从错误响应中提取错误信息
     *
     * @param responseBody 错误响应Body
     * @return 错误信息字符串
     */
    private String parseErrorMessage(String responseBody) {
        try {
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            String message = jsonResponse.path("error").path("message").asText();
            if (message != null && !message.isBlank()) {
                return message;
            }
        } catch (Exception e) {
            log.debug("无法解析错误信息: {}", e.getMessage());
        }
        return responseBody;
    }

    /**
     * 从错误响应中提取错误代码
     *
     * @param responseBody 错误响应Body
     * @return 错误代码字符串
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
}
