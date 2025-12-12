/**
 * 模型提供者抽象接口
 * 统一不同AI模型（OpenAI/Anthropic/本地模型）的调用方式
 */

import { ChatMessage, ModelResponse, ModelConfig } from '@shared/types/agent-io.types';

/**
 * 模型提供者接口
 * 所有AI模型提供商必须实现此接口
 */
export interface IModelProvider {
  /**
   * 聊天对话接口
   * @param messages - 对话消息列表
   * @param config - 可选的模型配置覆盖
   * @returns 模型响应
   */
  chat(messages: ReadonlyArray<ChatMessage>, config?: Partial<ModelConfig>): Promise<ModelResponse>;

  /**
   * 流式聊天接口（可选实现）
   * @param messages - 对话消息列表
   * @param onChunk - 接收到新chunk时的回调函数
   * @param config - 可选的模型配置覆盖
   */
  chatStream?(
    messages: ReadonlyArray<ChatMessage>,
    onChunk: (chunk: string) => void,
    config?: Partial<ModelConfig>,
  ): Promise<ModelResponse>;

  /**
   * 获取当前配置
   */
  getConfig(): ModelConfig;

  /**
   * 健康检查
   * @returns 是否可用
   */
  healthCheck(): Promise<boolean>;

  /**
   * 获取提供商名称
   */
  getProviderName(): string;
}

/**
 * 模型提供者工厂接口
 * 用于创建和管理不同的模型提供者实例
 */
export interface IModelProviderFactory {
  /**
   * 创建模型提供者实例
   * @param config - 模型配置
   */
  create(config: ModelConfig): IModelProvider;

  /**
   * 获取默认提供者
   */
  getDefault(): IModelProvider;

  /**
   * 列出所有可用的提供者
   */
  listAvailable(): string[];
}
