/**
 * 交互式分析SSE Hook
 * 支持步骤暂停、用户确认、继续分析的完整流程
 */
'use client';

import { useCallback, useRef, useState } from 'react';
import { getToken } from '@/lib/auth/token';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { AnalysisProgressMessage } from './use-analysis-sse';

export interface InteractiveAnalysisState {
  sessionId: string | null;
  currentStep: number;
  status: 'IDLE' | 'STARTING' | 'RUNNING' | 'WAITING_CONFIRMATION' | 'COMPLETED' | 'FAILED';
  messages: AnalysisProgressMessage[];
  stepResults: Record<number, unknown>;
  error: string | null;
}

export function useInteractiveAnalysisSse() {
  const [state, setState] = useState<InteractiveAnalysisState>({
    sessionId: null,
    currentStep: 0,
    status: 'IDLE',
    messages: [],
    stepResults: {},
    error: null
  });

  const abortControllerRef = useRef<AbortController | null>(null);

  const startSession = useCallback(async (requirement: string) => {
    const baseUrl = getApiBaseUrl();
    const token = getToken();

    setState(prev => ({ ...prev, status: 'STARTING', error: null }));

    try {
      const response = await fetch(`${baseUrl}/v1/interactive-analysis/start`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': token } : {})
        },
        body: JSON.stringify({ requirement })
      });

      if (!response.ok) {
        throw new Error(`启动会话失败: ${response.status}`);
      }

      const data = await response.json();
      setState(prev => ({
        ...prev,
        sessionId: data.sessionId,
        currentStep: data.currentStep,
        status: 'RUNNING'
      }));

      // 自动开始执行第一步
      executeCurrentStep(data.sessionId);
    } catch (error) {
      setState(prev => ({
        ...prev,
        status: 'FAILED',
        error: error instanceof Error ? error.message : '启动会话失败'
      }));
    }
  }, []);

  const executeCurrentStep = useCallback((sessionId: string) => {
    const baseUrl = getApiBaseUrl();
    const token = getToken();

    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    fetch(`${baseUrl}/v1/interactive-analysis/${sessionId}/stream`, {
      method: 'GET',
      headers: {
        'Accept': 'text/event-stream',
        ...(token ? { 'Authorization': token } : {})
      },
      signal: abortController.signal
    }).then(response => {
      if (!response.ok) {
        throw new Error(`SSE请求失败: ${response.status}`);
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

                  if (currentEvent === 'progress') {
                    const message: AnalysisProgressMessage = data;
                    setState(prev => ({
                      ...prev,
                      messages: [...prev.messages, message]
                    }));
                  } else if (currentEvent === 'complete') {
                    setState(prev => ({
                      ...prev,
                      status: 'WAITING_CONFIRMATION',
                      stepResults: {
                        ...prev.stepResults,
                        [data.step]: data.result
                      }
                    }));
                    reader.cancel();
                    abortController.abort();
                  } else if (currentEvent === 'error') {
                    setState(prev => ({
                      ...prev,
                      status: 'FAILED',
                      error: data.error || '分析失败'
                    }));
                    reader.cancel();
                    abortController.abort();
                  }
                } catch (e) {
                  console.error('解析SSE消息失败:', e);
                }

                currentData = '';
                currentEvent = 'message';
              }
              continue;
            }

            if (line.startsWith('event:')) {
              currentEvent = line.substring(6).trim();
            } else if (line.startsWith('data:')) {
              if (currentData) currentData += '\n';
              currentData += line.replace(/^data:\s?/, '');
            }
          }

          readStream();
        }).catch(error => {
          if (error.name === 'AbortError') return;
          setState(prev => ({
            ...prev,
            status: 'FAILED',
            error: error.message
          }));
        });
      };

      readStream();
    }).catch(error => {
      if (error.name === 'AbortError') return;
      setState(prev => ({
        ...prev,
        status: 'FAILED',
        error: error.message
      }));
    });
  }, []);

  const confirmStep = useCallback(async (step: number) => {
    if (!state.sessionId) return;

    const baseUrl = getApiBaseUrl();
    const token = getToken();

    try {
      const response = await fetch(`${baseUrl}/v1/interactive-analysis/${state.sessionId}/confirm`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': token } : {})
        },
        body: JSON.stringify({ step })
      });

      if (!response.ok) {
        throw new Error(`确认步骤失败: ${response.status}`);
      }

      const data = await response.json();

      if (data.isCompleted) {
        setState(prev => ({ ...prev, status: 'COMPLETED' }));
      } else {
        setState(prev => ({
          ...prev,
          currentStep: data.currentStep,
          status: 'RUNNING'
        }));
        executeCurrentStep(state.sessionId!);
      }
    } catch (error) {
      setState(prev => ({
        ...prev,
        error: error instanceof Error ? error.message : '确认步骤失败'
      }));
    }
  }, [state.sessionId, executeCurrentStep]);

  const modifyStep = useCallback(async (step: number, feedback: string) => {
    if (!state.sessionId) return;

    const baseUrl = getApiBaseUrl();
    const token = getToken();

    try {
      const response = await fetch(`${baseUrl}/v1/interactive-analysis/${state.sessionId}/modify`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': token } : {})
        },
        body: JSON.stringify({ step, feedback })
      });

      if (!response.ok) {
        throw new Error(`修改步骤失败: ${response.status}`);
      }

      setState(prev => ({ ...prev, status: 'RUNNING' }));
      executeCurrentStep(state.sessionId!);
    } catch (error) {
      setState(prev => ({
        ...prev,
        error: error instanceof Error ? error.message : '修改步骤失败'
      }));
    }
  }, [state.sessionId, executeCurrentStep]);

  return {
    state,
    startSession,
    confirmStep,
    modifyStep
  };
}
