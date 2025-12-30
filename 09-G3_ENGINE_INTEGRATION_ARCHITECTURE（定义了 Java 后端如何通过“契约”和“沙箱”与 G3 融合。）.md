# Ingenio G3 引擎融合架构方案

**版本**: v2.0 (Architecture Roadmap)
**日期**: 2025-12-27
**核心目标**: 实现从“线性模版生成”到“契约驱动+自修复智能体”的架构转型
**适用场景**: React + Spring Boot 全栈代码生成

---

## 1. 核心理念转变

| 维度 | 当前 V2 模式 (ExecuteAgentV2) | G3 引擎融合模式 (Target) |
| :--- | :--- | :--- |
| **生成方式** | **线性单向**：需求 → Schema → 模板渲染 → 结束 | **闭环迭代**：需求 → 规划 → 生成 → **编译/运行** → **错误修复** → 结束 |
| **前后端协同** | **软关联**：前端与后端仅靠需求文本关联，无硬性契约 | **契约驱动**：先生成 **OpenAPI (Swagger)** 规范，前后端 Agent 基于同一份契约开发 |
| **Java代码** | **模板填充**：基于 FreeMarker，逻辑固定 | **大模型编写**：理解业务逻辑，能写出复杂的 Service 层逻辑，不仅是 CRUD |
| **质量保证** | **无**：生成的代码可能有语法错误，无法保证能跑通 | **沙箱验证**：在 Docker/E2B 中执行 `mvn compile`，报错自动喂回 LLM 修复 |

---

## 2. 系统架构设计

### 2.1 总体架构图

```mermaid
graph TD
    subgraph "Control Plane (Spring Boot)"
        A[PlanRoutingService] -->|1. 提交任务| B[G3 Adapter (ExecuteAgentG3)];
    end

    subgraph "G3 Engine Core (独立服务/模块)"
        B -->|2. 注入上下文| C[Orchestrator (总控智能体)];
        
        C -->|3. 定义契约| D[Architect Agent (架构师)];
        D -->|产出| E(OpenAPI Spec + DB Schema);
        
        E -->|4a. 依据契约| F[Backend Coder (Java智能体)];
        E -->|4b. 依据契约+原型| G[Frontend Coder (React智能体)];
        
        subgraph "Self-Healing Sandbox (沙箱环境)"
            F -->|5a. 写入代码| H[Java Build Env];
            H -->|6a. mvn compile| I{编译成功?};
            I -- No (Log) --> F;
            I -- Yes --> J[Backend Artifact];
            
            G -->|5b. 写入代码| K[Node Build Env];
            K -->|6b. npm build| L{编译成功?};
            L -- No (Log) --> G;
            L -- Yes --> M[Frontend Artifact];
        end
    end

    J & M -->|7. 交付| N[最终全栈代码];
```

### 2.2 关键角色定义

1.  **Orchestrator (总控)**: 负责任务拆解、进度管理。
2.  **Architect Agent (架构师)**:
    *   **输入**: `PlanResult` (用户需求 + 实体蓝图 + 前端原型信息)。
    *   **产出**: `api.yaml` (OpenAPI 3.0), `schema.sql` (PostgreSQL DDL)。
    *   **职责**: 确保前后端对接口定义达成一致，解决模版代码与业务逻辑割裂的问题。
3.  **Backend Coder (Java Agent)**:
    *   **职责**: 基于 `schema.sql` 和 `api.yaml` 编写 Spring Boot 代码。
    *   **能力**: 使用 MyBatis-Plus Wrapper 实现复杂查询，而非简单 CRUD。
4.  **Frontend Coder (React Agent)**:
    *   **职责**: 融合 OpenLovable 视觉原型 + 真实 API 调用。
    *   **动作**: 解析 React 原型，���别 `mockData`，替换为基于 `api.yaml` 生成的 `fetch/axios` 调用。

---

## 3. 详细实施步骤

### 阶段一：接口标准化 (The Contract Layer)
**目标**: 建立前后端协作的“法律基础”。

*   **动作**:
    1.  设计 Architect Agent 的 Prompt，使其能根据需求生成标准的 OpenAPI YAML。
    2.  确保生成的 YAML 包含完整的 Request/Response Schema。
*   **产出**: 自动生成的 `api.yaml` 文件。

### 阶段二：后端智能体升级 (Backend Agent with G3)
**目标**: 让 AI 写出有“灵魂”的业务逻辑。

*   **动作**:
    1.  **Scaffolding (骨架)**: 保留 FreeMarker 生成项目结构 (pom.xml, Application.java)。
    2.  **Logic Generation (血肉)**:
        *   将 `schema.sql` + `api.yaml` + `复杂业务规则` 喂给 LLM。
        *   生成 `ServiceImpl` 和 `Controller`。
    3.  **ORM Constraint**: 强制 LLM 使用 MyBatis-Plus 标准写法。

### 阶段三：引入自修复闭环 (Self-Healing Loop)
**目标**: 保证代码 100% 可编译。

*   **动作**:
    1.  **Sandbox Integration**: 后端集成 Docker Client 或 E2B SDK。
    2.  **Feedback Loop**:
        *   执行 `mvn clean compile`。
        *   捕获 `stderr` (如 "Cannot find symbol 'User'").
        *   将错误回传给 LLM 进行修复 (Max Retries = 3)。

### 阶段四：前端融合 (Frontend Convergence)
**目标**: OpenLovable 的皮 + Spring Boot 的骨。

*   **动作**:
    1.  解析 OpenLovable 生成的 React 代码 AST。
    2.  定位静态数据 (`const data = [...]`)。
    3.  生成 API Client (`/src/api/user.ts`)。
    4.  注入 `useEffect` 和 API 调用逻辑。

---

## 4. 代码改造计划

### 4.1 新增 G3 适配器

在 `backend/src/main/java/com/ingenio/backend/agent/v2/` 下创建适配器：

```java
@Component("executeAgentG3")
public class ExecuteAgentG3Adapter implements IExecuteAgent {
    // G3 引擎客户端（可能是 gRPC 或 HTTP 调用独立服务）
    private final G3EngineClient g3Client;

    @Override
    public Map<String, Object> execute(PlanResult planResult) {
        // 1. 提交任务到 G3 总控
        String jobId = g3Client.submitJob(planResult);
        
        // 2. 等待自修复流程完成
        return g3Client.waitForArtifacts(jobId);
    }
}
```

### 4.2 更新 Agent 工厂

修改 `ExecuteAgentFactory.java` 支持灰度切换：

```java
public IExecuteAgent getExecuteAgent() {
    // 支持通过配置切换引擎
    if ("G3".equalsIgnoreCase(agentVersion)) {
        return executeAgentG3;
    }
    return executeAgentV2;
}
```

---

## 5. 总结

该方案通过引��� **OpenAPI 契约** 作为中间层，解决了“模版生成的代码如何结合”的难题，并通过 **沙箱自修复机制** 解决了 AI 生成代码质量不稳定的问题。这是 Ingenio 迈向工业级代码生成的关键一步。
