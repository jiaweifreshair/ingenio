/**
 * G3引擎API客户端
 * G3 = Generate-Check-Fix 自修复代码生成引擎
 *
 * 提供与后端 G3Controller 的完整API交互：
 * - 提交G3任务
 * - 查询任务状态
 * - 订阅实时日志（SSE）
 * - 获取生成产物
 *
 * @author Ingenio Team
 * @since 2.0.0
 */

import { get, post, APIResponse } from './client';
import { getApiBaseUrl } from './base-url';
import { getToken } from '@/lib/auth/token';
import type {
  G3LogEntry,
  G3ArtifactSummary,
  G3ArtifactContent,
  G3Contract,
  SubmitG3JobRequest,
  SubmitG3JobResponse,
  G3HealthStatus,
} from '@/types/g3';

/**
 * G3任务状态响应（与后端JobStatusResponse对齐）
 */
export interface G3JobStatusResponse {
  /** 任务ID */
  id: string;
  /** 任务状态 */
  status: string;
  /** 当前修复轮次 */
  currentRound: number;
  /** 最大修复轮次 */
  maxRounds: number;
  /** 契约是否已锁定 */
  contractLocked: boolean;
  /** 沙箱ID */
  sandboxId: string | null;
  /** 沙箱URL */
  sandboxUrl: string | null;
  /** 最近错误 */
  lastError: string | null;
  /** 开始时间 */
  startedAt: string | null;
  /** 完成时间 */
  completedAt: string | null;
}

/**
 * G3 SSE事件回调类型
 */
export interface G3SSECallbacks {
  /** SSE 连接建立（HTTP 200 且可读流就绪） */
  onOpen?: (info: { status: number; contentType: string | null }) => void;
  /** 收到日志事件 */
  onLog?: (entry: G3LogEntry) => void;
  /** 收到心跳事件 */
  onHeartbeat?: () => void;
  /** 收到错误事件 */
  onError?: (error: string) => void;
  /** 任务完成 */
  onComplete?: () => void;
  /** 连接关闭 */
  onClose?: () => void;
}

/**
 * 提交G3任务
 *
 * @param request - 任务请求参数
 * @returns 任务提交结果（包含jobId）
 */
export async function submitG3Job(
  request: SubmitG3JobRequest
): Promise<APIResponse<SubmitG3JobResponse>> {
  return post<SubmitG3JobResponse>('/v1/g3/jobs', request);
}

/**
 * 查询G3任务状态
 *
 * @param jobId - 任务ID
 * @returns 任务状态信息
 */
export async function getG3JobStatus(
  jobId: string
): Promise<APIResponse<G3JobStatusResponse>> {
  return get<G3JobStatusResponse>(`/v1/g3/jobs/${jobId}`);
}

/**
 * 获取G3任务产物列表
 *
 * @param jobId - 任务ID
 * @returns 产物列表
 */
export async function getG3Artifacts(
  jobId: string
): Promise<APIResponse<G3ArtifactSummary[]>> {
  return get<G3ArtifactSummary[]>(`/v1/g3/jobs/${jobId}/artifacts`);
}

/**
 * 获取单个产物的详细内容
 *
 * @param jobId - 任务ID
 * @param artifactId - 产物ID
 * @returns 产物详情（包含代码内容）
 */
export async function getG3ArtifactContent(
  jobId: string,
  artifactId: string
): Promise<APIResponse<G3ArtifactContent>> {
  return get<G3ArtifactContent>(`/v1/g3/jobs/${jobId}/artifacts/${artifactId}/content`);
}

/**
 * 获取G3任务契约内容
 *
 * @param jobId - 任务ID
 * @returns 契约信息（OpenAPI YAML + DB Schema SQL）
 */
export async function getG3Contract(
  jobId: string
): Promise<APIResponse<G3Contract>> {
  return get<G3Contract>(`/v1/g3/jobs/${jobId}/contract`);
}

/**
 * G3引擎健康检查
 *
 * @returns 健康状态
 */
export async function checkG3Health(): Promise<APIResponse<G3HealthStatus>> {
  return get<G3HealthStatus>('/v1/g3/health');
}

/**
 * SSE重连配置
 */
