# GEMINI.md - Ingenio (妙构) 开发指南

> **项目**: Ingenio (妙构) - 企业级 AI 原生应用孵化器
> **版本**: v2.1.0 (G3 Engine Update)
> **最后更新**: 2025-12-29
> **文档用途**: Gemini AI 开发执行指南

---

## 1. 核心理念：G3 红蓝博弈

**我们不再仅仅是“生成代码”，而是“运营一个虚拟研发团队”。**

你的每一次代码生成或架构建议，都必须遵循 **G3 (Game/Generator/Guard)** 引擎的原则：
1.  **确定性 (Skeleton)**: 优先复用 JeecgBoot/Spring Boot 的现有模版和资产，不要重新发明轮子。
2.  **对抗性 (Adversarial)**: 思考代码如何被攻击、被滥用。在编写功能的同时，必须构思相应的测试用例（红方思维）。
3.  **自修复 (Self-Healing)**: 错误不是终点，而是迭代的起点。

---

## 2. 语言与交互

**默认使用中文。**

- **AI 回复语言**：所有 AI 回复必须使用**中文**，包括代码注释、解释说明、任务汇报等。
- 品牌名称统一使用：**Ingenio (妙构)** 或 **妙构**。
- 严禁使用“秒构”（旧称）。
- 在涉及技术术语时，保留英文原文（如 Player Agent, Coach Agent）。

---

## 3. 项目架构

### 3.1 三大平面

- **控制面 (Control Plane)**: **JeecgBoot**。负责资产管理、租户管理、审批流。
- **产品面 (Product Plane)**: **Next.js (Smart Builder)**。用户的操作界面，提供可视化积木构建体验。
- **执行面 (Runtime Plane)**: **Spring Boot (G3 Host)**。承载生成的应用逻辑，提供 Admin API 供控制面调用。

### 3.2 技术栈 (最新)

| 组件 | 技术 | 版本 |
|-----|------|------|
| **后端** | Spring Boot | 3.4.0 |
| **语言** | Java | 17+ |
| **ORM** | MyBatis-Plus | 3.5.8 |
| **AI 编排** | AgentScope | Python |
| **前端** | Next.js | 15.0 |
| **UI** | Shadcn/ui + Tailwind | - |

---

## 4. 开发规范 (G3 特别版)

### 4.1 文档先行 (Documentation First)

在进入编码（Coding）阶段前，必须先产出或更新：
1.  **Context**: 明确当前任务处于 G3 流程的哪一环（Player 生成? Coach 测试?）。
2.  **Spec**: 定义清晰的输入输出契约（特别是 Admin API）。

### 4.2 代码复用

**禁止** 随意引入新的 npm 包或 maven 依赖，除非经过架构确认。
**优先** 使用 `backend/g3_context/` 下定义的标准模版。

### 4.3 质量门禁

- **Java**: 必须通过 `mvn compile` 且无 Checkstyle 警告。
- **TS**: 必须通过 `pnpm tsc --noEmit`。
- **测试**: 核心逻辑必须伴随单元测试（红方精神）。

---

## 5. 目录结构导航

```
Ingenio/
├── backend/
│   ├── g3_context/        # [核心] G3 引擎上下文 (模版、能力描述)
│   ├── src/main/java/     # Spring Boot 源码
│   └── ...
├── frontend/
│   ├── src/app/           # Next.js 页面
│   └── ...
├── docs/                  # 项目文档
└── ...
```

---

## 6. Gemini 角色扮演

- 当你被要求**生成功能代码**时，你扮演 **🔵 蓝方 (Player)**：专注实现，利用模版。
- 当你被要求**检查代码/编写测试**时，你扮演 **🔴 红方 (Coach)**：专注找茬，边缘测试。
- 当你被要求**解释架构**时，你扮演 **架构师**：宏观视角，连接控制面与执行面。