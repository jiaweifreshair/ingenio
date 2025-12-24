# INGENIO项目技术指南

> **项目**: Ingenio (秒构AI) - AI驱动的自然语言编程平台
> **版本**: v2.0.0 (意图识别+双重选择机制)
> **最后更新**: 2025-12-02
> **文档用途**: Claude Code开发执行指南

---

## 0. 当前有效口径（先读文档再改代码）

- 规划文档：`01-INGENIO_SMART_BUILDER_DEVELOPMENT_PLAN.md` ~ `07-AGENT_STACK_AGENT_SCOPE_GOOSE_SELECTION.md`
- 文档编号索引：
  - `docs/00-INDEX.md`
  - `backend/docs/00-INDEX.md`
  - `frontend/docs/00-INDEX.md`

## 1. 项目概览

### 1.1 系统定位

Ingenio是基于**意图识别+双重选择机制**的AI应用全栈生成系统，通过**意图分析 → 模板匹配 → 用户选择 → 原型预览 → 后端生成 → 自动化验证**的完整链路，实现从需求到可部署应用的端到端自动化。

### 1.2 核心价值

- **AI意图识别**：自动识别克隆/设计/混合意图（94.3%准确率）
- **行业模板库**：40+行业模板快速启动（电商/教育/社交/生活服务）
- **双重选择机制**：模板选择（可选）+ 风格选择（必选）
- **可交互原型**：5-10秒生成，用户确认后再生成后端
- **智能路由**：自动选择最优生成路径

### 1.3 关键指标

| 指标 | 目标值 | 阻塞标准 |
|-----|--------|---------|
| 单元测试覆盖率 | ≥85% | <85% |
| E2E测试通过率 | 100% | <100% |
| TypeScript错误 | 0 | >0 |
| ESLint错误 | 0 | >0 |
| API响应时间P95 | <100ms | >500ms |

---

## 2. 技术栈

### 2.1 后端技术

| 组件 | 技术 | 版本 |
|-----|------|------|
| 框架 | Spring Boot | 3.4.0 |
| 语言 | Java | 17+ |
| ORM | MyBatis-Plus | 3.5.8 |
| 数据库 | PostgreSQL | 15+ |
| 缓存 | Redis | 7+ |
| AI集成 | 七牛云AI（DeepSeek） | - |

### 2.2 前端技术

| 组件 | 技术 | 版本 |
|-----|------|------|
| 框架 | Next.js | 15 |
| UI库 | React | 19 |
| 状态管理 | Zustand | 4.x |
| 样式 | TailwindCSS | 3.x |

### 2.3 AI模型栈

结合当前“JeecgBoot × Smart Builder”方向，模型栈建议按职责拆分：

- **Gemini（前端/产品面）**：交互与组件方案、前端类型与契约、产品文档与说明。
- **Claude（服务端/工程闭环）**：服务端代码生成与修复、测试修复、工程化自修复闭环。
- **OpenLovable-CN（可选）**：原型预览生成（保持现状，后续可演进/替换）。

---

## 3. 基础设施规范 ⚠️ **强制遵守**

### 3.0 数据库连接规范 🚫 **禁止本地数据库**

**强制要求**：
- 🚫 **禁止启动本地PostgreSQL服务**（如 `brew services start postgresql`）
- 🚫 **禁止连接本地安装的数据库**（非Docker容器内的数据库）
- ✅ **必须使用Docker容器中的数据库服务**

**原因**：
1. 本地数据库与Docker数据库端口冲突（都使用5432）
2. 本地数据库schema可能不完整，导致难以排查的bug
3. 团队环境一致性要求

**正确做法**：
```bash
# 启动Docker服务
docker-compose up -d postgres redis minio

# 验证Docker数据库运行
docker ps | grep postgres
```

**错误做法**：
```bash
# 禁止操作
brew services start postgresql@15
pg_ctl start
```

**端口冲突排查**：
如果遇到数据库连接问题，先检查是否有本地PostgreSQL占用端口：
```bash
lsof -i :5432
# 如果看到非Docker进程，需要停止：
brew services stop postgresql@15
```

