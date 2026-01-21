# G3 引擎服务端增强方案（RAG 优先 + Terminal/Shell 执行能力）

> **目标读者**：后端（Spring Boot）与前端（Next.js/Playwright）开发同学  
> **适用版本**：Ingenio G3 v2.2+（已具备 M1-M5 + 初版 M6（产物级 RAG）雏形）  
> **优先级**：先 RAG（上下文能力）→ 再 Terminal/Shell（自主探索与日志分析）  

---

## 0. 背景与结论

现有 G3 的 M1-M5 主要提升了 **会话内稳定性、上下文压缩、自修复闭环**。但要达到 Manus / GenSpark / ClaudeCode 这类“代理式生成”的能力，需要补齐两块“硬能力底座”：

1. **RAG（全仓库语义上下文）**：让 Agent 能在百万行级存量代码里精准找“需要的那几段”。  
2. **Terminal/Shell 交互（受控执行 + 日志读取 + 探索）**：让 Agent 像人一样 `ls/rg/cat/curl` 自主定位问题，而不是只看 Maven 报错。

本方案以“可落地、可验收、可逐步上线”为第一原则：先做 **Repo 级 RAG + Tools API（读/查/跑）**，再逐步升级为 **Code Graph + 更强的执行工具链**。

---

## 1. 现状盘点（与接入点）

### 1.1 已有能力（仓库中已存在/可复用）

- **M1：SessionMemory Redis 持久化**：`backend/src/main/java/com/ingenio/backend/service/g3/G3MemoryPersistenceService.java`
- **M2：context.md 智能压缩**：`backend/src/main/java/com/ingenio/backend/service/g3/G3ContextBuilder.java`（通过 `buildCompactContext(..., tokenBudget)`）
- **M3：分阶段验证**：`backend/src/main/java/com/ingenio/backend/service/g3/G3PhaseValidator.java`
- **M4：静态分析雏形**：`backend/src/main/java/com/ingenio/backend/service/g3/G3StaticAnalyzer.java`（当前需接入 PhaseValidator）
- **M5：动态 maxRounds**：`backend/src/main/java/com/ingenio/backend/service/g3/G3OrchestratorService.java`（动态延长 Max + 3）
- **M6（雏形）：产物级 RAG**：`backend/src/main/java/com/ingenio/backend/service/g3/G3KnowledgeStore.java`（对“已生成产物”切片入库）
- **沙箱执行入口**：`backend/src/main/java/com/ingenio/backend/service/g3/G3SandboxService.java`（通过 OpenLovable `/api/sandbox/execute` 执行命令）
- **日志流**：`backend/src/main/java/com/ingenio/backend/module/g3/controller/G3Controller.java`（SSE）

### 1.2 明确缺口（与本方案要补齐的点）

- RAG 仅覆盖“本次生成产物”，未覆盖 **仓库存量代码**、文档、配置、历史修复经验。
- 现有“执行”聚焦 **编译/验证**，缺少：
  - `read_file`（读任意 workspace 文件/沙箱文件）
  - `search_codebase`（全文/语义检索）
  - `run_shell_command`（受控命令执行）
  - 日志探索（`tail`、`grep`、读取测试/运行日志）

---

## 2. 总体目标（可验收定义）

### 2.1 RAG（Repo 级上下文）验收标准

- 能对 **指定项目/仓库** 建立向量索引（代码 + 配置 + 关键文档），并支持按租户/项目隔离。
- Coach/Coder 在生成/修复时，能通过检索拿到“与当前错误/需求最相关的片段”，且能在 Token 预算内稳定注入 Prompt。
- 在大仓库下，检索结果可控：有 **去重、裁剪、排序**，并能解释来源（filePath、commit、chunk 摘要）。

### 2.2 Terminal/Shell（受控执行与日志读取）验收标准

- Agent 能执行 **只读/诊断类**命令（`ls`, `cat`, `rg`, `tail`, `mvn -q -DskipTests test` 等需分级开关）并获取输出。
- 输出具备：
  - 统一截断（长度/行数）
  - 敏感信息脱敏（token/密码/密钥）
  - 审计记录（谁、何时、对哪个 job、执行了什么、返回了什么摘要）
- Playwright E2E 能覆盖“工具能力可用 + 安全拒绝策略生效”。

---

## 3. 目标架构（服务端）

### 3.1 统一知识层：分两类索引（避免“全都塞进 context.md”）

| 索引类型 | 作用 | 生命周期 | 典型检索场景 |
|---|---|---|---|
| **Job Index（会话/任务）** | 当前生成产物、修复增量 | 随 job 结束可过期 | “刚生成的 Service/Mapper 里谁引用了它？” |
| **Repo Index（项目/仓库）** | 存量代码、配置、文档 | 跨会话长期 | “项目里鉴权在哪？”“哪个模块有类似实现？” |

建议保留现有 `G3KnowledgeStore`，但补齐“Repo Index 的 ingest 与隔离”，并将检索结果在 Prompt 注入时做 **统一裁剪**。

### 3.2 统一工具层：G3 Toolset（受控执行 + 文件/搜索能力）

建议新增“工具层”作为 Orchestrator/Coach 的依赖，而不是在 Agent 内直接拼命令：

- `search_codebase`：优先语义检索（Repo Index），必要时全文检索（keyword fallback）
- `read_file`：带路径白名单/工作区根目录约束
- `run_shell_command`：严格 allowlist（按“只读/诊断/可变更”分级开关）
- `read_logs`：标准化读取验证日志、应用日志（避免把“日志在哪”推给 Agent 猜）

并将工具调用结果记录为 `G3LogEntry`（可被前端 Console/WS/SSE 实时展示）。

---

## 4. 数据模型与隔离策略（关键）

