# G3引擎任务规划增强与JeecgBoot能力集成 - 实施方案

> **版本**: v1.0.0
> **创建日期**: 2025-01-08
> **预估工时**: 105小时（约14个工作日）
> **状态**: 已批准，实施中

---

## 一、项目背景

### 1.1 问题诊断

G3代码生成引擎当前存在三个核心问题：

| 问题 | 表现 | 影响 |
|-----|------|------|
| **依赖关系错误** | 生成的代码有import缺失、类名不存在 | 编译失败率高 |
| **缺乏任务规划** | 各Agent独立工作，缺乏全局上下文 | 代码质量不一致 |
| **能力集成缺失** | 无法集成JeecgBoot的登录、支付等能力 | 功能不完整 |

### 1.2 解决方案

基于 **planning-with-files (Manus工作流)** 的三文件模式：

```
task_plan.md   → 追踪阶段和进度
notes.md       → 存储研究和发现
context.md     → 项目上下文（已生成文件清单、import索引）
```

**核心原则**：
- 文件系统作为外部记忆
- 每次决策前重新读取计划文件
- 按用户+项目隔离存储
- 按依赖顺序生成代码（Entity → Mapper → Service → Controller）

---

## 二、技术架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端 (Next.js)                          │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ capability-     │  │ capability-     │  │ task-dependency-│ │
│  │ picker.tsx      │  │ config-form.tsx │  │ graph.tsx       │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ planning-file-  │  │ task-progress-  │  │ artifact-group- │ │
│  │ viewer.tsx      │  │ tracker.tsx     │  │ list.tsx        │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                       后端 (Spring Boot)                         │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                 G3 Orchestrator Service                  │   │
│  │  ┌───────────┐  ┌───────────┐  ┌───────────────────┐   │   │
│  │  │ Planning  │  │ Dependency│  │ Context           │   │   │
│  │  │ File      │  │ Analyzer  │  │ Builder           │   │   │
│  │  │ Service   │  │           │  │                   │   │   │
│  │  └───────────┘  └───────────┘  └───────────────────┘   │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    G3 Agent Layer                        │   │
│  │  ┌───────────┐  ┌───────────┐  ┌───────────────────┐   │   │
│  │  │ Architect │  │ Backend   │  │ Coach             │   │   │
│  │  │ Agent     │  │ Coder     │  │ Agent             │   │   │
│  │  │           │  │ Agent     │  │                   │   │   │
│  │  └───────────┘  └───────────┘  └───────────────────┘   │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              JeecgBoot Capability Layer                  │   │
│  │  ┌───────────┐  ┌───────────┐  ┌───────────────────┐   │   │
│  │  │ Capability│  │ Config    │  │ Code              │   │   │
│  │  │ Service   │  │ Encrypt   │  │ Generator         │   │   │
│  │  │           │  │ Service   │  │                   │   │   │
│  │  └───────────┘  └───────────┘  └───────────────────┘   │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      数据库 (PostgreSQL)                         │
├─────────────────────────────────────────────────────────────────┤
│  jeecg_capabilities          │ 能力清单表                        │
│  project_capability_configs  │ 项目能力配置表                    │
│  g3_planning_files          │ 规划文件存储表                     │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 任务依赖图 (DAG)

