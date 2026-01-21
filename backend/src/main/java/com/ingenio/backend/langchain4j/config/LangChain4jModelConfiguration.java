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
}