---

### 3.1 核心配置

**后端配置**：
- context-path: `/api`（在application-local.yml中配置）
- 服务端口: 8080
- ⚠️ **启动前必须加载环境变量**：`source backend/.env` 或使用 `scripts/start-backend.sh`

**前端环境变量**：
- `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api`（包含 `/api` 前缀）

**后端启动命令**：
```bash
# 方式1：使用启动脚本（推荐）
./scripts/start-backend.sh

# 方式2：手动加载环境变量
cd backend && source .env && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 3.2 API路径规则

| 配置项 | 正确值 | 说明 |
|-------|-------|------|
| 环境变量 API_BASE_URL | `http://localhost:8080/api` | 包含 context-path |
| 前端端点格式 | `/v1/auth/login` | 不带 `/api` 前缀 |
| 最终请求URL | `http://localhost:8080/api/v1/auth/login` | 完整路径 |

### 3.3 两种API调用模式

**模式1：使用 client.ts 统一封装（推荐）**
- client.ts 已配置 API_BASE_URL（包含 `/api`）
- 调用时端点使用 `/v1/xxx` 格式
- 示例：`post('/v1/auth/login', data)`

**模式2：直接使用 fetch**
- 必须使用完整的 API_BASE_URL
- 端点同样使用 `/v1/xxx` 格式
- 示例：`fetch(\`\${API_BASE_URL}/v1/projects\`)`

### 3.4 常见错误及修复

| 错误现象 | 原因 | 修复方法 |
|---------|------|---------|
| 请求到 `/api/api/v1/xxx` | 端点重复添加了 `/api` | 端点改为 `/v1/xxx` |
| 请求到 `/v1/xxx`（缺少 `/api`） | API_BASE_URL 未包含 `/api` | 确保 API_BASE_URL 包含 `/api` |
| 404 Not Found | 路径不正确 | 检查后端Controller映射 |

### 3.5 Next.js API Routes

前端存在一些 API Routes（`/app/api/v1/...`），这些是前端自己的代理层：
- 调用时使用相对路径 `/api/v1/...`
- 不要与直接调用后端的路径混淆
- 主要用于 SSR 场景或特殊处理

---

## 4. 开发规范

### 4.1 代码复用优先原则

**所有代码修改前必须执行四步流程**：

1. **深度理解需求**：明确功能边界、确认修改范围、提取搜索关键词
2. **本地代码库搜索**：至少使用2种搜索工具（Glob/Grep/Read）
3. **确认修改范围**：列出修改清单、新增清单、复用清单
4. **决策与实施**：找到相似功能优先复用，未找到则谨慎新建

### 4.2 TypeScript严格类型检查

**零容忍策略**：
- 阻塞标准：`pnpm tsc --noEmit` 必须 0 errors
- 严格模式：tsconfig.json 启用 `"strict": true`
- 禁止any：除非有明确注释说明原因

### 4.3 ESLint代码质量规范

**零容忍策略**：
- 阻塞标准：`pnpm lint` 必须 0 errors
- Error必须修复（阻塞提交）
- Warning建议修复（不阻塞但应尽量修复）

### 4.4 注释规范

所有新增代码定义必须添加完整的中文注释，包含：
- 完整性：描述"是什么"、"做什么"、"为什么"
- 准确性：注释与代码同步更新
- 简洁性：用词精准，避免冗余

---

## 5. 智能体工程化（文档先行）

> 目标：提升“代码生成一次性通过率”，以工程闭环实现自修复（Generate → Build/Test → Fix → Re-test）。

### 5.1 技术选型口径（你已确认的方向）

- 多智能体编排：`AgentScope（Python）`
- 代码修改执行器：`Goose`（`https://github.com/block/goose`，以 PoC 指标验证可用性）
- 模型职责拆分：前端/产品面可用 Gemini；服务端代码生成/修复以 Claude 为主

详见：`07-AGENT_STACK_AGENT_SCOPE_GOOSE_SELECTION.md`

