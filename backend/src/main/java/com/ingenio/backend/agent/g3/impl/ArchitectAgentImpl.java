package com.ingenio.backend.agent.g3.impl;

import com.ingenio.backend.agent.g3.IArchitectAgent;
import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.ai.AIProviderFactory;
import com.ingenio.backend.ai.UniaixAIProvider;
import com.ingenio.backend.entity.ProjectEntity;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.prompt.PromptTemplateService;
import com.ingenio.backend.service.ProjectService;
import com.ingenio.backend.service.blueprint.BlueprintComplianceResult;
import com.ingenio.backend.service.blueprint.BlueprintPromptBuilder;
import com.ingenio.backend.service.blueprint.BlueprintValidator;
import com.ingenio.backend.service.g3.hooks.G3HookPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 架构师Agent实现
 * 负责根据需求生成OpenAPI契约和数据库Schema
 *
 * 核心职责：
 * 1. 分析用户需求，提取业务实体和关系
 * 2. 生成符合OpenAPI 3.0规范的契约文档
 * 3. 生成PostgreSQL DDL SQL
 * 4. 验证生成的契约和Schema格式
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ingenio.g3.agent.engine", havingValue = "legacy", matchIfMissing = true)
public class ArchitectAgentImpl implements IArchitectAgent {

    private static final String AGENT_NAME = "ArchitectAgent";

    /**
     * Blueprint 合规性修复的最大尝试次数
     */
    private static final int MAX_BLUEPRINT_SCHEMA_ATTEMPTS = 3;

    /**
     * G3专用提供商标识。
     *
     * 是什么：G3 任务可选的AI Provider名称。
     * 做什么：覆盖默认Provider选择逻辑。
     * 为什么：确保G3服务生成使用Claude等指定模型。
     */
    @org.springframework.beans.factory.annotation.Value("${ingenio.g3.ai.provider:}")
    private String g3Provider;

    /**
     * G3专用模型名称。
     *
     * 是什么：G3 任务的模型名称配置。
     * 做什么：在AIRequest中指定Claude模型。
     * 为什么：避免G3与其他业务模型混用。
     */
    @org.springframework.beans.factory.annotation.Value("${ingenio.g3.ai.model:}")
    private String g3Model;

    private final AIProviderFactory aiProviderFactory;
    /**
     * UniAix AI Provider（Claude入口）。
     *
     * 是什么：支持Claude等多模型的OpenAI兼容Provider。
     * 做什么：为G3任务提供Claude能力。
     * 为什么：G3生成阶段需要稳定的Claude模型。
     */
    private final UniaixAIProvider uniaixAIProvider;
    private final BlueprintPromptBuilder blueprintPromptBuilder;
    private final BlueprintValidator blueprintValidator;
    private final PromptTemplateService promptTemplateService;
    private final G3HookPipeline hookPipeline;
    /**
     * 项目服务
     *
     * 是什么：根据 appSpecId 查询项目实体的服务。
     * 做什么：解析项目级AI配置入口。
     * 为什么：保留项目级Provider入口，便于后续扩展。
     */
    private final ProjectService projectService;

    public ArchitectAgentImpl(
            AIProviderFactory aiProviderFactory,
            UniaixAIProvider uniaixAIProvider,
            BlueprintPromptBuilder blueprintPromptBuilder,
            BlueprintValidator blueprintValidator,
            PromptTemplateService promptTemplateService,
            G3HookPipeline hookPipeline,
            ProjectService projectService) {
        this.aiProviderFactory = aiProviderFactory;
        this.uniaixAIProvider = uniaixAIProvider;
        this.blueprintPromptBuilder = blueprintPromptBuilder;
        this.blueprintValidator = blueprintValidator;
        this.promptTemplateService = promptTemplateService;
        this.hookPipeline = hookPipeline;
        this.projectService = projectService;
    }

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public G3LogEntry.Role getRole() {
        return G3LogEntry.Role.ARCHITECT;
    }

