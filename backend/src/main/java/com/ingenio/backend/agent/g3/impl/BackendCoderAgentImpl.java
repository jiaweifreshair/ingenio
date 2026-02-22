package com.ingenio.backend.agent.g3.impl;

import com.ingenio.backend.agent.g3.ICoderAgent;
import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.ai.AIProviderFactory;
import com.ingenio.backend.ai.UniaixAIProvider;
import com.ingenio.backend.codegen.util.NamingConverter;
import com.ingenio.backend.entity.ProjectEntity;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.prompt.PromptTemplateService;
import com.ingenio.backend.service.ProjectService;
import com.ingenio.backend.service.blueprint.BlueprintPromptBuilder;
import com.ingenio.backend.service.g3.hooks.G3HookPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 后端编码器Agent实现
 * 负责根据OpenAPI契约和DB Schema生成Spring Boot代码
 *
 * 核心职责：
 * 1. 解析OpenAPI契约，提取endpoints定义
 * 2. 解析DB Schema，提取实体结构
 * 3. 生成完整的Spring Boot代码：Entity、Mapper、Service、Controller
 * 4. 确保生成的代码符合契约规范
 */
@Component
@ConditionalOnProperty(name = "ingenio.g3.agent.engine", havingValue = "legacy", matchIfMissing = true)
public class BackendCoderAgentImpl implements ICoderAgent {

    private static final Logger log = LoggerFactory.getLogger(BackendCoderAgentImpl.class);

    private static final String AGENT_NAME = "BackendCoderAgent";
    private static final String TARGET_TYPE = "backend";
    private static final String TARGET_LANGUAGE = "java";

    /**
     * G3专用提供商标识。
     *
     * 是什么：G3 任务可选的AI Provider名称。
     * 做什么：覆盖默认Provider选择逻辑。
     * 为什么：确保G3服务生成使用Claude。
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

    @org.springframework.beans.factory.annotation.Value("${ingenio.ai.models.execute:}")
    private String executionModel;

    /**
     * 单次AI生成超时（毫秒）
     */
    @org.springframework.beans.factory.annotation.Value("${ingenio.g3.codegen.timeout-ms:120000}")
    private long aiTimeoutMs;

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
    private final PromptTemplateService promptTemplateService;
    private final com.ingenio.backend.service.g3.G3ContextBuilder contextBuilder;
    private final G3HookPipeline hookPipeline;
    /**
     * 项目服务
     *
     * 是什么：根据 appSpecId 查询项目实体的服务。
     * 做什么：解析项目级AI配置入口。
     * 为什么：保留项目级Provider入口，便于后续扩展。
     */
    private final ProjectService projectService;

    public BackendCoderAgentImpl(
            AIProviderFactory aiProviderFactory,
            UniaixAIProvider uniaixAIProvider,
            BlueprintPromptBuilder blueprintPromptBuilder,
            PromptTemplateService promptTemplateService,
            com.ingenio.backend.service.g3.G3ContextBuilder contextBuilder,
            G3HookPipeline hookPipeline,
            ProjectService projectService) {
        this.aiProviderFactory = aiProviderFactory;
        this.uniaixAIProvider = uniaixAIProvider;
        this.blueprintPromptBuilder = blueprintPromptBuilder;
        this.promptTemplateService = promptTemplateService;
        this.contextBuilder = contextBuilder;
        this.hookPipeline = hookPipeline;
        this.projectService = projectService;
    }

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public String getDescription() {
        return "后端编码器Agent - 根据契约生成Spring Boot代码";
    }

    @Override
    public String getTargetType() {
        return TARGET_TYPE;
    }

    @Override
    public String getTargetLanguage() {
        return TARGET_LANGUAGE;
    }

    @Override
    public List<G3ArtifactEntity> execute(G3JobEntity job, Consumer<G3LogEntry> logConsumer) throws G3AgentException {
        CoderResult result = generate(job, job.getCurrentRound(), logConsumer);

        if (!result.success()) {
            throw new G3AgentException(AGENT_NAME, getRole(), result.errorMessage());
        }

        return result.artifacts();
    }

