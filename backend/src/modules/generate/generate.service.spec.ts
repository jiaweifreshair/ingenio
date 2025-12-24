/**
 * GenerateService 单元测试
 * 说明：历史 Python Agent 执行面已移除，Nest 侧仅保留占位实现，避免误用。
 */
import { GenerateService } from './generate.service';
import { PlanAgentInput } from '@shared/types/agent-io.types';

describe('GenerateService', () => {
  let service: GenerateService;

  beforeEach(() => {
    service = new GenerateService();
  });

  it('应该正确初始化服务', () => {
    expect(service).toBeDefined();
  });

  it('generateAppSpec 应返回 AGENT_DISABLED（占位实现）', async () => {
    const input: PlanAgentInput = {
      requirement: '创建一个用户管理系统（占位测试）',
      context: {
        tenantId: 'tenant-1',
        userId: 'user-1',
        projectType: 'custom',
      },
    };

    const result = await service.generateAppSpec(input);

    expect(result.success).toBe(false);
    expect(result.error?.code).toBe('AGENT_DISABLED');
    expect(result.metadata.agentType).toBe('execute');
    expect(result.metadata.requestId).toBeTruthy();
  });
});
