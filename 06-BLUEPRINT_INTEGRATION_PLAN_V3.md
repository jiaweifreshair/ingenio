# Blueprint 系统集成方案 V3（修订版）

> **版本**: V3.0
> **创建日期**: 2025-12-31
> **状态**: 当前版本
> **原则**: 功能闭环（前后端完整再开发下一功能）

---

## 1. 核心决策确认

| 决策项 | 结论 |
|-------|------|
| G3 系统 | **统一为 Java G3**，移除前端 Lab Mock |
| Frontend Lab G3 | **移除** |
| Python g3-engine | **短期保留探索用，中期移植到 Java** |
| OpenLovable | 统一称为 **OpenLovable-CN** |
| 开发原则 | **功能闭环**：每个功能完成前后端再开发另一个功能 |

---

## 2. 当前架构问题

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         当前问题：三套 G3 系统并存                            │
└─────────────────────────────────────────────────────────────────────────────┘

❌ 问题 1: Frontend Lab G3 (Mock)
   调用链: Lab Page → G3LogViewer → /api/lab/g3-poc → lib/lab/g3-engine.ts
   影响文件:
   - frontend/src/lib/lab/g3-engine.ts (Mock)
   - frontend/src/lib/lab/mock-agents.ts (Mock)
   - frontend/src/lib/lab/typescript-check.ts (Mock)
   - frontend/src/app/api/lab/g3-poc/route.ts
   - frontend/src/app/api/v1/generate/analyze-stream/route.ts (mode='g3')
   - frontend/src/components/generation/G3LogViewer.tsx
   - frontend/src/app/lab/page.tsx

❌ 问题 2: Python g3-engine (独立服务)
   路径: g3-engine/main.py
   端点: /api/v1/g3/start, /api/v1/g3/scout
   状态: 仅用于 Scout 智能发现探索

✅ 生产系统: Java G3
   路径: backend/src/main/java/com/ingenio/backend/module/g3/
   端点: /v1/g3/jobs, /v1/g3/jobs/{id}/logs, etc.
   状态: 完整实现，生产级
```

---

## 3. 目标架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           目标架构：统一 Java G3                              │
└─────────────────────────────────────────────────────────────────────────────┘

                    ┌────────────────────────────────────┐
                    │         前端 (Next.js)              │
                    │   所有 G3 调用 → /v1/g3/*           │
                    └──────────────┬─────────────────────┘
                                   │
                    ┌──────────────▼─────────────────────┐
                    │      Java G3 系统 (唯一入口)         │
                    │                                     │
                    │  ┌─────────────────────────────┐   │
                    │  │  G3OrchestratorService       │   │
                    │  │  ├─ Architect 阶段           │   │
                    │  │  │   └─ + Blueprint 约束注入  │   │
                    │  │  ├─ Coder 阶段               │   │
                    │  │  │   └─ + Blueprint 约束注入  │   │
                    │  │  └─ Coach 阶段               │   │
                    │  │      └─ + Blueprint 验证      │   │
                    │  └─────────────────────────────┘   │
                    └─────────────────────────────────────┘
                                   │
          ┌────────────────────────┼────────────────────────┐
          ▼                        ▼                        ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ PostgreSQL      │     │ E2B Sandbox     │     │ OpenLovable-CN  │
│ + Blueprint表    │     │ (编译验证)       │     │ (原型预览)       │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

---

## 4. 功能闭环实施方案（5 个功能模块）

### 功能模块总览

| 序号 | 功能模块 | 前端范围 | 后端范围 | 预计工时 |
|------|----------|---------|---------|---------|
| F1 | Lab 页面迁移 | Lab UI + API Route | G3 SSE 接口 | ~4h |
| F2 | 模板选择 + Blueprint 加载 | 模板选择组件 | selectTemplate API | ~6h |
| F3 | Blueprint 约束注入 (G3) | 无 | Agent Prompt 修改 | ~6h |
| F4 | Blueprint 验证 | 验证结果展示 | CoachAgent 验证 | ~4h |
| F5 | 数据结构扩展 | 类型定义 | Entity + 迁移 | ~4h |
| **F6** | **OpenLovable-CN Blueprint 集成** | **无** | **Prompt 增强** | **~4h** |

**总计**: ~28h（约 3.5 天）

---

## 功能 F1: Lab 页面迁移（前端 Mock → 后端 Java G3）

### F1.1 目标
将 Lab 页面从调用前端 Mock 改为调用后端 Java G3 系统

### F1.2 前端修改

**移除文件** (3个):
```
frontend/src/lib/lab/
├── g3-engine.ts       [删除]
├── mock-agents.ts     [删除]
└── typescript-check.ts [删除]
```

**修改文件**:

**1. `frontend/src/app/api/lab/g3-poc/route.ts`**
```typescript
// 修改前: 调用前端 Mock
import { runG3Loop } from '@/lib/lab/g3-engine';

