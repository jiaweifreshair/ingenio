/**
 * 意图识别API客户端
 * 与后端IntentController交互，提供意图识别和修改功能
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-15
 */

import { post, put, type APIResponse } from '@/lib/api/client';
import type { IntentClassificationResult } from '@/types/intent';
import { RequirementIntent } from '@/types/intent';
import { getToken } from '@/lib/auth/token';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { generateTraceId } from '@/lib/api/trace-id';

/**
 * 意图识别请求接口
 */
interface ClassifyIntentRequest {
  /** 用户输入的需求描述 */
  userRequirement: string;
  /** 是否返回调试信息 */
  includeDebugInfo?: boolean;
  /**
   * 用户预选的复杂度分类（可选）
   *
   * V2.0新增：用户从首页4种分类中选择后传入
   * - SIMPLE: 多端套壳应用 → H5+WebView
   * - MEDIUM: 纯Web应用 → React + Supabase
   * - COMPLEX: 企业级应用 → React + Spring Boot
   * - NEEDS_CONFIRMATION: 原生功能应用 → Kuikly
   */
  complexityHint?: string;
  /**
   * 用户预选的技术栈提示（可选）
   */
  techStackHint?: string;
}

/**
 * 意图修改请求接口
 */
interface UpdateIntentRequest {
  /** 新的意图类型 */
  intent: RequirementIntent;
}

/**
 * 意图识别选项
 */
export interface ClassifyIntentOptions {
  /** 用户预选的复杂度分类（可选） */
  complexityHint?: string;
  /** 用户预选的技术栈提示（可选） */
  techStackHint?: string;
}

/**
 * 调用意图识别API
 *
 * 功能：
 * - 分析用户需求描述
 * - 识别意图类型（克隆/设计/混合）
 * - 提取关键词和参考URL
 * - 返回置信度和AI推理过程
 *
 * @param userInput - 用户输入的需求描述
 * @param options - 可选参数，包括用户预选的复杂度和技术栈
 * @returns 意图识别结果
 * @throws APIError 当请求失败或后端返回错误时
 *
 * @example
 * ```typescript
 * // 基本用法
 * const result = await classifyIntent("我想做一个类似Airbnb的民宿预订网站");
 *
 * // 带预选分类
 * const result = await classifyIntent("做一个待办事项应用", {
 *   complexityHint: "SIMPLE",
 *   techStackHint: "H5+WebView"
 * });
 * ```
 */
export async function classifyIntent(
  userInput: string,
  options?: ClassifyIntentOptions
): Promise<IntentClassificationResult> {
  console.log('[Intent API] 开始意图识别, userInput:', userInput, 'options:', options);

  // 参数验证
  if (!userInput || userInput.trim().length === 0) {
    throw new Error('用户需求描述不能为空');
  }

  if (userInput.trim().length < 10) {
    throw new Error('需求描述过短，请至少输入10个字符以便AI准确识别意图');
  }

  const request: ClassifyIntentRequest = {
    userRequirement: userInput.trim(),
    complexityHint: options?.complexityHint,
    techStackHint: options?.techStackHint,
  };

  try {
    const response = await post<IntentClassificationResult>(
      '/v1/intent/classify',
      request
    );

    console.log('[Intent API] 意图识别响应:', response);

    const isSuccess = determineSuccess(response);
    const result = response.data as IntentClassificationResult | undefined;

    if (!isSuccess || !result) {
      const errorMessage = response.error || (response as { message?: string }).message || '意图识别失败，返回数据为空';
      throw new Error(errorMessage);
    }

    // 验证返回数据的完整性
    if (!result.intent) {
      throw new Error('意图识别结果缺少intent字段');
    }

    if (typeof result.confidence !== 'number' || result.confidence < 0 || result.confidence > 1) {
      throw new Error('意图识别结果的confidence字段无效');
    }

    return result;
  } catch (error) {
    console.error('[Intent API] 意图识别失败:', error);
    throw error;
  }
}

function determineSuccess(response: APIResponse<IntentClassificationResult>): boolean {
  if (typeof response.success === 'boolean') {
    return response.success;
  }

  const code = response.code;
  if (typeof code === 'string') {
    return code === '0000' || code === '0';
  }

  if (typeof code === 'number') {
    return code === 0 || code === 200;
  }

  return false;
}

