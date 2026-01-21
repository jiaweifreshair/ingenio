package com.ingenio.backend.langchain4j.agent;

import com.ingenio.backend.agent.g3.IArchitectAgent;
import com.ingenio.backend.agent.g3.IG3Agent;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.langchain4j.LangChain4jToolRegistry;
import com.ingenio.backend.langchain4j.model.LangChain4jModelFactory;
import com.ingenio.backend.langchain4j.model.LangChain4jModelRouter;
import com.ingenio.backend.prompt.PromptTemplateService;
import com.ingenio.backend.service.blueprint.BlueprintComplianceResult;
import com.ingenio.backend.service.blueprint.BlueprintPromptBuilder;
import com.ingenio.backend.service.blueprint.BlueprintValidator;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * LangChain4j 架构师 Agent 实现。
 *
 * 是什么：基于 LangChain4j 的架构设计代理。
 * 做什么：生成 OpenAPI 契约与数据库 Schema（最小可用骨架）。
 * 为什么：替换旧实现以支持工具调用与结构化输出。
 */
public class LangChain4jArchitectAgentImpl implements IArchitectAgent {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jArchitectAgentImpl.class);

    private static final String AGENT_NAME = "LangChain4jArchitectAgent";
    /**
     * 最大路由尝试次数。
     *
     * 是什么：模型路由的最大尝试次数。
     * 做什么：控制失败时的降级次数。
     * 为什么：避免单次失败导致流程直接中断。
     */
    private static final int MAX_ROUTE_ATTEMPTS = 3;

    /**
     * 系统提示词。
     *
     * 是什么：LangChain4j System Message 内容。
     * 做什么：约束模型输出符合提示词要求的格式。
     * 为什么：保证契约/Schema 可被解析与校验。
     */
    private static final String SYSTEM_MESSAGE = """
            你是 Ingenio G3 的架构师 Agent。
            输出必须符合提示词要求，禁止追加解释说明。
            """;

    /**
     * 模型路由器。
     *
     * 是什么：模型选择策略入口。
     * 做什么：选择本次设计阶段的模型。
     * 为什么：确保高成功率优先。
     */
    private final LangChain4jModelRouter modelRouter;

    /**
     * 模型工厂。
     *
     * 是什么：模型构建入口。
     * 做什么：创建 Chat 模型实例。
     * 为什么：统一 LangChain4j 调用方式。
     */
    private final LangChain4jModelFactory modelFactory;

    /**
     * 工具注册表。
     *
     * 是什么：工具集合容器。
     * 做什么：供 Agent 调用工具。
     * 为什么：统一管理工具清单。
     */
    private final LangChain4jToolRegistry toolRegistry;

    /**
     * Prompt 模版服务。
     *
     * 是什么：Prompt 资产管理服务。
     * 做什么：提供契约与 Schema 生成模板。
     * 为什么：复用现有 Prompt 资产。
     */
    private final PromptTemplateService promptTemplateService;

    /**
     * Blueprint Prompt 构建器。
     *
     * 是什么：Blueprint 约束构建器。
     * 做什么：将 Blueprint 约束注入 Prompt。
     * 为什么：保证生成结果符合 Blueprint。
     */
    private final BlueprintPromptBuilder blueprintPromptBuilder;

    /**
     * Blueprint 校验器。
     *
     * 是什么：Blueprint 合规性校验服务。
     * 做什么：校验 Schema 是否符合 Blueprint。
     * 为什么：保证约束一致性。
     */
    private final BlueprintValidator blueprintValidator;

    /**
     * 构造函数。
     *
     * 是什么：架构师 Agent 初始化入口。
     * 做什么：注入模型路由、工具、Prompt 与 Blueprint 依赖。
     * 为什么：保证架构设计流程可用。
     *
     * @param modelRouter 模型路由器
     * @param modelFactory 模型工厂
     * @param toolRegistry 工具注册表
     * @param promptTemplateService Prompt 模板服务
     * @param blueprintPromptBuilder Blueprint Prompt 构建器
     * @param blueprintValidator Blueprint 校验器
     */
    public LangChain4jArchitectAgentImpl(
            LangChain4jModelRouter modelRouter,
            LangChain4jModelFactory modelFactory,
            LangChain4jToolRegistry toolRegistry,
            PromptTemplateService promptTemplateService,
            BlueprintPromptBuilder blueprintPromptBuilder,
            BlueprintValidator blueprintValidator) {
        this.modelRouter = modelRouter;
        this.modelFactory = modelFactory;
        this.toolRegistry = toolRegistry;
        this.promptTemplateService = promptTemplateService;
        this.blueprintPromptBuilder = blueprintPromptBuilder;
        this.blueprintValidator = blueprintValidator;
    }

    /**
     * 获取 Agent 名称。
     *
     * 是什么：Agent 标识获取方法。
     * 做什么：返回当前 Agent 名称。
     * 为什么：用于日志与追踪。
     */
    @Override
    public String getName() {
        return AGENT_NAME;
    }

    /**
     * 获取 Agent 描述。
     *
     * 是什么：描述信息输出方法。
     * 做什么：返回当前 Agent 的职责描述。
     * 为什么：便于运维与调试识别。
     */
    @Override
    public String getDescription() {
        return "LangChain4j 架构师 Agent - 生成契约与 Schema";
    }

    /**
     * 执行架构任务。
     *
     * 是什么：Agent 执行入口。
     * 做什么：调用设计逻辑并产出产物列表。
     * 为什么：与编排层的统一执行模型对齐。
     */
    @Override
    public List<G3ArtifactEntity> execute(G3JobEntity job, Consumer<G3LogEntry> logConsumer)
            throws IG3Agent.G3AgentException {
        ArchitectResult result = design(job, logConsumer);

        if (!result.success()) {
            throw new IG3Agent.G3AgentException(AGENT_NAME, getRole(), result.errorMessage());
        }

        List<G3ArtifactEntity> artifacts = new ArrayList<>();
        if (result.contractYaml() != null) {
            artifacts.add(G3ArtifactEntity.create(
                    job.getId(),
                    "contracts/openapi.yaml",
                    result.contractYaml(),
                    G3ArtifactEntity.GeneratedBy.ARCHITECT,
                    0));
        }
        if (result.dbSchemaSql() != null) {
            artifacts.add(G3ArtifactEntity.create(
                    job.getId(),
                    "db/schema.sql",
                    result.dbSchemaSql(),
                    G3ArtifactEntity.GeneratedBy.ARCHITECT,
                    0));
        }
        return artifacts;
    }

    /**
     * 进行架构设计。
     *
     * 是什么：架构设计入口。
     * 做什么：生成契约与 Schema 的结果对象。
     * 为什么：为后续编码阶段提供契约基础。
     */
    @Override
    public ArchitectResult design(G3JobEntity job, Consumer<G3LogEntry> logConsumer) {
        if (job == null || job.getRequirement() == null || job.getRequirement().isBlank()) {
            return ArchitectResult.failure("需求为空，无法生成契约");
        }

        LangChain4jModelRouter.FailureContext failureContext = null;
        Exception lastError = null;
        for (int attempt = 0; attempt < MAX_ROUTE_ATTEMPTS; attempt++) {
            LangChain4jModelRouter.ModelSelection selection =
                    modelRouter.select(LangChain4jModelRouter.TaskType.ANALYSIS, attempt, failureContext);
            try {
                ChatLanguageModel model = modelFactory.chatModel(selection.provider(), selection.model());
                ArchitectService service = buildService(model);

                String contractPrompt = buildContractPrompt(job);
                String contractYaml = sanitizeContract(service.generate(contractPrompt));
                if (!validateContract(contractYaml)) {
                    throw new IllegalStateException("OpenAPI 契约校验失败");
                }

                String schemaPrompt = buildSchemaPrompt(job, contractYaml);
                String schemaSql = sanitizeSchema(service.generate(schemaPrompt));
                if (!validateSchema(schemaSql)) {
                    throw new IllegalStateException("数据库 Schema 校验失败");
                }

                if (Boolean.TRUE.equals(job.getBlueprintModeEnabled())) {
                    BlueprintComplianceResult compliance =
                            blueprintValidator.validateSchemaCompliance(schemaSql, job.getBlueprintSpec());
                    if (!compliance.passed()) {
                        throw new IllegalStateException("Blueprint 合规性校验失败: " + compliance.violations());
                    }
                }

                if (logConsumer != null) {
                    logConsumer.accept(G3LogEntry.success(getRole(), "LangChain4j 架构设计完成"));
                }
                return ArchitectResult.success(contractYaml, schemaSql);
            } catch (Exception e) {
                lastError = e;
                failureContext = new LangChain4jModelRouter.FailureContext(
                        selection.provider(), selection.model(), e.getMessage());
                log.warn("[{}] 架构设计失败，尝试降级: provider={}, model={}, reason={}",
                        AGENT_NAME, selection.provider(), selection.model(), e.getMessage());
            }
        }

        String errorMessage = lastError != null ? lastError.getMessage() : "未知错误";
        if (logConsumer != null) {
            logConsumer.accept(G3LogEntry.error(getRole(), "LangChain4j 架构设计失败: " + errorMessage));
        }
        return ArchitectResult.failure("LangChain4j 架构设计失败: " + errorMessage);
    }

    /**
     * 构建 LangChain4j 服务代理。
     *
     * 是什么：AiServices 生成方法。
     * 做什么：绑定模型、记忆与工具，返回服务接口。
     * 为什么：统一 LangChain4j 调用方式。
     *
     * @param model Chat 模型
     * @return 架构师服务代理
     */
    private ArchitectService buildService(ChatLanguageModel model) {
        return AiServices.builder(ArchitectService.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .systemMessageProvider(ignore -> SYSTEM_MESSAGE)
                .tools(toolRegistry.tools())
                .build();
    }

    /**
     * 构建契约生成 Prompt。
     *
     * 是什么：契约 Prompt 组装方法。
     * 做什么：将需求与 Blueprint 约束注入模板。
     * 为什么：确保契约生成符合需求与约束。
     *
     * @param job G3 任务
     * @return 契约 Prompt
     */
    private String buildContractPrompt(G3JobEntity job) {
        String template = promptTemplateService.architectContractTemplate();
        String prompt = String.format(template, job.getRequirement());
        if (Boolean.TRUE.equals(job.getBlueprintModeEnabled())) {
            String constraint = blueprintPromptBuilder.buildArchitectConstraint(job.getBlueprintSpec());
            if (constraint != null && !constraint.isBlank()) {
                prompt = prompt + "\n" + constraint;
            }
        }
        return prompt;
    }

    /**
     * 构建 Schema 生成 Prompt。
     *
     * 是什么：Schema Prompt 组装方法。
     * 做什么：将需求、契约与 Blueprint 约束注入模板。
     * 为什么：确保 Schema 生成与契约一致。
     *
     * @param job G3 任务
     * @param contractYaml OpenAPI 契约
     * @return Schema Prompt
     */
    private String buildSchemaPrompt(G3JobEntity job, String contractYaml) {
        String template = promptTemplateService.architectSchemaTemplate();
        String prompt = String.format(template, job.getRequirement(), contractYaml);
        if (Boolean.TRUE.equals(job.getBlueprintModeEnabled())) {
            String constraint = blueprintPromptBuilder.buildArchitectConstraint(job.getBlueprintSpec());
            if (constraint != null && !constraint.isBlank()) {
                prompt = prompt + "\n" + constraint;
            }
        }
        return prompt;
    }

    /**
     * 清洗契约输出。
     *
     * 是什么：OpenAPI 输出清洗方法。
     * 做什么：去除代码围栏并截取 openapi 开头。
     * 为什么：保证契约格式可被校验。
     *
     * @param raw 原始输出
     * @return 清洗后的契约文本
     */
    private String sanitizeContract(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = stripCodeFence(raw);
        int index = cleaned.indexOf("openapi:");
        if (index >= 0) {
            return cleaned.substring(index).trim();
        }
        return cleaned.trim();
    }

    /**
     * 清洗 Schema 输出。
     *
     * 是什么：Schema SQL 输出清洗方法。
     * 做什么：去除代码围栏并截取 CREATE TABLE 开头。
     * 为什么：避免无关文本影响校验与执行。
     *
     * @param raw 原始输出
     * @return 清洗后的 Schema SQL
     */
    private String sanitizeSchema(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = stripCodeFence(raw);
        int index = cleaned.toLowerCase().indexOf("create table");
        if (index >= 0) {
            return cleaned.substring(index).trim();
        }
        return cleaned.trim();
    }

    /**
     * 去除代码围栏。
     *
     * 是什么：Markdown 代码块清理方法。
     * 做什么：移除 ```yaml/```sql/``` 标记。
     * 为什么：避免解析器误判内容。
     *
     * @param raw 原始文本
     * @return 清理后的文本
     */
    private String stripCodeFence(String raw) {
        return raw.replace("```yaml", "")
                .replace("```sql", "")
                .replace("```", "")
                .trim();
    }

    /**
     * 验证契约格式。
     *
     * 是什么：OpenAPI 契约校验入口。
     * 做什么：使用 YAML 解析并检查关键字段。
     * 为什么：避免无效契约进入编码阶段。
     */
    @Override
    public boolean validateContract(String contractYaml) {
        if (contractYaml == null || contractYaml.isBlank()) {
            return false;
        }
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(contractYaml);
            return data.containsKey("openapi") && data.containsKey("info") && data.containsKey("paths");
        } catch (Exception e) {
            log.warn("[{}] 契约校验失败: {}", AGENT_NAME, e.getMessage());
            return false;
        }
    }

    /**
     * 验证 Schema 格式。
     *
     * 是什么：SQL Schema 校验入口。
     * 做什么：检查 CREATE TABLE 与 PRIMARY KEY。
     * 为什么：保证基础结构合法可编译。
     */
    @Override
    public boolean validateSchema(String schemaSql) {
        if (schemaSql == null || schemaSql.isBlank()) {
            return false;
        }
        String upper = schemaSql.toUpperCase();
        return upper.contains("CREATE TABLE") && upper.contains("PRIMARY KEY");
    }

    /**
     * LangChain4j 文本生成接口。
     *
     * 是什么：AiServices 使用的接口契约。
     * 做什么：承载单次 Prompt 输出。
     * 为什么：统一工具与系统消息接入。
     */
    private interface ArchitectService {
        /**
         * 执行生成。
         *
         * 是什么：文本生成调用入口。
         * 做什么：向模型发送 Prompt 并返回输出。
         * 为什么：统一 Agent 生成调用方式。
         *
         * @param prompt Prompt 文本
         * @return 模型输出
         */
        @UserMessage("{{it}}")
        String generate(String prompt);
    }
}
