# Ingenio Global: 技术架构与里程碑规划 (v1.0)

> **目标**: 将 "Ingenio A.O.S" 战略转化为可执行的工程路线图。
> **适用范围**: 核心开发团队、架构师。

> ⚠️ 文档状态：**历史版本里程碑/架构草案**，未纳入当前 Jeecg 控制面与 Smart Builder 资产闭环口径；短期实施请以 `02-TECH_ARCHITECTURE_PHASED_PLAN_JEECG_SMART_BUILDER.md` 与 `05-CURRENT_CODEBASE_SOLUTION_SCAN.md` 为准。

---

## 1. 技术架构设计 (Technical Architecture)

### 1.1 总体架构图 (High-Level Design)

```mermaid
graph TD
    User[用户/创作者] -->|Vibe Coding (自然语言/截图)| PlatformFE[Ingenio 平台前端 (Next.js)]
    PlatformFE -->|API Call| Gateway[API Gateway (Nginx/Kong)]
    
    subgraph "Layer 1: The Cloud Brain (AI Salesforce)"
        Gateway --> CoreService[NestJS 核心服务]
        CoreService -->|Auth/Tenant| AuthDB[(PostgreSQL - Multi-Tenant)]
        CoreService -->|Task Dispatch| TaskQueue[Redis Queue]
        
        TaskQueue --> AIWorker[Python AI Worker (FastAPI)]
        AIWorker -->|LLM Interface| OmniModels[GPT-4o / Claude 3.5 / Gemini]
        AIWorker -->|Memory R/W| VectorDB[(Vector DB - User Memory)]
    end

    subgraph "Layer 2: The Factory (构建引擎)"
        AIWorker -->|Generate Code| CodeGenEngine[代码生成引擎]
        CodeGenEngine -->|AST Mutation| PolymorphicCompiler[多态编译器 (防封号)]
        PolymorphicCompiler -->|Build & Deploy| DeployService[Vercel/Docker 部署服务]
    end

    subgraph "Layer 3: The Ecosystem (应用矩阵)"
        DeployService --> App1[App A: 植物识别]
        DeployService --> App2[App B: 昆虫识别]
        App1 -- "API (Memory/Tools)" --> Gateway
        App2 -- "API (Memory/Tools)" --> Gateway
    end
```

### 1.2 关键技术栈 (Tech Stack)

| 领域 | 技术选型 | 理由 |
| :--- | :--- | :--- |
| **Frontend** | **Next.js 15 (App Router)** | React Server Components 适合 SEO 和高性能动态渲染。 |
| **Styling** | Tailwind CSS + Shadcn UI | 适合 AI 生成和 Vibe Coding 的动态样式调整。 |
| **Backend Core** | **NestJS** (Node.js) | 企业级架构，强类型，适合复杂业务逻辑和多租户管理。 |
| **AI/Data Ops** | **Python (FastAPI)** | 拥有最丰富的 AI 库 (LangChain, PyTorch) 和 AST 处理工具。 |
| **Database** | **PostgreSQL** | 稳定，支持 JSONB (多租户配置) 和 pgvector (向量搜索)。 |
| **Queue** | Redis / BullMQ | 异步处理耗时的 AI 生成任务。 |
| **i18n** | **next-intl** | 轻量级、类型安全的 Next.js 国际化标准方案。 |

---

## 2. 阶段里程碑与愿景 (Phased Milestones)

### Phase 1: The Genesis (创世纪 - 全球化地基)
**愿景**: "连接全球的起点"。打造一个支持中英双语、具备基本社区功能的 MVP，验证我们自己的技术栈 (Dogfooding)。

*   **里程碑目标**:
    1.  **架构**: 完成 `next-intl` 国际化改造，支持无缝语言切换。
    2.  **业务**: 上线 "Ingenio Guild" (社区) 原型。
    3.  **数据**: 设计并部署 User/Tenant 基础数据库 Schema。
    4.  **交付物**: 一个可访问的、多语言的社区网站。

### Phase 2: The Awakening (觉醒 - 记忆与大脑)
**愿景**: "应用开始拥有记忆"。上线 Cloud Brain 核心，让生成的 App 不再是只有 UI 的空壳，而是能记住用户的智能体。

*   **里程碑目标**:
    1.  **架构**: 搭建 NestJS 后端，实现 "AI Salesforce" 基础 API (Auth, Memory)。
    2.  **AI**: 实现 "Vision-to-Text" 流程，打通多模态记忆存储。
    3.  **组件**: 定义 "Component Standard v1.0"，上线第一个官方组件 (e.g., Stripe Payment)。
    4.  **交付物**: 具备“用户记忆”功能的 Demo App（如：AI 记梦日记）。

### Phase 3: The Evolution (进化 - 自治工厂)
**愿景**: "Devin 进驻工厂"。实现 Vibe Coding 和代码多态性，让应用生成变成工业化流水线。

*   **里程碑目标**:
    1.  **交互**: 上线 "Vibe Coding" 界面 (看图写代码，对话修改 UI)。
    2.  **核心**: 开发 **Polymorphic Compiler (多态编译器)**，实现 AST 级别的代码变异。
    3.  **自治**: 实现 "Self-Healing" 机制，Agent 自动修复编译错误。
    4.  **交付物**: 能够一键生成 10 个外观不同但功能相似的 App 矩阵。

### Phase 4: The Singularity (奇点 - 完整生态)
**愿景**: "万物互联的生态"。开放市场，多租户企业版上线，DAO 治理。

*   **里程碑目标**:
    1.  **商业**: 上线 Component Marketplace (组件市场)，开启计费系统。
    2.  **企业**: 推出 Enterprise Tenant (私有租户)，支持私有模型部署。
    3.  **生态**: 社区驱动的 Prompt 和 Template 交易。

---

## 3. 下一步立即行动 (Immediate Actions - Sprint 1)

**Focus: Phase 1 (Foundation)**

1.  **Task**: Install `next-intl` & Configure Middleware.
2.  **Task**: Refactor `ui-text.ts` to `en.json` / `zh.json`.
3.  **Task**: Create Database Schema for `Users` and `Communities`.
