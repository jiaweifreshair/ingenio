/**
 * Prototype API Client - 原型生成API客户端
 * 负责将意图识别/风格选择结果提交到后端,获取OpenLovable沙箱预览
 */

import type { DesignStyle } from '@/types/design-style';
import type { IntentClassificationResult, RequirementIntent } from '@/types/intent';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { getToken } from '@/lib/auth/token';
import { generateTraceId } from '@/lib/api/trace-id';

/** 原型请求超时时间（毫秒），超过即判定依赖服务未响应
 * 参考 open-lovable-cn: E2B沙箱创建通常需要30-40秒，生成代码可能需要60-90秒
 * 设置为180秒（3分钟）以确保有足够时间完成生成，避免network error
 */
const PROTOTYPE_GENERATION_TIMEOUT_MS = 180_000;

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
  /** 
   * Step 6 生成的技术蓝图 Markdown（可选）
   * M1: 用于透传给 OpenLovable，约束前端生成遵循蓝图设计
   */
  blueprintMarkdown?: string | null;
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
    const url = `${apiBaseUrl}/v1/openlovable/generate/stream`;
    const token = getToken();

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), PROTOTYPE_GENERATION_TIMEOUT_MS);

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'x-trace-id': generateTraceId(),
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
 * 修改原型请求参数
 */
export interface ModifyPrototypeRequest {
  /** 沙箱ID */
  sandboxId: string;
  /** 用户的修改请求描述 */
  modificationRequest: string;
  /** 原始需求（可选，用于上下文） */
  originalRequirement?: string;
  /** 设计风格（可选） */
  designStyle?: string;
}

/**
 * 修改原型 - 使用isEdit模式让AI基于现有代码进行修改
 *
 * 功能说明：
 * - 调用 /v1/openlovable/generate/stream 接口，传入 isEdit=true
 * - AI 会基于现有沙箱代码进行修改，而不是从零生成
 * - 修改完成后自动 apply 到沙箱
 *
 * @param request 修改请求参数
 * @param onProgress 进度回调
 */
export async function modifyPrototypeStream(
  request: ModifyPrototypeRequest,
  onProgress?: (event: SSEProgressEvent) => void
): Promise<GeneratePrototypeResponse> {
  const startTime = Date.now();
  const { sandboxId, modificationRequest, originalRequirement, designStyle } = request;

  try {
    const apiBaseUrl = getApiBaseUrl();
    const url = `${apiBaseUrl}/v1/openlovable/generate/stream`;
    const token = getToken();

    // 构建修改请求的 prompt
    const modifyPrompt = buildModifyPrompt(modificationRequest, originalRequirement);

    const payload = {
      sandboxId,
      prompt: modifyPrompt,
      isEdit: true, // 关键：使用编辑模式
      designStyle: designStyle || undefined,
      // 不传 fastPreview，让 AI 有足够时间理解现有代码
    };

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), PROTOTYPE_GENERATION_TIMEOUT_MS);

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'x-trace-id': generateTraceId(),
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
        // 忽略解析失败
      }
      throw new Error(errorText);
    }

    if (!response.body) {
      throw new Error('Response body is null');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let currentData = '';
    let sandboxUrl: string | null = null;
    let provider: string | undefined;

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split(/\r\n|\r|\n/);
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith(':')) continue;

        if (line.trim() === '') {
          if (currentData) {
            try {
              const event: SSEProgressEvent = JSON.parse(currentData);

              if (event.type === 'sandbox') {
                sandboxUrl = event.sandboxUrl || null;
                provider = event.provider;
              }

              if (onProgress) {
                onProgress(event);
              }

              if (event.type === 'error') {
                throw new Error(event.error || '原型修改失败');
              }
            } catch (parseError) {
              // 降级处理多行JSON
              const subLines = currentData.split('\n');
              if (subLines.length > 1) {
                try {
                  for (const subLine of subLines) {
                    if (!subLine.trim()) continue;
                    const event: SSEProgressEvent = JSON.parse(subLine);
                    if (event.type === 'sandbox') {
                      sandboxUrl = event.sandboxUrl || null;
                      provider = event.provider;
                    }
                    if (onProgress) onProgress(event);
                    if (event.type === 'error') {
                      throw new Error(event.error || '原型修改失败');
                    }
                  }
                } catch {
                  console.warn('[Prototype SSE] 修改流解析失败:', currentData, parseError);
                }
              }
            }
            currentData = '';
          }
          continue;
        }

        if (line.startsWith('data:')) {
          if (currentData) currentData += '\n';
          currentData += line.replace(/^data:\s?/, '');
        }
      }
    }

    // 修改成功，返回原沙箱URL（代码已apply）
    return {
      success: true,
      sandboxUrl: sandboxUrl || `https://${sandboxId}.e2b.dev`,
      sandboxId,
      provider,
      generationTime: Date.now() - startTime,
    };
  } catch (error) {
    console.error('[Prototype SSE] 修改流失败', error);

    if (error instanceof DOMException && error.name === 'AbortError') {
      return {
        success: false,
        sandboxUrl: null,
        generationTime: Date.now() - startTime,
        error: '原型修改超时（3分钟）。修改内容可能较复杂，请稍后重试',
      };
    }

    return {
      success: false,
      sandboxUrl: null,
      generationTime: Date.now() - startTime,
      error: error instanceof Error ? error.message : '原型修改失败',
    };
  }
}

