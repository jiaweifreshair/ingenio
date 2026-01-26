'use client';

import React, { useState, useEffect, useRef } from 'react';
import {
  CheckCircle2,
  Circle,
  Loader2,
  XCircle,
  ChevronDown,
  ChevronRight,
  Database,
  Layout,
  Brain,
  LucideIcon,
  Briefcase,
  Code,
  ScanSearch,
  Palette
} from 'lucide-react';
import { type AnalysisProgressMessage } from '@/hooks/use-analysis-sse';
import { cn } from '@/lib/utils';
import { Card } from '@/components/ui/card';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Typewriter } from '@/components/ui/typewriter';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import type { PhaseType } from '@/types/requirement-form';
import { PlanDisplay } from './PlanDisplay';
// import { useLanguage } from '@/contexts/LanguageContext';
import { StepResultDisplay } from './StepResultDisplay';
import type { StepResult } from '@/types/analysis-step-results';
import { normalizeStepResult } from './step-result-normalizer';

export interface AnalysisProgressPanelProps {
  requirement?: string;
  messages: AnalysisProgressMessage[];
  isConnected: boolean;
  isCompleted: boolean;
  isLoading?: boolean;
  error: string | null;
  finalResult?: unknown;
  currentPhase?: PhaseType;
  /** 本地存储Key（可选）：用于持久化已完成步骤的结构化结果，支持刷新后回看 */
  storageKey?: string;
  onConfirmPlan?: () => void;
  onModifyPlan?: (requirement: string) => void;
  onConfirmStep?: (step: number) => void;
  onModifyStep?: (step: number, feedback: string) => void | Promise<void>;
}

const STEP_CONFIG = [
  { name: '👩‍💼 产品经理 (PM)', icon: Briefcase, description: '产品经理正在分析您的需求，拆解业务流程...' },
  { name: '👨‍💻 数据架构师', icon: Database, description: '架构师正在设计数据模型与实体关系...' },
  { name: '🕵️ 业务分析师', icon: ScanSearch, description: '分析师正在识别功能意图与边界...' },
  { name: '🏗️ 技术负责人', icon: Code, description: 'Tech Lead 正在选型技术栈与开发框架...' },
  { name: '👩‍🎨 交互设计师', icon: Palette, description: '设计师正在智能识别场景并生成最佳交互方案...' },
  { name: '🧠 首席架构师', icon: Brain, description: '首席架构师正在生成最终实施蓝图...' }
];

/**
 * 步骤日志项组件
 */
