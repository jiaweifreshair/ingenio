/**
 * SSE代码生成流式订阅Hook
 * 订阅后端的流式代码生成接口，实时接收AI生成的代码
 *
 * 核心功能:
 * 1. 实时接收AI思考过程
 * 2. 逐步显示生成的文件内容
 * 3. 追踪每个文件的生成进度
 * 4. 提供完整的生成文件列表
 *
 * 使用场景:
 * - 向导页面实时显示代码生成过程
 * - 用户可见AI思考和文件生成进度
 * - 生成完成后可立即预览代码
 *
 * @author Ingenio Team
 * @since 2.0.0
 */
'use client';

import { useEffect, useRef, useState, useCallback } from 'react';
import { getToken } from '@/lib/auth/token';

import { getApiBaseUrl } from '@/lib/api/base-url';
import { generateTraceId } from '@/lib/api/trace-id';

/**
 * 生成的文件信息
 */
export interface GeneratedFile {
  path: string;
  content: string;
  type: string;
  completed: boolean;
  edited: boolean;
}

/**
 * SSE事件类型
 */
export type SseEventType =
  | 'thinking'        // AI思考中
  | 'file-start'      // 开始生成文件
  | 'file-content'    // 文件内容chunk
  | 'file-complete'   // 文件生成完成
  | 'status'          // 状态更新
  | 'error'           // 错误信息
  | 'complete';       // 全部完成

/**
 * SSE事件消息
 */
export interface SseEventMessage {
  type: SseEventType;
  message?: string;
  path?: string;
  content?: string;
  fileType?: string;
  duration?: number;
  files?: GeneratedFile[];
  metadata?: Record<string, unknown>;
}

/**
 * 代码生成SSE状态
 */
export interface CodeGenerationSseState {
  isConnected: boolean;
  isConnecting: boolean;
  error: string | null;
  isCompleted: boolean;

  // 思考状态
  thinkingMessage: string | null;
  thinkingDuration: number | null;

  // 文件生成状态
  currentFile: string | null;          // 当前正在生成的文件路径
  generatedFiles: Map<string, GeneratedFile>; // 已生成的文件

  // 进度统计
  totalFiles: number;
  completedFiles: number;
}

/**
 * Hook选项
 */
export interface UseCodeGenerationStreamOptions {
  appSpecId: string;
  regenerate?: boolean;
  autoConnect?: boolean;
  onThinking?: (message: string, duration: number) => void;
  onFileStart?: (path: string, fileType: string) => void;
  onFileContent?: (path: string, content: string) => void;
  onFileComplete?: (path: string) => void;
  onComplete?: (files: GeneratedFile[]) => void;
  onError?: (error: string) => void;
}

/**
 * 使用SSE订阅代码生成流
 */