    @Override
    public CoderResult generate(G3JobEntity job, int generationRound, Consumer<G3LogEntry> logConsumer) {
        String contractYaml = job.getContractYaml();
        String dbSchemaSql = job.getDbSchemaSql();

        if (contractYaml == null || contractYaml.isBlank()) {
            return CoderResult.failure("契约文档为空，无法生成代码");
        }

        if (dbSchemaSql == null || dbSchemaSql.isBlank()) {
            return CoderResult.failure("数据库Schema为空，无法生成代码");
        }

        try {
            if (shouldEnableBlueprint(job)) {
                logConsumer.accept(G3LogEntry.info(getRole(), "Blueprint Mode 激活 - 注入编码约束"));
            }

            AIProvider aiProvider = hookPipeline.wrapProvider(resolveProvider(job), job, logConsumer);
            if (!aiProvider.isAvailable()) {
                logConsumer.accept(G3LogEntry.warn(getRole(), "AI提供商不可用，进入兜底骨架生成模式"));
            }

            List<G3ArtifactEntity> artifacts = new ArrayList<>();

            // 0. 生成通用基础类 (Common)
            logConsumer.accept(G3LogEntry.info(getRole(), "正在生成通用基础类 (Result, BaseResponse)..."));
            List<G3ArtifactEntity> commonArtifacts = generateCommonArtifacts(job, generationRound);
            artifacts.addAll(commonArtifacts);

            // 1. 生成实体类
            logConsumer.accept(G3LogEntry.info(getRole(), "正在生成实体类..."));
            List<G3ArtifactEntity> entityArtifacts = generateEntities(job, dbSchemaSql, aiProvider, generationRound,
                    logConsumer);
            artifacts.addAll(entityArtifacts);
            logConsumer.accept(G3LogEntry.success(getRole(), "实体类生成完成，共 " + entityArtifacts.size() + " 个文件"));

            // 2. 生成Mapper接口
            logConsumer.accept(G3LogEntry.info(getRole(), "正在生成Mapper接口..."));
            String entityCode = combineArtifactsContent(entityArtifacts);
            List<G3ArtifactEntity> mapperArtifacts = generateMappers(job, entityCode, entityArtifacts, aiProvider,
                    generationRound,
                    logConsumer);
            artifacts.addAll(mapperArtifacts);
            logConsumer.accept(G3LogEntry.success(getRole(), "Mapper接口生成完成，共 " + mapperArtifacts.size() + " 个文件"));

            // 3. 生成DTO类（解决编译时缺少DTO类的问题）
            logConsumer.accept(G3LogEntry.info(getRole(), "正在生成DTO类..."));
            List<G3ArtifactEntity> dtoArtifacts = generateDTOs(job, contractYaml, entityCode, entityArtifacts,
                    aiProvider,
                    generationRound, logConsumer);
            artifacts.addAll(dtoArtifacts);
            logConsumer.accept(G3LogEntry.success(getRole(), "DTO类生成完成，共 " + dtoArtifacts.size() + " 个文件"));
            String dtoCode = combineArtifactsContent(dtoArtifacts);

            // 4. 生成Service层
            logConsumer.accept(G3LogEntry.info(getRole(), "正在生成Service层..."));
            String mapperCode = combineArtifactsContent(mapperArtifacts);
            List<G3ArtifactEntity> serviceArtifacts = generateServices(job, contractYaml, mapperCode, dtoCode,
                    entityArtifacts, mapperArtifacts, dtoArtifacts, aiProvider, generationRound, logConsumer);
            artifacts.addAll(serviceArtifacts);
            logConsumer.accept(G3LogEntry.success(getRole(), "Service层生成完成，共 " + serviceArtifacts.size() + " 个文件"));

            // 5. 生成Controller层
            logConsumer.accept(G3LogEntry.info(getRole(), "正在生成Controller层..."));
            String serviceCode = combineArtifactsContent(serviceArtifacts.stream()
                    .filter(a -> a.getFilePath().contains("Service.java") && !a.getFilePath().contains("Impl"))
                    .toList());
            List<G3ArtifactEntity> controllerArtifacts = generateControllers(job, contractYaml, serviceCode, dtoCode,
                    serviceArtifacts, dtoArtifacts, aiProvider, generationRound, logConsumer);
            artifacts.addAll(controllerArtifacts);
            logConsumer
                    .accept(G3LogEntry.success(getRole(), "Controller层生成完成，共 " + controllerArtifacts.size() + " 个文件"));

            // 6. 生成pom.xml依赖补充
            logConsumer.accept(G3LogEntry.info(getRole(), "正在生成项目配置..."));
            G3ArtifactEntity pomArtifact = generatePomFragment(job, generationRound);
            artifacts.add(pomArtifact);

            // 7. 补齐基础代码骨架（兜底：防止AI输出缺失导致编译失败）
            List<G3ArtifactEntity> baselineArtifacts = ensureBaselineArtifacts(
                    job,
                    artifacts,
                    dbSchemaSql,
                    generationRound,
                    logConsumer);
            if (!baselineArtifacts.isEmpty()) {
                artifacts.addAll(baselineArtifacts);
                logConsumer.accept(G3LogEntry.success(getRole(),
                        "基础骨架补齐完成，共 " + baselineArtifacts.size() + " 个文件"));
            }

            // === Planning with Files: Update Docs ===
            // 1. task_plan.md (Update: Mark Coding as done)
            String taskPlan = """
                    # Implementation Plan

                    - [x] Design Phase (Architect)
                    - [x] Coding Phase (Backend)
                        - [x] Entities
                        - [x] Mappers
                        - [x] Services
                        - [x] Controllers
                    - [ ] Verification Phase (Coach)
                    """;
            artifacts.add(G3ArtifactEntity.create(
                    job.getId(),
                    "docs/task_plan.md",
                    taskPlan,
                    G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                    generationRound));

            // 2. progress.md (Append log)
            String progress = "\n\n## " + java.time.LocalDateTime.now() + " Backend Coder Agent\n" +
                    "- Status: Completed\n" +
                    "- Generated: " + artifacts.size() + " files (Entity, Mapper, Service, Controller)\n";
            artifacts.add(G3ArtifactEntity.create(
                    job.getId(),
                    "docs/progress.md",
                    progress,
                    G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                    generationRound));

            logConsumer.accept(G3LogEntry.success(getRole(), "后端代码生成完成，共 " + artifacts.size() + " 个文件"));
            return CoderResult.success(artifacts);

        } catch (AIProvider.AIException e) {
            log.error("[{}] AI调用失败: {}", AGENT_NAME, e.getMessage(), e);
            logConsumer.accept(G3LogEntry.error(getRole(), "AI调用失败: " + e.getMessage()));
            return CoderResult.failure("AI调用失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[{}] 代码生成失败: {}", AGENT_NAME, e.getMessage(), e);
            logConsumer.accept(G3LogEntry.error(getRole(), "代码生成失败: " + e.getMessage()));
            return CoderResult.failure("代码生成失败: " + e.getMessage());
        }
    }

    /**
     * 生成实体类
     */
    private List<G3ArtifactEntity> generateEntities(
            G3JobEntity job,
            String dbSchemaSql,
            AIProvider aiProvider,
            int generationRound,
            Consumer<G3LogEntry> logConsumer) {

        String blueprintConstraint = shouldEnableBlueprint(job)
                ? blueprintPromptBuilder.buildEntityConstraint(job.getBlueprintSpec())
                : "";

        // Planning with Files: Inject Context
        String projectContext = contextBuilder.buildGlobalContext(job.getId());

        String prompt = String.format(
                promptTemplateService.coderEntityTemplate(),
                promptTemplateService.coderStandardsTemplate() + blueprintConstraint + "\n\n" + projectContext,
                dbSchemaSql);
        AIProvider.AIResponse response = safeGenerate(
                aiProvider,
                prompt,
                buildG3RequestBuilder()
                        .temperature(0.2)
                        .maxTokens(8000)
                        .build(),
                logConsumer,
                "实体类");

        return parseJavaFiles(response.content(), job.getId(), generationRound,
                "src/main/java/com/ingenio/backend/entity/generated/");
    }

    /**
     * 生成Mapper接口
     */
    private List<G3ArtifactEntity> generateMappers(
            G3JobEntity job,
            String entityCode,
            List<G3ArtifactEntity> entityArtifacts,
            AIProvider aiProvider,
            int generationRound,
            Consumer<G3LogEntry> logConsumer) {

        String blueprintConstraint = shouldEnableBlueprint(job)
                ? blueprintPromptBuilder.buildEntityConstraint(job.getBlueprintSpec())
                : "";

        // S2增强：注入已生成的Entity类清单
        String entityClassList = contextBuilder.buildGeneratedClassList(entityArtifacts, "Entity");

        String prompt = String.format(
                promptTemplateService.coderMapperTemplate(),
                promptTemplateService.coderStandardsTemplate() + blueprintConstraint + "\n\n" + entityClassList,
                entityCode);
        AIProvider.AIResponse response = safeGenerate(
                aiProvider,
                prompt,
                buildG3RequestBuilder()
                        .temperature(0.2)
                        .maxTokens(4000)
                        .build(),
                logConsumer,
                "Mapper接口");

        return parseJavaFiles(response.content(), job.getId(), generationRound,
                "src/main/java/com/ingenio/backend/mapper/generated/");
    }

    /**
     * 生成DTO类
     * 解决编译时缺少DTO类（如UserDTO、UserCreateRequest等）的问题
     */
    private List<G3ArtifactEntity> generateDTOs(
            G3JobEntity job,
            String contractYaml,
            String entityCode,
            List<G3ArtifactEntity> entityArtifacts,
            AIProvider aiProvider,
            int generationRound,
            Consumer<G3LogEntry> logConsumer) {

        String blueprintConstraint = shouldEnableBlueprint(job)
                ? blueprintPromptBuilder.buildEntityConstraint(job.getBlueprintSpec())
                : "";

        // S2增强：注入已生成的Entity类清单
        String entityClassList = contextBuilder.buildGeneratedClassList(entityArtifacts, "Entity");

        String prompt = String.format(
                promptTemplateService.coderDtoTemplate(),
                promptTemplateService.coderStandardsTemplate() + blueprintConstraint + "\n\n" + entityClassList,
                contractYaml,
                entityCode);
        AIProvider.AIResponse response = safeGenerate(
                aiProvider,
                prompt,
                buildG3RequestBuilder()
                        .temperature(0.2)
                        .maxTokens(8000)
                        .build(),
                logConsumer,
                "DTO类");

        return parseJavaFiles(response.content(), job.getId(), generationRound,
                "src/main/java/com/ingenio/backend/dto/generated/");
    }

