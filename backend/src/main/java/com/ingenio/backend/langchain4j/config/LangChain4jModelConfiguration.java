package com.ingenio.backend.langchain4j.config;

import com.ingenio.backend.langchain4j.model.LangChain4jModelFactory;
import com.ingenio.backend.langchain4j.model.LangChain4jModelFactoryImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 模型工厂配置。
 *
 * 是什么：模型工厂的 Spring 装配入口。
 * 做什么：提供 LangChain4jModelFactory Bean。
 * 为什么：在 Agent/RAG 场景中统一模型构建逻辑。
 */
@Configuration
@EnableConfigurationProperties(LangChain4jProperties.class)
public class LangChain4jModelConfiguration {

    /**
     * 模型工厂 Bean。
     *
     * 是什么：LangChain4jModelFactory 实例。
     * 做什么：创建 Chat/Embedding 模型。
     * 为什么：统一模型构建与缓存策略。
     *
     * @param properties LangChain4j 配置
     * @return 模型工厂
     */
    @Bean
    public LangChain4jModelFactory langChain4jModelFactory(LangChain4jProperties properties) {
        return new LangChain4jModelFactoryImpl(properties);
    }

    /**
     * 默认的 ChatLanguageModel Bean。
     *
     * 是什么：ChatLanguageModel 实例，用于直接注入的场景。
     * 做什么：创建代码生成场景的默认模型（Claude）。
     * 为什么：支持不使用工厂模式直接注入 ChatLanguageModel 的组件（如 FrontendApiClientGenerator）。
     *
     * @param factory 模型工厂
     * @return ChatLanguageModel 实例
     */
    @Bean
    public dev.langchain4j.model.chat.ChatLanguageModel chatLanguageModel(
            LangChain4jModelFactory factory) {
        // 使用 claude 作为默认提供商，null 表示使用提供商的默认模型
        return factory.chatModel("claude", null);
    }
}
