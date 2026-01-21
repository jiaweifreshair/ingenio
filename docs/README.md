# 文档索引总览

> 本仓库当前处于“JeecgBoot × Smart Builder 融合”方案推进期。为减少认知冲突，文档按**当前有效**与**历史参考**分层管理。

---

## 1. 当前有效（优先阅读）

> 下列 7 份文档是“当前执行口径”，其余文档如有冲突，以此为准。

- `01-INGENIO_SMART_BUILDER_DEVELOPMENT_PLAN.md`：Smart Builder 综合开发路线图
- `02-TECH_ARCHITECTURE_PHASED_PLAN_JEECG_SMART_BUILDER.md`：Jeecg × Smart Builder 融合架构与分期
- `03-PRODUCT_UPGRADE_PLAN_SMART_BUILDER.md`：Smart Builder 产品升级与分期（含“国内/出海胶水层”抽象实施方案）
- `04-JEECGBOOT_INTEGRATION_MODIFICATION_PLAN.md`：JeecgBoot 融入方案（控制面/执行面/安全/多租户）
- `05-CURRENT_CODEBASE_SOLUTION_SCAN.md`：当前代码实际链路扫描纪要（以脚本/配置为准）
- `06-REPO_CLEANUP_CANDIDATES.md`：仓库清理候选清单（需确认后执行）
- `07-AGENT_STACK_AGENT_SCOPE_GOOSE_SELECTION.md`：智能体工程化技术栈选型（AgentScope + Goose）

> 编号索引：`docs/00-INDEX.md`

---

## 2. 工程文档（实现层）

- 后端（Java / Spring Boot）：`backend/docs/README.md`
- 前端（Next.js）：`frontend/docs/EXPLORATION_REPORTS_INDEX.md`（历史报告索引入口：`frontend/docs/HISTORY_REPORTS_INDEX.md`）
- /lab 指南：`docs/G3_LAB_RAG_TOOLSET_GUIDE.md`（RAG 索引/检索 + Toolset 执行）
- 架构与 ADR：`docs/architecture/README.md`
- 开发规范：`docs/development/DEVELOPMENT_GUIDE.md`、`docs/development/CODING_STANDARDS.md`、`docs/development/GIT_WORKFLOW.md`
- 部署相关：`docs/deployment/DEPLOYMENT_GUIDE.md`、`docs/deployment/DOCKER_GUIDE.md`、`docs/deployment/LOCAL_DEPLOYMENT_GUIDE.md`

---

## 3. 历史/归档说明

- `docs/legacy/`：历史方案与归档材料（仅作参考，不保证与现状一致）
- 归档命名：按 `YYYYMMDD/NN-<name>.md`（例如：`docs/legacy/20251224/04-product-tech-design.md`）
- `frontend/docs/DAY_*`：过程性日报文档（已确认删除）