    /**
     * 生成Service层
     */
    private List<G3ArtifactEntity> generateServices(
            G3JobEntity job,
            String contractYaml,
            String mapperCode,
            String dtoCode,
            List<G3ArtifactEntity> entityArtifacts,
            List<G3ArtifactEntity> mapperArtifacts,
            List<G3ArtifactEntity> dtoArtifacts,
            AIProvider aiProvider,
            int generationRound,
            Consumer<G3LogEntry> logConsumer) {

        String blueprintConstraint = shouldEnableBlueprint(job)
                ? blueprintPromptBuilder.buildServiceConstraint(job.getBlueprintSpec())
                : "";

        // Planning with Files: Inject Context
        String projectContext = contextBuilder.buildGlobalContext(job.getId());

        // S2增强：注入已生成的类清单
        String entityClassList = contextBuilder.buildGeneratedClassList(entityArtifacts, "Entity");
        String mapperClassList = contextBuilder.buildGeneratedClassList(mapperArtifacts, "Mapper");
        String dtoClassList = contextBuilder.buildGeneratedClassList(dtoArtifacts, "DTO");
        String generatedClassesContext = entityClassList + mapperClassList + dtoClassList;

        String prompt = String.format(
                promptTemplateService.coderServiceTemplate(),
                promptTemplateService.coderStandardsTemplate() + blueprintConstraint + "\n\n" + projectContext + "\n\n"
                        + generatedClassesContext,
                contractYaml,
                mapperCode,
                dtoCode);
        AIProvider.AIResponse response = safeGenerate(
                aiProvider,
                prompt,
                buildG3RequestBuilder()
                        .temperature(0.2)
                        .maxTokens(8000)
                        .build(),
                logConsumer,
                "Service层");

        return parseJavaFiles(response.content(), job.getId(), generationRound,
                "src/main/java/com/ingenio/backend/service/generated/");
    }

    /**
     * 生成Controller层
     */
    private List<G3ArtifactEntity> generateControllers(
            G3JobEntity job,
            String contractYaml,
            String serviceCode,
            String dtoCode,
            List<G3ArtifactEntity> serviceArtifacts,
            List<G3ArtifactEntity> dtoArtifacts,
            AIProvider aiProvider,
            int generationRound,
            Consumer<G3LogEntry> logConsumer) {

        String blueprintConstraint = shouldEnableBlueprint(job)
                ? blueprintPromptBuilder.buildServiceConstraint(job.getBlueprintSpec())
                : "";

        // Planning with Files: Inject Context
        String projectContext = contextBuilder.buildGlobalContext(job.getId());

        // S2增强：注入已生成的类清单（Controller只需要Service和DTO）
        String serviceClassList = contextBuilder.buildGeneratedClassList(
                serviceArtifacts.stream()
                        .filter(a -> a.getFilePath().contains("Service.java") && !a.getFilePath().contains("Impl"))
                        .toList(),
                "Service接口");
        String dtoClassList = contextBuilder.buildGeneratedClassList(dtoArtifacts, "DTO");
        String generatedClassesContext = serviceClassList + dtoClassList;

        String prompt = String.format(
                promptTemplateService.coderControllerTemplate(),
                promptTemplateService.coderStandardsTemplate() + blueprintConstraint + "\n\n" + projectContext + "\n\n"
                        + generatedClassesContext,
                contractYaml,
                serviceCode,
                dtoCode);
        AIProvider.AIResponse response = safeGenerate(
                aiProvider,
                prompt,
                buildG3RequestBuilder()
                        .temperature(0.2)
                        .maxTokens(6000)
                        .build(),
                logConsumer,
                "Controller层");

        return parseJavaFiles(response.content(), job.getId(), generationRound,
                "src/main/java/com/ingenio/backend/controller/generated/");
    }

    /**
     * 生成pom.xml依赖片段
     * 包含所有G3生成代码所需的依赖，避免编译错误
     */
    private G3ArtifactEntity generatePomFragment(G3JobEntity job, int generationRound) {
        String pomFragment = """
                <!-- G3 Generated Dependencies -->
                <dependencies>
                    <!-- MyBatis-Plus -->
                    <dependency>
                        <groupId>com.baomidou</groupId>
                        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                        <version>3.5.8</version>
                    </dependency>

                    <!-- PostgreSQL Driver -->
                    <dependency>
                        <groupId>org.postgresql</groupId>
                        <artifactId>postgresql</artifactId>
                        <scope>runtime</scope>
                    </dependency>

                    <!-- Lombok -->
                    <dependency>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <optional>true</optional>
                    </dependency>

                    <!-- Validation (Jakarta EE - Spring Boot 3) -->
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-validation</artifactId>
                    </dependency>

                    <!-- Spring Security (for PasswordEncoder) -->
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-security</artifactId>
                    </dependency>

                    <!-- Spring Web -->
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                </dependencies>
                """;

        return G3ArtifactEntity.create(
                job.getId(),
                "pom-fragment.xml",
                pomFragment,
                G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                generationRound);
    }

    /**
     * 生成通用基础类 (Result, BaseResponse)
     * 解决 generated 模块缺失 common 依赖和 duplicate class 问题
     */
    private List<G3ArtifactEntity> generateCommonArtifacts(G3JobEntity job, int generationRound) {
        List<G3ArtifactEntity> artifacts = new ArrayList<>();
        java.util.UUID jobId = job.getId();

        // 1. Helper: Result.java
        String resultClass = """
                package com.ingenio.backend.common.api;

                import lombok.Data;
                import java.io.Serializable;

                /**
                 * 通用返回结果
                 * @param <T> 数据类型
                 */
                @Data
                public class Result<T> implements Serializable {
                    private static final long serialVersionUID = 1L;

                    private Integer code;
                    private String message;
                    private T data;

                    public static <T> Result<T> success() {
                        Result<T> result = new Result<>();
                        result.setCode(200);
                        result.setMessage("Success");
                        return result;
                    }

                    public static <T> Result<T> success(T data) {
                        Result<T> result = new Result<>();
                        result.setCode(200);
                        result.setMessage("Success");
                        result.setData(data);
                        return result;
                    }

                    public static <T> Result<T> success(String message) {
                        Result<T> result = new Result<>();
                        result.setCode(200);
                        result.setMessage(message);
                        return result;
                    }

                    public static <T> Result<T> failed(String message) {
                        Result<T> result = new Result<>();
                        result.setCode(500);
                        result.setMessage(message);
                        return result;
                    }

                    public static <T> Result<T> error(String message) {
                        return failed(message);
                    }
                }
                """;
        artifacts.add(G3ArtifactEntity.create(jobId, "src/main/java/com/ingenio/backend/common/api/Result.java",
                resultClass, G3ArtifactEntity.GeneratedBy.BACKEND_CODER, generationRound));

        // 2. Helper: BaseResponse.java
        String baseResponseClass = """
                package com.ingenio.backend.dto.generated;

                import lombok.Data;
                import java.io.Serializable;

                /**
                 * 基础响应类
                 */
                @Data
                public class BaseResponse implements Serializable {
                    private static final long serialVersionUID = 1L;
                }
                """;
        artifacts
                .add(G3ArtifactEntity.create(jobId, "src/main/java/com/ingenio/backend/dto/generated/BaseResponse.java",
                        baseResponseClass, G3ArtifactEntity.GeneratedBy.BACKEND_CODER, generationRound));

        return artifacts;
    }

