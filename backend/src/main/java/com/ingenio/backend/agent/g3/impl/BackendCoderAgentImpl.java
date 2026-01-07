package com.ingenio.backend.agent.g3.impl;

import com.ingenio.backend.agent.g3.ICoderAgent;
import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.ai.AIProviderFactory;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.service.blueprint.BlueprintPromptBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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
@Slf4j
@Component
public class BackendCoderAgentImpl implements ICoderAgent {

    private static final String AGENT_NAME = "BackendCoderAgent";
    private static final String TARGET_TYPE = "backend";
    private static final String TARGET_LANGUAGE = "java";

    private final AIProviderFactory aiProviderFactory;
    private final BlueprintPromptBuilder blueprintPromptBuilder;

    /**
     * 公共代码规范提示词 - 精选自项目 .claude/CLAUDE.md
     * 这些规范适用于所有生成的Java代码
     */
    private static final String CODE_STANDARDS_PROMPT = """

        ## 代码生成核心规范

        ### 代码生成八荣八耻
        - 以明确定义职责为荣，以模糊职责界限为耻
        - 以复用可靠模块为荣，以重复造轮子为耻
        - 以实现验证闭环为荣，以忽略测试验证为耻
        - 以优化算法架构为荣，以牺牲代码性能为耻
        - 以注重长期质量为荣，以追求短期快捷为耻

        ### SOLID设计原则（必须遵守）
        - **S - 单一职责**：一个类只有一个变化原因
        - **O - 开闭原则**：对扩展开放，对修改关闭
        - **L - 里氏替换**：子类型必须能替换基类型
        - **I - 接口隔离**：客户端不依赖不需要的接口
        - **D - 依赖倒置**：依赖抽象而非实现

        ### Java语言规范
        - 遵循 Google Java Style Guide
        - 使用 Lombok 减少样板代码（@Data, @Builder, @RequiredArgsConstructor）
        - 构造器注入优于字段注入
        - 使用 Optional 处理可能为空的返回值
        - 禁止 Magic Number，使用常量定义

        ### 注释规范（强制要求）
        - **所有类必须有类级JavaDoc**：描述职责和用途
        - **所有public方法必须有JavaDoc**：描述参数、返回值、异常
        - **使用中文注释**：便于团队理解
        - **完整性**：描述"是什么"、"做什么"、"为什么"
        - **准确性**：注释与代码同步更新

        ### 安全编码规范
        - **SQL注入防护**：使用参数化查询或MyBatis-Plus的方法
        - **日志安全**：禁止记录明文密码、Token、敏感信息
        - **输入校验**：使用 @Validated 进行参数校验
        - **异常处理**：细粒度异常处理，不吞没异常

        ### 质量标准
        - 代码编译必须通过（0 errors, 0 warnings）
        - 遵循分层架构：Controller -> Service -> Mapper -> Entity
        - 业务逻辑放在Service层，Controller只做请求转发
        - 使用 @Transactional 管理事务边界

        """;

