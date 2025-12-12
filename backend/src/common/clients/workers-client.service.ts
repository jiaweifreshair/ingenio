/**
 * Workers HTTP客户端服务
 * 负责与Python Workers服务进行HTTP通信
 */

import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import axios, { AxiosInstance, AxiosError } from 'axios';
import {
  PlanAgentInput,
  PlanAgentOutput,
  ExecuteAgentInput,
  ExecuteAgentOutput,
  ValidateAgentInput,
  ValidateAgentOutput,
  AgentResponse,
} from '@shared/types/agent-io.types';

/**
 * Workers服务配置
 */
interface WorkersConfig {
  baseUrl: string;
  timeout: number;
}

/**
 * Workers客户端服务
 */
@Injectable()
export class WorkersClientService {
  private readonly logger = new Logger(WorkersClientService.name);
  private readonly httpClient: AxiosInstance;
  private readonly config: WorkersConfig;

  constructor(private readonly configService: ConfigService) {
    // 从环境变量读取配置
    this.config = {
      baseUrl: this.configService.get<string>('WORKERS_BASE_URL', 'http://localhost:8000'),
      timeout: this.configService.get<number>('WORKERS_TIMEOUT', 30000),
    };

    // 创建axios实例
    this.httpClient = axios.create({
      baseURL: this.config.baseUrl,
      timeout: this.config.timeout,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    // 请求拦截器
    this.httpClient.interceptors.request.use(
      (config) => {
        this.logger.debug(`发起请求: ${config.method?.toUpperCase()} ${config.url}`);
        return config;
      },
      (error) => {
        this.logger.error('请求拦截器错误', error);
        return Promise.reject(error);
      },
    );

    // 响应拦截器
    this.httpClient.interceptors.response.use(
      (response) => {
        this.logger.debug(
          `收到响应: ${response.config.method?.toUpperCase()} ${response.config.url} - ${response.status}`,
        );
        return response;
      },
      (error: AxiosError) => {
        this.logger.error(
          `HTTP错误: ${error.config?.method?.toUpperCase()} ${error.config?.url} - ${error.message}`,
        );
        return Promise.reject(error);
      },
    );

    this.logger.log(`WorkersClient初始化完成: baseUrl=${this.config.baseUrl}`);
  }

  /**
   * 调用PlanAgent
   */
  async invokePlanAgent(
    input: PlanAgentInput,
    requestId: string,
  ): Promise<AgentResponse<PlanAgentOutput>> {
    try {
      this.logger.log(`[${requestId}] 调用PlanAgent: tenantId=${input.context.tenantId}`);

      const response = await this.httpClient.post<{
        success: boolean;
        data?: PlanAgentOutput;
        error?: string;
        metadata: Record<string, any>;
      }>('/api/agents/plan', {
        requirement: input.requirement,
        tenant_id: input.context.tenantId,
        user_id: input.context.userId,
        project_type: input.context.projectType,
        existing_models: input.context.existingModels,
        constraints: input.context.constraints,
      });

      if (!response.data.success || !response.data.data) {
        const errorMsg = response.data.error || '未知错误';
        this.logger.error(`[${requestId}] PlanAgent失败: ${errorMsg}`);

        return {
          success: false,
          error: {
            code: 'PLAN_AGENT_FAILED',
            message: errorMsg,
            recoverable: true,
          },
          metadata: {
            requestId,
            agentType: 'plan',
            timestamp: new Date().toISOString(),
            latencyMs: response.data.metadata.latencyMs || 0,
          },
        };
      }

      this.logger.log(
        `[${requestId}] PlanAgent成功: modules=${response.data.data.modules.length}`,
      );

      return {
        success: true,
        data: response.data.data,
        metadata: {
          requestId,
          agentType: 'plan',
          timestamp: new Date().toISOString(),
          latencyMs: response.data.metadata.latencyMs || 0,
        },
      };
    } catch (error) {
      return this.handleError(error, requestId, 'plan');
    }
  }

  /**
   * 调用ExecuteAgent
   */
  async invokeExecuteAgent(
    input: ExecuteAgentInput,
    requestId: string,
  ): Promise<AgentResponse<ExecuteAgentOutput>> {
    try {
      this.logger.log(
        `[${requestId}] 调用ExecuteAgent: mode=${input.context.generationMode}`,
      );

      const response = await this.httpClient.post<{
        success: boolean;
        data?: ExecuteAgentOutput;
        error?: string;
        metadata: Record<string, any>;
      }>('/api/agents/execute', {
        plan: input.plan,
        tenant_id: input.context.tenantId,
        user_id: 'default-user', // 使用默认用户ID
        template_id: input.context.templateId,
        generation_mode: input.context.generationMode,
      });

      if (!response.data.success || !response.data.data) {
        const errorMsg = response.data.error || '未知错误';
        this.logger.error(`[${requestId}] ExecuteAgent失败: ${errorMsg}`);

        return {
          success: false,
          error: {
            code: 'EXECUTE_AGENT_FAILED',
            message: errorMsg,
            recoverable: true,
          },
          metadata: {
            requestId,
            agentType: 'execute',
            timestamp: new Date().toISOString(),
            latencyMs: response.data.metadata.latencyMs || 0,
          },
        };
      }

      this.logger.log(
        `[${requestId}] ExecuteAgent成功: appSpecId=${response.data.data.appSpec.id}`,
      );

      return {
        success: true,
        data: response.data.data,
        metadata: {
          requestId,
          agentType: 'execute',
          timestamp: new Date().toISOString(),
          latencyMs: response.data.metadata.latencyMs || 0,
        },
      };
    } catch (error) {
      return this.handleError(error, requestId, 'execute');
    }
  }

  /**
   * 调用ValidateAgent
   */
  async invokeValidateAgent(
    input: ValidateAgentInput,
    requestId: string,
  ): Promise<AgentResponse<ValidateAgentOutput>> {
    try {
      this.logger.log(`[${requestId}] 调用ValidateAgent: strictMode=${input.strictMode}`);

      const response = await this.httpClient.post<{
        success: boolean;
        data?: ValidateAgentOutput;
        error?: string;
      }>('/api/agents/validate', {
        app_spec: input.appSpec,
        strict_mode: input.strictMode,
      });

      if (!response.data.success || !response.data.data) {
        const errorMsg = response.data.error || '未知错误';
        this.logger.error(`[${requestId}] ValidateAgent失败: ${errorMsg}`);

        return {
          success: false,
          error: {
            code: 'VALIDATE_AGENT_FAILED',
            message: errorMsg,
            recoverable: true,
          },
          metadata: {
            requestId,
            agentType: 'validate',
            timestamp: new Date().toISOString(),
            latencyMs: 0,
          },
        };
      }

      this.logger.log(
        `[${requestId}] ValidateAgent成功: isValid=${response.data.data.isValid}, score=${response.data.data.score}`,
      );

      return {
        success: true,
        data: response.data.data,
        metadata: {
          requestId,
          agentType: 'validate',
          timestamp: new Date().toISOString(),
          latencyMs: 0,
        },
      };
    } catch (error) {
      return this.handleError(error, requestId, 'validate');
    }
  }

  /**
   * 健康检查
   */
  async healthCheck(): Promise<boolean> {
    try {
      const response = await this.httpClient.get<{
        status: string;
        version: string;
        model_provider: string;
      }>('/health');

      const isHealthy = response.data.status === 'healthy';

      this.logger.log(
        `Workers健康检查: status=${response.data.status}, version=${response.data.version}`,
      );

      return isHealthy;
    } catch (error) {
      this.logger.error('Workers健康检查失败', error);
      return false;
    }
  }

  /**
   * 统一错误处理
   */
  private handleError(
    error: any,
    requestId: string,
    agentType: 'plan' | 'execute' | 'validate',
  ): AgentResponse<any> {
    if (axios.isAxiosError(error)) {
      const axiosError = error as AxiosError;

      // 超时错误
      if (axiosError.code === 'ECONNABORTED') {
        this.logger.error(`[${requestId}] ${agentType}Agent超时`);
        return {
          success: false,
          error: {
            code: 'TIMEOUT',
            message: `${agentType}Agent调用超时`,
            recoverable: true,
          },
          metadata: {
            requestId,
            agentType,
            timestamp: new Date().toISOString(),
            latencyMs: this.config.timeout,
          },
        };
      }

      // 网络错误
      if (axiosError.code === 'ECONNREFUSED') {
        this.logger.error(`[${requestId}] Workers服务不可用`);
        return {
          success: false,
          error: {
            code: 'SERVICE_UNAVAILABLE',
            message: 'Workers服务不可用，请检查服务是否启动',
            recoverable: true,
          },
          metadata: {
            requestId,
            agentType,
            timestamp: new Date().toISOString(),
            latencyMs: 0,
          },
        };
      }

      // HTTP错误
      if (axiosError.response) {
        const status = axiosError.response.status;
        const data = axiosError.response.data as any;

        this.logger.error(
          `[${requestId}] HTTP ${status}错误: ${JSON.stringify(data)}`,
        );

        return {
          success: false,
          error: {
            code: `HTTP_${status}`,
            message: data?.detail || data?.message || `HTTP ${status} 错误`,
            recoverable: status >= 500,
          },
          metadata: {
            requestId,
            agentType,
            timestamp: new Date().toISOString(),
            latencyMs: 0,
          },
        };
      }
    }

    // 其他错误
    this.logger.error(`[${requestId}] 未知错误`, error);
    return {
      success: false,
      error: {
        code: 'UNKNOWN_ERROR',
        message: error.message || '未知错误',
        recoverable: false,
      },
      metadata: {
        requestId,
        agentType,
        timestamp: new Date().toISOString(),
        latencyMs: 0,
      },
    };
  }
}
