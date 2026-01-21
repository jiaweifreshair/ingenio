/**
 * 交互式分析面板组件
 *
 * 展示AI深度思考的交互式分析流程:
 * - 左侧: 6个分析步骤列表,当前步骤高亮
 * - 右侧: AI思考过程实时展示 + 人工确认UI
 * - 底部: 对话输入框供用户提出修改建议
 */
'use client';

import React, { useState } from 'react';
import { CheckCircle2, Circle, Loader2, XCircle, Send, ChevronDown, ChevronUp } from 'lucide-react';
import { useInteractiveAnalysis } from '@/hooks/use-interactive-analysis';
import { cn } from '@/lib/utils';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Progress } from '@/components/ui/progress';

const STEP_CONFIG = [
  { name: '需求语义解析', description: '正在解构您的自然语言需求...' },
  { name: '实体关系建模', description: '识别核心数据实体与关联...' },
  { name: '功能意图识别', description: '分析所需的功能模块与业务逻辑...' },
  { name: '技术架构选型', description: '匹配最佳技术栈与设计模式...' },
  { name: '复杂度与风险评估', description: '计算开发成本与潜在风险...' },
  { name: 'Ultrathink 深度规划', description: '构建系统架构、数据流图与实施路径...' }
];

export interface InteractiveAnalysisPanelProps {
  requirement: string;
  onComplete?: () => void;
}

