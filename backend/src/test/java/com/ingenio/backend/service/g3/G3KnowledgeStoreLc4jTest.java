package com.ingenio.backend.service.g3;

import com.ingenio.backend.config.G3RagProperties;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G3KnowledgeStoreLc4j 测试。
 *
 * 是什么：LangChain4j 知识库实现的单元测试。
 * 做什么：验证索引写入与语义检索基本流程。
 * 为什么：确保 LC4J RAG 的最小能力可用。
 */
public class G3KnowledgeStoreLc4jTest {

    /**
     * 验证写入后可以检索到结果。
     *
     * 是什么：索引与检索的基础验证。
     * 做什么：写入示例产物并执行检索。
     * 为什么：确保 EmbeddingStore 写入与查询正常。
     */
    @Test
    void shouldIngestAndSearch() {
        EmbeddingModel embeddingModel = new FixedEmbeddingModel();
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        G3RagProperties ragProperties = new G3RagProperties();
        ragProperties.setChunkSize(200);
        ragProperties.setChunkOverlap(0);

        G3KnowledgeStoreLc4j knowledgeStore = new G3KnowledgeStoreLc4j(
                embeddingModel,
                embeddingStore,
                ragProperties);

        UUID jobId = UUID.randomUUID();
        G3JobEntity job = G3JobEntity.builder()
                .id(jobId)
                .currentRound(0)
                .build();

        G3ArtifactEntity artifact = G3ArtifactEntity.create(
                jobId,
                "src/main/java/com/demo/Demo.java",
                "package com.demo;\npublic class Demo {}",
                G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                0);

        knowledgeStore.ingest(job, List.of(artifact));

        List<?> results = knowledgeStore.search("Demo", jobId, 3);
        assertFalse(results.isEmpty());

        String context = knowledgeStore.formatForContext(results);
        assertTrue(context.contains("Demo.java"));
    }

    /**
     * 验证 Embedding 失败时不抛出异常。
     *
     * 是什么：Embedding 失败容错测试。
     * 做什么：模拟 Embedding 抛异常并确保流程不中断。
     * 为什么：避免外部 Embedding 不可用导致 G3 失败。
     */
    @Test
    void shouldIgnoreEmbeddingFailureOnIngest() {
        EmbeddingModel embeddingModel = new FailingEmbeddingModel();
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        G3RagProperties ragProperties = new G3RagProperties();

        G3KnowledgeStoreLc4j knowledgeStore = new G3KnowledgeStoreLc4j(
                embeddingModel,
                embeddingStore,
                ragProperties);

        UUID jobId = UUID.randomUUID();
        G3JobEntity job = G3JobEntity.builder()
                .id(jobId)
                .currentRound(0)
                .build();

        G3ArtifactEntity artifact = G3ArtifactEntity.create(
                jobId,
                "src/main/java/com/demo/Failing.java",
                "package com.demo;\npublic class Failing {}",
                G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                0);

        assertDoesNotThrow(() -> knowledgeStore.ingest(job, List.of(artifact)));
        assertTrue(knowledgeStore.search("Failing", jobId, 3).isEmpty());
    }

    /**
     * 固定 Embedding 模型。
     *
     * 是什么：测试用嵌入模型实现。
     * 做什么：返回固定向量，保证检索稳定性。
     * 为什么：避免依赖外部 Embedding 服务。
     */
    private static class FixedEmbeddingModel implements EmbeddingModel {

        /**
         * 批量生成固定向量。
         *
         * 是什么：Embedding 批量生成实现。
         * 做什么：为每个片段返回固定向量。
         * 为什么：确保检索测试稳定可复现。
         *
         * @param segments 文本片段
         * @return 固定向量响应
         */
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            List<Embedding> embeddings = segments.stream()
                    .map(segment -> new Embedding(new float[] {1f, 0f, 0f}))
                    .toList();
            return Response.from(embeddings);
        }
    }

    /**
     * 失败的 Embedding 模型。
     *
     * 是什么：测试用 Embedding 失败模型。
     * 做什么：调用即抛出异常。
     * 为什么：验证 ingest 失败时的容错逻辑。
     */
    private static class FailingEmbeddingModel implements EmbeddingModel {

        /**
         * 批量生成向量时抛异常。
         *
         * 是什么：Embedding 抛异常实现。
         * 做什么：直接抛出运行时异常。
         * 为什么：模拟外部 Embedding 服务失败。
         *
         * @param segments 文本片段
         * @return 不返回结果
         */
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            throw new RuntimeException("Embedding service unavailable");
        }
    }
}
