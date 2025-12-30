import { AgentRole } from './types';

// TODO: Phase 4 - Move these to backend API and fetch dynamically
const PROMPTS = {
  ARCHITECT: `# Role: Ingenio G3 Chief Architect (架构师)
## 1. 你的使命
你负责 Ingenio (妙构) 平台的架构拆解。用户会输入一段自然语言的业务需求，你需要将其转化为符合 Ingenio 技术规范的技术任务列表。

## 2. 核心原则
1. 复用优先: 优先调用 capabilities.md 中定义的系统能力。
2. 标准分层: Controller -> Service -> Mapper。
3. 原子化: 将大需求拆解为独立的模块。

## 3. 输出格式 (JSON)
必须输出严格的 JSON。
`,

  PLAYER: `# Role: Ingenio G3 Player Agent (蓝方)
## 1. 你的使命
根据架构设计，填充标准代码模版。

## 2. 行为准则
1. 严格遵循模版: 使用 JavaController.txt 和 JavaService.txt。
2. 能力复用: 必须调用 capabilities.md。
3. 安全性: 检查 TenantContext。
`,

  COACH: `# Role: Ingenio G3 Coach Agent (红方)
## 1. 你的使命
编写测试用例来证明蓝方的代码是垃圾。

## 2. 攻击策略
1. 越权访问 (IDOR)
2. 输入验缺失
3. 逻辑漏洞

## 3. 输出格式
输出 JUnit 5 测试代码 (@Test)。
`
};

const TEMPLATES = {
  CONTROLLER: `package com.ingenio.backend.module.\${moduleName}.controller;
// ... (Standard Controller Template)
`,
  SERVICE: `package com.ingenio.backend.module.\${moduleName}.service;
// ... (Standard Service Template)
`
};

const CAPABILITIES = `# Ingenio G3 引擎能力注册表
1. UserService / TenantService
2. PaymentService
3. NotificationService
4. StorageService
`;

export class G3ContextLoader {
  async getSystemPrompt(role: AgentRole): Promise<string> {
    return PROMPTS[role] || '';
  }

  async getTemplate(type: 'CONTROLLER' | 'SERVICE'): Promise<string> {
    return TEMPLATES[type] || '';
  }

  async getCapabilities(): Promise<string> {
    return CAPABILITIES;
  }

  async assemblePrompt(role: AgentRole, userRequirement: string, extraContext: string = ''): Promise<string> {
    const systemPrompt = await this.getSystemPrompt(role);
    const capabilities = await this.getCapabilities();
    
    return `
${systemPrompt}

## Context: System Capabilities
${capabilities}

## Context: Extra Info
${extraContext}

## User Requirement
${userRequirement}
`;
  }
}
