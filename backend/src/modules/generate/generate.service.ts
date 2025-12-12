/**
 * Generate服务
 * 负责协调PlanAgent→ExecuteAgent→ValidateAgent的生成流程
 */

import { Injectable, Logger, Inject } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { v4 as uuidv4 } from 'uuid';
import { IModelProvider } from '@common/interfaces/model-provider.interface';
import {
  PlanAgentInput,
  PlanAgentOutput,
  ExecuteAgentInput,
  ExecuteAgentOutput,
  ValidateAgentInput,
  ValidateAgentOutput,
  AgentResponse,
} from '@shared/types/agent-io.types';
import { AppSpec } from '@shared/types/appspec.types';
import { WorkersClientService } from '@common/clients/workers-client.service';
import { AppSpecEntity, AppSpecVersionEntity } from '../../entities';

/**
 * Generate服务
 */
@Injectable()
export class GenerateService {
  private readonly logger = new Logger(GenerateService.name);

  constructor(
    @InjectRepository(AppSpecEntity)
    private readonly appSpecRepo: Repository<AppSpecEntity>,
    @InjectRepository(AppSpecVersionEntity)
    private readonly versionRepo: Repository<AppSpecVersionEntity>,
    @Inject('IModelProvider')
    private readonly modelProvider: IModelProvider,
    private readonly workersClient: WorkersClientService,
  ) {}