    /**
     * 代码生成提示词模板 - 实体类
     * 优化版本: 添加完整示例、UUID显式生成说明、JSONB字段处理、枚举定义
     */
    private static final String ENTITY_PROMPT_TEMPLATE = """
        你是一个专业的Java开发工程师，使用Claude模型进行代码生成。请根据以下数据库Schema生成MyBatis-Plus实体类。

        %s

        ## 数据库Schema
        ```sql
        %s
        ```

        ## 核心要求（Critical）

        ### 1. UUID主键处理（⚠️ 重要）
        **PostgreSQL的DEFAULT gen_random_uuid()不生效！必须在Java代码中显式生成UUID。**

        正确做法：
        ```java
        @TableId(value = "id", type = IdType.ASSIGN_UUID)
        @TableField(typeHandler = UUIDv8TypeHandler.class)
        private UUID id;
        ```

        ### 2. 基础注解
        - @Data, @Builder, @NoArgsConstructor, @AllArgsConstructor
        - @TableName(value = "表名", autoResultMap = true)
        - @TableField 用于字段映射

        ### 3. 字段类型映射

        | PostgreSQL类型 | Java类型 | MyBatis-Plus注解 |
        |--------------|----------|----------------|
        | UUID | java.util.UUID | @TableField(typeHandler = UUIDv8TypeHandler.class) |
        | VARCHAR | String | @TableField("column_name") |
        | TEXT | String | @TableField("column_name") |
        | TIMESTAMP | java.time.Instant | @TableField("created_at") |
        | JSONB | String | @TableField("metadata") |
        | BOOLEAN | Boolean | @TableField("is_active") |

        ### 4. 特殊字段处理

        **枚举字段**：定义内部枚举类
        ```java
        @TableField("status")
        private String status;

        public enum Status {
            PENDING("PENDING", "待处理"),
            COMPLETED("COMPLETED", "已完成");

            private final String value;
            private final String description;

            Status(String value, String description) {
                this.value = value;
                this.description = description;
            }
        }
        ```

        **JSONB字段**：使用String存储
        ```java
        @TableField("metadata")
        private String metadata;  // 存储JSON字符串
        ```

        ## 输出格式要求

        ⚠️ **重要**：**不要**使用```java标记包裹代码。

        使用以下格式分隔多个文件：
        // === 文件: EntityName.java ===
        package com.ingenio.backend.entity.generated;
        // 代码内容

        // === 文件: AnotherEntity.java ===
        package com.ingenio.backend.entity.generated;
        // 代码内容

        ## 示例参考（Task实体类）

        // === 文件: TaskEntity.java ===
        package com.ingenio.backend.entity.generated;

        import com.baomidou.mybatisplus.annotation.*;
        import com.ingenio.backend.config.UUIDv8TypeHandler;
        import lombok.AllArgsConstructor;
        import lombok.Builder;
        import lombok.Data;
        import lombok.NoArgsConstructor;

        import java.time.Instant;
        import java.util.UUID;

        /**
         * 待办事项实体类
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @TableName(value = "tasks", autoResultMap = true)
        public class TaskEntity {

            /**
             * 主键ID
             */
            @TableId(value = "id", type = IdType.ASSIGN_UUID)
            @TableField(typeHandler = UUIDv8TypeHandler.class)
            private UUID id;

            /**
             * 标题
             */
            @TableField("title")
            private String title;

            /**
             * 状态
             */
            @TableField("status")
            private String status;

            /**
             * 优先级
             */
            @TableField("priority")
            private String priority;

            /**
             * 创建时间
             */
            @TableField(value = "created_at", fill = FieldFill.INSERT)
            private Instant createdAt;

            /**
             * 更新时间
             */
            @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
            private Instant updatedAt;

            /**
             * 状态枚举
             */
            public enum Status {
                PENDING("PENDING", "待处理"),
                COMPLETED("COMPLETED", "已完成");

                private final String value;
                private final String description;

                Status(String value, String description) {
                    this.value = value;
                    this.description = description;
                }

                public String getValue() {
                    return value;
                }
            }
        }

        ## 质量检查清单
        - [ ] UUID字段使用UUIDv8TypeHandler
        - [ ] 所有字段都有@TableField注解
        - [ ] 时间字段使用Instant类型
        - [ ] 枚举字段定义了内部枚举类
        - [ ] 输出格式正确（// === 文件: xxx ===）
        - [ ] 无```java标记包裹

        现在请根据Schema生成实体类。
        """;

    /**
     * 代码生成提示词模板 - Mapper接口
     */
    private static final String MAPPER_PROMPT_TEMPLATE = """
        你是一个专业的Java开发工程师，使用Claude模型进行代码生成。请根据以下实体类生成MyBatis-Plus Mapper接口。

        %s

        ## 实体类定义
        ```java
        %s
        ```

        ## 输出要求
        1. 继承 BaseMapper<EntityName>
        2. 使用 @Mapper 注解
        3. 添加常用的自定义查询方法（如按条件分页、按关联ID查询等）
        4. 添加完整的中文JavaDoc注释
        5. 包名使用 com.ingenio.backend.mapper.generated

        ## 输出格式
        请为每个实体生成一个Mapper接口，使用以下格式：
        ```java
        // === 文件: EntityNameMapper.java ===
        package com.ingenio.backend.mapper.generated;
        // 代码内容
        ```
        """;