// 修改后: 代理到后端 Java G3
export async function POST(req: NextRequest) {
  const { requirement } = await req.json();

  // 调用后端 Java G3 API
  const backendUrl = `${BACKEND_API_URL}/v1/g3/jobs`;
  const response = await fetch(backendUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ requirement }),
  });

  const { jobId } = await response.json();

  // 订阅后端 SSE 日志流
  const logsUrl = `${BACKEND_API_URL}/v1/g3/jobs/${jobId}/logs`;
  const logsResponse = await fetch(logsUrl, {
    headers: { 'Accept': 'text/event-stream' },
  });

  // 转发 SSE 流
  return new Response(logsResponse.body, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
    },
  });
}
```

**2. `frontend/src/app/api/v1/generate/analyze-stream/route.ts`**
```typescript
// 修改: 移除 g3 mode，只保留 legacy mode
// 删除 handleG3Mode 函数
// 删除 import { runG3Loop }

export async function POST(request: NextRequest) {
  const body = await request.json();
  // 移除 mode 判断，直接使用 legacy 模式
  return handleLegacyMode(body);
}
```

**3. `frontend/src/components/generation/G3LogViewer.tsx`**
```typescript
// 修改: 更新事件类型以匹配后端格式
// 后端返回格式: { timestamp, role, message, level }
// 保持 UI 逻辑不变，仅调整数据解析
```

### F1.3 后端验证
```bash
# 确认后端 G3 API 正常工作
curl -X POST http://localhost:8080/api/v1/g3/jobs \
  -H "Content-Type: application/json" \
  -d '{"requirement": "测试需求"}'
```

### F1.4 验收标准
- [ ] Lab 页面可正常访问
- [ ] 点击 "START FULL BUILD" 触发后端 G3 任务
- [ ] 日志实时显示
- [ ] 代码产物正确展示
- [ ] 无前端 Mock 代码残留

---

## 功能 F2: 模板选择 + Blueprint 加载

### F2.1 目标
用户选择行业模板后，自动加载对应的 Blueprint 规范

### F2.2 数据流
```
用户点击模板 → 前端调用 selectTemplate API → 后端加载 blueprintSpec
                                           → 更新 AppSpec
                                           → 返回完整 PlanRoutingResult
```

### F2.3 后端实现

**新建: `PlanRoutingController.java` 新增端点**
```java
@PostMapping("/{appSpecId}/select-template")
public Result<PlanRoutingResult> selectTemplate(
    @PathVariable UUID appSpecId,
    @RequestBody SelectTemplateRequest request
) {
    return Result.success(
        planRoutingService.selectTemplate(appSpecId, request.getTemplateId())
    );
}
```

**修改: `PlanRoutingService.java`**
```java
@Transactional
public PlanRoutingResult selectTemplate(UUID appSpecId, UUID templateId) {
    // 1. 验证 AppSpec 存在
    AppSpecEntity appSpec = appSpecMapper.selectById(appSpecId);
    if (appSpec == null) {
        throw new BusinessException("AppSpec 不存在");
    }

    // 2. 加载 IndustryTemplate + blueprintSpec
    IndustryTemplateEntity template = templateMapper.selectById(templateId);
    if (template == null) {
        throw new BusinessException("模板不存在");
    }

    Map<String, Object> blueprintSpec = template.getBlueprintSpec();

    // 3. 更新 AppSpec
    appSpec.setSelectedTemplateId(templateId);
    appSpec.setBlueprintSpec(blueprintSpec);
    appSpec.setBlueprintModeEnabled(blueprintSpec != null);
    appSpecMapper.updateById(appSpec);

    log.info("✅ Blueprint Mode {} - templateId: {}",
        blueprintSpec != null ? "激活" : "未激活", templateId);

    // 4. 返回更新后的结果
    return buildPlanRoutingResult(appSpec);
}
```

### F2.4 前端实现

**新建: `frontend/src/lib/api/plan-routing.ts` 新增函数**
```typescript
export async function selectTemplate(
  appSpecId: string,
  templateId: string
): Promise<PlanRoutingResult> {
  return post(`/v1/plan-routing/${appSpecId}/select-template`, { templateId });
}
```

**修改: 模板选择组件（RequirementForm 或 TemplateGallery）**
```typescript
async function handleTemplateSelect(template: TemplateInfo) {
  // 更新本地状态
  setSelectedTemplate(template);

  // 如果已有 appSpecId，调用后端保存
  if (appSpecId) {
    try {
      const result = await selectTemplate(appSpecId, template.id);
      // 更新 Blueprint 状态
      setBlueprintModeEnabled(result.blueprintModeEnabled);
      toast.success('模板已选择，Blueprint 约束已加载');
    } catch (error) {
      toast.error('模板选择失败');
    }
  }
}
```

### F2.5 验收标准
- [ ] 用户点击模板后，后端正确加载 blueprintSpec
- [ ] AppSpec.selectedTemplateId 正确保存
- [ ] AppSpec.blueprintSpec 正确保存
- [ ] 前端显示 Blueprint 激活状态

---

## 功能 F3: Blueprint 约束注入（G3 Agent Prompt）

### F3.1 目标
在 G3 代码生成过程中，将 Blueprint 约束注入到 Agent Prompt

### F3.2 后端实现

**新建: `backend/src/main/java/com/ingenio/backend/service/blueprint/BlueprintPromptBuilder.java`**
```java
@Service
public class BlueprintPromptBuilder {

