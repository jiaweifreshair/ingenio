/**
 * Uniaix API客户端
 * 支持多种国内AI模型：Qwen、DeepSeek、Kimi、MiniMax
 * API文档: https://www.uniaix.com
 */

/**
 * Uniaix支持的模型列表
 */
export const UNIAIX_MODELS = {
  // 阿里通义千问
  QWEN_TURBO: 'qwen-turbo',
  QWEN_PLUS: 'qwen-plus',
  QWEN_MAX: 'qwen-max',

  // DeepSeek
  DEEPSEEK_CHAT: 'deepseek-chat',
  DEEPSEEK_CODER: 'deepseek-coder',

  // 月之暗面Kimi
  KIMI: 'moonshot-v1-8k',
  KIMI_32K: 'moonshot-v1-32k',
  KIMI_128K: 'moonshot-v1-128k',

  // MiniMax
  MINIMAX_ABAB: 'abab6-chat',
  MINIMAX_ABAB_PRO: 'abab6.5-chat',
} as const;

export type UniaixModel = typeof UNIAIX_MODELS[keyof typeof UNIAIX_MODELS];

/**
 * 模型配置信息
 */
export interface ModelConfig {
  id: UniaixModel;
  name: string;
  provider: string;
  contextWindow: number;
  description: string;
  recommended: boolean;
}

/**
 * 所有模型的配置信息
 */
export const MODEL_CONFIGS: ModelConfig[] = [
  {
    id: UNIAIX_MODELS.QWEN_MAX,
    name: 'Qwen Max',
    provider: '阿里云',
    contextWindow: 8000,
    description: '通义千问旗舰模型,推理能力强,适合复杂任务',
    recommended: true,
  },
  {
    id: UNIAIX_MODELS.QWEN_PLUS,
    name: 'Qwen Plus',
    provider: '阿里云',
    contextWindow: 8000,
    description: '通义千问增强模型,性价比高',
    recommended: false,
  },
  {
    id: UNIAIX_MODELS.QWEN_TURBO,
    name: 'Qwen Turbo',
    provider: '阿里云',
    contextWindow: 8000,
    description: '通义千问快速模型,响应速度快',
    recommended: false,
  },
  {
    id: UNIAIX_MODELS.DEEPSEEK_CHAT,
    name: 'DeepSeek Chat',
    provider: 'DeepSeek',
    contextWindow: 32000,
    description: 'DeepSeek对话模型,支持长上下文',
    recommended: true,
  },
  {
    id: UNIAIX_MODELS.DEEPSEEK_CODER,
    name: 'DeepSeek Coder',
    provider: 'DeepSeek',
    contextWindow: 16000,
    description: 'DeepSeek代码模型,专注代码生成',
    recommended: true,
  },
  {
    id: UNIAIX_MODELS.KIMI_128K,
    name: 'Kimi 128K',
    provider: '月之暗面',
    contextWindow: 128000,
    description: 'Kimi超长上下文模型,适合大规模需求分析',
    recommended: false,
  },
  {
    id: UNIAIX_MODELS.KIMI_32K,
    name: 'Kimi 32K',
    provider: '月之暗面',
    contextWindow: 32000,
    description: 'Kimi长上下文模型',
    recommended: false,
  },
  {
    id: UNIAIX_MODELS.MINIMAX_ABAB_PRO,
    name: 'MiniMax Pro',
    provider: 'MiniMax',
    contextWindow: 8000,
    description: 'MiniMax增强模型,中文理解能力强',
    recommended: false,
  },
];

/**
 * Uniaix API请求消息
 */
export interface UniaixMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

/**
 * Uniaix API请求参数
 */
export interface UniaixChatRequest {
  model: UniaixModel;
  messages: UniaixMessage[];
  temperature?: number;
  max_tokens?: number;
  top_p?: number;
  stream?: boolean;
}

/**
 * Uniaix API响应
 */