    /**
     * 代码生成提示词模板 - DTO类
     * 用于生成请求/响应DTO，解决编译时缺少DTO类的问题
     */
    private static final String DTO_PROMPT_TEMPLATE = """
        你是一个专业的Java开发工程师，使用Claude模型进行代码生成。请根据以下OpenAPI契约和实体类生成DTO类。

        %s

        ## OpenAPI契约
        ```yaml
        %s
        ```

        ## 实体类定义
        ```java
        %s
        ```

        ## 核心要求（Critical）

        ### 1. DTO类型（必须生成）
        为每个实体生成以下DTO：
        - **EntityDTO**：响应DTO，用于返回给前端
        - **EntityCreateRequest**：创建请求DTO
        - **EntityUpdateRequest**：更新请求DTO
        - **EntityQueryRequest**：查询请求DTO（可选）

        ### 2. 注解规范（Spring Boot 3 + Jakarta EE）
        ⚠️ **重要**：必须使用 `jakarta.validation` 包，不要使用 `javax.validation`！

        正确导入：
        ```java
        import jakarta.validation.constraints.NotBlank;
        import jakarta.validation.constraints.NotNull;
        import jakarta.validation.constraints.Size;
        import jakarta.validation.constraints.Email;
        ```

        ### 3. Lombok注解
        - @Data, @Builder, @NoArgsConstructor, @AllArgsConstructor

        ### 4. 示例参考

        // === 文件: UserDTO.java ===
        package com.ingenio.backend.dto.generated;

        import lombok.AllArgsConstructor;
        import lombok.Builder;
        import lombok.Data;
        import lombok.NoArgsConstructor;

        import java.time.Instant;
        import java.util.UUID;

        /**
         * 用户响应DTO
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public class UserDTO {
            private UUID id;
            private String username;
            private String email;
            private Instant createdAt;
        }

        // === 文件: UserCreateRequest.java ===
        package com.ingenio.backend.dto.generated;

        import jakarta.validation.constraints.Email;
        import jakarta.validation.constraints.NotBlank;
        import jakarta.validation.constraints.Size;
        import lombok.AllArgsConstructor;
        import lombok.Builder;
        import lombok.Data;
        import lombok.NoArgsConstructor;

        /**
         * 用户创建请求DTO
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public class UserCreateRequest {
            @NotBlank(message = "用户名不能为空")
            @Size(min = 2, max = 50, message = "用户名长度必须在2-50之间")
            private String username;

            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            private String email;

            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 100, message = "密码长度必须在6-100之间")
            private String password;
        }

        ## 输出格式要求

        ⚠️ **重要**：**不要**使用```java标记包裹代码。

        使用以下格式分隔多个文件：
        // === 文件: EntityDTO.java ===
        package com.ingenio.backend.dto.generated;
        // 代码内容

        // === 文件: EntityCreateRequest.java ===
        package com.ingenio.backend.dto.generated;
        // 代码内容

        ## 质量检查清单
        - [ ] 使用 jakarta.validation（不是 javax.validation）
        - [ ] 所有必填字段有 @NotBlank 或 @NotNull 注解
        - [ ] 字符串字段有 @Size 注解
        - [ ] 邮箱字段有 @Email 注解
        - [ ] 输出格式正确（// === 文件: xxx ===）
        - [ ] 无```java标记包裹

        现在请根据契约和实体类生成所有必要的DTO类。
        """;

