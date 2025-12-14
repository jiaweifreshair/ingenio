'use client';

import React, { useState, useEffect, useRef } from 'react';
import { 
  CheckCircle2, 
  Circle, 
  Loader2, 
  XCircle, 
  ChevronDown, 
  ChevronRight, 
  Terminal, 
  Cpu, 
  Database, 
  Layout, 
  Zap,
  LucideIcon
} from 'lucide-react';
import { type AnalysisProgressMessage } from '@/hooks/use-analysis-sse';
import { cn } from '@/lib/utils';
import { Card } from '@/components/ui/card';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Typewriter } from '@/components/ui/typewriter';

/**
 * 分析进度展示面板 - 思维链增强版
 *
 * 功能：
 * - "Chain of Thought" 可视化展示
 * - 终端/日志风格的详细推理过程
 * - 关键词高亮
 * - 结构化数据预览
 */
export interface AnalysisProgressPanelProps {
  messages: AnalysisProgressMessage[];
  isConnected: boolean;
  isCompleted: boolean;
  error: string | null;
  finalResult?: unknown;
}

const STEP_CONFIG = [
  { name: '需求语义解析', icon: Terminal, description: '正在解构您的自然语言需求...' },
  { name: '实体关系建模', icon: Database, description: '识别核心数据实体与关联...' },
  { name: '功能意图识别', icon: Cpu, description: '分析所需的功能模块与业务逻辑...' },
  { name: '技术架构选型', icon: Layout, description: '匹配最佳技术栈与设计模式...' },
  { name: '复杂度与风险评估', icon: Zap, description: '计算开发成本与潜在风险...' }
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
  onToggle 
}: { 
  step: number; 
  config: { name: string; icon: LucideIcon; description: string }; 
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'; 
  message: AnalysisProgressMessage | null;
  isExpanded: boolean;
  onToggle: () => void;
}) => {
  const Icon = config.icon;
  const progressPercent = message?.progress || 0;

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
        onClick={onToggle}
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
              {isExpanded ? <ChevronDown className="h-4 w-4 text-muted-foreground" /> : <ChevronRight className="h-4 w-4 text-muted-foreground" />}
            </div>
          </div>
          <p className="text-xs text-muted-foreground truncate pr-4">
             {message?.detail || config.description}
          </p>
        </div>
      </div>

      {/* 展开的内容区域 - 模拟终端输出 */}
      {isExpanded && (
        <div className="bg-zinc-950 dark:bg-black text-zinc-300 p-3 font-mono text-xs border-t border-border/50">
          <div className="space-y-1">
            <div className="flex items-center text-zinc-500">
              <span className="mr-2">$</span>
              <span>analyzing --step={step} --verbose</span>
            </div>
            
            {status === 'PENDING' && (
              <div className="text-zinc-600 italic">Waiting for previous steps...</div>
            )}

            {(status === 'RUNNING' || status === 'COMPLETED') && (
               <div className="animate-in fade-in slide-in-from-left-1 duration-300">
                 <div className="text-blue-400">→ Initiating {config.name} process...</div>
                 {message?.detail && (
                   <div className="text-zinc-100 pl-4 border-l-2 border-zinc-800 my-1 py-1 min-h-[1.5em]">
                     <Typewriter 
                       text={message.detail} 
                       speed={10} 
                       instant={status === 'COMPLETED'}
                     />
                   </div>
                 )}
                 {status === 'COMPLETED' && (
                   <div className="text-green-400">✓ Process completed successfully.</div>
                 )}
               </div>
            )}

            {/* 结构化结果预览 */}
            {status === 'COMPLETED' && !!message?.result && (
              <div className="mt-2 bg-zinc-900 rounded p-2 border border-zinc-800 overflow-x-auto">
                <div className="text-zinc-500 mb-1 text-[10px] uppercase tracking-wider">Output Preview</div>
                <pre className="text-green-300/90 text-[10px] leading-relaxed">
                  {JSON.stringify(message.result as object, null, 2)}
                </pre>
              </div>
            )}
            
            {status === 'FAILED' && message?.error && (
               <div className="text-red-400 font-bold">
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

export function AnalysisProgressPanel({
  messages,
  isConnected, // Keep prop but mark as used or ignore
  isCompleted,
  error,
  finalResult
}: AnalysisProgressPanelProps): React.ReactElement {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [expandedStep, setExpandedStep] = useState<number | null>(1);

  // 判断当前是否处于等待原型生成状态
  const waitingForPrototype = isWaitingForPrototype(isCompleted, finalResult);

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

    const runningStep = messages.find(m => m.status === 'RUNNING')?.step;
    if (runningStep && runningStep !== expandedStep) {
      setExpandedStep(runningStep);
    }
  }, [messages, isCompleted, expandedStep, waitingForPrototype, finalResult]); // Added dependencies

  // 自动滚动到底部
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

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
        ) : (
          /* 正常的分析步骤列表 */
          <div className="space-y-3 pb-4">
            {STEP_CONFIG.map((config, index) => {
              const step = index + 1;
              const { status, message } = getStepStatus(step);

              return (
                <StepLogItem
                  key={step}
                  step={step}
                  config={config}
                  status={status}
                  message={message}
                  isExpanded={expandedStep === step}
                  onToggle={() => setExpandedStep(expandedStep === step ? null : step)}
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
    </Card>
  );
}
