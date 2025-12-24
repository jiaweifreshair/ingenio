# 架构文档索引

> 本文档索引了所有系统架构相关的文档，包括架构设计、技术选型和架构决策记录（ADR）。

---

## 🏗️ 核心架构文档

### 系统架构
- [系统架构文档](./ARCHITECTURE.md) - 完整的系统架构设计，包括 Dyad 多 Agent 架构

### AI 代码生成架构
- [AI 代码生成器架构](./AI_CODE_GENERATOR_ARCHITECTURE.md) - AI 代码生成器整体架构
- [代码生成 Agent 架构](./CODE_GENERATION_AGENT_ARCHITECTURE.md) - Agent 架构详解
- [代码生成 Agent 深度思考](./CODE_GENERATION_AGENT_ULTRATHINK.md) - Agent 设计深度分析
- [最终代码生成 Agent 架构](./FINAL_CODE_GENERATION_AGENT_ARCHITECTURE.md) - 最终架构方案
- [Phase 4 AI 增强代码生成架构](./PHASE4_AI_ENHANCED_CODEGEN_ARCHITECTURE.md) - Phase 4 架构设计

### 集成和实现
- [最佳实践应用工具实现](./BEST_PRACTICE_APPLIER_TOOL_IMPLEMENTATION.md) - 最佳实践工具实现
- [集成总结报告](./INTEGRATION_SUMMARY_REPORT.md) - 集成方案总结
- [终极混合代码生成方案](./ULTIMATE_HYBRID_CODE_GENERATION_SOLUTION.md) - 混合方案设计

---

## 📋 架构决策记录 (ADR)

架构决策记录（Architecture Decision Records）记录了重要的技术选型和架构决策。

### ADR 列表

| ADR | 标题 | 描述 |
|-----|------|------|
| [ADR-001](./adr/001-ai-model-selection.md) | AI 模型选择 | AI 模型选型决策（通义千问、DeepSeek、Claude） |
| [ADR-002](./adr/002-code-generation-strategy.md) | 代码生成策略 | 代码生成策略决策（Kotlin Multiplatform + KuiklyUI） |
| [ADR-003](./adr/003-zero-mock-policy.md) | 零 Mock 策略 | 测试 Mock 策略决策 |
| [ADR-004](./adr/004-typescript-strict-mode.md) | TypeScript 严格模式 | TypeScript 严格模式启用决策 |

### ADR 格式说明

每个 ADR 文档遵循以下结构：
- **状态**: 提议 / 已接受 / 已拒绝 / 已弃用
- **上下文**: 决策背景和问题描述
- **决策**: 做出的架构决策
- **后果**: 决策带来的影响和权衡

---

## 🔍 按主题查找

### AI Agent 架构
- [代码生成 Agent 架构](./CODE_GENERATION_AGENT_ARCHITECTURE.md)
- [代码生成 Agent 深度思考](./CODE_GENERATION_AGENT_ULTRATHINK.md)
- [最终代码生成 Agent 架构](./FINAL_CODE_GENERATION_AGENT_ARCHITECTURE.md)

### 代码生成策略
- [ADR-002: 代码生成策略](./adr/002-code-generation-strategy.md)
- [Phase 4 AI 增强代码生成架构](./PHASE4_AI_ENHANCED_CODEGEN_ARCHITECTURE.md)
- [终极混合代码生成方案](./ULTIMATE_HYBRID_CODE_GENERATION_SOLUTION.md)

### 系统集成
- [集成总结报告](./INTEGRATION_SUMMARY_REPORT.md)
- [最佳实践应用工具实现](./BEST_PRACTICE_APPLIER_TOOL_IMPLEMENTATION.md)

---

## 📚 相关文档

- [技术文档](../TECHNICAL_DOCUMENTATION.md) - 完整技术实现文档
- [开发指南](../development/DEVELOPMENT_GUIDE.md) - 开发环境配置
- [API 文档](../../backend/docs/api/) - API 接口文档

---

**最后更新**: 2025-12-24





