package com.ingenio.backend.langchain4j.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * FallbackEmbeddingModel 测试。
 *
 * 是什么：降级嵌入模型的单元测试。
 * 做什么：验证主模型失败时的自动降级行为。
 * 为什么：确保 RAG 嵌入过程具备稳定性。
 */
class FallbackEmbeddingModelTest {

    @Test
    void shouldFallbackWhenPrimaryEmbedFails() {
        RecordingEmbeddingModel primary = new RecordingEmbeddingModel(true);
        RecordingEmbeddingModel fallback = new RecordingEmbeddingModel(false);
        FallbackEmbeddingModel model = new FallbackEmbeddingModel(primary, fallback, "primary", "fallback");

        Response<Embedding> response = model.embed("hello");

        assertNotNull(response);
        assertEquals(1, primary.getEmbedCalls());
        assertEquals(1, fallback.getEmbedCalls());
    }

    @Test
    void shouldUsePrimaryWhenEmbedSucceeds() {
        RecordingEmbeddingModel primary = new RecordingEmbeddingModel(false);
        RecordingEmbeddingModel fallback = new RecordingEmbeddingModel(false);
        FallbackEmbeddingModel model = new FallbackEmbeddingModel(primary, fallback, "primary", "fallback");

        Response<Embedding> response = model.embed("hello");

        assertNotNull(response);
        assertEquals(1, primary.getEmbedCalls());
        assertEquals(0, fallback.getEmbedCalls());
    }

    @Test
    void shouldFallbackWhenEmbedAllFails() {
        RecordingEmbeddingModel primary = new RecordingEmbeddingModel(true);
        RecordingEmbeddingModel fallback = new RecordingEmbeddingModel(false);
        FallbackEmbeddingModel model = new FallbackEmbeddingModel(primary, fallback, "primary", "fallback");

        Response<List<Embedding>> response = model.embedAll(List.of(TextSegment.from("hello")));

        assertNotNull(response);
        assertEquals(1, primary.getEmbedAllCalls());
        assertEquals(1, fallback.getEmbedAllCalls());
    }

    /**
     * 记录调用次数的嵌入模型。
     *
     * 是什么：可配置失败与统计的 EmbeddingModel。
     * 做什么：在测试中统计主/备模型调用次数。
     * 为什么：验证降级逻辑是否生效。
     */
    private static class RecordingEmbeddingModel implements EmbeddingModel {
        /**
         * 是否强制失败。
         *
         * 是什么：调用失败开关。
         * 做什么：控制 embed/embedAll 是否抛异常。
         * 为什么：模拟主模型失败场景。
         */
        private final boolean fail;
        /**
         * 单条调用次数。
         *
         * 是什么：embed 调用计数。
         * 做什么：记录调用次数。
         * 为什么：验证降级行为。
         */
        private int embedCalls;
        /**
         * 批量调用次数。
         *
         * 是什么：embedAll 调用计数。
         * 做什么：记录调用次数。
         * 为什么：验证降级行为。
         */
        private int embedAllCalls;

        /**
         * 构造函数。
         *
         * 是什么：记录模型初始化入口。
         * 做什么：设置是否失败的行为。
         * 为什么：按需模拟主/备模型表现。
         *
         * @param fail 是否失败
         */
        private RecordingEmbeddingModel(boolean fail) {
            this.fail = fail;
        }

        /**
         * 单条 Embedding 调用。
         *
         * 是什么：embed 测试实现。
         * 做什么：按开关返回结果或抛异常。
         * 为什么：模拟主模型成功/失败分支。
         *
         * @param text 输入文本
         * @return Embedding 响应
         */
        @Override
        public Response<Embedding> embed(String text) {
            embedCalls++;
            if (fail) {
                throw new RuntimeException("primary embed failed");
            }
            return Response.from(new Embedding(new float[] {1f, 0f}));
        }

        /**
         * 批量 Embedding 调用。
         *
         * 是什么：embedAll 测试实现。
         * 做什么：按开关返回结果或抛异常。
         * 为什么：模拟主模型失败场景。
         *
         * @param segments 文本片段
         * @return Embedding 响应
         */
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            embedAllCalls++;
            if (fail) {
                throw new RuntimeException("primary embedAll failed");
            }
            List<Embedding> embeddings = segments.stream()
                    .map(segment -> new Embedding(new float[] {1f, 0f}))
                    .toList();
            return Response.from(embeddings);
        }

        /**
         * 返回向量维度。
         *
         * 是什么：Embedding 维度实现。
         * 做什么：返回固定向量维度。
         * 为什么：满足接口契约。
         *
         * @return 向量维度
         */
        @Override
        public int dimension() {
            return 2;
        }

        /**
         * 获取 embed 调用次数。
         *
         * 是什么：次数查询方法。
         * 做什么：暴露 embed 调用次数。
         * 为什么：便于断言降级行为。
         *
         * @return embed 调用次数
         */
        private int getEmbedCalls() {
            return embedCalls;
        }

        /**
         * 获取 embedAll 调用次数。
         *
         * 是什么：次数查询方法。
         * 做什么：暴露 embedAll 调用次数。
         * 为什么：便于断言降级行为。
         *
         * @return embedAll 调用次数
         */
        private int getEmbedAllCalls() {
            return embedAllCalls;
        }
    }
}
