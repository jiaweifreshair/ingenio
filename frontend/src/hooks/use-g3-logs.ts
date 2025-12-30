/**
 * G3日志流Hook
 * 提供React组件中订阅G3任务日志的能力
 *
 * 功能：
 * - 自动管理SSE连接生命周期
 * - 日志累积和状态管理
 * - 自动清理（组件卸载时断开连接）
 *
 * @author Ingenio Team
 * @since 2.0.0
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { subscribeToG3Logs, getG3JobStatus, G3JobStatusResponse } from '@/lib/api/g3';
import type { G3LogEntry, G3JobStatus } from '@/types/g3';

/**
 * G3日志Hook状态
 */
export interface UseG3LogsState {
  /** 日志条目列表 */
  logs: G3LogEntry[];
  /** 任务状态 */
  status: G3JobStatus | null;
  /** 任务详细信息 */
  jobInfo: G3JobStatusResponse | null;
  /** 是否正在加载 */
  isLoading: boolean;
  /** 是否已连接 */
  isConnected: boolean;
  /** 是否已完成 */
  isCompleted: boolean;
  /** 错误信息 */
  error: string | null;
  /** 最后心跳时间 */
  lastHeartbeat: Date | null;
}

/**
 * G3日志Hook返回值
 */
export interface UseG3LogsReturn extends UseG3LogsState {
  /** 清空日志 */
  clearLogs: () => void;
  /** 刷新任务状态 */
  refreshStatus: () => Promise<void>;
  /** 手动断开连接 */
  disconnect: () => void;
}

/**
 * 使用G3日志流
 *
 * @param jobId - G3任务ID（为空时不连接）
 * @returns 日志状态和控制方法
 *
 * @example
 * ```tsx
 * function G3LogViewer({ jobId }: { jobId: string }) {
 *   const { logs, status, isConnected, error } = useG3Logs(jobId);
 *
 *   return (
 *     <div>
 *       <div>状态: {status} | 连接: {isConnected ? '是' : '否'}</div>
 *       {error && <div className="text-red-500">{error}</div>}
 *       {logs.map((log, i) => (
 *         <div key={i}>[{log.role}] {log.message}</div>
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export function useG3Logs(jobId: string | null): UseG3LogsReturn {
  const [logs, setLogs] = useState<G3LogEntry[]>([]);
  const [status, setStatus] = useState<G3JobStatus | null>(null);
  const [jobInfo, setJobInfo] = useState<G3JobStatusResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isConnected, setIsConnected] = useState(false);
  const [isCompleted, setIsCompleted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastHeartbeat, setLastHeartbeat] = useState<Date | null>(null);

  // 取消函数引用
  const cancelRef = useRef<(() => void) | null>(null);

  /**
   * 清空日志
   */
  const clearLogs = useCallback(() => {
    setLogs([]);
  }, []);

  /**
   * 刷新任务状态
   */
  const refreshStatus = useCallback(async () => {
    if (!jobId) return;

    try {
      const response = await getG3JobStatus(jobId);
      if (response.success && response.data) {
        setJobInfo(response.data);
        setStatus(response.data.status as G3JobStatus);

        // 检查是否已完成
        if (response.data.status === 'COMPLETED' || response.data.status === 'FAILED') {
          setIsCompleted(true);
        }
      }
    } catch (err) {
      console.warn('[useG3Logs] 刷新状态失败:', err);
    }
  }, [jobId]);

  /**
   * 断开连接
   */
  const disconnect = useCallback(() => {
    if (cancelRef.current) {
      cancelRef.current();
      cancelRef.current = null;
    }
    setIsConnected(false);
  }, []);

  // 订阅日志流
  useEffect(() => {
    if (!jobId) {
      return;
    }

    setIsLoading(true);
    setError(null);
    setIsCompleted(false);

    // 先获取一次状态
    refreshStatus();

    // 订阅SSE日志流
    const cancel = subscribeToG3Logs(jobId, {
      onLog: (entry) => {
        setLogs((prev) => [...prev, entry]);
        setIsLoading(false);
        setIsConnected(true);

        // 从日志推断状态变化
        if (entry.message.includes('任务完成') || entry.message.includes('🎉')) {
          setStatus('COMPLETED');
          setIsCompleted(true);
        } else if (entry.message.includes('任务失败') || entry.message.includes('❌')) {
          setStatus('FAILED');
          setIsCompleted(true);
        }
      },

      onHeartbeat: () => {
        setLastHeartbeat(new Date());
        setIsConnected(true);
      },

      onError: (errMsg) => {
        setError(errMsg);
        setIsLoading(false);
      },

      onComplete: () => {
        setIsCompleted(true);
        setIsConnected(false);
        refreshStatus(); // 完成后刷新最终状态
      },

      onClose: () => {
        setIsConnected(false);
      },
    });

    cancelRef.current = cancel;

    // 清理函数
    return () => {
      if (cancelRef.current) {
        cancelRef.current();
        cancelRef.current = null;
      }
    };
  }, [jobId, refreshStatus]);

  return {
    logs,
    status,
    jobInfo,
    isLoading,
    isConnected,
    isCompleted,
    error,
    lastHeartbeat,
    clearLogs,
    refreshStatus,
    disconnect,
  };
}

