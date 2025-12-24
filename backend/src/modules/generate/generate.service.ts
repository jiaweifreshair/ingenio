/**
 * Generate服务
 * 说明：本仓库已移除历史 Python Agent（workers/、agent-service/）。
 * 后续“生成/修复智能体”能力以 JeecgBoot AI 平台为主；Nest 侧仅保留最小占位实现，避免误用。
 */

import { v4 as uuidv4 } from 'uuid';
import { Injectable, Logger } from '@nestjs/common';
import { AgentResponse, PlanAgentInput } from '@shared/types/agent-io.types';
import { AppSpec } from '@shared/types/appspec.types';

/**
 * Generate服务
 */
@Injectable()
export class GenerateService {
  private readonly logger = new Logger(GenerateService.name);

  /**
   * 生成AppSpec的完整流程
   * 包括规划、执行、校验三个阶段
   */
  async generateAppSpec(input: PlanAgentInput): Promise<AgentResponse<AppSpec>> {
    const requestId = uuidv4();
    const startTime = Date.now();

    this.logger.warn(
      `[${requestId}] Nest GenerateService 已禁用：Python Agent 已移除，生成能力请以 JeecgBoot AI 平台为主。` +
        ` tenantId=${input.context.tenantId}, requirementLength=${input.requirement.length}`,
    );

    const totalLatency = Date.now() - startTime;

    return {
      success: false,
      error: {
        code: 'AGENT_DISABLED',
        message:
          'Nest 侧 Python Agent 已移除，生成能力请以 JeecgBoot AI 平台为主（本接口仅保留占位，避免误用）',
        recoverable: false,
      },
      metadata: {
        requestId,
        agentType: 'execute',
        timestamp: new Date().toISOString(),
        latencyMs: totalLatency,
      },
    };
  }
}