```
┌─────────┐     ┌─────────┐     ┌─────────┐     ┌────────────┐
│ Entity  │────▶│ Mapper  │────▶│ Service │────▶│ Controller │
│ 生成    │      │ 生成    │      │ 生成    │     │ 生成       │
└─────────┘     └─────────┘     └─────────┘     └────────────┘
     │               │               │                │
     ▼               ▼               ▼                ▼
┌─────────────────────────────────────────────────────────────┐
│                    context.md 更新                           │
│  - 已生成文件清单                                            │
│  - import索引                                               │
│  - 类签名摘要                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 三、实施阶段

### 阶段1：JeecgBoot能力清单梳理 (P0, 10h)

**目标**：建立JeecgBoot能力的数据模型和管理接口

#### 任务清单

| 序号 | 任务 | 产出物 | 工时 |
|-----|------|--------|------|
| 1.1 | 设计能力清单数据模型 | JeecgCapabilityEntity.java | 2h |
| 1.2 | 创建数据库迁移脚本 | V032__create_jeecg_capabilities_table.sql | 1h |
| 1.3 | 实现能力清单Mapper | JeecgCapabilityMapper.java | 1h |
| 1.4 | 实现能力清单Service | JeecgCapabilityService.java | 2h |
| 1.5 | 实现能力清单Controller | JeecgCapabilityController.java | 2h |
| 1.6 | 初始化基础能力数据 | V033__init_jeecg_capabilities.sql | 2h |

#### 新增文件

```
backend/src/main/java/com/ingenio/backend/
├── entity/
│   └── JeecgCapabilityEntity.java           ✅ 已完成
├── mapper/
│   └── JeecgCapabilityMapper.java
├── service/
│   └── JeecgCapabilityService.java
└── controller/
    └── JeecgCapabilityController.java

backend/src/main/resources/db/migration/
├── V032__create_jeecg_capabilities_table.sql
└── V033__init_jeecg_capabilities.sql
```

#### 初始能力范围

| 能力代码 | 能力名称 | 分类 | 优先级 |
|---------|---------|------|--------|
| auth | 用户认证 | infrastructure | P0 |
| payment_alipay | 支付宝支付 | business | P1 |
| payment_wechat | 微信支付 | business | P1 |
| sms_aliyun | 阿里云短信 | third_party | P1 |
| oss_aliyun | 阿里云OSS | third_party | P1 |
| oss_minio | MinIO存储 | third_party | P2 |

---

### 阶段2：用户配置管理模块 (P0, 14h)

**目标**：允许用户为项目配置JeecgBoot能力的参数

#### 任务清单

| 序号 | 任务 | 产出物 | 工时 |
|-----|------|--------|------|
| 2.1 | 设计项目能力配置数据模型 | ProjectCapabilityConfigEntity.java | 2h |
| 2.2 | 创建配置存储数据库迁移脚本 | V034__create_project_capability_configs.sql | 1h |
| 2.3 | 实现配置加密服务 | CapabilityConfigEncryptService.java | 3h |
| 2.4 | 实现配置管理Mapper | ProjectCapabilityConfigMapper.java | 1h |
| 2.5 | 实现配置管理服务 | ProjectCapabilityConfigService.java | 4h |
| 2.6 | 实现配置管理Controller | ProjectCapabilityConfigController.java | 3h |

#### 新增文件

```
backend/src/main/java/com/ingenio/backend/
├── entity/
│   └── ProjectCapabilityConfigEntity.java
├── mapper/
│   └── ProjectCapabilityConfigMapper.java
├── service/
│   ├── ProjectCapabilityConfigService.java
│   └── CapabilityConfigEncryptService.java
└── controller/
    └── ProjectCapabilityConfigController.java

backend/src/main/resources/db/migration/
└── V034__create_project_capability_configs_table.sql
```

#### 加密方案

```java
// 使用AES-256-GCM加密敏感配置
public class CapabilityConfigEncryptService {
    // 加密密钥从环境变量读取
    private static final String KEY_ENV = "CAPABILITY_CONFIG_ENCRYPT_KEY";

