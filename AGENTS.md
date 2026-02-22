# Repository Guidelines

## 当前有效规划（先读文档再动代码）
- 根目录规划文档：`01-INGENIO_SMART_BUILDER_DEVELOPMENT_PLAN.md` ~ `07-AGENT_STACK_AGENT_SCOPE_GOOSE_SELECTION.md`（“胶水层”方案已合并进 `03-PRODUCT_UPGRADE_PLAN_SMART_BUILDER.md`）
- 编号索引：
  - `docs/00-INDEX.md`
  - `backend/docs/00-INDEX.md`
  - `frontend/docs/00-INDEX.md`

## Project Structure & Module Organization
- `backend/`：pring Boot（Java）：
  - **Spring Boot（Java）**：`backend/pom.xml` + `backend/src/main/java`（当前默认运行入口，端口 8080，见 `start-services.sh`）。open-lovable-cn在3001端口启动。
- `frontend/`：Next.js 15 App Router，核心代码位于 `src/app`、`src/components`、`src/lib`，E2E/单测位于 `src/e2e` 与 `src/components/**/__tests__`。
- `shared/`：前后端共享类型与协议（例如 `shared/types/plan-routing.types.ts`）。
- `scripts/`：一键运维脚本，如 `start-services.sh`、`stop-services.sh`、`scripts/v2-validation.ts`。

## Build, Test, and Development Commands
- `./start-services.sh` / `./stop-services.sh`：在本机拉起或关闭 PostgreSQL、Redis、MinIO 与 Spring Boot（需 Docker Desktop 运行）。
- `cd backend && mvn test`：Spring Boot 单测（修改 Java 代码时必跑）。
- `cd backend && pnpm build`：使用 Nest CLI 构建后端；`pnpm test`、`pnpm lint` 分别执行 Jest 与 ESLint。
- `cd frontend && pnpm dev`：Next.js 开发模式；`pnpm build` 生成生产包；`pnpm test:unit` / `pnpm e2e:chromium` 运行 Vitest 与 Playwright。
- `scripts/v2-validation.ts`：一键触发 V2 流程校验（包含 API、前端组件与 Maven 编译）。

## Coding Style & Naming Conventions
- TypeScript/JavaScript 采用 2 空格缩进、ESM、`"strict": true`，新增类型禁止不必要的 `any`。
- 统一运行 `pnpm lint`、`pnpm format`（Prettier + Tailwind 插件）以及 Nest/Next 自带 ESLint 规约。
- Java 区域遵循 Google Style，PEP 8 对应 Python 脚本（如工具链）。
- 注释、提交信息与新文档默认使用中文；新增定义需写明用途与理由。

## AI 编码思维框架 (Extended Thinking Protocol)

### 思考过程（六步法）
1. **Understand（理解）**：复述问题，识别已知/未知，标注歧义点
2. **Hypothesize（假设）**：在确定方案前考虑多种解释，避免过早锁定
3. **Analyze（分析）**：表面观察 → 模式识别 → 质疑假设 → 深层洞察
4. **Verify（验证）**：对照证据检验，检查逻辑，考虑边界情况和反例
5. **Correct（纠正）**：发现错误时："等等，这里有问题因为..." → 整合修正
6. **Synthesize（综合）**：连接各部分，识别原则，得出有依据的结论

**自然过渡短语**："嗯..."、"实际上..."、"等等..."、"这与...相关"、"深入看..."

### 思维陷阱避免
| 陷阱 | 应对策略 |
|-----|---------|
| **确认偏误** | 主动寻找反对初始假设的证据 |
| **过度自信** | 不确定时明确说"我不确定"；不要编造 |
| **范围蔓延** | 专注于被要求的内容，不要过度工程化 |
| **假设盲区** | 明确陈述并质疑你的假设 |
| **首选方案固化** | 始终考虑至少一种替代方案 |

### 输出前检查清单
- [ ] 直接回答了所问的问题？
- [ ] 假设已陈述并有理由支持？
- [ ] 边界情况已考虑？
- [ ] 没有编造的事实或代码？
- [ ] 详细程度适当（不过度/不过简）？

### 代码输出标准
- **依赖优先**：实现前先分析导入、文件关系
- **匹配现有风格**：遵循代码库约定（命名、格式、模式）
- **错误处理**：处理可能的失败，不要静默吞掉异常
- **不过度工程化**：解决实际问题，避免过早抽象
- **安全意识**：验证输入，避免注入漏洞，不硬编码密钥
- **可测试性**：编写可验证的代码；在实现中考虑边界情况

### 何时使用深度思考
- **必须使用**：代码任务、架构设计、调试、多步骤问题、数学、复杂解释
- **可以跳过**：问候、简单事实查询、是/否问题

## Testing Guidelines
- 前端：Vitest + Testing Library，文件命名 `*.test.tsx`；E2E 使用 Playwright，关键脚本 `src/e2e/v2-complete-pipeline.spec.ts`。
- 后端：Jest 覆盖服务逻辑，运行 `pnpm test`；Java 侧使用 Maven `mvn test`（若修改 Spring 代码）。
- 目标覆盖率 ≥85%，提交前至少执行一次 `pnpm test`（对应修改域）与关键 E2E。

## 智能体工程化（文档先行）
- 目标：提升“代码生成一次性通过率”，以工程闭环实现自修复（Generate → Build/Test → Fix → Re-test）。
- 选型口径：`AgentScope（Python）+ Goose（代码修改执行器）`，详见 `07-AGENT_STACK_AGENT_SCOPE_GOOSE_SELECTION.md`（Goose：`https://github.com/block/goose`）。
- 说明：仓库已移除历史 Python Agent 目录（`workers/`、`agent-service/`）；后续如需引入 Agent 执行器，建议以独立工具/服务形态落地，并由 JeecgBoot 控制面治理资产（Prompt/评测集/模板）。

## Commit & Pull Request Guidelines
- 遵循 Conventional Commits，例如 `feat: 新增PlanRouting接口`、`fix: 修复design确认流程`。
- PR 需说明变更范围、关联 Issue/需求，以及必要的截图或日志（UI、API变化时尤其重要）。
- 提交前确保 `pnpm tsc --noEmit`、`pnpm lint`、`pnpm test` 与必要的 `scripts/v2-validation.ts` 全部通过，并附带验证步骤说明。

## Security & Configuration Tips
- 不要提交 `.env`、凭证或真实 SMTP 信息；所有敏感配置均读取环境变量。
- Docker 依赖需使用 `env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY` 方式运行脚本，以免代理干扰镜像拉取。
