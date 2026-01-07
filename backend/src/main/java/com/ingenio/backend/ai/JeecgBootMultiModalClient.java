package com.ingenio.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JeecgBoot多模态AI服务客户端
 *
 * 封装对JeecgBoot七牛云多模态API的调用。
 * 使用OkHttp客户端，与现有代码风格保持一致。
 *
 * API端点说明：
 * - POST /airag/multimodal/chat        - 文本生成
 * - POST /airag/multimodal/vision      - 图像分析
 * - POST /airag/multimodal/asr         - 语音识别
 * - POST /airag/multimodal/tts         - 语音合成
 * - POST /airag/multimodal/ocr         - OCR识别
 * - POST /airag/multimodal/image       - 图像生成
 * - GET  /airag/multimodal/health      - 健康检查
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class JeecgBootMultiModalClient {

    @Value("${jeecgboot.api.base-url:http://localhost:7008}")
    private String baseUrl;

    @Value("${jeecgboot.api.token:}")
    private String apiToken;

    @Value("${jeecgboot.api.connect-timeout:60000}")
    private int connectTimeout;

    @Value("${jeecgboot.api.read-timeout:300000}")
    private int readTimeout;

    @Value("${jeecgboot.api.write-timeout:60000}")
    private int writeTimeout;

    private final ObjectMapper objectMapper;
    private OkHttpClient httpClient;

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    public JeecgBootMultiModalClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeout, TimeUnit.MILLISECONDS)
                .build();
        log.info("JeecgBoot多模态客户端初始化完成: baseUrl={}", baseUrl);
    }

    // ==================== 公共方法 ====================

    /**
     * 检查JeecgBoot服务是否可用
     */
    public boolean isAvailable() {
        try {
            String url = baseUrl + "/airag/multimodal/health";
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JsonNode json = objectMapper.readTree(body);
                    // 检查 result.available 字段
                    return json.path("result").path("available").asBoolean(false);
                }
            }
        } catch (Exception e) {
            log.warn("JeecgBoot健康检查失败: {}", e.getMessage());
        }
        return false;
    }

    // ==================== 图像分析 ====================

    /**
     * 图像分析结果
     */
    public static class VisionAnalysisResult {
        private String content;
        private String description;
        private boolean success;
        private String errorMessage;
        private long durationMs;

        // Getters and Setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }

    /**
     * 分析图像
     *
     * @param imageUrl 图像URL
     * @param prompt 分析提示词
     * @return 分析结果
     */
    public VisionAnalysisResult analyzeImage(String imageUrl, String prompt) {
        log.info("调用JeecgBoot Vision API: imageUrl={}", imageUrl);
        long startTime = System.currentTimeMillis();

        VisionAnalysisResult result = new VisionAnalysisResult();

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("image", imageUrl);
            requestBody.put("imageType", "url");
            requestBody.put("prompt", prompt);
            requestBody.put("maxTokens", 4096);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String responseBody = executePost("/airag/multimodal/vision", jsonBody);

            JsonNode json = objectMapper.readTree(responseBody);

            if (json.path("success").asBoolean(false)) {
                JsonNode resultNode = json.path("result");
                result.setSuccess(resultNode.path("success").asBoolean(false));
                result.setContent(resultNode.path("content").asText());
                result.setDescription(resultNode.path("description").asText());
                result.setDurationMs(resultNode.path("durationMs").asLong());

                if (!result.isSuccess()) {
                    result.setErrorMessage(resultNode.path("errorMessage").asText());
                }
            } else {
                result.setSuccess(false);
                result.setErrorMessage(json.path("message").asText("JeecgBoot API返回失败"));
            }

        } catch (Exception e) {
            log.error("JeecgBoot Vision API调用失败", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        result.setDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    // ==================== 语音识别 ====================

    /**
     * 语音识别结果
     */
    public static class SpeechRecognitionResult {
        private String text;
        private String language;
        private boolean success;
        private String errorMessage;
        private long durationMs;

        // Getters and Setters
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }

    /**
     * 语音识别（从URL）
     *
     * @param audioUrl 音频文件URL
     * @return 识别结果
     */
    public SpeechRecognitionResult transcribeAudio(String audioUrl) {
        log.info("调用JeecgBoot ASR API: audioUrl={}", audioUrl);
        long startTime = System.currentTimeMillis();

        SpeechRecognitionResult result = new SpeechRecognitionResult();

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("audioUrl", audioUrl);
            requestBody.put("language", "zh");

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String responseBody = executePost("/airag/multimodal/asr", jsonBody);

            JsonNode json = objectMapper.readTree(responseBody);

            if (json.path("success").asBoolean(false)) {
                JsonNode resultNode = json.path("result");
                result.setSuccess(resultNode.path("success").asBoolean(false));
                result.setText(resultNode.path("text").asText());
                result.setLanguage(resultNode.path("language").asText());
                result.setDurationMs(resultNode.path("durationMs").asLong());

                if (!result.isSuccess()) {
                    result.setErrorMessage(resultNode.path("errorMessage").asText());
                }
            } else {
                result.setSuccess(false);
                result.setErrorMessage(json.path("message").asText("JeecgBoot API返回失败"));
            }

        } catch (Exception e) {
            log.error("JeecgBoot ASR API调用失败", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        result.setDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    // ==================== 文本生成 ====================

    /**
     * 文本生成结果
     */
    public static class ChatCompletionResult {
        private String content;
        private String model;
        private int totalTokens;
        private boolean success;
        private String errorMessage;
        private long durationMs;

        // Getters and Setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }

    /**
     * 文本生成
     *
     * @param prompt 提示词
     * @param model 模型名称（可选）
     * @return 生成结果
     */
    public ChatCompletionResult chatCompletion(String prompt, String model) {
        log.info("调用JeecgBoot Chat API: model={}, promptLength={}",
                model, prompt != null ? prompt.length() : 0);
        long startTime = System.currentTimeMillis();

        ChatCompletionResult result = new ChatCompletionResult();

        try {
            Map<String, Object> requestBody = new HashMap<>();
            if (model != null && !model.isBlank()) {
                requestBody.put("model", model);
            }

            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);
            requestBody.put("messages", messages);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String responseBody = executePost("/airag/multimodal/chat", jsonBody);

            JsonNode json = objectMapper.readTree(responseBody);

            if (json.path("success").asBoolean(false)) {
                JsonNode resultNode = json.path("result");
                result.setSuccess(resultNode.path("success").asBoolean(false));
                result.setModel(resultNode.path("model").asText());

                // 提取content
                JsonNode choicesNode = resultNode.path("choices");
                if (choicesNode.isArray() && choicesNode.size() > 0) {
                    result.setContent(choicesNode.get(0).path("message").path("content").asText());
                }

                // 提取token统计
                JsonNode usageNode = resultNode.path("usage");
                result.setTotalTokens(usageNode.path("totalTokens").asInt(0));
                result.setDurationMs(resultNode.path("durationMs").asLong());

                if (!result.isSuccess()) {
                    result.setErrorMessage(resultNode.path("errorMessage").asText());
                }
            } else {
                result.setSuccess(false);
                result.setErrorMessage(json.path("message").asText("JeecgBoot API返回失败"));
            }

        } catch (Exception e) {
            log.error("JeecgBoot Chat API调用失败", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        result.setDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    // ==================== 工具方法 ====================

    /**
     * 执行POST请求
     */
    private String executePost(String path, String jsonBody) throws IOException {
        String url = baseUrl + path;

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE));

        // 添加认证Token（如果配置了）
        if (apiToken != null && !apiToken.isBlank()) {
            requestBuilder.header("X-Access-Token", apiToken);
        }

        Request request = requestBuilder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("JeecgBoot API调用失败: url={}, code={}, body={}",
                        url, response.code(), responseBody);
                throw new IOException("HTTP错误: " + response.code() + " - " + responseBody);
            }

            return responseBody;
        }
    }
}
