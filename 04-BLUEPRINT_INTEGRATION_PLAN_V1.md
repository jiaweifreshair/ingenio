# Blueprint 系统集成方案 V1（修订版）

> **版本**: V1.0
> **创建日期**: 2025-12-31
> **状态**: 已被 V2 取代
> **说明**: 此方案仅关注 Blueprint 与核心系统的集成，后被 V2 完整方案取代

---

## 1. 方案概述

### 1.1 目标
将 Blueprint（蓝图规范）注入到 Ingenio 的 AI 代码生成流程中，约束生成的代码符合预定义的表结构、API 规范和技术栈要求。

### 1.2 已完成部分
- ✅ `blueprintSpec` 字段已添加到 `IndustryTemplateEntity`
- ✅ `BlueprintSpec` DTO 已定义
- ✅ 蓝图文件已创建（`marketplace.json`, `blog.json`）
- ✅ 数据库迁移脚本已完成（V023）

### 1.3 待完成部分
- ❌ `BlueprintInjectionService` - 需要创建
- ❌ `BackendCoderAgentImpl` Prompt 修改 - 注入 blueprint 约束
- ❌ `ArchitectAgentImpl` Prompt 修改 - 注入 blueprint 约束
- ❌ `G3JobEntity` 扩展 - 添加 blueprintSpec 字段
- ❌ Schema 验证逻辑
- ❌ 编译验证

---

## 2. 系统架构

### 2.1 数据流架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Blueprint-Driven Code Generation                     │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────┐    ┌───────────────────┐    ┌──────────────────────────────┐
│   用户需求    │───▶│  PlanRoutingService │───▶│   IndustryTemplateMapper     │
│ "做个电商平台" │    │   (意图识别+匹配)    │    │   查询匹配的模板+蓝图         │
└──────────────┘    └───────────────────┘    └──────────────────────────────┘
                              │                            │
                              │                            ▼
                              │               ┌──────────────────────────┐
                              │               │  IndustryTemplateEntity   │
                              │               │  ├─ templateName          │
                              │               │  ├─ templatePrompt        │
                              │               │  └─ blueprintSpec ◀─────── 核心字段
                              │               │      ├─ constraints       │
                              │               │      ├─ schema[]          │
                              │               │      └─ features[]        │
                              │               └──────────────────────────┘
                              │                            │
                              ▼                            ▼
               ┌─────────────────────────────────────────────────────────┐
               │                     G3JobEntity                          │
               │  ┌─────────────────────────────────────────────────┐    │
               │  │ + requirement: String                            │    │
               │  │ + blueprintSpec: Map<String,Object>  ◀─── 新增    │    │
               │  │ + matchedTemplateId: UUID            ◀─── 新增    │    │
               │  │ + blueprintModeEnabled: Boolean      ◀─── 新增    │    │
               │  │ + contractYaml: String                           │    │
               │  │ + dbSchemaSql: String                            │    │
               │  └─────────────────────────────────────────────────┘    │
               └─────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      BlueprintInjectionService (新建)                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ + buildArchitectConstraint(blueprintSpec) → String                   │   │
│  │   └─ 生成: 技术栈约束 + 强制表结构 + 生成指导                           │   │
│  │                                                                      │   │
│  │ + buildEntityConstraint(blueprintSpec) → String                      │   │
│  │   └─ 生成: 表名→Entity映射 + 字段约束                                  │   │
│  │                                                                      │   │
│  │ + buildServiceConstraint(blueprintSpec) → String                     │   │
│  │   └─ 生成: 业务功能约束 + API规范                                      │   │
│  │                                                                      │   │
│  │ + validateSchemaCompliance(dbSchemaSql, blueprintSpec) → Result      │   │
│  │   └─ 验证: 生成的Schema是否符合蓝图定义                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                              │
            ┌─────────────────┼─────────────────┐
            ▼                 ▼                 ▼
┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐
│  ArchitectAgent   │ │ BackendCoderAgent │ │    CoachAgent     │
│  (契约生成)        │ │   (代码生成)       │ │   (验证修复)       │
└───────────────────┘ └───────────────────┘ └───────────────────┘
```

### 2.2 G3 Engine 执行流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          G3 Engine Pipeline                                  │
└─────────────────────────────────────────────────────────────────────────────┘

  [QUEUED]                                                               [COMPLETED]
     │                                                                        ▲
     ▼                                                                        │
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐     │
│ Step 1  │───▶│ Step 2  │───▶│ Step 3  │───▶│ Step 4  │───▶│ Step 5  │─────┘
│ 初始化   │    │ 规划     │    │ 编码     │    │ 验证     │    │ 完成/修复│
└─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘
     │              │              │              │              │
     ▼              ▼              ▼              ▼              ▼
┌─────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│G3Job    │   │Architect │   │Backend   │   │Coach     │   │结果汇总   │
│创建     │   │Agent     │   │CoderAgent│   │Agent     │   │          │
│         │   │          │   │          │   │          │   │          │
│注入:    │   │注入:     │   │注入:     │   │验证:     │   │输出:     │
│blueprint│   │约束提示词 │   │Entity约束│   │Schema    │   │代码产物   │
│Spec     │   │表结构定义 │   │Service约束│  │一致性    │   │验证报告   │
└─────────┘   └──────────┘   └──────────┘   └──────────┘   └──────────┘

                    ▲                ▲               ▲
                    │                │               │
                    └────────────────┴───────────────┘
                              │
                    ┌─────────────────────┐
                    │BlueprintInjection   │
                    │Service              │
                    │  统一构建约束提示词   │
                    └─────────────────────┘
```

---

## 3. 实施阶段（7 Phases）

### Phase 1: 数据结构扩展 (~2h)

**修改文件**: `G3JobEntity.java`

```java
// 新增字段
@TableField(value = "blueprint_spec", typeHandler = JacksonTypeHandler.class)
private Map<String, Object> blueprintSpec;

@TableField("matched_template_id")
private UUID matchedTemplateId;

@TableField("blueprint_mode_enabled")
private Boolean blueprintModeEnabled;
```

**数据库迁移**: `V024__add_blueprint_to_g3job.sql`

---

### Phase 2: BlueprintInjectionService 创建 (~2h)

**新建文件**: `BlueprintInjectionService.java`

```java
@Service
public class BlueprintInjectionService {

    /**
     * 为 ArchitectAgent 构建约束 Prompt
     */
    public String buildArchitectConstraint(Map<String, Object> blueprintSpec) {
        // 生成: 技术栈约束 + 强制表结构 + 生成指导
    }

    /**
     * 为 BackendCoderAgent Entity 生成构建约束
     */
    public String buildEntityConstraint(Map<String, Object> blueprintSpec) {
        // 生成: 表名→Entity映射 + 字段约束
    }

    /**
     * 为 BackendCoderAgent Service 生成构建约束
     */
    public String buildServiceConstraint(Map<String, Object> blueprintSpec) {
        // 生成: 业务功能约束 + API规范
    }

    /**
     * 验证 Schema 是否符合 Blueprint
     */
    public ComplianceResult validateSchemaCompliance(
        String dbSchemaSql,
        Map<String, Object> blueprintSpec
    ) {
        // 验证: 表结构、字段、约束是否匹配
    }
}
```

---

### Phase 3: ArchitectAgent Prompt 修改 (~1.5h)

**修改文件**: `ArchitectAgentImpl.java`

```java
@Autowired
private BlueprintInjectionService blueprintInjectionService;

@Override
public ArchitectResult generate(G3JobEntity job, int generationRound, Consumer<G3LogEntry> logConsumer) {
    Map<String, Object> blueprintSpec = job.getBlueprintSpec();

    // 新增: 构建 Blueprint 约束
    String blueprintConstraint = "";
    if (blueprintSpec != null) {
        blueprintConstraint = blueprintInjectionService.buildArchitectConstraint(blueprintSpec);
        logConsumer.accept(G3LogEntry.info(getRole(), "Blueprint Mode 激活 - 注入架构约束"));
    }

    String prompt = String.format(CONTRACT_PROMPT_TEMPLATE,
        job.getRequirement(),
        blueprintConstraint);  // 注入约束

    // AI 生成 contractYaml + dbSchemaSql
}
```

---

### Phase 4: BackendCoderAgent Prompt 修改 (~2h)

**修改文件**: `BackendCoderAgentImpl.java`

```java
private List<G3ArtifactEntity> generateEntities(G3JobEntity job, ...) {
    Map<String, Object> blueprintSpec = job.getBlueprintSpec();

    // 新增: 构建 Entity 约束
    String entityConstraint = "";
    if (blueprintSpec != null) {
        entityConstraint = blueprintInjectionService.buildEntityConstraint(blueprintSpec);
    }

    String prompt = String.format(ENTITY_PROMPT_TEMPLATE,
        CODE_STANDARDS_PROMPT + entityConstraint,  // 合并约束
        dbSchemaSql);
}

// 类似修改: generateMappers, generateServices, generateControllers
```

