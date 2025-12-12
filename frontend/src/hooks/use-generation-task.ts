/**
 * 生成任务状态管理Hook
 * 负责管理AI生成任务的状态查询和更新
 * 修复：删除未使用的类型导入
 */
'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import {
  getTaskStatus,
  cancelTask,
  type TaskStatusResponse,
} from '@/lib/api/generate';

/**
 * 任务状态
 */
export interface GenerationTaskState {
  /** 任务ID */
  taskId: string | null;
  /** 任务状态响应 */
  taskStatus: TaskStatusResponse | null;
  /** 是否正在加载 */
  isLoading: boolean;
  /** 是否正在查询状态 */
  isPolling: boolean;
  /** 错误信息 */
  error: string | null;
  /** 是否已连接WebSocket */
  isConnected: boolean;
}

/**
 * Hook配置选项
 */
export interface UseGenerationTaskOptions {
  /** 是否自动开始轮询 */
  autoPoll?: boolean;
  /** 轮询间隔（毫秒） */
  pollInterval?: number;
  /** 轮询次数限制 */
  maxPolls?: number;
  /** 状态变化回调 */
  onStatusChange?: (status: TaskStatusResponse) => void;
  /** 完成回调 */
  onComplete?: (status: TaskStatusResponse) => void;
  /** 失败回调 */
  onError?: (error: string) => void;
}

/**
 * 生成任务状态管理Hook
 */