export function InteractiveAnalysisPanel({ requirement, onComplete }: InteractiveAnalysisPanelProps) {
  const [feedback, setFeedback] = useState('');
  const [expandedDetails, setExpandedDetails] = useState<Set<number>>(new Set());

  const {
    session,
    isLoading,
    error,
    currentStepMessages,
    startSession,
    advanceToNextStep,
    modifyStep
  } = useInteractiveAnalysis({
    onStepComplete: (step, result) => {
      console.log(`步骤 ${step} 完成:`, result);
    },
    onAllComplete: (session) => {
      console.log('所有步骤完成:', session);
      onComplete?.();
    },
    onError: (error) => {
      console.error('分析错误:', error);
    }
  });

  // 启动会话
  React.useEffect(() => {
    if (requirement && !session) {
      startSession(requirement);
    }
  }, [requirement, session, startSession]);

  const currentStep = session?.currentStep || 1;
  const isWaitingConfirmation = session?.status === 'WAITING_CONFIRMATION';

  const handleConfirm = () => {
    if (session) {
      advanceToNextStep(currentStep);
      setFeedback('');
    }
  };

  const handleModify = () => {
    if (session && feedback.trim()) {
      modifyStep(currentStep, feedback.trim());
      setFeedback('');
    }
  };

  return (
    <div className="flex h-screen bg-background">
      {/* 左侧步骤列表 */}
      <div className="w-64 border-r bg-card p-4">
        <div className="mb-6">
          <h2 className="text-xl font-bold">AI 深度思考中</h2>
          <p className="text-sm text-muted-foreground mt-1">
            {isWaitingConfirmation ? '等待您的确认' : '正在分析...'}
          </p>
        </div>

        <div className="space-y-3">
          {STEP_CONFIG.map((config, index) => {
            const step = index + 1;
            const isActive = step === currentStep;
            const isCompleted = session?.stepResults[step] !== undefined;

            return (
              <div
                key={step}
                className={cn(
                  "flex items-start gap-3 p-3 rounded-lg transition-all",
                  isActive && "bg-blue-50 dark:bg-blue-900/10 ring-1 ring-blue-200 dark:ring-blue-800",
                  isCompleted && "opacity-60"
                )}
              >
                <div className="flex-shrink-0 mt-0.5">
                  {isCompleted ? (
                    <CheckCircle2 className="h-5 w-5 text-green-600 dark:text-green-400" />
                  ) : isActive ? (
                    <Loader2 className="h-5 w-5 text-blue-600 dark:text-blue-400 animate-spin" />
                  ) : (
                    <Circle className="h-5 w-5 text-muted-foreground" />
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className={cn(
                    "font-medium text-sm",
                    isActive && "text-blue-700 dark:text-blue-300"
                  )}>
                    {config.name}
                  </h3>
                  <p className="text-xs text-muted-foreground truncate">
                    {config.description}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* 右侧AI思考过程 + 确认UI */}
      <div className="flex-1 flex flex-col">
        {/* AI思考过程展示区 */}
        <ScrollArea className="flex-1">
          <div className="max-w-4xl mx-auto">
            {/* 步骤标题区 */}
            {currentStep > 0 && (
              <div className="sticky top-0 z-10 bg-background/95 backdrop-blur border-b p-4 mb-4">
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-lg font-semibold">
                      步骤 {currentStep}: {STEP_CONFIG[currentStep - 1]?.name}
                    </h2>
                    <p className="text-sm text-muted-foreground mt-1">
                      {STEP_CONFIG[currentStep - 1]?.description}
                    </p>
                  </div>
                  <div className="text-right">
                    <div className="text-sm text-muted-foreground">进度</div>
                    <div className="text-lg font-semibold">{currentStep}/{STEP_CONFIG.length}</div>
                  </div>
                </div>
                <Progress value={(currentStep / STEP_CONFIG.length) * 100} className="mt-3" />
              </div>
            )}

            {/* 上下文传递指示器 */}
            {currentStep > 1 && session && (
              <div className="mx-6 mb-4 p-3 bg-blue-50 dark:bg-blue-900/10 border-l-4 border-blue-500 rounded">
                <p className="text-sm font-semibold text-blue-700 dark:text-blue-300 mb-2">
                  📋 已传递上下文
                </p>
                <ul className="text-xs text-blue-600 dark:text-blue-400 space-y-1">
                  {Array.from({ length: currentStep - 1 }, (_, i) => i + 1).map(step => (
                    <li key={step} className="flex items-center gap-2">
                      <CheckCircle2 className="h-3 w-3 flex-shrink-0" />
                      <span>
                        Step {step} 结果
                        {session.stepFeedback && session.stepFeedback[step] && ' + 用户反馈'}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            <div className="p-6 space-y-4">
              {/* 错误提示 */}
              {error && (
                <Card className="p-4 bg-red-50 dark:bg-red-900/10 border-red-200 dark:border-red-800">
                  <div className="flex items-start gap-3">
                    <XCircle className="h-5 w-5 text-red-500 flex-shrink-0 mt-0.5" />
                    <div>
                      <h3 className="font-semibold text-red-900 dark:text-red-100">错误</h3>
                      <p className="text-sm text-red-700 dark:text-red-300">{error}</p>
                    </div>
                  </div>
                </Card>
              )}

              {/* 当前步骤的消息列表 */}
              {currentStepMessages.map((msg, index) => {
                const isExpanded = expandedDetails.has(index);
                return (
                  <Card key={index} className="overflow-hidden">
                    <div className="p-4">
                      <div className="flex items-start gap-3">
                        {msg.status === 'RUNNING' ? (
                          <Loader2 className="h-5 w-5 text-blue-500 animate-spin flex-shrink-0 mt-0.5" />
                        ) : msg.status === 'COMPLETED' ? (
                          <CheckCircle2 className="h-5 w-5 text-green-500 flex-shrink-0 mt-0.5" />
                        ) : msg.status === 'FAILED' ? (
                          <XCircle className="h-5 w-5 text-red-500 flex-shrink-0 mt-0.5" />
                        ) : null}
                        <div className="flex-1 min-w-0">
                          <h3 className="font-medium">{msg.stepName}</h3>
                          <p className="text-sm text-muted-foreground mt-1">{msg.description}</p>
                        </div>
                      </div>

                      {/* AI思考过程 */}
                      {msg.detail && (
                        <div className="mt-3 ml-8">
                          <button
                            onClick={() => {
                              const newExpanded = new Set(expandedDetails);
                              if (isExpanded) {
                                newExpanded.delete(index);
                              } else {
                                newExpanded.add(index);
                              }
                              setExpandedDetails(newExpanded);
                            }}
                            className="flex items-center gap-2 text-sm font-medium text-blue-600 hover:text-blue-700"
                          >
                            {isExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                            AI思考过程
                          </button>
                          {isExpanded && (
                            <div className="mt-2 p-3 bg-muted rounded text-sm whitespace-pre-wrap">
                              {msg.detail}
                            </div>
                          )}
                        </div>
                      )}

                      {/* 结果数据 */}
                      {msg.result != null && (
                        <div className="mt-3 ml-8">
                          <div className="p-3 bg-green-50 dark:bg-green-900/10 border border-green-200 dark:border-green-800 rounded">
                            <div className="text-sm font-medium text-green-900 dark:text-green-100 mb-2">
                              ✓ 生成结果
                            </div>
                            <pre className="text-xs overflow-x-auto text-green-800 dark:text-green-200">
                              {typeof msg.result === 'string' ? msg.result : JSON.stringify(msg.result, null, 2)}
                            </pre>
                          </div>
                        </div>
                      )}
                    </div>
                  </Card>
                );
              })}

              {/* 等待确认提示 */}
              {isWaitingConfirmation && (
                <Card className="p-6 bg-blue-50 dark:bg-blue-900/10 border-blue-200 dark:border-blue-800">
                  <div className="text-center">
                    <h3 className="text-lg font-semibold text-blue-900 dark:text-blue-100 mb-2">
                      步骤 {currentStep} 已完成
                    </h3>
                    <p className="text-sm text-blue-700 dark:text-blue-300 mb-4">
                      请确认结果是否满意,或提出修改建议
                    </p>
                  </div>
                </Card>
              )}
            </div>
          </div>
        </ScrollArea>

        {/* 底部确认/修改UI */}
        {isWaitingConfirmation && (
          <div className="border-t bg-card p-6">
            <div className="max-w-4xl mx-auto space-y-4">
              {/* 修改建议输入框 */}
              <div>
                <label className="text-sm font-medium mb-2 block">
                  修改建议 (可选)
                </label>
                <Textarea
                  value={feedback}
                  onChange={(e) => setFeedback(e.target.value)}
                  placeholder="例如: 把按钮改成蓝色的..."
                  className="min-h-[100px]"
                />
              </div>

              {/* 操作按钮 */}
              <div className="flex gap-3">
                <Button
                  onClick={handleConfirm}
                  disabled={isLoading}
                  className="flex-1"
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                      处理中...
                    </>
                  ) : (
                    <>
                      <CheckCircle2 className="h-4 w-4 mr-2" />
                      确认,进入下一步
                    </>
                  )}
                </Button>

                {feedback.trim() && (
                  <Button
                    onClick={handleModify}
                    disabled={isLoading}
                    variant="outline"
                    className="flex-1"
                  >
                    {isLoading ? (
                      <>
                        <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                        处理中...
                      </>
                    ) : (
                      <>
                        <Send className="h-4 w-4 mr-2" />
                        提交修改建议
                      </>
                    )}
                  </Button>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
