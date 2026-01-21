package com.ingenio.backend.agent.g3.impl;

import com.ingenio.backend.agent.g3.IArchitectAgent;
import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.ai.AIProviderFactory;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.prompt.PromptTemplateService;
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

    private final AIProviderFactory aiProviderFactory;
    private final BlueprintPromptBuilder blueprintPromptBuilder;
    private final BlueprintValidator blueprintValidator;
    private final PromptTemplateService promptTemplateService;
    private final G3HookPipeline hookPipeline;

    public ArchitectAgentImpl(
            AIProviderFactory aiProviderFactory,
            BlueprintPromptBuilder blueprintPromptBuilder,
            BlueprintValidator blueprintValidator,
            PromptTemplateService promptTemplateService,
            G3HookPipeline hookPipeline) {
        this.aiProviderFactory = aiProviderFactory;
        this.blueprintPromptBuilder = blueprintPromptBuilder;
        this.blueprintValidator = blueprintValidator;
        this.promptTemplateService = promptTemplateService;
        this.hookPipeline = hookPipeline;
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

        try {
            // 1. 获取AI提供商
            AIProvider aiProvider = hookPipeline.wrapProvider(aiProviderFactory.getProvider(), job, logConsumer);
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
                        AIProvider.AIRequest.builder()
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
                        AIProvider.AIRequest.builder()
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
     * 截断字符串
     */
    private String truncate(String text, int maxLength) {
        if (text == null)
            return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength) + "...";
    }
}