    /**
     * 代码生成提示词模板 - Service层
     */
    private static final String SERVICE_PROMPT_TEMPLATE = """
        你是一个专业的Java开发工程师，使用Claude模型进行代码生成。请根据以下OpenAPI契约和Mapper接口生成Service层代码。

        %s

        ## OpenAPI契约
        ```yaml
        %s
        ```

        ## Mapper接口
        ```java
        %s
        ```

        ## DTO类
        ```java
        %s
        ```

        ## 输出要求
        1. 创建Service接口和ServiceImpl实现类
        2. 使用 @Service 和 @Transactional 注解
        3. 实现CRUD基础方法
        4. 根据API契约实现业务方法
        5. 使用构造器注入（@RequiredArgsConstructor）
        6. 添加完整的中文JavaDoc注释
        7. 包名使用 com.ingenio.backend.service.generated
        8. ⚠️ **重要**：导入DTO时使用 com.ingenio.backend.dto.generated 包
        9. ⚠️ **重要**：密码加密使用简单的哈希方法（不使用PasswordEncoder），或直接存储（由Controller层处理）

        ## 输出格式
        请生成Service接口和实现类，使用以下格式：

        ⚠️ **重要**：**不要**使用```java标记包裹代码。

        // === 文件: IEntityNameService.java ===
        package com.ingenio.backend.service.generated;
        // 接口代码

        // === 文件: EntityNameServiceImpl.java ===
        package com.ingenio.backend.service.generated;
        // 实现类代码
        """;

    /**
     * 代码生成提示词模板 - Controller层
     */
    private static final String CONTROLLER_PROMPT_TEMPLATE = """
        你是一个专业的Java开发工程师，使用Claude模型进行代码生成。请根据以下OpenAPI契约和Service接口生成REST Controller。

        %s

        ## OpenAPI契约
        ```yaml
        %s
        ```

        ## Service接口
        ```java
        %s
        ```

        ## DTO类
        ```java
        %s
        ```

        ## 输出要求
        1. 使用 @RestController 和 @RequestMapping 注解
        2. 严格按照OpenAPI契约定义的路径和方法
        3. 使用 @GetMapping/@PostMapping/@PutMapping/@DeleteMapping
        4. 添加 @Validated 参数校验
        5. 统一使用 ResponseEntity 返回
        6. 使用构造器注入（@RequiredArgsConstructor）
        7. 添加完整的中文JavaDoc注释
        8. 包名使用 com.ingenio.backend.controller.generated
        9. ⚠️ **重要**：导入DTO时使用 com.ingenio.backend.dto.generated 包
        10. ⚠️ **重要**：使用 jakarta.validation（不是 javax.validation）

        ## 示例导入（Spring Boot 3 + Jakarta EE）
        ```java
        import jakarta.validation.Valid;
        import com.ingenio.backend.dto.generated.UserDTO;
        import com.ingenio.backend.dto.generated.UserCreateRequest;
        ```

        ## 输出格式
        请生成Controller类，使用以下格式：

        ⚠️ **重要**：**不要**使用```java标记包裹代码。

        // === 文件: EntityNameController.java ===
        package com.ingenio.backend.controller.generated;
        // 代码内容
        """;

