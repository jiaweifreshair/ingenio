/**
 * 交互式分析SSE Hook
 * 支持步骤暂停、用户确认、继续分析的完整流程
 */
'use client';

import { useCallback, useRef, useState } from 'react';
import { getToken } from '@/lib/auth/token';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { AnalysisProgressMessage } from './use-analysis-sse';
import type { StepConfirmPayload } from '@/types/analysis-step-results';

export interface InteractiveAnalysisState {
  sessionId: string | null;
  currentStep: number;
  status: 'IDLE' | 'STARTING' | 'RUNNING' | 'WAITING_CONFIRMATION' | 'COMPLETED' | 'FAILED';
  messages: AnalysisProgressMessage[];
  stepResults: Record<number, unknown>;
  error: string | null;
}

/**
 * 规范化交互式分析的网络错误提示
 *
 * 是什么：交互式分析（含 SSE）请求的错误提示映射。
 * 做什么：将“Failed to fetch”等浏览器原始错误转换为更可读、可定位的中文提示。
 * 为什么：SSE 场景下浏览器通常只给出泛化错误，用户难以判断是后端未启动、跨域/CORS 还是网络代理问题。
 */
function normalizeInteractiveAnalysisErrorMessage(message: string): string {
  const normalized = message.toLowerCase();

  if (
    normalized.includes('failed to fetch') ||
    normalized.includes('networkerror') ||
    normalized.includes('econnrefused')
  ) {
    return '网络连接失败：请检查后端服务是否可访问（默认 http://localhost:8080），以及当前访问地址是否在后端 CORS 白名单中';
  }

  if (normalized.includes('timeout')) {
    return '网络请求超时，请稍后重试';
  }

  return message;
}

/**
 * 从后端返回中提取可展示的错误信息
 *
 * 说明：
 * - 兼容 Spring Result（message/error）与 Sa-Token 风格（msg）等不同返回结构；
 * - 在响应体结构异常时，提供兜底错误信息，避免继续用 undefined sessionId 发起后续请求。
 */
