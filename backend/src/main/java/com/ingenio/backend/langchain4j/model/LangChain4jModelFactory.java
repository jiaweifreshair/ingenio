package com.ingenio.backend.langchain4j.model;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * LangChain4j 模型工厂接口。
 *
 * 是什么：模型构建的统一入口。
 * 做什么：根据提供商配置创建 Chat/Embedding 模型。
 * 为什么：集中管理模型实例与缓存策略。
 */
public interface LangChain4jModelFactory {

    /**
     * 构建 Chat 模型。
     *
     * 是什么：聊天模型构建入口。
     * 做什么：返回可用于对话生成的 ChatLanguageModel。
     * 为什么：不同任务需要统一的模型实例构建逻辑。
     *
     * @param providerKey 提供商标识
     * @param modelName   模型名称（为空时使用提供商默认）
     * @return Chat 模型实例
     */
    ChatLanguageModel chatModel(String providerKey, String modelName);

    /**
     * 构建 Embedding 模型。
     *
     * 是什么：Embedding 模型构建入口。
     * 做什么：返回可用于向量化的 EmbeddingModel。
     * 为什么：为 RAG 检索提供统一的嵌入模型。
     *
     * @param providerKey 提供商标识
     * @return Embedding 模型实例
     */
    EmbeddingModel embeddingModel(String providerKey);
}