---

### Phase 5: PlanRoutingService 集成 (~1.5h)

**修改文件**: `PlanRoutingService.java`

```java
@Transactional
public Map<String, Object> executeCodeGeneration(UUID appSpecId, Map<String, Object> analysisContext) {
    // 从匹配的模板中提取 blueprintSpec
    List<String> matchedTemplateIds = appSpec.getMatchedTemplates();
    Map<String, Object> blueprintSpec = null;

    if (matchedTemplateIds != null && !matchedTemplateIds.isEmpty()) {
        UUID templateId = UUID.fromString(matchedTemplateIds.get(0));
        IndustryTemplateEntity template = templateMapper.selectById(templateId);
        if (template != null && template.getBlueprintSpec() != null) {
            blueprintSpec = template.getBlueprintSpec();
            log.info("已从模板提取 blueprintSpec - templateId: {}", templateId);
        }
    }

    // 创建 G3Job，注入 blueprintSpec
    G3JobEntity job = G3JobEntity.create(
        requirement,
        userId,
        tenantId,
        templateContext,
        matchedTemplateIds != null ? UUID.fromString(matchedTemplateIds.get(0)) : null,
        blueprintSpec
    );
}
```

---

### Phase 6: CoachAgent 验证逻辑 (~1h)

**修改文件**: `CoachAgentImpl.java`

```java
@Override
public ValidationResult validate(G3JobEntity job, ...) {
    // 现有验证逻辑...

    // 新增: Blueprint 合规性验证
    if (job.getBlueprintModeEnabled()) {
        ComplianceResult compliance = blueprintInjectionService
            .validateSchemaCompliance(job.getDbSchemaSql(), job.getBlueprintSpec());

        if (!compliance.isPassed()) {
            logConsumer.accept(G3LogEntry.error(getRole(),
                "Blueprint 合规性验证失败: " + compliance.getViolations()));
            return ValidationResult.failed(compliance.getViolations());
        }
    }
}
```

---

### Phase 7: 编译验证 (~1h)

```bash
# 后端编译
cd backend
mvn clean compile -DskipTests  # 0 errors
mvn test                       # 所有测试通过

# 前端编译（如有修改）
cd frontend
pnpm tsc --noEmit              # 0 errors
```

---

## 4. 文件修改清单

```
backend/
├── src/main/java/com/ingenio/backend/
│   ├── entity/g3/
│   │   └── G3JobEntity.java                    [修改] +3字段 +create方法
│   │
│   ├── service/
│   │   ├── BlueprintInjectionService.java      [新建] 核心服务
│   │   └── PlanRoutingService.java             [修改] 集成点
│   │
│   └── agent/g3/impl/
│       ├── ArchitectAgentImpl.java             [修改] Prompt注入
│       ├── BackendCoderAgentImpl.java          [修改] Prompt注入
│       └── CoachAgentImpl.java                 [修改] Schema验证
│
└── src/main/resources/db/migration/
    └── V024__add_blueprint_to_g3job.sql        [新建] 数据库迁移
```

---

## 5. 方案局限性

> ⚠️ **此方案的遗漏点**（已在 V2 方案中补充）

| 遗漏点 | 影响 | V2 状态 |
|-------|------|---------|
| OpenLovable 集成 | 原型生成无 Blueprint 约束 | ✅ 已补充 |
| Supabase Schema 生成 | Schema 使用通用模板 | ✅ 已补充 |
| AppSpec 字段扩展 | 无法持久化 Blueprint | ✅ 已补充 |
| 前端集成 | 无法传递 Blueprint 到前端 | ✅ 已补充 |
| API 端点补全 | 缺失关键 API | ✅ 已补充 |
| 验证逻辑增强 | 缺少状态验证 | ✅ 已补充 |

---

## 6. 预计工时

| Phase | 内容 | 工时 |
|-------|------|------|
| Phase 1 | 数据结构扩展 | 2h |
| Phase 2 | BlueprintInjectionService | 2h |
| Phase 3 | ArchitectAgent 修改 | 1.5h |
| Phase 4 | BackendCoderAgent 修改 | 2h |
| Phase 5 | PlanRoutingService 集成 | 1.5h |
| Phase 6 | CoachAgent 验证 | 1h |
| Phase 7 | 编译验证 | 1h |
| **总计** | | **~11h** |

---

**此方案已被 V2 完整方案取代，请参考 `BLUEPRINT_INTEGRATION_PLAN_V2.md`**