    public String encrypt(String plaintext);
    public String decrypt(String ciphertext);
}
```

---

### 阶段3：规划文件系统 (P1, 17h)

**目标**：实现基于Manus工作流的规划文件管理

#### 任务清单

| 序号 | 任务 | 产出物 | 工时 |
|-----|------|--------|------|
| 3.1 | 设计规划文件数据模型 | G3PlanningFileEntity.java | 2h |
| 3.2 | 创建规划文件数据库迁移脚本 | V035__create_g3_planning_files_table.sql | 1h |
| 3.3 | 实现规划文件Mapper | G3PlanningFileMapper.java | 1h |
| 3.4 | 实现规划文件服务 | G3PlanningFileService.java | 4h |
| 3.5 | 设计task_plan.md模板 | TaskPlanTemplate.java | 2h |
| 3.6 | 设计notes.md模板 | NotesTemplate.java | 2h |
| 3.7 | 设计context.md模板 | ContextTemplate.java | 2h |
| 3.8 | 实现规划文件Controller | G3PlanningController.java | 3h |

#### 新增文件

```
backend/src/main/java/com/ingenio/backend/
├── entity/g3/
│   └── G3PlanningFileEntity.java
├── mapper/g3/
│   └── G3PlanningFileMapper.java
├── service/g3/
│   ├── G3PlanningFileService.java
│   └── template/
│       ├── TaskPlanTemplate.java
│       ├── NotesTemplate.java
│       └── ContextTemplate.java
└── controller/g3/
    └── G3PlanningController.java

backend/src/main/resources/db/migration/
└── V035__create_g3_planning_files_table.sql
```

#### 规划文件模板

**task_plan.md 模板**：
```markdown
# 任务计划: {{projectName}}

## 目标
{{goal}}

## 阶段
- [ ] 阶段1: 架构设计
- [ ] 阶段2: Entity生成
- [ ] 阶段3: Mapper生成
- [ ] 阶段4: Service生成
- [ ] 阶段5: Controller生成
- [ ] 阶段6: 能力集成
- [ ] 阶段7: 编译验证

## 当前状态
**当前阶段**: {{currentPhase}}
**进度**: {{progress}}%

## 错误记录
{{errors}}

## 决策记录
{{decisions}}
```

**context.md 模板**：
```markdown
# 项目上下文: {{projectName}}

