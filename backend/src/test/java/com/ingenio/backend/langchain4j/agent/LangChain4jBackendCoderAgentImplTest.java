package com.ingenio.backend.langchain4j.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.g3.ICoderAgent;
import com.ingenio.backend.config.PromptProperties;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.langchain4j.LangChain4jToolRegistry;
import com.ingenio.backend.langchain4j.model.LangChain4jModelFactory;
import com.ingenio.backend.langchain4j.model.LangChain4jModelRouter;
import com.ingenio.backend.prompt.PromptTemplateService;
import com.ingenio.backend.service.blueprint.BlueprintPromptBuilder;
import com.ingenio.backend.service.g3.G3ContextBuilder;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * LangChain4jBackendCoderAgentImpl 测试。
 *
 * 是什么：后端编码 Agent 的单元测试。
 * 做什么：验证 JSON 输出解析与产物生成。
 * 为什么：确保 LangChain4j 生成链路可用。
 */
public class LangChain4jBackendCoderAgentImplTest {

    /**
     * 验证生成结果可解析为产物。
     *
     * 是什么：基本生成流程测试。
     * 做什么：使用固定模型输出 JSON 并解析。
     * 为什么：保证 Agent 不依赖真实模型也能运行。
     */
    @Test
    void shouldGenerateArtifactsFromJson() {
        String json = "{\"artifacts\":[{\"filePath\":\"src/main/java/com/demo/Demo.java\"," +
                "\"content\":\"package com.demo;\\npublic class Demo {}\"," +
                "\"language\":\"java\"}]}";

        ChatLanguageModel chatModel = new FixedChatLanguageModel(json);
        LangChain4jModelFactory modelFactory = new FixedModelFactory(chatModel);
        LangChain4jModelRouter modelRouter = (taskType, attempt, failureContext) ->
                new LangChain4jModelRouter.ModelSelection("test", "test-model");

        PromptTemplateService promptTemplateService = new PromptTemplateService(
                new PromptProperties(),
                new DefaultResourceLoader());

        BlueprintPromptBuilder blueprintPromptBuilder = new BlueprintPromptBuilder(new ObjectMapper());
        G3ContextBuilder contextBuilder = Mockito.mock(G3ContextBuilder.class);
        when(contextBuilder.buildGlobalContext(any())).thenReturn("");

        LangChain4jBackendCoderAgentImpl agent = new LangChain4jBackendCoderAgentImpl(
                modelRouter,
                modelFactory,
                new LangChain4jToolRegistry(List.of()),
                promptTemplateService,
                blueprintPromptBuilder,
                contextBuilder,
                new ObjectMapper());

        G3JobEntity job = G3JobEntity.builder()
                .id(UUID.randomUUID())
                .requirement("生成一个 Demo 服务")
                .contractYaml("openapi: '3.0.3'\ninfo:\n  title: Demo API\n  version: '1.0.0'\npaths: {}")
                .dbSchemaSql("CREATE TABLE demo (id UUID PRIMARY KEY);")
                .currentRound(0)
                .build();

        ICoderAgent.CoderResult result = agent.generate(job, 0, null);
        assertTrue(result.success());
        assertEquals(1, result.artifacts().size());
        assertEquals("src/main/java/com/demo/Demo.java", result.artifacts().get(0).getFilePath());
    }

    /**
     * 验证 Gemini/DeepSeek 的 JSON 修复可用。
     *
     * 是什么：JSON 修复兜底测试。
     * 做什么：模拟首次输出非 JSON，修复后成功解析。
     * 为什么：避免模型输出不规范导致编码阶段失败。
     */
    @Test
    void shouldRepairInvalidJsonForGemini() {
        String invalid = "不是JSON输出";
        String json = "{\"artifacts\":[{\"filePath\":\"src/main/java/com/demo/Fixed.java\"," +
                "\"content\":\"package com.demo;\\npublic class Fixed {}\"," +
                "\"language\":\"java\"}]}";

        ChatLanguageModel chatModel = new SequenceChatLanguageModel(invalid, json);
        LangChain4jModelFactory modelFactory = new FixedModelFactory(chatModel);
        LangChain4jModelRouter modelRouter = (taskType, attempt, failureContext) ->
                new LangChain4jModelRouter.ModelSelection("gemini", "test-model");

        PromptTemplateService promptTemplateService = new PromptTemplateService(
                new PromptProperties(),
                new DefaultResourceLoader());

        BlueprintPromptBuilder blueprintPromptBuilder = new BlueprintPromptBuilder(new ObjectMapper());
        G3ContextBuilder contextBuilder = Mockito.mock(G3ContextBuilder.class);
        when(contextBuilder.buildGlobalContext(any())).thenReturn("");

        LangChain4jBackendCoderAgentImpl agent = new LangChain4jBackendCoderAgentImpl(
                modelRouter,
                modelFactory,
                new LangChain4jToolRegistry(List.of()),
                promptTemplateService,
                blueprintPromptBuilder,
                contextBuilder,
                new ObjectMapper());

        G3JobEntity job = G3JobEntity.builder()
                .id(UUID.randomUUID())
                .requirement("生成一个 Fixed 服务")
                .contractYaml("openapi: '3.0.3'\ninfo:\n  title: Fixed API\n  version: '1.0.0'\npaths: {}")
                .dbSchemaSql("CREATE TABLE fixed (id UUID PRIMARY KEY);")
                .currentRound(0)
                .build();

        ICoderAgent.CoderResult result = agent.generate(job, 0, null);
        assertTrue(result.success());
        assertEquals(1, result.artifacts().size());
        assertEquals("src/main/java/com/demo/Fixed.java", result.artifacts().get(0).getFilePath());
    }