/**
 * 构建修改提示词
 *
 * @param modificationRequest 用户的修改请求
 * @param originalRequirement 原始需求（可选）
 */
function buildModifyPrompt(modificationRequest: string, originalRequirement?: string): string {
  let prompt = `请根据以下用户反馈修改现有代码：

## 用户修改请求
${modificationRequest}
`;

  if (originalRequirement) {
    prompt += `
## 原始需求（参考）
${originalRequirement}
`;
  }

  prompt += `
## 修改要求
1. 仅修改与用户请求相关的部分，保持其他代码不变
2. 确保修改后的代码可以正常运行
3. 保持代码风格一致
4. 如果用户反馈涉及错误修复，请彻底解决问题
`;

  return prompt;
}

/**
 * 修改原型（简化版，不带进度回调）
 */
export async function modifyPrototype(
  sandboxId: string,
  modificationRequest: string,
  originalRequirement?: string
): Promise<GeneratePrototypeResponse> {
  return modifyPrototypeStream({
    sandboxId,
    modificationRequest,
    originalRequirement,
  });
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
    // M1: Blueprint 透传给后端 OpenLovable
    blueprintMarkdown: request.blueprintMarkdown ?? null,
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
      'x-trace-id': generateTraceId(),
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
      'x-trace-id': generateTraceId(),
    },
    body: JSON.stringify({ sandboxId }),
  });

  if (!response.ok) {
    throw new Error(`Failed to restart Vite: ${response.statusText}`);
  }
}

/**
 * 智能修复沙箱预览白屏（兜底）
 *
 * 说明：
 * - 该接口用于“iframe 白屏但无显式报错”的场景；
 * - 后端会尝试修复入口挂载（src/main.*）并在必要时生成兜底 App.jsx，确保预览可见。
 */
export type SmartFixSandboxResult = {
  fixed: boolean;
  filesCreated: string[];
  filesUpdated: string[];
  diagnostics?: Record<string, unknown>;
  message?: string;
  timestamp?: string;
};

export async function smartFixPrototypeSandbox(sandboxId: string): Promise<SmartFixSandboxResult> {
  const apiBaseUrl = getApiBaseUrl();
  const url = `${apiBaseUrl}/v1/openlovable/sandbox/smart-fix`;
  const token = getToken();

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-trace-id': generateTraceId(),
      ...(token ? { Authorization: token } : {}),
    },
    body: JSON.stringify({ sandboxId }),
    credentials: 'include',
  });

  if (!response.ok) {
    let errorText = `智能修复失败: HTTP ${response.status}`;
    try {
      const errorBody = await response.json();
      if (typeof errorBody?.message === 'string') errorText = errorBody.message;
      if (typeof errorBody?.error === 'string') errorText = errorBody.error;
    } catch {
      // 忽略解析失败，保留默认错误信息
    }
    throw new Error(errorText);
  }

  const result = await response.json();
  if (result && typeof result.success === 'boolean' && result.success === false) {
    throw new Error(result.message || '智能修复失败');
  }

  const data = result?.data ?? result;
  return {
    fixed: Boolean(data?.fixed),
    filesCreated: Array.isArray(data?.filesCreated) ? data.filesCreated : [],
    filesUpdated: Array.isArray(data?.filesUpdated) ? data.filesUpdated : [],
    diagnostics: data?.diagnostics && typeof data.diagnostics === 'object' ? data.diagnostics : undefined,
    message: typeof data?.message === 'string' ? data.message : undefined,
    timestamp: typeof data?.timestamp === 'string' ? data.timestamp : undefined,
  };
}
