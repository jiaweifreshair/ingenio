/**
 * Prototype API Client - 原型生成API客户端
 * 负责将意图识别/风格选择结果提交到后端,获取OpenLovable沙箱预览
 */

import type { DesignStyle } from '@/types/design-style';
import type { IntentClassificationResult, RequirementIntent } from '@/types/intent';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { getToken } from '@/lib/auth/token';

/** 原型请求超时时间（毫秒），超过即判定依赖服务未响应
 * 参考 open-lovable-cn: E2B沙箱创建通常需要30-40秒，生成代码可能需要60-90秒
 * 设置为120秒（2分钟）以确保有足够时间完成生成
 */
const PROTOTYPE_GENERATION_TIMEOUT_MS = 120_000;

/**
 * 原型生成请求参数
 */
export interface GeneratePrototypeRequest {
  /** 用户需求描述 */
  userRequirement: string;
  /** 选中的设计风格 */
  selectedStyle: DesignStyle;
  /** 选中的模板信息（可选） */
  selectedTemplate?: {
    id?: string;
    name?: string;
    referenceUrl?: string;
  } | null;
  /** 意图识别结果（可选） */
  intentResult?: IntentClassificationResult | null;
}

/**
 * 原型生成响应
 */
export interface GeneratePrototypeResponse {
  success: boolean;
  sandboxUrl: string | null;
  generationTime: number;
  provider?: string;
  sandboxId?: string;
  intentType?: RequirementIntent;
  needsCrawling?: boolean;
  warnings?: string[];
  error?: string;
}

/**
 * SSE 事件类型
 *
 * 注意：后端转发open-lovable-cn的事件类型包括：
 * - progress: 进度消息
 * - sandbox: 沙箱创建完成
 * - stream: 流式代码/思考内容
 * - status: 状态消息
 * - conversation: 对话内容（思考过程）
 * - component: 组件生成完成
 * - app: 应用生成完成
 * - complete: 生成完成
 * - error: 错误信息
 */
export interface SSEProgressEvent {
  type: 'progress' | 'sandbox' | 'thinking' | 'code' | 'files' | 'complete' | 'error' | 'stream' | 'status' | 'conversation' | 'component' | 'app';
  message?: string;
  sandboxId?: string;
  sandboxUrl?: string;
  provider?: string;
  content?: string;
  error?: string;
  /** stream事件的文本内容 */
  text?: string;
  /** 是否为原始内容（stream事件） */
  raw?: boolean;
  /** 进度阶段（progress事件） */
  stage?: string;
  /** component事件的组件名称 */
  name?: string;
  /** component事件的文件路径 */
  path?: string;
  /** component事件的索引 */
  index?: number;
}

/**
 * SSE 流式生成原型预览（带进度回调）
 */
export async function generatePrototypeStream(
  request: GeneratePrototypeRequest,
  onProgress?: (event: SSEProgressEvent) => void
): Promise<GeneratePrototypeResponse> {
  const startTime = Date.now();
  let sandboxUrl: string | null = null;
  let sandboxId: string | null = null;
  let provider: string | undefined;

  try {
    const payload = buildPayload(request);
    const apiBaseUrl = getApiBaseUrl();
    const url = `${apiBaseUrl}/v1/prototype/generate/stream`;
    const token = getToken();

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), PROTOTYPE_GENERATION_TIMEOUT_MS);

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        ...(token ? { Authorization: token } : {}),
      },
      body: JSON.stringify(payload),
      signal: controller.signal,
      credentials: 'include',
    });

    clearTimeout(timeoutId);

    if (!response.ok) {
      let errorText = `HTTP ${response.status}: ${response.statusText}`;

      try {
        const errorBody = await response.json();
        if (typeof errorBody?.message === 'string') {
          errorText = errorBody.message;
        } else if (typeof errorBody?.error === 'string') {
          errorText = errorBody.error;
        }
      } catch {
        // 忽略解析失败，保留默认错误信息
      }

      throw new Error(errorText);
    }

    if (!response.body) {
      throw new Error('Response body is null');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    // SSE解析缓冲区
    let buffer = '';
    let currentData = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      // 按行分割，支持各种换行符
      const lines = buffer.split(/\r\n|\r|\n/);
      // 保留最后一个可能不完整的行
      buffer = lines.pop() || '';

      for (const line of lines) {
        // 忽略注释
        if (line.startsWith(':')) continue;

        // 空行表示事件结束，处理累积的数据
        if (line.trim() === '') {
          if (currentData) {
            try {
              const event: SSEProgressEvent = JSON.parse(currentData);

              // 提取沙箱信息
              if (event.type === 'sandbox') {
                sandboxUrl = event.sandboxUrl || null;
                sandboxId = event.sandboxId || null;
                provider = event.provider;
              }

              // 调用进度回调
              if (onProgress) {
                onProgress(event);
              }

              // 处理错误事件
              if (event.type === 'error') {
                throw new Error(event.error || '原型生成失败');
              }
            } catch (parseError) {
              // 尝试降级处理：处理因后端未发送空行导致的多行JSON合并情况
              // 例如：data: {"a":1}\ndata: {"b":2} -> currentData: {"a":1}\n{"b":2}
              const subLines = currentData.split('\n');
              let recovered = false;

              if (subLines.length > 1) {
                try {
                  for (const subLine of subLines) {
                    if (!subLine.trim()) continue;

                    const event: SSEProgressEvent = JSON.parse(subLine);

                    // 提取沙箱信息
                    if (event.type === 'sandbox') {
                      sandboxUrl = event.sandboxUrl || null;
                      sandboxId = event.sandboxId || null;
                      provider = event.provider;
                    }

                    // 调用进度回调
                    if (onProgress) {
                      onProgress(event);
                    }

                    // 处理错误事件
                    if (event.type === 'error') {
                      throw new Error(event.error || '原型生成失败');
                    }
                  }
                  recovered = true;
                } catch {
                  // 降级处理也失败，保持 recovered = false
                }
              }

              if (!recovered) {
                console.warn('[Prototype SSE] 解析事件失败:', currentData, parseError);
              }
            }
            // 重置数据缓冲区
            currentData = '';
          }
          continue;
        }

        // 累积 data 字段内容
        if (line.startsWith('data:')) {
          if (currentData) {
            currentData += '\n';
          }
          currentData += line.replace(/^data:\s?/, '');
        }
      }
    }



    // 如果流结束但没有获取到沙箱URL，视为失败
    if (!sandboxUrl) {
      return {
        success: false,
        sandboxUrl: null,
        generationTime: Date.now() - startTime,
        error: '未收到沙箱环境信息，生成可能已中断或后台服务异常',
      };
    }

    return {
      success: true,
      sandboxUrl,
      sandboxId: sandboxId || undefined,
      provider,
      generationTime: Date.now() - startTime,
      intentType: request.intentResult?.intent,
      needsCrawling: false,
    };
  } catch (error) {
    console.error('[Prototype SSE] 流式生成失败', error);

    if (error instanceof DOMException && error.name === 'AbortError') {
      return {
        success: false,
        sandboxUrl: null,
        generationTime: Date.now() - startTime,
        error: '原型生成超时（2分钟）。生成过程较慢，请稍后重试',
        warnings: ['原型生成耗时较长，已自动取消请求'],
      };
    }

    return {
      success: false,
      sandboxUrl: null,
      generationTime: Date.now() - startTime,
      error: error instanceof Error ? error.message : '原型生成失败',
    };
  }
}

