package com.ingenio.backend.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

/**
 * VectorStore 兜底配置。
 *
 * <p>目的：</p>
 * <ul>
 *   <li>当 Redis Vector Store 未配置或不可用时，避免应用启动失败；</li>
 *   <li>以“空实现”保证 G3 主流程可运行（RAG 自动降级）。</li>
 * </ul>
 */
@Configuration
public class G3VectorStoreFallbackConfig {

    /**
     * 当没有 VectorStore Bean 时，提供空实现。
     */
    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore noopVectorStore() {
        return new NoopVectorStore();
    }

    /**
     * 空 VectorStore 实现（仅用于降级）。
     */
    static class NoopVectorStore implements VectorStore {
        @Override
        public void add(List<Document> documents) {
            // no-op
        }

        @Override
        public Optional<Boolean> delete(List<String> ids) {
            return Optional.of(Boolean.TRUE);
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return List.of();
        }
    }
}