    /**
     * 为 ArchitectAgent 构建约束 Prompt
     */
    public String buildArchitectConstraint(Map<String, Object> blueprintSpec) {
        if (blueprintSpec == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## Blueprint 约束（必须遵守）\n");

        // 技术栈约束
        Object constraints = blueprintSpec.get("constraints");
        if (constraints instanceof Map) {
            Map<?, ?> c = (Map<?, ?>) constraints;
            sb.append("### 技术栈约束\n");
            sb.append("- 数据库: ").append(c.get("database")).append("\n");
            sb.append("- 认证方式: ").append(c.get("auth")).append("\n");
            sb.append("- API风格: ").append(c.get("apiStyle")).append("\n");
        }

        // 强制表结构
        Object schema = blueprintSpec.get("schema");
        if (schema instanceof List) {
            sb.append("### 强制表结构\n");
            sb.append("以下表结构必须完全按照定义生成，不可修改表名和核心字段：\n");
            for (Object table : (List<?>) schema) {
                // 解析并输出表结构
            }
        }

        return sb.toString();
    }

    /**
     * 为 BackendCoderAgent Entity 生成构建约束
     */
    public String buildEntityConstraint(Map<String, Object> blueprintSpec) {
        // 类似实现...
    }

    /**
     * 为 BackendCoderAgent Service 生成构建约束
     */
    public String buildServiceConstraint(Map<String, Object> blueprintSpec) {
        // 类似实现...
    }
}
```

**修改: `ArchitectAgentImpl.java`**
```java
@Autowired
private BlueprintPromptBuilder blueprintPromptBuilder;

@Override
public ArchitectResult design(G3JobEntity job, Consumer<G3LogEntry> logConsumer) {
    Map<String, Object> blueprintSpec = job.getBlueprintSpec();

    // 构建 Blueprint 约束
    String blueprintConstraint = "";
    if (blueprintSpec != null) {
        blueprintConstraint = blueprintPromptBuilder.buildArchitectConstraint(blueprintSpec);
        logConsumer.accept(G3LogEntry.info(getRole(), "Blueprint Mode 激活 - 注入架构约束"));
    }

    // 合并到 Prompt
    String prompt = String.format(CONTRACT_PROMPT_TEMPLATE,
        job.getRequirement(),
        blueprintConstraint);  // 注入约束

    // ... 调用 AI 生成 ...
}
```

**修改: `BackendCoderAgentImpl.java`**
```java
private List<G3ArtifactEntity> generateEntities(G3JobEntity job, ...) {
    Map<String, Object> blueprintSpec = job.getBlueprintSpec();

    String entityConstraint = "";
    if (blueprintSpec != null) {
        entityConstraint = blueprintPromptBuilder.buildEntityConstraint(blueprintSpec);
    }

    String prompt = String.format(ENTITY_PROMPT_TEMPLATE,
        CODE_STANDARDS_PROMPT + entityConstraint,
        dbSchemaSql);

    // ... 生成代码 ...
}
```

### F3.3 验收标准
- [ ] Blueprint Mode 激活时，日志显示"Blueprint Mode 激活"
- [ ] 生成的契约包含 Blueprint 定义的表结构
- [ ] 生成的代码符合 Blueprint 约束

---

## 功能 F4: Blueprint 验证

### F4.1 目标
在 Coach 阶段验证生成的代码是否符合 Blueprint 规范

### F4.2 后端实现

**新建: `BlueprintValidator.java`**
```java
@Service
public class BlueprintValidator {

