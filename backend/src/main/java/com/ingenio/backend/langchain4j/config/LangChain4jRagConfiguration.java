package com.ingenio.backend.langchain4j.config;

import com.ingenio.backend.config.G3RagProperties;
import com.ingenio.backend.langchain4j.model.LangChain4jModelFactory;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * LangChain4j RAG 配置。
 *
 * 是什么：LangChain4j 向量检索相关 Bean 的配置。
 * 做什么：提供 EmbeddingModel 与 EmbeddingStore。
 * 为什么：支持 Redis 向量检索的渐进式迁移。
 */
@Configuration
@ConditionalOnProperty(name = "ingenio.g3.rag.engine", havingValue = "lc4j")
public class LangChain4jRagConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jRagConfiguration.class);

    /**
     * 构建 Embedding 模型。
     *
     * 是什么：RAG 用 EmbeddingModel Bean。
     * 做什么：根据 embeddingProvider 构建嵌入模型。
     * 为什么：向量检索依赖统一的嵌入模型。
     */
    @Bean
    public EmbeddingModel langChain4jEmbeddingModel(
            LangChain4jModelFactory modelFactory,
            G3RagProperties ragProperties) {
        String providerKey = ragProperties.getEmbeddingProvider();
        return modelFactory.embeddingModel(providerKey);
    }

    /**
     * 构建 EmbeddingStore。
     *
     * 是什么：向量存储 Bean。
     * 做什么：优先使用 Redis，失败时自动降级为内存存储。
     * 为什么：便于本地与生产环境切换。
     */
    @Bean
    public EmbeddingStore<TextSegment> langChain4jEmbeddingStore(
            G3RagProperties ragProperties,
            RedisProperties redisProperties) {
        if (!"redis".equalsIgnoreCase(ragProperties.getEmbeddingStore())) {
            log.info("[LangChain4jRag] EmbeddingStore 使用内存模式");
            return new InMemoryEmbeddingStore<>();
        }

        try {
            RedisEmbeddingStore.Builder builder = RedisEmbeddingStore.builder()
                    .host(redisProperties.getHost())
                    .port(redisProperties.getPort())
                    .indexName(ragProperties.getEmbeddingIndexName())
                    .prefix(ragProperties.getEmbeddingPrefix())
                    .dimension(ragProperties.getEmbeddingDimension())
                    .metadataKeys(defaultMetadataKeys());

            if (redisProperties.getUsername() != null && !redisProperties.getUsername().isBlank()) {
                builder.user(redisProperties.getUsername());
            }
            if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
                builder.password(redisProperties.getPassword());
            }

            log.info("[LangChain4jRag] EmbeddingStore 使用 Redis: {}:{}",
                    redisProperties.getHost(), redisProperties.getPort());
            return builder.build();
        } catch (Exception e) {
            log.warn("[LangChain4jRag] Redis EmbeddingStore 初始化失败，降级内存存储: {}", e.getMessage());
            return new InMemoryEmbeddingStore<>();
        }
    }

    /**
     * 默认的元数据字段列表。
     *
     * 是什么：需要进入向量索引的元数据键。
     * 做什么：保证检索过滤条件可用。
     * 为什么：Redis 向量索引需提前声明可检索字段。
     */
    private List<String> defaultMetadataKeys() {
        return List.of(
                "scope",
                "tenantId",
                "projectId",
                "jobId",
                "filePath",
                "fileName",
                "language",
                "round",
                "contentHash",
                "ref");
    }
}