function extractBackendErrorMessage(data: unknown): string | null {
  if (!data || typeof data !== 'object') return null;
  const record = data as Record<string, unknown>;

  const code = record.code;
  const isErrorCode = typeof code === 'number' ? code >= 400 : typeof code === 'string' ? code !== '0000' && code !== '0' : false;
  if (!isErrorCode) return null;

  const message =
    (typeof record.error === 'string' && record.error) ||
    (typeof record.message === 'string' && record.message) ||
    (typeof record.msg === 'string' && record.msg) ||
    null;

  return message || '请求失败，请稍后重试';
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

      const rawText = await response.text();
      let data: unknown;
      try {
        data = JSON.parse(rawText);
      } catch (e) {
        console.error('[InteractiveAnalysis] 启动会话返回非JSON:', e);
        throw new Error('启动会话失败：后端返回了无效响应');
      }

      const backendError = extractBackendErrorMessage(data);
      if (backendError) {
        throw new Error(backendError);
      }

      const record = data as Record<string, unknown>;
      const sessionId = typeof record.sessionId === 'string' ? record.sessionId : null;
      const currentStep = typeof record.currentStep === 'number' ? record.currentStep : null;

      if (!sessionId || currentStep == null) {
        throw new Error('启动会话失败：后端未返回有效的 sessionId/currentStep');
      }

      setState(prev => ({
        ...prev,
        sessionId,
        currentStep,
        status: 'RUNNING'
      }));

      // 自动开始执行第一步
      executeCurrentStep(sessionId);
    } catch (error) {
      const message = normalizeInteractiveAnalysisErrorMessage(error instanceof Error ? error.message : '启动会话失败');
      setState(prev => ({
        ...prev,
        status: 'FAILED',
        error: message
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

      const contentType = response.headers.get('content-type') || '';
      if (!contentType.includes('text/event-stream')) {
        return response.text().then(text => {
          let backendError: string | null = null;
          try {
            const parsed = JSON.parse(text) as unknown;
            backendError = extractBackendErrorMessage(parsed);
          } catch {
            // ignore：保持兜底错误文案
          }
          if (backendError) {
            throw new Error(backendError);
          }
          const preview = text.length > 200 ? `${text.slice(0, 200)}...` : text;
          throw new Error(`SSE响应异常（非event-stream）：${preview || '空响应'}`);
        });
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
          const message = normalizeInteractiveAnalysisErrorMessage(error instanceof Error ? error.message : String(error));
          setState(prev => ({
            ...prev,
            status: 'FAILED',
            error: message
          }));
        });
      };

      readStream();
    }).catch(error => {
      if (error.name === 'AbortError') return;
      const message = normalizeInteractiveAnalysisErrorMessage(error instanceof Error ? error.message : String(error));
      setState(prev => ({
        ...prev,
        status: 'FAILED',
        error: message
      }));
    });
  }, []);

  const confirmStep = useCallback(async (step: number, payload?: StepConfirmPayload) => {
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
        body: JSON.stringify({
          step,
          ...(payload?.selectedStyleId ? { selectedStyleId: payload.selectedStyleId } : {})
        })
      });

      if (!response.ok) {
        throw new Error(`确认步骤失败: ${response.status}`);
      }

      const rawText = await response.text();
      let data: unknown;
      try {
        data = JSON.parse(rawText);
      } catch (e) {
        console.error('[InteractiveAnalysis] 确认步骤返回非JSON:', e);
        throw new Error('确认步骤失败：后端返回了无效响应');
      }

      const backendError = extractBackendErrorMessage(data);
      if (backendError) {
        throw new Error(backendError);
      }

      const record = data as Record<string, unknown>;
      const isCompleted = record.isCompleted === true;
      const nextStep = typeof record.currentStep === 'number' ? record.currentStep : null;

      if (isCompleted) {
        setState(prev => ({ ...prev, status: 'COMPLETED' }));
      } else {
        if (nextStep == null) {
          throw new Error('确认步骤失败：后端未返回有效的 currentStep');
        }
        setState(prev => ({
          ...prev,
          currentStep: nextStep,
          status: 'RUNNING'
        }));
        executeCurrentStep(state.sessionId!);
      }
    } catch (error) {
      const message = normalizeInteractiveAnalysisErrorMessage(error instanceof Error ? error.message : '确认步骤失败');
      setState(prev => ({
        ...prev,
        error: message
      }));
    }
  }, [state.sessionId, executeCurrentStep]);

  const modifyStep = useCallback(async (step: number, feedback: string) => {
    if (!state.sessionId) return;
    const trimmedFeedback = feedback.trim();
    if (!trimmedFeedback) {
      setState(prev => ({ ...prev, error: '修改建议不能为空' }));
      return;
    }

    const baseUrl = getApiBaseUrl();
    const token = getToken();

    try {
      const response = await fetch(`${baseUrl}/v1/interactive-analysis/${state.sessionId}/modify`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': token } : {})
        },
        body: JSON.stringify({ step, feedback: trimmedFeedback })
      });

      if (!response.ok) {
        throw new Error(`修改步骤失败: ${response.status}`);
      }

      const rawText = await response.text();
      if (rawText) {
        let backendError: string | null = null;
        try {
          const parsed = JSON.parse(rawText) as unknown;
          backendError = extractBackendErrorMessage(parsed);
        } catch {
          // ignore：后端也可能返回空体或非标准字段，这里只做防御性判断
        }
        if (backendError) {
          throw new Error(backendError);
        }
      }

      setState(prev => ({ ...prev, status: 'RUNNING' }));
      executeCurrentStep(state.sessionId!);
    } catch (error) {
      const message = normalizeInteractiveAnalysisErrorMessage(error instanceof Error ? error.message : '修改步骤失败');
      setState(prev => ({
        ...prev,
        error: message
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
