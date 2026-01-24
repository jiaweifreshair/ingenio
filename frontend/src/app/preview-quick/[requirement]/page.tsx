/**
 * Open-Lovable快速预览页面
 *
 * 功能：
 * - 5-10秒快速生成Web应用预览
 * - 使用Open-Lovable AI + Vercel Sandbox
 * - SSE流式显示生成进度
 * - 实时显示生成的代码（类似Open-Lovable-CN）
 * - iframe嵌入预览
 * - 支持聊天式迭代修改
 *
 * 架构：
 * - 后端代理: /api/v1/openlovable/*
 * - Open-Lovable服务: localhost:3001
 */
'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
	import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
	import { useToast } from '@/hooks/use-toast';
	import { FeedbackDialog } from '@/components/feedback-dialog';
	import {
	  ArrowLeft,
	  Loader2,
	  AlertCircle,
	  Send,
  Sparkles,
  Code,
  Eye,
	  Download,
	  FileCode,
	  Check,
	} from 'lucide-react';
import { cn } from '@/lib/utils';
import { getToken } from '@/lib/auth/token';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { parseFilesFromResponse, type GeneratedFile } from '@/lib/ai-stream-parser';
import {
  applyOpenLovableSseMessage,
  getInitialOpenLovableAccumulationState,
  getOpenLovableCodeForApply,
} from '@/lib/openlovable-stream-accumulator';
import LivePreviewIframe from '@/components/code-generation/live-preview-iframe';
import type { SandboxStatus } from '@/lib/sandbox/sandbox-manager';

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
 * 沙箱信息
 */
