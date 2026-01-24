/**
 * SSE分析进度订阅Hook
 * 订阅后端的流式分析接口，实时接收分析进度
 */
'use client';

import { useEffect, useRef, useState, useCallback } from 'react';
import { getToken } from '@/lib/auth/token';
import { getApiBaseUrl } from '@/lib/api/base-url';

/**
 * 分析进度消息
 *
 * 对应后端DTO: com.ingenio.backend.dto.response.AnalysisProgressMessage
 */
export interface AnalysisProgressMessage {
  /** 步骤编号（1-6） */
  step: number;
  /** 步骤名称 */
  stepName: string;
  /** 步骤描述 */
  description: string;
  /** 步骤状态 */
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  /** 进度百分比（0-100） */
  progress: number;
  /** 步骤结果（可选，completed时返回） */
  result?: unknown;
  /** 错误信息（可选，failed时返回） */
  error?: string;
  /** 详细日志信息 */
  detail?: string;
  /** 时间戳 */
  timestamp: string;
  /** 推理内容（DeepSeek R1 等推理模型的思考过程，step 6 时使用） */
  reasoning?: string;
  /** 是否正在推理中（step 6 时使用） */
  isReasoning?: boolean;
}

/**
 * SSE连接状态
 */
export interface SseState {
  isConnected: boolean;
  isConnecting: boolean;
  error: string | null;
  messages: AnalysisProgressMessage[];
  isCompleted: boolean;
  finalResult?: unknown;
}

/**
 * Hook选项
 */
export interface UseAnalysisSseOptions {
  requirement: string;
  autoConnect?: boolean;
  onProgress?: (message: AnalysisProgressMessage) => void;
  onComplete?: () => void;
  onError?: (error: string) => void;
}

/**
 * 规范化SSE网络错误提示
 *
 * 是什么：SSE流式请求的错误提示映射。
 * 做什么：将“Failed to fetch”等英文错误转换为可读的中文提示。
 * 为什么：避免用户看到生硬的英文网络错误，提升体验与可定位性。
 */
function normalizeSseErrorMessage(message: string): string {
  const normalized = message.toLowerCase();

  if (
    normalized.includes('failed to fetch') ||
    normalized.includes('networkerror') ||
    normalized.includes('econnrefused')
  ) {
    return '网络连接失败，请检查后端服务是否启动（http://localhost:8080）';
  }

  if (normalized.includes('timeout')) {
    return '分析请求超时，请稍后重试';
  }

  return message;
}

/**
 * 使用SSE订阅分析进度
 */