    @Override
    public String getDescription() {
        return "架构师Agent - 生成OpenAPI契约和数据库Schema";
    }

    @Override
    public List<G3ArtifactEntity> execute(G3JobEntity job, Consumer<G3LogEntry> logConsumer) throws G3AgentException {
        ArchitectResult result = design(job, logConsumer);

        if (!result.success()) {
            throw new G3AgentException(AGENT_NAME, getRole(), result.errorMessage());
        }

        List<G3ArtifactEntity> artifacts = new ArrayList<>();

        // 创建契约产物
        if (result.contractYaml() != null) {
            artifacts.add(G3ArtifactEntity.create(
                    job.getId(),
                    "contracts/openapi.yaml",
                    result.contractYaml(),
                    G3ArtifactEntity.GeneratedBy.ARCHITECT,
                    0));
        }

        // 创建Schema产物
        if (result.dbSchemaSql() != null) {
            artifacts.add(G3ArtifactEntity.create(
                    job.getId(),
                    "db/schema.sql",
                    result.dbSchemaSql(),
                    G3ArtifactEntity.GeneratedBy.ARCHITECT,
                    0));
        }

        // === Planning with Files: Initialize Docs ===
        // 1. task_plan.md
        String taskPlan = """
                # Implementation Plan

                - [x] Design Phase (Architect)
                - [ ] Coding Phase (Backend)
                    - [ ] Entities
                    - [ ] Mappers
                    - [ ] Services
                    - [ ] Controllers
                - [ ] Verification Phase (Coach)
                """;
        artifacts.add(G3ArtifactEntity.create(
                job.getId(),
                "docs/task_plan.md",
                taskPlan,
                G3ArtifactEntity.GeneratedBy.ARCHITECT,
                0));

        // 2. findings.md
        String findings = "# Findings & Analysis\\n\\n## Requirement Analysis\\n" + result.contractYaml(); // 简化：暂时放入契约作为分析
        // 更好的做法是提取分析过程，但现在先占位
        artifacts.add(G3ArtifactEntity.create(
                job.getId(),
                "docs/findings.md",
                findings,
                G3ArtifactEntity.GeneratedBy.ARCHITECT,
                0));

        // 3. progress.md
        String progress = "# Execution Log\\n\\n## " + java.time.LocalDateTime.now()
                + " Architect Agent\\n- Status: Completed\\n- Generated: openapi.yaml, schema.sql\\n";
        artifacts.add(G3ArtifactEntity.create(
                job.getId(),
                "docs/progress.md",
                progress,
                G3ArtifactEntity.GeneratedBy.ARCHITECT,
                0));

        return artifacts;
    }

