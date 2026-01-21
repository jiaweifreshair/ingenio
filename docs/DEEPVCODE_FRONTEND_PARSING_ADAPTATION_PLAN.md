# DeepVCode 前端解析逻辑借鉴与 Ingenio 向导 Chat 透传方案

## 目标（对齐你的诉求）
1. **结果可展示**：分析阶段/原型阶段/执行阶段的“阶段化输出 + 终端日志”可稳定展示。
2. **上下文可透传**：用户在原型确认前的所有 Chat 修改，都能影响后续 G3 生成（服务端代码生成）。
3. **可持续对话修改**：在“分析完成→原型确认”期间，用户可通过 Chat 持续追加/修正约束，不需要反复重开流程。
4. **工程闭环**：变更必须可回放（可追溯）、可测试（单测/E2E可覆盖关键链路）。

---

## DeepVCode 借鉴点（与“代码优化/前端解析逻辑”最相关）
> 参考路径：`/Users/apus/Documents/UGit/DeepVCode-main`

### 1) 统一的“事件协议”与强类型消息分发
- DeepVCode 在 VSCode WebView 中通过 `MultiSessionMessageService` 定义了**明确的消息类型枚举**，并集中处理 `window.addEventListener('message', ...)` 的分发。
- 为避免“UI 还没 ready 消息就来了”，它做了 **queue + ready handshake**（先缓存，ready 后 flush）。
- 对应代码：
  - `DeepVCode-main/packages/vscode-ui-plugin/webview/src/services/multiSessionMessageService.ts`
  - `DeepVCode-main/packages/vscode-ui-plugin/webview/src/index.tsx`

### 2) 解析逻辑的关键：把“可解析结构”嵌入到流式内容中
- DeepVCode 对部分能力（例如 subagent 进度）采用了“**在 tool result 中塞 JSON 事件**，前端再识别并转成 UI 消息”的策略。
- 对应代码（通过 `resultStr.includes('"type":"subagent_update"')` 识别）：
  - `DeepVCode-main/packages/vscode-ui-plugin/webview/src/components/MultiSessionApp.tsx`

### 3) “命令/功能结果”采用全局广播，避免组件挂载时序问题
- 以 `/refine` 为例：
  - WebView 启动时就注册 `refine_result/refine_error` 监听；
  - 收到后向 `window.dispatchEvent(new CustomEvent(...))` 广播；
  - 业务 Hook `useRefineCommand` 只监听浏览器事件即可。
- 对应代码：
  - `DeepVCode-main/packages/vscode-ui-plugin/webview/src/hooks/useRefineCommand.ts`
  - `DeepVCode-main/packages/vscode-ui-plugin/webview/src/index.tsx`

---

## Ingenio 落地方案（面向 /lab/g3 + SmartWizard）

### A. “上下文透传”的最小可落地协议（已开始落地）
核心原则：**把“用户持续修改”写回 AppSpec.specContent.userRequirement**，然后在 G3 提交任务时传 `appSpecId`。

这样后端可以统一在 `G3OrchestratorService.resolveJobContext(...)` 中读取最新需求与 Blueprint 上下文，避免“前端拼接字符串但后端拿不到”的断层。

### B. 向导阶段的 Chat 修改策略（原型确认前）
1. **分析阶段（PlanDisplay）**
   - 用户输入的“修改内容”视为增量 delta；
   - 前端将 delta 追加到 `userRequirement`（带时间戳段落），并重启分析 SSE；
   - 同时调用 `/v2/plan-routing/route` 并带上 `appSpecId`，复用同一个 AppSpec，避免创建新记录导致上下文丢失。
2. **原型确认阶段（InteractionPanel）**
   - 用户输入的 Chat 同时用于：
     - 迭代修改原型（OpenLovable 的 `sendIterationMessage`）
     - 同步到 AppSpec（调用 `update-requirement`），保证后续 G3 生成拿到最新需求。

### C. “输出可展示”的下一步（与 DeepVCode 对齐）
为了达到 Manus/GenSpark 的观感与可解释性，建议把后端输出统一升级为结构化事件流（而不是仅靠文本日志猜测阶段）：
1. **事件 Schema（建议）**
   - `phase_update`：阶段开始/完成/失败
   - `tool_call` / `tool_result`：工具调用与结果摘要（脱敏）
   - `compile_result`：编译结果结构化（模块/错误列表/建议）
   - `context_snapshot`：关键上下文快照（蓝图/约束/变更摘要）
2. **前端解析**
   - 按事件类型渲染对应 UI（阶段条、终端、diff、错误列表）
   - 对无法结构化的内容，使用“文本日志兜底”

---

## 当前已落地的代码改动（本次迭代）
- 分析阶段支持“方案修改”并复用 AppSpec：`frontend/src/components/home/smart-wizard.tsx`
- 分析阶段引入 InteractionPanel，支持历史记录透传与 `<think>` 思考段折叠展示：`frontend/src/components/prototype/interaction-panel.tsx`
- 允许原型阶段 Chat 修改同步到 AppSpec：`frontend/src/components/prototype/prototype-confirmation.tsx`
- G3 提交任务支持携带 `appSpecId`：`frontend/src/components/g3/g3-console.tsx` + `frontend/src/lib/api/g3.ts`
- /lab 增强：RAG 索引/检索 + Toolset 搜索/读文件/命令执行 + jobId 透传：`frontend/src/app/lab/page.tsx`
- SSE Analyze API 增加空请求体保护：`frontend/src/app/api/v1/generate/analyze-stream/route.ts`
- 后端支持：
  - `/v2/plan-routing/route` 接收 `appSpecId` 并更新同一个 AppSpec
  - `/v2/plan-routing/{appSpecId}/update-requirement` 仅更新需求文本（不影响原型状态）
  - 见：`backend/src/main/java/com/ingenio/backend/controller/PlanRoutingController.java`

---

## 下一步建议（按优先级）
1. **统一事件协议（G3Event/SSE）**：减少前端“字符串猜测阶段”，提升可视化可信度。
2. **RAG（Context）增强**：引入向量检索 + Code Graph（至少先把 repo 索引与检索结果结构化输出）。
3. **终端/日志能力**：在服务端引入受控 Shell/LogReader（白名单 + 沙箱路径限制），并把执行结果结构化返回给前端/Agent。
