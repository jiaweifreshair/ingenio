# JeecgBoot（springboot3-satoken）融入 Ingenio 的修改方案（Draft）

> **目的**：把 JeecgBoot 作为“后台核心运营的主开发管理系统（控制面）”，支撑自动化生成/配置化开发；同时保持 Ingenio 现有 **C 端账密+微信登录**、现有 **Sa-Token 校验链路** 与 **Nest BFF** 不被破坏，并为后续多租户、多商户、多渠道的支付/AI 等能力中心沉淀预留演进空间。

---

## 0. 已确认的前提与约束（来自本次讨论）

- JeecgBoot 基线：`https://github.com/jeecgboot/JeecgBoot/tree/springboot3-satoken`（Jeecg 已支持 Sa-Token）。
- C 端登录：沿用 Ingenio 现有实现（账密 + 微信），短期不做大改。
- C 端接入形态：前端持有 `Bearer token`（不改为 Cookie）。
- BFF：`backend/`（Nest）继续作为 C 端对外入口。
- 鉴权：现有 C 端 JWT 已确认为 `RS256`；现有校验链路一期先不动。
- 基础设施：Redis 已有；数据库 PostgreSQL；单库多租户（所有域表带 `tenant_id`）。
- 事件总线：一期不引 MQ；后续可演进（Higress/Shengyu 网关也后置）。
- 安全：必须全链路敏感字段加密/脱敏与操作留痕。
- 资产治理：模板/能力/Prompt 为**平台全局共享**资产；写入/发布由 Jeecg 控制面完成，执行面只读消费。
- 服务间鉴权：Jeecg → BFF 采用 `Service JWT`（不复用 C 端 token）。
- 密钥过渡：无 KMS 阶段，KEK 采用“文件托管”（挂载文件，禁止入库）。
- 访问边界：Jeecg 仅内网/VPN，不需要 MFA。
- 近期优先级：Smart Builder 前端改版 + 资产闭环。

---

## 1. 总体定位与边界（先定“控制面/数据面”）

### 1.1 JeecgBoot 的定位（Control Plane）

JeecgBoot 负责“可配置、可快速交付、偏运营/管理/审批”的能力：

- 运营后台 CRUD、字典、配置、策略编排（如 AI 路由策略、支付路由/费率配置等）。
- 流程审批与工单（商户入驻、渠道开通、费率变更、密钥轮换审批等）。
- 报表与审计查询（按租户/商户/渠道维度统计）。
- 统一权限与后台用户管理（OPS 域）。

### 1.2 Ingenio（Nest BFF + 业务执行）定位（Data Plane）

Ingenio 现有体系负责“面向 C 端/核心链路”的业务执行：

- C 端登录交互（账密/微信）与接口聚合（BFF）。
- 各能力中心的核心执行逻辑（账号/AI/支付等），保证幂等/一致性/状态机。
- 多租户隔离在服务端强制执行（`tenant_id` 过滤、RLS/索引/审计）。

> **原则**：JeecgBoot 管“配置/审批/报表/审计”，不直接承载“核心交易执行”。

---

## 2. 目标架构（一期不引网关/MQ 的现实落法）

```mermaid
graph TD
  subgraph OPS[内部运营控制面（仅内网/VPN，无MFA）]
    Jeecg[JeecgBoot springboot3-satoken]
  end

  subgraph CEND[C端体验层]
    FE[Next.js 前端] -->|Bearer| BFF[Nest BFF backend/]
  end

  subgraph CORE[业务执行/能力中心（逐步沉淀）]
    S1[Account/AI/Payment 等服务(可混合栈)]
    DB[(PostgreSQL 单库多租户)]
    R[(Redis)]
  end

  Jeecg -->|Admin API + Service JWT| BFF
  BFF -->|业务API| S1
  S1 --> DB
  BFF --> DB
  Jeecg --> DB
  Jeecg --> R
  BFF --> R
```

### 2.1 一期集成方式（关键点）

- JeecgBoot **不直接调用 C 端接口**，而是调用“管理侧 Admin API”（建议走 Nest BFF 或独立 Admin Facade）。
- JeecgBoot 与 BFF 的调用需要**服务间鉴权**（不复用 C 端 Bearer token）。
- 不引 MQ：跨系统联动（审计/报表汇总）先走同步 HTTP + 本地 Outbox 预埋（见第 6 节）。

---

## 3. 鉴权与身份：保持现状 + 为 RS256 收敛预留