    @Override
    public ArchitectResult design(G3JobEntity job, Consumer<G3LogEntry> logConsumer) {
        String requirement = job.getRequirement();

        // 注入 Scout 模版上下文 (Phase 7 Integration)
        if (job.getTemplateContext() != null && !job.getTemplateContext().isBlank()) {
            logConsumer.accept(G3LogEntry.info(getRole(), "检测到Scout模版上下文，已注入Architect Prompt"));
            requirement = requirement + "\n\n" + job.getTemplateContext();
        }

        // 注入 Blueprint 约束（V3）
        boolean blueprintModeEnabled = Boolean.TRUE.equals(job.getBlueprintModeEnabled())
                && job.getBlueprintSpec() != null
                && !job.getBlueprintSpec().isEmpty();
        if (blueprintModeEnabled) {
            String blueprintConstraint = blueprintPromptBuilder.buildArchitectConstraint(job.getBlueprintSpec());
            if (!blueprintConstraint.isBlank()) {
                logConsumer.accept(G3LogEntry.info(getRole(), "Blueprint Mode 激活 - 注入架构约束"));
                requirement = requirement + blueprintConstraint;
            }
        }

        // 注入分析上下文（M8 Enhanced）
        if (job.getAnalysisContextJson() != null && !job.getAnalysisContextJson().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        false);

                com.ingenio.backend.entity.g3.AnalysisContextSummary summary = mapper.convertValue(
                        job.getAnalysisContextJson(),
                        com.ingenio.backend.entity.g3.AnalysisContextSummary.class);

                String contextMarkdown = summary.formatAsMarkdown();
                logConsumer.accept(G3LogEntry.info(getRole(), "注入分析上下文摘要 (" + summary.getCompressionLevel() + ")"));
                requirement = requirement + "\n\n" + contextMarkdown;

                // 修复：明确技术栈要求
                String techStackType = (String) job.getAnalysisContextJson().get("techStackType");
                String techStackCode = (String) job.getAnalysisContextJson().get("techStackCode");
                if (techStackType != null) {
                    logConsumer.accept(G3LogEntry.info(getRole(), "检测到技术栈要求: " + techStackCode + " (" + techStackType + ")"));

                    // 根据技术栈类型添加明确的架构约束
                    String techStackConstraint = buildTechStackConstraint(techStackType, techStackCode);
                    if (!techStackConstraint.isBlank()) {
                        requirement = requirement + "\n\n" + techStackConstraint;
                        logConsumer.accept(G3LogEntry.info(getRole(), "已注入技术栈架构约束"));
                    }
                }
            } catch (Exception e) {
                log.warn("[ArchitectAgent] 解析分析上下文失败: {}", e.getMessage());
                // 不中断流程，降级继续
            }
        }

        try {
            // 1. 获取AI提供商
            AIProvider aiProvider = hookPipeline.wrapProvider(resolveProvider(job), job, logConsumer);
            if (!aiProvider.isAvailable()) {
                return ArchitectResult.failure("AI提供商不可用");
            }

            logConsumer.accept(G3LogEntry.info(getRole(), "开始分析需求: " + truncate(requirement, 50)));

            String contractYaml = null;
            String dbSchemaSql = null;
            String lastViolationHint = null;

            for (int attempt = 1; attempt <= MAX_BLUEPRINT_SCHEMA_ATTEMPTS; attempt++) {
                String attemptRequirement = requirement;
                if (lastViolationHint != null && !lastViolationHint.isBlank()) {
                    attemptRequirement = attemptRequirement
                            + "\n\n## 上一轮 Blueprint 合规性失败信息（必须修复）\n"
                            + lastViolationHint
                            + "\n\n请严格修复上述 Blueprint 违规项后重新生成契约与DDL。";
                }

                // 2. 生成OpenAPI契约
                logConsumer.accept(G3LogEntry.info(getRole(),
                        "正在生成API契约文档...（attempt " + attempt + "/" + MAX_BLUEPRINT_SCHEMA_ATTEMPTS + "）"));
                String contractPrompt = String.format(promptTemplateService.architectContractTemplate(),
                        attemptRequirement);
                AIProvider.AIResponse contractResponse = aiProvider.generate(contractPrompt,
                        buildG3RequestBuilder()
                                .temperature(0.3) // 降低随机性，保证格式稳定
                                .maxTokens(8000)
                                .build());

                contractYaml = extractYamlContent(contractResponse.content());

                // 验证契约格式
                if (!validateContract(contractYaml)) {
                    logConsumer.accept(G3LogEntry.warn(getRole(), "契约格式验证失败，尝试修复..."));
                    contractYaml = fixYamlFormat(contractYaml);
                }
                logConsumer.accept(G3LogEntry.success(getRole(), "API契约生成完成"));

                // 3. 生成数据库Schema
                logConsumer.accept(G3LogEntry.info(getRole(), "正在生成数据库Schema..."));
                String schemaPrompt = String.format(promptTemplateService.architectSchemaTemplate(), attemptRequirement,
                        contractYaml);
                AIProvider.AIResponse schemaResponse = aiProvider.generate(schemaPrompt,
                        buildG3RequestBuilder()
                                .temperature(0.2) // 更低的随机性
                                .maxTokens(4000)
                                .build());

                dbSchemaSql = extractSqlContent(schemaResponse.content());

                // 验证Schema格式
                if (!validateSchema(dbSchemaSql)) {
                    logConsumer.accept(G3LogEntry.warn(getRole(), "Schema格式验证失败，尝试修复..."));
                    dbSchemaSql = fixSqlFormat(dbSchemaSql);
                }

                // Blueprint 合规性校验（仅 Blueprint Mode）
                if (blueprintModeEnabled) {
                    BlueprintComplianceResult compliance = blueprintValidator.validateSchemaCompliance(dbSchemaSql,
                            job.getBlueprintSpec());
                    if (!compliance.passed()) {
                        String violationsPreview = compliance.violations().stream()
                                .limit(10)
                                .reduce("", (acc, v) -> acc + "- " + v + "\n");

                        logConsumer.accept(G3LogEntry.warn(getRole(),
                                "Blueprint 合规性验证失败（attempt " + attempt + "），准备重试"));

                        lastViolationHint = violationsPreview;

                        if (attempt < MAX_BLUEPRINT_SCHEMA_ATTEMPTS) {
                            continue;
                        }

                        logConsumer.accept(G3LogEntry.error(getRole(),
                                "Blueprint 合规性验证失败，已达到最大尝试次数"));
                        return ArchitectResult.failure("Blueprint 合规性验证失败: " + compliance.violations());
                    }

                    logConsumer.accept(G3LogEntry.success(getRole(), "Blueprint 合规性验证通过 ✅"));
                }

                logConsumer.accept(G3LogEntry.success(getRole(), "数据库Schema生成完成"));
                break;
            }

            // 4. 返回结果
            logConsumer.accept(G3LogEntry.success(getRole(), "架构设计完成，契约已锁定"));
            return ArchitectResult.success(contractYaml, dbSchemaSql);

        } catch (AIProvider.AIException e) {
            log.error("[{}] AI调用失败: {}", AGENT_NAME, e.getMessage(), e);
            logConsumer.accept(G3LogEntry.error(getRole(), "AI调用失败: " + e.getMessage()));
            return ArchitectResult.failure("AI调用失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[{}] 架构设计失败: {}", AGENT_NAME, e.getMessage(), e);
            logConsumer.accept(G3LogEntry.error(getRole(), "架构设计失败: " + e.getMessage()));
            return ArchitectResult.failure("架构设计失败: " + e.getMessage());
        }
    }

    @Override
    public boolean validateContract(String contractYaml) {
        if (contractYaml == null || contractYaml.isBlank()) {
            return false;
        }

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(contractYaml);

            // 检查必需字段
            if (!data.containsKey("openapi")) {
                log.warn("[{}] 契约缺少openapi字段", AGENT_NAME);
                return false;
            }
            if (!data.containsKey("info")) {
                log.warn("[{}] 契约缺少info字段", AGENT_NAME);
                return false;
            }
            if (!data.containsKey("paths")) {
                log.warn("[{}] 契约缺少paths字段", AGENT_NAME);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("[{}] 契约YAML解析失败: {}", AGENT_NAME, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean validateSchema(String schemaSql) {
        if (schemaSql == null || schemaSql.isBlank()) {
            return false;
        }

        // 基本检查：包含CREATE TABLE语句
        String upper = schemaSql.toUpperCase();
        if (!upper.contains("CREATE TABLE")) {
            log.warn("[{}] Schema缺少CREATE TABLE语句", AGENT_NAME);
            return false;
        }

        // 检查是否有基本的表结构
        if (!upper.contains("PRIMARY KEY")) {
            log.warn("[{}] Schema缺少PRIMARY KEY", AGENT_NAME);
            return false;
        }

        return true;
    }

    /**
     * 从AI响应中提取YAML内容
     */
    private String extractYamlContent(String content) {
        if (content == null)
            return "";

        // 移除可能的markdown代码块标记
        String cleaned = content.trim();
        if (cleaned.startsWith("```yaml")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    /**
     * 从AI响应中提取SQL内容
     */
    private String extractSqlContent(String content) {
        if (content == null)
            return "";

        // 移除可能的markdown代码块标记
        String cleaned = content.trim();
        if (cleaned.startsWith("```sql")) {
            cleaned = cleaned.substring(6);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    /**
     * 尝试修复YAML格式问题
     */
    private String fixYamlFormat(String yaml) {
        if (yaml == null)
            return "";

        // 确保以openapi开头
        if (!yaml.toLowerCase().startsWith("openapi")) {
            yaml = "openapi: '3.0.3'\n" + yaml;
        }

        return yaml;
    }

    /**
     * 尝试修复SQL格式问题
     */
    private String fixSqlFormat(String sql) {
        if (sql == null)
            return "";

        // 确保以注释开头
        if (!sql.startsWith("--")) {
            sql = "-- DDL Schema\n" + sql;
        }

        return sql;
    }

    /**
     * 获取项目级AI Provider
     *
     * 是什么：基于 appSpecId 解析项目并选择AI Provider。
     * 做什么：通过项目上下文选择Provider入口（当前回退系统默认）。
     * 为什么：保留项目级扩展点且不影响未配置项目。
     *
     * @param job G3任务实体
     * @return 可用的AI Provider
     */
    private AIProvider resolveProvider(G3JobEntity job) {
        AIProvider g3OverrideProvider = resolveG3ProviderOverride();
        if (g3OverrideProvider != null) {
            return g3OverrideProvider;
        }
        if (job == null || job.getAppSpecId() == null) {
            return aiProviderFactory.getProvider();
        }

        UUID appSpecId = job.getAppSpecId();
        ProjectEntity project = projectService.findByAppSpecId(appSpecId);
        if (project == null) {
            return aiProviderFactory.getProvider();
        }

        return aiProviderFactory.getProviderForProject(project.getId());
    }

    /**
     * 解析G3专用Provider覆盖。
     *
     * 是什么：G3阶段专用的Provider覆盖逻辑。
     * 做什么：根据配置选择UniAix或指定Provider。
     * 为什么：确保G3生成使用Claude等固定模型。
     *
     * @return 可用的Provider，未命中返回null
     */
    private AIProvider resolveG3ProviderOverride() {
        if (g3Provider == null || g3Provider.isBlank()) {
            return null;
        }
        String normalized = g3Provider.trim().toLowerCase();
        if ("claude".equals(normalized) || "uniaix".equals(normalized)) {
            if (uniaixAIProvider != null && uniaixAIProvider.isAvailable()) {
                return uniaixAIProvider;
            }
            log.warn("[{}] G3 Provider=UniAix不可用，回退默认Provider", AGENT_NAME);
            return null;
        }
        AIProvider provider = aiProviderFactory.getProviderByName(normalized);
        if (provider != null && provider.isAvailable()) {
            return provider;
        }
        log.warn("[{}] G3 Provider={}不可用，回退默认Provider", AGENT_NAME, g3Provider);
        return null;
    }

    /**
     * 构建带G3模型的请求Builder。
     *
     * 是什么：携带G3模型配置的AIRequest.Builder。
     * 做什么：在生成契约/Schema时注入Claude模型。
     * 为什么：保持G3生成模型一致性与可控性。
     *
     * @return AIRequest.Builder
     */
    private AIProvider.AIRequest.Builder buildG3RequestBuilder() {
        AIProvider.AIRequest.Builder builder = AIProvider.AIRequest.builder();
        String model = resolveG3Model();
        if (model != null && !model.isBlank()) {
            builder.model(model);
        }
        return builder;
    }

    /**
     * 解析G3模型名称。
     *
     * 是什么：G3模型解析方法。
     * 做什么：读取并返回配置的G3模型名。
     * 为什么：统一模型解析入口便于复用。
     *
     * @return G3模型名称，未配置返回null
     */
    private String resolveG3Model() {
        if (g3Model != null && !g3Model.isBlank()) {
            return g3Model;
        }
        return null;
    }

    /**
     * 截断字符串
     */
    private String truncate(String text, int maxLength) {
        if (text == null)
            return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 根据技术栈类型构建架构约束
     *
     * @param techStackType 技术栈类型（REACT_SUPABASE / REACT_SPRING_BOOT / KUIKLY / H5_WEBVIEW）
     * @param techStackCode 技术栈代码（用于展示）
     * @return 架构约束文本
     */
    private String buildTechStackConstraint(String techStackType, String techStackCode) {
        if (techStackType == null || techStackType.isBlank()) {
            return "";
        }

        return switch (techStackType) {
            case "REACT_SUPABASE" -> """
                ## 技术栈要求：React + Supabase (BaaS模式)

                **强制要求**：
                1. **前端**：使用 React + TypeScript + Vite
                2. **后端**：使用 Supabase BaaS，前端直连数据库
                3. **数据库**：PostgreSQL（通过Supabase提供）
                4. **认证**：使用 Supabase Auth
                5. **存储**：使用 Supabase Storage

                **禁止生成**：
                - ❌ 不要生成 Spring Boot 后端代码
                - ❌ 不要生成 Java 代码
                - ❌ 不要生成独立的后端服务

                **API设计**：
                - 使用 Supabase 的 REST API 和 Realtime API
                - 前端通过 @supabase/supabase-js 客户端直接调用
                """;

            case "REACT_SPRING_BOOT" -> """
                ## 技术栈要求：React + Spring Boot (企业级应用)

                **强制要求**：
                1. **前端**：使用 React + TypeScript + Vite
                2. **后端**：使用 Spring Boot 3.x + Java 17+
                3. **数据库**：PostgreSQL
                4. **ORM**：MyBatis-Plus 或 Spring Data JPA
                5. **API风格**：RESTful API

                **必须生成**：
                - ✅ 完整的 Spring Boot 后端代码
                - ✅ Controller、Service、Entity、Mapper 层
                - ✅ 数据库迁移脚本（Flyway 或 Liquibase）
                - ✅ API 文档（OpenAPI 3.0）

                **架构要求**：
                - 前后端分离架构
                - 后端提供 RESTful API
                - 前端通过 HTTP 客户端调用后端 API
                """;

            case "KUIKLY" -> """
                ## 技术栈要求：Kuikly (原生跨端应用)

                **强制要求**：
                1. **前端**：使用 Kuikly 框架（支持 Android/iOS/HarmonyOS）
                2. **后端**：使用 Spring Boot 3.x + Java 17+
                3. **数据库**：PostgreSQL
                4. **原生能力**：支持相机、GPS、蓝牙、NFC等原生功能

                **必须生成**：
                - ✅ Kuikly 跨端应用代码
                - ✅ Spring Boot 后端代码
                - ✅ 原生能力调用接口

                **架构要求**：
                - 原生跨端架构
                - 后端提供 RESTful API
                - 前端通过原生能力增强用户体验
                """;

            case "H5_WEBVIEW" -> """
                ## 技术栈要求：H5 + WebView (简单套壳应用)

                **强制要求**：
                1. **前端**：使用 HTML5 + CSS3 + JavaScript
                2. **后端**：可选（如需要可使用简单的 Node.js 或静态托管）
                3. **部署**：静态网站托管或简单的 Web 服务器

                **架构要求**：
                - 轻量级架构
                - 快速上线
                - 适合简单展示类应用
                """;

            default -> "";
        };
    }
}
