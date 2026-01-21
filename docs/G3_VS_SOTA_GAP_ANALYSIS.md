# G3 引擎能力差距分析报告 (Benchmarking vs SOTA)

> **对标对象**: Manus, GenSpark, ClaudeCode
> **核心维度**: 上下文管理 (Context), 记忆能力 (Memory), 执行交互 (Execution), 规划能力 (Planning)

## 1. 核心能力全景对比

| 能力维度 | Ingenio G3 (当前 v2.2) | 业界顶尖 (Manus/ClaudeCode) | 差距分析 |
| :--- | :--- | :--- | :--- |
| **上下文管理** | **静态构建 (M2)**<br>- 基于文件列表/签名/摘要<br>- 4k Token 限制<br>- 仅感知生成产物 | **动态语义检索 (RAG)**<br>- 向量数据库索引全量代码<br>- 语义搜索 (Need-to-know basis)<br>- 感知整个 Git 仓库 | **高** (需引入 Vector DB) |
| **记忆能力** | **会话级 (M1)**<br>- 仅当前任务通过 Redis 持久化<br>- 任务结束即丢弃<br>- 无法跨任务学习 | **分层记忆系统**<br>- 短期 (Session)<br>- 长期 (Project/User Knowledge)<br>- 经验库 (Common Fix Patterns) | **中** (需建立 Global Knowledge) |
| **执行交互** | **黑盒沙箱**<br>- 仅支持简单的 Compile/Test 命令<br>- 无法探索环境 (ls, cat, grep)<br>- 无法调试 (System.out) | **交互式 Shell (REPL)**<br>- 像人类一样使用终端<br>- 自主探索文件系统<br>- 运行脚本/Debugger 收集信息 | **高** (需升级 Agent Toolset) |
| **规划能力** | **线性流水线**<br>- Architect -> Coder -> Coach<br>- 固定的状态机流转 | **动态思维链 (CoT)**<br>- 自主决定下一步 (Plan-Execute-Reflect)<br>- 发现错误可回溯规划 | **中** (需升级 AgentScope 编排) |

---

## 2. 具体差距与改进方向

### 2.1 上下文管理 (Context Management)
**现状问题**:
- 目前 G3 依赖 `G3ContextBuilder` 暴力拼接文件摘要。对于大型单体应用（如 Legacy Code），Context 很快不仅会溢出，而且充斥着无关信息（Noise）。
- Coach Agent 仅能看到 explicit 提供的文件，无法回答“这个 Entity 在哪个 module 定义的？”这类跨模块问题， unless explicitly included.

**对标能力 (Advanced)**:
- **Code Graph**: 建立类与方法的引用关系图，Agent 可以查询 "Who calls method X?"。
- **Semantic Search**: Agent 可以搜索 "Find authentication logic"，而不必知道文件名。

**建议改进**:
- [ ] 引入 `G3KnowledgeService` (Vector Store)。
- [ ] 支持 `search_codebase` 工具供 Agent 主动查询。

### 2.2 成功/失败历史记录 (History & Learning)
**现状问题**:
- M1 解决了“重启不丢失”问题，但依然是“一次性”的。
- 如果 Job A 修复了一个复杂的 `MyBatis Mapper XML` 错误，Job B 遇到同样的错误时，G3 依然会从零开始“瞎猜”，完全没有复用之前的成功经验。

**对标能力 (Advanced)**:
- **Episodic Memory**: 系统会记住“由于 X 错误，我修改了 Y 文件解决了它”。
- **Feedback Loop**: 用户对结果的点赞/修改会反哺给系统。

**建议改进**:
- [ ] 建立 `GlobalRepairDatabase`，存储 (ErrorSignature -> SuccessfulDiffPatch)。
- [ ] 在 Coach 修复前，先检索“历史最佳实践”。

### 2.3 交互式执行 (Agentic Execution)
**现状问题**:
- G3 是“生成式”而非“代理式”。它假设写完代码就能跑，跑不通就看报错。它不会“先看看服务器状态”或“检查一下数据库连接配置”。
- 排错极度依赖 Maven Stdout，信息非常有限。

**对标能力 (Advanced)**:
- **Terminal Access**: Agent 可以运行 `curl localhost:8080/health` 查看服务是否真的挂了。
- **File System Exploration**: Agent 可以 `ls -R` 确认文件到底生成在哪里了。

**建议改进**:
- [ ] 赋予 Coach Agent `run_shell_command` 能力 (需严格受控)。
- [ ] 支持 `read_file` (不仅仅是生成的 artifact，而是 workspace 任意文件)。

---

## 3. 结论

G3 目前处于 **Level 2 (Pipeline Automation)** 阶段，而 Manus/ClaudeCode 处于 **Level 3 (Autonomous Agency)** 阶段。

**当前 G3 的优势**:
- 工程化约束强（Blueprint/Specs），适合企业级生成的“确定性”要求。
- 自修复闭环（M1-M5）已经比单纯的 Copilot 强很多。

**当前 G3 的劣势**:
- 缺乏对环境的**感知力** (Exploration) 和 **记忆力** (Long-term Memory)。

**下一步推荐 (Next Priority)**:
1.  **Semantic Context (RAG)**: 解决大项目“看不全”的问题。
2.  **Global Learning**: 让 G3 越用越聪明。
