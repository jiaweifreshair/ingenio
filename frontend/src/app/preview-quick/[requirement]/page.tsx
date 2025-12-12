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
import LivePreviewIframe from '@/components/code-generation/live-preview-iframe';
import type { SandboxStatus } from '@/lib/sandbox/sandbox-manager';

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
  type: 'content' | 'tool_call' | 'error' | 'complete';
  content?: string;
  text?: string;
  name?: string;
  args?: unknown;
  error?: string;
}

/**
 * 生成阶段
 */
type GenerationStage = 'init' | 'sandbox' | 'generating' | 'complete' | 'error';

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
  const params = useParams();
  const router = useRouter();
  const requirement = decodeURIComponent(params.requirement as string);

  const [stage, setStage] = useState<GenerationStage>('init');
  const [sandboxInfo, setSandboxInfo] = useState<SandboxInfo | null>(null);
  const [currentMessage, setCurrentMessage] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [generationLog, setGenerationLog] = useState<string[]>([]);

  // 🆕 代码显示相关状态
  const [viewMode, setViewMode] = useState<ViewMode>('preview');
  const [streamedCode, setStreamedCode] = useState('');
  const [generatedFiles, setGeneratedFiles] = useState<GeneratedFile[]>([]);
  const [currentFile, setCurrentFile] = useState<GeneratedFile | null>(null);
  const [selectedFile, setSelectedFile] = useState<string | null>(null);

  // 🆕 计时器状态
  const [startTime, setStartTime] = useState<number | null>(null);
  const [elapsedTime, setElapsedTime] = useState(0);
  const [totalTime, setTotalTime] = useState<number | null>(null);

  // 🆕 预览刷新 Key (用于强制刷新组件)
  const [previewKey, setPreviewKey] = useState(0);

  const hasStartedRef = useRef(false);
  const codeContainerRef = useRef<HTMLDivElement>(null);
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
   * 自动滚动代码容器到底部
   */
  useEffect(() => {
    if (codeContainerRef.current && (currentFile || streamedCode)) {
      codeContainerRef.current.scrollTop = codeContainerRef.current.scrollHeight;
    }
  }, [currentFile, streamedCode]);

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
   * 🆕 重新加载预览（重启Vite服务器）
   * LivePreviewIframe 会处理 UI 刷新，这里只负责后端重启逻辑
   */
  const reloadPreview = async (): Promise<boolean> => {
    try {
      const currentSandboxId = sandboxInfo?.sandboxId;
      addLog(`🔄 正在重启开发服务器... (sandbox: ${currentSandboxId || 'unknown'})`);

      const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';
      const token = getToken();
      const response = await fetch(`${API_BASE_URL}/v1/openlovable/restart-vite`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': token } : {}),
        },
        // 传递sandboxId确保重启正确的sandbox
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
        description: '开发服务器已重启',
      });
      return true;
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : '未知错误';
      addLog(`❌ 重启失败: ${errorMsg}`);
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

      setSandboxInfo(sandbox);
      addLog(`✅ 沙箱创建成功: ${sandbox.sandboxId}`);
      addLog(`🌐 预览地址: ${sandbox.url}`);

      // Step 2: 生成AI代码（SSE流式）
      setStage('generating');
      addLog('🤖 AI正在生成代码（流式输出）...');

      await generateCodeStream(requirement, sandbox.sandboxId);

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
  const generateCodeStream = async (userMessage: string, sandboxId: string): Promise<void> => {
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
        body: JSON.stringify({
          userMessage,
          sandboxId,
        }),
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
                addLog('✅ AI代码生成流式响应完成');

                // 调用apply API将代码写入sandbox
                try {
                  // 🔍 调试日志：记录发送到apply API的内容长度
                  console.log('[preview-quick] fullAIResponse length:', fullAIResponse.length);
                  console.log('[preview-quick] fullAIResponse preview:', fullAIResponse.substring(0, 500));
                  addLog(`📝 正在将代码应用到Sandbox... (响应长度: ${fullAIResponse.length} 字符)`);

                  const applyResponse = await fetch(`${API_BASE_URL}/v1/openlovable/apply`, {
                    method: 'POST',
                    headers: {
                      'Content-Type': 'application/json',
                      ...(token ? { 'Authorization': token } : {}),
                    },
                    body: JSON.stringify({
                      sandboxId,
                      response: fullAIResponse
                    })
                  });

                  if (!applyResponse.ok) {
                    throw new Error(`Apply API失败: ${applyResponse.status}`);
                  }

                  const applyResult = await applyResponse.json();
                  addLog(`✅ 代码已成功写入Sandbox: ${applyResult.data?.filesWritten || 0} 个文件`);

                  // 最终解析文件
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

                  // 从SSE中提取text字段并累加
                  if (data.text) {
                    fullAIResponse += data.text;
                    setStreamedCode(fullAIResponse);

                    // 🆕 实时解析文件
                    updateFilesFromStream(fullAIResponse);
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
      const userMsg = currentMessage;
      setCurrentMessage('');
      setStreamedCode('');
      setCurrentFile(null);

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
   * 下载代码
   */
  const downloadCode = () => {
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
              stage === 'sandbox' && 'bg-blue-100 text-blue-700',
              stage === 'generating' && 'bg-purple-100 text-purple-700',
              stage === 'complete' && 'bg-green-100 text-green-700',
              stage === 'error' && 'bg-red-100 text-red-700'
            )}>
              {stage === 'init' && <Loader2 className="w-3 h-3 animate-spin" />}
              {stage === 'sandbox' && <Loader2 className="w-3 h-3 animate-spin" />}
              {stage === 'generating' && <Loader2 className="w-3 h-3 animate-spin" />}
              {stage === 'complete' && <Eye className="w-3 h-3" />}
              {stage === 'error' && <AlertCircle className="w-3 h-3" />}
              {stage === 'init' && '初始化'}
              {stage === 'sandbox' && `创建沙箱 ${elapsedTime}s`}
              {stage === 'generating' && `生成中 ${elapsedTime}s`}
              {stage === 'complete' && `已完成 ${totalTime}s`}
              {stage === 'error' && '失败'}
            </div>

            <Button
              size="sm"
              variant="outline"
              onClick={downloadCode}
              disabled={generatedFiles.length === 0}
            >
              <Download className="w-4 h-4 mr-2" />
              下载代码
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

                          {/* 代码内容 */}
                          <div className="flex-1 overflow-auto">
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
                              <span className="inline-block w-3 h-4 bg-orange-400 ml-4 mb-4 animate-pulse" />
                            )}
                          </div>
                        </div>
                      ) : (
                        // 显示原始流式输出
                        <div className="h-full overflow-auto p-4">
                          {streamedCode ? (
                            <pre className="text-xs text-gray-300 font-mono whitespace-pre-wrap">
                              {streamedCode}
                              <span className="inline-block w-2 h-4 bg-orange-400 ml-1 animate-pulse" />
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
            {stage === 'complete' && (
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
                      placeholder="输入修改需求，例如：把标题改成蓝色"
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
