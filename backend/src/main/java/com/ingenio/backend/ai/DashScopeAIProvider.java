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
 * 阿里云DashScope AI提供商实现（通义千问）
 *
 * 技术特点：
 * - API端点：https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation
 * - 默认模型：qwen-max（通义千问最大参数版本）
 * - 阿里云专有API格式（非OpenAI兼容）
 * - 支持流式和非流式响应（当前实现非流式）
 *
 * 优势：
 * - 阿里云生态完整集成
 * - 中文理解能力优秀
 * - 企业级稳定性保障
 * - 支持通义系列全家桶（文本、视觉、语音等）
 *
 * API Key获取方式：
 * 1. 访问 https://dashscope.aliyuncs.com
 * 2. 进入管理控制台 > API-KEY管理
 * 3. 创建API密钥
 * 4. 配置到环境变量：DASHSCOPE_API_KEY
 *
 * @author Ingenio Team
 * @since 1.0.0
 * @see <a href="https://dashscope.aliyuncs.com/">阿里云DashScope</a>
 */
@Slf4j
@Component
public class DashScopeAIProvider implements AIProvider {

    /**
     * 阿里云DashScope API端点
     */
    private static final String API_ENDPOINT = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

    /**
     * 默认模型：通义千问最大参数版本
     *
     * 其他可选模型：
     * - qwen-max（推荐，综合性能最佳）
     * - qwen-plus（性价比版本）
     * - qwen-turbo（高速版本）
     * - qwen-vl-plus（视觉理解模型）
     * - qwen-vl-max（视觉理解最强版）
     */
    private static final String DEFAULT_MODEL = "qwen-max";

    /**
     * HTTP客户端配置
     * - 连接超时：60秒
     * - 读取超时：180秒（生成长文本需要更长时间）
     * - 写入超时：60秒
     */
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * 阿里云DashScope API Key
     */
    @Value("${DASHSCOPE_API_KEY:}")
    private String apiKey;

    /**
     * JSON序列化工具
     */
    private final ObjectMapper objectMapper;

    public DashScopeAIProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "dashscope";
    }

    @Override
    public String getProviderDisplayName() {
        return "阿里云DashScope";
    }

    @Override
    public String getDefaultModel() {
        return DEFAULT_MODEL;
    }

    @Override
    public boolean isAvailable() {
        boolean available = apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("sk-placeholder");
        if (!available) {
            log.warn("阿里云DashScope提供商不可用：API Key未配置或为占位符");
            log.warn("请配置环境变量 DASHSCOPE_API_KEY");
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
                    "阿里云DashScope提供商不可用：API Key未配置",
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
            // 1. 构建请求Body（DashScope专有格式）
            String requestBody = buildRequestBody(prompt, request);

            log.debug("阿里云DashScope API请求: model={}, temperature={}, maxTokens={}",
                    request.model() != null ? request.model() : DEFAULT_MODEL,
                    request.temperature(),
                    request.maxTokens());

            // 2. 执行HTTP请求
            String responseBody = executeHttpRequest(requestBody);

            // 3. 解析响应
            AIResponse response = parseResponse(responseBody, startTime);

            log.info("阿里云DashScope API调用成功: model={}, promptTokens={}, completionTokens={}, duration={}ms",
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
            log.error("阿里云DashScope API调用异常: duration={}ms", duration, e);
            throw new AIException(
                    "API调用失败: " + e.getMessage(),
                    getProviderName(),
                    e
            );
        }
    }

    /**
     * 构建DashScope专有格式的请求Body
     *
     * DashScope请求格式示例：
     * {
     *   "model": "qwen-max",
     *   "input": {
     *     "messages": [
     *       { "role": "user", "content": "提示词" }
     *     ]
     *   },
     *   "parameters": {
     *     "result_format": "message",
     *     "temperature": 0.7,
     *     "max_tokens": 4096
     *   }
     * }
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

            // 输入消息（DashScope格式）
            Map<String, Object> input = new HashMap<>();
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "user", "content", prompt));
            input.put("messages", messages);
            requestBody.put("input", input);

            // 生成参数
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("result_format", "message");  // 返回message格式

            if (request.temperature() != null) {
                parameters.put("temperature", request.temperature());
            }
            if (request.maxTokens() != null) {
                parameters.put("max_tokens", request.maxTokens());
            }
            if (request.topP() != null) {
                parameters.put("top_p", request.topP());
            }
            if (request.stopSequence() != null) {
                parameters.put("stop", request.stopSequence());
            }

            requestBody.put("parameters", parameters);

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
                    log.error("阿里云DashScope API调用失败: code={}, message={}, body={}",
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
     * 解析DashScope格式的响应
     *
     * DashScope响应格式示例：
     * {
     *   "output": {
     *     "choices": [
     *       {
     *         "message": {
     *           "content": "生成的文本内容"
     *         }
     *       }
     *     ]
     *   },
     *   "usage": {
     *     "input_tokens": 100,
     *     "output_tokens": 200,
     *     "total_tokens": 300
     *   }
     * }
     *
     * @param responseBody 响应JSON字符串
     * @param startTime 请求开始时间（毫秒）
     * @return AIResponse对象
     * @throws AIException 当解析失败时抛出
     */
    private AIResponse parseResponse(String responseBody, long startTime) throws AIException {
        try {
            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            // 提取生成的内容（DashScope格式）
            String content = jsonResponse
                    .path("output")
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

            // 提取模型名称（DashScope可能返回实际使用的模型）
            String model = jsonResponse.path("model").asText();
            if (model == null || model.isBlank()) {
                model = DEFAULT_MODEL;
            }

            // 提取token统计信息（DashScope格式：input_tokens, output_tokens）
            JsonNode usage = jsonResponse.path("usage");
            int promptTokens = usage.path("input_tokens").asInt(0);
            int completionTokens = usage.path("output_tokens").asInt(0);
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

            // DashScope可能返回多种错误格式
            // 格式1: { "message": "错误信息" }
            String message = jsonResponse.path("message").asText();
            if (message != null && !message.isBlank()) {
                return message;
            }

            // 格式2: { "error": { "message": "错误信息" } }
            message = jsonResponse.path("error").path("message").asText();
            if (message != null && !message.isBlank()) {
                return message;
            }

            // 格式3: { "error_message": "错误信息" }
            message = jsonResponse.path("error_message").asText();
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

            // DashScope可能返回多种错误代码格式
            // 格式1: { "code": "错误代码" }
            String code = jsonResponse.path("code").asText();
            if (code != null && !code.isBlank()) {
                return code;
            }

            // 格式2: { "error": { "code": "错误代码" } }
            code = jsonResponse.path("error").path("code").asText();
            if (code != null && !code.isBlank()) {
                return code;
            }

            // 格式3: { "error_code": "错误代码" }
            code = jsonResponse.path("error_code").asText();
            if (code != null && !code.isBlank()) {
                return code;
            }

        } catch (Exception e) {
            log.debug("无法提取错误代码: {}", e.getMessage());
        }
        return null;
    }
}