### 3.1 一期策略：不改现有 C 端 token 校验

一期的目标不是“统一身份根”，而是“Jeecg 先上线为运营主系统并能安全调用管理 API”。

- C 端：保持现有 Bearer token 签发与校验逻辑不变。
- OPS：JeecgBoot 走自身 Sa-Token 体系（后台用户）。
- 服务间：Jeecg → BFF 使用 **Service JWT（RS256）**（独立于 C 端 token，不复用、不混用）。

> 这样可以做到：JeecgBoot 快速上线且不影响已完成的 Ingenio C 端鉴权。

### 3.2 RS256（已确认）与 JWKS（待固化）

现有 C 端 JWT 已确认为 `RS256`，二期重点从“迁移算法”转为“固化规范”：

- 统一 Claim 规范（至少 `sub/tenantId/roles/permissions`，推荐 `iss/aud/jti`）。
- 提供公钥分发（JWKS）与轮换机制（含回滚预案）。

---

## 4. 多租户/多商户/多渠道：数据模型与隔离规范（必须先统一）

### 4.1 强制字段与索引（后续所有能力中心都遵守）

- 所有业务表必须带：`tenant_id`
- 支付域必须带：`merchant_id`、`channel_id`（以及必要的 `account_id`/`app_id`）
- 索引基线：以 `tenant_id` 作为联合索引前缀（如 `tenant_id + user_id/status/created_at`）

### 4.2 JeecgBoot 的“内部使用不多租户登录”与“平台多租户运营”不矛盾

运营台不需要“每个租户一套 Jeecg 部署”，但 Jeecg 需要具备：

- 平台运营视角的租户切换（对租户配置/审批/报表查询）。
- 切换动作强审计（谁、何时、从哪个 tenant 切到哪个 tenant）。

---

## 5. Jeecg → Ingenio（Nest BFF）管理侧 Admin API 设计

### 5.1 原则

- Admin API 与 C 端 API 逻辑隔离（路径、权限、限流、审计不同）。
- Admin API 不信任前端携带的 C 端 token，必须走服务间鉴权。
- 所有 Admin API 必须显式带上 `tenantId`（Header 或 Path），并且服务端二次校验。

### 5.2 最小接口清单（一期建议）

**租户/主数据**
- `POST /admin/tenants`、`PATCH /admin/tenants/:id/status`
- `POST /admin/merchants`、`POST /admin/channels`

**C 端账号运营**
- `GET /admin/app-users`（分页/筛选）
- `PATCH /admin/app-users/:id/status`（冻结/解冻）

**AI 能力配置（示例）**
- `POST /admin/ai/providers`
- `POST /admin/ai/keys`（密钥托管，入库加密）
- `PATCH /admin/ai/policies/:id`

**审计查询**
- `GET /admin/audit-logs`（tenantId + action + traceId + timeRange）

> 支付域二期再上，但建议一期就把 `merchant/channel` 主数据模型定型，避免后续大改。

---

## 6. 安全基线：敏感字段加密/脱敏/留痕（一期就必须落地）

### 6.1 字段级加密（PostgreSQL + 应用层信封加密）

- 算法建议：`AES-256-GCM`
- 入库字段建议：`ciphertext`、`nonce`、`key_version`（以及可选 `aad`）
- 可检索字段：额外存 `hash/盲索引`（避免明文检索）

### 6.2 密钥服务（无 KMS 的过渡实现）

一期可先做“密钥版本 + 轮换”能力（可在 JeecgBoot 内模块化实现）：

- 生成/轮换 DEK（数据加密密钥），KEK（主密钥）采用**文件托管**（运维挂载，禁止入库）。
- 所有密钥操作必须审计（谁操作、影响范围、key_version）。

### 6.3 统一脱敏与审计

- 脱敏：日志与接口响应对手机号/证件/卡号/token 统一 mask。
- 审计：关键操作落 `audit_log`（或扩展 Jeecg 日志），必须包含：
  - `trace_id`、`tenant_id`、`actor_uid`、`action`、`resource`、`before/after`（可 JSON diff）、`ip`、`ua`、`result`

### 6.4 无 MQ 情况下的“事件预埋”（Outbox）

即使一期不同步引 MQ，也建议在核心服务预埋：

- `outbox_event` 表（同事务写入）
- 定时任务/HTTP 回调把事件送到审计汇总端
- 后期上 Kafka/Rabbit 只需要把 outbox 投递改成消费链路

---

