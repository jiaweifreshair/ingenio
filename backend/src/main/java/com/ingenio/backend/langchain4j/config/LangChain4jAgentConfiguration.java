package com.ingenio.backend.langchain4j.config;

import com.ingenio.backend.agent.g3.IArchitectAgent;
import com.ingenio.backend.agent.g3.ICoachAgent;
import com.ingenio.backend.agent.g3.ICoderAgent;
import com.ingenio.backend.langchain4j.LangChain4jToolRegistry;
import com.ingenio.backend.langchain4j.agent.LangChain4jArchitectAgentImpl;
import com.ingenio.backend.langchain4j.agent.LangChain4jBackendCoderAgentImpl;
import com.ingenio.backend.langchain4j.agent.LangChain4jCoachAgentImpl;
import com.ingenio.backend.langchain4j.model.LangChain4jModelFactory;
import com.ingenio.backend.langchain4j.model.LangChain4jModelRouter;
import com.ingenio.backend.langchain4j.model.LangChain4jModelRouterImpl;
import com.ingenio.backend.prompt.PromptTemplateService;
import com.ingenio.backend.service.blueprint.BlueprintPromptBuilder;
import com.ingenio.backend.service.blueprint.BlueprintValidator;
import com.ingenio.backend.service.g3.G3ContextBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * LangChain4j Agent 装配配置。
 *
 * 是什么：LangChain4j Agent 的 Spring 配置入口。
 * 做什么：在 engine=lc4j 时注册模型路由与 Agent Bean。
 * 为什么：支持配置开关与灰度迁移。
 */
@Configuration
@ConditionalOnProperty(name = "ingenio.g3.agent.engine", havingValue = "lc4j")
@EnableConfigurationProperties(LangChain4jProperties.class)
public class LangChain4jAgentConfiguration {

    /**
     * 模型路由器 Bean。
     *
     * 是什么：LangChain4j 模型选择器实例。
     * 做什么：提供模型路由逻辑。
     * 为什么：集中管理模型优先级。
     *
     * @param properties LangChain4j 配置
     * @return 模型路由器
     */
    @Bean
    public LangChain4jModelRouter langChain4jModelRouter(LangChain4jProperties properties) {
        return new LangChain4jModelRouterImpl(properties);
    }

    /**
     * 工具注册表 Bean。
     *
     * 是什么：LangChain4j 工具集合容器。
     * 做什么：聚合工具列表并注入 Agent。
     * 为什么：避免工具注册分散导致遗漏。
     *
     * @param tools 工具列表
     * @return 工具注册表
     */
    @Bean
    public LangChain4jToolRegistry langChain4jToolRegistry(
            @Qualifier("langchain4jTools") List<Object> tools) {
        return new LangChain4jToolRegistry(tools);
    }

    /**
     * LangChain4j 架构师 Agent。
     *
     * 是什么：架构师 Agent 实例。
     * 做什么：生成契约与 Schema（骨架实现）。
     * 为什么：为后续迁移提供落地点。
     */
    @Bean
    public IArchitectAgent langChain4jArchitectAgent(
            LangChain4jModelRouter modelRouter,
            LangChain4jModelFactory modelFactory,
            LangChain4jToolRegistry toolRegistry,
            PromptTemplateService promptTemplateService,
            BlueprintPromptBuilder blueprintPromptBuilder,
            BlueprintValidator blueprintValidator) {
        return new LangChain4jArchitectAgentImpl(
                modelRouter,
                modelFactory,
                toolRegistry,
                promptTemplateService,
                blueprintPromptBuilder,
                blueprintValidator);
    }

    /**
     * LangChain4j 后端编码 Agent。
     *
     * 是什么：后端编码 Agent 实例。
     * 做什么：生成后端代码产物（骨架实现）。
     * 为什么：为后续工具编排实现做准备。
     */
    @Bean
    public ICoderAgent langChain4jBackendCoderAgent(
            LangChain4jModelRouter modelRouter,
            LangChain4jModelFactory modelFactory,
            LangChain4jToolRegistry toolRegistry,
            PromptTemplateService promptTemplateService,
            BlueprintPromptBuilder blueprintPromptBuilder,
            G3ContextBuilder contextBuilder,
            ObjectMapper objectMapper) {
        return new LangChain4jBackendCoderAgentImpl(
                modelRouter,
                modelFactory,
                toolRegistry,
                promptTemplateService,
                blueprintPromptBuilder,
                contextBuilder,
                objectMapper);
    }

    /**
     * LangChain4j 教练 Agent。
     *
     * 是什么：修复 Agent 实例。
     * 做什么：执行错误修复（骨架实现）。
     * 为什么：为修复闭环迁移提供入口。
     */
    @Bean
    public ICoachAgent langChain4jCoachAgent(
            LangChain4jModelRouter modelRouter,
            LangChain4jModelFactory modelFactory,
            LangChain4jToolRegistry toolRegistry,
            PromptTemplateService promptTemplateService,
            G3ContextBuilder contextBuilder) {
        return new LangChain4jCoachAgentImpl(
                modelRouter,
                modelFactory,
                toolRegistry,
                promptTemplateService,
                contextBuilder);
    }
}