    public ComplianceResult validateSchemaCompliance(
        String dbSchemaSql,
        Map<String, Object> blueprintSpec
    ) {
        if (blueprintSpec == null) {
            return ComplianceResult.passed();
        }

        List<String> violations = new ArrayList<>();

        // 验证表结构
        List<?> requiredTables = (List<?>) blueprintSpec.get("schema");
        if (requiredTables != null) {
            for (Object table : requiredTables) {
                String tableName = ((Map<?, ?>) table).get("tableName").toString();
                if (!dbSchemaSql.contains(tableName)) {
                    violations.add("缺少必需表: " + tableName);
                }
            }
        }

        if (violations.isEmpty()) {
            return ComplianceResult.passed();
        }
        return ComplianceResult.failed(violations);
    }
}
```

**修改: `CoachAgentImpl.java`**
```java
@Autowired
private BlueprintValidator blueprintValidator;

@Override
public ValidationResult validate(G3JobEntity job, ...) {
    // 现有验证逻辑...

    // 新增: Blueprint 合规性验证
    if (Boolean.TRUE.equals(job.getBlueprintModeEnabled())) {
        ComplianceResult compliance = blueprintValidator.validateSchemaCompliance(
            job.getDbSchemaSql(),
            job.getBlueprintSpec()
        );

        if (!compliance.isPassed()) {
            logConsumer.accept(G3LogEntry.error(getRole(),
                "Blueprint 合规性验证失败: " + compliance.getViolations()));
            return ValidationResult.failed(compliance.getViolations());
        }

        logConsumer.accept(G3LogEntry.success(getRole(), "Blueprint 合规性验证通过 ✅"));
    }

    return ValidationResult.success();
}
```

### F4.3 前端展示
```typescript
// G3LogViewer 中展示验证结果
// 验证失败时高亮显示违规项
```

### F4.4 验收标准
- [ ] Blueprint Mode 下执行合规性验证
- [ ] 验证失败时显示具体违规项
- [ ] 验证通过时显示成功消息

---

## 功能 F6: OpenLovable-CN Blueprint 集成 🆕

### F6.1 目标
在 OpenLovable-CN 前端原型生成时注入 Blueprint 约束，确保生成的前端代码：
- 调用正确的 API 端点
- 使用正确的数据结构
- 遵循 UI 规范

### F6.2 OpenLovable-CN 需要的 Blueprint 子集

| Blueprint 内容 | 是否需要 | 用途 |
|---------------|---------|------|
| `apiSpec` | ✅ | 前端调用的 API 端点定义 |
| `dataStructure` | ✅ | 组件渲染的数据字段定义 |
| `uiSpec` | ✅ | 视觉风格和组件规范 |
| `schema` (DDL) | ❌ | 前端不直接操作数据库 |
| `constraints.database` | ❌ | 后端技术栈无关 |

### F6.3 接口设计

**现有请求 DTO 扩展**: `OpenLovableGenerateRequest.java`

```java
/**
 * OpenLovable代码生成请求DTO - V2.1 Blueprint 增强版
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenLovableGenerateRequest {

    // === 现有字段（保持不变） ===
    private String userRequirement;
    private List<String> referenceUrls;
    private String customizationRequirement;
    @Builder.Default
    private String aiModel = "gemini-3-pro-preview";
    private Boolean needsCrawling;
    @Builder.Default
    private Integer timeoutSeconds = 30;
    @Builder.Default
    private Boolean streaming = false;
    private String sandboxId;

    // === 新增 Blueprint 相关字段 ===

    /**
     * Blueprint 前端约束规范（可选）
     * 仅包含前端相关的约束，不包含数据库 DDL
     *
     * 结构示例：
     * {
     *   "apiSpec": {
     *     "baseUrl": "/api/v1",
     *     "endpoints": [
     *       { "path": "/products", "method": "GET", "response": "Product[]" },
     *       { "path": "/products/{id}", "method": "GET", "response": "Product" }
     *     ]
     *   },
     *   "dataStructure": {
     *     "Product": {
     *       "id": "string",
     *       "name": "string",
     *       "price": "number",
     *       "description": "string",
     *       "images": "string[]"
     *     }
     *   },
     *   "uiSpec": {
     *     "primaryColor": "#6366f1",
     *     "style": "modern-minimal",
     *     "components": ["shadcn-ui", "lucide-react"]
     *   }
     * }
     */
    private Map<String, Object> blueprintFrontendSpec;

    /**
     * 是否启用 Blueprint 模式
     * 当 blueprintFrontendSpec 不为空时自动设为 true
     */
    private Boolean blueprintModeEnabled;

    /**
     * 构建发送给 OpenLovable-CN 的增强提示词
     * 包含 Blueprint 约束注入
     */
    public String buildEnhancedPrompt() {
        StringBuilder prompt = new StringBuilder();

        // 基础需求
        prompt.append(userRequirement);

        // 参考网站
        if (shouldCrawl()) {
            prompt.append("\n\n参考网站：");
            for (String url : referenceUrls) {
                prompt.append("\n- ").append(url);
            }
        }

        // 定制化需求
        if (customizationRequirement != null && !customizationRequirement.trim().isEmpty()) {
            prompt.append("\n\n定制化要求：\n").append(customizationRequirement);
        }

        // 🆕 Blueprint 约束注入
        if (blueprintFrontendSpec != null && !blueprintFrontendSpec.isEmpty()) {
            prompt.append("\n\n## Blueprint 约束（强制遵守）\n");
            prompt.append(buildBlueprintConstraintPrompt());
        }

        return prompt.toString();
    }