## 7. 代码与仓库层面的“修改清单”（输出到工程可执行）

> 下面是建议的改动清单（不等于必须一次性完成），用于拆分 Phase 与排期。

### 7.1 新增目录/工程（建议）

- `jeecg/`：JeecgBoot 代码与部署文件（建议作为独立子工程管理，发布节奏独立）。

### 7.2 配置与部署（建议）

- `docker-compose.yml`：新增 Jeecg 服务（连接现有 Postgres/Redis）。
- `nginx/`：新增 `/admin/*` 路由（仅内网/VPN），与 C 端隔离。
- `.env.example`：补齐 Jeecg 所需环境变量（DB/Redis、加密密钥、Service Token）。

### 7.3 Nest BFF（backend/）新增能力（建议）

- 新增 `Admin API` 路由与权限校验（服务间鉴权）。
- 统一 `traceId` 注入与透传（Jeecg → BFF → 下游）。
- 统一 `tenantId` 上下文解析与强制过滤（所有查询必须带 `tenant_id`）。

### 7.4 shared/（建议）

- 定义共享的 `TenantContext`、`TraceContext`、`AdminErrorCode`、以及 Admin API DTO（仅类型层，不引 mock）。

---

## 8. Phase 化落地路线（建议 3 个 Phase）

### Phase 1：资产闭环（Jeecg 写入/发布 + 执行面只读消费）

- 交付物：
  - JeecgBoot `springboot3-satoken` 跑通基础运维与后台登录。
  - 模板/能力/Prompt 的最小“编辑-审批-发布”流程在 Jeecg 落地（平台全局共享）。
  - `Admin API`（最小集）在 Nest BFF 落地，Jeecg 能用 `Service JWT` 安全调用并触发发布/回滚。
  - 审计/脱敏基线：至少覆盖租户切换、密钥托管、策略变更。
- 验收：
  - Jeecg 对 Admin API 的调用 0 未授权；审计可追溯；tenant_id 不串租。

### Phase 2：多租户主数据定型 + 加密/轮换完善

- 交付物：
  - `tenant/merchant/channel` 模型与索引基线固化。
  - 字段级加密与密钥轮换流程跑通（含回滚预案）。
- 验收：
  - 密钥轮换不影响业务读取；审计完整；敏感字段不落日志明文。

### Phase 3：JWKS 与服务间 JWT 轮换（固化规范）

- 交付物：
  - C 端与服务间 `RS256` 的公私钥体系、JWKS 发布与轮换策略。
  - `jti` 撤销与最小化踢人策略（适配服务间鉴权与后台高危操作）。
- 验收：
  - 密钥轮换不影响业务；撤销即时生效；可回滚。

---

## 8.1 质量门槛与本地验证（建议纳入 CI）

> 本次文档不直接改代码，但落地时建议把以下校验作为“每个 Phase 的最小门槛”，避免集成返工。

- 后端（Nest，`backend/`）：
  - `cd backend && pnpm tsc --noEmit`
  - `cd backend && pnpm lint`
  - `cd backend && pnpm test`（若本次改动涉及可测逻辑）
- 前端（Next.js，`frontend/`）：
  - `cd frontend && pnpm lint`
  - `cd frontend && pnpm test:unit`（若改动到登录态/鉴权 UI）
- 一键校验（如适用）：`node scripts/v2-validation.ts`
- 联调最小闭环：
  - Jeecg 能调用 Admin API 完成一条“租户/主数据变更”
  - 变更在 BFF 与下游落审计（含 `traceId/tenantId/actor`）
  - 任意跨租户查询被拒绝或强制过滤（抽样验证）

---

## 9. 风险与对策（必须提前承认的现实）

- **单体风险（Jeecg 越写越重）**：用“模块隔离 + 可拆部署（jeecg-ops/jeecg-auth）”控制；核心交易执行不进 Jeecg。
- **双鉴权体系短期共存**：一期通过“Admin API 服务间鉴权”隔离；三期再统一。
- **多租户串租风险**：强制 `tenant_id` 注入 + 索引基线 + 审计抽查；关键查询做自动化检测（CI 规则可后置）。
- **密钥自管风险（无 KMS）**：最小化暴露面、强审计、支持轮换与版本；预留迁移到 KMS/Vault 的接口。

---

## 10. 待补齐事项（落地前的最后 1 个问题）

1. 敏感字段清单（P0/P1/P2 分级）与必须加密的字段范围（AI Key/支付证书/个人信息等）？
