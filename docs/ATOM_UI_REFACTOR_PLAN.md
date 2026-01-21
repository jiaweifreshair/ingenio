# Atoms 风格 UI & 链路整改方案（G3/Agent-OS）

> 目标：将现有 “G3 Console” 从“日志观看器”升级为“玻璃盒（可控、可解释、可回放）”的 Agent IDE 体验，逐步支持 MetaGPT OS 接入。

---

## 1. 现状资产复用（你仓库已经具备的基础）

### 1.1 后端（Java G3）
- `backend/src/main/java/com/ingenio/backend/module/g3/controller/G3Controller.java`：任务、日志（SSE）、产物、契约、规划文件完整 API。
- `backend/src/main/java/com/ingenio/backend/service/g3/G3OrchestratorService.java`：编排循环 + `Sinks.Many` 日志推送机制。

### 1.2 前端（Console 雏形）
- `frontend/src/components/g3/g3-console.tsx`：任务提交、SSE 订阅、结果拉取、对话框展示。
- `frontend/src/components/g3/g3-console-view.tsx`：左右栏（Agent Card + LogStream）布局、阶段推断、进度条。
- `frontend/src/components/g3/file-tree.tsx`：IDE 风格文件树（适合右侧 Preview）。

### 1.3 运行资产（Kernel）
- `backend/g3_context/templates/JavaController.txt`、`backend/g3_context/templates/JavaService.txt`
- `backend/g3_context/prompts/SYSTEM_PROMPT_*.md`
- `backend/g3_context/capabilities.md`

---

## 2. 目标交互（Atoms 三栏）

### 2.1 左栏：指挥控制台（Command Center）
- Chat 输入框（当前 requirement 输入可复用）。
- Agent Selector（头像/角色按钮）：`ARCHITECT / PLAYER / COACH / (PM/QA/FRONTEND/BACKEND 作为后续扩展)`
- Direct Order（定向指令）：选择角色后发送命令，只路由给目标角色。
- 关键状态：当前项目/任务状态、轮次、成本（可选）、运行开关。

### 2.2 中栏：Agent Workspace（Glassbox Trace）
- Live Trace：显示“结构化事件流”，而不是输出大段思考。
- Handoff：当一个角色产出 artifacts 或阶段完成，展示“文件传递动画”（事件驱动）。
- Debug 面板：工具调用、模板引用、Guard 失败原因（可折叠）。

### 2.3 右栏：全栈预览（Preview）
- File Tree（已具备）：按 artifacts 构建树。
- Code View：选中文件显示内容（含 diff 与错误标记）。
- Schema View（后续）：根据 `dbSchemaSql` 渲染 ER（可从已有 Mermaid/ERD 工具接入）。
- UI Preview（后续）：对接 OpenLovable 或本地组件预览，形成“所见即所得”。

---

## 3. 链路整改：从“日志”升级为“事件”（逐步兼容）

### 3.1 事件模型（建议新增 shared types）
定义 `AgentEvent`（JSON），最小集合：
- `think_summary`：计划摘要（短句 + 输入引用）
- `action_started/action_finished`：actionName、耗时、token/cost（可选）
- `tool_call/tool_result`：读模板/写文件/运行校验
- `artifact_written`：path、hash、diff
- `handoff`：fromRole -> toRole + artifactRefs
- `guard_failed/guard_fixed`
- `error`

### 3.2 兼容策略
Phase 1：`log` 事件（复用现有 `G3LogEntry`） + artifacts/contract 拉取（当前已支持）。
Phase 2：在后端编排点增加结构化事件（不破坏现有 SSE），前端中栏切换为渲染事件流。

---

## 4. UI 组件整改建议（基于你现有组件最小重构）

### 4.1 页面与路由
- 新增/替换入口页：`/g3` 或 `/agent-os`（避免只放在 /lab）
- 保留 `/lab/g3` 用于开发验证，但业务主链路应跳转到新入口。

### 4.2 组件拆分（建议）
- `G3CommandCenter`：左栏，负责 requirement + agent selector + direct order。
- `G3TracePanel`：中栏，消费 WS 事件流。
- `G3PreviewPanel`：右栏，FileTree + CodeViewer + Contract/Schema tabs。

### 4.4 已落地（当前仓库状态）
- 三栏容器：`frontend/src/components/g3/g3-atoms-console-view.tsx:1`
- 右侧预览：`frontend/src/components/g3/g3-preview-panel.tsx:1`
- G3Console 接入：`frontend/src/components/g3/g3-console.tsx:1`（SSE + WS 去重聚合 + 运行中轮询刷新产物）

### 4.3 数据流（React）
- 统一在 `g3-console.tsx` 内维护：
  - `jobId/projectId`
  - `logs/events`
  - `artifacts/contract`
  - `selectedArtifact`
- 左栏触发 `submit/command`；中栏订阅 WS；右栏只读 artifacts。

---

## 5. Phase 实施路线（与你的 A/B 顺序对齐）

### Phase A（已开始）：Kernel 资产 API + 前端动态加载
- 后端提供 `/v1/g3/kernel/*`
- 前端 `G3ContextLoader` 不再硬编码 prompts/templates/capabilities

### Phase B：WebSocket 事件通道骨架
- 后端提供 `/ws/g3?jobId=<uuid>`（注意：本项目后端 `context-path=/api`，实际访问路径为 `/api/ws/g3?jobId=<uuid>`）
- 先广播 `log`，前端可新增可选的 WS 订阅用于实时 Trace

### Phase C：UI 三栏落地（Atoms MVP）
- 右栏接入 `FileTree` + `getG3ArtifactContent` 实时显示代码
- 中栏支持 Handoff/Guard 展示（先用现有日志关键字推断）
- 左栏 Agent Selector 与 Direct Order（先做 UI，后端指令路由在 Agent-OS 阶段接入）

### Phase D：MetaGPT OS 接入
- 新增 `/v1/agent-os/*`，复用同一 UI 与事件模型
- g3-engine(Python) 作为 runtime，Java 为 control-plane/gateway

---

## 6. 常见问题：本机代理导致 localhost 访问失败

若你在浏览器打开 `http://localhost:3000/lab/g3` 出现“连接失败/代理连接失败”，通常是系统或浏览器代理把 `localhost` 也走了代理（例如 `127.0.0.1:7897`），但代理未运行或未配置绕过。

- 建议在代理软件里将 `localhost,127.0.0.1,::1` 加入绕过列表（bypass/no_proxy）。
- 脚本侧已做兜底：`scripts/start-all.sh:1`、`scripts/start-frontend.sh:1` 会清理 `HTTP_PROXY/HTTPS_PROXY/ALL_PROXY` 并设置 `NO_PROXY`，避免本机服务被误代理。