export function useCodeGenerationStream(options: UseCodeGenerationStreamOptions) {
  const {
    appSpecId,
    regenerate = false,
    autoConnect = true,
    onThinking,
    onFileStart,
    onFileContent,
    onFileComplete,
    onComplete,
    onError
  } = options;

  const abortControllerRef = useRef<AbortController | null>(null);
  const timeoutIdRef = useRef<NodeJS.Timeout | null>(null);

  // 使用ref存储回调函数，避免依赖变化导致无限循环
  const onThinkingRef = useRef(onThinking);
  const onFileStartRef = useRef(onFileStart);
  const onFileContentRef = useRef(onFileContent);
  const onFileCompleteRef = useRef(onFileComplete);
  const onCompleteRef = useRef(onComplete);
  const onErrorRef = useRef(onError);

  // 更新ref当回调变化时
  useEffect(() => {
    onThinkingRef.current = onThinking;
    onFileStartRef.current = onFileStart;
    onFileContentRef.current = onFileContent;
    onFileCompleteRef.current = onFileComplete;
    onCompleteRef.current = onComplete;
    onErrorRef.current = onError;
  }, [onThinking, onFileStart, onFileContent, onFileComplete, onComplete, onError]);

  const [state, setState] = useState<CodeGenerationSseState>({
    isConnected: false,
    isConnecting: false,
    error: null,
    isCompleted: false,
    thinkingMessage: null,
    thinkingDuration: null,
    currentFile: null,
    generatedFiles: new Map(),
    totalFiles: 0,
    completedFiles: 0
  });

  /**
   * 更新状态
   */
  const updateState = useCallback((updates: Partial<CodeGenerationSseState>) => {
    setState(prev => ({ ...prev, ...updates }));
  }, []);

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
   * 处理thinking事件
   */
  const handleThinking = useCallback((event: SseEventMessage) => {
    const message = event.message || '正在思考...';
    const duration = event.duration || 0;

    updateState({
      thinkingMessage: message,
      thinkingDuration: duration
    });

    onThinkingRef.current?.(message, duration);
  }, [updateState]);

  /**
   * 处理file-start事件
   */
  const handleFileStart = useCallback((event: SseEventMessage) => {
    const path = event.path || '';
    const fileType = event.fileType || 'text';

    // 创建新文件记录
    const newFile: GeneratedFile = {
      path,
      content: '',
      type: fileType,
      completed: false,
      edited: false
    };

    setState(prev => {
      const newFiles = new Map(prev.generatedFiles);
      newFiles.set(path, newFile);

      return {
        ...prev,
        currentFile: path,
        generatedFiles: newFiles,
        totalFiles: Math.max(prev.totalFiles, newFiles.size)
      };
    });

    onFileStartRef.current?.(path, fileType);
  }, []);

  /**
   * 处理file-content事件
   */
  const handleFileContent = useCallback((event: SseEventMessage) => {
    const path = event.path || '';
    const content = event.content || '';

    setState(prev => {
      const newFiles = new Map(prev.generatedFiles);
      const existingFile = newFiles.get(path);

      if (existingFile) {
        newFiles.set(path, {
          ...existingFile,
          content: existingFile.content + content
        });
      }

      return {
        ...prev,
        generatedFiles: newFiles
      };
    });

    onFileContentRef.current?.(path, content);
  }, []);

  /**
   * 处理file-complete事件
   */
  const handleFileComplete = useCallback((event: SseEventMessage) => {
    const path = event.path || '';

    setState(prev => {
      const newFiles = new Map(prev.generatedFiles);
      const existingFile = newFiles.get(path);

      if (existingFile) {
        newFiles.set(path, {
          ...existingFile,
          completed: true
        });
      }

      return {
        ...prev,
        generatedFiles: newFiles,
        completedFiles: prev.completedFiles + 1
      };
    });

    onFileCompleteRef.current?.(path);
  }, []);

  /**
   * 处理complete事件
   */
  const handleComplete = useCallback((event: SseEventMessage) => {
    clearTimeoutTimer();

    const files = event.files || Array.from(state.generatedFiles.values());

    updateState({
      isCompleted: true,
      isConnected: false,
      currentFile: null,
      thinkingMessage: null
    });

    onCompleteRef.current?.(files);
  }, [state.generatedFiles, updateState, clearTimeoutTimer]);

  /**
   * 处理error事件
   */
  const handleError = useCallback((event: SseEventMessage) => {
    clearTimeoutTimer();

    const errorMsg = event.message || '代码生成失败';

    updateState({
      error: errorMsg,
      isConnected: false,
      isConnecting: false
    });

    onErrorRef.current?.(errorMsg);
  }, [updateState, clearTimeoutTimer]);

  /**
   * 连接SSE
   */
  const connect = useCallback(() => {
    if (!appSpecId) {
      updateState({
        error: 'appSpecId不能为空'
      });
      return;
    }

    // 关闭现有连接和超时计时器
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    clearTimeoutTimer();

    updateState({
      isConnecting: true,
      error: null,
      isCompleted: false,
      generatedFiles: new Map(),
      totalFiles: 0,
      completedFiles: 0,
      currentFile: null,
      thinkingMessage: null,
      thinkingDuration: null
    });

    // 代码生成可能需要较长时间（10分钟）
    const TIMEOUT_MS = 600000; // 10分钟
    timeoutIdRef.current = setTimeout(() => {
      console.warn('⏰ 代码生成超时（10分钟）');
      updateState({
        error: '代码生成超时，请稍后重试',
        isConnected: false,
        isConnecting: false
      });
      onErrorRef.current?.('代码生成超时（10分钟），请稍后重试');

      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
    }, TIMEOUT_MS);

    try {
      const abortController = new AbortController();
      abortControllerRef.current = abortController;

      const baseUrl = getApiBaseUrl();
      const apiUrl = `${baseUrl}/v1/code-generation/stream`;

      // 获取认证token
      const token = getToken();

      fetch(apiUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          'X-Trace-Id': generateTraceId(),
          ...(token && { 'Authorization': token })
        },
        body: JSON.stringify({
          appSpecId,
          regenerate,
          streaming: true
        }),
        signal: abortController.signal
      }).then(response => {
        if (!response.ok) {
          throw new Error(`SSE请求失败: ${response.status}`);
        }

        if (!response.body) {
          throw new Error('响应体为空');
        }

        updateState({
          isConnected: true,
          isConnecting: false
        });

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let currentEvent: SseEventType | null = null;
        let currentData = '';

        // 递归读取流
        const readStream = (): void => {
          reader.read().then(({ done, value }) => {
            if (done) {
              console.log('SSE流读取完成');
              clearTimeoutTimer();
              updateState({
                isConnected: false,
                isCompleted: true
              });
              return;
            }

            // 解码数据并拼接到缓冲区
            buffer += decoder.decode(value, { stream: true });

            // 处理SSE消息
            // 按照SSE标准，必须按行处理
            const lines = buffer.split(/\r\n|\r|\n/);
            //保留最后一个可能不完整的行
            buffer = lines.pop() || '';

            for (const line of lines) {
              // 忽略注释
              if (line.startsWith(':')) continue;

              // 空行表示事件结束，分发事件
              if (line.trim() === '') {
                if (currentData) {
                  try {
                    const data: SseEventMessage = JSON.parse(currentData);
                    const eventType = currentEvent || 'status';

                    console.log('SSE接收:', eventType, data);

                    // 路由到对应的处理函数
                    switch (eventType) {
                      case 'thinking':
                        handleThinking(data);
                        break;
                      case 'file-start':
                        handleFileStart(data);
                        break;
                      case 'file-content':
                        handleFileContent(data);
                        break;
                      case 'file-complete':
                        handleFileComplete(data);
                        break;
                      case 'complete':
                        handleComplete(data);
                        break;
                      case 'error':
                        handleError(data);
                        break;
                      default:
                        console.log('未知事件类型:', eventType);
                    }
                  } catch (parseError) {
                    console.error('解析SSE消息失败:', parseError, currentData);
                  }

                  // 重置事件状态
                  currentData = '';
                  currentEvent = null;
                }
                continue;
              }

              // 解析字段
              if (line.startsWith('event:')) {
                currentEvent = line.substring(6).trim() as SseEventType;
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
            if (error.name === 'AbortError') {
              console.log('SSE连接已中止');
              return;
            }

            console.error('读取SSE流失败:', error);
            clearTimeoutTimer();
            updateState({
              error: error.message,
              isConnected: false
            });
            onErrorRef.current?.(error.message);
          });
        };

        readStream();
      }).catch(error => {
        if (error.name === 'AbortError') {
          console.log('SSE请求已中止');
          return;
        }

        console.error('SSE连接失败:', error);
        clearTimeoutTimer();
        updateState({
          error: error.message,
          isConnecting: false,
          isConnected: false
        });
        onErrorRef.current?.(error.message);
      });

    } catch (error) {
      console.error('初始化SSE失败:', error);
      clearTimeoutTimer();
      updateState({
        error: error instanceof Error ? error.message : '未知错误',
        isConnecting: false,
        isConnected: false
      });
      onErrorRef.current?.(error instanceof Error ? error.message : '未知错误');
    }
  }, [
    appSpecId,
    regenerate,
    updateState,
    clearTimeoutTimer,
    handleThinking,
    handleFileStart,
    handleFileContent,
    handleFileComplete,
    handleComplete,
    handleError
  ]);

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
  }, [updateState, clearTimeoutTimer]);

  /**
   * 重置状态
   */
  const reset = useCallback(() => {
    disconnect();
    updateState({
      error: null,
      isCompleted: false,
      generatedFiles: new Map(),
      totalFiles: 0,
      completedFiles: 0,
      currentFile: null,
      thinkingMessage: null,
      thinkingDuration: null
    });
  }, [disconnect, updateState]);

  // 自动连接
  useEffect(() => {
    if (autoConnect && appSpecId) {
      connect();
    }

    return () => {
      disconnect();
    };
  }, [autoConnect, appSpecId, connect, disconnect]);

  return {
    // 状态
    state,
    isConnected: state.isConnected,
    isConnecting: state.isConnecting,
    error: state.error,
    isCompleted: state.isCompleted,

    // 思考状态
    thinkingMessage: state.thinkingMessage,
    thinkingDuration: state.thinkingDuration,

    // 文件生成状态
    currentFile: state.currentFile,
    generatedFiles: Array.from(state.generatedFiles.values()),
    generatedFilesMap: state.generatedFiles,

    // 进度统计
    totalFiles: state.totalFiles,
    completedFiles: state.completedFiles,
    progress: state.totalFiles > 0 ? (state.completedFiles / state.totalFiles) * 100 : 0,

    // 方法
    connect,
    disconnect,
    reset
  };
}