    public BackendCoderAgentImpl(
            AIProviderFactory aiProviderFactory,
            BlueprintPromptBuilder blueprintPromptBuilder) {
        this.aiProviderFactory = aiProviderFactory;
        this.blueprintPromptBuilder = blueprintPromptBuilder;
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

            AIProvider aiProvider = aiProviderFactory.getProvider();
            if (!aiProvider.isAvailable()) {
                return CoderResult.failure("AI提供商不可用");
            }

            List<G3ArtifactEntity> artifacts = new ArrayList<>();

            // 1. 生成实体类
            logConsumer.accept(G3LogEntry.info(getRole(), "正在生成实体类..."));
            List<G3ArtifactEntity> entityArtifacts = generateEntities(job, dbSchemaSql, aiProvider, generationRound, logConsumer);
            artifacts.addAll(entityArtifacts);
            logConsumer.accept(G3LogEntry.success(getRole(), "实体类生成完成，共 " + entityArtifacts.size() + " 个文件"));

            // 2. 生成Mapper接口
            logConsumer.accept(G3LogEntry.info(getRole(), "正在生成Mapper接口..."));
            String entityCode = combineArtifactsContent(entityArtifacts);
            List<G3ArtifactEntity> mapperArtifacts = generateMappers(job, entityCode, aiProvider, generationRound, logConsumer);
            artifacts.addAll(mapperArtifacts);
            logConsumer.accept(G3LogEntry.success(getRole(), "Mapper接口生成完成，共 " + mapperArtifacts.size() + " 个文件"));

            // 3. 生成DTO类（解决编译时缺少DTO类的问题）
            logConsumer.accept(G3LogEntry.info(getRole(), "正在生成DTO类..."));
            List<G3ArtifactEntity> dtoArtifacts = generateDTOs(job, contractYaml, entityCode, aiProvider, generationRound, logConsumer);
            artifacts.addAll(dtoArtifacts);
            logConsumer.accept(G3LogEntry.success(getRole(), "DTO类生成完成，共 " + dtoArtifacts.size() + " 个文件"));
            String dtoCode = combineArtifactsContent(dtoArtifacts);

            // 4. 生成Service层
            logConsumer.accept(G3LogEntry.info(getRole(), "正在生成Service层..."));
            String mapperCode = combineArtifactsContent(mapperArtifacts);
            List<G3ArtifactEntity> serviceArtifacts = generateServices(job, contractYaml, mapperCode, dtoCode, aiProvider, generationRound, logConsumer);
            artifacts.addAll(serviceArtifacts);
            logConsumer.accept(G3LogEntry.success(getRole(), "Service层生成完成，共 " + serviceArtifacts.size() + " 个文件"));

            // 5. 生成Controller层
            logConsumer.accept(G3LogEntry.info(getRole(), "正在生成Controller层..."));
            String serviceCode = combineArtifactsContent(serviceArtifacts.stream()
                    .filter(a -> a.getFilePath().contains("Service.java") && !a.getFilePath().contains("Impl"))
                    .toList());
            List<G3ArtifactEntity> controllerArtifacts = generateControllers(job, contractYaml, serviceCode, dtoCode, aiProvider, generationRound, logConsumer);
            artifacts.addAll(controllerArtifacts);
            logConsumer.accept(G3LogEntry.success(getRole(), "Controller层生成完成，共 " + controllerArtifacts.size() + " 个文件"));

            // 6. 生成pom.xml依赖补充
            logConsumer.accept(G3LogEntry.info(getRole(), "正在生成项目配置..."));
            G3ArtifactEntity pomArtifact = generatePomFragment(job, generationRound);
            artifacts.add(pomArtifact);

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
        String prompt = String.format(ENTITY_PROMPT_TEMPLATE, CODE_STANDARDS_PROMPT + blueprintConstraint, dbSchemaSql);
        AIProvider.AIResponse response = aiProvider.generate(prompt,
                AIProvider.AIRequest.builder()
                        .temperature(0.2)
                        .maxTokens(8000)
                        .build());

        return parseJavaFiles(response.content(), job.getId(), generationRound,
                "src/main/java/com/ingenio/backend/entity/generated/");
    }

