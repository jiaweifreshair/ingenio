# Ingenio Smart Builder 综合开发方案

本文档基于 `05-CURRENT_CODEBASE_SOLUTION_SCAN.md`、`04-JEECGBOOT_INTEGRATION_MODIFICATION_PLAN.md`、`03-PRODUCT_UPGRADE_PLAN_SMART_BUILDER.md` 和 `02-TECH_ARCHITECTURE_PHASED_PLAN_JEECG_SMART_BUILDER.md` 四份核心规划文档制定，旨在提供一个清晰、可落地的综合开发路线图。

---

## 总体目标与架构

**目标**：将 Ingenio 从代码生成工具，升级为集成了 JeecgBoot 作为强大控制面的“AI原生应用孵化器”（Smart App Builder）。

---

### 核心职责划分与API类型

为了确保系统架构的清晰与安全，我们明确了API的两种核心角色：

*   **C端用户API (如 `/api/v1/**`, `/api/v2/**` 等)**:
    *   **用途**: 服务于平台的最终用户及由平台生成的各类应用。负责处理用户注册、登录、业务操作等所有面向公众的请求。
    *   **鉴权**: 使用**C端用户JWT**。

*   **Admin管理API (`/api/admin/**`)**:
    *   **用途**: 专用于内部服务间的管理通道，特别是为控制面（JeecgBoot）提供管理执行面（Spring Boot）的接口。
    *   **鉴权**: 使用**服务间JWT (Service JWT)**，与C端用户体系完全隔离。

**结论**：未来由Ingenio生成的应用，其用户鉴权将通过C端用户API完成，**不会**使用`/api/admin`接口。

---

## 最终确认版：第一阶段 (Phase 0) 实施方案

本方案已整合所有讨论细节，将作为第一阶段开发的最终执行依据。所有后端实现将聚焦于 **Spring Boot** (`backend/`) 应用。

### **任务1：统一上下文规范**

*   **Trace ID (`trace_id`)**:
    *   我们将通过一个高优先级的Spring Boot `Filter`来处理。
    *   该Filter会检查请求头 `X-Trace-Id`，如果不存在则生成一个新UUID，并利用`MDC`机制将其注入日志上下文，确保全链路日志的可追踪性。

*   **Tenant ID (`tenant_id`)**:
    *   **获取**: 在用户通过Sa-Token认证后，后续的Filter将从用户会话中解析出`tenant_id`，并将其存入一个基于`ThreadLocal`的请求上下文中。
    *   **修正**: 我们已确认，将**修正**当前`MyBatisPlusMetaObjectHandler`在新增数据时生成随机`tenant_id`的逻辑。修改后，它将从`ThreadLocal`中获取当前请求的正确`tenant_id`进行填充，这是实现多租户功能的正确方式。

### **任务2：Admin API 与服务间认证**

*   **路由**: 在Spring Boot中，通过创建`admin`子包和使用`@RequestMapping("/api/admin")`注解，来构建统一的`/api/admin/**`管理接口路由。
*   **鉴权**:
    *   对`/api/admin/**`路径的请求，将由Spring Boot应用**自身进行鉴权**，不会转到JeecgBoot。
    *   Spring Boot会通过Sa-Token拦截器，调用独立的JWT验证逻辑。该逻辑使用配置好的服务公钥，来验证由JeecgBoot签发的`Service JWT`。
    *   请求头统一使用`Authorization: Bearer <token>`的形式。

### **任务3：敏感信息处理规范**

*   **数据结构**: 我们将在Java代码中定义一个标准的`EncryptedData` POJO类，作为所有加密数据在程序中的统一模型。
*   **服务契约**: 定义一个`EncryptionService` Java接口，用于统一加密和解密操作的规范。
*   **密钥管理**: 正式采纳“文件托管KEK（密钥加密密钥）”方案。KEK文件由外部挂载，Spring Boot应用启动时加载，用于加密/解密存储在数据库中的DEK（数据加密密钥）。

---

## 第二阶段：Smart Builder MVP 与资产闭环 (Phase 1) - [近期工作重点]

打通从资产创建到消费的完整链路，实现核心价值。

- **任务1：控制面 - JeecgBoot 资产管理**
    - **目标**：在 JeecgBoot 中，创建管理“行业模板 (Template)”、“AI 能力 (Capability)”和“Prompt 包 (PromptPack)”的 CRUD 界面，并具备 `DRAFT` -> `PUBLISHED` 的发布状态流。
- **任务2：执行面 - 资产消费接口**
    - **目标**：在 Spring Boot 中，提供只读的 API，供前端查询状态为 `PUBLISHED` 的资产。
- **任务3：产品面 - 前端 Smart Builder 改造**
    - **目标**：根据产品方案，重构前端首页，实现“行业模板长廊”和“AI能力元素表”等新 UI，并对接资产消费接口。
- **任务4：打通发布链路**
    - **目标**：实现 JeecgBoot 通过调用 Spring Boot 的 Admin API 来安全地触发“资产发布”的动作。

### 第三阶段：核心运营能力建设 (Phase 2)

在 MVP 基础上，完善 JeecgBoot 的后台管理能力。

- **任务**：在 JeecgBoot 中，逐步建立租户、商户、渠道等核心主数据的管理功能和审批流，为后续的 AI 和支付能力中心建设铺路。

### 后续阶段：专业能力中心沉淀 (Phase 3+)

- **任务**：根据规划，逐步建设独立的 AI 能力中心（统一管理多厂商模型、密钥和用量）和支付中心。