/**
 * 手动修改意图
 *
 * 功能：
 * - 当AI识别的意图不准确时，用户可手动修改
 * - 更新AppSpec中的意图类型
 * - 触发后续流程的重新路由
 *
 * @param appSpecId - 应用规格ID
 * @param intent - 新的意图类型
 * @throws APIError 当请求失败或后端返回错误时
 *
 * @example
 * ```typescript
 * await updateIntent(
 *   "550e8400-e29b-41d4-a716-446655440000",
 *   RequirementIntent.DESIGN_FROM_SCRATCH
 * );
 * ```
 */
export async function updateIntent(
  appSpecId: string,
  intent: RequirementIntent
): Promise<void> {
  console.log('[Intent API] 开始修改意图, appSpecId:', appSpecId, 'intent:', intent);

  // 参数验证
  if (!appSpecId || appSpecId.trim().length === 0) {
    throw new Error('应用规格ID不能为空');
  }

  if (!intent) {
    throw new Error('意图类型不能为空');
  }

  // 验证意图类型是否有效
  const validIntents = Object.values(RequirementIntent);
  if (!validIntents.includes(intent)) {
    throw new Error(`无效的意图类型: ${intent}，有效值为: ${validIntents.join(', ')}`);
  }

  const request: UpdateIntentRequest = {
    intent,
  };

  try {
    const response = await put<void>(
      `/v1/intent/${appSpecId}/update`,
      request
    );

    console.log('[Intent API] 意图修改成功');

    if (!response.success) {
      throw new Error(response.error || '意图修改失败');
    }
  } catch (error) {
    console.error('[Intent API] 意图修改失败:', error);
    throw error;
  }
}

/**
 * 流式回调接口
 */
export interface StreamCallbacks {
  /** 收到思考过程片段 */
  onThinking: (content: string) => void;
  /** 完成并收到结果 */
  onComplete: (result: IntentClassificationResult) => void;
  /** 发生错误 */
  onError: (error: string) => void;
}

/**
 * 调用意图识别API (流式版本)
 *
 * 功能：
 * - 使用SSE流式获取AI分析过程
 * - 实时返回思考内容 (打字机效果)
 * - 最后返回完整的JSON结果
 *
 * @param userInput - 用户输入的需求描述
 * @param options - 可选参数
 * @param callbacks - 回调函数
 */
export async function classifyIntentStream(
  userInput: string,
  options?: ClassifyIntentOptions,
  callbacks?: StreamCallbacks
): Promise<void> {
  console.log('[Intent API] 开始流式意图识别, userInput:', userInput);

  const request: ClassifyIntentRequest = {
    userRequirement: userInput.trim(),
    complexityHint: options?.complexityHint,
    techStackHint: options?.techStackHint,
  };

  try {
    const baseUrl = getApiBaseUrl();
    const token = getToken();
    
    const response = await fetch(`${baseUrl}/v1/intent/classify/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'x-trace-id': generateTraceId(),
        ...(token ? { 'Authorization': token } : {})
      },
      body: JSON.stringify(request)
    });

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status} ${response.statusText}`);
    }

    if (!response.body) {
      throw new Error('响应体为空');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (!line.trim()) continue;
        
        // 解析SSE格式
        const eventMatch = line.match(/^event:\s*(.+)$/m);
        const dataMatch = line.match(/^data:\s*(.+)$/m);

        if (!dataMatch) continue;

        const eventType = eventMatch ? eventMatch[1].trim() : 'message';
        // data可能包含换行符，需要处理
        const rawData = dataMatch[1];
        
        try {
          if (eventType === 'thinking') {
            // thinking事件的数据是纯文本
            callbacks?.onThinking(rawData);
          } else if (eventType === 'complete') {
            // complete事件的数据是JSON
            const result = JSON.parse(rawData) as IntentClassificationResult;
            callbacks?.onComplete(result);
            return; // 结束流
          } else if (eventType === 'error') {
            callbacks?.onError(rawData);
            return;
          }
        } catch (e) {
          console.error('解析流数据失败:', e, line);
        }
      }
    }
  } catch (error) {
    console.error('[Intent API] 流式识别失败:', error);
    callbacks?.onError(error instanceof Error ? error.message : '未知错误');
  }
}