interface SSEReconnectConfig {
  /** 最大重连次数 */
  maxRetries: number;
  /** 基础重连间隔（毫秒） */
  baseDelay: number;
  /** 最大重连间隔（毫秒） */
  maxDelay: number;
}

/** 默认重连配置 */
const DEFAULT_RECONNECT_CONFIG: SSEReconnectConfig = {
  maxRetries: 10,
  baseDelay: 2000,
  maxDelay: 30000,
};

/**
 * 订阅G3任务日志流（SSE）- 支持自动重连
 *
 * 使用Server-Sent Events实时接收任务执行日志。
 * 当连接意外断开时，会自动检查任务状态并重连。
 * 返回一个取消函数，调用后可关闭连接。
 *
 * @param jobId - 任务ID
 * @param callbacks - 事件回调
 * @param reconnectConfig - 重连配置（可选）
 * @returns 取消订阅函数
 *
 * @example
 * ```typescript
 * const cancel = subscribeToG3Logs('job-123', {
 *   onLog: (entry) => console.log(entry.message),
 *   onComplete: () => console.log('任务完成'),
 *   onError: (err) => console.error(err),
 * });
 *
 * // 取消订阅
 * cancel();
 * ```
 */
export function subscribeToG3Logs(
  jobId: string,
  callbacks: G3SSECallbacks,
  reconnectConfig: Partial<SSEReconnectConfig> = {}
): () => void {
  const config = { ...DEFAULT_RECONNECT_CONFIG, ...reconnectConfig };
  const baseUrl = getApiBaseUrl();
  const url = `${baseUrl}/v1/g3/jobs/${jobId}/logs`;

  let isCancelled = false;
  let retryCount = 0;
  let currentController: AbortController | null = null;

  /**
   * 检查任务是否已完成
   */
  async function isJobFinished(): Promise<boolean> {
    try {
      const response = await getG3JobStatus(jobId);
      if (response.success && response.data) {
        const status = response.data.status;
        return status === 'COMPLETED' || status === 'FAILED';
      }
    } catch (e) {
      console.warn('[G3 SSE] 检查任务状态失败:', e);
    }
    return false;
  }

  /**
   * 计算重连延迟（指数退避）
   */
  function getRetryDelay(): number {
    const delay = Math.min(
      config.baseDelay * Math.pow(2, retryCount),
      config.maxDelay
    );
    return delay;
  }

  /**
   * 启动SSE连接
   */
  async function connect(): Promise<void> {
    if (isCancelled) return;

    const token = getToken();
    const controller = new AbortController();
    currentController = controller;

    const headers: Record<string, string> = {
      'Accept': 'text/event-stream',
      'Cache-Control': 'no-cache',
    };

    if (token) {
      headers['Authorization'] = token;
    }

    try {
      const response = await fetch(url, {
        method: 'GET',
        headers,
        signal: controller.signal,
        credentials: 'include',
      });

      if (!response.ok) {
        callbacks.onError?.(`SSE连接失败: HTTP ${response.status}`);
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body?.getReader();
      if (!reader) {
        callbacks.onError?.('SSE连接失败: 无法读取响应流');
        throw new Error('无法读取响应流');
      }

      callbacks.onOpen?.({
        status: response.status,
        contentType: response.headers.get('content-type'),
      });

      // 连接成功，重置重试计数
      retryCount = 0;

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();

        if (done) {
          // 流正常结束，检查是否需要重连
          if (!isCancelled) {
            await handleDisconnect();
          }
          break;
        }

        buffer += decoder.decode(value, { stream: true });

        // 兼容 CRLF（\r\n）分隔：统一去掉 \r，避免无法命中 '\n\n' 分隔符导致“看起来没有流式输出”
        buffer = buffer.replace(/\r/g, '');

        // 解析SSE事件（以双换行分隔）
        const events = buffer.split('\n\n');
        buffer = events.pop() || '';

        for (const eventText of events) {
          if (!eventText.trim()) continue;

          const parsedEvent = parseSSEEvent(eventText);
          if (!parsedEvent) continue;

          switch (parsedEvent.event) {
            case 'log':
            case 'message':
              if (parsedEvent.data) {
                try {
                  const logEntry = JSON.parse(parsedEvent.data) as G3LogEntry;
                  if (logEntry.level === 'heartbeat') {
                    callbacks.onHeartbeat?.();
                  } else {
                    callbacks.onLog?.(logEntry);
                  }
                } catch {
                  console.warn('[G3 SSE] 解析日志数据失败:', parsedEvent.data);
                }
              }
              break;

            case 'heartbeat':
              callbacks.onHeartbeat?.();
              break;

            case 'error':
              callbacks.onError?.(parsedEvent.data || '未知错误');
              break;

            case 'complete':
              callbacks.onComplete?.();
              isCancelled = true; // 任务完成，停止重连
              break;

            default:
              break;
          }
        }
      }
    } catch (error) {
      // 忽略主动取消
      if (error instanceof DOMException && error.name === 'AbortError') {
        return;
      }

      if (!isCancelled) {
        const errorMessage = error instanceof Error ? error.message : '未知错误';
        console.warn(`[G3 SSE] 连接错误: ${errorMessage}, 准备重连...`);
        callbacks.onError?.(`SSE连接错误: ${errorMessage}，准备重连...`);
        await handleDisconnect();
      }
    }
  }

  /**
   * 处理断开连接，决定是否重连
   */
  async function handleDisconnect(): Promise<void> {
    if (isCancelled) {
      callbacks.onClose?.();
      return;
    }

    // 检查任务是否已完成
    const finished = await isJobFinished();
    if (finished) {
      console.log('[G3 SSE] 任务已完成，停止重连');
      callbacks.onClose?.();
      return;
    }

    // 检查重试次数
    if (retryCount >= config.maxRetries) {
      console.warn('[G3 SSE] 达到最大重连次数，停止重连');
      callbacks.onError?.('SSE连接多次失败，已停止重试');
      callbacks.onClose?.();
      return;
    }

    // 重连
    retryCount++;
    const delay = getRetryDelay();
    console.log(`[G3 SSE] ${delay}ms 后进行第 ${retryCount} 次重连...`);
    callbacks.onError?.(`SSE断开：${delay}ms 后重连（第 ${retryCount}/${config.maxRetries} 次）`);

    await new Promise(resolve => setTimeout(resolve, delay));

    if (!isCancelled) {
      connect();
    }
  }

  // 启动连接
  connect();

  // 返回取消函数
  return () => {
    isCancelled = true;
    if (currentController) {
      currentController.abort();
    }
  };
}

