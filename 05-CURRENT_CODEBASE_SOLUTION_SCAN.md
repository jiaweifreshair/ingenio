# 现有代码方案扫描纪要（用于 Jeecg × Smart Builder 融入落地）

> 目的：把“当前仓库实际在跑的链路/模块/接口”梳理清楚，用于对齐 `02-TECH_ARCHITECTURE_PHASED_PLAN_JEECG_SMART_BUILDER.md` 的 Phase 0/1 实施。

---

## 1. 默认运行拓扑（以脚本/配置为准）

- **依赖服务（Docker）**：PostgreSQL/Redis/MinIO 由 `docker-compose.yml:5` 启动（后端容器被注释，不走 Docker）。
- **服务启动脚本**：`start-services.sh:84` 会启动 Spring Boot（`mvn spring-boot:run -Dspring-boot.run.profiles=local`），健康检查为 `http://localhost:8080/api/actuator/health`（`start-services.sh:117`）。
- **前端默认对接后端**：`frontend/.env.local:6` 指向 `http://localhost:8080/api`（直连 Spring Boot）。
- **OpenLovable**：脚本仅做健康检查（默认 `http://localhost:3001`，`start-services.sh:132`），并不在本仓库启动。
- **Python Agent（已移除）**：原 `workers/`、`agent-service/` 两套 FastAPI 实现已确认删除，后续以 JeecgBoot 的 AI 平台能力为主。
- **NestJS（backend/ TS）**：存在独立服务入口（默认端口 3001，`backend/src/main.ts:1`），但脚本未启动，且端口与 OpenLovable 默认端口冲突。

---

## 2. 鉴权与登录（当前实现）

### 2.1 Spring Boot（Java）侧

- **Sa-Token JWT**：本地 profile（`application-local.yml`）启用 `token-style: jwt`（`backend/src/main/resources/application-local.yml:71`），使用 `jwt-secret-key`（对称密钥形态，`backend/src/main/resources/application-local.yml:73`）。
- **拦截策略**：`SaTokenConfig` 对 `/v2/**` 整段放行（`backend/src/main/java/com/ingenio/backend/config/SaTokenConfig.java:81`），因此 V2 链路可匿名跑通（对“资产闭环/对外化”会有安全影响）。
- **登录能力**：账密登录与微信扫码登录在 `/api/v1/auth/*`（例如 `backend/src/main/java/com/ingenio/backend/controller/AuthController.java:1`、`backend/src/main/java/com/ingenio/backend/service/WechatService.java:1`）。

### 2.2 前端请求头形态

- 前端 API client 会把本地保存的 token 直接写入 `Authorization` 头（无 `Bearer` 前缀，`frontend/src/lib/api/client.ts:82`）。
- Java 侧接口返回里存在 `tokenType=Bearer` 的语义（例如刷新 token 返回 `tokenType("Bearer")`，`backend/src/main/java/com/ingenio/backend/controller/AuthController.java:300`），但前端与后端对“Bearer 前缀是否必须”目前并未统一。

> **提示**：你已确认“C 端 JWT = RS256”。当前仓库的本地 profile 配置并未体现 RS256（未出现 RSA 公私钥/JWKS 配置），后续需要以“实际线上实现”为准做一次对齐检查。

---

## 3. 多租户（tenant_id）现状

- Java 侧大量实体具备 `tenant_id` 字段（如 `backend/src/main/java/com/ingenio/backend/entity/ProjectEntity.java:1`、`backend/src/main/java/com/ingenio/backend/entity/AppSpecEntity.java:1` 等）。
- 默认租户 ID 来自配置：`ingenio.tenant.default-tenant-id`（`backend/src/main/resources/application.yml:256`）。
- V2 PlanRouting 在未登录情况下会使用默认 tenantId/userId 兜底（`backend/src/main/java/com/ingenio/backend/controller/PlanRoutingController.java:56`）。
- MyBatis-Plus 的 `MetaObjectHandler` 会在 insert 时尝试填充 `tenantId`（`backend/src/main/java/com/ingenio/backend/config/MyBatisPlusMetaObjectHandler.java:41`），但目前是“生成一个新 UUID 字符串”作为 tenantId 的写入逻辑，和“单库多租户 tenant_id 作为租户主键”的语义并不一致，需要在 Phase 0 统一租户上下文注入策略。

