# /lab G3 RAG + Toolset 使用说明

## 目标
- 提供可视化的 G3 生成日志与任务状态
- 支持 Repo 级 RAG 索引/检索，提升上下文定位能力
- 提供受控的 Toolset（搜索/读文件/命令执行）用于日志分析与自助排障

---

## 入口与前置条件
- 入口：`http://localhost:3000/lab`
- 后端：`./start-services.sh`（确保 8080 端口可用）
- 登录：需要有效 Token（本地登录后即可）
- 注意：`/v1/g3/tools/execute` 需要 jobId，必须先启动 G3 任务

---

## 功能概览
- 需求输入与模板选择
- G3 日志监控（SSE 流）
- RAG 仓库索引与语义检索
- Toolset 搜索与文件读取
- Toolset 受控命令执行（只读）

---

## 实现位置速查（与代码同步）
- 前端页面：`frontend/src/app/lab/page.tsx`（调用后端 `/v1/g3/knowledge/*`、`/v1/g3/tools/*`）
- 前端转发（旧链路/兼容）：`frontend/src/app/api/lab/g3-poc/route.ts`（转发后端创建 Job + 订阅日志 SSE）
- 后端 Repo RAG：`backend/src/main/java/com/ingenio/backend/module/g3/controller/G3KnowledgeController.java`
- 后端 Toolset：`backend/src/main/java/com/ingenio/backend/module/g3/controller/G3ToolsetController.java`
- 关键接口：`POST /v1/g3/knowledge/repo/index`、`GET /v1/g3/knowledge/repo/search`、`GET /v1/g3/tools/search`、`GET /v1/g3/tools/read-file`、`POST /v1/g3/tools/execute`（需 jobId）

---

## 操作步骤

### 1. 启动 G3 任务
1) 输入需求（至少 10 字符）
2) 可选：选择行业模板
3) 点击“启动任务”
4) 日志区会显示 `任务已创建，jobId=...`

### 2. RAG 索引与检索
1) 点击“索引仓库”触发 Repo Index
2) 输入关键词后点击“检索”
3) 结果会显示命中的 filePath / score / content 片段

### 3. Toolset 搜索与读文件
1) 在“搜索工作区内容”输入关键词并点击“搜索”
2) 在“读取文件路径”输入路径并点击“读取”
3) 读出的内容会显示在右侧文本面板

### 4. Toolset 命令执行（只读）
1) 先确保 jobId 已生成（否则会提示“请先启动任务”）
2) 输入只读命令（例如：`rg "G3" -n backend/src`）
3) 点击“执行”，查看 stdout/stderr 与执行耗时

---

## 注意事项
- Toolset 受控执行：只允许白名单命令与安全路径
- 若命令被拒绝，会返回 `policyDecision=BLOCK` 与 `policyReason`
- Repo Index 依赖向量存储可用性；不可用时可能返回“skipped”

---

## 常见问题
- **索引失败**：检查后端日志与向量存储配置
- **搜索无结果**：尝试更通用的关键词或先重新索引
- **命令执行失败**：确认 jobId 是否存在，命令是否在白名单内

---

## 回归测试
- `E2E_G3_FULL=1 E2E_ENTRY_FULL=1 E2E_USERNAME=justin E2E_PASSWORD=Test12345 pnpm e2e:chromium -- src/e2e/g3-lab-rag-toolset.spec.ts`
