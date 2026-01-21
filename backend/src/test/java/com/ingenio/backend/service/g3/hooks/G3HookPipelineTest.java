package com.ingenio.backend.service.g3.hooks;

import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.config.G3HookProperties;
import com.ingenio.backend.entity.g3.G3JobEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G3 Hook 管线单元测试。
 */
class G3HookPipelineTest {

    @Test
    void beforeTool_shouldBlockWhenHandlerBlocks() {
        G3HookProperties properties = new G3HookProperties();
        TestHookHandler handler = new TestHookHandler(true);
        G3HookPipeline pipeline = new G3HookPipeline(properties, List.of(handler));

        G3HookContext context = G3HookContext.builder()
                .eventType(G3HookEventType.BEFORE_TOOL)
                .toolName("shell")
                .toolInput("rm -rf /")
                .build();

        G3HookResult result = pipeline.beforeTool(context);

        assertTrue(result.isBlocked());
        assertEquals(G3HookDecision.BLOCK, result.getDecision());
        assertEquals(1, handler.beforeToolCount.get());
    }

    @Test
    void hookedAiProvider_shouldCallHooks() {
        G3HookProperties properties = new G3HookProperties();
        TestHookHandler handler = new TestHookHandler(false);
        G3HookPipeline pipeline = new G3HookPipeline(properties, List.of(handler));

        AIProvider delegate = new StubProvider();
        G3JobEntity job = G3JobEntity.builder().id(UUID.randomUUID()).build();
        AIProvider provider = new G3HookedAIProvider(delegate, pipeline, properties, job, null);

        AIProvider.AIResponse response = provider.generate(
                "test prompt",
                AIProvider.AIRequest.builder().maxTokens(10).build()
        );

        assertNotNull(response);
        assertEquals("ok", response.content());
        assertEquals(1, handler.beforeModelCount.get());
        assertEquals(1, handler.afterModelCount.get());
    }

    /**
     * Hook 测试处理器。
     */
    private static class TestHookHandler implements G3HookHandler {
        private final boolean shouldBlock;
        private final AtomicInteger beforeToolCount = new AtomicInteger();
        private final AtomicInteger beforeModelCount = new AtomicInteger();
        private final AtomicInteger afterModelCount = new AtomicInteger();

        private TestHookHandler(boolean shouldBlock) {
            this.shouldBlock = shouldBlock;
        }

        @Override
        public G3HookResult beforeTool(G3HookContext context) {
            beforeToolCount.incrementAndGet();
            return shouldBlock ? G3HookResult.block("策略阻断") : G3HookResult.allow();
        }

        @Override
        public G3HookResult beforeModel(G3HookContext context) {
            beforeModelCount.incrementAndGet();
            return G3HookResult.allow();
        }

        @Override
        public void afterModel(G3HookContext context, G3HookResult result) {
            afterModelCount.incrementAndGet();
        }
    }

    /**
     * Stub AIProvider，用于单元测试。
     */
    private static class StubProvider implements AIProvider {
        @Override
        public String getProviderName() {
            return "stub";
        }

        @Override
        public String getProviderDisplayName() {
            return "Stub Provider";
        }

        @Override
        public String getDefaultModel() {
            return "stub-model";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public AIResponse generate(String prompt, AIRequest request) {
            return AIResponse.builder()
                    .content("ok")
                    .model("stub-model")
                    .promptTokens(1)
                    .completionTokens(1)
                    .totalTokens(2)
                    .durationMs(10)
                    .provider("stub")
                    .rawResponse("{}")
                    .build();
        }
    }
}