export function useAnalysisSse(options: UseAnalysisSseOptions) {
  const {
    requirement,
    autoConnect = true,
    onProgress,
    onComplete,
    onError
  } = options;

  const abortControllerRef = useRef<AbortController | null>(null);
  const timeoutIdRef = useRef<NodeJS.Timeout | null>(null);  // 超时计时器引用
  const terminalEventRef = useRef<'none' | 'complete' | 'error'>('none');
  // 使用ref存储回调函数，避免依赖变化导致无限循环
  const onProgressRef = useRef(onProgress);
  const onCompleteRef = useRef(onComplete);
  const onErrorRef = useRef(onError);

  // 更新ref当回调变化时
  useEffect(() => {
    onProgressRef.current = onProgress;
    onCompleteRef.current = onComplete;
    onErrorRef.current = onError;
  }, [onProgress, onComplete, onError]);

  const [state, setState] = useState<SseState>({
    isConnected: false,
    isConnecting: false,
    error: null,
    messages: [],
    isCompleted: false,
    finalResult: undefined
  });

  // 重连计数器
  const retryCountRef = useRef<number>(0);
  const MAX_RETRIES = 2;

  /**
   * 更新状态
   */
  const updateState = useCallback((updates: Partial<SseState>) => {
    setState(prev => ({ ...prev, ...updates }));
  }, []);

  /**
   * 重置超时计时器 (Keep-Alive)
   * 每次收到消息时重置，如果长时间无响应则判定超时
   */
  const resetTimeoutTimer = useCallback(() => {
    if (timeoutIdRef.current) {
      clearTimeout(timeoutIdRef.current);
    }

    // 设置空闲超时：如果 120秒 内没有收到任何消息，认为连接断开或后端卡死
    const IDLE_TIMEOUT_MS = 120000; // 从60秒增加到120秒
    timeoutIdRef.current = setTimeout(() => {
      console.warn(`⏰ SSE分析超时（${IDLE_TIMEOUT_MS / 1000}秒无响应）`);

      // 尝试重连（最多2次）
      if (retryCountRef.current < MAX_RETRIES) {
        retryCountRef.current += 1;
        console.log(`🔄 尝试重连 (${retryCountRef.current}/${MAX_RETRIES})...`);

        updateState({
          error: `分析耗时较长，正在重试（${retryCountRef.current}/${MAX_RETRIES}）...`,
          isConnected: false,
          isConnecting: false
        });

        // 关闭当前连接（中止正在进行的fetch流）
        if (abortControllerRef.current) {
          abortControllerRef.current.abort();
          abortControllerRef.current = null;
        }

        // 1秒后重连
        setTimeout(() => {
          connect();
        }, 1000);
      } else {
        // 重连次数用尽，报错
        console.error('❌ SSE重连失败，已达最大重试次数');
        updateState({
          error: '分析响应超时，请刷新页面重试',
          isConnected: false,
          isConnecting: false
        });
        onErrorRef.current?.('分析响应超时，请刷新页面重试');

        // 关闭SSE连接（中止正在进行的fetch流）
        if (abortControllerRef.current) {
          abortControllerRef.current.abort();
          abortControllerRef.current = null;
        }
      }
    }, IDLE_TIMEOUT_MS);
  }, [updateState]);

  /**
   * 清理超时计时器
   */
  const clearTimeoutTimer = useCallback(() => {
    if (timeoutIdRef.current) {
      clearTimeout(timeoutIdRef.current);
      timeoutIdRef.current = null;
    }
  }, []);

  /**
   * 连接SSE
   */
  const connect = useCallback((overrideRequirement?: string) => {
    const effectiveRequirement = overrideRequirement || requirement;
    if (!effectiveRequirement || effectiveRequirement.trim().length < 10) {
      updateState({
        error: '需求描述至少需要10个字符'
      });
      return;
    }

    // 关闭现有连接和超时计时器
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    clearTimeoutTimer();

    updateState({
      isConnecting: true,
      error: null,
      messages: [],
      isCompleted: false
    });

    terminalEventRef.current = 'none';

    // 启动超时计时器 (等待首次响应)
    resetTimeoutTimer();

    try {
      // 构造SSE URL（POST请求需要通过fetch发起，然后用EventSource接收）
      // 注意：标准EventSource不支持POST，这里使用fetch的ReadableStream方式
      const baseUrl = getApiBaseUrl();
      const apiUrl = `${baseUrl}/v1/generate/analyze-stream`;

      // 获取认证token
      const token = getToken();

      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
      };

      if (token) {
        headers['Authorization'] = token;
      }

      const abortController = new AbortController();
      abortControllerRef.current = abortController;

      fetch(apiUrl, {
        method: 'POST',
        headers,
        body: JSON.stringify({ requirement: effectiveRequirement }),
        signal: abortController.signal
      }).then(response => {
        if (response.status === 401) {
          throw new Error('未登录或登录已失效，请先登录');
        }

        if (!response.ok) {
          throw new Error(`SSE请求失败: ${response.status} ${response.statusText}`);
        }

        if (!response.body) {
          throw new Error('响应体为空');
        }

        updateState({
          isConnected: true,
          isConnecting: false
        });

        // 成功连接，重置重连计数器
        retryCountRef.current = 0;

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let currentEvent = 'message';
        let currentData = '';

        // 递归读取流
        const readStream = (): void => {
          reader.read().then(({ done, value }) => {
            if (done) {
              console.log('SSE流读取完成');
              clearTimeoutTimer();  // 清理超时计时器
              updateState({ isConnected: false });

              // 只有明确收到complete事件才算“已完成”，避免异常断流被误判为成功
              if (terminalEventRef.current === 'complete') {
                updateState({ isCompleted: true });
                onCompleteRef.current?.();
              }
              return;
            }

            // 解码数据并拼接到缓冲区
            buffer += decoder.decode(value, { stream: true });

            // 处理SSE消息
            // 按照SSE标准，必须按行处理
            const lines = buffer.split(/\r\n|\r|\n/);
            // 保留最后一个可能不完整的行
            buffer = lines.pop() || '';

            for (const line of lines) {
              // 忽略注释
              if (line.startsWith(':')) continue;

              // 空行表示事件结束，分发事件
              if (line.trim() === '') {
                if (currentData) {
                  try {
                    const data = JSON.parse(currentData);
                    const eventType = currentEvent || 'message';

                    console.log('SSE接收:', eventType, data);

                    if (eventType === 'progress') {
                      // 收到进度更新，重置超时计时器 (Keep-Alive)
                      resetTimeoutTimer();

                      const message: AnalysisProgressMessage = data;
                      setState(prev => ({
                        ...prev,
                        messages: [...prev.messages, message]
                      }));
                      onProgressRef.current?.(message);
                    } else if (eventType === 'complete') {
                      terminalEventRef.current = 'complete';
                      clearTimeoutTimer();  // 清理超时计时器
                      
                      // 尝试提取最终结果
                      let finalResult = undefined;
                      if (data) {
                         if (data.result) {
                            finalResult = data.result;
                         } else {
                            finalResult = data;
                         }
                      }

                      updateState({
                        isCompleted: true,
                        isConnected: false,
                        finalResult
                      });
                      onCompleteRef.current?.();
                      // 主动中止连接，避免后续done/网络中断被误判为错误
                      try {
                        reader.cancel();
                      } catch {
                        // ignore
                      }
                      if (abortControllerRef.current) {
                        abortControllerRef.current.abort();
                        abortControllerRef.current = null;
                      }
                      return;
                    } else if (eventType === 'error') {
                      terminalEventRef.current = 'error';
                      clearTimeoutTimer();  // 清理超时计时器
                      const errorMsg = data.error || '分析失败';
                      updateState({
                        error: errorMsg,
                        isConnected: false
                      });
                      onErrorRef.current?.(errorMsg);
                      // 主动中止连接，避免后续done/网络中断被误判为成功
                      try {
                        reader.cancel();
                      } catch {
                        // ignore
                      }
                      if (abortControllerRef.current) {
                        abortControllerRef.current.abort();
                        abortControllerRef.current = null;
                      }
                      return;
                    }
                  } catch (parseError) {
                    console.error('解析SSE消息失败:', parseError, currentData);
                  }

                  // 重置事件状态
                  currentData = '';
                  currentEvent = 'message';
                }
                continue;
              }

              // 解析字段
              if (line.startsWith('event:')) {
                currentEvent = line.substring(6).trim();
              } else if (line.startsWith('data:')) {
                // 如果已有数据，添加换行符（支持多行数据）
                if (currentData) {
                  currentData += '\n';
                }
                // 移除'data:'前缀
                currentData += line.replace(/^data:\s?/, '');
              }
            }

            // 继续读取
            readStream();
          }).catch(error => {
            if (error instanceof Error && error.name === 'AbortError') {
              console.log('SSE连接已中止');
              return;
            }

            // 若已收到终止事件，则把网络中断视为正常结束，避免DevOverlay报错
            if (terminalEventRef.current !== 'none') {
              return;
            }

            const errorMessage = normalizeSseErrorMessage(
              error instanceof Error ? error.message : String(error)
            );
            console.error('读取SSE流失败:', error);
            updateState({
              error: errorMessage,
              isConnected: false
            });
            onErrorRef.current?.(errorMessage);
          });
        };

        readStream();
      }).catch(error => {
        if (error instanceof Error && error.name === 'AbortError') {
          console.log('SSE请求已中止');
          return;
        }
        const errorMessage = normalizeSseErrorMessage(
          error instanceof Error ? error.message : String(error)
        );
        console.error('SSE连接失败:', error);
        updateState({
          error: errorMessage,
          isConnecting: false,
          isConnected: false
        });
        onErrorRef.current?.(errorMessage);
      });

    } catch (error) {
      const errorMessage = normalizeSseErrorMessage(
        error instanceof Error ? error.message : '未知错误'
      );
      console.error('初始化SSE失败:', error);
      updateState({
        error: errorMessage,
        isConnecting: false,
        isConnected: false
      });
      onErrorRef.current?.(errorMessage);
    }
  }, [requirement, updateState, clearTimeoutTimer, resetTimeoutTimer]);

  /**
   * 断开连接
   */
  const disconnect = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }

    clearTimeoutTimer();
    updateState({
      isConnected: false,
      isConnecting: false
    });
  }, [clearTimeoutTimer, updateState]);

  /**
   * 重置状态
   */
  const reset = useCallback(() => {
    disconnect();
    updateState({
      error: null,
      messages: [],
      isCompleted: false,
      finalResult: undefined
    });
  }, [disconnect, updateState]);

  // 自动连接
  useEffect(() => {
    if (autoConnect && requirement) {
      connect();
    }

    return () => {
      disconnect();
    };
  }, [autoConnect, requirement, connect, disconnect]);

  return {
    // 状态
    state,
    isConnected: state.isConnected,
    isConnecting: state.isConnecting,
    error: state.error,
    messages: state.messages,
    isCompleted: state.isCompleted,
    finalResult: state.finalResult,

    // 方法
    connect,
    disconnect,
    reset
  };
}
