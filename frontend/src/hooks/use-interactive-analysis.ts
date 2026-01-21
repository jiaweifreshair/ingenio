/**
 * 交互式分析Hook
 *
 * 用于管理AI深度思考的交互式分析流程:
 * - 启动交互式分析会话
 * - SSE流式接收当前步骤的分析过程
 * - 确认当前步骤,进入下一步
 * - 提出修改建议,重新执行当前步骤
 */
'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { getToken } from '@/lib/auth/token';
import type { AnalysisProgressMessage } from './use-analysis-sse';

/**
 * 会话状态
 */
export interface InteractiveSession {
  sessionId: string;
  currentStep: number;
  status: 'RUNNING' | 'WAITING_CONFIRMATION' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  stepResults: Record<number, unknown>;
  stepFeedback: Record<number, string>;
  messages: AnalysisProgressMessage[];
}

/**
 * Hook状态
 */
export interface InteractiveAnalysisState {
  session: InteractiveSession | null;
  isConnected: boolean;
  isLoading: boolean;
  error: string | null;
  currentStepMessages: AnalysisProgressMessage[];
}

/**
 * Hook选项
 */
export interface UseInteractiveAnalysisOptions {
  onStepComplete?: (step: number, result: unknown) => void;
  onAllComplete?: (session: InteractiveSession) => void;
  onError?: (error: string) => void;
}

/**
 * 使用交互式分析
 */