interface SandboxInfo {
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
  type:
    | 'content'
    | 'tool_call'
    | 'error'
    | 'complete'
    | 'status'
    | 'thinking'
    | 'stream'
    | 'conversation'
    | 'warning'
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
 * Scout 模板摘要结构
 * 用途：只抽取前端生成提示需要的字段，避免强依赖后端完整结构。
 */
interface ScoutTemplateSummary {
  name: string;
  description: string;
  matchScore?: number;
  analysisReason: string;
}

/**
 * 青少年压力/心理领域关键词
 * 用途：当需求命中该领域时，阻断明显不相关的模板上下文注入。
 */
const YOUTH_STRESS_KEYWORDS = [
  '压力',
  '情绪',
  '心理',
  '青少年',
  '学生',
  '班主任',
  '心理老师',
  '焦虑',
  '抑郁',
  'stress',
  'mental',
  'mood',
  'emotion',
  'counselor',
  'teen',
];

/**
 * 旅行/住宿类模板关键词
 * 用途：识别与心理健康需求明显不匹配的模板场景。
 */
const TRAVEL_TEMPLATE_KEYWORDS = [
  '民宿',
  '预订',
  '住宿',
  '酒店',
  'airbnb',
  'booking',
  '房源',
  '短租',
  '旅行',
  '旅游',
];

/**
 * 判断是否应用 Scout 模板上下文
 * 说明：当需求与模板领域明显不匹配时，跳过注入，避免模型跑偏。
 */
function shouldApplyScoutTemplateContext(
  requirementText: string,
  template: ScoutTemplateSummary,
): boolean {
  const requirement = requirementText.toLowerCase();
  const templateText = `${template.name} ${template.description} ${template.analysisReason}`.toLowerCase();
  const isYouthStress = YOUTH_STRESS_KEYWORDS.some((keyword) => requirement.includes(keyword));
  const isTravelTemplate = TRAVEL_TEMPLATE_KEYWORDS.some((keyword) => templateText.includes(keyword));

  if (typeof template.matchScore === 'number' && template.matchScore < 0.55) {
    return false;
  }

  if (isYouthStress && isTravelTemplate) {
    return false;
  }

  if (isYouthStress) {
    const overlap = YOUTH_STRESS_KEYWORDS.filter(
      (keyword) => requirement.includes(keyword) && templateText.includes(keyword),
    );
    if (overlap.length === 0) {
      return false;
    }
  }

  return true;
}

/**
 * 生成阶段
 */
type GenerationStage = 'init' | 'scouting' | 'sandbox' | 'generating' | 'complete' | 'error';

/**
 * 代码显示视图
 */
type ViewMode = 'preview' | 'code';

/**
 * 获取语法高亮语言
 */
function getSyntaxLanguage(type: string): string {
  const langMap: Record<string, string> = {
    'javascript': 'jsx',
    'typescript': 'tsx',
    'css': 'css',
    'scss': 'scss',
    'html': 'html',
    'json': 'json',
    'markdown': 'markdown',
    'text': 'text',
  };
  return langMap[type] || 'jsx';
}

export default function QuickPreviewPage() {
  console.log('DEBUG: QuickPreviewPage mounted');
  const params = useParams();
  const router = useRouter();
  const requirement = decodeURIComponent(params.requirement as string);

  const [stage, setStage] = useState<GenerationStage>('init');
  const [sandboxInfo, setSandboxInfo] = useState<SandboxInfo | null>(null);
  const [currentMessage, setCurrentMessage] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [generationLog, setGenerationLog] = useState<string[]>([]);

  const [statusMessage, setStatusMessage] = useState('');

  // 🆕 代码显示相关状态
  const [viewMode, setViewMode] = useState<ViewMode>('preview');
  const [streamedCode, setStreamedCode] = useState('');
  const [generatedFiles, setGeneratedFiles] = useState<GeneratedFile[]>([]);
  const [currentFile, setCurrentFile] = useState<GeneratedFile | null>(null);
  const [selectedFile, setSelectedFile] = useState<string | null>(null);

  // 🆕 运行时错误捕获
  const [runtimeError, setRuntimeError] = useState<any>(null);

  // 🆕 计时器状态
  const [startTime, setStartTime] = useState<number | null>(null);
  const [elapsedTime, setElapsedTime] = useState(0);
  const [totalTime, setTotalTime] = useState<number | null>(null);

  // 🆕 预览刷新 Key (用于强制刷新组件)
  const [previewKey, setPreviewKey] = useState(0);

  const hasStartedRef = useRef(false);
  const scoutContextRef = useRef<string>('');
  const codeContainerRef = useRef<HTMLDivElement>(null);
  const codeScrollRef = useRef<HTMLDivElement>(null);
  const isUserScrollingRef = useRef(false);
  const { toast } = useToast();

  /**
   * 计算沙箱状态
   */
  const getSandboxStatus = (): SandboxStatus => {
    switch (stage) {
      case 'init': return 'idle';
      case 'sandbox': return 'creating';
      case 'generating': return 'syncing';
      case 'complete': return 'ready';
      case 'error': return 'error';
      default: return 'idle';
    }
  };

  /**
   * 解析并更新文件状态
   */
  const updateFilesFromStream = useCallback((text: string) => {
    const { files, currentFile: current } = parseFilesFromResponse(text);

    if (files.length > 0) {
      setGeneratedFiles(files);
      // 自动选择第一个文件
      if (!selectedFile && files.length > 0) {
        setSelectedFile(files[0].path);
      }
    }

    if (current) {
      setCurrentFile(current);
    } else {
      setCurrentFile(null);
    }
  }, [selectedFile]);

  /**
   * 初始化：创建沙箱 + 生成代码
   */
  useEffect(() => {
    if (stage === 'init' && !hasStartedRef.current) {
      hasStartedRef.current = true;
      startQuickGeneration();
    }
  }, []);

  /**
   * 自动滚动代码容器到底部（用户手动滚动后暂停自动滚动）
   */
  useEffect(() => {
    if (codeScrollRef.current && (currentFile || streamedCode) && !isUserScrollingRef.current) {
      codeScrollRef.current.scrollTop = codeScrollRef.current.scrollHeight;
    }
  }, [currentFile, streamedCode, generatedFiles]);

  /**
   * 检测用户手动滚动：如果用户向上滚动则暂停自动滚动
   */
  const handleCodeScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
    const el = e.currentTarget;
    const isAtBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 50;
    // 如果用户滚动到底部附近，恢复自动滚动
    if (isAtBottom) {
      isUserScrollingRef.current = false;
    } else {
      // 用户向上滚动，暂停自动滚动
      isUserScrollingRef.current = true;
    }
  }, []);

  /**
   * 当代码生成完成时，重置滚动状态
   */
  useEffect(() => {
    if (stage === 'complete' || stage === 'error') {
      isUserScrollingRef.current = false;
    }
  }, [stage]);

  /**
   * 🆕 实时计时器：在生成过程中每秒更新
   */
  useEffect(() => {
    // 当进入sandbox或generating阶段时开始计时
    if ((stage === 'sandbox' || stage === 'generating') && !startTime) {
      setStartTime(Date.now());
    }

    // 当完成或出错时停止计时并记录总耗时
    if ((stage === 'complete' || stage === 'error') && startTime && totalTime === null) {
      const finalTime = Math.round((Date.now() - startTime) / 1000);
      setTotalTime(finalTime);
    }

    // 在生成过程中启动定时器
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
   * 🆕 重新加载预览（智能修复 + 重启Vite服务器）
   * 1. 调用 smart-refresh API 检测并自动修复代码错误
   * 2. 重启 Vite 服务器
   * LivePreviewIframe 会处理 UI 刷新
   */
  const reloadPreview = async (): Promise<boolean> => {
    try {
      const currentSandboxId = sandboxInfo?.sandboxId;
      const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';
      const token = getToken();

      // Step 1: 智能修复（自动检测并修复代码错误）
      addLog(`🔍 正在检测代码错误... (sandbox: ${currentSandboxId || 'unknown'})`);
      
      try {
        const smartRefreshResponse = await fetch(`${API_BASE_URL}/v1/openlovable/sandbox/smart-refresh`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(token ? { 'Authorization': token } : {}),
          },
          body: JSON.stringify({
            sandboxId: currentSandboxId,
            model: 'deepseek-v3',  // 使用稳定模型
            errorLog: runtimeError, // 🆕 将捕获的错误日志传给后端
            url: sandboxInfo?.url,  // 🆕 传递当前沙箱 URL 供后端读取文件
          }),
        });

        if (smartRefreshResponse.ok) {
          const smartRefreshResult = await smartRefreshResponse.json();
          
          if (smartRefreshResult.success && smartRefreshResult.data) {
            // 如果修复成功，清除错误状态
            if (smartRefreshResult.data.fixed) {
               setRuntimeError(null);
            }
            const { fixed, filesCreated, filesUpdated, message } = smartRefreshResult.data;
            
            if (fixed) {
              const createdCount = Array.isArray(filesCreated) ? filesCreated.length : 0;
              const updatedCount = Array.isArray(filesUpdated) ? filesUpdated.length : 0;
              
              addLog(`🛠️ AI 自动修复完成: ${createdCount} 个文件创建, ${updatedCount} 个文件更新`);
              
              if (createdCount > 0) {
                addLog(`   📁 创建: ${(filesCreated as string[]).join(', ')}`);
              }
              if (updatedCount > 0) {
                addLog(`   ✏️ 更新: ${(filesUpdated as string[]).join(', ')}`);
              }
              
              toast({
                title: 'AI 自动修复',
                description: message || `已修复 ${createdCount + updatedCount} 个文件`,
              });
            } else {
              addLog('✅ 代码检测通过，无需修复');
            }
          }
        } else {
          addLog('⚠️ 智能修复跳过（服务不可用）');
        }
      } catch (smartRefreshErr) {
        // 智能修复失败不阻塞后续流程
        addLog(`⚠️ 智能修复跳过: ${smartRefreshErr instanceof Error ? smartRefreshErr.message : '未知错误'}`);
      }

      // Step 2: 重启 Vite 服务器
      addLog(`🔄 正在重启开发服务器...`);
      
      const response = await fetch(`${API_BASE_URL}/v1/openlovable/restart-vite`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': token } : {}),
        },
        body: JSON.stringify({
          sandboxId: currentSandboxId,
        }),
      });

      if (!response.ok) {
        throw new Error(`重启失败: ${response.statusText}`);
      }

      addLog('✅ 开发服务器重启成功');
      
      // 等待Vite服务器启动
      await new Promise(resolve => setTimeout(resolve, 2000));

      toast({
        title: '刷新成功',
        description: '代码已检查并刷新预览',
      });
      return true;
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : '未知错误';
      addLog(`❌ 刷新失败: ${errorMsg}`);
      toast({
        title: '刷新失败',
        description: errorMsg,
        variant: 'destructive',
      });
      return false;
    }
  };

  /**
   * 启动快速生成流程
   */
  const startQuickGeneration = async () => {
    try {
      addLog('🚀 启动快速Web预览生成...');
      
      // Step 0: G3 Scout 智能侦察
      setStage('scouting');
      addLog('🕵️ 启动 Repo Scout 智能侦察兵...');
      
      try {
        const scoutRes = await fetch('/api/g3/scout', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ 
            requirement: requirement,
            tenant_id: 'default'
          })
        });
        
        if (scoutRes.ok) {
          const scoutData = await scoutRes.json();
          const scoutTaskId = scoutData.task_id;
          addLog(`✅ Scout 任务已启动: ${scoutTaskId}`);
          
          // 轮询 Scout 日志
          let scoutCompleted = false;
          while (!scoutCompleted) {
            const logsRes = await fetch(`/api/g3/logs/${scoutTaskId}`);
            if (logsRes.ok) {
              const logs = await logsRes.json();
              // 简单的去重显示逻辑（实际应该比较 timestamp）
              // 日志已由后端存储，这里只检查任务状态
              // 实际项目应使用更复杂的日志合并策略，目前通过UI状态展示进度
              void logs; // 显式标记变量已处理
              
              // 检查任务是否完成 (后端 API 目前没返回 status，只能通过日志判断或者另外一个 API)
              // 临时方案：检查日志中是否有 "COMPLETED" 或 "FAILED"
              const lastLog = logs[logs.length - 1];
              if (lastLog && (lastLog.content.includes('Completed') || lastLog.content.includes('Failed'))) {
                scoutCompleted = true;
                if (lastLog.content.includes('Failed')) {
                   addLog('⚠️ Scout 侦察遇到问题，降级为普通生成模式');
                } else {
                   addLog('✅ Scout 侦察完成，已选定最佳模版');
                   
                   // 获取 Scout 结果
                   try {
                     const resultRes = await fetch(`/api/g3/result/${scoutTaskId}`);
                     if (resultRes.ok) {
                       const resultData = await resultRes.json();
                       if (resultData && Array.isArray(resultData) && resultData.length > 0) {
                         const topPickRaw = resultData[0] as Record<string, unknown>;
                         const topPick: ScoutTemplateSummary = {
                           name: typeof topPickRaw.name === 'string' ? topPickRaw.name : '未命名模板',
                           description: typeof topPickRaw.description === 'string' ? topPickRaw.description : '',
                           matchScore: typeof topPickRaw.match_score === 'number' ? topPickRaw.match_score : undefined,
                           analysisReason: typeof topPickRaw.analysis_reason === 'string' ? topPickRaw.analysis_reason : '',
                         };

                         if (shouldApplyScoutTemplateContext(requirement, topPick)) {
                           const contextStr = `## G3 Scout Recommendation\n` +
                             `Based on your requirements, the Repo Scout Agent has identified the following template as the best starting point:\n` +
                             `- Name: ${topPick.name}\n` +
                             `- Description: ${topPick.description}\n` +
                             `- Match Score: ${topPick.matchScore ?? 'N/A'}\n` +
                             `- Reason: ${topPick.analysisReason}\n\n` +
                             `Please prioritize using patterns and structures from this template where applicable.`;

                           scoutContextRef.current = contextStr;
                           addLog(`📋 已加载模版上下文: ${topPick.name}`);
                         } else {
                           addLog('⚠️ 模版上下文与需求领域不匹配，已跳过注入');
                         }
                       }
                     }
                   } catch (err) {
                     console.error('Failed to fetch scout result:', err);
                   }
                }
              }
            }
            await new Promise(r => setTimeout(r, 1000));
          }
        } else {
          addLog('⚠️ Scout 服务不可用，跳过侦察阶段');
        }
      } catch (e) {
        console.error('Scout error:', e);
        addLog('⚠️ Scout 连接失败，跳过侦察阶段');
      }

      setStage('sandbox');
      setStreamedCode('');
      setGeneratedFiles([]);
      setCurrentFile(null);

      // Step 1: 创建沙箱
      addLog('📦 创建AI沙箱（Vercel Sandbox）...');
      const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';
      const token = getToken();
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

      const sandboxResult = await sandboxResponse.json();
      const sandbox: SandboxInfo = sandboxResult.data;

      // 验证sandbox URL是否合法（防止API返回测试消息等无效URL）
      if (!isValidUrl(sandbox.url)) {
        throw new Error(`沙箱URL无效: ${sandbox.url || '空'}`);
      }

      setSandboxInfo(sandbox);
      addLog(`✅ 沙箱创建成功: ${sandbox.sandboxId}`);
      addLog(`🌐 预览地址: ${sandbox.url}`);

      // Step 2: 生成AI代码（SSE流式）
      setStage('generating');
      addLog('🤖 AI正在生成代码（流式输出）...');

      await generateCodeStream(requirement, sandbox.sandboxId, scoutContextRef.current);

      // Step 3: 生成完成
      setStage('complete');
      addLog('🎉 生成完成！预览已就绪');

      toast({
        title: '生成成功',
        description: `应用已部署到沙箱，预览地址：${sandbox.url}`,
      });
      
      // 强制刷新预览组件
      setPreviewKey(prev => prev + 1);

    } catch (error) {
      console.error('快速生成失败:', error);
      const errorMessage = error instanceof Error ? error.message : '未知错误';
      setError(errorMessage);
      setStage('error');
      addLog(`❌ 生成失败: ${errorMessage}`);

      toast({
        title: '生成失败',
        description: errorMessage,
        variant: 'destructive',
      });
    }
  };

  /**
   * SSE流式生成代码
   */
  const generateCodeStream = async (userMessage: string, sandboxId: string, templateContext?: string): Promise<void> => {
    return new Promise((resolve, reject) => {
      const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';
      const apiUrl = `${API_BASE_URL}/v1/openlovable/generate/stream`;
      const token = getToken();

      let accumulationState = getInitialOpenLovableAccumulationState();
      
      const requestBody: { userMessage: string; sandboxId: string; templateContext?: string } = {
        userMessage,
        sandboxId,
      };
      
      if (templateContext) {
        requestBody.templateContext = templateContext;
      }

      fetch(apiUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': token } : {}),
        },
        body: JSON.stringify(requestBody),
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
                  const responseToApply = getOpenLovableCodeForApply(accumulationState);
                  if (!responseToApply.trim()) {
                    throw new Error('AI生成的代码为空，无法部署到Sandbox（请检查 OpenLovable 服务输出或稍后重试）');
                  }
                  if (!responseToApply.includes('<file')) {
                    throw new Error('AI生成的代码格式异常（缺少 <file> 标签），无法部署到Sandbox（请重试生成或切换模型）');
                  }
                  // 🔍 调试日志：记录发送到apply API的内容长度
                  console.log('[preview-quick] responseToApply length:', responseToApply.length);
                  console.log('[preview-quick] responseToApply preview:', responseToApply.substring(0, 500));
                  addLog(`📝 正在将代码应用到Sandbox... (响应长度: ${responseToApply.length} 字符)`);

                  const applyResponse = await fetch(`${API_BASE_URL}/v1/openlovable/apply`, {
                    method: 'POST',
                    headers: {
                      'Content-Type': 'application/json',
                      ...(token ? { 'Authorization': token } : {}),
                    },
                    body: JSON.stringify({
                      sandboxId,
                      response: responseToApply
                    })
                  });

                  if (!applyResponse.ok) {
                    let detail = '';
                    try {
                      const body = await applyResponse.json();
                      const message = typeof body?.message === 'string' ? body.message : '';
                      detail = message ? `: ${message}` : '';
                    } catch {
                      // 忽略解析失败，保留状态码
                    }
                    throw new Error(`Apply API失败: ${applyResponse.status}${detail}`);
                  }

                  const applyResult = await applyResponse.json();
                  const filesWritten = applyResult.data?.filesWritten || 0;
                  addLog(`✅ 代码已成功写入Sandbox: ${filesWritten} 个文件`);

                  // V2.5增强：显示被过滤的文件（如 lock files）
                  const filteredFiles = applyResult.data?.filteredFiles as string[] | undefined;
                  if (filteredFiles && filteredFiles.length > 0) {
                     addLog(`⚠️ 已过滤 ${filteredFiles.length} 个不安全/锁文件: ${filteredFiles.join(', ')}`);
                  }

                  // 上游可能替换 sandboxId（例如传入的 sandboxId 不存在），需要同步到预览 URL
                  const appliedSandboxId = typeof applyResult.data?.sandboxId === 'string' ? applyResult.data.sandboxId : null;
                  const appliedSandboxUrl =
                    typeof applyResult.data?.sandboxUrl === 'string'
                      ? applyResult.data.sandboxUrl
                      : typeof applyResult.data?.url === 'string'
                        ? applyResult.data.url
                        : null;

                  if (appliedSandboxId && appliedSandboxId !== sandboxId) {
                    addLog(`⚠️ 上游已替换Sandbox: ${sandboxId} → ${appliedSandboxId}`);
                  }

                  if (appliedSandboxId || (appliedSandboxUrl && isValidUrl(appliedSandboxUrl))) {
                    setSandboxInfo(prev => {
                      if (!prev) return prev;
                      return {
                        ...prev,
                        sandboxId: appliedSandboxId || prev.sandboxId,
                        url: appliedSandboxUrl && isValidUrl(appliedSandboxUrl) ? appliedSandboxUrl : prev.url,
                      };
                    });
                    setPreviewKey(prev => prev + 1);
                  }

                  // 最终解析文件
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

              // 解码SSE数据
              const chunk = decoder.decode(value, { stream: true });
              buffer += chunk;

              const lines = buffer.split('\n\n');
              buffer = lines.pop() || '';

              for (const line of lines) {
                if (!line.trim() || !line.startsWith('data:')) continue;

                try {
                  const jsonStr = line.replace(/^data:\s*/, '').trim();
                  const data: AIMessage = JSON.parse(jsonStr);

                  // 只拼接 stream 的增量，并在 complete 时用 generatedCode 覆盖，避免 conversation 事件导致重复污染
                  applyAndUpdateState(data);

                  // 处理状态消息
                  if (data.type === 'status' && data.message) {
                    setStatusMessage(data.message);
                    addLog(`ℹ️ 状态: ${data.message}`);
                  } else if (data.type === 'thinking' && data.message) {
                    setStatusMessage(`思考中: ${data.message}`);
                  }

                  // 处理消息并记录日志
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
  };

  /**
   * 添加日志
   */
  const addLog = (message: string) => {
    const timestamp = new Date().toLocaleTimeString();
    setGenerationLog(prev => [...prev, `[${timestamp}] ${message}`]);
  };

  /**
   * 发送聊天消息（迭代修改）
   */
  const sendMessage = async () => {
    if (!currentMessage.trim() || !sandboxInfo) return;

    try {
      addLog(`💬 用户: ${currentMessage}`);
      let userMsg = currentMessage;

      // Intent Recognition: 如果处于错误状态，自动注入错误上下文
      if (stage === 'error' && error) {
        addLog(`🤖 意图识别: 修复模式 (已自动注入错误上下文)`);
        userMsg = `Context: The previous code generation or application failed with the following error: "${error}".\n\nUser Request: ${currentMessage}\n\nPlease fix the code based on the error and the user's request. Ensure the code is complete and correct.`;
      }

      setCurrentMessage('');
      setStreamedCode('');
      setCurrentFile(null);
      setError(null);
      setStage('generating');

      await generateCodeStream(userMsg, sandboxInfo.sandboxId);

	      toast({
	        title: '修改成功',
	        description: '代码已更新，刷新预览查看效果',
	      });

	      // 通过重挂载预览组件强制刷新
	      setPreviewKey(prev => prev + 1);

	    } catch (error) {
      console.error('发送消息失败:', error);
      toast({
        title: '修改失败',
        description: error instanceof Error ? error.message : '未知错误',
        variant: 'destructive',
      });
    }
  };

  /**
   * 下载代码（从沙箱获取完整项目ZIP）
   */
  const [isDownloading, setIsDownloading] = useState(false);
  
  const downloadCode = async () => {
    // 优先从沙箱下载完整项目
    if (sandboxInfo?.sandboxId) {
      setIsDownloading(true);
      try {
        const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';
        const token = getToken();
        
        addLog('📦 正在打包项目代码...');
        
        const response = await fetch(`${API_BASE_URL}/v1/openlovable/sandbox/create-zip`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(token ? { 'Authorization': token } : {}),
          },
          body: JSON.stringify({ sandboxId: sandboxInfo.sandboxId }),
        });

        if (!response.ok) {
          throw new Error(`下载失败: ${response.statusText}`);
        }

        const result = await response.json();
        
        if (!result.success || !result.data?.dataUrl) {
          throw new Error(result.message || '生成ZIP包失败');
        }

        // base64 data URL 转 Blob 并下载
        const dataUrl = result.data.dataUrl as string;
        const fileName = (result.data.fileName as string) || 'project.zip';
        
        // 创建下载链接
        const a = document.createElement('a');
        a.href = dataUrl;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);

        addLog('✅ 项目代码下载成功');
        toast({
          title: '下载成功',
          description: `已下载 ${fileName}`,
        });
      } catch (err) {
        const errorMsg = err instanceof Error ? err.message : '未知错误';
        addLog(`❌ 下载失败: ${errorMsg}`);
        toast({
          title: '下载失败',
          description: errorMsg,
          variant: 'destructive',
        });
        
        // 降级：下载解析出的文件
        downloadParsedFiles();
      } finally {
        setIsDownloading(false);
      }
      return;
    }

    // 没有沙箱时，下载解析出的文件
    downloadParsedFiles();
  };

  /**
   * 下载解析出的文件（降级方案）
   */
  const downloadParsedFiles = () => {
    if (generatedFiles.length === 0) {
      toast({
        title: '无代码可下载',
        description: '请先生成代码',
        variant: 'destructive',
      });
      return;
    }

    // 创建一个包含所有文件的文本
    let content = '// ====== 生成的代码 ======\n\n';
    generatedFiles.forEach(file => {
      content += `// ====== ${file.path} ======\n`;
      content += file.content;
      content += '\n\n';
    });

    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'generated-code.txt';
    a.click();
    URL.revokeObjectURL(url);

    toast({
      title: '下载成功',
      description: `已下载 ${generatedFiles.length} 个文件`,
    });
  };

  /**
   * 获取当前显示的文件
   */
  const displayFile = selectedFile
    ? generatedFiles.find(f => f.path === selectedFile) || currentFile
    : currentFile || (generatedFiles.length > 0 ? generatedFiles[0] : null);

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 dark:from-gray-900 dark:to-gray-800">
      {/* 顶部导航栏 */}
      <div className="sticky top-0 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm border-b">
        <div className="container mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => router.back()}
            >
              <ArrowLeft className="w-4 h-4 mr-2" />
              返回
            </Button>
            <div>
              <h1 className="text-lg font-semibold flex items-center gap-2">
                <Sparkles className="w-5 h-5 text-purple-500" />
                快速Web预览
              </h1>
              <p className="text-sm text-gray-500 max-w-md truncate">{requirement}</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {/* 状态指示器 */}
            <div className={cn(
              'px-3 py-1 rounded-full text-sm font-medium flex items-center gap-2',
              stage === 'init' && 'bg-gray-200 text-gray-700',
              stage === 'scouting' && 'bg-orange-100 text-orange-700',
              stage === 'sandbox' && 'bg-blue-100 text-blue-700',
              stage === 'generating' && 'bg-purple-100 text-purple-700',
              stage === 'complete' && 'bg-green-100 text-green-700',
              stage === 'error' && 'bg-red-100 text-red-700'
            )}>
              {stage === 'init' && <Loader2 className="w-3 h-3 animate-spin" />}
              {stage === 'scouting' && <Loader2 className="w-3 h-3 animate-spin" />}
              {stage === 'sandbox' && <Loader2 className="w-3 h-3 animate-spin" />}
              {stage === 'generating' && <Loader2 className="w-3 h-3 animate-spin" />}
              {stage === 'complete' && <Eye className="w-3 h-3" />}
              {stage === 'error' && <AlertCircle className="w-3 h-3" />}
              {stage === 'init' && '初始化'}
              {stage === 'scouting' && '侦察中...'}
              {stage === 'sandbox' && `创建沙箱 ${elapsedTime}s`}
              {stage === 'generating' && (statusMessage || `生成中 ${elapsedTime}s`)}
              {stage === 'complete' && `已完成 ${totalTime}s`}
              {stage === 'error' && '失败'}
            </div>

            <Button
              size="sm"
              variant="outline"
              onClick={downloadCode}
              disabled={stage !== 'complete' || isDownloading}
            >
              {isDownloading ? (
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
              ) : (
                <Download className="w-4 h-4 mr-2" />
              )}
              {isDownloading ? '下载中...' : '下载源码'}
            </Button>
            
            <FeedbackDialog taskId={sandboxInfo?.sandboxId} />
          </div>
        </div>
      </div>

      {/* 主内容区 */}
      <div className="container mx-auto px-4 py-6">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* 左侧：预览/代码切换区域 */}
          <div className="lg:col-span-2">
            <Card className="h-[calc(100vh-200px)]">
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-sm font-medium text-gray-500">
                    {viewMode === 'preview' ? '实时预览' : '生成的代码'}
                    {sandboxInfo && viewMode === 'preview' && (
                      <span className="ml-2 text-xs text-gray-400">
                        {sandboxInfo.url}
                      </span>
                    )}
                  </CardTitle>

                  {/* 视图切换按钮 */}
                  <Tabs value={viewMode} onValueChange={(v) => setViewMode(v as ViewMode)}>
                    <TabsList className="h-8">
                      <TabsTrigger value="preview" className="text-xs px-3">
                        <Eye className="w-3 h-3 mr-1" />
                        预览
                      </TabsTrigger>
                      <TabsTrigger value="code" className="text-xs px-3">
                        <Code className="w-3 h-3 mr-1" />
                        代码
                      </TabsTrigger>
                    </TabsList>
                  </Tabs>
                </div>
              </CardHeader>
              <CardContent className="p-0 h-[calc(100%-60px)]">
                {stage === 'error' ? (
                  <div className="flex flex-col items-center justify-center h-full text-red-500">
                    <AlertCircle className="w-16 h-16 mb-4" />
                    <p className="text-lg font-medium">生成失败</p>
                    <p className="text-sm text-gray-500 mt-2">{error}</p>
                    <Button
                      onClick={() => {
                        hasStartedRef.current = false;
                        setStage('init');
                        startQuickGeneration();
                      }}
                      className="mt-6"
                    >
                      重试
                    </Button>
                  </div>
                ) : viewMode === 'preview' ? (
                  // 预览模式 - 使用 LivePreviewIframe 组件
                  <LivePreviewIframe
                    key={previewKey}
                    previewUrl={sandboxInfo?.url || null}
                    sandboxStatus={getSandboxStatus()}
                    isGenerating={stage === 'generating'}
                    loadingText={
                      stage === 'sandbox' ? '正在创建云端沙箱环境...' : 
                      stage === 'init' ? '正在初始化AI引擎...' : 
                      '正在加载预览...'
                    }
                    onRefresh={reloadPreview}
                    onRuntimeError={setRuntimeError}
                    className="h-full border-0 rounded-none"
                    title="应用预览"
                    showDeviceSwitcher={true}
                    showRefreshButton={true}
                    autoRefresh={false}
                  />
                ) : (
                  // 代码模式
                  <div className="h-full flex">
                    {/* 文件列表 */}
                    <div className="w-48 bg-gray-900 border-r border-gray-700 overflow-y-auto">
                      <div className="p-2 text-xs text-gray-400 font-medium border-b border-gray-700">
                        文件列表 ({generatedFiles.length})
                      </div>
                      {generatedFiles.map((file) => (
                        <button
                          key={file.path}
                          onClick={() => setSelectedFile(file.path)}
                          className={cn(
                            'w-full px-3 py-2 text-left text-xs font-mono flex items-center gap-2 hover:bg-gray-800 transition-colors',
                            selectedFile === file.path ? 'bg-gray-800 text-white' : 'text-gray-400'
                          )}
                        >
                          <FileCode className="w-3 h-3 flex-shrink-0" />
                          <span className="truncate">{file.path}</span>
                          {file.completed && <Check className="w-3 h-3 text-green-500 flex-shrink-0" />}
                        </button>
                      ))}
                      {currentFile && (
                        <button
                          onClick={() => setSelectedFile(currentFile.path)}
                          className={cn(
                            'w-full px-3 py-2 text-left text-xs font-mono flex items-center gap-2 hover:bg-gray-800 transition-colors',
                            selectedFile === currentFile.path ? 'bg-gray-800 text-white' : 'text-gray-400'
                          )}
                        >
                          <Loader2 className="w-3 h-3 animate-spin flex-shrink-0" />
                          <span className="truncate">{currentFile.path}</span>
                        </button>
                      )}
                      {generatedFiles.length === 0 && !currentFile && (
                        <div className="p-3 text-xs text-gray-500 text-center">
                          {stage === 'generating' ? '等待代码生成...' : '暂无文件'}
                        </div>
                      )}
                    </div>

                    {/* 代码显示 */}
                    <div className="flex-1 overflow-hidden bg-gray-900" ref={codeContainerRef}>
                      {displayFile ? (
                        <div className="h-full flex flex-col">
                          {/* 文件头 */}
                          <div className="px-4 py-2 bg-[#36322F] text-white flex items-center justify-between border-b border-gray-700">
                            <div className="flex items-center gap-2">
                              {!displayFile.completed && (
                                <Loader2 className="w-4 h-4 animate-spin text-orange-400" />
                              )}
                              {displayFile.completed && (
                                <Check className="w-4 h-4 text-green-500" />
                              )}
                              <span className="font-mono text-sm">{displayFile.path}</span>
                              <span className={cn(
                                'px-2 py-0.5 text-xs rounded',
                                displayFile.type === 'css' ? 'bg-blue-600 text-white' :
                                displayFile.type === 'javascript' ? 'bg-yellow-600 text-white' :
                                displayFile.type === 'typescript' ? 'bg-blue-500 text-white' :
                                displayFile.type === 'json' ? 'bg-green-600 text-white' :
                                'bg-gray-600 text-white'
                              )}>
                                {displayFile.type.toUpperCase()}
                              </span>
                            </div>
                          </div>

                          {/* 代码内容 - 支持流式滚动 */}
                          <div 
                            ref={codeScrollRef}
                            className="flex-1 overflow-y-auto scroll-smooth"
                            onScroll={handleCodeScroll}
                            style={{ maxHeight: 'calc(100% - 36px)' }}
                          >
                            <SyntaxHighlighter
                              language={getSyntaxLanguage(displayFile.type)}
                              style={vscDarkPlus}
                              customStyle={{
                                margin: 0,
                                padding: '1rem',
                                fontSize: '0.75rem',
                                background: 'transparent',
                                minHeight: '100%',
                              }}
                              showLineNumbers={true}
                              wrapLongLines={true}
                            >
                              {displayFile.content || '// 等待代码生成...'}
                            </SyntaxHighlighter>
                            {!displayFile.completed && (
                              <div className="flex items-center px-4 pb-4">
                                <span className="inline-block w-3 h-4 bg-orange-400 animate-pulse" />
                                <span className="ml-2 text-xs text-orange-400 animate-pulse">正在生成...</span>
                              </div>
                            )}
                          </div>
                        </div>
                      ) : (
                        // 显示原始流式输出 - 支持流式滚动
                        <div 
                          ref={codeScrollRef}
                          className="h-full overflow-y-auto scroll-smooth p-4"
                          onScroll={handleCodeScroll}
                        >
                          {streamedCode ? (
                            <pre className="text-xs text-gray-300 font-mono whitespace-pre-wrap">
                              {streamedCode}
                              <div className="flex items-center mt-2">
                                <span className="inline-block w-2 h-4 bg-orange-400 animate-pulse" />
                                <span className="ml-2 text-xs text-orange-400 animate-pulse">正在生成...</span>
                              </div>
                            </pre>
                          ) : (
                            <div className="flex items-center justify-center h-full text-gray-500">
                              <div className="text-center">
                                <Loader2 className="w-8 h-8 animate-spin mx-auto mb-2" />
                                <p className="text-sm">等待代码生成...</p>
                              </div>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* 右侧：日志和聊天 */}
          <div className="flex flex-col gap-4">
            {/* 生成日志 */}
            <Card className="flex-1 max-h-[400px]">
              <CardHeader className="pb-3">
                <CardTitle className="text-sm font-medium text-gray-500 flex items-center gap-2">
                  <Code className="w-4 h-4" />
                  生成日志
                </CardTitle>
              </CardHeader>
              <CardContent className="p-4 h-[calc(100%-60px)] overflow-y-auto">
                <div className="space-y-1 font-mono text-xs">
                  {generationLog.map((log, index) => (
                    <div
                      key={index}
                      className="text-gray-600 dark:text-gray-400"
                    >
                      {log}
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>

            {/* 聊天输入（迭代修改） */}
            {(stage === 'complete' || (stage === 'error' && sandboxInfo)) && (
              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-sm font-medium text-gray-500">
                    迭代修改
                  </CardTitle>
                </CardHeader>
                <CardContent className="p-4">
                  <div className="flex gap-2">
                    <Input
                      value={currentMessage}
                      onChange={(e) => setCurrentMessage(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && !e.shiftKey) {
                          e.preventDefault();
                          sendMessage();
                        }
                      }}
                      placeholder={stage === 'error' ? "输入修改建议以修复错误..." : "输入修改需求，例如：把标题改成蓝色"}
                      className="flex-1"
                    />
                    <Button
                      onClick={sendMessage}
                      disabled={!currentMessage.trim()}
                    >
                      <Send className="w-4 h-4" />
                    </Button>
                  </div>
                  <p className="text-xs text-gray-400 mt-2">
                    按Enter发送，Shift+Enter换行
                  </p>
                </CardContent>
              </Card>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
          // ... existing code ...
        // 查找 <LivePreviewIframe ... /> 并添加 onRuntimeError={setRuntimeError}
        // 由于无法确定 render 部分的行号，这里可能通过 MultiReplace 比较困难。
        // 将尝试使用宽泛的上下文匹配。
        // 鉴于 page.tsx 通常很大，我先不在此处替换 render，而是先确保逻辑部分正确。
        // 实际上 LivePreviewIframe 的调用在 JSX 中。
        // 我需要再读取一次 page.tsx 的后半部分来定位 JSX。