/**
 * G3任务管理Hook
 *
 * 提供完整的G3任务创建和监控能力，包括：
 * - 提交新任务
 * - 订阅日志流
 * - 状态管理
 */
export interface UseG3TaskState extends UseG3LogsState {
  /** 当前任务ID */
  jobId: string | null;
  /** 是否正在提交 */
  isSubmitting: boolean;
  /** 提交错误 */
  submitError: string | null;
}

export interface UseG3TaskReturn extends UseG3TaskState {
  /** 提交新任务 */
  submitJob: (requirement: string) => Promise<string | null>;
  /** 清空日志 */
  clearLogs: () => void;
  /** 刷新任务状态 */
  refreshStatus: () => Promise<void>;
  /** 断开连接 */
  disconnect: () => void;
  /** 重置所有状态 */
  reset: () => void;
}

/**
 * 使用G3任务（包含提交和监控）
 *
 * @returns 任务状态和控制方法
 *
 * @example
 * ```tsx
 * function G3TaskManager() {
 *   const {
 *     jobId,
 *     logs,
 *     status,
 *     isSubmitting,
 *     submitJob,
 *     reset
 *   } = useG3Task();
 *
 *   const handleSubmit = async () => {
 *     await submitJob('创建一个用户管理系统');
 *   };
 *
 *   return (
 *     <div>
 *       <button onClick={handleSubmit} disabled={isSubmitting}>
 *         {isSubmitting ? '提交中...' : '提交任务'}
 *       </button>
 *       {jobId && <div>任务ID: {jobId}</div>}
 *       {logs.map((log, i) => (
 *         <div key={i}>{log.message}</div>
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export function useG3Task(): UseG3TaskReturn {
  const [jobId, setJobId] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  // 使用日志Hook
  const logsState = useG3Logs(jobId);

  /**
   * 提交新任务
   */
  const submitJob = useCallback(async (requirement: string): Promise<string | null> => {
    setIsSubmitting(true);
    setSubmitError(null);

    try {
      // 动态导入避免循环依赖
      const { submitG3Job } = await import('@/lib/api/g3');
      const response = await submitG3Job({ requirement });

      if (!response.success || !response.data?.jobId) {
        const errorMsg = response.error || response.message || '任务提交失败';
        setSubmitError(errorMsg);
        return null;
      }

      const newJobId = response.data.jobId;
      setJobId(newJobId);
      return newJobId;
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : '任务提交失败';
      setSubmitError(errorMsg);
      return null;
    } finally {
      setIsSubmitting(false);
    }
  }, []);

  /**
   * 重置所有状态
   */
  const reset = useCallback(() => {
    logsState.disconnect();
    setJobId(null);
    setIsSubmitting(false);
    setSubmitError(null);
    logsState.clearLogs();
  }, [logsState]);

  return {
    jobId,
    isSubmitting,
    submitError,
    ...logsState,
    submitJob,
    reset,
  };
}
