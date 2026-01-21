package com.ingenio.backend.langchain4j.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.langchain4j.tool.BlueprintValidatorTool;
import com.ingenio.backend.langchain4j.tool.CompilationValidatorTool;
import com.ingenio.backend.langchain4j.tool.KnowledgeSearchTool;
import com.ingenio.backend.langchain4j.tool.RepairServiceTool;
import com.ingenio.backend.service.CompilationValidator;
import com.ingenio.backend.service.RepairService;
import com.ingenio.backend.service.blueprint.BlueprintValidator;
import com.ingenio.backend.service.g3.G3KnowledgeStorePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * LangChain4j 工具装配配置。
 *
 * 是什么：LangChain4j 工具 Bean 的集中注册配置。
 * 做什么：统一构建工具实例并提供工具列表。
 * 为什么：保证工具注册一致性，避免遗漏。
 */
@Configuration
@ConditionalOnProperty(name = "ingenio.g3.agent.engine", havingValue = "lc4j")
public class LangChain4jToolConfiguration {

    /**
     * 编译验证工具 Bean。
     *
     * 是什么：编译验证工具实例。
     * 做什么：封装 CompilationValidator。
     * 为什么：提供可被 Agent 调用的编译验证能力。
     */
    @Bean
    public CompilationValidatorTool compilationValidatorTool(
            CompilationValidator compilationValidator,
            ObjectMapper objectMapper) {
        return new CompilationValidatorTool(compilationValidator, objectMapper);
    }

    /**
     * 修复工具 Bean。
     *
     * 是什么：修复工具实例。
     * 做什么：封装 RepairService。
     * 为什么：提供可被 Agent 调用的修复能力。
     */
    @Bean
    public RepairServiceTool repairServiceTool(
            RepairService repairService,
            ObjectMapper objectMapper) {
        return new RepairServiceTool(repairService, objectMapper);
    }

    /**
     * 检索工具 Bean。
     *
     * 是什么：知识检索工具实例。
     * 做什么：封装 G3KnowledgeStorePort。
     * 为什么：为 Agent 提供 RAG 检索能力。
     */
    @Bean
    public KnowledgeSearchTool knowledgeSearchTool(
            G3KnowledgeStorePort knowledgeStore,
            ObjectMapper objectMapper) {
        return new KnowledgeSearchTool(knowledgeStore, objectMapper);
    }

    /**
     * Blueprint 校验工具 Bean。
     *
     * 是什么：Blueprint 校验工具实例。
     * 做什么：封装 BlueprintValidator。
     * 为什么：确保输出符合 Blueprint 约束。
     */
    @Bean
    public BlueprintValidatorTool blueprintValidatorTool(
            BlueprintValidator blueprintValidator,
            ObjectMapper objectMapper) {
        return new BlueprintValidatorTool(blueprintValidator, objectMapper);
    }

    /**
     * 工具列表 Bean。
     *
     * 是什么：LangChain4j 工具集合。
     * 做什么：聚合所有工具供 Agent 注入。
     * 为什么：统一工具注册入口。
     *
     * @return 工具列表
     */
    @Bean
    public List<Object> langchain4jTools(
            CompilationValidatorTool compilationValidatorTool,
            RepairServiceTool repairServiceTool,
            KnowledgeSearchTool knowledgeSearchTool,
            BlueprintValidatorTool blueprintValidatorTool) {
        return List.of(
                compilationValidatorTool,
                repairServiceTool,
                knowledgeSearchTool,
                blueprintValidatorTool);
    }
}
