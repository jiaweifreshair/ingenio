package com.ingenio.backend.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Uniaix AI Provider
 *
 * 统一AI服务提供商，支持多个AI模型提供商：
 * - Anthropic (Claude)
 * - Google Gemini
 * - OpenAI (GPT)
 * - Moonshot
 * - Volcengine
 * - xAI
 * - 智谱 (Zhipu)
 * - 腾讯 (Tencent)
 * - 阿里云 (Alibaba Cloud)
 *
 * Uniaix提供OpenAI兼容的API接口，可以无缝切换不同的AI模型
 */
@Slf4j
@Component
public class UniaixAIProvider implements AIProvider {

    private final ChatClient chatClient;
    private final String defaultModel;
    /**
     * UniAix API Key。
     *
     * 是什么：访问 UniAix 的鉴权密钥。
     * 做什么：用于请求签名与鉴权。
     * 为什么：没有密钥无法调用服务。
     */
    private final String apiKey;
    /**
     * UniAix Base URL。
     *
     * 是什么：OpenAI 兼容接口基础地址。
     * 做什么：用于构建请求 URL。
     * 为什么：支持自定义代理或网关。
     */
    private final String baseUrl;

    public UniaixAIProvider(
            @Value("${ingenio.ai.uniaix.base-url:https://api.uniaix.com}") String baseUrl,
            @Value("${ingenio.ai.uniaix.api-key:sk-placeholder}") String apiKey,
            @Value("${ingenio.ai.uniaix.default-model:claude-3-5-sonnet-20241022}") String defaultModel,
            @Value("${ingenio.ai.uniaix.timeout:180000}") int timeout
    ) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;

        // 创建OpenAI兼容的API客户端
        OpenAiApi openAiApi = new OpenAiApi(baseUrl, apiKey);

        // 配置Chat模型
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(defaultModel)
                .withTemperature(0.7)
                .withMaxTokens(4096)
                .build();

        ChatModel chatModel = new OpenAiChatModel(openAiApi, options);
        this.chatClient = ChatClient.create(chatModel);

        log.info("Uniaix AI Provider initialized: baseUrl={}, model={}", baseUrl, defaultModel);
    }

    /**
     * 获取内部 ChatClient（兼容旧调用）。
     *
     * 是什么：返回已初始化的 ChatClient 实例。
     * 做什么：提供给旧逻辑直接调用。
     * 为什么：兼容历史代码路径，避免重复构建。
     *
     * @return ChatClient 实例
     */
    public ChatClient getChatClient() {
        return chatClient;
    }

    @Override
    public String getProviderName() {
        return "uniaix";
    }

    @Override
    public String getProviderDisplayName() {
        return "UniAix";
    }

    @Override
    public String getDefaultModel() {
        return defaultModel;
    }

    @Override
    public boolean isAvailable() {
        boolean available = !isPlaceholderApiKey(apiKey);
        if (!available) {
            log.warn("UniAix 提供商不可用：API Key未配置或为占位符");
        }
        return available;
    }

    /**
     * 生成文本（OpenAI 兼容 Chat Completion）。
     *
     * 是什么：UniAix 文本生成实现。
     * 做什么：调用 ChatClient 输出文本内容。
     * 为什么：对齐 AIProvider 接口，便于统一调用。
     *
     * @param prompt 提示词
     * @param request 请求参数
     * @return AI 响应
     */
    @Override
    public AIResponse generate(String prompt, AIRequest request) {
        if (prompt == null || prompt.isBlank()) {
            throw new AIException("提示词不能为空", getProviderName());
        }
        long start = System.currentTimeMillis();

        String model = request != null && request.model() != null ? request.model() : defaultModel;
        Double temperature = request != null ? request.temperature() : null;
        Integer maxTokens = request != null ? request.maxTokens() : null;

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature != null ? temperature : 0.7)
                .withMaxTokens(maxTokens != null ? maxTokens : 4096)
                .build();

        ChatModel chatModel = new OpenAiChatModel(new OpenAiApi(baseUrl, apiKey), options);
        ChatClient client = ChatClient.create(chatModel);

        try {
            String content = client.prompt()
                    .user(prompt)
                    .call()
                    .content();
            long duration = System.currentTimeMillis() - start;
            return AIResponse.builder()
                    .content(content)
                    .model(model)
                    .promptTokens(0)
                    .completionTokens(0)
                    .totalTokens(0)
                    .durationMs(duration)
                    .provider(getProviderName())
                    .rawResponse(null)
                    .build();
        } catch (Exception e) {
            throw new AIException("UniAix 调用失败: " + e.getMessage(), getProviderName(), e);
        }
    }

    /**
     * 使用指定模型创建ChatClient
     *
     * @param model 模型名称（如：claude-3-5-sonnet-20241022, gpt-4, gemini-pro等）
     * @return ChatClient实例
     */
    public ChatClient getChatClientWithModel(String model) {
        log.info("Creating Uniaix ChatClient with model: {}", model);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(0.7)
                .withMaxTokens(4096)
                .build();

        // 复用现有的API客户端，只更新模型配置
        ChatModel chatModel = new OpenAiChatModel(
                new OpenAiApi(
                        baseUrl,
                        apiKey
                ),
                options
        );

        return ChatClient.create(chatModel);
    }

    /**
     * 判断 API Key 是否为占位符。
     *
     * 是什么：占位符检测方法。
     * 做什么：识别未配置的 Key。
     * 为什么：避免误用示例 Key 发起请求。
     *
     * @param value API Key
     * @return 是否为占位符
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
                || lower.startsWith("sk-your-");
    }
}