    /**
     * 判断是否启用 Blueprint Mode
     */
    private boolean shouldEnableBlueprint(G3JobEntity job) {
        return Boolean.TRUE.equals(job.getBlueprintModeEnabled())
                && job.getBlueprintSpec() != null
                && !job.getBlueprintSpec().isEmpty();
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
        // 兼容网宿（Wangsu）命名，统一映射到 ECA Gateway。
        if ("wangsu".equals(normalized)) {
            normalized = "eca-gateway";
        }
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
     * 做什么：在G3生成阶段注入Claude模型。
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
        if (executionModel != null && !executionModel.isBlank()) {
            return executionModel;
        }
        return null;
    }

    /**
     * 安全执行AI生成（带超时与兜底）
     *
     * 是什么：封装AI调用的超时控制与异常处理。
     * 做什么：在AI不可用/超时情况下返回空响应，保证流程可继续。
     * 为什么：防止单次调用卡死导致G3任务长时间停留在CODING阶段。
     */
    private AIProvider.AIResponse safeGenerate(
            AIProvider aiProvider,
            String prompt,
            AIProvider.AIRequest request,
            Consumer<G3LogEntry> logConsumer,
            String stageName) {
        if (aiProvider == null || !aiProvider.isAvailable()) {
            logConsumer.accept(G3LogEntry.warn(getRole(),
                    stageName + " 生成跳过：AI提供商不可用"));
            return buildEmptyResponse(aiProvider);
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return aiProvider.generate(prompt, request);
                } catch (AIProvider.AIException e) {
                    throw new IllegalStateException(e);
                }
            }).orTimeout(aiTimeoutMs, TimeUnit.MILLISECONDS).join();
        } catch (Exception e) {
            logConsumer.accept(G3LogEntry.warn(getRole(),
                    stageName + " 生成超时/失败，使用兜底输出: " + e.getMessage()));
            return buildEmptyResponse(aiProvider);
        }
    }

    /**
     * 构建空的AI响应（兜底使用）
     */
    private AIProvider.AIResponse buildEmptyResponse(AIProvider aiProvider) {
        String provider = aiProvider != null ? aiProvider.getProviderName() : "unknown";
        String model = aiProvider != null ? aiProvider.getDefaultModel() : "unknown";
        return AIProvider.AIResponse.builder()
                .content("")
                .model(model)
                .provider(provider)
                .durationMs(0)
                .promptTokens(0)
                .completionTokens(0)
                .totalTokens(0)
                .rawResponse("")
                .build();
    }

    /**
     * 兜底补齐基础产物，避免AI输出缺失导致编译失败
     *
     * 是什么：扫描已生成代码的import依赖和Schema表名，自动生成缺失的DTO/Entity/Mapper/Service骨架。
     * 做什么：为缺失类型生成最小可编译实现，保障G3验证闭环可收敛。
     * 为什么：AI输出偶发不完整会导致编译卡住，兜底骨架可显著提升E2E稳定性。
     */
    private List<G3ArtifactEntity> ensureBaselineArtifacts(
            G3JobEntity job,
            List<G3ArtifactEntity> artifacts,
            String dbSchemaSql,
            int generationRound,
            Consumer<G3LogEntry> logConsumer) {
        Set<String> existingPaths = new HashSet<>();
        for (G3ArtifactEntity artifact : artifacts) {
            if (artifact.getFilePath() != null) {
                existingPaths.add(artifact.getFilePath());
            }
        }

        Set<String> missingClasses = new LinkedHashSet<>();
        missingClasses.addAll(collectMissingImports(artifacts, existingPaths));

        // 根据Schema补齐实体（确保至少有基础Entity）
        for (String entityName : extractEntityNamesFromSchema(dbSchemaSql)) {
            String entityFqcn = "com.ingenio.backend.entity." + entityName;
            String entityPath = toFilePath(entityFqcn);
            if (!existingPaths.contains(entityPath)) {
                missingClasses.add(entityFqcn);
            }
        }

        // 由Mapper/Service推导实体依赖，避免泛型缺失
        Set<String> derivedEntities = new LinkedHashSet<>();
        for (String fqcn : missingClasses) {
            if (fqcn.startsWith("com.ingenio.backend.mapper.") && fqcn.endsWith("Mapper")) {
                String entityName = fqcn.substring(fqcn.lastIndexOf('.') + 1, fqcn.length() - "Mapper".length());
                if (!entityName.isBlank()) {
                    derivedEntities.add("com.ingenio.backend.entity." + entityName);
                }
            }
            if (fqcn.startsWith("com.ingenio.backend.service.") && fqcn.endsWith("Service")) {
                String entityName = fqcn.substring(fqcn.lastIndexOf('.') + 1, fqcn.length() - "Service".length());
                if (!entityName.isBlank()) {
                    derivedEntities.add("com.ingenio.backend.entity." + entityName);
                }
            }
        }
        missingClasses.addAll(derivedEntities);

        List<G3ArtifactEntity> generated = new ArrayList<>();
        Set<String> entityNames = new HashSet<>(extractEntityNamesFromSchema(dbSchemaSql));
        for (String path : existingPaths) {
            if (path != null && path.contains("/entity/") && path.endsWith(".java")) {
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                String name = fileName.replace(".java", "");
                if (!name.isBlank()) {
                    entityNames.add(name);
                }
            }
        }

        for (String fqcn : missingClasses) {
            String filePath = toFilePath(fqcn);
            if (existingPaths.contains(filePath)) {
                continue;
            }

            String content = buildStubByPackage(fqcn, entityNames);
            if (content == null || content.isBlank()) {
                continue;
            }

            G3ArtifactEntity artifact = G3ArtifactEntity.create(
                    job.getId(),
                    filePath,
                    content,
                    G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                    generationRound);
            generated.add(artifact);
        }

        if (!generated.isEmpty()) {
            logConsumer.accept(G3LogEntry.info(getRole(), "兜底生成基础骨架: " + generated.size() + " 个文件"));
        }

        return generated;
    }

    /**
     * 收集缺失的import类（仅关注com.ingenio.backend命名空间）
     *
     * 是什么：从已生成代码中解析import列表。
     * 做什么：识别缺失的内部类型，为后续兜底生成提供清单。
     * 为什么：生成流程可能遗漏DTO/Service等基础类，需自动补齐。
     */
    private Set<String> collectMissingImports(List<G3ArtifactEntity> artifacts, Set<String> existingPaths) {
        Set<String> missing = new LinkedHashSet<>();
        Pattern importPattern = Pattern.compile("^import\\s+([\\w.]+);", Pattern.MULTILINE);

        for (G3ArtifactEntity artifact : artifacts) {
            String content = artifact.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }

            Matcher matcher = importPattern.matcher(content);
            while (matcher.find()) {
                String fqcn = matcher.group(1);
                if (!fqcn.startsWith("com.ingenio.backend.")) {
                    continue;
                }
                String filePath = toFilePath(fqcn);
                if (!existingPaths.contains(filePath)) {
                    missing.add(fqcn);
                }
            }
        }

        return missing;
    }

    /**
     * 从Schema中提取实体名称
     *
     * 是什么：解析CREATE TABLE语句并转换为PascalCase类名。
     * 做什么：为实体兜底生成提供可靠来源。
     * 为什么：避免AI遗漏核心表对应的实体类。
     */
    private Set<String> extractEntityNamesFromSchema(String schemaSql) {
        Set<String> entities = new LinkedHashSet<>();
        if (schemaSql == null || schemaSql.isBlank()) {
            return entities;
        }

        Pattern tablePattern = Pattern.compile(
                "(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([\\w\\\"]+)");
        Matcher matcher = tablePattern.matcher(schemaSql);
        while (matcher.find()) {
            String rawName = matcher.group(1).replace("\"", "");
            if (rawName.isBlank()) {
                continue;
            }
            String normalized = rawName.toLowerCase();
            if (normalized.contains("flyway_schema_history") || normalized.contains("schema_migrations")) {
                continue;
            }
            entities.add(NamingConverter.toPascalCase(rawName));
        }
        return entities;
    }

    /**
     * 根据包名生成最小可编译骨架
     *
     * 是什么：为不同包路径构建对应的最小实现。
     * 做什么：DTO/Entity/Mapper/Service生成占位代码。
     * 为什么：确保编译通过并为后续人工完善保留入口。
     */
    private String buildStubByPackage(String fqcn, Set<String> schemaEntities) {
        if (fqcn == null || fqcn.isBlank()) {
            return "";
        }

        String packageName = fqcn.substring(0, fqcn.lastIndexOf('.'));
        String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);

        if (fqcn.startsWith("com.ingenio.backend.dto.")) {
            return buildStubDto(packageName, className);
        }
        if (fqcn.startsWith("com.ingenio.backend.entity.")) {
            return buildStubEntity(packageName, className);
        }
        if (fqcn.startsWith("com.ingenio.backend.mapper.")) {
            String entityName = className.endsWith("Mapper")
                    ? className.substring(0, className.length() - "Mapper".length())
                    : null;
            return buildStubMapper(packageName, className, entityName);
        }
        if (fqcn.startsWith("com.ingenio.backend.service.") && !fqcn.contains(".impl.")) {
            String entityName = className.endsWith("Service")
                    ? className.substring(0, className.length() - "Service".length())
                    : null;
            boolean hasEntity = entityName != null && schemaEntities.contains(entityName);
            return buildStubService(packageName, className, entityName, hasEntity);
        }

        return buildStubPlain(packageName, className);
    }

    /**
     * 构建DTO占位类
     */
    private String buildStubDto(String packageName, String className) {
        return String.format("""
                package %s;

                /**
                 * %s
                 *
                 * 是什么：%s 数据传输对象。
                 * 做什么：用于接口层/服务层之间的数据承载。
                 * 为什么：避免直接暴露实体并保持接口稳定。
                 */
                public class %s {
                }
                """, packageName, className, className, className).trim() + "\n";
    }

    /**
     * 构建实体占位类
     */
    private String buildStubEntity(String packageName, String className) {
        return String.format("""
                package %s;

                import java.io.Serializable;

                /**
                 * %s
                 *
                 * 是什么：%s 实体类。
                 * 做什么：承载持久化数据结构的最小骨架。
                 * 为什么：保证编译通过并便于后续扩展字段。
                 */
                public class %s implements Serializable {
                    private static final long serialVersionUID = 1L;
                }
                """, packageName, className, className, className).trim() + "\n";
    }

    /**
     * 构建Mapper占位接口
     */
    private String buildStubMapper(String packageName, String className, String entityName) {
        boolean hasEntity = entityName != null && !entityName.isBlank();
        String entity = hasEntity ? entityName : "Object";
        String entityImport = hasEntity ? ("import com.ingenio.backend.entity." + entity + ";\n") : "";
        return String.format("""
                package %s;

                import com.baomidou.mybatisplus.core.mapper.BaseMapper;
                %simport org.apache.ibatis.annotations.Mapper;

                /**
                 * %s
                 *
                 * 是什么：%s 数据访问接口。
                 * 做什么：提供MyBatis-Plus基础CRUD能力。
                 * 为什么：隔离持久层细节并统一访问入口。
                 */
                @Mapper
                public interface %s extends BaseMapper<%s> {
                }
                """, packageName, entityImport, className, entity, className, className, entity).trim() + "\n";
    }

    /**
     * 构建Service占位接口
     */
    private String buildStubService(String packageName, String className, String entityName, boolean hasEntity) {
        if (hasEntity && entityName != null && !entityName.isBlank()) {
            return String.format("""
                    package %s;

                    import com.baomidou.mybatisplus.extension.service.IService;
                    import com.ingenio.backend.entity.%s;

                    /**
                     * %s
                     *
                     * 是什么：%s 业务服务接口。
                     * 做什么：定义%s相关的业务能力。
                     * 为什么：便于实现类注入与后续扩展。
                     */
                    public interface %s extends IService<%s> {
                    }
                    """, packageName, entityName, className, entityName, entityName, className, entityName).trim() + "\n";
        }

        return String.format("""
                package %s;

                /**
                 * %s
                 *
                 * 是什么：业务服务接口。
                 * 做什么：承载模块级业务能力入口。
                 * 为什么：为实现类与调用方提供解耦抽象。
                 */
                public interface %s {
                }
                """, packageName, className, className).trim() + "\n";
    }

    /**
     * 构建通用占位类
     */
    private String buildStubPlain(String packageName, String className) {
        return String.format("""
                package %s;

                /**
                 * %s
                 *
                 * 是什么：基础占位类型。
                 * 做什么：保证编译通过并预留扩展空间。
                 * 为什么：生成流程依赖该类型但缺失实现。
                 */
                public class %s {
                }
                """, packageName, className, className).trim() + "\n";
    }

    /**
     * 将类名转换为源码文件路径
     */
    private String toFilePath(String fqcn) {
        return "src/main/java/" + fqcn.replace('.', '/') + ".java";
    }

    /**
     * 解析AI返回的Java文件内容
     * 支持格式：// === 文件: FileName.java ===
     */
    private List<G3ArtifactEntity> parseJavaFiles(String content, java.util.UUID jobId, int generationRound,
            String basePath) {
        List<G3ArtifactEntity> artifacts = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return artifacts;
        }

        // 移除可能的markdown代码块标记
        content = cleanMarkdownBlocks(content);

        // 使用正则匹配文件分隔符
        Pattern filePattern = Pattern.compile(
                "(?:// ===\\s*文件[：:]\\s*|// === File:\\s*)(\\w+\\.java)\\s*===",
                Pattern.CASE_INSENSITIVE);

        Matcher matcher = filePattern.matcher(content);
        List<Integer> positions = new ArrayList<>();
        List<String> fileNames = new ArrayList<>();

        while (matcher.find()) {
            positions.add(matcher.start());
            fileNames.add(matcher.group(1));
        }

        // 解析每个文件内容
        for (int i = 0; i < positions.size(); i++) {
            int start = positions.get(i);
            int end = (i + 1 < positions.size()) ? positions.get(i + 1) : content.length();

            String fileContent = content.substring(start, end);
            // 移除文件头标记
            fileContent = filePattern.matcher(fileContent).replaceFirst("").trim();
            fileContent = sanitizeJavaSource(fileContent);

            String fileName = fileNames.get(i);

            // Prevent overwriting Common classes provided by system (Standardization)
            if (fileName.equals("Result.java") || fileName.equals("BaseResponse.java")) {
                continue;
            }

            String filePath = basePath + fileName;

            G3ArtifactEntity artifact = G3ArtifactEntity.create(
                    jobId,
                    filePath,
                    fileContent,
                    G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                    generationRound);

            artifacts.add(artifact);
        }

        // 如果没有匹配到文件分隔符，尝试按package语句分割
        if (artifacts.isEmpty() && content.contains("package ")) {
            artifacts.addAll(parseByPackageStatement(content, jobId, generationRound, basePath));
        }

        return artifacts;
    }

    /**
     * 按package语句分割代码
     */
    private List<G3ArtifactEntity> parseByPackageStatement(String content, java.util.UUID jobId, int generationRound,
            String basePath) {
        List<G3ArtifactEntity> artifacts = new ArrayList<>();

        // 匹配 public class/interface ClassName
        Pattern classPattern = Pattern.compile(
                "package\\s+[\\w.]+;[\\s\\S]*?(?=package\\s+|$)");

        Matcher matcher = classPattern.matcher(content);

        while (matcher.find()) {
            String classContent = matcher.group().trim();

            // 提取类名
            Pattern namePattern = Pattern.compile("(?:public\\s+)?(?:class|interface|enum)\\s+(\\w+)");
            Matcher nameMatcher = namePattern.matcher(classContent);

            if (nameMatcher.find()) {
                String className = nameMatcher.group(1);
                String filePath = basePath + className + ".java";
                classContent = sanitizeJavaSource(classContent);

                G3ArtifactEntity artifact = G3ArtifactEntity.create(
                        jobId,
                        filePath,
                        classContent,
                        G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                        generationRound);

                artifacts.add(artifact);
            }
        }

        return artifacts;
    }

    /**
     * 清理Java源文件内容，避免模型把“清单/说明/符号”附加到文件末尾导致编译失败。
     *
     * 规则（保守裁剪）：
     * - 若存在最后一个 '}'，则仅保留到该位置（包含该字符）
     * - 仅裁掉文件尾部内容，避免误伤中文注释等正文
     */
    private String sanitizeJavaSource(String content) {
        if (content == null)
            return "";
        String normalized = content.replace("\r", "").trim();

        // 若模型在文件头部插入了说明文字，裁剪到第一个 package 声明处（保守处理，优先保证可编译）
        int packagePos = normalized.indexOf("package ");
        if (packagePos > 0) {
            normalized = normalized.substring(packagePos).trim();
        }

        int lastBrace = normalized.lastIndexOf('}');
        if (lastBrace >= 0 && lastBrace + 1 < normalized.length()) {
            normalized = normalized.substring(0, lastBrace + 1).trim();
        }

        normalized = rewriteMapStructBeanConverter(normalized);
        normalized = rewriteMultiplePublicTypes(normalized);
        normalized = injectMissingLombokImports(normalized);
        normalized = rewriteSpringDataPagination(normalized);
        normalized = rewriteMyBatisPlusPageMismatch(normalized);
        normalized = rewriteMyBatisPlusPageConvertReturn(normalized);
        normalized = injectCommonImports(normalized);

        return normalized + "\n";
    }

    /**
     * 修复单文件多 public 类型导致的编译失败（保守降级）
     *
     * 是什么：将同一文件中除第一个外的 public 类型降级为包内可见。
     * 做什么：避免“public class 应位于同名文件”的编译错误。
     * 为什么：模型偶发输出多个public类型，先保证可编译以完成闭环。
     */
    private String rewriteMultiplePublicTypes(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        Pattern pattern = Pattern.compile("\\bpublic\\s+(class|interface|enum|record)\\b");
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        int count = 0;
        while (matcher.find()) {
            count++;
            if (count == 1) {
                matcher.appendReplacement(sb, matcher.group(0));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(1)));
            }
        }
        matcher.appendTail(sb);
        return count > 1 ? sb.toString() : content;
    }

    /**
     * 将 Spring Data 的分页类型（org.springframework.data.domain.*）改写为 MyBatis-Plus 分页实现。
     *
     * 覆盖场景：
     * - import org.springframework.data.domain.Page / PageImpl / PageRequest
     * - return new PageImpl<>(records, PageRequest.of(...), total)
     *
     * 说明：
     * - 当前生成工程以 MyBatis-Plus 为主，不引入 Spring Data 依赖，直接使用会导致编译失败
     * - 该改写为“生成后防线”，用于提升多轮修复收敛与一次性编译通过率
     */
    private String rewriteSpringDataPagination(String content) {
        if (content == null || content.isBlank())
            return content;

        if (!content.contains("org.springframework.data.domain")) {
            return content;
        }

        String updated = content;

        // 1) 移除 Spring Data 分页相关 import
        updated = updated
                .replace("import org.springframework.data.domain.Page;\n", "")
                .replace("import org.springframework.data.domain.PageImpl;\n", "")
                .replace("import org.springframework.data.domain.PageRequest;\n", "")
                .replace("import org.springframework.data.domain.Pageable;\n", "");

        // 2) 确保 MyBatis-Plus Page import 存在（Controller 常见缺失）
        if (updated.contains("Page<")
                && !updated.contains("import com.baomidou.mybatisplus.extension.plugins.pagination.Page;")) {
            // 尽量插在现有 import 末尾
            updated = updated.replaceFirst(
                    "(?m)^(import\\s+.+;\\s*)$",
                    "$1\nimport com.baomidou.mybatisplus.extension.plugins.pagination.Page;\n");
        }

        // 3) 将 PageImpl 返回改写为 MyBatis-Plus Page
        // 解析关键变量：recordsVar、iPageVar、dtoType
        String dtoType = null;
        java.util.regex.Matcher sig = java.util.regex.Pattern
                .compile("public\\s+Page<\\s*(\\w+)\\s*>\\s+\\w+\\s*\\(", java.util.regex.Pattern.MULTILINE)
                .matcher(updated);
        if (sig.find()) {
            dtoType = sig.group(1);
        }

        String iPageVar = "result";
        java.util.regex.Matcher iPageMatcher = java.util.regex.Pattern
                .compile("IPage<[^>]+>\\s+(\\w+)\\s*=", java.util.regex.Pattern.MULTILINE)
                .matcher(updated);
        while (iPageMatcher.find()) {
            iPageVar = iPageMatcher.group(1);
        }

        java.util.regex.Matcher pageImplMatcher = java.util.regex.Pattern
                .compile(
                        "return\\s+new\\s+PageImpl<[^>]*>\\(\\s*(\\w+)\\s*,\\s*PageRequest\\.of\\([\\s\\S]*?\\)\\s*,\\s*([^\\)]+)\\);",
                        java.util.regex.Pattern.MULTILINE)
                .matcher(updated);

        if (pageImplMatcher.find()) {
            String recordsVar = pageImplMatcher.group(1);
            String totalExpr = pageImplMatcher.group(2).trim();

            String type = dtoType != null ? dtoType : "Object";
            String replacement = ""
                    + "Page<" + type + "> dtoPage = new Page<>(" + iPageVar + ".getCurrent(), " + iPageVar
                    + ".getSize(), " + totalExpr + ");\n"
                    + "        dtoPage.setRecords(" + recordsVar + ");\n"
                    + "        return dtoPage;";

            updated = pageImplMatcher.replaceFirst(replacement);
        }

        return updated;
    }

    /**
     * 为使用了 Lombok 注解但缺少 import 的文件补齐导入，避免编译失败。
     *
     * 说明：
     * - 这是“生成后防线”，用于提升一次性编译通过率
     * - 仅补齐常见 Lombok 注解的 import，不改变业务逻辑
     */
    private String injectMissingLombokImports(String content) {
        if (content == null || content.isBlank())
            return content;

        // 若已使用通配符导入，则无需补齐
        if (content.contains("import lombok.*;")) {
            return content;
        }

        record LombokImport(String annotation, String importLine) {
        }
        List<LombokImport> candidates = List.of(
                new LombokImport("@Data", "import lombok.Data;"),
                new LombokImport("@Builder", "import lombok.Builder;"),
                new LombokImport("@NoArgsConstructor", "import lombok.NoArgsConstructor;"),
                new LombokImport("@AllArgsConstructor", "import lombok.AllArgsConstructor;"),
                new LombokImport("@RequiredArgsConstructor", "import lombok.RequiredArgsConstructor;"),
                new LombokImport("@Getter", "import lombok.Getter;"),
                new LombokImport("@Setter", "import lombok.Setter;"),
                new LombokImport("@EqualsAndHashCode", "import lombok.EqualsAndHashCode;"),
                new LombokImport("@ToString", "import lombok.ToString;"),
                new LombokImport("@Slf4j", "import lombok.extern.slf4j.Slf4j;"));

        List<String> missing = new ArrayList<>();
        for (LombokImport cand : candidates) {
            if (!content.contains(cand.annotation()))
                continue;
            if (content.contains(cand.importLine()))
                continue;
            missing.add(cand.importLine());
        }

        if (missing.isEmpty())
            return content;

        String[] lines = content.split("\n", -1);

        int packageLine = -1;
        int lastImportLine = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (packageLine < 0 && line.startsWith("package ") && line.endsWith(";")) {
                packageLine = i;
            }
            if (line.startsWith("import ") && line.endsWith(";")) {
                lastImportLine = i;
            }
        }

        // 没有 package 的情况直接返回（异常输出，避免破坏）
        if (packageLine < 0)
            return content;

        int insertAt = lastImportLine >= 0 ? (lastImportLine + 1) : (packageLine + 1);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == insertAt) {
                // 若紧贴 package 行且下一行非空，补一个空行再加 import
                if (lastImportLine < 0 && (i < lines.length) && !lines[i].trim().isEmpty()) {
                    sb.append("\n");
                }
                for (String imp : missing) {
                    sb.append(imp).append("\n");
                }
            }
            sb.append(lines[i]);
            if (i < lines.length - 1)
                sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 为常见的“缺少 import 导致编译失败”提供生成后补齐能力（提升一次性编译通过率）。
     *
     * 典型失败场景：
     * - 使用了 List/Map/Set/Instant 等类型但忘记 import
     * - 使用了 MyBatis-Plus 的 LambdaQueryWrapper/QueryWrapper 但忘记 import
     *
     * 说明：
     * - 这是“生成后防线”，不改变业务语义，仅做保守补齐
     * - 即使出现未使用 import，Maven 默认也不会因为未使用 import 而编译失败（且优先保证可编译）
     */
    private String injectCommonImports(String content) {
        if (content == null || content.isBlank())
            return content;

        // 若已使用通配符导入，则无需补齐
        if (content.contains("import java.util.*;") || content.contains("import java.time.*;")) {
            return content;
        }

        record CommonImport(String token, String importLine) {
        }
        List<CommonImport> candidates = List.of(
                new CommonImport("IPage", "import com.baomidou.mybatisplus.core.metadata.IPage;"),
                new CommonImport("List", "import java.util.List;"),
                new CommonImport("Map", "import java.util.Map;"),
                new CommonImport("Set", "import java.util.Set;"),
                new CommonImport("Collection", "import java.util.Collection;"),
                new CommonImport("UUID", "import java.util.UUID;"),
                new CommonImport("Instant", "import java.time.Instant;"),
                new CommonImport("LocalDateTime", "import java.time.LocalDateTime;"),
                new CommonImport("LocalDate", "import java.time.LocalDate;"),
                new CommonImport("BigDecimal", "import java.math.BigDecimal;"),
                new CommonImport("Stream", "import java.util.stream.Stream;"),
                new CommonImport("Collectors", "import java.util.stream.Collectors;"),
                new CommonImport("LambdaQueryWrapper",
                        "import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;"),
                new CommonImport("QueryWrapper", "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;"),
                new CommonImport("Wrappers", "import com.baomidou.mybatisplus.core.toolkit.Wrappers;"));

        List<String> missing = new ArrayList<>();
        for (CommonImport cand : candidates) {
            // 已经显式全限定名使用则跳过
            if (content.contains(cand.importLine()))
                continue;
            if (content.contains(cand.importLine().replace("import ", "").replace(";", "")))
                continue;

            // 仅在源文件中“看起来”使用过该标识符时补齐（保守：允许命中注释）
            Pattern tokenPattern = Pattern.compile("\\b" + Pattern.quote(cand.token()) + "\\b");
            if (!tokenPattern.matcher(content).find())
                continue;
            missing.add(cand.importLine());
        }

        if (missing.isEmpty())
            return content;

        String[] lines = content.split("\n", -1);
        int packageLine = -1;
        int lastImportLine = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (packageLine < 0 && line.startsWith("package ") && line.endsWith(";")) {
                packageLine = i;
            }
            if (line.startsWith("import ") && line.endsWith(";")) {
                lastImportLine = i;
            }
        }

        // 没有 package 的情况直接返回（异常输出，避免破坏）
        if (packageLine < 0)
            return content;

        int insertAt = lastImportLine >= 0 ? (lastImportLine + 1) : (packageLine + 1);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == insertAt) {
                if (lastImportLine < 0 && (i < lines.length) && !lines[i].trim().isEmpty()) {
                    sb.append("\n");
                }
                for (String imp : missing) {
                    sb.append(imp).append("\n");
                }
            }
            sb.append(lines[i]);
            if (i < lines.length - 1)
                sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 修复 MyBatis-Plus 分页类型不一致导致的泛型推断编译错误（提升一次性编译通过率）。
     *
     * 典型错误：
     * - Controller 方法签名返回 ResponseEntity<Page<DTO>>
     * - Service 返回 IPage<DTO>
     * - 触发 javac：inference variable T has incompatible bounds
     *
     * 兜底策略（保守）：
     * - 若发现 `ResponseEntity<Page<...>>` 且直接
     * `ResponseEntity.ok(taskService.xxx(...))`，
     * 则将返回类型改为 `ResponseEntity<IPage<...>>`（不改动业务逻辑）
     */
    private String rewriteMyBatisPlusPageMismatch(String content) {
        if (content == null || content.isBlank())
            return content;

        boolean hasResponseEntityPage = content.contains("ResponseEntity<Page<");
        boolean returnsFromService = content.contains("ResponseEntity.ok(taskService.");
        if (hasResponseEntityPage && returnsFromService) {
            return content.replace("ResponseEntity<Page<", "ResponseEntity<IPage<");
        }

        boolean hasResultPage = content.contains("Result<Page<");
        boolean returnsResultFromService = content.contains("Result.success(") && content.contains("taskService.");
        if (hasResultPage && returnsResultFromService) {
            return content.replace("Result<Page<", "Result<IPage<");
        }

        return content;
    }

    /**
     * 将 MapStruct 风格的 BeanConverter 改写为最小可用实现，避免缺少 MapStruct 依赖导致编译失败。
     *
     * 背景：
     * - 模型常常“自作主张”生成 `@Mapper interface BeanConverter` 并引用 `org.mapstruct.*`
     * - 生成工程默认不引入 MapStruct，会直接编译失败
     *
     * 兜底策略：
     * - 若检测到 BeanConverter + org.mapstruct，则替换为基于 Spring BeanUtils 的简单实现
     */
    private String rewriteMapStructBeanConverter(String content) {
        if (content == null || content.isBlank())
            return content;
        if (!content.contains("org.mapstruct"))
            return content;
        if (!content.contains("BeanConverter"))
            return content;

        // 仅在“看起来就是 BeanConverter 定义文件”时触发（避免误伤其他 Mapper）
        boolean looksLikeBeanConverterDefinition = Pattern.compile("\\b(interface|class)\\s+BeanConverter\\b")
                .matcher(content).find();
        if (!looksLikeBeanConverterDefinition) {
            return content;
        }

        return """
                package com.ingenio.backend.util;

                import org.springframework.beans.BeanUtils;
                import org.springframework.stereotype.Component;

                import java.util.ArrayList;
                import java.util.List;

                /**
                 * 对象转换/拷贝工具组件（G3 生成代码兜底）
                 *
                 * <p>用途：为生成的 Service 层提供最小可用的对象转换能力，避免引用缺失/不在依赖中的转换器导致编译失败。</p>
                 *
                 * <p>注意：该实现以“可编译、可运行”为第一目标；复杂映射建议在业务代码中显式编写。</p>
                 */
                @Component
                public class BeanConverter {

                    /**
                     * 将 source 转换为 targetClass 类型（基于属性名拷贝）
                     *
                     * @param source      源对象（可为空）
                     * @param targetClass 目标类型
                     * @param <T>         目标类型
                     * @return 目标对象（source 为空则返回 null）
                     */
                    public <T> T convert(Object source, Class<T> targetClass) {
                        if (source == null) {
                            return null;
                        }
                        try {
                            T target = targetClass.getDeclaredConstructor().newInstance();
                            BeanUtils.copyProperties(source, target);
                            return target;
                        } catch (Exception e) {
                            throw new IllegalStateException("对象转换失败: " + targetClass.getSimpleName(), e);
                        }
                    }

                    /**
                     * 将 source 的同名属性拷贝到 target
                     *
                     * @param source 源对象（可为空）
                     * @param target 目标对象（不可为空）
                     */
                    public void copyProperties(Object source, Object target) {
                        if (source == null || target == null) {
                            return;
                        }
                        BeanUtils.copyProperties(source, target);
                    }

                    /**
                     * 列表转换（逐项 convert）
                     *
                     * @param sourceList  源列表（可为空）
                     * @param targetClass 目标类型
                     * @param <S>         源类型
                     * @param <T>         目标类型
                     * @return 目标列表（sourceList 为空返回空列表）
                     */
                    public <S, T> List<T> convertList(List<S> sourceList, Class<T> targetClass) {
                        if (sourceList == null || sourceList.isEmpty()) {
                            return List.of();
                        }
                        List<T> result = new ArrayList<>(sourceList.size());
                        for (S item : sourceList) {
                            result.add(convert(item, targetClass));
                        }
                        return result;
                    }
                }
                """.trim();
    }

    /**
     * 修复 MyBatis-Plus Page.convert(...) 的返回类型不匹配问题（提升一次性编译通过率）。
     *
     * 背景：
     * - `Page<T>.convert(Function)` 在签名上返回 `IPage<R>`（而不是 `Page<R>`）
     * - 模型常会写出 `public Page<DTO> xxx() { return page.convert(...); }` 触发编译失败
     *
     * 兜底策略（保守）：
     * - 若方法返回类型是 `Page<DTO>` 且直接 `return something.convert(...)`，
     * 则对返回表达式加一个显式强转：`return (Page<DTO>) something.convert(...)`
     *
     * 说明：
     * - 对于 MyBatis-Plus 的 Page 实现，这个强转在实践中通常成立（convert 会返回 Page 的实现）
     * - 该兜底优先保证“可编译”；更推荐在生成时让 Service/Controller 统一使用 `IPage<DTO>` 返回
     */
    private String rewriteMyBatisPlusPageConvertReturn(String content) {
        if (content == null || content.isBlank())
            return content;
        if (!content.contains(".convert("))
            return content;
        if (!content.contains("public Page<"))
            return content;

        Matcher m = Pattern.compile("public\\s+Page<\\s*(\\w+)\\s*>\\s+\\w+\\s*\\(").matcher(content);
        if (!m.find())
            return content;
        String dtoType = m.group(1);

        // 已经加过强转则跳过
        if (content.contains("return (Page<" + dtoType + ">)")) {
            return content;
        }

        // 仅处理“直接 return ...convert(...)”的场景，避免误伤其他 return
        return content.replaceFirst(
                "(?m)^\\s*return\\s+([^;]+\\.convert\\([^;]+\\))\\s*;\\s*$",
                "        return (Page<" + dtoType + ">) $1;");
    }

    /**
     * 清理Markdown代码块标记
     */
    private String cleanMarkdownBlocks(String content) {
        if (content == null)
            return "";

        // 移除 ```java 和 ``` 标记
        content = content.replaceAll("```java\\s*", "");
        content = content.replaceAll("```\\s*", "");

        return content.trim();
    }

    /**
     * 合并多个产物的内容
     */
    private String combineArtifactsContent(List<G3ArtifactEntity> artifacts) {
        StringBuilder sb = new StringBuilder();
        for (G3ArtifactEntity artifact : artifacts) {
            sb.append("// === ").append(artifact.getFileName()).append(" ===\n");
            sb.append(artifact.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}
