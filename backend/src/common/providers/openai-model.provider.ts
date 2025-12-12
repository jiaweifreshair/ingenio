/**
 * OpenAI模型提供者实现
 */

import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import axios, { AxiosInstance } from 'axios';
import { IModelProvider } from '../interfaces/model-provider.interface';
import { ChatMessage, ModelResponse, ModelConfig } from '@shared/types/agent-io.types';

/**
 * OpenAI模型提供者
 * 实现与OpenAI API的集成
 */
@Injectable()
export class OpenAIModelProvider implements IModelProvider {
  private readonly logger = new Logger(OpenAIModelProvider.name);
  private readonly client: AxiosInstance;
  private readonly config: ModelConfig;

  constructor(
    config: ModelConfig,
    private readonly configService?: ConfigService,
  ) {
    // 初始化配置，优先使用传入的config，其次使用环境变量
    this.config = {
      provider: 'openai',
      model: config.model || this.configService?.get('OPENAI_MODEL') || 'gpt-4',
      apiKey: config.apiKey || this.configService?.get('OPENAI_API_KEY'),
      baseUrl: config.baseUrl || 'https://api.openai.com/v1',
      temperature: config.temperature ?? 0.7,
      maxTokens: config.maxTokens ?? 4096,
      timeout: config.timeout ?? 60000,
    };

    // 创建axios实例
    this.client = axios.create({
      baseURL: this.config.baseUrl,
      timeout: this.config.timeout,
      headers: {
        'Authorization': `Bearer ${this.config.apiKey}`,
        'Content-Type': 'application/json',
      },
    });

    this.logger.log(`OpenAI模型提供者初始化完成: ${this.config.model}`);
  }

  /**
   * 聊天对话接口实现
   */
  async chat(
    messages: ReadonlyArray<ChatMessage>,
    config?: Partial<ModelConfig>,
  ): Promise<ModelResponse> {
    try {
      const startTime = Date.now();

      // 合并配置
      const requestConfig = {
        ...this.config,
        ...config,
      };

      this.logger.debug(`发起OpenAI请求: 模型=${requestConfig.model}, 消息数=${messages.length}`);

      // 调用OpenAI API
      const response = await this.client.post('/chat/completions', {
        model: requestConfig.model,
        messages: messages.map((msg) => ({
          role: msg.role,
          content: msg.content,
        })),
        temperature: requestConfig.temperature,
        max_tokens: requestConfig.maxTokens,
      });

      const latency = Date.now() - startTime;

      // 解析响应
      const choice = response.data.choices[0];
      const usage = response.data.usage;

      this.logger.log(
        `OpenAI请求成功: 耗时=${latency}ms, ` +
          `tokens=${usage.total_tokens} (prompt=${usage.prompt_tokens}, completion=${usage.completion_tokens})`,
      );

      return {
        content: choice.message.content,
        usage: {
          promptTokens: usage.prompt_tokens,
          completionTokens: usage.completion_tokens,
          totalTokens: usage.total_tokens,
        },
        finishReason: this.mapFinishReason(choice.finish_reason),
      };
    } catch (error: any) {
      this.logger.error(`OpenAI请求失败: ${error.message || String(error)}`, error?.stack);

      // 重新抛出错误，附带更多上下文信息
      throw new Error(
        `OpenAI API调用失败: ${error.response?.data?.error?.message || error.message || String(error)}`,
      );
    }
  }

  /**
   * 流式聊天接口实现（简化版，实际生产环境需要使用SSE）
   */
  async chatStream(
    messages: ReadonlyArray<ChatMessage>,
    onChunk: (chunk: string) => void,
    config?: Partial<ModelConfig>,
  ): Promise<ModelResponse> {
    // 简化实现：直接调用非流式接口，然后一次性返回
    // 生产环境应该使用Server-Sent Events (SSE)
    this.logger.warn('流式接口当前使用简化实现，生产环境请使用SSE');

    const response = await this.chat(messages, config);
    onChunk(response.content);
    return response;
  }

  /**
   * 获取当前配置
   */
  getConfig(): ModelConfig {
    // 返回配置的副本，隐藏API Key
    return {
      ...this.config,
      apiKey: '***HIDDEN***',
    };
  }

  /**
   * 健康检查
   */
  async healthCheck(): Promise<boolean> {
    try {
      // 发送一个简单的请求来检查API是否可用
      const response = await this.client.get('/models');
      return response.status === 200;
    } catch (error: any) {
      this.logger.error(`OpenAI健康检查失败: ${error.message || String(error)}`);
      return false;
    }
  }

  /**
   * 获取提供商名称
   */
  getProviderName(): string {
    return 'openai';
  }

  /**
   * 映射finish_reason到标准格式
   */
  private mapFinishReason(reason: string): 'stop' | 'length' | 'error' {
    switch (reason) {
      case 'stop':
        return 'stop';
      case 'length':
      case 'max_tokens':
        return 'length';
      default:
        return 'error';
    }
  }
}
