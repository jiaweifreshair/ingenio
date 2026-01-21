package com.ingenio.backend.langchain4j.agent;

import com.ingenio.backend.agent.g3.ICoachAgent;
import com.ingenio.backend.agent.g3.IG3Agent;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.entity.g3.G3SessionMemory;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import com.ingenio.backend.langchain4j.LangChain4jToolRegistry;
import com.ingenio.backend.langchain4j.model.LangChain4jModelFactory;
import com.ingenio.backend.langchain4j.model.LangChain4jModelRouter;
import com.ingenio.backend.prompt.PromptTemplateService;
import com.ingenio.backend.service.g3.G3ContextBuilder;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * LangChain4j 教练 Agent 实现。
 *
 * 是什么：基于 LangChain4j 的修复代理。
 * 做什么：分析错误并生成修复产物（最小可用骨架）。
 * 为什么：替换旧实现以接入工具调用与结构化输出。
 */
public class LangChain4jCoachAgentImpl implements ICoachAgent {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jCoachAgentImpl.class);

    private static final String AGENT_NAME = "LangChain4jCoachAgent";
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
     * 做什么：约束模型输出修复结果格式。
     * 为什么：保证修复流程可控。
     */
    private static final String SYSTEM_MESSAGE = """
            你是 Ingenio G3 的 Coach 修复 Agent。
            必须严格按提示词要求输出，不要添加解释文字。
            """;

    /**
     * 模型路由器。
     *
     * 是什么：模型选择策略入口。
     * 做什么：选择本次修复阶段的模型。
     * 为什么：确保修复成功率优先。
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
     * 做什么：提供修复阶段的模板。
     * 为什么：复用现有 Prompt 资产。
     */
    private final PromptTemplateService promptTemplateService;

    /**
     * 上下文构建器。
     *
     * 是什么：上下文汇总工具。
     * 做什么：为修复阶段提供类索引与上下文。
     * 为什么：提升修复命中率。
     */
    private final G3ContextBuilder contextBuilder;

    /**
     * 构造函数。
     *
     * 是什么：教练 Agent 初始化入口。
     * 做什么：注入模型路由、工具、Prompt 与上下文依赖。
     * 为什么：保证修复流程可用。
     *
     * @param modelRouter 模型路由器
     * @param modelFactory 模型工厂
     * @param toolRegistry 工具注册表
     * @param promptTemplateService Prompt 模板服务
     * @param contextBuilder 上下文构建器
     */
    public LangChain4jCoachAgentImpl(
            LangChain4jModelRouter modelRouter,
            LangChain4jModelFactory modelFactory,
            LangChain4jToolRegistry toolRegistry,
            PromptTemplateService promptTemplateService,
            G3ContextBuilder contextBuilder) {
        this.modelRouter = modelRouter;
        this.modelFactory = modelFactory;
        this.toolRegistry = toolRegistry;
        this.promptTemplateService = promptTemplateService;
        this.contextBuilder = contextBuilder;
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
        return "LangChain4j 教练 Agent - 自动修复代码";
    }

    /**
     * 执行入口（不直接执行）。
     *
     * 是什么：IG3Agent 执行入口。
     * 做什么：提示必须通过 fix() 调用。
     * 为什么：Coach 不直接参与主流程执行。
     */
    @Override
    public List<G3ArtifactEntity> execute(G3JobEntity job, Consumer<G3LogEntry> logConsumer) {
        throw new IG3Agent.G3AgentException(AGENT_NAME, getRole(), "LangChain4j Coach 应通过 fix() 调用");
    }

    /**
     * 执行修复流程。
     *
     * 是什么：修复入口方法。
     * 做什么：分析错误并返回修复结果。
     * 为什么：为编排层提供修复闭环能力。
     */
    @Override
    public CoachResult fix(
            G3JobEntity job,
            List<G3ArtifactEntity> errorArtifacts,
            List<G3ValidationResultEntity> validationResults,
            G3SessionMemory sessionMemory,
            Consumer<G3LogEntry> logConsumer) {
        if (errorArtifacts == null || errorArtifacts.isEmpty()) {
            return CoachResult.failure("没有需要修复的产物");
        }

        LangChain4jModelRouter.FailureContext failureContext = null;
        Exception lastError = null;
        for (int attempt = 0; attempt < MAX_ROUTE_ATTEMPTS; attempt++) {
            LangChain4jModelRouter.ModelSelection selection =
                    modelRouter.select(LangChain4jModelRouter.TaskType.REPAIR, attempt, failureContext);
            try {
                ChatLanguageModel model = modelFactory.chatModel(selection.provider(), selection.model());
                CoachService service = buildService(model);

                String repairHistory = sessionMemory != null ? sessionMemory.buildCoachContext() : "(首次修复，无历史记录)";
                String context = safeBuildContext(job, errorArtifacts);

                List<G3ArtifactEntity> fixedArtifacts = new ArrayList<>();
                StringBuilder analysisReport = new StringBuilder();

                for (G3ArtifactEntity artifact : errorArtifacts) {
                    if (artifact == null) {
                        continue;
                    }
                    String compilerOutput = resolveCompilerOutput(artifact, validationResults);
                    if (!canAutoFix(compilerOutput)) {
                        return CoachResult.cannotFix("错误不支持自动修复: " + compilerOutput);
                    }

                    String analysis = service.generate(buildAnalysisPrompt(artifact, compilerOutput));
                    String plan = service.generate(buildPlanPrompt(artifact, compilerOutput, analysis, context));
                    String fixPrompt = buildFixPrompt(artifact, compilerOutput, analysis, plan, context, repairHistory);
                    String fixedCode = stripCodeFence(service.generate(fixPrompt));

                    if (fixedCode == null || fixedCode.isBlank()) {
                        continue;
                    }

                    G3ArtifactEntity fixed = artifact.createNewVersion(
                            fixedCode,
                            G3ArtifactEntity.GeneratedBy.COACH.getValue());
                    fixedArtifacts.add(fixed);

                    analysisReport.append("- ").append(artifact.getFilePath()).append(":\n")
                            .append(analysis).append("\n");

                    if (sessionMemory != null) {
                        sessionMemory.addRepairAttempt(
                                job.getCurrentRound() != null ? job.getCurrentRound() : 0,
                                List.of(artifact.getFilePath()),
                                true,
                                compilerOutput,
                                "LangChain4j 修复完成");
                    }
                }

                if (fixedArtifacts.isEmpty()) {
                    throw new IllegalStateException("LangChain4j 未生成可用修复产物");
                }

                if (logConsumer != null) {
                    logConsumer.accept(G3LogEntry.success(getRole(),
                            "LangChain4j 修复完成，修复文件数: " + fixedArtifacts.size()));
                }
                return CoachResult.success(fixedArtifacts, analysisReport.toString());
            } catch (Exception e) {
                lastError = e;
                failureContext = new LangChain4jModelRouter.FailureContext(
                        selection.provider(), selection.model(), e.getMessage());
                log.warn("[{}] 修复失败，尝试降级: provider={}, model={}, reason={}",
                        AGENT_NAME, selection.provider(), selection.model(), e.getMessage());
            }
        }

        String errorMessage = lastError != null ? lastError.getMessage() : "未知错误";
        if (logConsumer != null) {
            logConsumer.accept(G3LogEntry.error(getRole(), "LangChain4j 修复失败: " + errorMessage));
        }
        return CoachResult.failure("LangChain4j 修复失败: " + errorMessage);
    }

    /**
     * 分析错误信息。
     *
     * 是什么：单文件错误分析入口。
     * 做什么：返回可读的错误分析文本。
     * 为什么：为修复提示提供上下文。
     */
    @Override
    public String analyzeError(G3ArtifactEntity artifact, String compilerOutput) {
        if (artifact == null || compilerOutput == null || compilerOutput.isBlank()) {
            return "编译输出为空，无法分析";
        }
        try {
            LangChain4jModelRouter.ModelSelection selection =
                    modelRouter.select(LangChain4jModelRouter.TaskType.ANALYSIS, 0, null);
            ChatLanguageModel model = modelFactory.chatModel(selection.provider(), selection.model());
            CoachService service = buildService(model);
            return service.generate(buildAnalysisPrompt(artifact, compilerOutput));
        } catch (Exception e) {
            return "LangChain4j 错误分析失败: " + e.getMessage();
        }
    }

    /**
     * 判断是否可自动修复。
     *
     * 是什么：自动修复可行性判断。
     * 做什么：根据错误关键字做粗略判断。
     * 为什么：避免在不可修复场景浪费资源。
     */
    @Override
    public boolean canAutoFix(String compilerOutput) {
        if (compilerOutput == null || compilerOutput.isBlank()) {
            return false;
        }
        return Pattern.compile("error:|错误:", Pattern.CASE_INSENSITIVE).matcher(compilerOutput).find();
    }

    /**
     * 构建 LangChain4j 服务代理。
     *
     * 是什么：AiServices 生成方法。
     * 做什么：绑定模型、记忆与工具，返回服务接口。
     * 为什么：统一 LangChain4j 调用方式。
     *
     * @param model Chat 模型
     * @return 教练服务代理
     */
    private CoachService buildService(ChatLanguageModel model) {
        return AiServices.builder(CoachService.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .systemMessageProvider(ignore -> SYSTEM_MESSAGE)
                .tools(toolRegistry.tools())
                .build();
    }

    /**
     * 构建错误分析 Prompt。
     *
     * 是什么：分析 Prompt 组装方法。
     * 做什么：注入文件路径与编译输出。
     * 为什么：为修复提供可读分析上下文。
     *
     * @param artifact 产物
     * @param compilerOutput 编译输出
     * @return 分析 Prompt
     */
    private String buildAnalysisPrompt(G3ArtifactEntity artifact, String compilerOutput) {
        String template = promptTemplateService.coachAnalysisTemplate();
        return String.format(template,
                artifact.getFilePath() != null ? artifact.getFilePath() : artifact.getFileName(),
                compilerOutput);
    }

    /**
     * 构建修复计划 Prompt。
     *
     * 是什么：修复计划 Prompt 组装方法。
     * 做什么：注入错误摘要、分析与上下文信息。
     * 为什么：提升模型生成修复方案的可行性。
     *
     * @param artifact 产物
     * @param compilerOutput 编译输出
     * @param analysis 错误分析
     * @param context 上下文片段
     * @return 计划 Prompt
     */
    private String buildPlanPrompt(G3ArtifactEntity artifact,
            String compilerOutput,
            String analysis,
            String context) {
        String template = promptTemplateService.coachPlanTemplate();
        String summary = truncate(compilerOutput, 2000);
        return String.format(template,
                artifact.getFilePath() != null ? artifact.getFilePath() : artifact.getFileName(),
                summary,
                analysis,
                context);
    }

    /**
     * 构建修复 Prompt。
     *
     * 是什么：修复 Prompt 组装方法。
     * 做什么：按是否为 pom.xml 选择不同模板。
     * 为什么：保证修复提示词与文件类型匹配。
     *
     * @param artifact 产物
     * @param compilerOutput 编译输出
     * @param analysis 错误分析
     * @param plan 修复计划
     * @param context 上下文片段
     * @param repairHistory 修复历史
     * @return 修复 Prompt
     */
    private String buildFixPrompt(G3ArtifactEntity artifact,
            String compilerOutput,
            String analysis,
            String plan,
            String context,
            String repairHistory) {
        if (isPomFile(artifact)) {
            String template = promptTemplateService.coachFixPomXmlTemplate();
            return String.format(template,
                    artifact.getContent() != null ? artifact.getContent() : "",
                    compilerOutput,
                    analysis,
                    plan);
        }
        String template = promptTemplateService.coachFixTemplate();
        return String.format(template,
                repairHistory,
                context,
                analysis,
                plan,
                artifact.getContent() != null ? artifact.getContent() : "",
                compilerOutput);
    }

    /**
     * 构建上下文片段（容错）。
     *
     * 是什么：上下文安全构建方法。
     * 做什么：构建紧凑上下文，失败时返回空串。
     * 为什么：避免上下文构建失败中断修复。
     *
     * @param job G3 任务
     * @param artifacts 产物列表
     * @return 上下文文本
     */
    private String safeBuildContext(G3JobEntity job, List<G3ArtifactEntity> artifacts) {
        try {
            return contextBuilder.buildCompactContext(job.getId(), artifacts, 2000);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 解析编译输出。
     *
     * 是什么：编译输出解析方法。
     * 做什么：优先使用产物内的编译输出，再回退验证结果。
     * 为什么：确保修复提示词包含真实错误信息。
     *
     * @param artifact 产物
     * @param results 验证结果
     * @return 编译输出文本
     */
    private String resolveCompilerOutput(G3ArtifactEntity artifact, List<G3ValidationResultEntity> results) {
        if (artifact != null && artifact.getCompilerOutput() != null && !artifact.getCompilerOutput().isBlank()) {
            return artifact.getCompilerOutput();
        }
        if (results == null) {
            return "";
        }
        return results.stream()
                .filter(Objects::nonNull)
                .map(this::extractValidationOutput)
                .filter(output -> output != null && !output.isBlank())
                .findFirst()
                .orElse("");
    }

    /**
     * 提取验证输出。
     *
     * 是什么：验证结果输出提取方法。
     * 做什么：优先返回 stderr，再回退 stdout。
     * 为什么：stderr 通常包含编译错误信息。
     *
     * @param result 验证结果
     * @return 输出文本
     */
    private String extractValidationOutput(G3ValidationResultEntity result) {
        if (result.getStderr() != null && !result.getStderr().isBlank()) {
            return result.getStderr();
        }
        return result.getStdout();
    }

    /**
     * 判断是否为 pom.xml。
     *
     * 是什么：文件类型判断方法。
     * 做什么：识别是否为 Maven 配置文件。
     * 为什么：决定使用特定的修复模板。
     *
     * @param artifact 产物
     * @return 是否为 pom.xml
     */
    private boolean isPomFile(G3ArtifactEntity artifact) {
        if (artifact == null) {
            return false;
        }
        String fileName = artifact.getFileName();
        String filePath = artifact.getFilePath();
        return "pom.xml".equalsIgnoreCase(fileName)
                || (filePath != null && filePath.endsWith("pom.xml"));
    }

    /**
     * 去除代码围栏。
     *
     * 是什么：Markdown 代码块清理方法。
     * 做什么：移除 ```java/```xml/``` 标记。
     * 为什么：避免修复结果包含多余标记。
     *
     * @param raw 原始文本
     * @return 清理后的文本
     */
    private String stripCodeFence(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("```java", "")
                .replace("```xml", "")
                .replace("```", "")
                .trim();
    }

    /**
     * 截断文本。
     *
     * 是什么：文本截断方法。
     * 做什么：将文本限制在最大长度内。
     * 为什么：避免 Prompt 超长影响模型输出。
     *
     * @param raw 原始文本
     * @param maxChars 最大长度
     * @return 截断后的文本
     */
    private String truncate(String raw, int maxChars) {
        if (raw == null || raw.length() <= maxChars) {
            return raw != null ? raw : "";
        }
        return raw.substring(0, maxChars);
    }

    /**
     * LangChain4j 文本生成接口。
     *
     * 是什么：AiServices 使用的接口契约。
     * 做什么：承载单次 Prompt 输出。
     * 为什么：统一工具与系统消息接入。
     */
    private interface CoachService {
        /**
         * 执行生成。
         *
         * 是什么：文本生成调用入口。
         * 做什么：向模型发送 Prompt 并返回输出。
         * 为什么：统一 Agent 修复调用方式。
         *
         * @param prompt Prompt 文本
         * @return 模型输出
         */
        @UserMessage("{{it}}")
        String generate(String prompt);
    }
}
