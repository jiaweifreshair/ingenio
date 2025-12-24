/**
 * useOpenLovablePreview - OpenLovable快速预览Hook
 *
 * 功能：
 * - 5-10秒快速生成Web应用预览
 * - 使用Open-Lovable AI + Vercel Sandbox
 * - SSE流式显示生成进度
 * - 支持聊天式迭代修改
 *
 * 用于V2.0深度融合：
 * - 风格选择后触发快速预览生成
 * - 原型确认页面支持迭代修改
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-16
 */
'use client';

import { useState, useCallback, useRef, useEffect } from 'react';
import { getToken } from '@/lib/auth/token';
import { parseFilesFromResponse, type GeneratedFile as ParsedGeneratedFile } from '@/lib/ai-stream-parser';
import {
  applyOpenLovableSseMessage,
  getInitialOpenLovableAccumulationState,
  getOpenLovableCodeForApply,
} from '@/lib/openlovable-stream-accumulator';
import {
  ensureSandboxAvailable,
  ensureSandboxIdAvailable,
  extractSandboxUrl,
  isValidUrl,
  requestOpenLovableCreateSandbox,
  requestOpenLovableSandboxStatus,
} from '@/lib/openlovable/sandbox-lifecycle';
import type { SandboxInfo } from '@/lib/openlovable/sandbox-lifecycle';
export type { SandboxInfo } from '@/lib/openlovable/sandbox-lifecycle';

// ==================== 类型定义 ====================

/**
 * AI代码生成消息类型
 */
interface AIMessage {
  type:
    | 'content'
    | 'tool_call'
    | 'error'
    | 'complete'
    | 'stream'
    | 'status'
    | 'conversation'
    | 'warning'
    | 'thinking'
    | 'component';
  content?: string;
  text?: string;
  generatedCode?: string;
  name?: string;
  args?: unknown;
  error?: string;
  message?: string;
}

/**
 * 生成的文件信息
 */
/**
 * 生成的文件信息（在解析结果基础上增加“是否被编辑”标记）
 */
export interface GeneratedFile extends ParsedGeneratedFile {
  /** 是否在前端被手动编辑过 */
  edited: boolean;
}

/**
 * 生成阶段
 */
export type GenerationStage = 'idle' | 'sandbox' | 'generating' | 'complete' | 'error';

/**
 * Hook返回值
 */
export interface UseOpenLovablePreviewReturn {
  /** 当前阶段 */
  stage: GenerationStage;
  /** 沙箱信息 */
  sandboxInfo: SandboxInfo | null;
  /** 预览URL */
  previewUrl: string | null;
  /** 生成的文件列表 */
  generatedFiles: GeneratedFile[];
  /** 当前正在生成的文件 */
  currentFile: GeneratedFile | null;
  /** 流式输出的原始代码 */
  streamedCode: string;
  /** 生成日志 */
  generationLog: string[];
  /** 已用时间（秒） */
  elapsedTime: number;
  /** 总耗时（秒） */
  totalTime: number | null;
  /** 错误信息 */
  error: string | null;
  /** 是否正在刷新 */
  isReloading: boolean;
  /** 开始生成 */
  startGeneration: (userMessage: string, options?: { styleHint?: string; appSpecId?: string; styleId?: string }) => Promise<void>;
  /** 发送迭代修改消息 */
  sendIterationMessage: (message: string) => Promise<void>;
  /** 刷新预览 */
  reloadPreview: () => Promise<void>;
  /** 重置状态 */
  reset: () => void;
}

// ==================== Hook实现 ====================

/**
 * OpenLovable快速预览Hook
 */