    /**
     * 构建 Blueprint 约束 Prompt
     */
    private String buildBlueprintConstraintPrompt() {
        StringBuilder sb = new StringBuilder();

        // API 规范
        Object apiSpec = blueprintFrontendSpec.get("apiSpec");
        if (apiSpec instanceof Map) {
            sb.append("\n### API 规范\n");
            sb.append("前端必须调用以下 API 端点：\n");
            sb.append("```json\n");
            sb.append(new ObjectMapper().writeValueAsString(apiSpec));
            sb.append("\n```\n");
        }

        // 数据结构
        Object dataStructure = blueprintFrontendSpec.get("dataStructure");
        if (dataStructure instanceof Map) {
            sb.append("\n### 数据结构\n");
            sb.append("组件必须使用以下数据结构：\n");
            sb.append("```typescript\n");
            // 转换为 TypeScript 接口
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) dataStructure).entrySet()) {
                sb.append("interface ").append(entry.getKey()).append(" {\n");
                if (entry.getValue() instanceof Map) {
                    for (Map.Entry<?, ?> field : ((Map<?, ?>) entry.getValue()).entrySet()) {
                        sb.append("  ").append(field.getKey()).append(": ");
                        sb.append(field.getValue()).append(";\n");
                    }
                }
                sb.append("}\n\n");
            }
            sb.append("```\n");
        }

        // UI 规范
        Object uiSpec = blueprintFrontendSpec.get("uiSpec");
        if (uiSpec instanceof Map) {
            Map<?, ?> ui = (Map<?, ?>) uiSpec;
            sb.append("\n### UI 规范\n");
            if (ui.get("primaryColor") != null) {
                sb.append("- 主色调: ").append(ui.get("primaryColor")).append("\n");
            }
            if (ui.get("style") != null) {
                sb.append("- 设计风格: ").append(ui.get("style")).append("\n");
            }
            if (ui.get("components") instanceof List) {
                sb.append("- 必须使用组件库: ").append(ui.get("components")).append("\n");
            }
        }

        return sb.toString();
    }
}
```

### F6.4 后端服务修改

**修改: `OpenLovableController.java`**

```java
@PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public ResponseEntity<StreamingResponseBody> generateCodeStream(
        @RequestBody Map<String, Object> request,
        HttpServletRequest httpRequest) {

    // ... 现有代码 ...

    // 🆕 处理 Blueprint 前端约束
    if (adaptedRequest.containsKey("blueprintFrontendSpec")) {
        Map<String, Object> blueprintSpec = (Map<String, Object>) adaptedRequest.get("blueprintFrontendSpec");
        if (blueprintSpec != null && !blueprintSpec.isEmpty()) {
            String blueprintConstraint = buildBlueprintConstraintPrompt(blueprintSpec);
            originalPrompt = originalPrompt + "\n\n" + blueprintConstraint;
            log.info("已注入 Blueprint 前端约束");
        }
    }

    // ... 继续现有流程 ...
}

/**
 * 构建 Blueprint 约束 Prompt（提取为方法）
 */
private String buildBlueprintConstraintPrompt(Map<String, Object> blueprintSpec) {
    StringBuilder sb = new StringBuilder();
    sb.append("## Blueprint 约束（强制遵守）\n");

    // API 规范
    Object apiSpec = blueprintSpec.get("apiSpec");
    if (apiSpec != null) {
        sb.append("\n### API 端点\n");
        sb.append("前端必须调用以下 API：\n");
        sb.append(toJson(apiSpec));
    }

    // 数据结构
    Object dataStructure = blueprintSpec.get("dataStructure");
    if (dataStructure != null) {
        sb.append("\n### 数据结构\n");
        sb.append(toJson(dataStructure));
    }

    return sb.toString();
}
```

**修改: `PlanRoutingService.java`** - 传递 Blueprint 到 OpenLovable

```java
public CompletableFuture<PlanRoutingResult> processRoute(...) {
    // ... 现有代码 ...

    // 调用 OpenLovable 生成原型时传递 Blueprint
    if (appSpec.getBlueprintModeEnabled() && appSpec.getBlueprintSpec() != null) {
        Map<String, Object> fullBlueprint = appSpec.getBlueprintSpec();

        // 提取前端相关的 Blueprint 子集
        Map<String, Object> frontendBlueprint = extractFrontendBlueprint(fullBlueprint);

        // 传递给 OpenLovable
        openLovableRequest.setBlueprintFrontendSpec(frontendBlueprint);
        openLovableRequest.setBlueprintModeEnabled(true);
    }

    // ... 调用 OpenLovableService ...
}

/**
 * 从完整 Blueprint 中提取前端相关部分
 */
