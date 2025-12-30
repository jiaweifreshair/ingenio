# G3 引擎上下文 (G3 Engine Context)

本目录 (`backend/g3_context/`) 存放 **Ingenio G3 红蓝博弈引擎** 运行所需的**核心上下文**与**知识库**。

## 📂 目录结构

- **`prompts/`** (新)
    - **用途**: 存放驱动 Agent 的 System Prompts。
    - **`SYSTEM_PROMPT_ARCHITECT.md`**: 架构师。负责需求拆解。
    - **`SYSTEM_PROMPT_PLAYER.md`**: 🔵 蓝方。负责填空式代码生成。
    - **`SYSTEM_PROMPT_COACH.md`**: 🔴 红方。负责生成攻击性测试。

- **`capabilities.md`**
    - **用途**: 定义 JeecgBoot/Spring Boot 后端开放给 AI 调用的标准能力。
    - **使用者**: 🔵 蓝方 (Player)。

- **`templates/`**
    - **用途**: 存放标准代码骨架。
    - **`JavaController.txt`**: Controller 模版。
    - **`JavaService.txt`**: Service 模版。

## 🔄 G3 工作流 (The Loop)

1.  **User** 输入需求。
2.  **Architect** (基于 `SYSTEM_PROMPT_ARCHITECT`) -> 输出 JSON 任务单。
3.  **Player** (基于 `SYSTEM_PROMPT_PLAYER` + `templates`) -> 输出 `.java` 源码。
4.  **Coach** (基于 `SYSTEM_PROMPT_COACH`) -> 输出 `AttackTest.java`。
5.  **Executor** (CI/CD) -> 运行测试。
    - 🛡️ 攻击被拦截 (Test OK) -> **交付**。
    - 💥 攻击成功 (漏洞存在) -> **打回 Player 修复**。