### 4.1 VectorStore Document metadata 建议（最小集合）

所有切片文档统一附带 metadata（用于隔离/过滤/解释）：

- `tenantId`：租户隔离
- `projectId`：项目隔离（建议与 AppSpec/Project 表对齐）
- `scope`：`job` / `repo` / `docs`
- `jobId`（可选）：scope=job 时必填
- `filePath`：仓库相对路径
- `language`：`java/xml/yml/ts/tsx/md/...`
- `contentHash`：用于增量更新/去重
- `chunkId`：同文件切片序号
- `source`：`artifact` / `repo_scan` / `manual_upload`
- `ref`：`gitCommit` 或 `workspaceSnapshotId`（便于追溯与重建）

### 4.2 Repo Index 的“增量更新”策略

强烈建议按“可落地优先级”分两段上线：

1. **MVP**：全量扫描（可手动触发），写入 `ref=commit`；重复写入依赖 `contentHash` 去重。  
2. **增强**：基于 `git diff` 或文件 mtime 的增量 ingest；并提供“按目录重建”。

---

## 5. Agent Prompt 注入策略（避免 token 爆炸）

建议把上下文拆为固定 4 段，并分配预算（示例）：

1. **Blueprint/约束**（硬约束，必带）  
2. **context.md 压缩摘要（M2）**：≤ 4k tokens  
3. **RAG 结果（Repo + Job 混合）**：≤ 2k tokens（去重、同文件最多 2 段）  
4. **错误/日志（验证输出）**：≤ 2k tokens（仅保留关键片段）  

并提供统一的 `PromptBudgeter`（不要在各 Agent 里散落截断逻辑）。

---

## 6. Terminal/Shell 执行能力设计（安全第一）

### 6.1 命令分级（默认只开 L0）

- **L0 只读探索**（默认开启）：`ls`, `pwd`, `cat`, `tail`, `rg`, `find`, `sed -n`（限制行数）
- **L1 诊断执行**（需显式开关/白名单）：`mvn -q -DskipTests compile`, `curl /actuator/health`（限制 host=localhost）
- **L2 可变更操作**（默认关闭）：`rm`, `git`, `apply_patch`, `docker` 等（必须由“人类确认/工单授权”）

### 6.2 安全策略（必须）

- **工作目录限制**：只能在沙箱 `WORKING_DIR` 或配置的 workspace 根路径下读/搜/跑
- **输出限制**：最大字符数 + 最大行数（超过截断并提示“可用参数分页”）
- **敏感信息脱敏**：token、密码、key、Authorization header 等
- **审计**：每次调用写入 `g3_validation_results` 或新增 `g3_tool_invocations`（推荐）并推送到 SSE/WS

> 备注：当前 OpenLovable `/api/sandbox/execute` 使用 `command.split(\" \")` 执行，无法可靠支持管道/重定向。建议后续升级 OpenLovable：支持 `args: string[]` 或 `shell: true`（但必须配套 allowlist）。

---

## 7. 分阶段落地（每阶段 2–4 小时可交付）

### Phase A：Repo Index MVP（先让 RAG“看得见”全仓库）

- 新增 `RepoIndexer`：扫描目录、过滤（排除 `target/`, `node_modules/`, `.git/`, `dist/`）、切片、写入 VectorStore
- 新增索引状态：记录 `projectId/ref/indexedAt/chunkCount`（用于“是否已建索引”的判断）
- 提供 API（只读/内网）：
  - `POST /v1/g3/knowledge/repo/index`（触发索引）
  - `GET /v1/g3/knowledge/repo/search?q=...&topK=...`

### Phase B：Coach/Coder 接入 Repo RAG（提升“修复命中率”）

- Coach：构造 query（错误摘要 + 关键类名/方法名）→ 拉取 repo 片段 → 注入 Prompt（预算控制）
- Coder：生成新文件前检索同类实现/接口约束，减少“瞎写不符合项目风格”

### Phase C：Tools API（read_file/search/ls/rg）+ 日志读取（达成 Manus 式探索）

- 新增 `G3ToolsetService`：
  - `read_file(path, maxLines)`
  - `search_in_files(query, includeGlobs, maxMatches)`
  - `run_shell_command(args[], timeoutMs)`（L0 allowlist）
  - `read_build_logs(jobId|sandboxId)`（标准化入口）
- 将工具调用统一打点成 `G3LogEntry`，前端 Console 可见

### Phase D：Playwright E2E（MCP/真实后端）验收

- 新增 E2E 用例（建议按环境变量开关，避免 CI 误报）：
  - `E2E_G3_TOOLSET=1`：验证工具接口可用、拒绝策略生效
  - `E2E_G3_RAG=1`：索引后检索命中（可用固定 query：如 `G3OrchestratorService`）
- 验证点：
  - `/actuator/health` OK
  - `/v1/g3/knowledge/repo/index` 返回成功并可查询
  - `run_shell_command` 对 `rm -rf` 等命令返回 403/400

---

## 8. 风险与降级

- **Redis Vector Store 不可用**：RAG 退化为 keyword search（`rg`/数据库全文检索），保证流程不断。
- **检索噪声过大**：先做 metadata 过滤 + 去重，再做轻量 rerank（基于 filePath/symbol 命中加权）。
- **执行安全**：默认只开 L0；所有 L1/L2 必须配置开关 + 监控告警 + 审计落库。

---

## 9. 推荐下一步（落地顺序）

1. Phase A + Phase B：先把 Repo RAG 打通，让“上下文能力”立竿见影提升。  
2. Phase C：补齐 Toolset（只读探索 + 日志读取），让自修复不再只依赖 Maven 输出。  
3. Phase D：用 Playwright E2E 固化回归，确保“没问题”可持续。  