private Map<String, Object> extractFrontendBlueprint(Map<String, Object> fullBlueprint) {
    Map<String, Object> frontendSpec = new HashMap<>();

    // 提取 API 规范
    if (fullBlueprint.containsKey("apiSpec")) {
        frontendSpec.put("apiSpec", fullBlueprint.get("apiSpec"));
    }

    // 从 schema 生成数据结构（表结构 → TypeScript 接口）
    if (fullBlueprint.containsKey("schema")) {
        frontendSpec.put("dataStructure", convertSchemaToDataStructure(fullBlueprint.get("schema")));
    }

    // 提取 UI 规范
    if (fullBlueprint.containsKey("uiSpec")) {
        frontendSpec.put("uiSpec", fullBlueprint.get("uiSpec"));
    }

    return frontendSpec;
}
```

### F6.5 数据流示意

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    OpenLovable-CN Blueprint 集成数据流                        │
└─────────────────────────────────────────────────────────────────────────────┘

用户选择模板
    │
    ▼
┌─────────────────┐
│ IndustryTemplate │  blueprintSpec: {
│   (PostgreSQL)   │    schema: [...],      ← 后端使用
│                  │    constraints: {...}, ← 后端使用
│                  │    apiSpec: {...},     ← 前端使用 ✅
│                  │    uiSpec: {...}       ← 前端使用 ✅
│                  │  }
└────────┬────────┘
         │
         │ selectTemplate API
         ▼
┌─────────────────┐
│   AppSpecEntity  │  blueprintSpec → 完整 Blueprint
│   (PostgreSQL)   │  blueprintModeEnabled → true
└────────┬────────┘
         │
         │ 分发到不同生成路径
         │
    ┌────┴────────────────────┐
    │                         │
    ▼                         ▼
┌──────────────┐      ┌──────────────────────┐
│  Java G3     │      │  OpenLovable-CN      │
│  后端生成     │      │  前端原型生成          │
│              │      │                      │
│  接收完整     │      │  接收前端子集:         │
│  blueprintSpec│      │  - apiSpec           │
│              │      │  - dataStructure     │
│  ┌──────────┐│      │  - uiSpec            │
│  │schema    ││      │                      │
│  │constraints││      │  生成遵循 API 规范    │
│  │apiSpec   ││      │  的 React 组件        │
│  └──────────┘│      └──────────────────────┘
└──────────────┘
```

### F6.6 验收标准
- [ ] `OpenLovableGenerateRequest` 新增 `blueprintFrontendSpec` 字段
- [ ] `OpenLovableController` 正确解析并注入 Blueprint 约束
- [ ] `PlanRoutingService` 正确提取前端 Blueprint 子集
- [ ] 生成的前端代码调用正确的 API 端点
- [ ] 生成的前端代码使用正确的数据结构
- [ ] Blueprint Mode 下日志显示"已注入 Blueprint 前端约束"

---

## 功能 F5: 数据结构扩展

### F5.1 目标
扩展 Entity 和数据库表以支持 Blueprint

### F5.2 后端实现

**修改: `G3JobEntity.java`**
```java
// 新增字段
@TableField(value = "blueprint_spec", typeHandler = JacksonTypeHandler.class)
private Map<String, Object> blueprintSpec;

@TableField("matched_template_id")
private UUID matchedTemplateId;

@TableField("blueprint_mode_enabled")
private Boolean blueprintModeEnabled;

// 更新 create 方法
public static G3JobEntity create(
    String requirement,
    UUID userId,
    UUID tenantId,
    String templateContext,
    UUID matchedTemplateId,           // 新增
    Map<String, Object> blueprintSpec // 新增
) {
    return G3JobEntity.builder()
        .requirement(requirement)
        .userId(userId)
        .tenantId(tenantId)
        .templateContext(templateContext)
        .matchedTemplateId(matchedTemplateId)
        .blueprintSpec(blueprintSpec)
        .blueprintModeEnabled(blueprintSpec != null)
        // ... 其他字段 ...
        .build();
}
```

**修改: `AppSpecEntity.java`**
```java
// 新增字段（部分已存在但未持久化）
@TableField("blueprint_id")
private String blueprintId;

@TableField(value = "blueprint_spec", typeHandler = JacksonTypeHandler.class)
private Map<String, Object> blueprintSpec;

@TableField("blueprint_mode_enabled")
private Boolean blueprintModeEnabled;
```

**新建迁移脚本**:

`V024__add_blueprint_to_appspec.sql`:
```sql
-- AppSpec 表添加 Blueprint 相关字段
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS blueprint_id VARCHAR(64);
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS blueprint_spec JSONB;
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS blueprint_mode_enabled BOOLEAN DEFAULT FALSE;

COMMENT ON COLUMN app_specs.blueprint_id IS 'Blueprint ID';
COMMENT ON COLUMN app_specs.blueprint_spec IS 'Blueprint 完整规范 (JSONB)';
COMMENT ON COLUMN app_specs.blueprint_mode_enabled IS 'Blueprint 模式是否启用';
```

