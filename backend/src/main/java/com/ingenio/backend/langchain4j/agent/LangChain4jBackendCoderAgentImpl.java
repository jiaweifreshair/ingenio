package com.ingenio.backend.langchain4j.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.g3.ICoderAgent;
import com.ingenio.backend.agent.g3.IG3Agent;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.langchain4j.LangChain4jToolRegistry;
import com.ingenio.backend.langchain4j.model.LangChain4jModelFactory;
import com.ingenio.backend.langchain4j.model.LangChain4jModelRouter;
import com.ingenio.backend.prompt.PromptTemplateService;
import com.ingenio.backend.service.blueprint.BlueprintPromptBuilder;
import com.ingenio.backend.service.g3.G3ContextBuilder;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * LangChain4j 后端编码 Agent 实现。
 *
 * 是什么：基于 LangChain4j 的后端代码生成代理。
 * 做什么：生成实体、Mapper、DTO、Service、Controller（最小可用骨架）。
 * 为什么：替换旧实现以接入工具编排与结构化输出。
 */
public class LangChain4jBackendCoderAgentImpl implements ICoderAgent {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jBackendCoderAgentImpl.class);

    private static final String AGENT_NAME = "LangChain4jBackendCoderAgent";
    private static final String TARGET_TYPE = "backend";
    private static final String TARGET_LANGUAGE = "java";
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
     * 做什么：约束模型只输出 JSON 结构。
     * 为什么：保证输出可解析为产物列表。
     */
    private static final String SYSTEM_MESSAGE = """
            你是 Ingenio G3 的后端代码生成 Agent。
            必须输出严格的 JSON，不要包含解释文字或代码块标记。
            JSON 结构:
            {"artifacts":[{"filePath":"...","content":"...","language":"java"}]}
            """;

    /**
     * 严格 JSON 追加约束。
     *
     * 是什么：针对 Gemini/DeepSeek 的补充约束片段。
     * 做什么：强调只输出单个 JSON 对象。
     * 为什么：降低非 JSON 输出导致的解析失败。
     */
    private static final String STRICT_JSON_APPEND_MESSAGE = """
            严格要求：只输出一个 JSON 对象，不允许 Markdown/注释/额外文本。
            若无法生成有效内容，也必须返回 {"artifacts": []}。
            """;

    /**
     * JSON 修复系统提示词。
     *
     * 是什么：JSON 修复场景的 System Message。
     * 做什么：约束模型仅输出修复后的 JSON。
     * 为什么：避免二次修复输出继续带噪。
     */
    private static final String JSON_REPAIR_SYSTEM_MESSAGE = """
            你是 JSON 修复器，只输出严格 JSON。
            输出结构必须为 {"artifacts":[{"filePath":"...","content":"...","language":"java"}]}。
            禁止任何解释或代码块标记。
            """;

    /**
     * 需要强制 JSON 修复的提供商列表。
     *
     * 是什么：Gemini/DeepSeek 的 providerKey 集合。
     * 做什么：用于判断是否启用 JSON 修复链路。
     * 为什么：这两个模型更容易返回非 JSON 输出。
     */
    private static final Set<String> STRICT_JSON_PROVIDERS = Set.of("gemini", "deepseek");

    /**
     * 模型路由器。
     *
     * 是什么：模型选择策略入口。
     * 做什么：选择本次生成阶段的模型。
     * 为什么：确保高成功率优先。
     */
    private final LangChain4jModelRouter modelRouter;

    /**
     * 模型工厂。
     *
     * 是什么：模型构建入口。
     * 做什么：根据提供商构建 Chat 模型。
     * 为什么：统一 LangChain4j 模型创建逻辑。
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
     * 做什么：提供编码阶段的模板。
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
     * G3 上下文构建器。
     *
     * 是什么：上下文汇总工具。
     * 做什么：为生成阶段提供类索引与上下文。
     * 为什么：提升生成一致性与正确率。
     */
    private final G3ContextBuilder contextBuilder;

    /**
     * JSON 解析器。
     *
     * 是什么：ObjectMapper 实例。
     * 做什么：解析模型输出的 JSON。
     * 为什么：保证结构化产物可解析。
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数。
     *
     * 是什么：后端编码 Agent 初始化入口。
     * 做什么：注入模型路由、工具、Prompt 与上下文依赖。
     * 为什么：保证编码生成流程可用。
     *
     * @param modelRouter 模型路由器
     * @param modelFactory 模型工厂
     * @param toolRegistry 工具注册表
     * @param promptTemplateService Prompt 模板服务
     * @param blueprintPromptBuilder Blueprint Prompt 构建器
     * @param contextBuilder 上下文构建器
     * @param objectMapper JSON 解析器
     */
    public LangChain4jBackendCoderAgentImpl(
            LangChain4jModelRouter modelRouter,
            LangChain4jModelFactory modelFactory,
            LangChain4jToolRegistry toolRegistry,
            PromptTemplateService promptTemplateService,
            BlueprintPromptBuilder blueprintPromptBuilder,
            G3ContextBuilder contextBuilder,
            ObjectMapper objectMapper) {
        this.modelRouter = modelRouter;
        this.modelFactory = modelFactory;
        this.toolRegistry = toolRegistry;
        this.promptTemplateService = promptTemplateService;
        this.blueprintPromptBuilder = blueprintPromptBuilder;
        this.contextBuilder = contextBuilder;
        this.objectMapper = objectMapper;
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
        return "LangChain4j 后端编码 Agent - 生成后端代码产物";
    }

    /**
     * 获取编码目标类型。
     *
     * 是什么：编码器目标类型获取方法。
     * 做什么：返回后端类型标识。
     * 为什么：编排层需要按类型选择编码器。
     */
    @Override
    public String getTargetType() {
        return TARGET_TYPE;
    }

    /**
     * 获取编码目标语言。
     *
     * 是什么：编码器目标语言获取方法。
     * 做什么：返回 Java 语言标识。
     * 为什么：用于日志与产物标识。
     */
    @Override
    public String getTargetLanguage() {
        return TARGET_LANGUAGE;
    }

    /**
     * 执行编码任务。
     *
     * 是什么：Agent 执行入口。
     * 做什么：调用生成逻辑并返回产物列表。
     * 为什么：与编排层的统一执行模型对齐。
     */
    @Override
    public List<G3ArtifactEntity> execute(G3JobEntity job, Consumer<G3LogEntry> logConsumer)
            throws IG3Agent.G3AgentException {
        CoderResult result = generate(job, job.getCurrentRound(), logConsumer);
        if (!result.success()) {
            throw new IG3Agent.G3AgentException(AGENT_NAME, getRole(), result.errorMessage());
        }
        return result.artifacts();
    }

    /**
     * 生成代码产物。
     *
     * 是什么：编码阶段生成入口。
     * 做什么：生成后端代码并返回结果对象。
     * 为什么：为后续验证与修复阶段提供基础产物。
     */
    @Override
    public CoderResult generate(G3JobEntity job, int generationRound, Consumer<G3LogEntry> logConsumer) {
        if (job == null) {
            return CoderResult.failure("任务为空，无法生成代码");
        }
        if (job.getRequirement() == null || job.getRequirement().isBlank()) {
            return CoderResult.failure("需求为空，无法生成代码");
        }
        if (job.getContractYaml() == null || job.getContractYaml().isBlank()) {
            return CoderResult.failure("契约文档为空，无法生成代码");
        }
        if (job.getDbSchemaSql() == null || job.getDbSchemaSql().isBlank()) {
            return CoderResult.failure("数据库Schema为空，无法生成代码");
        }

        LangChain4jModelRouter.FailureContext failureContext = null;
        Exception lastError = null;
        for (int attempt = 0; attempt < MAX_ROUTE_ATTEMPTS; attempt++) {
            LangChain4jModelRouter.ModelSelection selection =
                    modelRouter.select(LangChain4jModelRouter.TaskType.CODEGEN, attempt, failureContext);
            try {
                ChatLanguageModel model = modelFactory.chatModel(selection.provider(), selection.model());
                BackendCoderService service = buildService(model, buildSystemMessage(selection.provider()));

                String prompt = buildCoderPrompt(job);
                String response = service.generate(prompt);
                ArtifactPayload payload = parseArtifacts(response);
                if (payload == null || payload.artifacts() == null || payload.artifacts().isEmpty()) {
                    payload = tryRepairArtifacts(response, model, selection.provider());
                }
                if (payload == null || payload.artifacts() == null || payload.artifacts().isEmpty()) {
                    throw new IllegalStateException("LangChain4j 输出为空，无法生成产物");
                }

                List<G3ArtifactEntity> artifacts = new ArrayList<>();
                for (ArtifactItem item : payload.artifacts()) {
                    if (item == null || item.filePath() == null || item.filePath().isBlank()) {
                        continue;
                    }
                    if (item.content() == null || item.content().isBlank()) {
                        continue;
                    }
                    artifacts.add(G3ArtifactEntity.create(
                            job.getId(),
                            item.filePath(),
                            item.content(),
                            G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                            generationRound));
                }

                if (artifacts.isEmpty()) {
                    throw new IllegalStateException("LangChain4j 输出未包含有效产物");
                }

                if (logConsumer != null) {
                    logConsumer.accept(G3LogEntry.success(getRole(),
                            "LangChain4j 后端生成完成，共 " + artifacts.size() + " 个文件"));
                }
                return CoderResult.success(artifacts);
            } catch (Exception e) {
                lastError = e;
                failureContext = new LangChain4jModelRouter.FailureContext(
                        selection.provider(), selection.model(), e.getMessage());
                log.warn("[{}] 生成失败，尝试降级: provider={}, model={}, reason={}",
                        AGENT_NAME, selection.provider(), selection.model(), e.getMessage());
            }
        }

        String errorMessage = lastError != null ? lastError.getMessage() : "未知错误";
        if (logConsumer != null) {
            logConsumer.accept(G3LogEntry.error(getRole(), "LangChain4j 生成失败: " + errorMessage));
        }
        return CoderResult.failure("LangChain4j 生成失败: " + errorMessage);
    }

    /**
     * 构建 LangChain4j 服务代理。
     *
     * 是什么：AiServices 生成方法。
     * 做什么：绑定模型、记忆与工具，返回服务接口。
     * 为什么：统一 LangChain4j 调用方式。
     *
     * @param model Chat 模型
     * @return 编码服务代理
     */
    private BackendCoderService buildService(ChatLanguageModel model, String systemMessage) {
        return AiServices.builder(BackendCoderService.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .systemMessageProvider(ignore -> systemMessage)
                .tools(toolRegistry.tools())
                .build();
    }

    /**
     * 构建系统提示词。
     *
     * 是什么：System Message 构建方法。
     * 做什么：针对特定模型追加严格 JSON 约束。
     * 为什么：提升 Gemini/DeepSeek 输出稳定性。
     *
     * @param providerKey 模型提供商标识
     * @return System Message
     */
    private String buildSystemMessage(String providerKey) {
        if (shouldAttemptJsonRepair(providerKey)) {
            return SYSTEM_MESSAGE + "\n" + STRICT_JSON_APPEND_MESSAGE;
        }
        return SYSTEM_MESSAGE;
    }

    /**
     * 尝试 JSON 修复并解析产物。
     *
     * 是什么：二次修复入口。
     * 做什么：对非 JSON 输出进行修复并重新解析。
     * 为什么：提高 Gemini/DeepSeek 输出的可解析率。
     *
     * @param raw 原始输出
     * @param model Chat 模型
     * @param providerKey 提供商标识
     * @return 修复后的产物载荷
     */
    private ArtifactPayload tryRepairArtifacts(String raw, ChatLanguageModel model, String providerKey) {
        if (!shouldAttemptJsonRepair(providerKey)) {
            return null;
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonRepairService repairService = buildJsonRepairService(model);
            String repaired = repairService.repair(stripCodeFence(raw));
            if (repaired == null || repaired.isBlank()) {
                return null;
            }
            return parseArtifacts(repaired);
        } catch (Exception e) {
            log.warn("[{}] JSON 修复失败: {}", AGENT_NAME, e.getMessage());
            return null;
        }
    }

    /**
     * 判断是否启用 JSON 修复。
     *
     * 是什么：修复开关判断方法。
     * 做什么：根据 providerKey 判断是否启用二次修复。
     * 为什么：仅对 Gemini/DeepSeek 启用，降低额外调用成本。
     *
     * @param providerKey 提供商标识
     * @return 是否启用 JSON 修复
     */
    private boolean shouldAttemptJsonRepair(String providerKey) {
        if (providerKey == null) {
            return false;
        }
        return STRICT_JSON_PROVIDERS.contains(providerKey.toLowerCase(Locale.ROOT));
    }

    /**
     * 构建 JSON 修复服务。
     *
     * 是什么：JSON 修复服务构建方法。
     * 做什么：创建不带工具的修复接口。
     * 为什么：避免修复阶段触发多余工具调用。
     *
     * @param model Chat 模型
     * @return JSON 修复服务
     */
    private JsonRepairService buildJsonRepairService(ChatLanguageModel model) {
        return AiServices.builder(JsonRepairService.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(4))
                .systemMessageProvider(ignore -> JSON_REPAIR_SYSTEM_MESSAGE)
                .build();
    }

    /**
     * 构建编码 Prompt。
     *
     * 是什么：编码 Prompt 组装方法。
     * 做什么：整合需求、契约、Schema 与 Blueprint 约束。
     * 为什么：确保编码输出包含完整上下文。
     *
     * @param job G3 任务
     * @return 编码 Prompt
     */
    private String buildCoderPrompt(G3JobEntity job) {
        StringBuilder sb = new StringBuilder();
        sb.append(promptTemplateService.coderStandardsTemplate()).append("\n\n");
        sb.append("## 需求描述\n").append(job.getRequirement()).append("\n\n");
        sb.append("## OpenAPI 契约\n").append(job.getContractYaml()).append("\n\n");
        sb.append("## 数据库 Schema\n").append(job.getDbSchemaSql()).append("\n\n");

        if (Boolean.TRUE.equals(job.getBlueprintModeEnabled())) {
            String entityConstraints = blueprintPromptBuilder.buildEntityConstraint(job.getBlueprintSpec());
            String serviceConstraints = blueprintPromptBuilder.buildServiceConstraint(job.getBlueprintSpec());
            sb.append(entityConstraints).append("\n").append(serviceConstraints).append("\n\n");
        }

        sb.append("## 已有上下文（可选）\n");
        sb.append(safeBuildContext(job)).append("\n\n");

        sb.append("## 输出要求\n");
        sb.append("- 输出严格 JSON 对象，仅包含 artifacts 数组\n");
        sb.append("- artifacts 每项包含 filePath/content/language\n");
        sb.append("- content 必须是完整文件内容，不要使用 ``` 包裹\n");
        sb.append("- 不要输出任何解释文字\n");

        return sb.toString();
    }

    /**
     * 构建上下文片段（容错）。
     *
     * 是什么：上下文安全构建方法。
     * 做什么：尝试构建上下文并在失败时返回空串。
     * 为什么：避免上下文构建失败中断主流程。
     *
     * @param job G3 任务
     * @return 上下文文本
     */
    private String safeBuildContext(G3JobEntity job) {
        try {
            return contextBuilder.buildGlobalContext(job.getId());
        } catch (Exception e) {
            log.debug("[{}] 上下文构建失败，已忽略: {}", AGENT_NAME, e.getMessage());
            return "";
        }
    }

    /**
     * 解析产物 JSON。
     *
     * 是什么：模型输出解析方法。
     * 做什么：提取 JSON 并反序列化为产物结构。
     * 为什么：保证输出可转换为 G3 产物。
     *
     * @param raw 原始输出
     * @return 产物载荷
     */
    private ArtifactPayload parseArtifacts(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = stripCodeFence(raw);
        String json = extractJson(cleaned);
        try {
            if (json.trim().startsWith("[")) {
                List<ArtifactItem> items = objectMapper.readValue(json, new TypeReference<List<ArtifactItem>>() {});
                return new ArtifactPayload(items);
            }
            return objectMapper.readValue(json, ArtifactPayload.class);
        } catch (Exception e) {
            log.warn("[{}] 解析 LangChain4j 输出失败: {}", AGENT_NAME, e.getMessage());
            return null;
        }
    }

    /**
     * 去除代码围栏。
     *
     * 是什么：Markdown 代码块清理方法。
     * 做什么：移除 ```json/``` 标记。
     * 为什么：避免解析器误判内容。
     *
     * @param raw 原始文本
     * @return 清理后的文本
     */
    private String stripCodeFence(String raw) {
        return raw.replace("```json", "")
                .replace("```", "")
                .trim();
    }

    /**
     * 提取 JSON 段。
     *
     * 是什么：JSON 提取方法。
     * 做什么：截取第一个 { 到最后一个 }。
     * 为什么：避免模型返回额外说明导致解析失败。
     *
     * @param raw 原始文本
     * @return JSON 文本
     */
    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1).trim();
        }
        return raw.trim();
    }

    /**
     * LangChain4j 文本生成接口。
     *
     * 是什么：AiServices 使用的接口契约。
     * 做什么：承载单次 Prompt 到文本输出的调用。
     * 为什么：统一接入工具与系统消息。
     */
    private interface BackendCoderService {
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

    /**
     * JSON 修复服务接口。
     *
     * 是什么：JSON 修复调用契约。
     * 做什么：将非 JSON 输出修复为严格 JSON。
     * 为什么：提升输出解析成功率。
     */
    private interface JsonRepairService {
        /**
         * 执行 JSON 修复。
         *
         * 是什么：修复调用入口。
         * 做什么：接收原始输出并返回修复后的 JSON。
         * 为什么：保证产物解析成功。
         *
         * @param raw 原始输出
         * @return 修复后的 JSON
         */
        @UserMessage("""
                请将以下内容修复为严格 JSON，对象结构必须为 {"artifacts":[{"filePath":"...","content":"...","language":"java"}]}。
                只输出 JSON，不要解释或 Markdown。
                如果无法修复，请输出 {"artifacts": []}。
                原始输出：
                {{it}}
                """)
        String repair(String raw);
    }

    /**
     * 产物输出结构。
     *
     * 是什么：LangChain4j 输出的顶层结构。
     * 做什么：承载产物列表。
     * 为什么：便于统一解析与校验。
     */
    private record ArtifactPayload(List<ArtifactItem> artifacts) {
    }

    /**
     * 单个产物条目。
     *
     * 是什么：单个文件的输出描述。
     * 做什么：提供文件路径与内容。
     * 为什么：用于生成 G3ArtifactEntity。
     */
    private record ArtifactItem(String filePath, String content, String language) {
    }
}
