package com.ingenio.backend.langchain4j.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.List;

/**
 * Uniaix Embedding 适配器。
 *
 * 是什么：Uniaix OpenAI 兼容 Embedding 的适配实现。
 * 做什么：将 Embedding 调用结果包装为 LangChain4j 结构。
 * 为什么：在接口差异或扩展参数场景下提供自定义适配能力。
 */
public class UniaixEmbeddingModelAdapter implements EmbeddingModel {

    /**
     * 内部 Embedding 模型。
     *
     * 是什么：实际执行嵌入计算的模型实例。
     * 做什么：承接 LangChain4j OpenAI 兼容实现。
     * 为什么：便于在适配层做统一封装与扩展。
     */
    private final EmbeddingModel delegate;

    /**
     * 构造函数。
     *
     * 是什么：Embedding 适配器初始化入口。
     * 做什么：注入实际的 Embedding 模型。
     * 为什么：便于扩展与统一封装。
     *
     * @param delegate 实际 Embedding 模型
     */
    public UniaixEmbeddingModelAdapter(EmbeddingModel delegate) {
        this.delegate = delegate;
    }

    /**
     * 生成文本向量。
     *
     * 是什么：Embedding 生成入口。
     * 做什么：将文本转换为 Embedding 返回。
     * 为什么：为 RAG 检索提供向量输入。
     *
     * @param text 输入文本
     * @return Embedding 响应
     */
    @Override
    public Response<Embedding> embed(String text) {
        return delegate.embed(text);
    }

    /**
     * 批量生成文本向量。
     *
     * 是什么：批量 Embedding 生成入口。
     * 做什么：将文本片段批量转换为 Embedding。
     * 为什么：提高向量生成吞吐与检索效率。
     *
     * @param segments 文本片段列表
     * @return Embedding 响应
     */
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        return delegate.embedAll(segments);
    }

    /**
     * 获取向量维度。
     *
     * 是什么：Embedding 维度查询方法。
     * 做什么：返回底层模型的向量维度。
     * 为什么：便于向量存储初始化与校验。
     *
     * @return 向量维度
     */
    @Override
    public int dimension() {
        return delegate.dimension();
    }
}