    /**
     * 固定 Chat 模型。
     *
     * 是什么：测试用 ChatLanguageModel。
     * 做什么：返回固定 JSON 文本。
     * 为什么：隔离外部模型依赖。
     */
    private static class FixedChatLanguageModel implements ChatLanguageModel {

        private final String content;

        /**
         * 构造函数。
         *
         * 是什么：固定模型初始化入口。
         * 做什么：注入固定输出内容。
         * 为什么：便于测试控制输出。
         *
         * @param content 固定输出内容
         */
        private FixedChatLanguageModel(String content) {
            this.content = content;
        }

        /**
         * 生成固定响应。
         *
         * 是什么：Chat 模型生成实现。
         * 做什么：返回固定的 AI Message。
         * 为什么：保证测试可预测。
         *
         * @param messages 输入消息
         * @return 固定响应
         */
        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return Response.from(AiMessage.from(content));
        }

        /**
         * 生成固定响应（带工具）。
         *
         * 是什么：带工具的 Chat 模型生成实现。
         * 做什么：返回固定的 AI Message。
         * 为什么：模拟工具场景下的输出。
         *
         * @param messages 输入消息
         * @param toolSpecifications 工具规范
         * @return 固定响应
         */
        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
            return Response.from(AiMessage.from(content));
        }
    }

    /**
     * 顺序返回的 Chat 模型。
     *
     * 是什么：按调用顺序返回输出的测试模型。
     * 做什么：先返回非 JSON，再返回修复 JSON。
     * 为什么：模拟 Gemini/DeepSeek 输出修复流程。
     */
    private static class SequenceChatLanguageModel implements ChatLanguageModel {

        private final List<String> responses;
        private int index = 0;

        /**
         * 构造函数。
         *
         * 是什么：顺序模型初始化入口。
         * 做什么：注入顺序输出内容列表。
         * 为什么：便于控制每次调用返回值。
         *
         * @param responses 顺序输出内容
         */
        private SequenceChatLanguageModel(String... responses) {
            this.responses = List.of(responses);
        }

        /**
         * 生成顺序响应。
         *
         * 是什么：无工具生成实现。
         * 做什么：按调用顺序返回输出。
         * 为什么：模拟不同阶段的模型响应。
         *
         * @param messages 输入消息
         * @return 顺序响应
         */
        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return Response.from(AiMessage.from(nextContent()));
        }

        /**
         * 生成顺序响应（带工具）。
         *
         * 是什么：带工具生成实现。
         * 做什么：按调用顺序返回输出。
         * 为什么：确保工具场景一致。
         *
         * @param messages 输入消息
         * @param toolSpecifications 工具规范
         * @return 顺序响应
         */
        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
            return Response.from(AiMessage.from(nextContent()));
        }

        /**
         * 获取下一段输出。
         *
         * 是什么：顺序输出的取值方法。
         * 做什么：按调用顺序返回预设文本。
         * 为什么：确保修复流程的输出可控。
         *
         * @return 输出内容
         */
        private String nextContent() {
            if (responses.isEmpty()) {
                return "";
            }
            int safeIndex = Math.min(index, responses.size() - 1);
            String content = responses.get(safeIndex);
            index++;
            return content;
        }
    }

    /**
     * 固定模型工厂。
     *
     * 是什么：测试用模型工厂实现。
     * 做什么：返回固定 Chat 模型。
     * 为什么：避免真实模型构建。
     */
    private static class FixedModelFactory implements LangChain4jModelFactory {

        private final ChatLanguageModel chatModel;

        /**
         * 构造函数。
         *
         * 是什么：固定模型工厂初始化入口。
         * 做什么：注入固定 Chat 模型。
         * 为什么：避免真实模型构建。
         *
         * @param chatModel 固定 Chat 模型
         */
        private FixedModelFactory(ChatLanguageModel chatModel) {
            this.chatModel = chatModel;
        }

        /**
         * 返回固定 Chat 模型。
         *
         * 是什么：模型工厂输出方法。
         * 做什么：忽略参数返回固定模型。
         * 为什么：保证测试可控。
         *
         * @param providerKey 提供商标识
         * @param modelName 模型名称
         * @return 固定 Chat 模型
         */
        @Override
        public ChatLanguageModel chatModel(String providerKey, String modelName) {
            return chatModel;
        }

        /**
         * Embedding 模型占位。
         *
         * 是什么：Embedding 模型输出方法。
         * 做什么：抛出未使用异常。
         * 为什么：测试场景不需要 Embedding。
         *
         * @param providerKey 提供商标识
         * @return Embedding 模型
         */
        @Override
        public dev.langchain4j.model.embedding.EmbeddingModel embeddingModel(String providerKey) {
            throw new UnsupportedOperationException("测试未使用 EmbeddingModel");
        }
    }
}
