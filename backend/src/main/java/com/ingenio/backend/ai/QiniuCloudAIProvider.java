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
 * - 默认模型：Qwen/Qwen2.5-72B-Instruct（通义千问2.5-72B参数版本）
 * - 兼容OpenAI Chat Completion API格式
 * - 支持流式和非流式响应（当前实现非流式）
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
     * 七牛云API端点（OpenAI兼容）
     */
    private static final String API_ENDPOINT = "https://api.qnaigc.com/v1/chat/completions";

    /**
     * 默认模型：通义千问3-235B指令微调版本
     *
     * 其他可选模型：
     * - qwen3-235b-a22b-instruct-2507（推荐，综合性能最佳，235B参数）
     * - deepseek-v3（数学和代码能力强）
     * - qwen3-coder-480b-a35b-instruct（代码生成专用，480B参数）
     * - qwen3-32b（轻量级模型，32B参数）
     */
    private static final String DEFAULT_MODEL = "qwen3-235b-a22b-instruct-2507";

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
     * 七牛云API Key
     * 优先级：
     * 1. spring.ai.openai.api-key（推荐：配置在application.yml中）
     * 2. QINIU_CLOUD_API_KEY 环境变量
     * 3. DEEPSEEK_API_KEY 环境变量（兼容旧配置）
     */
    @Value("${spring.ai.openai.api-key:${QINIU_CLOUD_API_KEY:${DEEPSEEK_API_KEY:}}}")
    private String apiKey;

    /**
     * JSON序列化工具
     */
    private final ObjectMapper objectMapper;

    public QiniuCloudAIProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        return DEFAULT_MODEL;
    }

    @Override
    public boolean isAvailable() {
        boolean available = apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("sk-placeholder");
        if (!available) {
            log.warn("七牛云AI提供商不可用：API Key未配置或为占位符");
            log.warn("请配置环境变量 QINIU_CLOUD_API_KEY 或 DEEPSEEK_API_KEY");
        }
        return available;
    }

    /**
     * 生成文本（同步调用）
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

        try {
            // 1. 构建请求Body（OpenAI格式）
            String requestBody = buildRequestBody(prompt, request);

            log.debug("七牛云API请求: model={}, temperature={}, maxTokens={}",
                    request.model() != null ? request.model() : DEFAULT_MODEL,
                    request.temperature(),
                    request.maxTokens());

            // 2. 执行HTTP请求
            String responseBody = executeHttpRequest(requestBody);

            // 3. 解析响应
            AIResponse response = parseResponse(responseBody, startTime);

            log.info("七牛云API调用成功: model={}, promptTokens={}, completionTokens={}, duration={}ms",
                    response.model(),
                    response.promptTokens(),
                    response.completionTokens(),
                    response.durationMs());

            return response;

        } catch (AIException e) {
            // 重新抛出AIException
            throw e;
        } catch (Exception e) {
            // 包装其他异常
            long duration = System.currentTimeMillis() - startTime;
            log.error("七牛云API调用异常: duration={}ms", duration, e);
            throw new AIException(
                    "API调用失败: " + e.getMessage(),
                    getProviderName(),
                    e
            );
        }
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
            requestBody.put("model", request.model() != null ? request.model() : DEFAULT_MODEL);

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
                    .url(API_ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            // 执行请求
            try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                // 检查HTTP状态码
                if (!response.isSuccessful()) {
                    log.error("七牛云API调用失败: code={}, message={}, body={}",
                            response.code(), response.message(), responseBody);

                    // 尝试解析错误信息
                    String errorMessage = parseErrorMessage(responseBody);

                    throw new AIException(
                            "API调用失败: " + errorMessage,
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
            String model = jsonResponse.path("model").asText(DEFAULT_MODEL);

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