`V025__add_blueprint_to_g3job.sql`:
```sql
-- G3Job 表添加 Blueprint 相关字段
ALTER TABLE g3_jobs ADD COLUMN IF NOT EXISTS blueprint_spec JSONB;
ALTER TABLE g3_jobs ADD COLUMN IF NOT EXISTS matched_template_id UUID;
ALTER TABLE g3_jobs ADD COLUMN IF NOT EXISTS blueprint_mode_enabled BOOLEAN DEFAULT FALSE;

COMMENT ON COLUMN g3_jobs.blueprint_spec IS 'Blueprint 完整规范 (JSONB)';
COMMENT ON COLUMN g3_jobs.matched_template_id IS '匹配的行业模板ID';
COMMENT ON COLUMN g3_jobs.blueprint_mode_enabled IS 'Blueprint 模式是否启用';
```

### F5.3 前端类型定义

**修改: `frontend/src/types/g3.ts`**
```typescript
export interface G3Job {
  // 现有字段...

  // 新增 Blueprint 相关
  blueprintSpec?: Record<string, unknown>;
  matchedTemplateId?: string;
  blueprintModeEnabled?: boolean;
}
```

### F5.4 验收标准
- [ ] 数据库迁移成功执行
- [ ] Entity 字段正确映射
- [ ] 前端类型定义正确
- [ ] 后端编译通过 (`mvn compile`)
- [ ] 前端编译通过 (`pnpm tsc --noEmit`)

---

## 5. 执行顺序（功能闭环）

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          执行顺序（功能闭环原则）                              │
└─────────────────────────────────────────────────────────────────────────────┘

Week 1 (Day 1-2):
┌────────────┐
│ F5: 数据   │ ← 基础设施，必须先行
│ 结构扩展   │   后端: Entity + 迁移
│ (~4h)      │   前端: 类型定义
└─────┬──────┘
      │
      ▼
Week 1 (Day 2-3):
┌────────────┐
│ F1: Lab    │ ← 清理架构，移除 Mock
│ 页面迁移   │   前端: 移除 Mock + 修改 Route
│ (~4h)      │   后端: 验证 G3 API
└─────┬──────┘
      │
      ▼
Week 1 (Day 3-4):
┌────────────┐
│ F2: 模板   │ ← 核心功能 1
│ 选择+加载  │   前端: 模板选择组件
│ (~6h)      │   后端: selectTemplate API
└─────┬──────┘
      │
      ▼
Week 2 (Day 1-2):
┌────────────┐
│ F3: 约束   │ ← 核心功能 2（后端为主）
│ 注入       │   后端: BlueprintPromptBuilder
│ (~6h)      │   后端: Agent Prompt 修改
└─────┬──────┘
      │
      ▼
Week 2 (Day 2-3):
┌────────────┐
│ F4: 验证   │ ← 核心功能 3
│            │   后端: BlueprintValidator
│ (~4h)      │   前端: 验证结果展示
└─────┬──────┘
      │
      ▼
Week 2 (Day 3):
┌────────────┐
│ F6: OL-CN  │ ← 核心功能 4（前端原型 Blueprint 集成）
│ Blueprint  │   后端: OpenLovableController 增强
│ 集成 (~4h) │   后端: PlanRoutingService 分发
└────────────┘
```

---

## 6. 文件修改清单

### 6.1 删除文件 (3个)
```
frontend/src/lib/lab/
├── g3-engine.ts       [删除]
├── mock-agents.ts     [删除]
└── typescript-check.ts [删除]
```

### 6.2 新建文件 (4个)
```
backend/src/main/java/com/ingenio/backend/service/blueprint/
├── BlueprintPromptBuilder.java    [新建]
└── BlueprintValidator.java        [新建]

backend/src/main/resources/db/migration/
├── V024__add_blueprint_to_appspec.sql  [新建]
└── V025__add_blueprint_to_g3job.sql    [新建]
```

### 6.3 修改文件 (12个)
```
后端 (8个):
├── entity/AppSpecEntity.java              [修改] +3字段
├── entity/g3/G3JobEntity.java             [修改] +3字段 +create方法
├── controller/PlanRoutingController.java  [修改] +1 API
├── service/PlanRoutingService.java        [修改] +selectTemplate方法 +extractFrontendBlueprint方法
├── agent/g3/impl/ArchitectAgentImpl.java  [修改] +Blueprint注入
├── agent/g3/impl/BackendCoderAgentImpl.java [修改] +Blueprint注入
├── agent/g3/impl/CoachAgentImpl.java      [修改] +Blueprint验证
├── controller/OpenLovableController.java  [修改] +Blueprint前端约束注入 (F6)
└── dto/request/OpenLovableGenerateRequest.java [修改] +blueprintFrontendSpec字段 (F6)

