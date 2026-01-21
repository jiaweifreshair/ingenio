package com.ingenio.backend.langchain4j.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.g3.IArchitectAgent;
import com.ingenio.backend.config.PromptProperties;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.langchain4j.LangChain4jToolRegistry;
import com.ingenio.backend.langchain4j.model.LangChain4jModelFactory;
import com.ingenio.backend.langchain4j.model.LangChain4jModelRouter;
import com.ingenio.backend.prompt.PromptTemplateService;
import com.ingenio.backend.service.blueprint.BlueprintPromptBuilder;
import com.ingenio.backend.service.blueprint.BlueprintValidator;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LangChain4jArchitectAgentImpl 测试。
 *
 * 是什么：架构师 Agent 的单元测试。
 * 做什么：验证契约与 Schema 输出解析。
 * 为什么：确保 LangChain4j 架构流程可执行。
 */
public class LangChain4jArchitectAgentImplTest {

    /**
     * 验证架构设计可返回契约与 Schema。
     *
     * 是什么：架构设计流程测试。
     * 做什么：使用固定模型输出并验证结果。
     * 为什么：避免依赖外部模型调用。
     */
    @Test
    void shouldGenerateContractAndSchema() {
        String contract = "openapi: '3.0.3'\ninfo:\n  title: Demo API\n  version: '1.0.0'\npaths: {}";
        String schema = "CREATE TABLE demo (id UUID PRIMARY KEY);";

        ChatLanguageModel chatModel = new SequenceChatLanguageModel(contract, schema);
        LangChain4jModelFactory modelFactory = new FixedModelFactory(chatModel);
        LangChain4jModelRouter modelRouter = (taskType, attempt, failureContext) ->
                new LangChain4jModelRouter.ModelSelection("test", "test-model");

        PromptTemplateService promptTemplateService = new PromptTemplateService(
                new PromptProperties(),
                new DefaultResourceLoader());

        LangChain4jArchitectAgentImpl agent = new LangChain4jArchitectAgentImpl(
                modelRouter,
                modelFactory,
                new LangChain4jToolRegistry(List.of()),
                promptTemplateService,
                new BlueprintPromptBuilder(new ObjectMapper()),
                new BlueprintValidator(new ObjectMapper()));

        G3JobEntity job = G3JobEntity.builder()
                .id(UUID.randomUUID())
                .requirement("生成 Demo API")
                .build();

        IArchitectAgent.ArchitectResult result = agent.design(job, null);
        assertTrue(result.success());
        assertTrue(result.contractYaml().contains("openapi"));
        assertTrue(result.dbSchemaSql().contains("CREATE TABLE"));
    }

    /**
     * 顺序输出的 Chat 模型。
     *
     * 是什么：测试用 ChatLanguageModel。
     * 做什么：按调用顺序返回预置文本。
     * 为什么：模拟多次调用场景。
     */
    private static class SequenceChatLanguageModel implements ChatLanguageModel {

        private final Deque<String> responses = new ArrayDeque<>();

        /**
         * 构造函数。
         *
         * 是什么：顺序输出模型初始化入口。
         * 做什么：注入预置响应序列。
         * 为什么：模拟多次调用场景。
         *
         * @param responses 预置响应序列
         */
        private SequenceChatLanguageModel(String... responses) {
            this.responses.addAll(List.of(responses));
        }

        /**
         * 按顺序生成响应。
         *
         * 是什么：Chat 模型生成实现。
         * 做什么：返回队列中的下一条响应。
         * 为什么：保证多次调用输出可预测。
         *
         * @param messages 输入消息
         * @return 固定响应
         */
        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            String content = responses.isEmpty() ? "" : responses.removeFirst();
            return Response.from(AiMessage.from(content));
        }

        /**
         * 按顺序生成响应（带工具）。
         *
         * 是什么：带工具的 Chat 模型生成实现。
         * 做什么：返回队列中的下一条响应。
         * 为什么：模拟工具场景下的多次调用。
         *
         * @param messages 输入消息
         * @param toolSpecifications 工具规范
         * @return 固定响应
         */
        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
            String content = responses.isEmpty() ? "" : responses.removeFirst();
            return Response.from(AiMessage.from(content));
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
