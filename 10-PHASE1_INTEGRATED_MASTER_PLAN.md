# Ingenio Smart Builder Phase 1 - 综合实施主计划 (Integrated Master Plan)

**版本**: v3.0 (Optimal Fusion Strategy)
**日期**: 2025-12-25
**状态**: **APPROVED FOR EXECUTION**
**编写者**: Product & Tech Architect
**基准**: 
- **架构**: `@07-AGENT_STACK` (Goose CLI 工程化)。
- **代码**: 复用 `open-lovable-cn` 现有 Prompt 引擎作为 Player。
- **策略**: 服务端优先，增量接入。

---

## 1. 战略综述 (Executive Summary)

### 1.1 核心目标
在保留现有代码生成能力（"Player"）的基础上，外挂一套 **"Coach + Executor"** 质量保障体系，实现从“单次生成”到“自动修复闭环”的进化。

### 1.2 核心策略：智能流式管道 (Smart Streaming Pipeline)
为了解决“后台修复耗时久，前台无反馈”的问题，我们将改造 API 响应协议：
*   **Legacy Mode**: 直接流式返回代码（保持现状）。
*   **G3 Mode**: 采用 **"Log + Code"** 双流模式。
    *   阶段 1 (Thinking): 流式输出修复日志 (e.g., `[Goose] Checking... Found 2 errors. Fixing...`)。
    *   阶段 2 (Delivering): 修复完成后，流式输出最终代码。

---

## 2. 系统架构 (System Architecture)

### 2.1 融合架构图

```mermaid
graph TD
    User[用户请求] --> API{API Gateway}
    
    subgraph "Legacy Path (现有逻辑)"
        API -- "mode=fast" --> P1[Legacy Player]
        P1 --> Stream1[直接流式输出]
    end
    
    subgraph "G3 Path (新增引擎)"
        API -- "mode=quality" --> P2[Player Wrapper]
        P2 -- "1. 复用现有Prompt生成" --> Code[初始代码]
        
        Code --> Loop{自修复循环}
        Loop -- "2. Goose 检查 (Mock/Real)" --> Check[验证结果]
        Check -- "有错" --> Coach[Coach 分析]
        Coach -- "Patch" --> P2
        Check -- "通过/超时" --> Final[最终代码]
        
        Loop -.-> LogStream[流式日志 (Thinking...)]
        Final --> CodeStream[流式代码]
    end
```

### 2.2 核心组件定义

1.  **资产适配器 (Asset Adapter)**
    *   **任务**: 将 `app/page.tsx` 中硬编码的模板（Ecommerce, SaaS 等）封装���符合 `types/assets.ts` 的标准对象。
    *   **目的**: 统一数据源，为未来对接 JeecgBoot 做准备，同时不破坏现有逻辑。

2.  **G3 Engine (The Loop)**
    *   **Player**: **复用现有项目**的 `generate` 函数。
    *   **Coach**: 新增组件，只负责看 Log 出 Patch。
    *   **Executor (Goose CLI)**:
        *   **Phase 1**: In-Memory TS Check (Mock Goose)。
        *   **Phase 2**: Dockerized Goose (Real CLI)。

---

## 3. 实施路线图 (Implementation Roadmap)

### 阶段一：服务端 PoC 验证 (Phase 1.1 - The "Lab")
**目标**：构建 G3 引擎内核，跑通“生成-检查-修复”闭环。

*   **Action 1**: 创建 `lib/lab/typescript-check.ts` (Mock Goose)。
    *   利用 TS Compiler API 实现内存级语法检查，模拟 Goose 的 `tsc` 命令。
*   **Action 2**: 创建 `lib/lab/g3-engine.ts`。
    *   实现 Loop 逻辑：`Generate -> Check -> Review -> Fix`。
*   **Action 3**: 创建 `/api/lab/g3-poc`。
    *   验证输入一个故意写错的 Prompt，输出修复后的代码和过程日志。

### 阶段二：资产数据化 (Phase 1.2 - The "Foundation")
**目标**：将现有硬编码模板“资产化”。

*   **Action**: 创建 `lib/assets/mock-data.ts`。
    *   把现有项目里的模板选项，提取为 `Template` 常量��表。
    *   确保现有 Prompt 引擎能适配这个数据结构。

### 阶段三：混合路由接入 (Phase 1.3 - The "Integration")
**目标**：让现有 API 支持 G3 模式。

*   **Action**: 改造 `api/generate-ai-code-stream/route.ts`。
    *   解析请求参数，决定走 `Legacy` 还是 `G3` 分支。
    *   如果走 G3，使用 `TransformStream` 将引擎的日志和代码合并下发。

---

## 4. 技术规格 (Technical Specs)

### 4.1 资产数据模型 (Unified Asset Model)

```typescript
// types/assets.ts
export interface Template {
  id: string; // e.g., "template-ecommerce"
  name: string;
  description: string;
  // 核心：将现有 prompt 逻辑映射到这里
  systemPromptConfig: {
    baseRole: string; // e.g., "Expert React Developer"
    techStack: string[]; // e.g., ["Next.js", "Tailwind"]
  };
}
```

### 4.2 智能日志协议 (Smart Log Protocol)

前端通过解析 SSE 消息前缀来区分日志和代码：

*   `LOG: [Goose] Running type check...` (前端展示为加载状态文本)
*   `CODE: import React from ...` (前端渲染到编辑器)

---

## 5. 验收标准 (Acceptance Criteria)

1.  **PoC 通过**: 实验室接口能自动修复 `Property 'x' missing` 类型的 TS 错误。
2.  **资产无缝**: 代码中不再散落硬编码的模板字符串，���部收敛到 `lib/assets`。
3.  **体验升级**: 即使后台在修复代码，前端也能看到动态的 "Thinking" 日志，而不是死板的 Loading 转圈。

---

## 6. 下一步立即行动 (Next Actions)

1.  **Clean Up**: 删除旧文档。
2.  **Execute Phase 1.1**:
    *   创建 `src/lib/lab/typescript-check.ts` (Mock Goose Executor)。
    *   创建 `src/lib/lab/mock-agents.ts` (Simple Player/Coach)。

---
**文档结束**
