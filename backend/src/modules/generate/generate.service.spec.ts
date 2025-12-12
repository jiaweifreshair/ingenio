/**
 * GenerateService 单元测试
 */
import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { GenerateService } from './generate.service';
import { AppSpecEntity } from '../../entities/app-spec.entity';
import { AppSpecVersionEntity } from '../../entities/app-spec-version.entity';
import { WorkersClientService } from '../../common/clients/workers-client.service';
import { PlanAgentInput } from '@shared/types/agent-io.types';

describe('GenerateService', () => {
  let service: GenerateService;

  // Mock Repositories
  const mockAppSpecRepo = {
    findOne: jest.fn(),
    create: jest.fn(),
    save: jest.fn(),
  };

  const mockVersionRepo = {
    create: jest.fn(),
    save: jest.fn(),
  };

  // Mock WorkersClientService
  const mockWorkersClient = {
    invokePlanAgent: jest.fn(),
    invokeExecuteAgent: jest.fn(),
    invokeValidateAgent: jest.fn(),
  };

  // Mock IModelProvider
  const mockModelProvider = {
    getProviderName: jest.fn().mockReturnValue('openai'),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        GenerateService,
        {
          provide: getRepositoryToken(AppSpecEntity),
          useValue: mockAppSpecRepo,
        },
        {
          provide: getRepositoryToken(AppSpecVersionEntity),
          useValue: mockVersionRepo,
        },
        {
          provide: WorkersClientService,
          useValue: mockWorkersClient,
        },
        {
          provide: 'IModelProvider',
          useValue: mockModelProvider,
        },
      ],
    }).compile();

    service = module.get<GenerateService>(GenerateService);

    // 清除所有mock
    jest.clearAllMocks();
  });

  it('应该正确初始化服务', () => {
    expect(service).toBeDefined();
  });

  describe('generateAppSpec', () => {
    const mockInput: PlanAgentInput = {
      requirement: '创建一个用户管理系统',
      context: {
        tenantId: 'tenant-1',
        userId: 'user-1',
        projectType: 'custom',
      },
    };

    const mockPlanResult = {
      success: true,
      data: {
        modules: [
          {
            name: '用户管理',
            description: '管理用户信息',
            priority: 1,
          },
        ],
        estimatedComplexity: 'medium',
      },
      metadata: {
        requestId: 'req-1',
        agentType: 'plan' as const,
        timestamp: new Date().toISOString(),
        latencyMs: 1000,
      },
    };

    const mockExecuteResult = {
      success: true,
      data: {
        appSpec: {
          id: 'appspec-1',
          version: '1.0.0',
          tenantId: 'tenant-1',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          pages: [],
          dataModels: [],
          flows: [],
        },
        generationMetadata: {
          modelUsed: 'gpt-4',
          tokensUsed: 5000,
        },
      },
      metadata: {
        requestId: 'req-1',
        agentType: 'execute' as const,
        timestamp: new Date().toISOString(),
        latencyMs: 2000,
      },
    };

    const mockValidateResult = {
      success: true,
      data: {
        isValid: true,
        errors: [],
        warnings: [],
        score: 95,
      },
      metadata: {
        requestId: 'req-1',
        agentType: 'validate' as const,
        timestamp: new Date().toISOString(),
        latencyMs: 500,
      },
    };

    it('应该成功生成AppSpec', async () => {
      // Arrange
      mockWorkersClient.invokePlanAgent.mockResolvedValue(mockPlanResult);
      mockWorkersClient.invokeExecuteAgent.mockResolvedValue(mockExecuteResult);
      mockWorkersClient.invokeValidateAgent.mockResolvedValue(mockValidateResult);
      mockAppSpecRepo.findOne.mockResolvedValue(null);
      mockAppSpecRepo.create.mockReturnValue({});
      mockAppSpecRepo.save.mockResolvedValue({});
      mockVersionRepo.create.mockReturnValue({});
      mockVersionRepo.save.mockResolvedValue({});

      // Act
      const result = await service.generateAppSpec(mockInput);

      // Assert
      expect(result.success).toBe(true);
      expect(result.data).toBeDefined();
      expect(mockWorkersClient.invokePlanAgent).toHaveBeenCalledWith(
        mockInput,
        expect.any(String),
      );
      expect(mockWorkersClient.invokeExecuteAgent).toHaveBeenCalled();
      expect(mockWorkersClient.invokeValidateAgent).toHaveBeenCalled();
      expect(mockAppSpecRepo.save).toHaveBeenCalled();
      expect(mockVersionRepo.save).toHaveBeenCalled();
    });

    it('PlanAgent失败时应该返回错误', async () => {
      // Arrange
      mockWorkersClient.invokePlanAgent.mockResolvedValue({
        success: false,
        error: {
          code: 'PLAN_FAILED',
          message: 'Planning failed',
          recoverable: true,
        },
        metadata: mockPlanResult.metadata,
      });

      // Act
      const result = await service.generateAppSpec(mockInput);

      // Assert
      expect(result.success).toBe(false);
      expect(result.error).toBeDefined();
      expect(result.error?.code).toBe('GENERATION_FAILED');
    });

    it('ExecuteAgent失败时应该返回错误', async () => {
      // Arrange
      mockWorkersClient.invokePlanAgent.mockResolvedValue(mockPlanResult);
      mockWorkersClient.invokeExecuteAgent.mockResolvedValue({
        success: false,
        error: {
          code: 'EXECUTE_FAILED',
          message: 'Execution failed',
          recoverable: true,
        },
        metadata: mockExecuteResult.metadata,
      });

      // Act
      const result = await service.generateAppSpec(mockInput);

      // Assert
      expect(result.success).toBe(false);
      expect(result.error).toBeDefined();
    });

    it('ValidateAgent失败时应该返回错误', async () => {
      // Arrange
      mockWorkersClient.invokePlanAgent.mockResolvedValue(mockPlanResult);
      mockWorkersClient.invokeExecuteAgent.mockResolvedValue(mockExecuteResult);
      mockWorkersClient.invokeValidateAgent.mockResolvedValue({
        success: false,
        error: {
          code: 'VALIDATE_FAILED',
          message: 'Validation failed',
          recoverable: true,
        },
        metadata: mockValidateResult.metadata,
      });

      // Act
      const result = await service.generateAppSpec(mockInput);

      // Assert
      expect(result.success).toBe(false);
      expect(result.error).toBeDefined();
    });
  });
});
