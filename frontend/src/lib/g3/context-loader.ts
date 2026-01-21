import { AgentRole } from "./types";
import {
  getG3KernelCapabilities,
  getG3KernelPrompt,
  getG3KernelTemplate,
} from "@/lib/api/g3-kernel";

export class G3ContextLoader {
  async getSystemPrompt(role: AgentRole): Promise<string> {
    // 将 Role 映射到后端 g3_context/prompts 的文件名
    const name =
      role === "ARCHITECT"
        ? "SYSTEM_PROMPT_ARCHITECT.md"
        : role === "PLAYER"
          ? "SYSTEM_PROMPT_PLAYER.md"
          : "SYSTEM_PROMPT_COACH.md";

    return getG3KernelPrompt(name);
  }

  async getTemplate(type: 'CONTROLLER' | 'SERVICE'): Promise<string> {
    const name = type === "CONTROLLER" ? "JavaController.txt" : "JavaService.txt";
    return getG3KernelTemplate(name);
  }

  async getCapabilities(): Promise<string> {
    return getG3KernelCapabilities();
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