const StepLogItem = ({
  step,
  config,
  status,
  message,
  isExpanded,
  onToggle,
  onViewResult
}: {
  step: number;
  config: { name: string; icon: LucideIcon; description: string };
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  message: AnalysisProgressMessage | null;
  isExpanded: boolean;
  onToggle: () => void;
  onViewResult?: () => void;
}) => {
  // const { t } = useLanguage(); // Removed unused translation hook
  const Icon = config.icon;
  const progressPercent = message?.progress || 0;
  const canViewResult = status === 'COMPLETED' && step !== 6 && !!onViewResult;

  return (
    <div className={cn(
      "border rounded-lg transition-all duration-300 overflow-hidden",
      status === 'RUNNING' && "border-blue-500 bg-blue-50/30 dark:bg-blue-900/10 ring-1 ring-blue-200 dark:ring-blue-800",
      status === 'COMPLETED' && "border-green-500/50 bg-green-50/30 dark:bg-green-900/5",
      status === 'FAILED' && "border-red-500 bg-red-50/30 dark:bg-red-900/10",
      status === 'PENDING' && "border-border bg-card opacity-60"
    )}>
      {/* 头部点击区域 */}
      <div 
        className="flex items-center p-3 cursor-pointer hover:bg-accent/50 transition-colors"
        onClick={() => {
          // 完成态：优先进入“结果回看”，展开日志由右侧箭头控制
          if (canViewResult) {
            onViewResult?.();
            return;
          }
          onToggle();
        }}
      >
        <div className="flex-shrink-0 mr-3">
          {status === 'COMPLETED' ? (
            <CheckCircle2 className="h-5 w-5 text-green-600 dark:text-green-400" />
          ) : status === 'RUNNING' ? (
            <Loader2 className="h-5 w-5 text-blue-600 dark:text-blue-400 animate-spin" />
          ) : status === 'FAILED' ? (
            <XCircle className="h-5 w-5 text-red-600 dark:text-red-400" />
          ) : (
            <Circle className="h-5 w-5 text-muted-foreground" />
          )}
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between">
            <h3 className={cn(
              "font-medium text-sm",
              status === 'RUNNING' && "text-blue-700 dark:text-blue-300",
              status === 'COMPLETED' && "text-green-700 dark:text-green-300"
            )}>
              {config.name}
            </h3>
            <div className="flex items-center gap-2">
              <Icon className="h-4 w-4 text-muted-foreground opacity-50" />
              {status === 'RUNNING' && (
                <span className="text-xs font-mono text-blue-600 dark:text-blue-400">{progressPercent}%</span>
              )}
              <button
                type="button"
                className="rounded p-0.5 hover:bg-accent"
                onClick={(e) => {
                  e.stopPropagation();
                  onToggle();
                }}
                aria-label={isExpanded ? '收起步骤日志' : '展开步骤日志'}
              >
                {isExpanded ? (
                  <ChevronDown className="h-4 w-4 text-muted-foreground" />
                ) : (
                  <ChevronRight className="h-4 w-4 text-muted-foreground" />
                )}
              </button>
            </div>
          </div>
          <p className="text-xs text-muted-foreground truncate pr-4">
             {message?.detail ? (message.detail.length > 50 ? message.detail.substring(0, 50) + '...' : message.detail) : config.description}
          </p>
        </div>
      </div>

      {/* 展开的内容区域 - 模拟终端输出 */}
      {isExpanded && (
        <div className="bg-zinc-950 dark:bg-black text-zinc-300 p-3 font-mono text-xs border-t border-border/50">
          <div className="space-y-3 font-mono text-xs">
            <div className="flex items-center text-zinc-500 gap-2 border-b border-zinc-900 pb-2 mb-2">
              <div className="flex items-center gap-1.5 px-2 py-0.5 rounded bg-zinc-900 text-zinc-400">
                 <Icon className="w-3 h-3" />
                 <span>{config.name.split(' ')[1] || '智能体'} 活动日志</span>
              </div>
              {status === 'RUNNING' && <span className="animate-pulse text-blue-500">● 实时</span>}
            </div>
            
            {status === 'PENDING' && (
              <div className="text-zinc-600 italic pl-2 border-l-2 border-zinc-900">
                等待上游智能体交付...
              </div>
            )}

            {(status === 'RUNNING' || status === 'COMPLETED') && (
               <div className="animate-in fade-in slide-in-from-left-1 duration-300 pl-2 border-l-2 border-blue-500/30">
                 <div className="text-blue-400 mb-1">
                    {status === 'RUNNING' ? '⚡ 正在分析需求...' : '✓ 分析已完成'}
                 </div>
                 {message?.detail && (
                   <div className="text-zinc-300 min-h-[1.5em] whitespace-pre-wrap leading-relaxed">
                     <Typewriter 
                       text={message.detail} 
                       speed={step === 6 ? 5 : 10} 
                       instant={status === 'COMPLETED' && step !== 6}
                     />
                   </div>
                 )}
               </div>
            )}

            {/* 结构化结果预览 */}
            {status === 'COMPLETED' && !!message?.result && step !== 6 && (
              <div className="mt-3 bg-zinc-900/50 rounded p-3 border border-zinc-800/50 overflow-x-auto">
                <div className="flex items-center gap-2 text-zinc-500 mb-2 text-[10px] uppercase tracking-wider">
                  <span className="w-1.5 h-1.5 rounded-full bg-green-500"></span>
                  输出产物
                </div>
                <pre className="text-green-300/90 text-[10px] leading-relaxed font-mono">
                  {JSON.stringify(message.result as object, null, 2)}
                </pre>
              </div>
            )}
            
            {status === 'FAILED' && message?.error && (
               <div className="text-red-400 font-bold pl-2 border-l-2 border-red-500">
                 Error: {message.error}
               </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

/**
 * 判断是否处于"等待原型生成"状态
 * 条件：SSE分析完成但路由结果还未返回
 */
const isWaitingForPrototype = (isCompleted: boolean, finalResult: unknown): boolean => {
  return isCompleted && !finalResult;
};

/**
 * 已完成步骤结果的本地存储结构（V1）
 *
 * 用途：
 * - 解决“每个完成的任务需要存储并可点击查看”的诉求；
 * - 在页面刷新/返回后仍可回看已完成步骤的结构化结果（StepResult）。
 */
type StoredStepResultsV1 = {
  version: 1;
  updatedAt: string;
  stepResults: Record<string, StepResult>;
};

/**
 * 从 localStorage 读取步骤结果（安全兜底）
 */
function loadStepResultsFromStorage(storageKey?: string): Record<number, StepResult> {
  if (!storageKey) return {};
  if (typeof window === 'undefined') return {};

  try {
    const raw = localStorage.getItem(storageKey);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as StoredStepResultsV1;
    if (!parsed || parsed.version !== 1 || !parsed.stepResults) return {};

    const result: Record<number, StepResult> = {};
    for (const [key, value] of Object.entries(parsed.stepResults)) {
      const stepNumber = Number(key);
      if (!Number.isFinite(stepNumber)) continue;
      if (!value || typeof value !== 'object') continue;
      result[stepNumber] = value;
    }
    return result;
  } catch {
    return {};
  }
}

/**
 * 将步骤结果写入 localStorage（安全兜底）
 */
function saveStepResultsToStorage(storageKey: string, stepResults: Record<number, StepResult>): void {
  if (typeof window === 'undefined') return;

  try {
    const payload: StoredStepResultsV1 = {
      version: 1,
      updatedAt: new Date().toISOString(),
      stepResults: Object.fromEntries(
        Object.entries(stepResults).map(([k, v]) => [String(k), v])
      ) as Record<string, StepResult>,
    };
    localStorage.setItem(storageKey, JSON.stringify(payload));
  } catch {
    // ignore：localStorage 可能不可用（隐私模式/容量限制等）
  }
}

export function AnalysisProgressPanel({
  requirement,
  messages,
  isConnected, // Keep prop but mark as used or ignore
  isCompleted,
  isLoading,
  error,
  finalResult,
  currentPhase,
  storageKey,
  onConfirmPlan,
  onModifyPlan,
  onConfirmStep,
  onModifyStep
}: AnalysisProgressPanelProps): React.ReactElement {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [expandedStep, setExpandedStep] = useState<number | null>(1);

  // 步骤确认状态管理
  const [waitingForStepConfirmation, setWaitingForStepConfirmation] = useState<number | null>(null);
  const [stepResults, setStepResults] = useState<Record<number, StepResult>>(() => loadStepResultsFromStorage(storageKey));

  // 步骤修改弹窗：收集用户对当前步骤的修改建议（避免直接提交空参数导致后端返回“参数错误”）
  const [stepModifyDialogOpen, setStepModifyDialogOpen] = useState(false);
  const [stepModifyTargetStep, setStepModifyTargetStep] = useState<number | null>(null);
  const [stepModifyFeedback, setStepModifyFeedback] = useState('');
  const [isSubmittingStepModify, setIsSubmittingStepModify] = useState(false);

  // 回看弹窗：用于“已完成任务”点击查看
  const [stepViewDialogOpen, setStepViewDialogOpen] = useState(false);
  const [stepViewTargetStep, setStepViewTargetStep] = useState<number | null>(null);

  // storageKey 变化时加载（例如新建会话/刷新恢复）
  useEffect(() => {
    if (!storageKey) return;
    setStepResults(loadStepResultsFromStorage(storageKey));
  }, [storageKey]);

  // 持久化保存已完成步骤结果
  useEffect(() => {
    if (!storageKey) return;
    saveStepResultsToStorage(storageKey, stepResults);
  }, [storageKey, stepResults]);

  // 判断当前是否处于等待原型生成状态
  const waitingForPrototype = isWaitingForPrototype(isCompleted, finalResult);

  // 🧠 跟踪 Step 6 的推理内容（DeepSeek R1 等推理模型的思考过程）
  const step6Messages = messages.filter(m => m.step === 6);
  const step6Reasoning = step6Messages
    .filter(m => m.reasoning)
    .map(m => m.reasoning)
    .join('');
  const isStep6Reasoning = step6Messages.some(m => m.isReasoning && m.status === 'RUNNING');

  // 检测步骤完成并提取结果
  useEffect(() => {
    // 检查Step 1-5是否完成，如果完成则提取结果并等待用户确认
    for (let step = 1; step <= 5; step++) {
      const stepMessages = messages.filter(m => m.step === step);
      if (stepMessages.length === 0) continue;

      const latestMessage = stepMessages[stepMessages.length - 1];

      // 如果步骤完成且有结果，且还没有保存结果，则保存并等待确认
      if (
        latestMessage.status === 'COMPLETED' &&
        latestMessage.result &&
        !stepResults[step] &&
        waitingForStepConfirmation === null
      ) {
        const normalized = normalizeStepResult(step as 1 | 2 | 3 | 4 | 5, latestMessage.result, {
          requirement,
          previousStepResults: {
            1: stepResults[1],
            2: stepResults[2],
            3: stepResults[3],
            4: stepResults[4]
          }
        });

        // 保存步骤结果（确保 UI 不因数据结构漂移而崩溃）
        setStepResults(prev => ({
          ...prev,
          [step]: normalized
        }));

        // 设置等待确认状态
        setWaitingForStepConfirmation(step);
      }
    }
  }, [messages, stepResults, waitingForStepConfirmation, requirement]);

  // 处理步骤确认
  const handleConfirmStep = (step: number) => {
    setWaitingForStepConfirmation(null);
    onConfirmStep?.(step);
  };

  // 处理步骤修改
  const handleModifyStep = (step: number) => {
    if (!onModifyStep) return;
    setStepModifyTargetStep(step);
    setStepModifyFeedback('');
    setStepModifyDialogOpen(true);
  };

  /**
   * 提交步骤修改
   *
   * 用途：
   * - 将用户输入的反馈传给上层（通常会调用 /interactive-analysis/{sessionId}/modify）
   * - 清理本地 stepResults，允许同一步骤重新产出并进入“等待确认”状态
   */
  const handleSubmitStepModify = async (): Promise<void> => {
    if (!onModifyStep) return;
    if (stepModifyTargetStep == null) return;

    const feedback = stepModifyFeedback.trim();
    if (!feedback) return;

    setIsSubmittingStepModify(true);
    // 清理旧结果，确保后续 SSE 新结果可覆盖
    setWaitingForStepConfirmation(null);
    setStepResults((prev) => {
      const next = { ...prev };
      delete next[stepModifyTargetStep];
      return next;
    });

    try {
      await Promise.resolve(onModifyStep(stepModifyTargetStep, feedback));
    } finally {
      setIsSubmittingStepModify(false);
      setStepModifyDialogOpen(false);
      setStepModifyTargetStep(null);
      setStepModifyFeedback('');
    }
  };

  /**
   * 打开步骤结果回看弹窗
   *
   * 用途：
   * - 支持“任务结果已保存，点击可查看”的交互；
   * - 复用 StepResultDisplay 的结构化展示能力。
   */
  const handleOpenStepView = (step: number) => {
    setStepViewTargetStep(step);
    setStepViewDialogOpen(true);
  };

  // 自动展开正在运行的步骤，等待原型时收起所有步骤
  useEffect(() => {
    if (waitingForPrototype) {
      // 等待原型生成时，收起所有步骤，显示简洁的等待UI
      setExpandedStep(null);
      return;
    }

    if (isCompleted) {
      // 完成时，保持最后一步展开，以便用户查看结果
      if (messages.length > 0) {
        const lastStep = messages[messages.length - 1].step;
        setExpandedStep(lastStep);
      }
      return;
    }

    // 查找真正正在运行的步骤（通过检查该步骤的最新状态，而不是历史消息）
    const currentlyRunningStep = STEP_CONFIG.map((_, i) => i + 1).find(step => {
       const { status } = getStepStatus(step);
       return status === 'RUNNING';
    });

    if (currentlyRunningStep && currentlyRunningStep !== expandedStep) {
      setExpandedStep(currentlyRunningStep);
    }
  }, [messages, isCompleted, expandedStep, waitingForPrototype, finalResult]);

  // 自动滚动到底部
  useEffect(() => {
    if (scrollRef.current) {
      // 使用 setTimeout 确保 DOM 更新后再滚动 (例如展开动画开始后)
      const timer = setTimeout(() => {
        if (scrollRef.current) {
          scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
      }, 100);
      return () => clearTimeout(timer);
    }
  }, [messages, expandedStep]);

  const getStepStatus = (step: number) => {
    const stepMessages = messages.filter(m => m.step === step);
    if (stepMessages.length === 0) return { status: 'PENDING' as const, message: null };
    const latest = stepMessages[stepMessages.length - 1];
    return { status: latest.status, message: latest };
  };

  const calculateProgress = () => {
    if (messages.length === 0) return 0;
    const latestProgress = messages[messages.length - 1]?.progress || 0;
    return latestProgress;
  };

  // 获取技术蓝图内容
  const blueprint = messages.find(m => m.step === 6 && m.status === 'COMPLETED')?.result as { blueprint?: string } | undefined;
  const showPlan = isCompleted && !!blueprint?.blueprint;

  if (showPlan) {
    return (
      <PlanDisplay
        planContent={blueprint!.blueprint!}
        onConfirm={onConfirmPlan || (() => {})}
        onModify={onModifyPlan || (() => {})}
        isGenerating={isLoading || currentPhase === 'style-selection' || currentPhase === 'prototype-preview'}
        reasoningContent={step6Reasoning || undefined}
        isReasoning={isStep6Reasoning}
      />
    );
  }

  return (
    <Card className="flex flex-col h-full border-0 shadow-none bg-transparent">
      {/* 头部状态区 */}
      <div className="mb-6 space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-2xl font-bold bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent">
              {isWaitingForPrototype(isCompleted, finalResult) ? '正在生成原型' : 'AI 深度思考中'}
            </h2>
            <div className="text-sm text-muted-foreground flex items-center gap-2 mt-1">
              {isWaitingForPrototype(isCompleted, finalResult) ? (
                <span className="flex items-center text-blue-600">
                  <Loader2 className="w-4 h-4 mr-1 animate-spin" />
                  AI正在设计您的应用原型，请稍候...
                </span>
              ) : isCompleted ? (
                <span className="flex items-center text-green-600">
                  <CheckCircle2 className="w-4 h-4 mr-1" /> 分析完成
                </span>
              ) : (
                <span className="flex items-center">
                  <Loader2 className="w-3 h-3 mr-1 animate-spin" />
                  {isConnected ? '正在构建思维链...' : '等待连接...'}
                </span>
              )}
            </div>
          </div>
          <div className="text-right">
             <div className="text-2xl font-mono font-bold text-primary">
               {calculateProgress()}%
             </div>
          </div>
        </div>

        {/* 进度条 */}
        <div className="h-1.5 w-full bg-secondary rounded-full overflow-hidden">
          <div 
            className="h-full bg-gradient-to-r from-blue-500 via-purple-500 to-pink-500 transition-all duration-500 ease-out"
            style={{ width: `${calculateProgress()}%` }}
          />
        </div>
      </div>

      {/* 错误提示 */}
      {error && (
        <div className="mb-4 p-4 rounded-lg bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-800 animate-in slide-in-from-top-2">
          <div className="flex items-start gap-3">
            <XCircle className="h-5 w-5 text-red-500 flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="font-semibold text-red-900 dark:text-red-100">中断</h3>
              <p className="text-sm text-red-700 dark:text-red-300">{error}</p>
            </div>
          </div>
        </div>
      )}

      {/* 步骤修改弹窗：用于收集用户修改建议并触发重新执行 */}
      <Dialog
        open={stepModifyDialogOpen}
        onOpenChange={(open) => {
          if (isSubmittingStepModify) return;
          setStepModifyDialogOpen(open);
          if (!open) {
            setStepModifyTargetStep(null);
            setStepModifyFeedback('');
          }
        }}
      >
        <DialogContent className="sm:max-w-[520px]">
          <DialogHeader>
            <DialogTitle>
              修改步骤：{stepModifyTargetStep ? STEP_CONFIG[stepModifyTargetStep - 1]?.name : ''}
            </DialogTitle>
            <DialogDescription>
              请输入你希望如何调整本步骤的结果，系统将基于你的反馈重新执行该步骤。
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-2">
            <Textarea
              value={stepModifyFeedback}
              onChange={(e) => setStepModifyFeedback(e.target.value)}
              placeholder={
                stepModifyTargetStep
                  ? `例如：补充/修正「${STEP_CONFIG[stepModifyTargetStep - 1]?.name}」中的关键点（字段、约束、模块划分等）...`
                  : '请输入修改建议...'
              }
              className="min-h-[120px] resize-none"
              disabled={isSubmittingStepModify}
            />
            <div className="text-xs text-muted-foreground">
              提示：尽量给出“可执行”的修改点，例如“实体增加字段 xxx”、“把架构改为 React + Spring Boot”。
            </div>
          </div>

          <DialogFooter className="gap-2 sm:gap-0">
            <Button
              variant="outline"
              onClick={() => setStepModifyDialogOpen(false)}
              disabled={isSubmittingStepModify}
            >
              取消
            </Button>
            <Button
              onClick={handleSubmitStepModify}
              disabled={!stepModifyFeedback.trim() || isSubmittingStepModify}
            >
              {isSubmittingStepModify ? '提交中...' : '提交修改'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 步骤结果回看弹窗：用于查看已完成任务的结构化结果 */}
      <Dialog
        open={stepViewDialogOpen}
        onOpenChange={(open) => {
          setStepViewDialogOpen(open);
          if (!open) {
            setStepViewTargetStep(null);
          }
        }}
      >
        <DialogContent className="sm:max-w-[860px]">
          <DialogHeader>
            <DialogTitle>
              查看步骤结果：{stepViewTargetStep ? STEP_CONFIG[stepViewTargetStep - 1]?.name : ''}
            </DialogTitle>
            <DialogDescription>
              已完成步骤结果已自动保存。若需要调整，请在“等待确认”的步骤中通过右侧对话框输入修改建议。
            </DialogDescription>
          </DialogHeader>

          <div className="max-h-[70vh] overflow-auto pr-1">
            {stepViewTargetStep && stepResults[stepViewTargetStep] ? (
              <StepResultDisplay
                result={stepResults[stepViewTargetStep]}
                onConfirm={() => setStepViewDialogOpen(false)}
                onModify={() => {}}
                loading={isLoading}
                confirmLabel="关闭"
                showModifyButton={false}
              />
            ) : (
              <div className="text-sm text-muted-foreground">暂无可展示的步骤结果</div>
            )}
          </div>
        </DialogContent>
      </Dialog>

      {/* 步骤列表区 - 等待原型时显示简洁的等待动画 */}
      <ScrollArea className="flex-1 -mx-4 px-4" ref={scrollRef}>
        {waitingForPrototype ? (
          /* 等待原型生成时的简洁UI */
          <div className="flex flex-col items-center justify-center py-12 space-y-6 animate-in fade-in duration-500">
            {/* 分析完成摘要 */}
            <div className="flex items-center gap-2 text-green-600 dark:text-green-400">
              <CheckCircle2 className="h-5 w-5" />
              <span className="font-medium">需求分析完成</span>
            </div>

            {/* 原型生成动画 */}
            <div className="relative">
              <div className="w-20 h-20 rounded-full border-4 border-blue-200 dark:border-blue-800" />
              <div className="absolute inset-0 w-20 h-20 rounded-full border-4 border-transparent border-t-blue-500 animate-spin" />
              <div className="absolute inset-0 flex items-center justify-center">
                <Layout className="h-8 w-8 text-blue-500" />
              </div>
            </div>

            {/* 提示文字 */}
            <div className="text-center space-y-2">
              <p className="text-lg font-medium text-foreground">正在生成7种设计风格</p>
              <p className="text-sm text-muted-foreground">
                AI正在为您的应用设计多种视觉方案，请稍候...
              </p>
              <p className="text-xs text-muted-foreground/70">
                通常需要60-90秒
              </p>
            </div>
          </div>
        ) : waitingForStepConfirmation !== null && stepResults[waitingForStepConfirmation] ? (
          /* 等待步骤确认时显示步骤结果 */
          <div className="space-y-4 pb-4">
            <StepResultDisplay
              result={stepResults[waitingForStepConfirmation]}
              onConfirm={() => handleConfirmStep(waitingForStepConfirmation)}
              onModify={() => handleModifyStep(waitingForStepConfirmation)}
              loading={isLoading}
            />
          </div>
        ) : (
          /* 正常的分析步骤列表 */
          <div className="space-y-3 pb-4">
            {STEP_CONFIG.map((config, index) => {
              const step = index + 1;
              const { status, message } = getStepStatus(step);
              const canView = !!stepResults[step];

              return (
                <StepLogItem
                  key={step}
                  step={step}
                  config={config}
                  status={status}
                  message={message}
                  isExpanded={expandedStep === step}
                  onToggle={() => setExpandedStep(expandedStep === step ? null : step)}
                  onViewResult={canView ? () => handleOpenStepView(step) : undefined}
                />
              );
            })}

            {/* 最终结果展示 */}
            {isCompleted && !!finalResult && (
               <div className="border rounded-lg border-green-500/30 bg-green-50/10 p-3 mt-4 animate-in slide-in-from-bottom-2">
                  <div className="flex items-center gap-2 mb-2">
                     <CheckCircle2 className="h-5 w-5 text-green-500" />
                     <h3 className="font-medium text-green-500">分析结论</h3>
                  </div>
                  <div className="bg-zinc-950 rounded p-3 text-xs font-mono text-zinc-300 overflow-x-auto border border-zinc-800">
                     <pre>{JSON.stringify(finalResult, null, 2)}</pre>
                  </div>
               </div>
            )}
          </div>
        )}
      </ScrollArea>

      {/* 原型生成中提示 - 当分析完成但仍处于style-selection阶段时显示 */}
      {isCompleted && currentPhase === 'style-selection' && (
        <div className="mt-4 p-3 rounded-lg border border-blue-500/30 bg-blue-50/10 animate-in slide-in-from-bottom-4 fade-in duration-700">
           <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                 <div className="relative">
                    <div className="absolute inset-0 rounded-full bg-blue-500/20 animate-ping"></div>
                    <Loader2 className="h-5 w-5 text-blue-500 animate-spin relative z-10" />
                 </div>
                 <div>
                    <h3 className="text-sm font-medium text-blue-600 dark:text-blue-400">正在生成交互原型</h3>
                    <p className="text-xs text-muted-foreground">AI正在基于技术蓝图构建界面...</p>
                 </div>
              </div>
              <span className="text-xs font-mono text-blue-500">PROTOTYPING...</span>
           </div>
        </div>
      )}
    </Card>
  );
}
