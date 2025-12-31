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
  G3Artifact,
  G3ArtifactDetail,
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
): Promise<APIResponse<G3Artifact[]>> {
  return get<G3Artifact[]>(`/v1/g3/jobs/${jobId}/artifacts`);
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
): Promise<APIResponse<G3ArtifactDetail>> {
  return get<G3ArtifactDetail>(`/v1/g3/jobs/${jobId}/artifacts/${artifactId}/content`);
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
 * 订阅G3任务日志流（SSE）
 *
 * 使用Server-Sent Events实时接收任务执行日志。
 * 返回一个取消函数，调用后可关闭连接。
 *
 * @param jobId - 任务ID
 * @param callbacks - 事件回调
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
  callbacks: G3SSECallbacks
): () => void {
  const baseUrl = getApiBaseUrl();
  const token = getToken();
  const url = `${baseUrl}/v1/g3/jobs/${jobId}/logs`;

  // 创建EventSource连接
  // 注意：标准EventSource不支持自定义headers，需要在URL中传递token或使用cookie
  // 这里使用fetch+ReadableStream实现SSE以支持Authorization header
  const controller = new AbortController();

  const headers: Record<string, string> = {
    'Accept': 'text/event-stream',
    'Cache-Control': 'no-cache',
  };

  if (token) {
    headers['Authorization'] = token;
  }

  // 异步启动SSE连接
  (async () => {
    try {
      const response = await fetch(url, {
        method: 'GET',
        headers,
        signal: controller.signal,
        credentials: 'include',
      });

      if (!response.ok) {
        callbacks.onError?.(`连接失败: HTTP ${response.status}`);
        return;
      }

      const reader = response.body?.getReader();
      if (!reader) {
        callbacks.onError?.('无法读取响应流');
        return;
      }

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();

        if (done) {
          callbacks.onClose?.();
          break;
        }

        buffer += decoder.decode(value, { stream: true });

        // 解析SSE事件（以双换行分隔）
        const events = buffer.split('\n\n');
        buffer = events.pop() || ''; // 最后一个可能是不完整的事件

        for (const eventText of events) {
          if (!eventText.trim()) continue;

          const parsedEvent = parseSSEEvent(eventText);
          if (!parsedEvent) continue;

          switch (parsedEvent.event) {
            case 'log':
            case 'message': // 兼容后端直接返回 Flux<G3LogEntry>（无 event 字段时默认为 message）
              if (parsedEvent.data) {
                try {
                  const logEntry = JSON.parse(parsedEvent.data) as G3LogEntry;
                  callbacks.onLog?.(logEntry);
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
              break;

            default:
              // 忽略未知事件类型
              break;
          }
        }
      }
    } catch (error) {
      // 忽略AbortError（正常取消）
      if (error instanceof DOMException && error.name === 'AbortError') {
        return;
      }

      const errorMessage = error instanceof Error ? error.message : '未知错误';
      callbacks.onError?.(`SSE连接错误: ${errorMessage}`);
    }
  })();

  // 返回取消函数
  return () => {
    controller.abort();
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
