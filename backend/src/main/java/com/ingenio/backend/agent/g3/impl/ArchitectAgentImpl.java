package com.ingenio.backend.agent.g3.impl;

import com.ingenio.backend.agent.g3.IArchitectAgent;
import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.ai.AIProviderFactory;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.service.blueprint.BlueprintComplianceResult;
import com.ingenio.backend.service.blueprint.BlueprintPromptBuilder;
import com.ingenio.backend.service.blueprint.BlueprintValidator;
import lombok.extern.slf4j.Slf4j;
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
public class ArchitectAgentImpl implements IArchitectAgent {

    private static final String AGENT_NAME = "ArchitectAgent";

    /**
     * OpenAPI契约生成提示词模板
     * 优化版本: 添加Few-Shot Learning示例、明确输出约束、质量检查清单
     */
    private static final String CONTRACT_PROMPT_TEMPLATE = """
        你是一个专业的API架构师。请严格按照以下要求生成OpenAPI 3.0规范的API契约文档。

        ## 需求描述
        %s

        ## 核心要求（Critical）
        1. **严格遵循OpenAPI 3.0.3规范**，确保契约可被Swagger Parser正确解析
        2. **RESTful设计原则**：使用标准HTTP方法（GET/POST/PUT/DELETE）和语义化URL
        3. **完整的Schema定义**：所有request/response必须有完整的schema定义
        4. **统一响应格式**：所有接口返回格式统一为{code, msg, data}

        ## API设计规范
        - **资源命名**：使用复数名词（如/tasks而非/task）
        - **路径参数**：使用{id}标记，类型为UUID
        - **查询参数**：分页使用page/pageSize，搜索使用keyword
        - **状态码**：200成功、201创建、400参数错误、404未找到、500服务器错误

        ## Schema定义规范
        - **主键类型**：所有ID字段使用 type: string, format: uuid
        - **时间字段**：使用 type: string, format: date-time（ISO 8601格式）
        - **枚举类型**：使用enum定义状态、类型等
        - **必填字段**：明确标注required字段

        ## 输出格式要求（Strict）

        ⚠️ **重要**：直接输出YAML内容，**不要**使用```yaml标记包裹，不要添加任何解释文字。

        必须以下面的结构开头：
        openapi: '3.0.3'
        info:
          title: [系统名称] API
          version: '1.0.0'

        ## 示例参考（待办事项系统）

        openapi: '3.0.3'
        info:
          title: 待办事项管理系统 API
          version: '1.0.0'
        servers:
          - url: http://localhost:8080/api

        paths:
          /tasks:
            get:
              summary: 获取待办列表
              parameters:
                - name: page
                  in: query
                  schema:
                    type: integer
                    default: 1
              responses:
                '200':
                  description: 查询成功
                  content:
                    application/json:
                      schema:
                        type: object
                        properties:
                          code:
                            type: integer
                            example: 200
                          msg:
                            type: string
                            example: 查询成功
                          data:
                            type: object
                            properties:
                              items:
                                type: array
                                items:
                                  $ref: '#/components/schemas/Task'
                              total:
                                type: integer

        components:
          schemas:
            Task:
              type: object
              required:
                - id
                - title
                - status
              properties:
                id:
                  type: string
                  format: uuid
                title:
                  type: string
                  minLength: 1
                  maxLength: 200
                status:
                  type: string
                  enum: [PENDING, COMPLETED]
                createdAt:
                  type: string
                  format: date-time

        ## 质量检查清单
        - [ ] 所有endpoints都有完整的request/response schema
        - [ ] 所有ID字段都是UUID类型
        - [ ] 所有时间字段都是date-time格式
        - [ ] 输出是纯YAML内容（无```标记）

        现在请根据需求生成OpenAPI契约。
        """;