前端 (4个):
├── app/api/lab/g3-poc/route.ts            [修改] 改为调用后端
├── app/api/v1/generate/analyze-stream/route.ts [修改] 移除g3 mode
├── lib/api/plan-routing.ts                [修改] +selectTemplate
└── types/g3.ts                            [修改] +Blueprint类型
```

---

## 7. 验收检查清单

### 7.1 编译验证
```bash
# 后端
cd backend
mvn clean compile -DskipTests  # 0 errors
mvn test                       # 所有测试通过

# 前端
cd frontend
pnpm tsc --noEmit              # 0 errors
pnpm lint                      # 0 errors
pnpm build                     # 构建成功
```

### 7.2 功能验证
- [ ] Lab 页面调用后端 Java G3（无 Mock）
- [ ] 模板选择后 Blueprint 正确加载
- [ ] G3 生成时 Blueprint 约束注入
- [ ] Coach 验证 Blueprint 合规性
- [ ] 前端显示 Blueprint 状态

### 7.3 端到端测试
```
测试场景 1: Blueprint Mode
输入: "做一个校园二手交易平台"
预期: 匹配模板 → 加载 Blueprint → 约束注入 → 代码生成 → 验证通过

测试场景 2: Freedom Mode
输入: "做一个自定义应用"
预期: 无模板匹配 → Blueprint 为空 → 正常生成 → 无约束验证
```

---

## 8. 风险评估

| 风险 | 等级 | 影响 | 缓解措施 |
|------|------|------|---------|
| 数据库迁移失败 | 🔴 高 | 数据丢失 | 测试环境先验证 |
| Lab 页面功能回退 | 🟠 中 | 用户体验 | 充分测试后再移除 Mock |
| Blueprint 格式不兼容 | 🟡 低 | 约束失效 | 添加 Schema 验证 |

---

## 9. Python Scout 迁移计划（中期）

当前状态：`g3-engine/` 保留探索用

中期计划：
1. 将 Scout 算法移植到 Java
2. 集成到 `G3OrchestratorService` 的 PLANNING 阶段
3. 移除 Python g3-engine

---

**版本历史**:
- V1: 仅关注 G3 Engine Blueprint 注入
- V2: 全链路集成（9 Phase）
- V3: 功能闭环 + 架构清理（5 功能模块）
- **V3.1**: 新增 F6 OpenLovable-CN Blueprint 集成（6 功能模块，~28h）

---

## 附录 A: OpenLovable-CN Blueprint 接口规范

### A.1 请求接口

**端点**: `POST /v1/openlovable/generate/stream`

**请求体** (新增字段):
```json
{
  "userRequirement": "做一个校园二手交易平台",
  "sandboxId": "sb_xxx",
  "aiModel": "gemini-3-pro-preview",

  "blueprintFrontendSpec": {
    "apiSpec": {
      "baseUrl": "/api/v1",
      "endpoints": [
        { "path": "/products", "method": "GET", "response": "Product[]" },
        { "path": "/products/{id}", "method": "GET", "response": "Product" },
        { "path": "/products", "method": "POST", "request": "CreateProductRequest", "response": "Product" }
      ]
    },
    "dataStructure": {
      "Product": {
        "id": "string",
        "name": "string",
        "price": "number",
        "description": "string",
        "sellerId": "string",
        "images": "string[]",
        "status": "'active' | 'sold' | 'reserved'",
        "createdAt": "Date"
      },
      "CreateProductRequest": {
        "name": "string",
        "price": "number",
        "description": "string",
        "images": "string[]"
      }
    },
    "uiSpec": {
      "primaryColor": "#6366f1",
      "style": "modern-minimal",
      "components": ["shadcn-ui", "lucide-react"],
      "theme": "light"
    }
  },
  "blueprintModeEnabled": true
}
```

### A.2 Blueprint 约束注入效果

当 `blueprintFrontendSpec` 存在时，生成的前端代码将：

1. **API 调用遵循规范**:
```typescript
// 生成的代码会使用正确的 API 端点
const products = await fetch('/api/v1/products').then(r => r.json());
const product = await fetch(`/api/v1/products/${id}`).then(r => r.json());
```

2. **数据结构类型安全**:
```typescript
// 生成的代码会使用正确的接口定义
interface Product {
  id: string;
  name: string;
  price: number;
  description: string;
  sellerId: string;
  images: string[];
  status: 'active' | 'sold' | 'reserved';
  createdAt: Date;
}
```

3. **UI 风格一致**:
```tsx
// 生成的代码会使用指定的组件库和配色
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ShoppingCart } from 'lucide-react';

// 使用主色调
<Button className="bg-indigo-600 hover:bg-indigo-700">购买</Button>
```
