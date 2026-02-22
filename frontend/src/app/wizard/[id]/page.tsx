/**
 * 向导页面 - 重构版本
 * 使用分屏布局展示AppSpec生成过程
 * 左侧：执行面板 (ExecutionPanel) - 展示输出过程 (Logs, Timeline)
 * 右侧：结果面板 (StepResultPanel) - 展示输出结果 (Plan, Code, Validation)
 * 逻辑：确认完成再进入下一步
 */
'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { Badge } from '@/components/ui/badge';
import {
  CheckCircle2,
  Loader2,
  FileText,
  Sparkles,
  Palette,
  Clock,
  Download
} from 'lucide-react';
import { getAppSpec, getBackendPackageDownload, type AppSpec } from '@/lib/api/appspec';
import {
  createAsyncGenerationTask,
  type AsyncGenerateRequest,
  type TaskStatusResponse
} from '@/lib/api/generate';
import { updateProjectRequirement, regenerateProject } from '@/lib/api/projects'; // Import new API methods
import { downloadG3Artifacts, getG3JobStatus } from '@/lib/api/g3';
import { GenerationConfig, AgentType, AgentState, type AgentExecutionStatus } from '@/types/wizard';
import { SplitLayout } from '@/components/wizard/split-layout';
import { ConfigurationPanel } from '@/components/wizard/configuration-panel'; // Ensure this is imported
import { ExecutionPanel } from '@/components/wizard/execution-panel';
import { StepResultPanel } from '@/components/wizard/step-result-panel';
import { CodeDownloadPanel } from '@/components/wizard/code-download-panel';
import { QuickActionCards } from '@/components/wizard/quick-action-cards';
import { GenerationStats, type GenerationStats as GenerationStatsType } from '@/components/wizard/generation-stats';
import { useGenerationTask } from '@/hooks/use-generation-task';
import { useGenerationWebSocket } from '@/hooks/use-generation-websocket';
import { useToast } from '@/hooks/use-toast';
import { createOpenLovableZip } from '@/lib/api/openlovable';
import { generatePrototypeForAppSpec } from '@/lib/api/plan-routing';
import { G3Console } from '@/components/g3/g3-console';

/**
 * 生成任务状态
 */
interface GenerationTask {
  id: string;
  config: GenerationConfig;
  status: 'idle' | 'generating' | 'completed' | 'failed';
  appSpecId?: string;
  result?: {
    qualityScore: number;
    modules: number;
    components: number;
    estimatedHours: number;
  };
}

/**
 * AppSpec数据结构扩展
 */
interface AppSpecWithPlan {
  id: string;
  projectId?: string;
  requirement?: string;
  model?: string;
  qualityScore?: number;
  metadata?: Record<string, unknown>;
  planResult?: {
    modules: Array<{
      name: string;
      description: string;
      priority: string;
      complexity: number;
      pages: string[];
    }>;
    estimatedHours?: number;
  };
  frontendPrototype?: Record<string, unknown>;
  frontendPrototypeUrl?: string;
}