---

## 4. Smart Builder（模板资产）现状

- **行业模板库**：
  - 后端存在 `IndustryTemplateController`（`/api/v1/templates`）与匹配接口（`/api/v1/templates/match`），见 `backend/src/main/java/com/ingenio/backend/controller/IndustryTemplateController.java:1`。
  - 启动时会 seed 41 个行业模板（`TemplateDataInitializer`，`backend/src/main/java/com/ingenio/backend/service/TemplateDataInitializer.java:1`）。
- **前端模板页**：`frontend/src/app/templates/page.tsx:1` 调用的 API 契约包含分页与 `GET /api/v1/templates/categories`（见 `frontend/src/lib/api/templates.ts:47`），但后端当前并未提供 `categories` 端点，且后端 `GET /api/v1/templates` 返回的是 List（非分页结构），存在契约不一致。
- **模板表并存**：Java 侧存在 `TemplateEntity`（`backend/src/main/java/com/ingenio/backend/entity/TemplateEntity.java:1`）与 `industry_templates` 并行，可能导致“模板资产”概念分裂，需要在资产闭环 Phase 1 统一口径（Body/Brain/Soul 的资产模型与发布态）。

---

## 5. 原型生成与 AI 链路现状（V2）

- V2 Plan 路由链路：`/api/v2/plan-routing/*`（`backend/src/main/java/com/ingenio/backend/controller/PlanRoutingController.java:1`）。
- 意图识别：`/api/v1/intent/*`（`backend/src/main/java/com/ingenio/backend/controller/IntentController.java:1`）。
- SuperDesign：`/api/v1/superdesign/*`（`backend/src/main/java/com/ingenio/backend/controller/SuperDesignController.java:1`）。
- OpenLovable 代理：Spring Boot 通过 `OpenLovableController` 转发到 `ingenio.openlovable.base-url`（`backend/src/main/java/com/ingenio/backend/controller/OpenLovableController.java:1`）。

---

## 6. NestJS（backend/ TS）现状（并行方案）

- NestJS 侧具备：
  - JWT Strategy（对称密钥配置）`backend/src/common/strategies/jwt.strategy.ts:1`
  - TenantMiddleware（强制 `X-Tenant-Id` 或 JWT claim）`backend/src/common/middleware/tenant.middleware.ts:47`
  - GenerateService（占位实现）：`backend/src/modules/generate/generate.service.ts:1` 当前直接返回 `AGENT_DISABLED`，避免误用历史 Python Agent 链路（后续生成能力以 JeecgBoot AI 平台为主）

> 现状更像“并行试验架构”：Spring Boot 承担对外主链路；Nest 侧链在仓库中存在但未被默认脚本使用；Python Workers 已确认移除。Phase 0 需要明确“执行面入口”最终收敛方案，避免重复建设。

---

## 7. 与目标方案的主要差距（用于 Phase 0/1）

- **资产闭环缺口**：缺少 Jeecg 控制面写入/审批/发布能力与执行面只读消费的完整链路（你已确认“写入全部由 Jeecg 完成”）。
- **契约不一致**：前后端模板 API 形态（分页/分类）不一致，需要统一（建议以“资产发布态 + 只读消费 API”重构）。
- **Service JWT 未落地**：Jeecg → 执行面 Admin API 的 `Service JWT` 校验、`jti` 撤销与审计尚未实现。
- **RS256/JWKS 需对齐**：你已确认 C 端 JWT 为 RS256，但当前代码配置未显式体现 RSA/JWKS；需要以“线上真实实现”为准补齐工程化配置。
- **租户上下文注入需统一**：tenantId 的来源、默认值、写入策略（尤其是 insertFill 的 tenantId 行为）需在 Phase 0 统一。

---

## 8. 下一步建议

- 以 `02-TECH_ARCHITECTURE_PHASED_PLAN_JEECG_SMART_BUILDER.md:1` 的 **Phase 0/Phase 1** 为基线推进；
- 先明确一条关键口径：**执行面入口到底是 Spring Boot 直出，还是 Nest BFF 统一入口（并把 Spring Boot 下沉为内部服务）**，否则后续 Admin API、鉴权、租户上下文会出现重复实现与不一致。
