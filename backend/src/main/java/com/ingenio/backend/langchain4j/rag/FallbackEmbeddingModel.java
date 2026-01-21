package com.ingenio.backend.langchain4j.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Embedding 降级模型封装。
 *
 * 是什么：带主/备模型的 EmbeddingModel 实现。
 * 做什么：主模型失败时自动切换到备用模型。
 * 为什么：提升 RAG 嵌入生成的成功率与稳定性。
 */
public class FallbackEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(FallbackEmbeddingModel.class);

    /**
     * 主 Embedding 模型。
     *
     * 是什么：首选的嵌入模型实例。
     * 做什么：优先处理 Embedding 请求。
     * 为什么：保证默认使用最优模型。
     */
    private final EmbeddingModel primary;
    /**
     * 备用 Embedding 模型。
     *
     * 是什么：主模型失败后的候选实例。
     * 做什么：在主模型失败时提供降级能力。
     * 为什么：避免嵌入失败导致流程中断。
     */
    private final EmbeddingModel fallback;
    /**
     * 主模型名称。
     *
     * 是什么：主模型标识。
     * 做什么：用于日志与排障定位。
     * 为什么：便于追踪失败来源。
     */
    private final String primaryName;
    /**
     * 备用模型名称。
     *
     * 是什么：备用模型标识。
     * 做什么：用于日志与排障定位。
     * 为什么：便于追踪降级行为。
     */
    private final String fallbackName;

    /**
     * 构造函数。
     *
     * 是什么：降级模型初始化入口。
     * 做什么：注入主模型与备用模型。
     * 为什么：保证 Embedding 调用可自动降级。
     *
     * @param primary 主模型
     * @param fallback 备用模型
     * @param primaryName 主模型名称
     * @param fallbackName 备用模型名称
     */
    public FallbackEmbeddingModel(EmbeddingModel primary,
            EmbeddingModel fallback,
            String primaryName,
            String fallbackName) {
        this.primary = Objects.requireNonNull(primary, "primary embedding model required");
        this.fallback = Objects.requireNonNull(fallback, "fallback embedding model required");
        this.primaryName = primaryName;
        this.fallbackName = fallbackName;
    }

    /**
     * 生成文本向量。
     *
     * 是什么：单条 Embedding 生成入口。
     * 做什么：主模型失败时使用备用模型。
     * 为什么：保证查询向量生成不中断。
     *
     * @param text 输入文本
     * @return Embedding 响应
     */
    @Override
    public Response<Embedding> embed(String text) {
        return withFallback("embed", () -> primary.embed(text), () -> fallback.embed(text));
    }

    /**
     * 批量生成文本向量。
     *
     * 是什么：批量 Embedding 生成入口。
     * 做什么：主模型失败时使用备用模型。
     * 为什么：保证索引构建不中断。
     *
     * @param segments 文本片段
     * @return Embedding 响应
     */
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        return withFallback("embedAll", () -> primary.embedAll(segments), () -> fallback.embedAll(segments));
    }

    /**
     * 获取向量维度。
     *
     * 是什么：Embedding 维度查询方法。
     * 做什么：返回主模型的维度。
     * 为什么：保证与向量存储维度保持一致。
     *
     * @return 向量维度
     */
    @Override
    public int dimension() {
        return primary.dimension();
    }

    /**
     * 执行降级调用。
     *
     * 是什么：主备模型调用封装。
     * 做什么：主模型失败时切换备用模型。
     * 为什么：统一降级策略，避免重复代码。
     *
     * @param action 操作名称
     * @param primaryCall 主模型调用
     * @param fallbackCall 备用模型调用
     * @param <T> 响应类型
     * @return Embedding 响应
     */
    private <T> Response<T> withFallback(String action,
            Supplier<Response<T>> primaryCall,
            Supplier<Response<T>> fallbackCall) {
        try {
            return primaryCall.get();
        } catch (RuntimeException primaryError) {
            log.warn("[FallbackEmbeddingModel] {} 主模型失败，准备降级: primary={}, fallback={}, reason={}",
                    action, primaryName, fallbackName, primaryError.getMessage());
            try {
                return fallbackCall.get();
            } catch (RuntimeException fallbackError) {
                fallbackError.addSuppressed(primaryError);
                throw fallbackError;
            }
        }
    }
}