    /**
     * 数据库Schema生成提示词模板
     * 优化版本: 添加索引策略、约束设计指导、避免循环依赖、性能优化建议
     */
    private static final String SCHEMA_PROMPT_TEMPLATE = """
        你是一个专业的PostgreSQL数据库架构师。请根据需求和API契约生成高质量的DDL SQL。

        ## 需求描述
        %s

        ## API契约参考
        %s

        ## 核心设计原则

        ### 1. 表设计规范
        - **主键策略**：使用UUID类型，DEFAULT gen_random_uuid()
        - **命名规范**：表名使用复数形式的snake_case（如tasks、user_profiles）
        - **必需字段**：每个表必须包含 id, created_at, updated_at

        ### 2. 索引策略

        **必需索引**：
        - 外键字段（如user_id、task_id）
        - 常用查询字段（如status、type）
        - 时间字段用于范围查询（created_at）

        ### 3. 约束设计

        **CHECK约束**（枚举验证）：
        - 示例：CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED'))

        **外键约束**：
        ⚠️ 注意：避免循环依赖！仅在明确的父子关系中使用。
        - 谨慎使用ON DELETE CASCADE（考虑业务影响）

        ## 输出格式要求

        ⚠️ **重要**：直接输出SQL内容，**不要**使用```sql标记包裹。

        必须按以下顺序组织：
        1. 注释说明
        2. 建表语句（CREATE TABLE）
        3. 索引创建（CREATE INDEX）
        4. 触发器（自动更新updated_at）

        ## 示例参考（待办事项表）

        -- DDL for tasks table
        -- 待办事项管理表

        CREATE TABLE IF NOT EXISTS tasks (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            title VARCHAR(200) NOT NULL,
            description TEXT,
            status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
            priority VARCHAR(20) DEFAULT 'MEDIUM',
            due_date TIMESTAMP,
            user_id UUID,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT chk_task_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
            CONSTRAINT chk_task_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'))
        );

        COMMENT ON TABLE tasks IS '待办事项表';
        COMMENT ON COLUMN tasks.id IS '主键ID';
        COMMENT ON COLUMN tasks.title IS '标题';
        COMMENT ON COLUMN tasks.status IS '状态：PENDING/IN_PROGRESS/COMPLETED/CANCELLED';

        -- 索引
        CREATE INDEX idx_tasks_user_id ON tasks(user_id);
        CREATE INDEX idx_tasks_status ON tasks(status);
        CREATE INDEX idx_tasks_due_date ON tasks(due_date);

        -- 自动更新updated_at触发器
        CREATE OR REPLACE FUNCTION update_updated_at_column()
        RETURNS TRIGGER AS $$
        BEGIN
            NEW.updated_at = CURRENT_TIMESTAMP;
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;

        CREATE TRIGGER update_tasks_updated_at
            BEFORE UPDATE ON tasks
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();

        ## 质量检查清单
        - [ ] 所有表都有UUID主键和时间戳字段
        - [ ] 枚举字段都有CHECK约束
        - [ ] 外键字段都有索引
        - [ ] 包含updated_at自动更新触发器
        - [ ] 输出是纯SQL内容（无```标记）

        现在请根据需求和契约生成PostgreSQL DDL。
        """;

    /**
     * Blueprint 合规性修复的最大尝试次数
     */
    private static final int MAX_BLUEPRINT_SCHEMA_ATTEMPTS = 3;

    private final AIProviderFactory aiProviderFactory;
    private final BlueprintPromptBuilder blueprintPromptBuilder;
    private final BlueprintValidator blueprintValidator;

    public ArchitectAgentImpl(
            AIProviderFactory aiProviderFactory,
            BlueprintPromptBuilder blueprintPromptBuilder,
            BlueprintValidator blueprintValidator) {
        this.aiProviderFactory = aiProviderFactory;
        this.blueprintPromptBuilder = blueprintPromptBuilder;
        this.blueprintValidator = blueprintValidator;
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
                    0
            ));
        }

        // 创建Schema产物
        if (result.dbSchemaSql() != null) {
            artifacts.add(G3ArtifactEntity.create(
                    job.getId(),
                    "db/schema.sql",
                    result.dbSchemaSql(),
                    G3ArtifactEntity.GeneratedBy.ARCHITECT,
                    0
            ));
        }

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
            AIProvider aiProvider = aiProviderFactory.getProvider();
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
                String contractPrompt = String.format(CONTRACT_PROMPT_TEMPLATE, attemptRequirement);
                AIProvider.AIResponse contractResponse = aiProvider.generate(contractPrompt,
                        AIProvider.AIRequest.builder()
                                .temperature(0.3)  // 降低随机性，保证格式稳定
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
                String schemaPrompt = String.format(SCHEMA_PROMPT_TEMPLATE, attemptRequirement, contractYaml);
                AIProvider.AIResponse schemaResponse = aiProvider.generate(schemaPrompt,
                        AIProvider.AIRequest.builder()
                                .temperature(0.2)  // 更低的随机性
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
                    BlueprintComplianceResult compliance =
                            blueprintValidator.validateSchemaCompliance(dbSchemaSql, job.getBlueprintSpec());
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
        if (content == null) return "";

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
        if (content == null) return "";

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
        if (yaml == null) return "";

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
        if (sql == null) return "";

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
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
