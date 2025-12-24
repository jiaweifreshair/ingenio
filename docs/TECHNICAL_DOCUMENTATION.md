# 技术文档总览（实现向）

> 本文档作为“技术文档入口页”，用于修复仓库内对 `docs/TECHNICAL_DOCUMENTATION.md` 的引用，并给出当前最常用的技术导航。
>
> **当前有效的方案口径**请优先阅读：`02-TECH_ARCHITECTURE_PHASED_PLAN_JEECG_SMART_BUILDER.md` 与 `05-CURRENT_CODEBASE_SOLUTION_SCAN.md`。

---

## 1. 快速导航

- 现状扫描（以脚本/配置为准）：`05-CURRENT_CODEBASE_SOLUTION_SCAN.md`
- 融合架构与分期：`02-TECH_ARCHITECTURE_PHASED_PLAN_JEECG_SMART_BUILDER.md`
- Jeecg 融入方案：`04-JEECGBOOT_INTEGRATION_MODIFICATION_PLAN.md`
- Smart Builder 产品升级：`03-PRODUCT_UPGRADE_PLAN_SMART_BUILDER.md`

---

## 2. 本地启动（以仓库脚本为准）

- 一键启动依赖 + 后端（Spring Boot）：`./start-services.sh`
- 一键启动（另一套脚本体系）：`./scripts/start-all.sh`

> 说明：当前默认链路是 **前端直连 Spring Boot（8080）**；Nest BFF 更偏“并行方案/未来入口”，详见 `05-CURRENT_CODEBASE_SOLUTION_SCAN.md`。

---

## 8. OpenLovable-CN

### 8.1 本地服务要求与健康检查

- 默认地址：`http://localhost:3001`
- 健康检查：`GET /api/health`
- 可通过环境变量覆盖：`OPENLOVABLE_BASE_URL`

> 若本地未启动 OpenLovable-CN，当前仓库脚本会给出提示，但不阻塞 Spring Boot 主链路启动（详见 `start-services.sh`）。