export function useInteractiveAnalysis(options: UseInteractiveAnalysisOptions = {}) {
  const { onStepComplete, onAllComplete, onError } = options;

  const [state, setState] = useState<InteractiveAnalysisState>({
    session: null,
    isConnected: false,
    isLoading: false,
    error: null,
    currentStepMessages: []
  });

  const abortControllerRef = useRef<AbortController | null>(null);
  const onStepCompleteRef = useRef(onStepComplete);
  const onAllCompleteRef = useRef(onAllComplete);
  const onErrorRef = useRef(onError);

  // 更新回调ref
  useEffect(() => {
    onStepCompleteRef.current = onStepComplete;
    onAllCompleteRef.current = onAllComplete;
    onErrorRef.current = onError;
  }, [onStepComplete, onAllComplete, onError]);

  /**
   * 启动交互式分析会话
   */
  const startSession = useCallback(async (requirement: string) => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));

    try {
      const baseUrl = getApiBaseUrl();
      const token = getToken();

      const response = await fetch(`${baseUrl}/v1/interactive-analysis/start`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': token } : {})
        },
        body: JSON.stringify({ requirement })
      });

      if (!response.ok) {
        throw new Error(`启动会话失败: ${response.status} ${response.statusText}`);
      }

      const data = await response.json();

      const session: InteractiveSession = {
        sessionId: data.sessionId,
        currentStep: data.currentStep,
        status: data.status,
        stepResults: {},
        stepFeedback: {},
        messages: []
      };

      setState(prev => ({
        ...prev,
        session,
        isLoading: false
      }));

      // 自动开始执行第一步
      await executeCurrentStep(session.sessionId);

      return session.sessionId;

    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : '启动会话失败';
      setState(prev => ({ ...prev, error: errorMsg, isLoading: false }));
      onErrorRef.current?.(errorMsg);
      throw error;
    }
  }, []);

  /**
   * 执行当前步骤
   */
  const executeCurrentStep = useCallback(async (sessionId: string) => {
    // 关闭现有连接
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    setState(prev => ({
      ...prev,
      isConnected: true,
      currentStepMessages: []
    }));

    const baseUrl = getApiBaseUrl();
    const token = getToken();
    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    try {
      const response = await fetch(`${baseUrl}/v1/interactive-analysis/${sessionId}/stream`, {
        method: 'GET',
        headers: {
          'Accept': 'text/event-stream',
          ...(token ? { 'Authorization': token } : {})
        },
        signal: abortController.signal
      });

      if (!response.ok) {
        throw new Error(`SSE请求失败: ${response.status} ${response.statusText}`);
      }

      if (!response.body) {
        throw new Error('响应体为空');
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let currentEvent = 'message';
      let currentData = '';

      const readStream = (): void => {
        reader.read().then(({ done, value }) => {
          if (done) {
            setState(prev => ({ ...prev, isConnected: false }));
            return;
          }

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split(/\r\n|\r|\n/);
          buffer = lines.pop() || '';

          for (const line of lines) {
            if (line.startsWith(':')) continue;

            if (line.trim() === '') {
              if (currentData) {
                try {
                  const data = JSON.parse(currentData);
                  const eventType = currentEvent || 'message';

                  if (eventType === 'progress') {
                    const message: AnalysisProgressMessage = data;
                    setState(prev => ({
                      ...prev,
                      currentStepMessages: [...prev.currentStepMessages, message],
                      session: prev.session ? {
                        ...prev.session,
                        messages: [...prev.session.messages, message]
                      } : null
                    }));
                  } else if (eventType === 'complete') {
                    const { step, result } = data;
                    setState(prev => ({
                      ...prev,
                      isConnected: false,
                      session: prev.session ? {
                        ...prev.session,
                        status: 'WAITING_CONFIRMATION',
                        stepResults: {
                          ...prev.session.stepResults,
                          [step]: result
                        }
                      } : null
                    }));
                    onStepCompleteRef.current?.(step, result);
                    reader.cancel();
                    if (abortControllerRef.current) {
                      abortControllerRef.current.abort();
                      abortControllerRef.current = null;
                    }
                    return;
                  } else if (eventType === 'error') {
                    const errorMsg = data.error || '执行失败';
                    setState(prev => ({
                      ...prev,
                      error: errorMsg,
                      isConnected: false,
                      session: prev.session ? {
                        ...prev.session,
                        status: 'FAILED'
                      } : null
                    }));
                    onErrorRef.current?.(errorMsg);
                    reader.cancel();
                    if (abortControllerRef.current) {
                      abortControllerRef.current.abort();
                      abortControllerRef.current = null;
                    }
                    return;
                  }
                } catch (parseError) {
                  console.error('解析SSE消息失败:', parseError, currentData);
                }

                currentData = '';
                currentEvent = 'message';
              }
              continue;
            }

            if (line.startsWith('event:')) {
              currentEvent = line.substring(6).trim();
            } else if (line.startsWith('data:')) {
              if (currentData) {
                currentData += '\n';
              }
              currentData += line.replace(/^data:\s?/, '');
            }
          }

          readStream();
        }).catch(error => {
          if (error instanceof Error && error.name === 'AbortError') {
            return;
          }
          console.error('读取SSE流失败:', error);
          setState(prev => ({
            ...prev,
            error: error.message,
            isConnected: false
          }));
          onErrorRef.current?.(error.message);
        });
      };

      readStream();

    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') {
        return;
      }
      const errorMsg = error instanceof Error ? error.message : 'SSE连接失败';
      setState(prev => ({
        ...prev,
        error: errorMsg,
        isConnected: false
      }));
      onErrorRef.current?.(errorMsg);
    }
  }, []);

  /**
   * 确认当前步骤(不自动进入下一步)
   */
  const confirmStep = useCallback(async (step: number) => {
    if (!state.session) {
      throw new Error('会话不存在');
    }

    setState(prev => ({ ...prev, isLoading: true, error: null }));

    try {
      const baseUrl = getApiBaseUrl();
      const token = getToken();

      const response = await fetch(`${baseUrl}/v1/interactive-analysis/${state.session.sessionId}/confirm`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': token } : {})
        },
        body: JSON.stringify({ step })
      });

      if (!response.ok) {
        throw new Error(`确认步骤失败: ${response.status} ${response.statusText}`);
      }

      const data = await response.json();

      setState(prev => ({
        ...prev,
        isLoading: false,
        session: prev.session ? {
          ...prev.session,
          currentStep: data.currentStep,
          status: data.status
        } : null
      }));

      // 如果已完成,触发回调
      if (data.isCompleted && state.session) {
        onAllCompleteRef.current?.(state.session);
      }
      // 不再自动执行下一步

    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : '确认步骤失败';
      setState(prev => ({ ...prev, error: errorMsg, isLoading: false }));
      onErrorRef.current?.(errorMsg);
      throw error;
    }
  }, [state.session]);

  /**
   * 确认并进入下一步
   */
  const advanceToNextStep = useCallback(async (step: number) => {
    if (!state.session) {
      throw new Error('会话不存在');
    }

    // 先确认当前步骤
    await confirmStep(step);

    // 如果不是最后一步,执行下一步
    if (step < 6) {
      await executeCurrentStep(state.session.sessionId);
    }
  }, [state.session, confirmStep, executeCurrentStep]);

  /**
   * 提出修改建议,重新执行当前步骤
   */
  const modifyStep = useCallback(async (step: number, feedback: string) => {
    if (!state.session) {
      throw new Error('会话不存在');
    }

    setState(prev => ({ ...prev, isLoading: true, error: null }));

    try {
      const baseUrl = getApiBaseUrl();
      const token = getToken();

      const response = await fetch(`${baseUrl}/v1/interactive-analysis/${state.session.sessionId}/modify`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': token } : {})
        },
        body: JSON.stringify({ step, feedback })
      });

      if (!response.ok) {
        throw new Error(`修改步骤失败: ${response.status} ${response.statusText}`);
      }

      const data = await response.json();

      setState(prev => ({
        ...prev,
        isLoading: false,
        currentStepMessages: [],
        session: prev.session ? {
          ...prev.session,
          currentStep: data.currentStep,
          status: data.status
        } : null
      }));

      // 重新执行当前步骤
      await executeCurrentStep(state.session.sessionId);

    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : '修改步骤失败';
      setState(prev => ({ ...prev, error: errorMsg, isLoading: false }));
      onErrorRef.current?.(errorMsg);
      throw error;
    }
  }, [state.session, executeCurrentStep]);

  /**
   * 取消会话
   */
  const cancelSession = useCallback(async () => {
    if (!state.session) {
      return;
    }

    // 关闭SSE连接
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }

    try {
      const baseUrl = getApiBaseUrl();
      const token = getToken();

      await fetch(`${baseUrl}/v1/interactive-analysis/${state.session.sessionId}/cancel`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': token } : {})
        }
      });

      setState(prev => ({
        ...prev,
        session: prev.session ? {
          ...prev.session,
          status: 'CANCELLED'
        } : null,
        isConnected: false
      }));

    } catch (error) {
      console.error('取消会话失败:', error);
    }
  }, [state.session]);

  // 清理
  useEffect(() => {
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, []);

  return {
    // 状态
    session: state.session,
    isConnected: state.isConnected,
    isLoading: state.isLoading,
    error: state.error,
    currentStepMessages: state.currentStepMessages,

    // 方法
    startSession,
    confirmStep,
    advanceToNextStep,
    modifyStep,
    cancelSession
  };
}