export function useGenerationTask(options: UseGenerationTaskOptions = {}) {
  const {
    autoPoll = false,
    pollInterval = 2000,
    maxPolls = 600, // 最大轮询20分钟
    onStatusChange,
    onComplete,
    onError
  } = options;

  // 状态管理
  const [state, setState] = useState<GenerationTaskState>({
    taskId: null,
    taskStatus: null,
    isLoading: false,
    isPolling: false,
    error: null,
    isConnected: false
  });

  // Refs
  const pollIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const pollCountRef = useRef<number>(0);
  const isPollingRef = useRef<boolean>(false);

  /**
   * 设置任务ID并开始监控
   */
  const setTaskId = useCallback((taskId: string) => {
    setState(prev => ({
      ...prev,
      taskId,
      error: null,
      taskStatus: taskId === prev.taskId ? prev.taskStatus : null
    }));

    if (taskId && autoPoll) {
      startPolling(taskId);
    } else if (!taskId) {
      stopPolling();
    }
  }, [autoPoll]);

  /**
   * 开始轮询任务状态
   */
  const startPolling = useCallback((taskId: string) => {
    if (isPollingRef.current) {
      stopPolling();
    }

    isPollingRef.current = true;
    pollCountRef.current = 0;

    setState(prev => ({ ...prev, isPolling: true, error: null }));

    const poll = async () => {
      if (!isPollingRef.current || pollCountRef.current >= maxPolls) {
        stopPolling();
        return;
      }

      try {
        const response = await getTaskStatus(taskId);

        if (response.success && response.data) {
          const taskStatus = response.data;

          setState(prev => ({
            ...prev,
            taskStatus,
            isLoading: false,
            error: null
          }));

          // 调用状态变化回调
          onStatusChange?.(taskStatus);

          // 检查任务是否完成
          if (taskStatus.status === 'completed') {
            onComplete?.(taskStatus);
            stopPolling();
            return;
          }

          // 检查任务是否失败
          if (taskStatus.status === 'failed') {
            onError?.(taskStatus.errorMessage || '任务执行失败');
            stopPolling();
            return;
          }
        } else {
          throw new Error(response.error || '查询任务状态失败');
        }
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : '查询任务状态失败';
        console.error('轮询任务状态失败:', error);

        setState(prev => ({
          ...prev,
          error: errorMessage,
          isPolling: false
        }));

        onError?.(errorMessage);
        stopPolling();
        return;
      }

      pollCountRef.current++;

      // 继续轮询
      pollIntervalRef.current = setTimeout(poll, pollInterval);
    };

    // 立即执行第一次查询
    poll();
  }, [pollInterval, maxPolls, onStatusChange, onComplete, onError]);

  /**
   * 停止轮询
   */
  const stopPolling = useCallback(() => {
    if (pollIntervalRef.current) {
      clearTimeout(pollIntervalRef.current);
      pollIntervalRef.current = null;
    }

    isPollingRef.current = false;

    setState(prev => ({ ...prev, isPolling: false }));
  }, []);

  /**
   * 手动刷新任务状态
   */
  const refreshStatus = useCallback(async () => {
    if (!state.taskId) {
      return;
    }

    setState(prev => ({ ...prev, isLoading: true, error: null }));

    try {
      const response = await getTaskStatus(state.taskId);

      if (response.success && response.data) {
        const taskStatus = response.data;

        setState(prev => ({
          ...prev,
          taskStatus,
          isLoading: false,
          error: null
        }));

        onStatusChange?.(taskStatus);

        // 检查任务是否完成
        if (taskStatus.status === 'completed') {
          onComplete?.(taskStatus);
        }

        // 检查任务是否失败
        if (taskStatus.status === 'failed') {
          onError?.(taskStatus.errorMessage || '任务执行失败');
        }
      } else {
        throw new Error(response.error || '查询任务状态失败');
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '查询任务状态失败';
      console.error('刷新任务状态失败:', error);

      setState(prev => ({
        ...prev,
        error: errorMessage,
        isLoading: false
      }));

      onError?.(errorMessage);
    }
  }, [state.taskId, onStatusChange, onComplete, onError]);

  /**
   * 取消任务
   */
  const cancelCurrentTask = useCallback(async () => {
    if (!state.taskId) {
      return false;
    }

    try {
      setState(prev => ({ ...prev, isLoading: true }));

      const response = await cancelTask(state.taskId);

      if (response.success) {
        stopPolling();
        setState(prev => ({
          ...prev,
          isLoading: false
        }));
        return true;
      } else {
        throw new Error(response.error || '取消任务失败');
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '取消任务失败';
      console.error('取消任务失败:', error);

      setState(prev => ({
        ...prev,
        error: errorMessage,
        isLoading: false
      }));

      return false;
    }
  }, [state.taskId, stopPolling]);

  /**
   * 重置状态
   */
  const reset = useCallback(() => {
    stopPolling();
    setState({
      taskId: null,
      taskStatus: null,
      isLoading: false,
      isPolling: false,
      error: null,
      isConnected: false
    });
  }, [stopPolling]);

  /**
   * 设置WebSocket连接状态
   */
  const setConnectionStatus = useCallback((isConnected: boolean) => {
    setState(prev => ({ ...prev, isConnected }));
  }, []);

  // 清理定时器
  useEffect(() => {
    return () => {
      if (pollIntervalRef.current) {
        clearTimeout(pollIntervalRef.current);
      }
    };
  }, []);

  return {
    // 状态
    state,

    // 属性
    taskId: state.taskId,
    taskStatus: state.taskStatus,
    isLoading: state.isLoading,
    isPolling: state.isPolling,
    error: state.error,
    isConnected: state.isConnected,

    // 便捷属性
    isCompleted: state.taskStatus?.status === 'completed',
    isFailed: state.taskStatus?.status === 'failed',
    isRunning: state.taskStatus?.status === 'planning' ||
              state.taskStatus?.status === 'executing' ||
              state.taskStatus?.status === 'validating' ||
              state.taskStatus?.status === 'generating',
    progress: state.taskStatus?.progress || 0,
    currentAgent: state.taskStatus?.currentAgent,

    // 方法
    setTaskId,
    startPolling,
    stopPolling,
    refreshStatus,
    cancelCurrentTask,
    reset,
    setConnectionStatus
  };
}
