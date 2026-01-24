package com.ingenio.backend.langchain4j.model;

import com.ingenio.backend.langchain4j.config.LangChain4jProperties;
import com.ingenio.backend.langchain4j.rag.FallbackEmbeddingModel;
import com.ingenio.backend.langchain4j.rag.UniaixEmbeddingModelAdapter;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LangChain4j 模型工厂默认实现。
 *
 * 是什么：基于配置创建 OpenAI 兼容模型。
 * 做什么：构建并缓存 Chat/Embedding 模型实例。
 * 为什么：避免重复创建模型实例并统一配置入口。
 */
public class LangChain4jModelFactoryImpl implements LangChain4jModelFactory {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jModelFactoryImpl.class);

    /**
     * LangChain4j 配置。
     *
     * 是什么：模型与提供商配置容器。
     * 做什么：读取 API Key/baseUrl/model 等信息。
     * 为什么：确保模型构建可配置化。
     */
    private final LangChain4jProperties properties;

    /**
     * Chat 模型缓存。
     *
     * 是什么：provider+model 维度的缓存。
     * 做什么：复用已创建的模型实例。
     * 为什么：减少重复构建开销。
     */
    private final Map<String, ChatLanguageModel> chatModelCache = new ConcurrentHashMap<>();

    /**
     * Embedding 模型缓存。
     *
     * 是什么：provider 维度的缓存。
     * 做什么：复用嵌入模型实例。
     * 为什么：减少重复构建开销。
     */
    private final Map<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();

    /**
     * 构造函数。
     *
     * 是什么：模型工厂实现的初始化入口。
     * 做什么：注入 LangChain4j 配置。
     * 为什么：保证模型构建依赖配置可用。
     *
     * @param properties LangChain4j 配置
     */
    public LangChain4jModelFactoryImpl(LangChain4jProperties properties) {
        this.properties = properties;
    }

    /**
     * 构建 Chat 模型。
     *
     * 是什么：Chat 模型构建入口。
     * 做什么：根据 providerKey 与 modelName 返回可复用实例。
     * 为什么：统一构建与缓存策略。
     *
     * @param providerKey 提供商标识
     * @param modelName 模型名称
     * @return Chat 模型实例
     */
    @Override
    public ChatLanguageModel chatModel(String providerKey, String modelName) {
        LangChain4jProperties.Provider provider = resolveProvider(providerKey);
        String resolvedModel = resolveModelName(modelName, provider.getModel());
        String cacheKey = providerKey + ":" + resolvedModel;
        return chatModelCache.computeIfAbsent(cacheKey, key -> buildChatModel(provider, resolvedModel));
    }

    /**
     * 构建 Embedding 模型。
     *
     * 是什么：Embedding 模型构建入口。
     * 做什么：根据 providerKey 返回可复用实例。
     * 为什么：统一构建与缓存策略。
     *
     * @param providerKey 提供商标识
     * @return Embedding 模型实例
     */
    @Override
    public EmbeddingModel embeddingModel(String providerKey) {
        LangChain4jProperties.Provider provider = resolveProvider(providerKey);
        String resolvedModel = resolveModelName(provider.getEmbeddingModel(), provider.getEmbeddingModel());
        if (resolvedModel == null) {
            throw new IllegalStateException("LangChain4j Embedding 模型未配置: " + providerKey);
        }
        return embeddingModelCache.computeIfAbsent(providerKey, key -> buildEmbeddingModel(provider, resolvedModel));
    }

    /**
     * 构建 Chat 模型。
     *
     * 是什么：Chat 模型构建方法。
     * 做什么：基于 OpenAI 兼容接口创建模型。
     * 为什么：支持 Claude/Gemini/DeepSeek/UniAix 等提供商。
     */
    private ChatLanguageModel buildChatModel(LangChain4jProperties.Provider provider, String modelName) {
        String baseUrl = normalizeOpenAiBaseUrl(provider.getBaseUrl());
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(provider.getApiKey())
                .baseUrl(baseUrl)
                .modelName(modelName);

        if (provider.getTemperature() != null) {
            builder.temperature(provider.getTemperature());
        }
        if (provider.getMaxTokens() != null) {
            builder.maxTokens(provider.getMaxTokens());
        }
        applyCommonTimeout(builder, provider);
        applyCommonRetries(builder, provider);

        log.debug("[LangChain4jModelFactory] 构建 Chat 模型: model={}", modelName);
        return builder.build();
    }

    /**
     * 构建 Embedding 模型。
     *
     * 是什么：Embedding 模型构建方法。
     * 做什么：基于 OpenAI 兼容接口创建嵌入模型。
     * 为什么：为 RAG 检索提供统一的 Embedding 能力。
     */
    private EmbeddingModel buildEmbeddingModel(LangChain4jProperties.Provider provider, String modelName) {
        String baseUrl = normalizeOpenAiBaseUrl(provider.getBaseUrl());
        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .apiKey(provider.getApiKey())
                .baseUrl(baseUrl)
                .modelName(modelName);

        if (provider.getEmbeddingDimension() != null) {
            builder.dimensions(provider.getEmbeddingDimension());
        }
        applyCommonTimeout(builder, provider);
        applyCommonRetries(builder, provider);

        log.debug("[LangChain4jModelFactory] 构建 Embedding 模型: model={}", modelName);
        EmbeddingModel primary = new UniaixEmbeddingModelAdapter(builder.build());

        String fallbackModelName = resolveModelName(provider.getEmbeddingFallbackModel(), null);
        if (fallbackModelName == null) {
            return primary;
        }

        Integer primaryDimension = provider.getEmbeddingDimension();
        Integer fallbackDimension = provider.getEmbeddingFallbackDimension() != null
                ? provider.getEmbeddingFallbackDimension()
                : primaryDimension;

        if (primaryDimension != null && fallbackDimension != null && !primaryDimension.equals(fallbackDimension)) {
            log.warn("[LangChain4jModelFactory] Embedding 降级维度不一致，已跳过降级: primaryDim={}, fallbackDim={}",
                    primaryDimension, fallbackDimension);
            return primary;
        }

        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder fallbackBuilder = OpenAiEmbeddingModel.builder()
                .apiKey(provider.getApiKey())
                .baseUrl(baseUrl)
                .modelName(fallbackModelName);

        if (fallbackDimension != null) {
            fallbackBuilder.dimensions(fallbackDimension);
        }
        applyCommonTimeout(fallbackBuilder, provider);
        applyCommonRetries(fallbackBuilder, provider);

        log.warn("[LangChain4jModelFactory] Embedding 启用自动降级: primary={}, fallback={}",
                modelName, fallbackModelName);
        EmbeddingModel fallback = new UniaixEmbeddingModelAdapter(fallbackBuilder.build());
        return new FallbackEmbeddingModel(primary, fallback, modelName, fallbackModelName);
    }

    /**
     * 解析提供商配置。
     *
     * 是什么：提供商解析方法。
     * 做什么：根据 providerKey 获取 Provider 配置。
     * 为什么：统一校验配置合法性。
     */
    private LangChain4jProperties.Provider resolveProvider(String providerKey) {
        if (providerKey == null || providerKey.isBlank()) {
            throw new IllegalStateException("LangChain4j providerKey 不能为空");
        }
        if (properties == null || properties.getProviders() == null) {
            throw new IllegalStateException("LangChain4j providers 未配置");
        }
        LangChain4jProperties.Provider provider = properties.getProviders().get(providerKey);
        if (provider == null) {
            throw new IllegalStateException("LangChain4j 提供商配置缺失: " + providerKey);
        }
        return provider;
    }

    /**
     * 解析模型名称。
     *
     * 是什么：模型名称解析方法。
     * 做什么：优先使用外部传入的模型名称。
     * 为什么：支持路由器动态指定模型。
     */
    private String resolveModelName(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }

    /**
     * 规范化 OpenAI 兼容 baseUrl。
     *
     * 是什么：OpenAI baseUrl 规范化方法。
     * 做什么：确保 baseUrl 包含 /v1 路径段（避免重复追加）。
     * 为什么：OpenAI 兼容接口通常需要 /v1 前缀，但部分网关会使用 /v1/{xxx}/{yyy} 作为根路径。
     *
     * @param baseUrl 原始 baseUrl
     * @return 规范化后的 baseUrl
     */
    private String normalizeOpenAiBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (trimmed.endsWith("/v1") || trimmed.contains("/v1/")) {
            return trimmed;
        }
        return trimmed + "/v1";
    }

    /**
     * 应用通用超时配置。
     *
     * 是什么：超时配置应用方法。
     * 做什么：读取 timeoutSeconds 并设置到模型。
     * 为什么：避免请求无限期等待。
     */
    private void applyCommonTimeout(OpenAiChatModel.OpenAiChatModelBuilder builder,
            LangChain4jProperties.Provider provider) {
        if (provider.getTimeoutSeconds() != null) {
            builder.timeout(Duration.ofSeconds(provider.getTimeoutSeconds()));
        }
    }

    /**
     * 应用通用超时配置。
     *
     * 是什么：超时配置应用方法。
     * 做什么：读取 timeoutSeconds 并设置到模型。
     * 为什么：避免请求无限期等待。
     */
    private void applyCommonTimeout(OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder,
            LangChain4jProperties.Provider provider) {
        if (provider.getTimeoutSeconds() != null) {
            builder.timeout(Duration.ofSeconds(provider.getTimeoutSeconds()));
        }
    }

    /**
     * 应用通用重试配置。
     *
     * 是什么：重试配置应用方法。
     * 做什么：读取 maxRetries 并设置到模型。
     * 为什么：提高网络波动下的调用成功率。
     */
    private void applyCommonRetries(OpenAiChatModel.OpenAiChatModelBuilder builder,
            LangChain4jProperties.Provider provider) {
        if (provider.getMaxRetries() != null) {
            builder.maxRetries(provider.getMaxRetries());
        }
    }

    /**
     * 应用通用重试配置。
     *
     * 是什么：重试配置应用方法。
     * 做什么：读取 maxRetries 并设置到模型。
     * 为什么：提高网络波动下的调用成功率。
     */
    private void applyCommonRetries(OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder,
            LangChain4jProperties.Provider provider) {
        if (provider.getMaxRetries() != null) {
            builder.maxRetries(provider.getMaxRetries());
        }
    }
}