/**
 * 生成原型预览（使用 SSE 流式接口）
 */
export async function generatePrototype(
  request: GeneratePrototypeRequest
): Promise<GeneratePrototypeResponse> {
  // 默认使用 SSE 流式生成，不传递进度回调
  return generatePrototypeStream(request);
}

/**
 * 修改原型（占位实现）
 */
export async function modifyPrototype(
  _sandboxId: string,
  _modificationRequest: string
): Promise<GeneratePrototypeResponse> {
  console.warn('[Prototype API] modifyPrototype 功能尚未实现');

  return {
    success: false,
    sandboxUrl: null,
    generationTime: 0,
    error: '原型修改功能将在后续版本实现',
  };
}

/**
 * 确认原型设计（占位实现）
 */
export async function confirmPrototypeDesign(sandboxId: string): Promise<{
  success: boolean;
  message: string;
}> {
  console.log('[Prototype API] 确认原型设计:', sandboxId);
  return {
    success: true,
    message: '设计已确认，即将进入后端生成阶段',
  };
}

function buildPayload(request: GeneratePrototypeRequest) {
  const referenceUrls = request.intentResult?.referenceUrls?.filter(Boolean);

  return {
    userRequirement: request.userRequirement.trim(),
    // 确保枚举被序列化为字符串
    designStyle: request.selectedStyle as string,
    intentType: request.intentResult?.intent
      ? String(request.intentResult.intent)
      : undefined,
    referenceUrls,
    customizationRequirement: request.intentResult?.customizationRequirement,
    selectedTemplateId: request.selectedTemplate?.id ?? null,
    selectedTemplateName: request.selectedTemplate?.name ?? null,
    selectedTemplateReferenceUrl: request.selectedTemplate?.referenceUrl ?? null,
  };
}

/**
 * 将 AI 生成的前端代码应用到指定 Sandbox
 *
 * 说明：
 * - 依赖后端 `/v1/openlovable/apply` 代理 open-lovable-cn 的 apply 接口
 * - response 应包含 `<file path="...">...</file>` 格式的文件片段
 *
 * @param sandboxId 沙箱ID
 * @param response AI输出的完整代码文本
 * @returns 写入文件数量与提示信息
 */
export async function applyAiCodeToSandbox(
  sandboxId: string,
  response: string
): Promise<{ filesWritten: number; message?: string }> {
  const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';
  const url = `${apiBaseUrl}/v1/openlovable/apply`;

  const applyResponse = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ sandboxId, response }),
  });

  if (!applyResponse.ok) {
    let errorText = `应用代码失败: HTTP ${applyResponse.status}`;
    try {
      const errorBody = await applyResponse.json();
      if (typeof errorBody?.message === 'string') {
        errorText = errorBody.message;
      } else if (typeof errorBody?.error === 'string') {
        errorText = errorBody.error;
      }
    } catch {
      // 忽略解析失败，保留默认错误信息
    }
    throw new Error(errorText);
  }

  const result = await applyResponse.json();
  const data = result?.data ?? result;

  return {
    filesWritten: Number(data?.filesWritten ?? 0),
    message: typeof data?.message === 'string' ? data.message : undefined,
  };
}

/**
 * 重启Vite开发服务器
 * @param sandboxId 沙箱ID
 */
export async function restartVite(sandboxId: string): Promise<void> {
  const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';
  const url = `${apiBaseUrl}/v1/openlovable/restart-vite`;

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ sandboxId }),
  });

  if (!response.ok) {
    throw new Error(`Failed to restart Vite: ${response.statusText}`);
  }
}