### 5.2 与 JeecgBoot 的关系

- JeecgBoot 作为控制面：治理模板/能力/PromptPack/评测集，负责写入/审批/发布。
- 工程化智能体建议以“独立执行器/CI 工具”形态落地：只读消费控制面已发布资产，产出 patch/PR 与报告。

### 5.3 现有 V2 链路（历史实现，供对齐现状用）

**Plan Agent（规划层）**：
- 意图识别：使用IntentClassifier分析用户需求类型
- 智能路由：根据意图选择正确工具链
- 快速原型：生成可交互的前端原型
- 用户确认：等待用户确认设计后才进入Execute阶段

**Execute Agent（执行层）**：
- 前置检查：验证用户已确认设计
- 数据库设计：生成PostgreSQL Schema
- 后端生成：生成Spring Boot代码
- 多端生成：生成所有平台代码

**Validate Agent（校验层）**：
- 编译验证：5个平台并行编译
- 运行时验证：应用启动、UI渲染、API调用
- 性能验证：启动时间、渲染时间、响应时间

### 5.4 关键开发规则（仍然有效）

- Plan阶段必须先进行意图识别，等待用户确认设计
- Execute阶段必须通过ExecuteGuard检查前置条件
- Validate阶段禁止生成代码，仅验证Execute阶段的输出

---

## 6. 质量标准

### 6.1 质量门禁（阻塞提交）

- 编译错误 > 0
- TypeScript错误 > 0
- ESLint Error > 0
- 单元测试失败 > 0
- 测试覆盖率 < 85%
- 代码包含明文敏感信息

### 6.2 测试覆盖率要求

| 层级 | 覆盖率要求 | 测试类型 |
|-----|----------|---------|
| Controller | ≥90% | E2E测试 |
| Service | ≥85% | 单元测试 + 集成测试 |
| Agent | ≥85% | 单元测试 |

### 6.3 零Mock策略

正式代码开发阶段严禁使用Mock数据：
- 数据库使用TestContainers启动真实PostgreSQL
- Redis使用TestContainers启动真实Redis
- AI API集成真实API（测试环境使用限流）

### 6.4 提交前检查清单

- Java：`mvn compile` 通过（0 errors）
- TypeScript：`pnpm tsc --noEmit` 通过（0 errors）
- ESLint：`pnpm lint` 通过（0 errors）
- 单元测试覆盖率≥85%
- 所有测试通过

---

## 7. Git工作流

### 7.1 分支策略

| 分支 | 用途 | 生命周期 |
|-----|------|---------|
| main | 生产环境代码 | 永久 |
| develop | 开发主分支 | 永久 |
| feature/* | 新功能开发 | 临时 |
| fix/* | Bug修复 | 临时 |

### 7.2 Conventional Commits规范

- `feat:` 新功能
- `fix:` 修复问题
- `perf:` 性能优化
- `refactor:` 重构代码
- `docs:` 文档更新
- `test:` 测试相关

---

## 8. 项目结构

### 8.1 后端结构

- `backend/src/main/java/com/ingenio/backend/` - 核心代码
  - `controller/` - REST API控制器
  - `service/` - 业务逻辑层
  - `entity/` - 数据实体
  - `mapper/` - MyBatis映射
  - `agent/` - AI Agent实现
  - `ai/` - AI提供商封装

### 8.2 前端结构

- `frontend/src/` - 前端源码
  - `app/` - Next.js App Router页面
  - `components/` - React组件
  - `lib/api/` - API客户端封装
  - `hooks/` - 自定义Hooks
  - `types/` - TypeScript类型定义

---

## 9. 相关项目

- **SuperDesign**：`/Users/apus/Documents/UGit/superdesign` - AI驱动的UI设计多方案生成
- **Open-Lovable-CN**：`/Users/apus/Documents/UGit/open-lovable-cn` - 参考项目

---

**Made with by Justin**

> 本文档是Claude Code开发执行指南（精简版），专注于核心开发规范和API调用规则。