## 已生成文件
| 文件路径 | 类名 | 类型 | 状态 |
|---------|------|------|------|
{{#files}}
| {{path}} | {{className}} | {{type}} | {{status}} |
{{/files}}

## Import索引
{{#imports}}
### {{className}}
```java
{{importStatements}}
```
{{/imports}}

## 类签名摘要
{{#signatures}}
### {{className}}
```java
{{signature}}
```
{{/signatures}}
```

---

### 阶段4：G3执行引擎改造 (P1, 29h)

**目标**：改造G3引擎支持任务规划和依赖分析

#### 任务清单

| 序号 | 任务 | 产出物 | 工时 |
|-----|------|--------|------|
| 4.1 | 设计任务依赖图数据结构 | G3TaskDependencyGraph.java | 3h |
| 4.2 | 实现依赖分析器 | G3DependencyAnalyzer.java | 5h |
| 4.3 | 实现上下文构建器 | G3ContextBuilder.java | 4h |
| 4.4 | 改造ArchitectAgent | ArchitectAgentImpl.java改造 | 4h |
| 4.5 | 改造BackendCoderAgent | BackendCoderAgentImpl.java改造 | 5h |
| 4.6 | 改造CoachAgent | CoachAgentImpl.java改造 | 3h |
| 4.7 | 实现JeecgBoot能力代码生成器 | JeecgCapabilityCodeGenerator.java | 5h |

#### 新增文件

```
backend/src/main/java/com/ingenio/backend/service/g3/
├── G3TaskDependencyGraph.java
├── G3DependencyAnalyzer.java
├── G3ContextBuilder.java
└── JeecgCapabilityCodeGenerator.java
```

#### 修改文件

```
backend/src/main/java/com/ingenio/backend/
├── service/g3/
│   └── G3OrchestratorService.java          # 集成规划文件和依赖分析
└── agent/g3/impl/
    ├── ArchitectAgentImpl.java             # 读取/更新task_plan.md
    ├── BackendCoderAgentImpl.java          # 读取context.md，更新已生成文件
    └── CoachAgentImpl.java                 # 更新错误记录到task_plan.md
```

#### 核心算法

**依赖分析算法**：
```java
public class G3DependencyAnalyzer {
    /**
     * 分析生成任务的依赖关系，构建DAG
     *
     * 规则：
     * 1. Entity → Mapper → Service → Controller
     * 2. 如果Service A调用Service B，则B必须先生成
     * 3. 能力集成代码依赖所有业务代码
     */
    public G3TaskDependencyGraph analyze(List<G3Task> tasks) {
        // 拓扑排序确定生成顺序
    }
}
```

**上下文构建算法**：
```java
public class G3ContextBuilder {
    /**
     * 为每个Agent构建上下文
     *
     * 包含：
     * 1. 已生成文件的import语句
     * 2. 已生成类的签名（方法名、参数、返回值）
     * 3. 当前任务的依赖类信息
     */
    public String buildContext(G3Task task, List<G3GeneratedFile> files) {
        // 构建精简的上下文信息
    }
}
```

---

### 阶段5：前端适配开发 (P1, 35h)

#### 5.1 能力选择与配置模块 (12h)

| 序号 | 任务 | 产出物 | 工时 |
|-----|------|--------|------|
| 5.1.1 | 能力类型定义 | types/capability.ts | 1h |
| 5.1.2 | 能力API客户端 | lib/api/capability.ts | 2h |
| 5.1.3 | 能力选择器组件 | components/wizard/capability-picker.tsx | 3h |
| 5.1.4 | 配置表单组件 | components/wizard/capability-config-form.tsx | 3h |
| 5.1.5 | 能力选择Hook | hooks/use-capabilities.ts | 2h |
| 5.1.6 | 集成到wizard流程 | 修改wizard页面 | 1h |

#### 5.2 G3控制台增强 (10h)

| 序号 | 任务 | 产出物 | 工时 |
|-----|------|--------|------|
| 5.2.1 | 规划文件类型定义 | types/g3-planning.ts | 1h |
| 5.2.2 | 规划文件API客户端 | lib/api/g3-planning.ts | 2h |
| 5.2.3 | 任务依赖图组件 | components/g3/task-dependency-graph.tsx | 3h |
| 5.2.4 | 规划文件查看器 | components/g3/planning-file-viewer.tsx | 2h |
| 5.2.5 | 集成到G3控制台 | 修改g3-console.tsx | 2h |

#### 5.3 日志流增强 (6h)

| 序号 | 任务 | 产出物 | 工时 |
|-----|------|--------|------|
| 5.3.1 | 增强日志类型 | 更新types/g3.ts | 1h |
| 5.3.2 | 任务进度追踪 | components/g3/task-progress-tracker.tsx | 2h |
| 5.3.3 | 依赖状态指示器 | components/g3/dependency-status.tsx | 2h |
| 5.3.4 | 集成到日志流 | 修改log-stream.tsx | 1h |

#### 5.4 产物展示增强 (7h)

| 序号 | 任务 | 产出物 | 工时 |
|-----|------|--------|------|
| 5.4.1 | 能力集成代码高亮 | components/g3/capability-code-viewer.tsx | 2h |
| 5.4.2 | 契约文件查看器增强 | 修改g3-result-dialog.tsx | 2h |
| 5.4.3 | 产物分组展示 | components/g3/artifact-group-list.tsx | 2h |
| 5.4.4 | 下载功能增强 | 修改产物下载逻辑 | 1h |

#### 前端新增文件汇总

```
frontend/src/
├── types/
│   ├── capability.ts                    # 能力相关类型定义
│   └── g3-planning.ts                   # 规划文件类型定义
├── lib/api/
│   ├── capability.ts                    # 能力API客户端
│   └── g3-planning.ts                   # 规划文件API客户端
├── hooks/
│   └── use-capabilities.ts              # 能力管理Hook
└── components/
    ├── wizard/
    │   ├── capability-picker.tsx        # 能力选择器
    │   └── capability-config-form.tsx   # 配置表单
    └── g3/
        ├── task-dependency-graph.tsx    # 任务依赖图
        ├── planning-file-viewer.tsx     # 规划文件查看器
        ├── task-progress-tracker.tsx    # 任务进度追踪
        ├── dependency-status.tsx        # 依赖状态指示器
        ├── capability-code-viewer.tsx   # 能力代码查看器
        └── artifact-group-list.tsx      # 产物分组列表
```

#### 前端修改文件汇总

```
frontend/src/
├── app/wizard/[id]/page.tsx             # 增加能力选择步骤
├── components/
│   ├── wizard/configuration-panel.tsx   # 集成能力配置
│   └── g3/
│       ├── g3-console.tsx               # 增加规划文件展示
│       ├── g3-console-view.tsx          # 增加依赖图视图
│       ├── log-stream.tsx               # 增加任务进度展示
│       └── g3-result-dialog.tsx         # 增强产物展示
├── types/g3.ts                          # 增加任务依赖相关类型
└── lib/api/g3.ts                        # 增加规划文件相关API
```

---

## 四、API接口设计

### 4.1 JeecgBoot能力管理

```yaml
# 获取能力清单
GET /api/v1/jeecg/capabilities
Response:
  - id: UUID
  - code: string
  - name: string
  - category: string
  - icon: string
  - configTemplate: object
  - dependencies: string[]

# 获取单个能力详情
GET /api/v1/jeecg/capabilities/{code}
Response:
  - id: UUID
  - code: string
  - name: string
  - description: string
  - category: string
  - apis: API[]
  - configTemplate: object
  - codeTemplates: object
  - docUrl: string
  - examples: object
```

### 4.2 项目能力配置

```yaml
# 获取项目已配置的能力
GET /api/v1/projects/{projectId}/capabilities
Response:
  - capabilityCode: string
  - capabilityName: string
  - status: 'pending' | 'validated' | 'failed'
  - configuredAt: timestamp

# 添加能力配置
POST /api/v1/projects/{projectId}/capabilities
Request:
  - capabilityCode: string
  - configValues: object  # 敏感字段会自动加密
Response:
  - id: UUID
  - status: 'pending'

# 更新能力配置
PUT /api/v1/projects/{projectId}/capabilities/{code}
Request:
  - configValues: object
Response:
  - status: 'pending' | 'validated'

# 删除能力配置
DELETE /api/v1/projects/{projectId}/capabilities/{code}

# 验证能力配置
POST /api/v1/projects/{projectId}/capabilities/{code}/validate
Response:
  - valid: boolean
  - error: string | null
```

### 4.3 G3规划文件

```yaml
# 获取所有规划文件
GET /api/v1/g3/jobs/{jobId}/planning
Response:
  - taskPlan: PlanningFile
  - notes: PlanningFile
  - context: PlanningFile

# 获取单个规划文件
GET /api/v1/g3/jobs/{jobId}/planning/{type}
# type: 'task_plan' | 'notes' | 'context'
Response:
  - id: UUID
  - content: string (markdown)
  - version: number
  - updatedAt: timestamp

# 追加内容到规划文件
POST /api/v1/g3/jobs/{jobId}/planning/{type}/append
Request:
  - content: string
  - section: string  # 可选，指定追加到哪个section
Response:
  - version: number
```

---

## 五、数据库设计

### 5.1 jeecg_capabilities 表

```sql
CREATE TABLE jeecg_capabilities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    icon VARCHAR(100),
    endpoint_prefix VARCHAR(200),
    apis JSONB,
    config_template JSONB,
    code_templates JSONB,
    dependencies JSONB,
    conflicts JSONB,
    version VARCHAR(20) DEFAULT '1.0.0',
    is_active BOOLEAN DEFAULT true,
    sort_order INTEGER DEFAULT 100,
    doc_url VARCHAR(500),
    examples JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_jeecg_capabilities_category ON jeecg_capabilities(category);
CREATE INDEX idx_jeecg_capabilities_active ON jeecg_capabilities(is_active);
```

### 5.2 project_capability_configs 表

```sql
CREATE TABLE project_capability_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    capability_id UUID NOT NULL REFERENCES jeecg_capabilities(id),
    capability_code VARCHAR(50) NOT NULL,
    config_values JSONB NOT NULL,  -- 敏感字段加密存储
    status VARCHAR(20) DEFAULT 'pending',
    validation_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(project_id, capability_code)
);

CREATE INDEX idx_project_capability_configs_project ON project_capability_configs(project_id);
```

### 5.3 g3_planning_files 表

```sql
CREATE TABLE g3_planning_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES g3_jobs(id) ON DELETE CASCADE,
    file_type VARCHAR(20) NOT NULL,  -- 'task_plan', 'notes', 'context'
    content TEXT NOT NULL,
    version INTEGER DEFAULT 1,
    last_updated_by VARCHAR(50),  -- 'architect', 'coder', 'coach', 'system'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(job_id, file_type)
);

CREATE INDEX idx_g3_planning_files_job ON g3_planning_files(job_id);
```

---

## 六、实施顺序

```
阶段1 (能力清单) ──┬──→ 阶段2 (用户配置后端) ──┐
      10h         │           14h             │
                  │                           │
阶段3 (规划文件) ──┴──→ 阶段5.1 (前端能力配置) ─┼──→ 阶段4 (引擎改造)
      17h                    12h              │         29h
                                              │
                  阶段5.2-5.4 (前端G3增强) ────┘
                           23h
```

- **阶段1 + 阶段3** 可并行开发
- **阶段2** 依赖阶段1
- **阶段5.1** 依赖阶段2
- **阶段4** 依赖所有前置阶段
- **阶段5.2-5.4** 依赖阶段3和阶段4

---

## 七、工时汇总

| 阶段 | 内容 | 工时 | 优先级 |
|-----|------|------|--------|
| 阶段1 | JeecgBoot能力清单 | 10h | P0 |
| 阶段2 | 用户配置管理（后端） | 14h | P0 |
| 阶段3 | 规划文件系统 | 17h | P1 |
| 阶段4 | G3执行引擎改造 | 29h | P1 |
| 阶段5 | 前端适配开发 | 35h | P1 |
| **合计** | | **105h** | |

**预计完成时间**：14个工作日（按每天7.5小时计算）

---

## 八、风险与缓解

| 风险 | 影响 | 缓解措施 |
|-----|------|---------|
| JeecgBoot API变更 | 能力集成代码失效 | 设计抽象层，隔离具体实现 |
| 规划文件过大 | 上下文超出限制 | 实现增量更新和摘要机制 |
| 依赖分析不准确 | 生成顺序错误 | 增加人工确认环节 |
| 前端组件复杂度 | 开发时间超出 | 使用现有UI库组件 |

---

## 九、验收标准

### 9.1 功能验收

- [ ] 能力清单CRUD接口正常工作
- [ ] 用户能够为项目配置能力
- [ ] 敏感配置正确加密存储
- [ ] G3引擎按依赖顺序生成代码
- [ ] 规划文件正确更新和展示
- [ ] 前端能力选择和配置正常工作
- [ ] 任务依赖图正确展示

### 9.2 质量验收

- [ ] 单元测试覆盖率 ≥ 85%
- [ ] TypeScript编译 0 errors
- [ ] ESLint检查 0 errors
- [ ] API响应时间 P95 < 500ms

---

## 十、当前进度

### 已完成

- [x] JeecgCapabilityEntity.java 数据模型设计

### 进行中

- [ ] V032__create_jeecg_capabilities_table.sql 数据库迁移脚本

### 待开始

- [ ] JeecgCapabilityMapper.java
- [ ] JeecgCapabilityService.java
- [ ] JeecgCapabilityController.java
- [ ] V033__init_jeecg_capabilities.sql
- [ ] ... (其他任务)

---

**文档维护者**: Claude
**最后更新**: 2025-01-08
