# LangChain4j 集成方案（G3）

## 1. 目标与原则

### 1.1 目标
- 一次生成可编译率提升（相对基线 +5% 或修复轮次下降 ≥20%）。
- 生成与修复优先使用 Claude 4.5 / Gemini 3 Pro，DeepSeek 作为兜底。
- RAG 切换至 Redis 向量检索 + Uniaix OpenAI 兼容 Embedding（doubao-embedding-text-240715）。
- 保留现有 G3 编排与质量门控，确保可回滚与可量化评估。

### 1.2 原则
- **不改编排**：`backend/src/main/java/com/ingenio/backend/service/g3/G3OrchestratorService.java` 流程不变。
- **渐进替换**：Agent 与 RAG 组件可配置切换，保留旧实现。
- **结构化输出**：优先使用结构化 JSON 输出，失败再走兜底解析。
- **成本可控**：模型路由与重试上限受配置约束。

## 2. 范围与不变部分

### 2.1 替换范围
- G3 Agent 实现（Architect/Coder/Coach）。
- RAG 组件（G3KnowledgeStore 与 RepoIndex）。
- 模型路由与工具编排（LangChain4j）。

### 2.2 不变部分
- 编排与状态机：G3 Phase 及自修复循环。
- 产物落库与日志流：`G3ArtifactEntity` / SSE 逻辑。
- 质量门控：`G3PhaseValidator` 与验证策略。
- 非 G3 业务模块仍可使用 Spring AI（本次不改）。

## 3. 现有流程嵌入点

- 编排入口：`backend/src/main/java/com/ingenio/backend/service/g3/G3OrchestratorService.java`
- Agent 接口：`backend/src/main/java/com/ingenio/backend/agent/g3/IArchitectAgent.java`
- Agent 接口：`backend/src/main/java/com/ingenio/backend/agent/g3/ICoderAgent.java`
- Agent 接口：`backend/src/main/java/com/ingenio/backend/agent/g3/ICoachAgent.java`
- Prompt 与上下文：`backend/src/main/java/com/ingenio/backend/prompt/PromptTemplateService.java`
- 上下文构建：`backend/src/main/java/com/ingenio/backend/service/g3/G3ContextBuilder.java`
- 修复历史：`backend/src/main/java/com/ingenio/backend/entity/g3/G3SessionMemory.java`
- 修复闭环：`backend/src/main/java/com/ingenio/backend/agent/g3/impl/CoachAgentImpl.java`

## 4. 模型策略与路由

### 4.1 生成与修复优先级
- **代码生成 / 修复**：Claude 4.5 → Gemini 3 Pro → DeepSeek。
- **降级触发**：超时、429、结构化输出失败、工具调用失败。

### 4.2 路由逻辑
- 按任务类型（codegen/repair/analysis）选择优先级列表。
- 连续失败触发降级，错误签名记录进 SessionMemory。
- 记录模型使用次数与成功率，用于后续调参。

## 5. RAG 替换方案（Redis + Uniaix Embedding）

### 5.1 目标
- 替换 `G3KnowledgeStore` 与 `G3RepoIndexService` 的 Spring AI VectorStore。
- 使用 LangChain4j EmbeddingStore + Redis 向量索引。

### 5.2 方案要点
- **Embedding 模型**：doubao-embedding-text-240715（Uniaix OpenAI 兼容接口）。
- **向量存储**：Redis Vector Index（要求 Redis Stack 或兼容服务；评估后可迁移 pgvector）。
- **切片策略**：保持 500/100 tokens 切片，确保可比性。
- **元数据**：scope/jobId/tenantId/projectId/filePath/fileName 保持一致。

### 5.3 迁移策略
- 双写：Spring AI VectorStore 与 LC4J EmbeddingStore 并存。
- 影子检索：仅对比召回与修复命中，不影响主流程。
- 达标后切主：`ingenio.g3.rag.engine=lc4j`。

### 5.4 Uniaix Embedding 接入规范（建议）
- **接口风格**：按 OpenAI 兼容 `/v1/embeddings` 调用。
- **Base URL**：支持直接配置 `https://www.uniaix.com`，系统会自动补齐 `/v1`。
- **向量维度**：`doubao-embedding-text-240715` 维度为 2048，使用 `INGENIO_G3_RAG_EMBEDDING_DIMENSION=2048` 统一配置。
- **批量策略**：按当前限额信息为不限，但仍建议分批与超时重试控制。

## 6. 组件设计（LangChain4j）

### 6.1 新增组件
- `LangChain4jModelRouter`：模型优先级与降级策略。
- `LangChain4jToolRegistry`：统一注册工具（@Tool）。
- `LangChain4jArchitectAgentImpl`：生成契约与 Schema。
- `LangChain4jBackendCoderAgentImpl`：生成后端代码产物。
- `LangChain4jCoachAgentImpl`：修复与验证闭环。
- `G3KnowledgeStoreLc4j`：RAG 新实现。

