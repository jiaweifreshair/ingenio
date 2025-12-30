# Ingenio Phase 1 执行手册 (Execution Manual)

**版本**: v1.0
**状态**: **EXECUTING**
**来源**: 聚合自 `@02`, `@08`, `@09`, `@10` 文档
**目标**: 提供 Phase 1 开发的详细、原子化操作指南。

---

## 1. 核心目标 (Objective)

在前端 (Next.js) 层构建 **G3 引擎 (Node.js 版)** 的最小可行性产品 (MVP)，实现 **"生成 -> 检查 -> 修复"** 的自动化闭环，并向用户展示实时的 **"红蓝博弈日志"**。

---

## 2. 目录结构规划 (Directory Structure)

```text
frontend/
├── src/
│   ├── types/
│   │   ├── g3.ts               # [NEW] G3 引擎相关类型 (Log, Artifact)
│   │   └── assets.ts           # [NEW] 资产相关类型 (Template, Capability)
│   │
│   ├── lib/
│   │   └── lab/                # [NEW] G3 实验室 (核心逻辑)
│   │       ├── typescript-check.ts # Mock Executor (内存级 TS 检查)
│   │       ├── g3-engine.ts        # Orchestrator (调度红蓝博弈)
│   │       └── mock-agents.ts      # Simple Player/Coach 实现
│   │
│   ├── components/
│   │   └── generation/         # [NEW] 生成相关组件
│   │       └── G3LogViewer.tsx     # 日志可视化组件
│   │
│   └── app/
│       └── api/
│           └── lab/
│               └── g3-poc/     # [NEW] G3 引擎测试接口
│                   └── route.ts
```

---

## 3. 详细开发任务 (Step-by-Step Tasks)

### Step 1: 基础类型定义 (Foundation)

**文件**: `frontend/src/types/g3.ts`

```typescript
export type G3Role = 'PLAYER' | 'COACH' | 'EXECUTOR';

export interface G3LogEntry {
  timestamp: string;
  role: G3Role;
  message: string;
  level: 'info' | 'warn' | 'error' | 'success';
}

export interface G3Artifact {
  code: string;
  filename: string;
  language: 'typescript' | 'java' | 'python';
  isValid: boolean;
}
```

**文件**: `frontend/src/types/assets.ts`

```typescript
export interface Capability {
  id: string;
  name: string;
  description: string;
  type: 'JAVA_SERVICE' | 'PYTHON_AGENT' | 'JEECG_AI_NATIVE'; // Added JEECG_AI_NATIVE
}

export interface Template {
  id: string;
  name: string;
  description: string;
  capabilities: Capability[];
}
```

---

### Step 2: 实现内存级 TypeScript 裁判 (The Executor)

**文件**: `frontend/src/lib/lab/typescript-check.ts`

**核心逻辑**:
1.  引入 `typescript` 库 (需确保 `package.json` 有依赖)。
2.  使用 `ts.createSourceFile` 解析代码字符串。
3.  遍历 AST 或使用 `ts.transpileModule` 捕获语法错误。
4.  **注意**: 这是一个 "Mock Executor"，只检查语法错误 (Syntax Error)，暂不检查类型语义错误 (Semantic Error)，以保证运行速度和环境兼容性。

**输出**:
```typescript
{
  valid: boolean;
  errors: string[]; // e.g. ["Line 10: Unexpected token"]
}
```

---

### Step 3: 实现 Agent 模拟器 (The Agents)

**文件**: `frontend/src/lib/lab/mock-agents.ts`

**核心逻辑**:
1.  `PlayerAgent`: 接收需求，调用 LLM (复用现有 `generate` 逻辑或 Mock) 生成初始代码。
    *   *Mock 模式*: 返回一段故意带有语法错误的代码。
2.  `CoachAgent`: 接收代码和错误日志，调用 LLM 生成修复后的代码。
    *   *Mock 模式*: 使用简单的字符串替换修复 Step 1 中的错误。

---

### Step 4: 实现 G3 ��度引擎 (The Engine)

**文件**: `frontend/src/lib/lab/g3-engine.ts`

**核心逻辑**:
```typescript
export async function* runG3Loop(requirement: string) {
  // 1. Player 生成
  yield { role: 'PLAYER', message: 'Generating initial code...' };
  let code = await PlayerAgent.generate(requirement);
  
  // 2. 循环检查 (Max 3 rounds)
  for (let i = 0; i < 3; i++) {
    yield { role: 'EXECUTOR', message: `Running check (Round ${i+1})...` };
    const checkResult = TypeScriptCheck.validate(code);
    
    if (checkResult.valid) {
      yield { role: 'EXECUTOR', message: 'Check passed! ✅' };
      yield { type: 'CODE', content: code };
      return;
    }
    
    // 3. Coach 修复
    yield { role: 'COACH', message: `Found errors: ${checkResult.errors.join(', ')}` };
    yield { role: 'COACH', message: 'Fixing code...' };
    code = await CoachAgent.fix(code, checkResult.errors);
  }
  
  yield { role: 'EXECUTOR', message: 'Max retries reached. Delivering best effort.' };
  yield { type: 'CODE', content: code };
}
```

---

### Step 5: 实现 API 接口 (The Gateway)

**文件**: `frontend/src/app/api/lab/g3-poc/route.ts`

**核心逻辑**:
1.  创建 `POST` 接口。
2.  使用 `ReadableStream`。
3.  调用 `runG3Loop`，将 yield 的日志和代码转换为 SSE 格式 (`data: ...`) 推送给前端���

---

### Step 6: 前端可视化组件 (The UI)

**文件**: `frontend/src/components/generation/G3LogViewer.tsx`

**核心逻辑**:
1.  接收 SSE 流数据。
2.  使用 Terminal 风格展示日志 (黑底绿字)。
3.  根据 `role` (PLAYER/COACH/EXECUTOR) 显示不同的 Icon 或颜色。
4.  最终收到 `CODE` 类型消息时，高亮展示代码。

---

## 4. 执行约束 (Constraints)

1.  **不破坏现有功能**: 所有新代码都在 `lib/lab` 和 `api/lab` 下，不影响主流程。
2.  **依赖最小化**: 尽量复用项目已有的 `openai` 或 `anthropic` SDK，不引入新的重型依赖。
3.  **Mock 优先**: 在 Phase 1，Agent 的智能程度不重要，重要的是**跑通闭环流程**。

---

## 5. 验收标准 (Checklist)

- [ ] `types/g3.ts` 创建完成。
- [ ] `typescript-check.ts` 能正确识别 `const a = ;` 这种语法错误。
- [ ] 访问 `/api/lab/g3-poc` 能看到流式的日志输出。
- [ ] 前端能渲染出红蓝博弈的动态效果。