export function useOpenLovablePreview(): UseOpenLovablePreviewReturn {
  // 状态
  const [stage, setStage] = useState<GenerationStage>('idle');
  const [sandboxInfo, setSandboxInfo] = useState<SandboxInfo | null>(null);
  const [generatedFiles, setGeneratedFiles] = useState<GeneratedFile[]>([]);
  const [currentFile, setCurrentFile] = useState<GeneratedFile | null>(null);
  const [streamedCode, setStreamedCode] = useState('');
  const [generationLog, setGenerationLog] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isReloading, setIsReloading] = useState(false);

  // 计时器
  const [startTime, setStartTime] = useState<number | null>(null);
  const [elapsedTime, setElapsedTime] = useState(0);
  const [totalTime, setTotalTime] = useState<number | null>(null);

  // Refs
  const isGeneratingRef = useRef(false);

  // 预览URL
  const previewUrl = sandboxInfo?.url || null;

  /**
   * 添加日志
   */
  const addLog = useCallback((message: string) => {
    const timestamp = new Date().toLocaleTimeString();
    setGenerationLog(prev => [...prev, `[${timestamp}] ${message}`]);
  }, []);

  /**
   * 解析并更新文件状态
   */
  const updateFilesFromStream = useCallback((text: string) => {
    const { files, currentFile: current } = parseFilesFromResponse(text);
    const nextFiles: GeneratedFile[] = files.map(file => ({ ...file, edited: false }));
    const nextCurrentFile: GeneratedFile | null = current ? { ...current, edited: false } : null;

    if (nextFiles.length > 0) {
      setGeneratedFiles(nextFiles);
    }

    setCurrentFile(nextCurrentFile);
  }, []);

  /**
   * 实时计时器
   */
  useEffect(() => {
    if ((stage === 'sandbox' || stage === 'generating') && !startTime) {
      setStartTime(Date.now());
    }

    if ((stage === 'complete' || stage === 'error') && startTime && totalTime === null) {
      setTotalTime(Math.round((Date.now() - startTime) / 1000));
    }

    let interval: NodeJS.Timeout | null = null;
    if (startTime && (stage === 'sandbox' || stage === 'generating')) {
      interval = setInterval(() => {
        setElapsedTime(Math.round((Date.now() - startTime) / 1000));
      }, 1000);
    }

    return () => {
      if (interval) clearInterval(interval);
    };
  }, [stage, startTime, totalTime]);

  /**
   * SSE流式生成代码
   */
  const generateCodeStreamPayload = useCallback(async (payload: { userRequirement: string; sandboxId: string; designStyle?: string; appSpecId?: string }): Promise<void> => {
    return new Promise((resolve, reject) => {
      const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';
      const apiUrl = `${API_BASE_URL}/v1/openlovable/generate/stream`;
      const token = getToken();

      let accumulationState = getInitialOpenLovableAccumulationState();

      fetch(apiUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': token } : {}),
        },
        body: JSON.stringify(payload),
      })
        .then(response => {
          if (!response.ok) {
            throw new Error(`SSE请求失败: ${response.status}`);
          }

          if (!response.body) {
            throw new Error('响应体为空');
          }

          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';

          const applyAndUpdateState = (data: AIMessage) => {
            const nextState = applyOpenLovableSseMessage(accumulationState, data);
            if (
              nextState.streamedText !== accumulationState.streamedText ||
              nextState.finalCode !== accumulationState.finalCode
            ) {
              accumulationState = nextState;
              setStreamedCode(accumulationState.streamedText);
              updateFilesFromStream(accumulationState.streamedText);
            }
          };

          const readStream = (): void => {
            reader.read().then(async ({ done, value }) => {
              if (done) {
                // 处理剩余buffer，避免末尾没有\n\n导致最后一个事件丢失
                if (buffer.trim()) {
                  const remainingEvents = buffer.split(/\n\n|\r\n\r\n/);
                  for (const event of remainingEvents) {
                    if (!event.trim()) continue;
                    const lines = event.split(/\n|\r\n/);
                    for (const line of lines) {
                      if (!line.trim() || line.startsWith(':')) continue;
                      if (!line.startsWith('data:')) continue;
                      try {
                        const jsonStr = line.replace(/^data:\s*/, '').trim();
                        if (!jsonStr) continue;
                        const data: AIMessage = JSON.parse(jsonStr);
                        applyAndUpdateState(data);
                      } catch (parseError) {
                        console.warn('解析SSE剩余Buffer失败:', line, parseError);
                      }
                    }
                  }
                }

                addLog('✅ AI代码生成流式响应完成');

                // 调用apply API将代码写入sandbox
                try {
                  const ensureResult = await ensureSandboxIdAvailable(
                    API_BASE_URL,
                    payload.sandboxId,
                    token,
                    { now: Date.now() }
                  );

                  let targetSandboxId = ensureResult.sandbox.sandboxId;

                  if (ensureResult.action === 'recreated') {
                    addLog('⚠️ Sandbox不可用，正在重新创建沙箱并重新部署代码...');
                    setSandboxInfo(ensureResult.sandbox);
                    addLog(`✅ 新沙箱创建成功: ${ensureResult.sandbox.sandboxId}`);
                    addLog(`🌐 新预览地址: ${ensureResult.sandbox.url}`);
                  } else if (ensureResult.sandbox.url && isValidUrl(ensureResult.sandbox.url)) {
                    // 仅同步 URL，避免覆盖既有 createdAt（用于时效判断）
                    setSandboxInfo(prev => (
                      prev && prev.sandboxId === targetSandboxId
                        ? { ...prev, url: ensureResult.sandbox.url }
                        : prev
                    ));
                  }

                  const responseToApply = getOpenLovableCodeForApply(accumulationState);
                  addLog(`📝 正在将代码应用到Sandbox... (sandbox: ${targetSandboxId}, 响应长度: ${responseToApply.length} 字符)`);

                  const applyResponse = await fetch(`${API_BASE_URL}/v1/openlovable/apply`, {
                    method: 'POST',
                    headers: {
                      'Content-Type': 'application/json',
                      ...(token ? { 'Authorization': token } : {}),
                    },
                    body: JSON.stringify({
                      sandboxId: targetSandboxId,
                      response: responseToApply
                    })
                  });

                  if (!applyResponse.ok) {
                    throw new Error(`Apply API失败: ${applyResponse.status}`);
                  }

                  const applyResult = await applyResponse.json();
                  addLog(`✅ 代码已成功写入Sandbox: ${applyResult.data?.filesWritten || 0} 个文件`);

                  // 重启Vite服务器确保热更新能够正确加载新代码
                  addLog('🔄 正在重启Vite服务器，确保热更新生效...');
                  try {
                    const restartResponse = await fetch(`${API_BASE_URL}/v1/openlovable/restart-vite`, {
                      method: 'POST',
                      headers: {
                        'Content-Type': 'application/json',
                        ...(token ? { 'Authorization': token } : {}),
                      },
                      body: JSON.stringify({ sandboxId: targetSandboxId }),
                    });
                    if (restartResponse.ok) {
                      addLog('✅ Vite服务器重启成功，预览即将更新');
                    } else {
                      addLog('⚠️ Vite重启失败，可能需要手动刷新预览');
                    }
                  } catch (restartError) {
                    console.warn('重启Vite失败:', restartError);
                    addLog('⚠️ Vite重启超时，请手动点击刷新按钮');
                  }

                  // 重启后再同步一次URL，处理上游可能返回新部署地址的情况
                  const postRestartStatus = await requestOpenLovableSandboxStatus(API_BASE_URL, targetSandboxId, token);
                  if (postRestartStatus.success && postRestartStatus.data) {
                    const latestUrl = extractSandboxUrl(postRestartStatus.data);
                    if (latestUrl && isValidUrl(latestUrl)) {
                      setSandboxInfo(prev => (prev && prev.sandboxId === targetSandboxId ? { ...prev, url: latestUrl } : prev));
                    }
                  }

                  updateFilesFromStream(responseToApply);
                  setCurrentFile(null);

                  resolve();
                } catch (applyError) {
                  const errorMsg = applyError instanceof Error ? applyError.message : '未知错误';
                  addLog(`❌ Apply失败: ${errorMsg}`);
                  reject(applyError);
                }
                return;
              }

              // Decode SSE data
              const chunk = decoder.decode(value, { stream: true });
              buffer += chunk;

              // SSE标准：事件由空行分隔（\n\n），但也要兼容单换行情况
              // 首先尝试按 \n\n 分割，如果没有则按 \n 处理每个data行
              const events = buffer.split(/\n\n|\r\n\r\n/);
              buffer = events.pop() || '';

              for (const event of events) {
                if (!event.trim()) continue;

                // 处理每个事件块中的所有行
                const lines = event.split(/\n|\r\n/);
                for (const line of lines) {
                  // 跳过注释行（以:开头）和空行
                  if (!line.trim() || line.startsWith(':')) continue;
                  if (!line.startsWith('data:')) continue;

                  try {
                    const jsonStr = line.replace(/^data:\s*/, '').trim();
                    if (!jsonStr) continue;
                    const data: AIMessage = JSON.parse(jsonStr);

                    // 只拼接 stream 的增量，并在 complete 时用 generatedCode 覆盖，避免 conversation 事件导致重复污染
                    applyAndUpdateState(data);

                    if (data.type === 'tool_call') {
                      addLog(`🔧 工具调用: ${data.name}`);
                    } else if (data.type === 'stream') {
                      // stream类型的消息，text已经在上面处理了
                      // 这里可以添加额外的日志或处理
                    } else if (data.type === 'status') {
                      // 状态消息
                      addLog(`📋 ${data.message || '状态更新'}`);
                    } else if (data.type === 'error') {
                      addLog(`❌ 错误: ${data.error}`);
                      reject(new Error(data.error));
                      return;
                    } else if (data.type === 'complete') {
                      addLog('🎯 AI生成完成');
                    }
                  } catch (parseError) {
                    console.warn('解析SSE消息失败:', line, parseError);
                  }
                }
              }

              readStream();
            }).catch(error => {
              console.error('读取SSE流失败:', error);
              reject(error);
            });
          };

          readStream();
        })
        .catch(error => {
          console.error('SSE请求失败:', error);
          reject(error);
        });
    });
  }, [addLog, updateFilesFromStream]);

  /**
   * 开始生成
   */
  const startGeneration = useCallback(async (userMessage: string, options?: { styleHint?: string; appSpecId?: string; styleId?: string }) => {
    if (isGeneratingRef.current) {
      console.warn('[useOpenLovablePreview] 已有生成任务在进行中');
      return;
    }

    isGeneratingRef.current = true;
    setError(null);
    setStreamedCode('');
    setGeneratedFiles([]);
    setCurrentFile(null);
    setGenerationLog([]);
    setStartTime(null);
    setElapsedTime(0);
    setTotalTime(null);

    try {
      addLog('🚀 启动快速Web预览生成...');
      setStage('sandbox');

      // Step 1: 创建沙箱
      addLog('📦 准备AI沙箱（E2B Sandbox）...');
      const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';
      const token = getToken();

      // 策略：每次“开始生成”都创建全新沙箱（不复用），确保“每创建一次=一个沙箱”
      if (sandboxInfo?.sandboxId) {
        try {
          await fetch(`${API_BASE_URL}/v1/openlovable/cleanup`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              ...(token ? { 'Authorization': token } : {}),
            },
            body: JSON.stringify({ sandboxId: sandboxInfo.sandboxId }),
            cache: 'no-store',
          });
          addLog(`🧹 已清理旧沙箱: ${sandboxInfo.sandboxId}`);
        } catch (cleanupError) {
          console.warn('[useOpenLovablePreview] 清理旧沙箱失败:', cleanupError);
          addLog('⚠️ 清理旧沙箱失败，将继续创建新沙箱');
        }
      }

      const activeSandbox = await requestOpenLovableCreateSandbox(API_BASE_URL, token, Date.now());
      addLog(`✅ 沙箱创建成功: ${activeSandbox.sandboxId}`);
      addLog(`🌐 预览地址: ${activeSandbox.url}`);
      setSandboxInfo(activeSandbox);

      // Step 2: 生成AI代码
      setStage('generating');
      addLog('🤖 AI正在生成代码（流式输出）...');

      if (!activeSandbox.sandboxId) {
        throw new Error('Sandbox ID not available');
      }

      await generateCodeStreamPayload({
        userRequirement: userMessage,
        sandboxId: activeSandbox.sandboxId,
        designStyle: options?.styleId || options?.styleHint,
        appSpecId: options?.appSpecId
      });

      // Step 3: 生成完成
      setStage('complete');
      addLog('🎉 生成完成！');

    } catch (err) {
      console.error('[useOpenLovablePreview] 生成失败:', err);
      const errorMessage = err instanceof Error ? err.message : JSON.stringify(err);
      setError(errorMessage || '发生未知错误');
      setStage('error');
      addLog(`❌ 生成失败: ${errorMessage}`);
    } finally {
      isGeneratingRef.current = false;
    }
  }, [addLog, sandboxInfo, generateCodeStreamPayload]);

  /**
   * 发送迭代修改消息
   */
  const sendIterationMessage = useCallback(async (message: string) => {
    if (!sandboxInfo || isGeneratingRef.current) {
      console.warn('[useOpenLovablePreview] 无法发送迭代消息：无沙箱或正在生成');
      return;
    }

    isGeneratingRef.current = true;
    setError(null);

    try {
      addLog(`💬 用户迭代请求: ${message}`);
      setStreamedCode('');
      setCurrentFile(null);

      const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';
      const token = getToken();

      // 迭代前先做沙箱可用性/时效性校验，避免在已过期沙箱上继续生成导致预览不可用
      const ensureResult = await ensureSandboxAvailable(API_BASE_URL, sandboxInfo, token, {
        now: Date.now(),
        // “一轮创建=一个沙箱”：迭代阶段不做主动“按时间换新”，只在上游确实不可用时才重建
        maxAgeMs: Number.MAX_SAFE_INTEGER,
      });
      const targetSandbox = ensureResult.sandbox;

      if (ensureResult.action === 'recreated') {
        const reasonText = ensureResult.reason === 'not_found' ? '沙箱不存在' : '沙箱状态异常';
        addLog(`⚠️ ${reasonText}，已自动重建`);
        addLog(`✅ 新沙箱创建成功: ${targetSandbox.sandboxId}`);
        addLog(`🌐 新预览地址: ${targetSandbox.url}`);
        setSandboxInfo(targetSandbox);
      } else if (ensureResult.urlUpdated) {
        addLog(`🔁 已同步最新预览地址: ${targetSandbox.url}`);
        setSandboxInfo(targetSandbox);
      }

      await generateCodeStreamPayload({
        userRequirement: message,
        sandboxId: targetSandbox.sandboxId
      });

      addLog('✅ 迭代修改完成');
    } catch (err) {
      console.error('[useOpenLovablePreview] 迭代失败:', err);
      const errorMessage = err instanceof Error ? err.message : '未知错误';
      setError(errorMessage);
      addLog(`❌ 迭代失败: ${errorMessage}`);
    } finally {
      isGeneratingRef.current = false;
    }
  }, [sandboxInfo, addLog, generateCodeStreamPayload]);

  /**
   * 刷新预览
   */
  const reloadPreview = useCallback(async () => {
    if (isReloading) return;
    if (!sandboxInfo) {
      addLog('⚠️ 无法刷新预览：缺少sandboxId');
      return;
    }

    try {
      setIsReloading(true);

      const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';
      const token = getToken();
      const ensureResult = await ensureSandboxAvailable(API_BASE_URL, sandboxInfo, token, {
        now: Date.now(),
        // “一轮创建=一个沙箱”：刷新阶段不做主动“按时间换新”，只在上游确实不可用时才重建
        maxAgeMs: Number.MAX_SAFE_INTEGER,
      });
      let targetSandbox = ensureResult.sandbox;

      if (ensureResult.action === 'recreated') {
        const reasonText = ensureResult.reason === 'not_found' ? '沙箱不存在' : '沙箱状态异常';
        addLog(`⚠️ ${reasonText}，已自动重建并准备重新部署`);
        addLog(`✅ 新沙箱创建成功: ${targetSandbox.sandboxId}`);
        addLog(`🌐 新预览地址: ${targetSandbox.url}`);
        setSandboxInfo(targetSandbox);
      } else if (ensureResult.urlUpdated) {
        addLog(`🔁 已同步最新预览地址: ${targetSandbox.url}`);
        setSandboxInfo(targetSandbox);
      }

      addLog(`🔄 正在刷新预览... (sandbox: ${targetSandbox.sandboxId})`);

      /**
       * 刷新兜底：自动重新 apply 一次已生成代码
       *
       * 背景：
       * - 旧沙箱中可能存在 AI 遗漏 React Hook 导入等问题（如 useState 未定义）
       * - apply 接口会在后端侧做自动补全修复
       * - 仅刷新时也需要触发一次 apply，避免要求用户重新生成
       */
      let responseToApply = streamedCode || '';

      // 如果 streamedCode 不含 <file>，使用已解析的文件重建
      if (!responseToApply.includes('<file') && generatedFiles.length > 0) {
        responseToApply = generatedFiles
          .map(file => `<file path="${file.path}">\n${file.content}\n</file>`)
          .join('\n\n');
      }

      if (responseToApply.includes('<file')) {
        try {
          addLog('🛠️ 正在重新应用代码到Sandbox（自动修复旧沙箱）...');
          const applyResponse = await fetch(`${API_BASE_URL}/v1/openlovable/apply`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              ...(token ? { 'Authorization': token } : {}),
            },
            body: JSON.stringify({
              sandboxId: targetSandbox.sandboxId,
              response: responseToApply,
            }),
          });

          if (applyResponse.ok) {
            const applyResult = await applyResponse.json();
            addLog(`✅ 代码已重新写入Sandbox: ${applyResult.data?.filesWritten || 0} 个文件`);
          } else {
            addLog('⚠️ 重新 apply 失败，将继续重启 Vite');
          }
        } catch (applyError) {
          console.warn('[useOpenLovablePreview] 刷新时 apply 失败:', applyError);
          addLog('⚠️ 重新 apply 异常，将继续重启 Vite');
        }
      }

      const response = await fetch(`${API_BASE_URL}/v1/openlovable/restart-vite`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': token } : {}),
        },
        body: JSON.stringify({
          sandboxId: targetSandbox.sandboxId,
        }),
      });

      if (!response.ok) {
        throw new Error(`重启失败: ${response.statusText}`);
      }

      addLog('✅ 开发服务器重启成功，预览即将刷新');

      // 重启后再同步一次URL，处理上游可能返回新部署地址的情况
      const postRestartStatus = await requestOpenLovableSandboxStatus(API_BASE_URL, targetSandbox.sandboxId, token);
      if (postRestartStatus.success && postRestartStatus.data) {
        const latestUrl = extractSandboxUrl(postRestartStatus.data);
        const nextUrl = latestUrl && isValidUrl(latestUrl) ? latestUrl : targetSandbox.url;
        if (nextUrl !== targetSandbox.url) {
          targetSandbox = { ...targetSandbox, url: nextUrl };
          setSandboxInfo(targetSandbox);
          addLog(`🔁 已同步最新预览地址: ${nextUrl}`);
        }
      }
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : '未知错误';
      addLog(`❌ 重启失败: ${errorMsg}`);
    } finally {
      setIsReloading(false);
    }
  }, [sandboxInfo, isReloading, addLog, streamedCode, generatedFiles]);

  /**
   * 重置状态
   */
  const reset = useCallback(() => {
    setStage('idle');
    setSandboxInfo(null);
    setGeneratedFiles([]);
    setCurrentFile(null);
    setStreamedCode('');
    setGenerationLog([]);
    setError(null);
    setIsReloading(false);
    setStartTime(null);
    setElapsedTime(0);
    setTotalTime(null);
    isGeneratingRef.current = false;
  }, []);

  return {
    stage,
    sandboxInfo,
    previewUrl,
    generatedFiles,
    currentFile,
    streamedCode,
    generationLog,
    elapsedTime,
    totalTime,
    error,
    isReloading,
    startGeneration,
    sendIterationMessage,
    reloadPreview,
    reset,
  };
}

export default useOpenLovablePreview;