    /**
     * 生成Mapper接口
     */
    private List<G3ArtifactEntity> generateMappers(
            G3JobEntity job,
            String entityCode,
            AIProvider aiProvider,
            int generationRound,
            Consumer<G3LogEntry> logConsumer) {

        String blueprintConstraint = shouldEnableBlueprint(job)
                ? blueprintPromptBuilder.buildEntityConstraint(job.getBlueprintSpec())
                : "";
        String prompt = String.format(MAPPER_PROMPT_TEMPLATE, CODE_STANDARDS_PROMPT + blueprintConstraint, entityCode);
        AIProvider.AIResponse response = aiProvider.generate(prompt,
                AIProvider.AIRequest.builder()
                        .temperature(0.2)
                        .maxTokens(4000)
                        .build());

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
            AIProvider aiProvider,
            int generationRound,
            Consumer<G3LogEntry> logConsumer) {

        String blueprintConstraint = shouldEnableBlueprint(job)
                ? blueprintPromptBuilder.buildEntityConstraint(job.getBlueprintSpec())
                : "";
        String prompt = String.format(DTO_PROMPT_TEMPLATE, CODE_STANDARDS_PROMPT + blueprintConstraint, contractYaml, entityCode);
        AIProvider.AIResponse response = aiProvider.generate(prompt,
                AIProvider.AIRequest.builder()
                        .temperature(0.2)
                        .maxTokens(8000)
                        .build());

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
            AIProvider aiProvider,
            int generationRound,
            Consumer<G3LogEntry> logConsumer) {

        String blueprintConstraint = shouldEnableBlueprint(job)
                ? blueprintPromptBuilder.buildServiceConstraint(job.getBlueprintSpec())
                : "";
        String prompt = String.format(SERVICE_PROMPT_TEMPLATE, CODE_STANDARDS_PROMPT + blueprintConstraint, contractYaml, mapperCode, dtoCode);
        AIProvider.AIResponse response = aiProvider.generate(prompt,
                AIProvider.AIRequest.builder()
                        .temperature(0.2)
                        .maxTokens(8000)
                        .build());

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
            AIProvider aiProvider,
            int generationRound,
            Consumer<G3LogEntry> logConsumer) {

        String blueprintConstraint = shouldEnableBlueprint(job)
                ? blueprintPromptBuilder.buildServiceConstraint(job.getBlueprintSpec())
                : "";
        String prompt = String.format(CONTROLLER_PROMPT_TEMPLATE, CODE_STANDARDS_PROMPT + blueprintConstraint, contractYaml, serviceCode, dtoCode);
        AIProvider.AIResponse response = aiProvider.generate(prompt,
                AIProvider.AIRequest.builder()
                        .temperature(0.2)
                        .maxTokens(6000)
                        .build());

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
                generationRound
        );
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
     * 解析AI返回的Java文件内容
     * 支持格式：// === 文件: FileName.java ===
     */
    private List<G3ArtifactEntity> parseJavaFiles(String content, java.util.UUID jobId, int generationRound, String basePath) {
        List<G3ArtifactEntity> artifacts = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return artifacts;
        }

        // 移除可能的markdown代码块标记
        content = cleanMarkdownBlocks(content);

        // 使用正则匹配文件分隔符
        Pattern filePattern = Pattern.compile(
                "(?:// ===\\s*文件[：:]\\s*|// === File:\\s*)(\\w+\\.java)\\s*===",
                Pattern.CASE_INSENSITIVE
        );

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

            String fileName = fileNames.get(i);
            String filePath = basePath + fileName;

            G3ArtifactEntity artifact = G3ArtifactEntity.create(
                    jobId,
                    filePath,
                    fileContent,
                    G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                    generationRound
            );

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
    private List<G3ArtifactEntity> parseByPackageStatement(String content, java.util.UUID jobId, int generationRound, String basePath) {
        List<G3ArtifactEntity> artifacts = new ArrayList<>();

        // 匹配 public class/interface ClassName
        Pattern classPattern = Pattern.compile(
                "package\\s+[\\w.]+;[\\s\\S]*?(?=package\\s+|$)"
        );

        Matcher matcher = classPattern.matcher(content);

        while (matcher.find()) {
            String classContent = matcher.group().trim();

            // 提取类名
            Pattern namePattern = Pattern.compile("(?:public\\s+)?(?:class|interface|enum)\\s+(\\w+)");
            Matcher nameMatcher = namePattern.matcher(classContent);

            if (nameMatcher.find()) {
                String className = nameMatcher.group(1);
                String filePath = basePath + className + ".java";

                G3ArtifactEntity artifact = G3ArtifactEntity.create(
                        jobId,
                        filePath,
                        classContent,
                        G3ArtifactEntity.GeneratedBy.BACKEND_CODER,
                        generationRound
                );

                artifacts.add(artifact);
            }
        }

        return artifacts;
    }

    /**
     * 清理Markdown代码块标记
     */
    private String cleanMarkdownBlocks(String content) {
        if (content == null) return "";

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
