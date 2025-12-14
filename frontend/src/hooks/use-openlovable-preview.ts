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

// ==================== 类型定义 ====================

/**
 * 沙箱信息
 */
export interface SandboxInfo {
  success: boolean;
  sandboxId: string;
  url: string;
  provider: string;
  message: string;
}

/**
 * AI代码生成消息类型
 */
interface AIMessage {
  type: 'content' | 'tool_call' | 'error' | 'complete';
  content?: string;
  text?: string;
  name?: string;
  args?: unknown;
  error?: string;
}

/**
 * 生成的文件信息
 */
export interface GeneratedFile {
  path: string;
  content: string;
  type: string;
  completed: boolean;
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

// ==================== 工具函数 ====================

/**
 * 验证URL是否合法
 * 防止API返回的无效URL（如包含中文的测试消息）导致前端崩溃
 */
function isValidUrl(urlString: string | null | undefined): boolean {
  if (!urlString || typeof urlString !== 'string') {
    return false;
  }
  // 检查是否包含中文字符（明显的无效URL）
  if (/[\u4e00-\u9fa5]/.test(urlString)) {
    console.error(`[URL验证] ❌ URL包含中文字符: "${urlString}"`);
    return false;
  }
  try {
    const url = new URL(urlString);
    return url.protocol === 'http:' || url.protocol === 'https:';
  } catch {
    console.error(`[URL验证] ❌ URL格式无效: "${urlString}"`);
    return false;
  }
}

/**
 * 从文件路径推断文件类型
 */
function getFileType(path: string): string {
  const ext = path.split('.').pop()?.toLowerCase() || '';
  const typeMap: Record<string, string> = {
    'js': 'javascript',
    'jsx': 'javascript',
    'ts': 'typescript',
    'tsx': 'typescript',
    'css': 'css',
    'scss': 'scss',
    'html': 'html',
    'json': 'json',
    'md': 'markdown',
  };
  return typeMap[ext] || 'text';
}

/**
 * 从AI响应中解析文件
 * 支持两种格式：
 * 1. <file path="...">...</file>
 * 2. ```filename:path\n...\n```
 */
function parseFilesFromResponse(text: string): { files: GeneratedFile[]; currentFile: GeneratedFile | null } {
  const fileMap = new Map<string, GeneratedFile>();
  let currentFile: GeneratedFile | null = null;

  // 正则匹配 <file path="...">...</file> 格式
  const fileRegex = /<file path="([^"]+)">([\s\S]*?)<\/file>/g;
  let match;

  while ((match = fileRegex.exec(text)) !== null) {
    const [, path, content] = match;
    fileMap.set(path, {
      path,
      content: content.trim(),
      type: getFileType(path),
      completed: true,
    });
  }

  // 检查是否有正在生成的文件（未闭合的<file>标签）
  const openFileMatch = text.match(/<file path="([^"]+)">([\s\S]*)$/);
  if (openFileMatch) {
    const [, path, content] = openFileMatch;
    if (!fileMap.has(path)) {
      currentFile = {
        path,
        content: content.trim(),
        type: getFileType(path),
        completed: false,
      };
    }
  }

  // 如果没有找到<file>格式，尝试解析markdown代码块格式
  if (fileMap.size === 0 && !currentFile) {
    const codeBlockRegex = /```(?:(\w+):)?([^\n]+)\n([\s\S]*?)```/g;
    while ((match = codeBlockRegex.exec(text)) !== null) {
      const [, lang, path, content] = match;
      const filePath = path.trim();
      fileMap.set(filePath, {
        path: filePath,
        content: content.trim(),
        type: lang || getFileType(filePath),
        completed: true,
      });
    }

    // 检查未闭合的代码块
    const openCodeBlockMatch = text.match(/```(?:(\w+):)?([^\n]+)\n([\s\S]*)$/);
    if (openCodeBlockMatch && !text.endsWith('```')) {
      const [, lang, path, content] = openCodeBlockMatch;
      const filePath = path.trim();
      if (!fileMap.has(filePath)) {
        currentFile = {
          path: filePath,
          content: content.trim(),
          type: lang || getFileType(filePath),
          completed: false,
        };
      }
    }
  }

  return { files: Array.from(fileMap.values()), currentFile };
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

    if (files.length > 0) {
      setGeneratedFiles(files);
    }

    if (current) {
      setCurrentFile(current);
    } else {
      setCurrentFile(null);
    }
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

      let fullAIResponse = '';

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

          const readStream = (): void => {
            reader.read().then(async ({ done, value }) => {
              if (done) {
                // If there is residual data in the buffer, process it as a final line
                if (buffer.trim() && buffer.startsWith('data:')) {
                  try {
                    const jsonStr = buffer.replace(/^data:\s*/, '').trim();
                    const data: AIMessage = JSON.parse(jsonStr);

                    if (data.text) {
                      fullAIResponse += data.text;
                      setStreamedCode(fullAIResponse);
                      // Don't call updateFilesFromStream here, wait for the final complete call
                    }
                  } catch (parseError) {
                    console.warn('解析SSE剩余Buffer失败:', buffer, parseError);
                  }
                }

                addLog('✅ AI代码生成流式响应完成');

                // 调用apply API将代码写入sandbox
                try {
                  addLog(`📝 正在将代码应用到Sandbox... (响应长度: ${fullAIResponse.length} 字符)`);

                  const applyResponse = await fetch(`${API_BASE_URL}/v1/openlovable/apply`, {
                    method: 'POST',
                    headers: {
                      'Content-Type': 'application/json',
                      ...(token ? { 'Authorization': token } : {}),
                    },
                    body: JSON.stringify({
                      sandboxId: payload.sandboxId,
                      response: fullAIResponse
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
                      body: JSON.stringify({ sandboxId: payload.sandboxId }),
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

                  updateFilesFromStream(fullAIResponse);
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

              const lines = buffer.split('\n\n');
              buffer = lines.pop() || '';

              for (const line of lines) {
                if (!line.trim() || !line.startsWith('data:')) continue;

                try {
                  const jsonStr = line.replace(/^data:\s*/, '').trim();
                  const data: AIMessage = JSON.parse(jsonStr);

                  if (data.text) {
                    fullAIResponse += data.text;
                    setStreamedCode(fullAIResponse);
                    updateFilesFromStream(fullAIResponse);
                  }

                  if (data.type === 'tool_call') {
                    addLog(`🔧 工具调用: ${data.name}`);
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
      addLog('📦 创建AI沙箱（Vercel Sandbox）...');
      const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';
      const token = getToken();
      
      let currentSandboxId = sandboxInfo?.sandboxId;
      
      // 如果没有sandboxId，则创建新的
      if (!currentSandboxId) {
        const sandboxResponse = await fetch(`${API_BASE_URL}/v1/openlovable/sandbox/create`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(token ? { 'Authorization': token } : {}),
          },
        });

        if (!sandboxResponse.ok) {
          throw new Error(`创建沙箱失败: ${sandboxResponse.statusText}`);
        }

        const sandboxData = await sandboxResponse.json();
        currentSandboxId = sandboxData.data.sandboxId;
        
        // 验证sandbox URL是否合法（防止API返回测试消息等无效URL）
        if (!isValidUrl(sandboxData.data.url)) {
          throw new Error(`沙箱URL无效: ${sandboxData.data.url || '空'}`);
        }

        setSandboxInfo(sandboxData.data);
        addLog(`✅ 沙箱创建成功: ${currentSandboxId}`);
        addLog(`🌐 预览地址: ${sandboxData.data.url}`);
      } else {
        addLog(`♻️ 复用现有沙箱: ${currentSandboxId}`);
      }

      // Step 2: 生成AI代码
      setStage('generating');
      addLog('🤖 AI正在生成代码（流式输出）...');

      if (!currentSandboxId) {
        throw new Error('Sandbox ID not available');
      }

      await generateCodeStreamPayload({
        userRequirement: userMessage,
        sandboxId: currentSandboxId,
        designStyle: options?.styleId || options?.styleHint,
        appSpecId: options?.appSpecId
      });

      // Step 3: 生成完成
      setStage('complete');
      addLog('🎉 生成完成！');

    } catch (err) {
      console.error('[useOpenLovablePreview] 生成失败:', err);
      const errorMessage = err instanceof Error ? err.message : '未知错误';
      setError(errorMessage);
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

      await generateCodeStreamPayload({
        userRequirement: message,
        sandboxId: sandboxInfo.sandboxId
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
    if (isReloading || !sandboxInfo) return;

    try {
      setIsReloading(true);
      addLog(`🔄 正在刷新预览... (sandbox: ${sandboxInfo.sandboxId})`);

      const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';
      const token = getToken();

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
              sandboxId: sandboxInfo.sandboxId,
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
          sandboxId: sandboxInfo.sandboxId,
        }),
      });

      if (!response.ok) {
        throw new Error(`重启失败: ${response.statusText}`);
      }

      addLog('✅ 开发服务器重启成功，预览即将刷新');
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
