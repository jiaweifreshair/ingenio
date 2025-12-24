# GEMINI.md - Gemini AI 开发指南

> **项目**: Ingenio (秒构AI) - AI驱动的自然语言编程平台
> **版本**: v2.0.0
> **最后更新**: 2025-12-02
> **文档用途**: Gemini AI 开发执行指南

---

## 语言偏好

**默认使用中文进行所有交流和代码注释。**

- 所有回复必须使用中文
- 代码注释使用中文
- 错误信息和日志说明使用中文
- 技术文档使用中文撰写

---

## 1. 项目概览

### 1.1 系统定位

Ingenio 当前处于“JeecgBoot × Smart Builder 融合落地”阶段：

- **控制面（Control Plane）**：JeecgBoot 负责资产（行业模板/能力/PromptPack）写入、审批、发布与运营管理。
- **产品面（Product Plane）**：Next.js 前端升级为 Smart Builder（模板长廊 + 能力积木 + Prompt 资产化）。
- **执行面（Runtime Plane）**：短期保持现有链路稳定；后续以“工程化自修复智能体”提升代码生成一次通过率（文档先行）。

> 当前有效规划文档：`01-INGENIO_SMART_BUILDER_DEVELOPMENT_PLAN.md` ~ `07-AGENT_STACK_AGENT_SCOPE_GOOSE_SELECTION.md`

### 1.2 技术栈

| 组件 | 技术 | 版本 |
|-----|------|------|
| **后端框架** | Spring Boot | 3.4.0 |
| **语言** | Java | 17+ |
| **ORM** | MyBatis-Plus | 3.5.8 |
| **数据库** | PostgreSQL | 15+ |
| **缓存** | Redis | 7+ |
| **前端** | Next.js 15 + React 19 | - |
| **状态管理** | Zustand | 4.x |

### 1.3 Gemini 的推荐职责（与你的“前端用 Gemini”一致）

- 产出 **前端体验与交互文档**：页面信息架构、组件拆分、可访问性检查清单。
- 产出 **类型与契约**：TS 类型、API 调用约定（避免前后端契约漂移）。
- 产出 **Prompt/资产描述文档**：面向控制面资产化（描述、输入输出、示例与评测用例）。

> 约束：以“文档先行”为准绳，优先写清楚 SPEC/验收/边界，再进入编码阶段。

---

## 2. 开发规范

### 2.1 代码复用优先原则

**所有代码修改前必须执行以下流程：**

1. **深度理解需求** - 明确功能边界和核心目标
2. **本地代码库搜索** - 使用搜索工具查找现有实现
3. **确认修改范围** - 列出需要修改/新增的文件
4. **决策与实施** - 优先复用现有代码

### 2.2 TypeScript严格类型检查

- 提交前检查: `pnpm tsc --noEmit` 必须 **0 errors**
- 严格模式: tsconfig.json 启用 `"strict": true`
- 禁止any: 除非有明确注释说明原因

### 2.3 ESLint代码质量规范

- 提交前检查: `pnpm lint` 必须 **0 errors**
- Error必须修复（阻塞提交）
- Warning建议修复

### 2.4 注释规范

**所有新增代码定义必须添加完整的中文注释：**

- **完整性**：描述"是什么"、"做什么"、"为什么"
- **准确性**：注释与代码同步更新
- **简洁性**：用词精准，避免冗余
- **中文化**：统一使用中文便于团队理解

---

## 3. 质量标准

### 3.1 质量门禁

**以下情况严禁提交代码（阻塞标准）：**

- 编译错误 > 0
- TypeScript错误 > 0
- ESLint Error > 0
- 单元测试失败 > 0
- 测试覆盖率 < 85%

### 3.2 关键指标

| 指标 | 目标值 | 阻塞标准 |
|-----|--------|---------|
| 单元测试覆盖率 | ≥85% | <85% |
| E2E测试通过率 | 100% | <100% |
| TypeScript错误 | 0 | >0 |
| ESLint错误 | 0 | >0 |
| API响应时间P95 | <100ms | >500ms |

---

## 4. 提交前检查清单

### 编译和类型检查
- [ ] Java：`mvn compile` 通过（0 errors）
- [ ] TypeScript：`pnpm tsc --noEmit` 通过（0 errors）

### 代码质量
- [ ] ESLint：`pnpm lint` 通过（0 errors）
- [ ] 无明文敏感信息
- [ ] 有完整中文注释

### 测试覆盖
- [ ] 单元测试覆盖率≥85%
- [ ] 所有单元测试通过
- [ ] E2E测试通过

---

## 5. Git提交规范

遵循约定式提交（Conventional Commits）：

- `feat:` 新功能
- `fix:` 修复问题
- `perf:` 性能优化
- `refactor:` 重构代码
- `docs:` 文档更新
- `test:` 测试相关

**示例**：
```bash
feat: 实现用户认证模块
fix: 修复登录页面样式问题
docs: 更新API文档
```

---

## 6. 项目结构

```
Ingenio/
├── backend/                 # Spring Boot后端
│   ├── src/main/java/      # Java源代码
│   └── src/test/           # 测试代码
├── frontend/               # Next.js前端
│   ├── src/app/           # 页面路由
│   ├── src/components/    # React组件
│   ├── src/lib/           # 工具库和API客户端
│   └── src/hooks/         # React Hooks
└── docs/                   # 项目文档
```

---

## 附：文档编号索引

- `docs/00-INDEX.md`
- `frontend/docs/00-INDEX.md`
- `backend/docs/00-INDEX.md`