export interface UniaixChatResponse {
  id: string;
  object: string;
  created: number;
  model: string;
  choices: Array<{
    index: number;
    message: {
      role: string;
      content: string;
    };
    finish_reason: string;
  }>;
  usage: {
    prompt_tokens: number;
    completion_tokens: number;
    total_tokens: number;
  };
}

/**
 * Uniaix API错误
 */
export class UniaixError extends Error {
  constructor(
    message: string,
    public statusCode?: number,
    public errorType?: string
  ) {
    super(message);
    this.name = 'UniaixError';
  }
}

/**
 * Uniaix API客户端配置
 */
export interface UniaixClientConfig {
  apiKey: string;
  baseUrl?: string;
  timeout?: number;
}

/**
 * Uniaix API客户端
 */
export class UniaixClient {
  private apiKey: string;
  private baseUrl: string;
  private timeout: number;

  constructor(config: UniaixClientConfig) {
    this.apiKey = config.apiKey;
    this.baseUrl = config.baseUrl || 'https://api.uniaix.com/v1';
    this.timeout = config.timeout || 60000; // 60秒超时
  }

  /**
   * 调用Chat Completion API
   */
  async chatCompletion(request: UniaixChatRequest): Promise<UniaixChatResponse> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.timeout);

    try {
      const response = await fetch(`${this.baseUrl}/chat/completions`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.apiKey}`,
        },
        body: JSON.stringify(request),
        signal: controller.signal,
      });

      if (!response.ok) {
        const error = await response.json().catch(() => ({ error: { message: 'Unknown error' } }));
        throw new UniaixError(
          error.error?.message || `API request failed with status ${response.status}`,
          response.status,
          error.error?.type
        );
      }

      const data = await response.json();
      return data as UniaixChatResponse;

    } catch (error) {
      if (error instanceof UniaixError) {
        throw error;
      }

      if (error instanceof Error) {
        if (error.name === 'AbortError') {
          throw new UniaixError('Request timeout', 408, 'timeout');
        }
        throw new UniaixError(error.message);
      }

      throw new UniaixError('Unknown error occurred');
    } finally {
      clearTimeout(timeoutId);
    }
  }

  /**
   * 简单文本生成
   */
  async generate(
    prompt: string,
    model: UniaixModel = UNIAIX_MODELS.QWEN_MAX,
    options: {
      systemPrompt?: string;
      temperature?: number;
      maxTokens?: number;
    } = {}
  ): Promise<string> {
    const messages: UniaixMessage[] = [];

    if (options.systemPrompt) {
      messages.push({
        role: 'system',
        content: options.systemPrompt,
      });
    }

    messages.push({
      role: 'user',
      content: prompt,
    });

    const response = await this.chatCompletion({
      model,
      messages,
      temperature: options.temperature ?? 0.7,
      max_tokens: options.maxTokens ?? 2000,
    });

    const choice = response.choices[0];
    if (!choice || !choice.message) {
      throw new UniaixError('No response from model');
    }

    return choice.message.content;
  }
}

/**
 * 获取推荐的模型
 */
export function getRecommendedModels(): ModelConfig[] {
  return MODEL_CONFIGS.filter(config => config.recommended);
}

/**
 * 根据ID获取模型配置
 */
export function getModelConfig(modelId: UniaixModel): ModelConfig | undefined {
  return MODEL_CONFIGS.find(config => config.id === modelId);
}

/**
 * 创建默认的Uniaix客户端
 * 使用环境变量中的API Key
 */
export function createUniaixClient(): UniaixClient {
  const apiKey = process.env.NEXT_PUBLIC_UNIAIX_API_KEY?.trim();

  if (!apiKey) {
    throw new UniaixError('未配置 NEXT_PUBLIC_UNIAIX_API_KEY（禁止在代码中硬编码 API Key）');
  }

  return new UniaixClient({
    apiKey,
  });
}