  /**
   * 生成AppSpec的完整流程
   * 包括规划、执行、校验三个阶段
   */
  async generateAppSpec(input: PlanAgentInput): Promise<AgentResponse<AppSpec>> {
    const requestId = uuidv4();
    const startTime = Date.now();

    try {
      this.logger.log(`[${requestId}] 开始生成AppSpec流程`);
      this.logger.debug(
        `[${requestId}] 输入: tenantId=${input.context.tenantId}, requirementLength=${input.requirement.length}`,
      );

      // ===== Phase 1: PlanAgent 规划阶段 =====
      this.logger.log(`[${requestId}] Phase 1: 调用PlanAgent进行需求规划`);
      const planResult = await this.invokePlanAgent(input, requestId);

      if (!planResult.success || !planResult.data) {
        throw new Error(`PlanAgent失败: ${planResult.error?.message}`);
      }

      this.logger.log(
        `[${requestId}] PlanAgent完成: 模块数=${planResult.data.modules.length}, ` +
          `复杂度=${planResult.data.estimatedComplexity}`,
      );

      // ===== Phase 2: ExecuteAgent 执行阶段 =====
      this.logger.log(`[${requestId}] Phase 2: 调用ExecuteAgent生成AppSpec`);
      const executeInput: ExecuteAgentInput = {
        plan: planResult.data,
        modelProvider: this.modelProvider.getProviderName() as any,
        context: {
          tenantId: input.context.tenantId,
          generationMode: 'full',
        },
      };

      const executeResult = await this.invokeExecuteAgent(executeInput, requestId);

      if (!executeResult.success || !executeResult.data) {
        throw new Error(`ExecuteAgent失败: ${executeResult.error?.message}`);
      }

      this.logger.log(
        `[${requestId}] ExecuteAgent完成: 页面数=${executeResult.data.appSpec.pages.length}, ` +
          `数据模型数=${executeResult.data.appSpec.dataModels.length}`,
      );

      // ===== Phase 3: ValidateAgent 校验阶段 =====
      this.logger.log(`[${requestId}] Phase 3: 调用ValidateAgent进行校验`);
      const validateInput: ValidateAgentInput = {
        appSpec: executeResult.data.appSpec,
        strictMode: true,
      };

      const validateResult = await this.invokeValidateAgent(validateInput, requestId);

      if (!validateResult.success || !validateResult.data) {
        throw new Error(`ValidateAgent失败: ${validateResult.error?.message}`);
      }

      const { errors, warnings, score } = validateResult.data;

      this.logger.log(
        `[${requestId}] ValidateAgent完成: 错误=${errors.length}, ` +
          `警告=${warnings.length}, 质量分=${score}`,
      );

      // 如果有致命错误，拒绝生成
      if (errors.length > 0) {
        this.logger.warn(`[${requestId}] 生成的AppSpec存在${errors.length}个错误，需要修复`);
        // 这里可以选择重试或返回错误
      }

      // ===== Phase 4: 保存到数据库 =====
      this.logger.log(`[${requestId}] Phase 4: 保存到数据库`);
      const appSpec = executeResult.data.appSpec;

      // 保存AppSpec主记录和版本快照
      await this.saveAppSpecToDatabase(
        appSpec,
        input,
        validateResult.data,
        executeResult.data.generationMetadata,
        requestId,
      );

      const totalLatency = Date.now() - startTime;

      this.logger.log(`[${requestId}] AppSpec生成流程完成: 总耗时=${totalLatency}ms`);

      return {
        success: true,
        data: appSpec,
        metadata: {
          requestId,
          agentType: 'execute',
          timestamp: new Date().toISOString(),
          latencyMs: totalLatency,
        },
      };
    } catch (error: any) {
      const totalLatency = Date.now() - startTime;

      this.logger.error(
        `[${requestId}] AppSpec生成流程失败: ${error.message || String(error)}`,
        error?.stack,
      );

      return {
        success: false,
        error: {
          code: 'GENERATION_FAILED',
          message: error.message || String(error),
          details: error,
          recoverable: true,
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

  /**
   * 调用PlanAgent（通过Python Workers HTTP服务）
   */
  private async invokePlanAgent(
    input: PlanAgentInput,
    requestId: string,
  ): Promise<AgentResponse<PlanAgentOutput>> {
    return await this.workersClient.invokePlanAgent(input, requestId);
  }

  /**
   * 调用ExecuteAgent（通过Python Workers HTTP服务）
   */
  private async invokeExecuteAgent(
    input: ExecuteAgentInput,
    requestId: string,
  ): Promise<AgentResponse<ExecuteAgentOutput>> {
    return await this.workersClient.invokeExecuteAgent(input, requestId);
  }

  /**
   * 调用ValidateAgent（通过Python Workers HTTP服务）
   */
  private async invokeValidateAgent(
    input: ValidateAgentInput,
    requestId: string,
  ): Promise<AgentResponse<ValidateAgentOutput>> {
    return await this.workersClient.invokeValidateAgent(input, requestId);
  }

  /**
   * 保存AppSpec到数据库
   * 包括AppSpec主记录和版本快照
   */
  private async saveAppSpecToDatabase(
    appSpec: AppSpec,
    input: PlanAgentInput,
    validationResult: ValidateAgentOutput,
    generationMetadata: Record<string, any>,
    requestId: string,
  ): Promise<void> {
    try {
      const tenantId = input.context.tenantId;
      const userId = input.context.userId;

      // 1. 查找或创建AppSpec主记录
      let appSpecEntity = await this.appSpecRepo.findOne({
        where: { id: appSpec.id },
      });

      if (!appSpecEntity) {
        // 创建新的AppSpec记录
        appSpecEntity = this.appSpecRepo.create({
          id: appSpec.id,
          tenantId,
          userId,
          name: `应用-${new Date().toISOString().split('T')[0]}`,
          description: input.requirement.substring(0, 500),
          currentVersion: appSpec.version,
          status: 'draft',
          requirementText: input.requirement,
          projectType: input.context.projectType,
          metadata: {
            createdBy: 'ai-agent',
            generationRequestId: requestId,
          },
        });

        appSpecEntity = await this.appSpecRepo.save(appSpecEntity);
        this.logger.log(`[${requestId}] 创建AppSpec主记录: id=${appSpecEntity.id}`);
      } else {
        // 更新现有AppSpec
        appSpecEntity.currentVersion = appSpec.version;
        appSpecEntity.updatedAt = new Date();
        await this.appSpecRepo.save(appSpecEntity);
        this.logger.log(`[${requestId}] 更新AppSpec主记录: id=${appSpecEntity.id}`);
      }

      // 2. 保存版本快照
      const versionEntity = this.versionRepo.create({
        id: uuidv4(),
        appId: appSpec.id,
        tenantId,
        version: appSpec.version,
        contentJson: appSpec,
        source: 'exec',
        agentType: 'execute-agent',
        changeDescription: `AI自动生成 - ${new Date().toISOString()}`,
        generationMetadata,
        validationResult: {
          isValid: validationResult.isValid,
          errors: validationResult.errors,
          warnings: validationResult.warnings,
          score: validationResult.score,
        },
        createdBy: 'ai-agent',
      });

      await this.versionRepo.save(versionEntity);

      this.logger.log(
        `[${requestId}] 版本快照已保存: versionId=${versionEntity.id}, ` +
          `appId=${appSpec.id}, version=${appSpec.version}`,
      );
    } catch (error: any) {
      this.logger.error(
        `[${requestId}] 保存到数据库失败: ${error.message || String(error)}`,
        error?.stack,
      );
      // 抛出错误，因为数据库保存失败应该阻止流程继续
      throw new Error(`数据库保存失败: ${error.message || String(error)}`);
    }
  }
}