/**
 * 解析SSE事件文本
 *
 * @param eventText - SSE事件原始文本
 * @returns 解析后的事件对象
 */
function parseSSEEvent(eventText: string): { event: string; data: string } | null {
  const lines = eventText.split('\n');
  let event = 'message'; // 默认事件类型
  const dataLines: string[] = [];

  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      // SSE 允许出现多行 data，这里按规范拼接，避免 JSON 被切断
      dataLines.push(line.slice(5).trim());
    } else if (line.startsWith(':')) {
      // 注释行，忽略
    }
  }

  const data = dataLines.join('\n');

  if (!event && !data) {
    return null;
  }

  return { event, data };
}

/**
 * 创建G3任务并订阅日志的便捷方法
 *
 * 组合了submitG3Job和subscribeToG3Logs，提供一体化的任务创建+监控体验。
 *
 * @param requirement - 需求描述
 * @param callbacks - SSE事件回调
 * @returns 包含jobId和取消函数的对象
 */
export async function createAndMonitorG3Job(
  requirement: string,
  callbacks: G3SSECallbacks & {
    /** 任务提交成功回调 */
    onSubmitted?: (jobId: string) => void;
    /** 任务提交失败回调 */
    onSubmitError?: (error: string) => void;
  }
): Promise<{ jobId: string | null; cancel: () => void }> {
  let cancel: (() => void) | null = null;

  try {
    // 提交任务
    const response = await submitG3Job({ requirement });

    if (!response.success || !response.data?.jobId) {
      const errorMessage = response.error || response.message || '任务提交失败';
      callbacks.onSubmitError?.(errorMessage);
      return { jobId: null, cancel: () => {} };
    }

    const jobId = response.data.jobId;
    callbacks.onSubmitted?.(jobId);

    // 订阅日志
    cancel = subscribeToG3Logs(jobId, callbacks);

    return { jobId, cancel };
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : '任务提交失败';
    callbacks.onSubmitError?.(errorMessage);
    return { jobId: null, cancel: () => {} };
  }
}
