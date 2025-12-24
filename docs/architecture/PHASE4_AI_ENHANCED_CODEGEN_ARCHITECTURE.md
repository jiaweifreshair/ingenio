# Phase 4 - AI增强代码生成架构设计文档

**版本**: V2.0
**日期**: 2025-11-17
**作者**: Ingenio Team
**状态**: ✅ 架构设计完成

---

## 📋 目录

1. [核心目标](#核心目标)
2. [整体架构](#整体架构)
3. [Phase 4.1: RequirementAnalyzer](#phase-41-requirementanalyzer)
4. [Phase 4.2: BusinessLogicGenerator](#phase-42-businesslogicgenerator)
5. [Phase 4.3: BestPracticeApplier](#phase-43-bestpracticeapplier)
6. [Phase 4.4: CodeOptimizer](#phase-44-codeoptimizer)
7. [Phase 4.5: 整合测试](#phase-45-整合测试)
8. [技术选型](#技术选型)
9. [质量保证](#质量保证)
10. [实施计划](#实施计划)

---

## 核心目标

将代码生成从"模板化"升级到"智能化"：

| 维度 | Phase 3（模板化） | Phase 4（智能化） |
|-----|-----------------|-----------------|
| **需求理解** | 手动定义Entity | AI自动理解自然语言 |
| **业务逻辑** | 仅基础CRUD | AI生成复杂业务逻辑 |
| **代码质量** | 模板固定模式 | AI应用最佳实践 |
| **优化程度** | 无优化 | AI智能优化 |

---

## 整体架构

### 工作流程

```
用户需求（自然语言）
"我要做一个用户管理系统，包括注册、登录、密码重置功能。
用户注册时需要验证邮箱格式、密码强度，年龄必须大于18岁。"
    ↓
【Phase 4.1】RequirementAnalyzer
    ├─ 调用Qwen-Max理解需求
    ├─ 提取实体：User (username, email, password, age)
    ├─ 提取业务规则：
    │   ├─ 注册：邮箱格式验证、密码强度验证、年龄≥18
    │   ├─ 登录：用户名密码校验
    │   └─ 密码重置：验证旧密码、密码强度校验
    └─ 输出：AnalyzedRequirement (JSON)
    ↓
【Phase 2】DatabaseSchemaGenerator
    └─ 生成PostgreSQL Schema
    ↓
【Phase 3】CodeGenerator
    ├─ EntityGenerator → User.java
    ├─ DTOGenerator → UserCreateDTO/UserUpdateDTO/UserResponseDTO
    ├─ ServiceGenerator → IUserService/UserServiceImpl（基础CRUD）
    └─ ControllerGenerator → UserController
    ↓
【Phase 4.2】BusinessLogicGenerator
    ├─ 分析BusinessRule: 注册业务规则
    ├─ 调用Qwen-Plus生成业务逻辑代码：
    │   ├─ 邮箱格式验证 if (!email.matches(regex)) throw...
    │   ├─ 密码强度校验 if (password.length() < 8) throw...
    │   ├─ 年龄校验 if (age < 18) throw...
    │   └─ 发送欢迎邮件 emailService.sendWelcomeEmail(...)
    └─ 插入方法到UserServiceImpl.register()
    ↓
【Phase 4.3】BestPracticeApplier
    ├─ TransactionRule → 添加@Transactional
    ├─ ExceptionHandlingRule → 统一异常处理
    ├─ LoggingRule → 添加日志记录
    ├─ CachingRule → 添加缓存注解
    └─ SecurityRule → SQL注入防护
    ↓
【Phase 4.4】CodeOptimizer
    ├─ 性能优化：批量查询、懒加载
    ├─ 代码质量优化：去重、简化
    └─ 安全优化：参数化查询、XSS防护
    ↓
✅ 完整的、生产级别的代码
```

---

## Phase 4.1: RequirementAnalyzer

### 核心职责

将用户的自然语言需求转换为结构化的数据模型。

### 数据模型设计

#### 1. AnalyzedRequirement（主模型）

```java
@Data
@Builder
public class AnalyzedRequirement {
    private String domain;                     // 业务领域
    private String description;                // 领域描述
    private List<EntityRequirement> entities;  // 实体列表
    private List<BusinessRule> businessRules;  // 业务规则
    private List<Relationship> relationships;  // 实体关系
    private List<Constraint> constraints;      // 约束条件
    private String source;                     // 需求来源
    private Double confidence;                 // AI置信度（0-1）
    private String reasoning;                  // AI推理过程
}
```

#### 2. EntityRequirement（实体需求）

```java
@Data
@Builder
public class EntityRequirement {
    private String name;                       // 实体名：User
    private String description;                // 描述
    private String tableName;                  // 表名：users
    private List<FieldRequirement> fields;     // 字段列表
    private List<String> businessMethods;      // 业务方法：["register", "login"]
    private Boolean softDelete;                // 是否软删除
    private Boolean auditFields;               // 是否审计字段
    private Boolean pagination;                // 是否分页
    private EntityPriority priority;           // 优先级：HIGH/MEDIUM/LOW
}
```

#### 3. FieldRequirement（字段需求）

```java
@Data
public class FieldRequirement {
    private String name;           // 字段名：username
    private String type;           // 类型：String、Integer、UUID
    private String description;    // 描述：用户名
    private Boolean required;      // 是否必填
    private Boolean unique;        // 是否唯一
    private String defaultValue;   // 默认值
    private Integer minLength;     // 最小长度
    private Integer maxLength;     // 最大长度
    private String pattern;        // 正则表达式
}
```

#### 4. BusinessRule（业务规则）

```java
@Data
@Builder
public class BusinessRule {
    private String name;           // 规则名：validateAge
    private String description;    // 描述：验证用户年龄必须≥18岁
    private BusinessRuleType type; // 类型：VALIDATION/CALCULATION/WORKFLOW
    private String entity;         // 关联实体：User
    private String method;         // 关联方法：register
    private String logic;          // 业务逻辑描述
    private Integer priority;      // 优先级：1-10
}

public enum BusinessRuleType {
    VALIDATION,    // 验证规则：邮箱格式、密码强度
    CALCULATION,   // 计算规则：订单总价、会员等级
    WORKFLOW,      // 工作流规则：订单状态流转
    NOTIFICATION   // 通知规则：发送邮件、短信
}
```

#### 5. Relationship（实体关系）

```java
@Data
@Builder
public class Relationship {
    private String sourceEntity;      // 源实体：User
    private String targetEntity;      // 目标实体：Order
    private RelationshipType type;    // 关系类型：ONE_TO_MANY
    private String sourcefield;       // 源字段：userId
    private String targetField;       // 目标字段：userId
    private Boolean cascadeDelete;    // 级联删除
}

public enum RelationshipType {
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE,
    MANY_TO_MANY
}
```

### AI Prompt设计

#### Prompt模板（Few-shot Learning）

```java
private String buildAnalysisPrompt(String userRequirement) {
    return """
你是一个专业的软件需求分析师。请分析以下用户需求，提取结构化信息。

用户需求：
%s

请按照以下JSON格式返回分析结果：

{
  "domain": "业务领域名称",
  "description": "领域描述",
  "confidence": 0.95,
  "reasoning": "分析推理过程",
  "entities": [
    {
      "name": "User",
      "description": "系统用户实体",
      "tableName": "users",
      "fields": [
        {
          "name": "username",
          "type": "String",
          "description": "用户名",
          "required": true,
          "unique": true,
          "minLength": 3,
          "maxLength": 20
        }
      ],
      "businessMethods": ["register", "login", "resetPassword"],
      "softDelete": true,
      "auditFields": true,
      "pagination": true,
      "priority": "HIGH"
    }
  ],
  "businessRules": [
    {
      "name": "validateEmailFormat",
      "description": "验证邮箱格式",
      "type": "VALIDATION",
      "entity": "User",
      "method": "register",
      "logic": "使用正则表达式验证邮箱格式是否合法",
      "priority": 10
    }
  ],
  "relationships": [
    {
      "sourceEntity": "User",
      "targetEntity": "Order",
      "type": "ONE_TO_MANY",
      "sourceField": "userId",
      "targetField": "userId",
      "cascadeDelete": false
    }
  ]
}

示例1：
输入："我要做一个博客系统，包括文章发布、评论、点赞功能"
输出：
{
  "domain": "博客管理",
  "entities": [
    {"name": "Post", "businessMethods": ["publish", "edit", "delete"]},
    {"name": "Comment", "businessMethods": ["create", "reply"]},
    {"name": "Like", "businessMethods": ["like", "unlike"]}
  ],
  "businessRules": [
    {"name": "validatePostContent", "logic": "文章内容不能为空且长度>10"}
  ]
}

要求：
1. 必须返回valid JSON
2. confidence范围0-1，反映分析的准确度
3. businessMethods要具体，不要只写CRUD
4. businessRules要详细，包含具体的验证逻辑
""".formatted(userRequirement);
}
```

### RequirementAnalyzer实现

```java
@Service
@Slf4j
public class RequirementAnalyzer {

    @Autowired
    private ChatClient chatClient; // Spring AI Alibaba

    /**
     * 分析用户需求，返回结构化的需求文档
     *
     * @param userRequirement 用户的自然语言需求
     * @return 结构化的需求文档
     */
    public AnalyzedRequirement analyze(String userRequirement) {
        log.info("[RequirementAnalyzer] 开始分析需求: userRequirement.length={}",
                userRequirement.length());

        // Step 1: 构建AI提示词
        String prompt = buildAnalysisPrompt(userRequirement);

        // Step 2: 调用Qwen-Max分析
        ChatResponse response = chatClient.call(new Prompt(prompt));
        String content = response.getResult().getOutput().getContent();

        // Step 3: 解析AI返回的JSON
        AnalyzedRequirement requirement = parseResponse(content);

        // Step 4: 验证和补充
        validateAndEnrich(requirement);

        log.info("[RequirementAnalyzer] ✅ 需求分析完成: domain={}, entities={}, rules={}",
                requirement.getDomain(),
                requirement.getEntities().size(),
                requirement.getBusinessRules().size());

        return requirement;
    }

    private AnalyzedRequirement parseResponse(String jsonContent) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(jsonContent, AnalyzedRequirement.class);
        } catch (JsonProcessingException e) {
            log.error("[RequirementAnalyzer] JSON解析失败", e);
            throw new RuntimeException("AI返回的JSON格式不正确", e);
        }
    }

    private void validateAndEnrich(AnalyzedRequirement requirement) {
        // 验证实体名称不为空
        requirement.getEntities().forEach(entity -> {
            if (entity.getName() == null || entity.getName().isEmpty()) {
                throw new IllegalArgumentException("实体名称不能为空");
            }
        });

        // 补充默认值
        requirement.getEntities().forEach(entity -> {
            if (entity.getSoftDelete() == null) {
                entity.setSoftDelete(true); // 默认启用软删除
            }
            if (entity.getAuditFields() == null) {
                entity.setAuditFields(true); // 默认启用审计字段
            }
            if (entity.getPriority() == null) {
                entity.setPriority(EntityPriority.MEDIUM); // 默认中等优先级
            }
        });
    }
}
```

---

## Phase 4.2: BusinessLogicGenerator

### 核心职责

根据BusinessRule生成具体的业务逻辑代码，并插入到Service方法中。

### 生成流程

```
输入: BusinessRule + 基础Service代码
    ↓
Step 1: 分析BusinessRule类型
    ├─ VALIDATION → 生成验证逻辑
    ├─ CALCULATION → 生成计算逻辑
    ├─ WORKFLOW → 生成工作流逻辑
    └─ NOTIFICATION → 生成通知逻辑
    ↓
Step 2: 构建上下文（Entity、DTO、现有代码）
    ↓
Step 3: 调用AI生成方法代码
    ↓
Step 4: 使用AST插入代码到Service
    ↓
输出: 增强后的Service代码
```

### AI Prompt设计

```java
private String buildBusinessLogicPrompt(BusinessRule rule, String context) {
    return """
你是一个专业的Java开发工程师。请根据以下业务规则生成Java方法代码。

业务规则：
- 规则名称：%s
- 规则描述：%s
- 规则类型：%s
- 关联实体：%s
- 关联方法：%s
- 业务逻辑：%s

上下文信息：
%s

请生成完整的Java方法代码，要求：
1. 方法签名符合业务场景
2. 包含详细的参数校验
3. 使用Spring框架标准（@Transactional、@Valid等）
4. 包含完整的异常处理
5. 添加详细的中文注释
6. 返回格式：```java\n方法代码\n```

示例：
业务规则："验证用户年龄必须≥18岁"
生成代码：
```java
/**
 * 验证用户年龄
 * @param age 用户年龄
 * @throws BusinessException 如果年龄<18
 */
private void validateAge(Integer age) {
    if (age == null) {
        throw new BusinessException("年龄不能为空");
    }
    if (age < 18) {
        throw new BusinessException("用户年龄必须大于等于18岁");
    }
    log.debug("年龄验证通过: age={}", age);
}
```
""".formatted(
        rule.getName(),
        rule.getDescription(),
        rule.getType(),
        rule.getEntity(),
        rule.getMethod(),
        rule.getLogic(),
        context
    );
}
```

### BusinessLogicGenerator实现

```java
@Service
@Slf4j
public class BusinessLogicGenerator {

    @Autowired
    private ChatClient chatClient;

    /**
     * 为Service生成业务逻辑方法
     *
     * @param entity 实体
     * @param rule 业务规则
     * @param baseServiceCode Phase 3生成的基础Service代码
     * @return 增强后的Service代码
     */
    public String generateBusinessLogic(
            Entity entity,
            BusinessRule rule,
            String baseServiceCode
    ) {
        log.info("[BusinessLogicGenerator] 开始生成业务逻辑: rule={}, entity={}",
                rule.getName(), entity.getName());

        // Step 1: 构建上下文
        String context = buildContext(entity, baseServiceCode);

        // Step 2: 构建AI提示词
        String prompt = buildBusinessLogicPrompt(rule, context);

        // Step 3: 调用AI生成代码
        ChatResponse response = chatClient.call(new Prompt(prompt));
        String methodCode = extractMethodCode(response.getResult().getOutput().getContent());

        // Step 4: 插入方法到Service代码
        String enhancedCode = insertMethodIntoService(baseServiceCode, methodCode, rule.getMethod());

        log.info("[BusinessLogicGenerator] ✅ 业务逻辑生成完成: methodName={}, codeLength={}",
                rule.getMethod(), methodCode.length());

        return enhancedCode;
    }

    private String buildContext(Entity entity, String baseServiceCode) {
        return String.format("""
实体信息：
- 实体名：%s
- 字段：%s

现有Service代码：
%s
        """,
                entity.getName(),
                entity.getFields().stream()
                        .map(f -> f.getName() + ":" + f.getType())
                        .collect(Collectors.joining(", ")),
                baseServiceCode.substring(0, Math.min(500, baseServiceCode.length())) // 仅取前500字符
        );
    }

    private String extractMethodCode(String aiResponse) {
        // 从AI响应中提取```java ... ```包裹的代码
        Pattern pattern = Pattern.compile("```java\\s*(.+?)```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(aiResponse);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        throw new RuntimeException("AI返回的代码格式不正确");
    }

    private String insertMethodIntoService(String baseCode, String methodCode, String targetMethod) {
        // 使用JavaParser解析代码并插入方法
        CompilationUnit cu = StaticJavaParser.parse(baseCode);
        ClassOrInterfaceDeclaration serviceClass = cu.getClassByName("UserServiceImpl")
                .orElseThrow(() -> new RuntimeException("Service类不存在"));

        // 找到目标方法（如register）并在其中插入业务逻辑
        MethodDeclaration targetMethodDecl = serviceClass.getMethodsByName(targetMethod).get(0);

        // 在方法开始处插入验证逻辑
        BlockStmt methodBody = targetMethodDecl.getBody().orElseThrow();
        Statement validationStmt = StaticJavaParser.parseStatement(methodCode);
        methodBody.addStatement(0, validationStmt);

        return cu.toString();
    }
}
```

---

## Phase 4.3: BestPracticeApplier

### 核心职责

应用行业最佳实践到生成的代码。

### 最佳实践清单

| 最佳实践 | 应用方式 | 示例 |
|---------|---------|------|
| **事务管理** | 为写操作添加@Transactional | `@Transactional(rollbackFor = Exception.class)` |
| **异常处理** | 统一异常处理 | `try-catch + 转换为BusinessException` |
| **日志记录** | 添加日志 | `log.info("操作开始")` `log.error("操作失败", e)` |
| **缓存策略** | 添加缓存注解 | `@Cacheable("users")` |
| **参数校验** | Bean Validation | `@Valid` `@NotNull` |
| **安全防护** | SQL注入、XSS防护 | 参数化查询、输入校验 |
| **性能优化** | 批量操作、分页 | `batchInsert()` `Page<T>` |
| **幂等性** | 防止重复提交 | 分布式锁、状态机 |

### BestPracticeApplier实现

```java
@Service
@Slf4j
public class BestPracticeApplier {

    private final List<BestPracticeRule> rules = Arrays.asList(
            new TransactionRule(),
            new ExceptionHandlingRule(),
            new LoggingRule(),
            new CachingRule(),
            new ValidationRule(),
            new SecurityRule()
    );

    /**
     * 应用所有最佳实践到代码
     *
     * @param sourceCode 源代码
     * @param entity 实体
     * @return 应用最佳实践后的代码
     */
    public String applyBestPractices(String sourceCode, Entity entity) {
        log.info("[BestPracticeApplier] 开始应用最佳实践: entity={}", entity.getName());

        String enhancedCode = sourceCode;
        int appliedCount = 0;

        for (BestPracticeRule rule : rules) {
            if (rule.isApplicable(enhancedCode, entity)) {
                enhancedCode = rule.apply(enhancedCode);
                appliedCount++;
                log.debug("[BestPracticeApplier] 应用规则: ruleName={}", rule.getName());
            }
        }

        log.info("[BestPracticeApplier] ✅ 最佳实践应用完成: appliedRules={}", appliedCount);
        return enhancedCode;
    }
}

// 最佳实践规则接口
public interface BestPracticeRule {
    String getName();
    boolean isApplicable(String sourceCode, Entity entity);
    String apply(String sourceCode);
}

// 事务管理规则
public class TransactionRule implements BestPracticeRule {
    @Override
    public String apply(String sourceCode) {
        // 为所有写操作方法（create/update/delete）添加@Transactional
        CompilationUnit cu = StaticJavaParser.parse(sourceCode);
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            String methodName = method.getNameAsString();
            if (methodName.matches("create|update|delete|save")) {
                method.addAnnotation("Transactional(rollbackFor = Exception.class)");
            }
        });
        return cu.toString();
    }
}

// 异常处理规则
public class ExceptionHandlingRule implements BestPracticeRule {
    @Override
    public String apply(String sourceCode) {
        // 为所有方法添加try-catch
        CompilationUnit cu = StaticJavaParser.parse(sourceCode);
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            BlockStmt body = method.getBody().orElse(new BlockStmt());
            TryStmt tryStmt = new TryStmt();
            tryStmt.setTryBlock(body);

            CatchClause catchClause = new CatchClause();
            catchClause.setParameter(new Parameter(
                    StaticJavaParser.parseType("Exception"),
                    "e"
            ));
            catchClause.setBody(StaticJavaParser.parseBlock(
                    "{ log.error(\"操作失败\", e); throw new BusinessException(\"操作失败\", e); }"
            ));
            tryStmt.setCatchClauses(NodeList.nodeList(catchClause));

            method.setBody(new BlockStmt(NodeList.nodeList(tryStmt)));
        });
        return cu.toString();
    }
}
```

---

## Phase 4.4: CodeOptimizer

### 核心职责

优化代码性能、质量和安全性。

### 优化策略

#### 1. 性能优化

```java
public class PerformanceOptimizer {
    /**
     * 识别N+1查询并优化为批量查询
     */
    public String optimizeNPlusOne(String code) {
        // 识别循环中的单个查询
        // 转换为批量查询
        return code;
    }

    /**
     * 添加分页支持
     */
    public String addPagination(String code) {
        // 为list方法添加分页参数
        return code;
    }

    /**
     * 添加缓存
     */
    public String addCaching(String code) {
        // 为查询方法添加@Cacheable
        return code;
    }
}
```

#### 2. 代码质量优化

```java
public class QualityOptimizer {
    /**
     * 去除重复代码
     */
    public String removeDuplicates(String code) {
        // 识别重复代码块
        // 提取为私有方法
        return code;
    }

    /**
     * 提取常量
     */
    public String extractConstants(String code) {
        // 将魔法数字和字符串提取为常量
        return code;
    }

    /**
     * 简化复杂逻辑
     */
    public String simplifyLogic(String code) {
        // 简化嵌套if、提取方法
        return code;
    }
}
```

#### 3. 安全优化

```java
public class SecurityOptimizer {
    /**
     * SQL注入防护
     */
    public String preventSQLInjection(String code) {
        // 确保使用参数化查询
        return code;
    }

    /**
     * XSS防护
     */
    public String preventXSS(String code) {
        // 添加输入校验和转义
        return code;
    }
}
```

---

## Phase 4.5: 整合测试

### 端到端测试

```java
@SpringBootTest
public class Phase4IntegrationTest {

    @Autowired
    private RequirementAnalyzer requirementAnalyzer;

    @Autowired
    private DatabaseSchemaGenerator schemaGenerator;

    @Autowired
    private EntityGenerator entityGenerator;

    @Autowired
    private ServiceGenerator serviceGenerator;

    @Autowired
    private BusinessLogicGenerator businessLogicGenerator;

    @Autowired
    private BestPracticeApplier bestPracticeApplier;

    @Autowired
    private CodeOptimizer codeOptimizer;

    @Test
    public void testFullPipeline() {
        // 用户需求
        String userRequirement = """
            我要做一个用户管理系统，包括注册、登录、密码重置功能。
            用户注册时需要验证邮箱格式、密码强度，年龄必须大于18岁。
            用户登录时验证用户名密码，登录成功后生成JWT Token。
            密码重置时需要验证旧密码，新密码必须符合强度要求。
        """;

        // Phase 4.1: AI需求理解
        AnalyzedRequirement requirement = requirementAnalyzer.analyze(userRequirement);
        assertThat(requirement.getEntities()).hasSize(1);
        assertThat(requirement.getBusinessRules()).hasSize(3);

        // Phase 2: 生成数据库Schema
        String schema = schemaGenerator.generate(requirement);
        assertThat(schema).contains("CREATE TABLE users");

        // Phase 3: 生成基础代码
        String entityCode = entityGenerator.generate(requirement.getEntities().get(0));
        String serviceCode = serviceGenerator.generate(requirement.getEntities().get(0));

        // Phase 4.2: 生成业务逻辑
        String enhancedServiceCode = serviceCode;
        for (BusinessRule rule : requirement.getBusinessRules()) {
            enhancedServiceCode = businessLogicGenerator.generateBusinessLogic(
                    requirement.getEntities().get(0),
                    rule,
                    enhancedServiceCode
            );
        }
        assertThat(enhancedServiceCode).contains("validateEmailFormat");
        assertThat(enhancedServiceCode).contains("validatePasswordStrength");

        // Phase 4.3: 应用最佳实践
        String bestPracticeCode = bestPracticeApplier.applyBestPractices(
                enhancedServiceCode,
                requirement.getEntities().get(0)
        );
        assertThat(bestPracticeCode).contains("@Transactional");
        assertThat(bestPracticeCode).contains("log.info");

        // Phase 4.4: 代码优化
        String optimizedCode = codeOptimizer.optimize(
                bestPracticeCode,
                requirement.getEntities().get(0)
        );

        // 验证最终代码质量
        assertThat(optimizedCode).doesNotContain("TODO");
        assertThat(compilationSucceeds(optimizedCode)).isTrue();
    }
}
```

---

## 技术选型

| 组件 | 技术 | 版本 | 用途 |
|-----|------|------|------|
| **AI模型** | 通义千问Qwen-Max | - | 需求理解（高准确率） |
| **AI模型** | 通义千问Qwen-Plus | - | 代码生成（性价比高） |
| **AI集成** | Spring AI Alibaba | 1.0.0-M6 | AI能力集成 |
| **代码解析** | JavaParser | 3.25.5 | AST解析和代码插入 |
| **JSON处理** | Jackson | 2.15+ | JSON序列化 |
| **测试框架** | JUnit 5 + AssertJ | - | 单元测试 |

---

## 质量保证

### 成功标准

| 指标 | 目标值 | 阻塞标准 |
|-----|-------|---------|
| **需求理解准确率** | ≥90% | <85% |
| **生成代码编译通过率** | 100% | <100% |
| **生成代码测试通过率** | ≥95% | <90% |
| **最佳实践应用覆盖率** | ≥80% | <70% |
| **代码优化改进率** | ≥30% | <20% |

### 质量门禁

```java
@Test
public void qualityGate() {
    // 1. 编译检查
    assertThat(compileCode(generatedCode)).succeeds();

    // 2. 语法检查
    assertThat(lintCode(generatedCode)).hasNoErrors();

    // 3. 测试覆盖率
    assertThat(testCoverage(generatedCode)).isGreaterThanOrEqualTo(0.85);

    // 4. 最佳实践检查
    assertThat(hasBestPractice(generatedCode, "Transaction")).isTrue();
    assertThat(hasBestPractice(generatedCode, "Logging")).isTrue();
    assertThat(hasBestPractice(generatedCode, "ExceptionHandling")).isTrue();
}
```

---

## 实施计划

### Phase 4.1: RequirementAnalyzer（3小时）

- ✅ Sub-task 1: 创建数据模型（30分钟）
  - AnalyzedRequirement.java
  - EntityRequirement.java
  - BusinessRule.java
  - Relationship.java

- [ ] Sub-task 2: 实现RequirementAnalyzer（1.5小时）
  - 实现analyze()方法
  - 实现parseResponse()方法
  - 实现validateAndEnrich()方法

- [ ] Sub-task 3: 设计AI Prompt（1小时）
  - Few-shot Learning示例
  - Chain of Thought引导
  - 结构化输出格式

- [ ] Sub-task 4: 编写测试用例（30分钟）
  - 测试基础需求理解
  - 测试复杂需求理解
  - 测试边界情况

### Phase 4.2: BusinessLogicGenerator（4小时）

- [ ] Sub-task 1: 创建BusinessRule模型（30分钟）
- [ ] Sub-task 2: 实现BusinessLogicGenerator（2小时）
- [ ] Sub-task 3: 实现代码插入逻辑（1小时）
- [ ] Sub-task 4: 编写测试用例（30分钟）

### Phase 4.3: BestPracticeApplier（3小时）

- [ ] Sub-task 1: 定义最佳实践规则（30分钟）
- [ ] Sub-task 2: 实现5个核心规则（1.5小时）
- [ ] Sub-task 3: 实现规则引擎（45分钟）
- [ ] Sub-task 4: 编写测试用例（45分钟）

### Phase 4.4: CodeOptimizer（2小时）

- [ ] Sub-task 1: 性能优化（45分钟）
- [ ] Sub-task 2: 质量优化（45分钟）
- [ ] Sub-task 3: 安全优化（30分钟）
- [ ] Sub-task 4: 编写测试用例（30分钟）

### Phase 4.5: 整合测试（2小时）

- [ ] Sub-task 1: 端到端测试（1小时）
- [ ] Sub-task 2: 性能测试（30分钟）
- [ ] Sub-task 3: 文档编写（30分钟）

---

## 风险和应对

### 1. AI输出不稳定性

**风险**: AI可能生成不一致或错误的代码

**应对策略**:
- 多次调用取最优结果
- 语法检查和编译验证
- 单元测试自动验证
- 人工审核关键逻辑

### 2. Prompt工程复杂度

**风险**: 难以设计出高质量的Prompt

**应对策略**:
- 使用Few-shot Learning提供示例
- 提供详细的上下文信息
- 逐步优化Prompt模板
- 记录Prompt版本和效果

### 3. 代码合并冲突

**风险**: AI生成的代码与基础代码合并时可能冲突

**应对策略**:
- 使用JavaParser进行AST级别的合并
- 保留代码的原始结构
- 智能检测冲突并提示
- 提供代码diff预览

### 4. 性能问题

**风险**: AI调用可能较慢

**应对策略**:
- 异步处理AI调用
- 缓存常见需求的生成结果
- 使用流式输出提升体验
- 优化Prompt减少token消耗

---

## 总结

Phase 4通过AI能力将代码生成从"模板化"升级到"智能化"，实现了：

1. ✅ **智能理解**：AI理解自然语言需求
2. ✅ **智能生成**：AI生成复杂业务逻辑
3. ✅ **智能优化**：AI应用最佳实践和优化
4. ✅ **质量保证**：完整的测试和验证机制

**预期效果**：
- 需求理解准确率 ≥ 90%
- 代码生成质量提升 50%
- 开发效率提升 3倍
- 代码可维护性提升 40%

---

**下一步**：开始实施Phase 4.1的Sub-task 2，创建RequirementAnalyzer核心逻辑。