### 6.2 输出结构
- Architect：`contractYaml`、`dbSchemaSql`、`analysisSummary`。
- Coder：`artifacts[]`、`pomFragment`。
- Coach：`fixedArtifacts[]`、`analysisReport`。
- 解析失败兜底：复用 `BackendCoderAgentImpl` 的 parse/sanitize 策略。

## 7. 工具映射与复用

- 编译验证：`backend/src/main/java/com/ingenio/backend/service/CompilationValidator.java`
- 修复服务：`backend/src/main/java/com/ingenio/backend/service/RepairService.java`
- Blueprint 校验：`backend/src/main/java/com/ingenio/backend/service/blueprint/BlueprintValidator.java`
- 检索与摘要：`backend/src/main/java/com/ingenio/backend/service/g3/G3ToolsetService.java`
- Schema 生成：`backend/src/main/java/com/ingenio/backend/codegen/generator/DatabaseSchemaGenerator.java`
  - 需要拆出“纯生成”方法，避免工具调用写盘副作用。

## 8. 配置清单（示例）

```yaml
ingenio:
  langchain4j:
    routing:
      codegen: [claude, gemini, deepseek]
      repair: [claude, gemini, deepseek]
      analysis: [claude, gemini, deepseek]
    providers:
      claude:
        base-url: ${INGENIO_LC4J_CLAUDE_BASE_URL:https://aigateway.edgecloudapp.com/v1/your-project-id/your-app}
        api-key: ${INGENIO_LC4J_CLAUDE_API_KEY}
        model: ${INGENIO_LC4J_CLAUDE_MODEL:claude-opus-4-5-20251101}
      gemini:
        base-url: ${INGENIO_LC4J_GEMINI_BASE_URL:https://aigateway.edgecloudapp.com/v1/your-project-id/your-app}
        api-key: ${INGENIO_LC4J_GEMINI_API_KEY}
        model: ${INGENIO_LC4J_GEMINI_MODEL:gemini-3-pro-high}
      deepseek:
        base-url: ${INGENIO_LC4J_DEEPSEEK_BASE_URL:https://api.qnaigc.com/v1}
        api-key: ${INGENIO_LC4J_DEEPSEEK_API_KEY}
        model: ${INGENIO_LC4J_DEEPSEEK_MODEL:deepseek-r1-0528}
      uniaix:
        base-url: ${UNIAIX_BASE_URL:https://www.uniaix.com}
        api-key: ${UNIAIX_API_KEY}
        embedding-model: ${UNIAIX_EMBEDDING_MODEL:doubao-embedding-text-240715}
        embedding-dimension: ${UNIAIX_EMBEDDING_DIMENSION:${INGENIO_G3_RAG_EMBEDDING_DIMENSION:2048}}
  g3:
    agent:
      engine: ${INGENIO_G3_AGENT_ENGINE:lc4j}
    rag:
      engine: ${INGENIO_G3_RAG_ENGINE:lc4j}
      embedding-dimension: ${INGENIO_G3_RAG_EMBEDDING_DIMENSION:2048}
```

> 注意：API Key 等敏感信息仅通过环境变量注入，禁止入库；`INGENIO_G3_RAG_EMBEDDING_DIMENSION` 必须与 `doubao-embedding-text-240715` 实际维度一致，否则 Redis 向量索引会初始化失败。
> 提醒：`claude-opus-4-5-thinking` 需要 `max_tokens` 大于 `thinking.budget_tokens`，请通过 `INGENIO_LC4J_CLAUDE_MAX_TOKENS` 提升上限，避免 400 错误。

## 9. 迁移路线图

- **Phase 0**：Claude/Gemini/DeepSeek 工具调用 PoC。
- **Phase 1**：接入 `LangChain4jBackendCoderAgentImpl`（关键路径）。
- **Phase 2**：迁移 Architect/Coach + 结构化输出。
- **Phase 3**：RAG 双写 + 影子检索评估。
- **Phase 4**：指标达标后切主并收尾文档。

## 10. 评估与验收

- **指标**：一次编译通过率、平均修复轮次、总耗时、成本、重复错误率。
- **对照**：旧 Agent + 旧 RAG 与新 Agent + 新 RAG A/B。
- **通过门槛**：可编译率 +5% 或修复轮次下降 ≥20%。
- **回滚策略**：配置切回旧 Agent 与旧 RAG。

## 11. 风险与对策

- **工具调用不稳定**：添加 JSON 指令兜底与二次修复，限制重试上限。
- **结构化输出偏离**：强制 schema 校验与 fallback 解析。
- **RAG 召回下降**：双写 + 影子检索，未达标不切主。
- **成本激增**：模型限额 + 超限降级。

## 12. 待确认项

- Uniaix Embedding 的可用性与调用稳定性验证结果。
- Redis Vector Index 已确定使用 Redis Stack（验证可用性与容量指标）。
- 是否需要在配置中区分“代码生成/修复/分析”的模型路由策略。
