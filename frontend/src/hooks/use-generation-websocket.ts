/**
 * 生成任务WebSocket Hook
 * 管理WebSocket连接和消息处理
 */
'use client';

import { useEffect, useCallback, useRef, useState } from 'react';
import {
  GenerationWebSocket,
  GenerationWebSocketMessage,
  TaskStatusMessage,
  AgentStatusMessage,
  ErrorMessage,
  WebSocketMessage,
  type GenerationWebSocketOptions
} from '@/lib/websocket/generation-websocket';

/**
 * WebSocket状态
 */
export interface WebSocketState {
  isConnected: boolean;
  isConnecting: boolean;
  error: string | null;
  lastMessage: GenerationWebSocketMessage | null;
  connectionAttempts: number;
}

/**
 * Hook配置选项
 */
export interface UseGenerationWebSocketOptions {
  taskId: string;
  autoConnect?: boolean;
  onTaskStatus?: (message: TaskStatusMessage) => void;
  onAgentStatus?: (message: AgentStatusMessage) => void;
  onError?: (message: ErrorMessage) => void;
  onConnect?: () => void;
  onDisconnect?: () => void;
}

/**
 * 生成任务WebSocket Hook
 */
export function useGenerationWebSocket(options: UseGenerationWebSocketOptions) {
  const {
    taskId,
    autoConnect = true,
    onTaskStatus,
    onAgentStatus,
    onError,
    onConnect,
    onDisconnect
  } = options;

  // WebSocket实例
  const wsRef = useRef<GenerationWebSocket | null>(null);

  // 使用useRef存储回调函数，避免依赖变化导致无限循环
  const callbacksRef = useRef({
    onTaskStatus,
    onAgentStatus,
    onError,
    onConnect,
    onDisconnect
  });

  // 更新回调引用（不会触发重新渲染）
  useEffect(() => {
    callbacksRef.current = {
      onTaskStatus,
      onAgentStatus,
      onError,
      onConnect,
      onDisconnect
    };
  }, [onTaskStatus, onAgentStatus, onError, onConnect, onDisconnect]);

  // 状态管理
  const [state, setState] = useState<WebSocketState>({
    isConnected: false,
    isConnecting: false,
    error: null,
    lastMessage: null,
    connectionAttempts: 0
  });

  /**
   * 更新状态
   */
  const updateState = useCallback((updates: Partial<WebSocketState> | ((prev: WebSocketState) => Partial<WebSocketState>)) => {
    setState(prev => ({
      ...prev,
      ...(typeof updates === 'function' ? updates(prev) : updates)
    }));
  }, []);

  /**
   * 连接WebSocket
   */
  const connect = useCallback(() => {
    if (wsRef.current) {
      wsRef.current.disconnect();
    }

    updateState(prev => ({
      isConnecting: true,
      error: null,
      connectionAttempts: prev.connectionAttempts + 1
    }));

    const wsOptions: GenerationWebSocketOptions = {
      taskId,
      onOpen: () => {
        console.log('WebSocket连接已建立');
        updateState({
          isConnected: true,
          isConnecting: false,
          error: null
        });
        callbacksRef.current.onConnect?.();
      },
      onClose: () => {
        console.log('WebSocket连接已关闭');
        updateState({
          isConnected: false,
          isConnecting: false
        });
        callbacksRef.current.onDisconnect?.();
      },
      onError: (error) => {
        console.error('WebSocket连接错误:', error);
        updateState({
          isConnected: false,
          isConnecting: false,
          error: 'WebSocket连接失败'
        });
      },
      onMessage: (message) => {
        updateState({
          lastMessage: message
        });
      },
      onTaskStatus: (message) => {
        console.log('收到任务状态更新:', message);
        callbacksRef.current.onTaskStatus?.(message);
      },
      onAgentStatus: (message) => {
        console.log('收到Agent状态更新:', message);
        callbacksRef.current.onAgentStatus?.(message);
      },
    };

    wsRef.current = new GenerationWebSocket(wsOptions);
    wsRef.current.connect();
  }, [taskId, updateState]);

  /**
   * 断开连接
   */
  const disconnect = useCallback(() => {
    if (wsRef.current) {
      wsRef.current.disconnect();
      wsRef.current = null;
    }

    updateState({
      isConnected: false,
      isConnecting: false,
      error: null
    });
  }, [updateState]);

  /**
   * 重新连接
   */
  const reconnect = useCallback(() => {
    disconnect();
    setTimeout(connect, 1000);
  }, [disconnect, connect]);

  /**
   * 发送消息
   */
  const sendMessage = useCallback((message: WebSocketMessage) => {
    if (wsRef.current) {
      return wsRef.current.send(message);
    }
    return false;
  }, []);

  /**
   * 取消任务
   */
  const cancelTask = useCallback(() => {
    if (wsRef.current) {
      return wsRef.current.cancelTask();
    }
    return false;
  }, []);

  // 自动连接
  useEffect(() => {
    if (autoConnect && taskId) {
      connect();
    }

    return () => {
      disconnect();
    };
  }, [autoConnect, taskId, connect, disconnect]);

  return {
    // 状态
    state,
    isConnected: state.isConnected,
    isConnecting: state.isConnecting,
    error: state.error,
    lastMessage: state.lastMessage,
    connectionAttempts: state.connectionAttempts,

    // 方法
    connect,
    disconnect,
    reconnect,
    sendMessage,
    cancelTask
  };
}