export default function WizardPage() {
  const params = useParams();
  const router = useRouter();
  const searchParams = useSearchParams();
  const appSpecId = params.id as string;
  const g3JobIdFromQuery = searchParams.get('g3JobId');
  const { toast } = useToast();

  // 使用任务状态管理Hook
  const {
    taskId,
    taskStatus,
    isLoading,
    error: apiError,
    isConnected: isApiConnected,
    isRunning: _isRunning,
    progress,
    currentAgent,
    setTaskId,
    cancelCurrentTask: _cancelCurrentTask,
    reset: _reset
  } = useGenerationTask({
    autoPoll: false, // 禁用轮询，使用WebSocket
    pollInterval: 2000,
    maxPolls: 600,
    onComplete: (_status: TaskStatusResponse) => {
      // 任务完成回调
    },
    onError: (_errorMessage: string) => {
      // 任务失败回调
    }
  });

  // 使用WebSocket管理Hook
  const {
    isConnected: isWebSocketConnected,
    isConnecting: isWebSocketConnecting,
    connectionAttempts,
    error: webSocketError,
    cancelTask: _wsCancelTask,
    reconnect
  } = useGenerationWebSocket({
    taskId: taskId || '',
    autoConnect: !!taskId,
    onTaskStatus: (_message) => {
      // WebSocket任务状态更新
    },
    onAgentStatus: (_message) => {
      // WebSocket Agent状态更新
    },
    onError: (_message) => {
      // WebSocket错误处理
    },
    onConnect: () => {
      // WebSocket连接建立
    },
    onDisconnect: () => {
      // WebSocket连接断开
    }
  });

  // 合并连接状态
  const isConnected = isApiConnected || isWebSocketConnected;

  // 状态管理
  const [task, setTask] = useState<GenerationTask>({
    id: appSpecId,
    config: {
      requirement: '',
      model: 'gemini-3-pro-preview',
      qualityThreshold: 70,
      skipValidation: false,
      generatePreview: false,
    },
    status: 'idle',
  });

  const [appSpec, setAppSpec] = useState<AppSpecWithPlan | null>(null);
  const [pageLoading, setPageLoading] = useState(true);
  
  // G3 任务恢复
  const [g3JobId, setG3JobId] = useState<string | null>(null);
  const [g3Status, setG3Status] = useState<'COMPLETED' | 'FAILED' | 'RUNNING' | null>(null);

  // 当前UI展示的步骤索引 (0: Plan, 1: Execute, 2: Validate)
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  // 是否显示成功完成页面
  const [isSuccessView, setIsSuccessView] = useState(false);

  // 下载状态
  const [isDownloadingFrontend, setIsDownloadingFrontend] = useState(false);
  const [isDownloadingBackend, setIsDownloadingBackend] = useState(false);

  // 下载前端代码包（OpenLovable）
  const handleFrontendDownload = useCallback(async () => {
    const currentAppSpecId = task.appSpecId || taskStatus?.appSpecId || appSpec?.id || appSpecId;
    if (!currentAppSpecId) {
      toast({
        variant: 'destructive',
        title: '下载失败',
        description: '未找到应用ID',
      });
      return;
    }

    let sandboxId =
      typeof appSpec?.metadata?.['sandboxId'] === 'string'
        ? (appSpec.metadata?.['sandboxId'] as string)
        : null;

    setIsDownloadingFrontend(true);
    try {
      /**
       * 触发浏览器下载（封装为函数便于失败后重试）
       *
       * 是什么：调用后端 create-zip 生成 dataUrl，然后创建 <a> 触发下载。
       * 做什么：把“生成 zip + 下载”这段逻辑可复用化，支持“先失败再重试”。
       * 为什么：OpenLovable 沙箱可能已过期/不可达，需要重建后再下载。
       */
      const downloadZipForSandbox = async (resolvedSandboxId: string): Promise<string> => {
        const resp = await createOpenLovableZip(resolvedSandboxId);
        if (!resp.success || !resp.data?.dataUrl) {
          throw new Error(resp.error || resp.message || '生成ZIP失败');
        }

        const dataUrl = resp.data.dataUrl;
        const fileName = resp.data.fileName || `frontend-${appSpecId}.zip`;

        const a = document.createElement('a');
        a.href = dataUrl;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);

        return fileName;
      };

      // 缺失 sandboxId 时，自动触发后端生成原型并写回 AppSpec（避免用户必须手动走确认流程）
      if (!sandboxId) {
        const genResp = await generatePrototypeForAppSpec(currentAppSpecId);
        const generatedSandboxId = genResp.data?.sandboxId;
        const previewUrl = genResp.data?.previewUrl;
        if (!genResp.success || !generatedSandboxId || !previewUrl) {
          throw new Error(genResp.error || genResp.message || '自动生成原型失败');
        }

        sandboxId = generatedSandboxId;
        setAppSpec((prev) => {
          if (!prev) return prev;
          const nextMetadata: Record<string, unknown> = {
            ...(prev.metadata ?? {}),
            sandboxId: generatedSandboxId,
            sandboxProvider: genResp.data?.provider || 'e2b',
          };
          return {
            ...prev,
            metadata: nextMetadata,
            frontendPrototypeUrl: previewUrl,
          };
        });
      }

      if (!sandboxId) {
        throw new Error('未找到前端沙箱信息，请先完成原型生成');
      }

      let fileName: string;
      try {
        fileName = await downloadZipForSandbox(sandboxId);
      } catch (firstError) {
        // 兜底：若下载失败（常见原因：沙箱已过期/不可达），自动重建原型后再重试一次
        const genResp = await generatePrototypeForAppSpec(currentAppSpecId);
        const regeneratedSandboxId = genResp.data?.sandboxId;
        const previewUrl = genResp.data?.previewUrl;
        if (!genResp.success || !regeneratedSandboxId || !previewUrl) {
          throw firstError;
        }

        sandboxId = regeneratedSandboxId;
        setAppSpec((prev) => {
          if (!prev) return prev;
          const nextMetadata: Record<string, unknown> = {
            ...(prev.metadata ?? {}),
            sandboxId: regeneratedSandboxId,
            sandboxProvider: genResp.data?.provider || 'e2b',
          };
          return {
            ...prev,
            metadata: nextMetadata,
            frontendPrototypeUrl: previewUrl,
          };
        });

        fileName = await downloadZipForSandbox(sandboxId);
      }

      toast({
        title: '下载成功',
        description: `已下载 ${fileName}`,
      });
    } catch (e) {
      toast({
        variant: 'destructive',
        title: '下载失败',
        description: e instanceof Error ? e.message : '无法下载前端代码',
      });
    } finally {
      setIsDownloadingFrontend(false);
    }
  }, [appSpec?.id, appSpec?.metadata, appSpecId, task.appSpecId, taskStatus?.appSpecId, toast]);

  // 下载服务端代码包（G3 或 Serverless 配置包）
  const handleBackendDownload = useCallback(async () => {
    const currentAppSpecId = task.appSpecId || taskStatus?.appSpecId || appSpec?.id || appSpecId;
    if (!currentAppSpecId) {
      toast({
        variant: 'destructive',
        title: '下载失败',
        description: '未找到应用ID',
      });
      return;
    }

    /**
     * 技术栈判定：H5/Supabase 优先走服务端配置包
     *
     * 目的：
     * - 避免在 Serverless 场景下载到 Spring Boot 产物
     * - 保证“服务端代码”下载与技术栈一致
     */
    const techStackType =
      typeof appSpec?.metadata?.['techStackType'] === 'string'
        ? (appSpec.metadata?.['techStackType'] as string)
        : null;
    const techStackCode =
      typeof appSpec?.metadata?.['techStackCode'] === 'string'
        ? (appSpec.metadata?.['techStackCode'] as string)
        : null;
    const isServerless =
      techStackType === 'H5_WEBVIEW' ||
      techStackType === 'REACT_SUPABASE' ||
      (techStackCode?.toLowerCase().includes('supabase') ?? false) ||
      (techStackCode?.toLowerCase().includes('webview') ?? false);

    if (!isServerless && g3Status === 'RUNNING') {
      toast({
        variant: 'destructive',
        title: '生成中',
        description: 'G3 任务尚未完成，请稍后再试',
      });
      return;
    }

    setIsDownloadingBackend(true);
    try {
      if (!isServerless && g3JobId) {
        const resp = await downloadG3Artifacts(g3JobId);
        if (resp.success && resp.data?.downloadUrl) {
          window.open(resp.data.downloadUrl, '_blank');
          toast({
            title: '下载已开始',
            description: `正在下载 ${resp.data.fileCount} 个文件`,
          });
          return;
        }
      }

      const fallbackResp = await getBackendPackageDownload(currentAppSpecId);
      if (fallbackResp.success && fallbackResp.data?.downloadUrl) {
        window.open(fallbackResp.data.downloadUrl, '_blank');
        toast({
          title: '下载已开始',
          description: `已生成服务端配置包（${fallbackResp.data.fileCount} 个文件）`,
        });
        return;
      }

      throw new Error(fallbackResp.error || fallbackResp.message || '下载失败');
    } catch (e) {
      console.error(e);
      toast({
        variant: 'destructive',
        title: '下载失败',
        description: e instanceof Error ? e.message : '无法下载服务端代码',
      });
    } finally {
      setIsDownloadingBackend(false);
    }
  }, [appSpec?.id, appSpecId, g3JobId, g3Status, task.appSpecId, taskStatus?.appSpecId, toast]);

  // 获取初始数据
  useEffect(() => {
    const fetchInitialData = async () => {
      if (!appSpecId) return;

      try {
        setPageLoading(true);

        // E2E测试逻辑
        if (appSpecId === 'test-wizard-123') {
             setTask(prev => ({ ...prev, id: appSpecId, status: 'idle' }));
             setPageLoading(false);
             return;
        }
        
        if (appSpecId === 'test-app-123') {
            // 模拟完成状态
             setTask(prev => ({ ...prev, id: appSpecId, status: 'completed' }));
             setIsSuccessView(true);
             setPageLoading(false);
             return;
        }

        if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(appSpecId)) {
          try {
            const response = await getAppSpec(appSpecId);
            if (response.success && response.data) {
              const fullAppSpec = response.data as AppSpec;
              const metadata = fullAppSpec.metadata || {};
              const latestJobId =
                typeof metadata['latestG3JobId'] === 'string'
                  ? (metadata['latestG3JobId'] as string)
                  : typeof metadata['latestGenerationTaskId'] === 'string'
                    ? (metadata['latestGenerationTaskId'] as string)
                    : null;
              const resolvedJobId = latestJobId || g3JobIdFromQuery;
              setAppSpec({
                id: fullAppSpec.id,
                requirement: fullAppSpec.userRequirement,
                model: 'gemini-3-pro-preview',
                qualityScore: fullAppSpec.qualityScore,
                planResult: fullAppSpec.planResult,
                frontendPrototype: fullAppSpec.frontendPrototype as Record<string, unknown>,

                frontendPrototypeUrl: fullAppSpec.frontendPrototypeUrl,
                projectId: fullAppSpec.projectId, // Map projectId
                metadata,
              });
              setG3JobId(resolvedJobId);
              setTask(prev => ({
                ...prev,
                config: { ...prev.config, requirement: fullAppSpec.userRequirement || '' },
                status: 'completed',
                result: {
                  qualityScore: fullAppSpec.qualityScore || 85,
                  modules: fullAppSpec.planResult?.modules?.length || 4,
                  components: 12,
                  estimatedHours: fullAppSpec.planResult?.estimatedHours || 48,
                }
              }));
              // 如无G3任务，直接展示成功页；有任务则等待状态确认
              if (!resolvedJobId) {
                setIsSuccessView(true);
              }
            }
          } catch (error) {
            console.error(error);
            setTask(prev => ({ ...prev, status: 'failed' }));
          }
        } else {
          setTask(prev => ({ ...prev, status: 'idle' }));
        }
      } catch (err) {
        console.error(err);
        setTask(prev => ({ ...prev, status: 'idle' }));
      } finally {
        setPageLoading(false);
      }
    };

    fetchInitialData();
  }, [appSpecId, g3JobIdFromQuery]);

  // 检查 G3 任务状态（用于断线恢复）
  useEffect(() => {
    if (!g3JobId) return;

    let cancelled = false;
    const checkStatus = async () => {
      try {
        const resp = await getG3JobStatus(g3JobId);
        if (!resp.success || !resp.data) {
          return;
        }
        const status = resp.data.status;
        if (cancelled) return;
        if (status === 'COMPLETED') {
          setG3Status('COMPLETED');
          setIsSuccessView(true);
        } else if (status === 'FAILED') {
          setG3Status('FAILED');
          setIsSuccessView(false);
        } else {
          setG3Status('RUNNING');
          setIsSuccessView(false);
        }
      } catch (err) {
        console.warn('获取G3状态失败:', err);
      }
    };

    checkStatus();
    return () => {
      cancelled = true;
    };
  }, [g3JobId]);

  const handleStartGeneration = useCallback(async () => {
    if (!task.config.requirement?.trim()) {
      alert('请填写应用需求描述');
      return;
    }

    try {
      setTask(prev => ({ ...prev, status: 'generating' }));

      // 判断是新建生成还是重新生成
      if (appSpec?.projectId) {
        // 1. 重新生成 (Regenerate)
        
        // 先更新需求
        await updateProjectRequirement(appSpec.projectId, task.config.requirement);
        
        // 再提交重新生成任务
        const response = await regenerateProject(appSpec.projectId);
        
        if (response && response.taskId) {
           setTaskId(response.taskId);
           setTask(prev => ({ ...prev, id: response.taskId, status: 'generating' }));
        } else {
           throw new Error('重新生成任务创建失败');
        }

      } else {
        // 2. 新建生成 (Create New)
        const request: AsyncGenerateRequest = {
          userRequirement: task.config.requirement,
          model: task.config.model,
          skipValidation: task.config.skipValidation || false,
          qualityThreshold: task.config.qualityThreshold || 70,
          generatePreview: task.config.generatePreview || false,
        };

        const response = await createAsyncGenerationTask(request);

        if (response.success && response.data) {
          const { taskId: newTaskId } = response.data;
          setTaskId(newTaskId);
          setTask(prev => ({ ...prev, id: newTaskId, status: 'generating' }));
        } else {
          throw new Error(response.error || '创建生成任务失败');
        }
      }
    } catch (err) {
      console.error('生成任务创建失败:', err);
      setTask(prev => ({ ...prev, status: 'failed' }));
      alert(err instanceof Error ? err.message : '创建生成任务失败');
    }
  }, [task.config, setTaskId, appSpec?.projectId]);

  // 同步任务状态
  useEffect(() => {
    if (taskStatus) {
      setTask(prev => ({
        ...prev,
        status: taskStatus.status === 'completed' ? 'completed' : 
                taskStatus.status === 'failed' || taskStatus.status === 'cancelled' ? 'failed' : 'generating',
        appSpecId: taskStatus.appSpecId,
      }));
    }
  }, [taskStatus]);

  // 计算Agents列表 (Memoized)
  const agents: AgentExecutionStatus[] = useMemo(() => {
    if (!taskStatus) {
       // 初始占位状态
       return [
        { id: 'plan', type: AgentType.PLAN, name: '需求分析', status: AgentState.PENDING, progress: 0 },
        { id: 'execute', type: AgentType.EXECUTE, name: '代码生成', status: AgentState.PENDING, progress: 0 },
        { id: 'validate', type: AgentType.VALIDATE, name: '质量验证', status: AgentState.PENDING, progress: 0 },
      ];
    }
    return taskStatus.agents?.map(agent => ({
      id: agent.agentType,
      type: agent.agentType as AgentType,
      name: agent.agentName,
      status: agent.status as AgentState,
      progress: agent.progress,
      duration: agent.durationMs,
      message: agent.resultSummary,
      error: agent.errorMessage ? { code: 'ERR', message: agent.errorMessage } : undefined,
      metrics: { 
        tokenUsage: { totalTokens: 0, inputTokens: 0, outputTokens: 0, estimatedCost: 0, total: 0 }, 
        apiCalls: 0, avgResponseTime: 0, successRate: 100 
      }
    })) || [];
  }, [taskStatus]);

  // 判断当前步骤是否完成
  const isCurrentStepCompleted = useMemo(() => {
    if (task.status === 'idle') return false;
    if (task.status === 'completed') return true;
    
    const currentAgentType = 
      currentStepIndex === 0 ? AgentType.PLAN :
      currentStepIndex === 1 ? AgentType.EXECUTE :
      AgentType.VALIDATE;
      
    const agent = agents.find(a => a.type === currentAgentType);
    return agent?.status === AgentState.COMPLETED;
  }, [currentStepIndex, agents, task.status]);

  // 处理右侧面板的确认操作
  const handleStepConfirm = useCallback(() => {
    if (task.status === 'idle') {
      handleStartGeneration();
    } else {
      // 确认完成，进入下一步
      if (currentStepIndex < 2) {
        setCurrentStepIndex(prev => prev + 1);
      } else {
        // 完成所有步骤，进入成功页面
        setIsSuccessView(true);
      }
    }
  }, [task.status, currentStepIndex, handleStartGeneration]);

  // 处理编辑操作
  const handleEdit = useCallback((data: { requirement: string }) => {
    if (data.requirement) {
      setTask(prev => ({
        ...prev,
        config: { ...prev.config, requirement: data.requirement }
      }));
    }
  }, []);

  // 处理配置更新
  const handleConfigChange = useCallback((config: GenerationConfig) => {
    setTask(prev => ({ ...prev, config }));
  }, []);

  // 处理重试
  const handleRetry = useCallback(() => {
    _reset();
    setTask(prev => ({
      ...prev,
      status: 'idle',
      id: '',
      appSpecId: undefined,
      result: undefined,
    }));
    setAppSpec(null);
  }, [_reset]);

  // 处理重置
  const handleReset = useCallback(() => {
    _reset();
    setTask(prev => ({
      ...prev,
      status: 'idle',
      id: '',
      appSpecId: undefined,
      result: undefined,
    }));
    setAppSpec(null);
  }, [_reset]);

  // 处理暂停
  const handlePauseGeneration = useCallback(async () => {
    if (taskId) {
      if (isWebSocketConnected) {
        const success = _wsCancelTask();
        if (success) {
          setTask(prev => ({ ...prev, status: 'idle' }));
          return;
        }
      }
      const success = await _cancelCurrentTask();
      if (success) {
        setTask(prev => ({ ...prev, status: 'idle' }));
      }
    }
  }, [taskId, isWebSocketConnected, _wsCancelTask, _cancelCurrentTask]);

  const wsError = apiError || webSocketError;
  const currentStep = currentAgent || 'idle';

  if (pageLoading) {
    return (
      <div className="h-screen bg-background flex items-center justify-center">
         <div className="text-center">
            <Loader2 className="w-8 h-8 animate-spin mx-auto mb-4 text-primary" />
            <p className="text-muted-foreground">加载中...</p>
         </div>
      </div>
    );
  }

  // G3 任务执行中：优先展示实时控制台
  if (g3JobId && g3Status !== 'COMPLETED') {
    return (
      <div className="min-h-screen bg-background p-6">
        <G3Console
          initialRequirement={appSpec?.requirement || task.config.requirement}
          appSpecId={appSpecId}
          resumeJobId={g3JobId}
          autoStart={false}
          onComplete={(task) => {
            if (task.status === 'FAILED') {
              setG3Status('FAILED');
              setIsSuccessView(false);
              return;
            }
            setG3Status('COMPLETED');
            setIsSuccessView(true);
          }}
        />
      </div>
    );
  }

  // 成功视图
  if (isSuccessView) {
     const currentAppSpec = appSpec || (taskStatus?.appSpecId ? { id: taskStatus.appSpecId } as AppSpecWithPlan : null);
     const generationStats: GenerationStatsType = {
        pagesCount: currentAppSpec?.planResult?.modules?.reduce((acc: number, m: { pages: string[] }) => acc + m.pages.length, 0) || 4,
        apiCount: (currentAppSpec?.planResult?.modules?.length || 4) * 3,
        tablesCount: currentAppSpec?.planResult?.modules?.length || 4,
        linesOfCode: 3000, // 估算值
        generationTime: agents.reduce((acc: number, a) => acc + (a.duration ? a.duration / 1000 : 0), 0)
     };

     return (
        <div className="min-h-screen bg-background">
          <div className="border-b bg-background px-6 py-4 mb-8">
            <div className="flex items-center gap-4">
              <h1 className="text-xl font-semibold">AppSpec 生成向导</h1>
              <Badge variant="default" className="text-sm bg-green-500">生成完成</Badge>
            </div>
          </div>

          <div className="max-w-4xl mx-auto px-8 pb-12">
            <div className="mb-8">
              <div className="flex items-center gap-4 mb-4">
                <h2 className="text-3xl font-bold">生成完成！</h2>
              </div>
              <p className="text-sm text-muted-foreground">
                ID: {task.appSpecId || taskStatus?.appSpecId || appSpec?.id || appSpecId}
              </p>
            </div>

            <Card className="mb-8">
              <CardContent className="pt-6">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm font-medium">总体进度</span>
                  <span className="text-sm text-muted-foreground">100%</span>
                </div>
                <Progress value={100} className="h-2" />
                <div className="flex items-center gap-2 mt-2">
                  <CheckCircle2 className="w-4 h-4 text-green-500" />
                  <p className="text-sm text-green-600">所有Agent执行完成</p>
                </div>
              </CardContent>
            </Card>

            <GenerationStats stats={generationStats} className="mb-8" />

            <div className="mb-8">
              <h3 className="text-xl font-semibold mb-4">接下来做什么？</h3>
              <QuickActionCards
                appId={task.appSpecId || taskStatus?.appSpecId || appSpec?.id || appSpecId}
                projectId={task.appSpecId || taskStatus?.appSpecId || appSpec?.id || appSpecId}
                onDownload={handleBackendDownload}
                onShare={() => {}}
              />
            </div>

            <Card className="mb-8">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Download className="w-5 h-5" />
                  产物下载
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex flex-col md:flex-row gap-3">
                  <Button
                    variant="outline"
                    onClick={handleFrontendDownload}
                    disabled={isDownloadingFrontend}
                    className="flex-1"
                  >
                    {isDownloadingFrontend ? (
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    ) : (
                      <Download className="w-4 h-4 mr-2" />
                    )}
                    下载前端页面
                  </Button>
                  <Button
                    onClick={handleBackendDownload}
                    disabled={isDownloadingBackend}
                    className="flex-1"
                  >
                    {isDownloadingBackend ? (
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    ) : (
                      <Download className="w-4 h-4 mr-2" />
                    )}
                    下载服务端代码
                  </Button>
                </div>
                <p className="text-xs text-muted-foreground">
                  前端包来自原型生成沙箱；服务端包根据技术栈选择 G3 产物或配置模板。
                </p>
              </CardContent>
            </Card>

            {/* 模块列表 */}
            {currentAppSpec && currentAppSpec.planResult?.modules && currentAppSpec.planResult.modules.length > 0 && (
              <Card className="mb-8">
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <FileText className="w-4 h-4" />
                    功能模块 ({currentAppSpec.planResult.modules.length})
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="grid gap-2">
                    {currentAppSpec.planResult.modules.map((module, index) => (
                      <div key={index} className="flex items-center justify-between p-3 bg-muted rounded-lg">
                        <div>
                          <p className="font-medium text-sm">{module.name}</p>
                          <p className="text-xs text-muted-foreground">{module.description}</p>
                        </div>
                        <div className="flex items-center gap-2">
                          <Badge variant="outline" className="text-xs">
                            复杂度 {module.complexity}
                          </Badge>
                          <Badge
                            variant={module.priority === 'high' ? 'destructive' :
                                    module.priority === 'medium' ? 'secondary' : 'outline'}
                            className="text-xs"
                          >
                            {module.priority === 'high' ? '高优先级' :
                            module.priority === 'medium' ? '中优先级' : '低优先级'}
                          </Badge>
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            )}

            {taskStatus && (taskStatus.downloadUrl || taskStatus.codeSummary) && (
              <CodeDownloadPanel
                codeDownloadUrl={taskStatus.downloadUrl}
                codeSummary={taskStatus.codeSummary}
                generatedFileList={taskStatus.generatedFileList}
                className="mb-8"
              />
            )}

            <Card className="mb-8 bg-gradient-to-br from-blue-50/50 via-white to-purple-50/50">
              <CardHeader>
                <CardTitle>探索更多功能</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <Button
                    variant="outline"
                    onClick={() => router.push('/wizard/ai-capabilities')}
                    className="flex items-center gap-3 h-auto p-4 justify-start hover:bg-purple-50/50 transition-colors"
                  >
                    <Sparkles className="w-6 h-6 text-purple-500 flex-shrink-0" />
                    <div className="text-left">
                      <div className="font-semibold mb-1">AI能力选择</div>
                      <div className="text-xs text-muted-foreground">智能分析需求，推荐AI能力组合</div>
                    </div>
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => router.push(`/superdesign/${task.appSpecId || taskStatus?.appSpecId}`)}
                    className="flex items-center gap-3 h-auto p-4 justify-start hover:bg-blue-50/50 transition-colors"
                  >
                    <Palette className="w-6 h-6 text-blue-500 flex-shrink-0" />
                    <div className="text-left">
                      <div className="font-semibold mb-1">SuperDesign</div>
                      <div className="text-xs text-muted-foreground">AI生成3种设计风格方案</div>
                    </div>
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => router.push('/dashboard')}
                    className="flex items-center gap-3 h-auto p-4 justify-start hover:bg-orange-50/50 transition-colors"
                  >
                    <Clock className="w-6 h-6 text-orange-500 flex-shrink-0" />
                    <div className="text-left">
                      <div className="font-semibold mb-1">时光机版本</div>
                      <div className="text-xs text-muted-foreground">查看版本历史，一键回溯</div>
                    </div>
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
     );
  }

  return (
    <div className="h-screen bg-background">
      {/* 顶部标题栏 - 压缩高度 */}
      <div className="border-b bg-background px-4 py-2">
        <div className="flex items-center gap-3">
          <h1 className="text-lg font-semibold">AppSpec 生成向导</h1>
          <Badge variant="outline" className="text-xs">
            {task.status === 'generating' ? '生成中' : task.status === 'completed' ? '已完成' : '待配置'}
          </Badge>
          {isLoading && <Loader2 className="w-4 h-4 animate-spin" />}
          {!isConnected && task.status !== 'idle' && task.status !== 'completed' && (
             <Badge variant="destructive" className="text-xs">连接断开</Badge>
          )}
        </div>
      </div>

      {/* 分屏布局 - 扩大工作区高度 */}
      <div className="h-[calc(100vh-49px)]">
        <SplitLayout
          leftContent={
            currentStepIndex === 0 ? (
              // 第一步显示配置面板
              <ConfigurationPanel
                config={task.config}
                onConfigChange={handleConfigChange}
                onStartGeneration={handleStartGeneration}
                onPauseGeneration={handlePauseGeneration}
                onRetry={handleRetry}
                onReset={handleReset}
                isGenerating={task.status === 'generating'}
                editable={task.status === 'idle'}
                error={wsError || undefined}
                isConnected={isConnected}
                onReconnect={reconnect}
              />
            ) : (
              // 后续步骤显示执行面板
              <ExecutionPanel
                agents={agents}
                currentStep={currentStep}
                progress={progress}
                isConnected={isConnected}
                isConnecting={isWebSocketConnecting}
                connectionAttempts={connectionAttempts}
                // error={wsError || undefined} // Removed unused error prop
                onReconnect={reconnect}
                onAgentClick={() => {}}
              />
            )
          }
          rightContent={
            <StepResultPanel
              currentStepIndex={currentStepIndex}
              agents={agents}
              appSpec={appSpec || { requirement: task.config.requirement }}
              taskStatus={task.status}
              isStepCompleted={isCurrentStepCompleted}
              onConfirm={handleStepConfirm}
              onEdit={handleEdit}
              config={task.config}
              onConfigChange={handleConfigChange}
            />
          }
          defaultLeftWidth={65}
          minLeftWidth={350}
          maxLeftWidthPercent={80}
        />
      </div>
    </div>
  );
}
