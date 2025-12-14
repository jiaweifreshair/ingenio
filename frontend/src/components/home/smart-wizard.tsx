'use client';

import React, { useState, useCallback, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Progress } from '@/components/ui/progress';
import {
  Loader2,
  ArrowLeft,
  Eye,
  Rocket,
  AlertCircle,
  FileText,
} from 'lucide-react';

import { MotionWrapper } from '@/components/ui/motion-wrapper';
import { useAnalysisSse } from '@/hooks/use-analysis-sse';
import { useToast } from '@/hooks/use-toast';

// V2.0组件导入
import { AnalysisProgressPanel } from '@/components/analysis/AnalysisProgressPanel';
import { PrototypeConfirmation } from '@/components/prototype/prototype-confirmation';

// V2.0 API导入
import {
  routeRequirement,
  confirmDesign,
  executeCodeGeneration,
  type PlanRoutingResult,
} from '@/lib/api/plan-routing';

import { type IndustryType, type AppComplexityMode, type AICapabilityType } from "@/types/smart-builder";

// ==================== 步骤定义 ====================

/**
 * 向导步骤枚举
 */
export enum WizardStep {
  /** 需求输入 (在HeroBanner中完成，但这里作为初始状态) */
  REQUIREMENT = 'requirement',
  /** 深度分析中 */
  ANALYZING = 'analyzing',
  /** 原型预览确认 */
  PROTOTYPE_CONFIRM = 'prototype_confirm',
  /** 生成执行中 */
  EXECUTE = 'execute',
}

/**
 * 步骤元数据
 */
const STEP_META: Record<WizardStep, { title: string; icon: React.ReactNode; description: string }> = {
  [WizardStep.REQUIREMENT]: {
    title: '需求确认',
    icon: <FileText className="h-5 w-5" />,
    description: '确认您的应用需求',
  },
  [WizardStep.ANALYZING]: {
    title: '深度分析',
    icon: <Loader2 className="h-5 w-5 animate-spin" />,
    description: 'AI正在分析您的需求...',
  },
  [WizardStep.PROTOTYPE_CONFIRM]: {
    title: '确认设计',
    icon: <Eye className="h-5 w-5" />,
    description: '预览原型并确认设计方案',
  },
  [WizardStep.EXECUTE]: {
    title: '生成应用',
    icon: <Rocket className="h-5 w-5" />,
    description: '全栈代码生成中...',
  },
};

/**
 * 步骤顺序
 */
const STEP_ORDER: WizardStep[] = [
  WizardStep.ANALYZING, // 跳过Requirement显示，直接进入分析
  WizardStep.PROTOTYPE_CONFIRM,
  WizardStep.EXECUTE,
];

interface SmartWizardProps {
  initialRequirement: string;
  onBack: () => void; // 返回首页重置状态
  initialContext?: {
    industry?: IndustryType | null;
    mode?: AppComplexityMode | null;
    capabilities?: AICapabilityType[];
  };
}

/**
 * SmartWizard - 嵌入式智能创建向导
 */
export function SmartWizard({ initialRequirement, onBack, initialContext }: SmartWizardProps): React.ReactElement {
  const router = useRouter();
  const { toast } = useToast();

  // ========== 状态管理 ==========
  const [currentStep, setCurrentStep] = useState<WizardStep>(WizardStep.ANALYZING);
  const [requirement] = useState(initialRequirement);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // V2核心状态
  const [routingResult, setRoutingResult] = useState<PlanRoutingResult | null>(null);

  // API超时计时器
  const apiTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const API_TIMEOUT_MS = 180000;
  
  // 状态标记
  const hasStartedRef = useRef(false);
  const hasTriggeredTransitionRef = useRef(false);

  // ========== SSE分析Hook ==========
  const {
    connect: startAnalysis,
    messages: analysisMessages,
    isConnected: isAnalysisConnected,
    isCompleted: isAnalysisCompleted,
    error: analysisError,
  } = useAnalysisSse({
    requirement: requirement,
    autoConnect: false,
  });

  // ========== 核心流程逻辑 ==========

  /**
   * 启动分析流程
   */
  const startProcess = useCallback(async () => {
    if (hasStartedRef.current) return;
    hasStartedRef.current = true;

    setLoading(true);
    setError(null);
    setCurrentStep(WizardStep.ANALYZING);

    // 1. 启动SSE视觉分析
    startAnalysis();

    // 2. 并行调用V2路由API
    try {
      console.log('[SmartWizard] 开始路由需求:', requirement);
      
      // Derive hints from initialContext
      let complexityHint: string | undefined;
      let techStackHint: string | undefined;
      
      if (initialContext?.mode) {
        if (initialContext.mode === 'ENTERPRISE') {
          complexityHint = 'COMPLEX';
          techStackHint = 'React + Spring Boot';
        } else if (initialContext.mode === 'WEB') {
          complexityHint = 'MEDIUM';
          techStackHint = 'React+Supabase';
        } else if (initialContext.mode === 'NATIVE') {
          complexityHint = 'COMPLEX';
          techStackHint = 'Kuikly + SpringBoot';
        }
      }

      const result = await routeRequirement({ 
        userRequirement: requirement,
        complexityHint,
        techStackHint
      });
      console.log('[SmartWizard] 路由结果:', result);
      setRoutingResult(result);
    } catch (err) {
      console.error('[SmartWizard] 路由失败:', err);
      toast({
        title: '分析失败',
        description: err instanceof Error ? err.message : '无法连接到服务器',
        variant: 'destructive',
      });
      setError(err instanceof Error ? err.message : '需求分析失败，请重试');
      setLoading(false);
      // 可以在这里提供重试按钮，或者自动重试
    }
  }, [requirement, startAnalysis, toast]);

  // 初始加载时自动启动
  useEffect(() => {
    if (requirement && !hasStartedRef.current) {
      startProcess();
    }
  }, [requirement, startProcess]);


  // 计算当前分析进度
  const analysisProgress = analysisMessages.length > 0
    ? analysisMessages[analysisMessages.length - 1]?.progress || 0
    : 0;

  // 是否应该触发跳转
  const shouldTransition = isAnalysisCompleted || analysisProgress >= 100;

  // 状态流转控制
  useEffect(() => {
    if (
      currentStep === WizardStep.ANALYZING &&
      routingResult &&
      shouldTransition &&
      !hasTriggeredTransitionRef.current
    ) {
      hasTriggeredTransitionRef.current = true;

      const startPrototypePhase = () => {
        console.log('[SmartWizard] 切换到原型确认步骤, appSpecId:', routingResult.appSpecId);
        
        // 更新 routingResult，设置默认风格
        setRoutingResult(prev => prev ? ({
            ...prev,
            selectedStyleId: 'modern_minimal',
            prototypeGenerated: false
        }) : null);
        
        setCurrentStep(WizardStep.PROTOTYPE_CONFIRM);
        setLoading(false);
      };

      // 稍微延迟，让用户感知到分析完成
      const timer = setTimeout(startPrototypePhase, 800);
      return () => clearTimeout(timer);
    }
  }, [currentStep, routingResult, shouldTransition, analysisProgress]);

  // 兜底超时跳转 (如果API返回了但SSE没推完)
  useEffect(() => {
    if (
      currentStep === WizardStep.ANALYZING &&
      routingResult &&
      !shouldTransition &&
      !hasTriggeredTransitionRef.current
    ) {
      const forceTimer = setTimeout(async () => {
        if (hasTriggeredTransitionRef.current) return;
        console.log('[SmartWizard] 兜底：SSE超时，强制切换步骤');
        hasTriggeredTransitionRef.current = true;
        
        setRoutingResult(prev => prev ? ({
            ...prev,
            selectedStyleId: 'modern_minimal',
            prototypeGenerated: false
        }) : null);
        
        setCurrentStep(WizardStep.PROTOTYPE_CONFIRM);
        setLoading(false);
      }, 5000);
      return () => clearTimeout(forceTimer);
    }
  }, [currentStep, routingResult, shouldTransition]);

  // API整体超时处理
  useEffect(() => {
    if (currentStep === WizardStep.ANALYZING && !routingResult) {
      apiTimeoutRef.current = setTimeout(() => {
        if (!routingResult && currentStep === WizardStep.ANALYZING) {
          console.error('[SmartWizard] API超时');
          toast({
            title: '请求超时',
            description: 'AI处理时间过长，请稍后重试',
            variant: 'destructive',
          });
          setError('请求超时，请重试');
          setLoading(false);
        }
      }, API_TIMEOUT_MS);
    }
    return () => {
      if (apiTimeoutRef.current) clearTimeout(apiTimeoutRef.current);
    };
  }, [currentStep, routingResult, toast]);

  // 清除超时计时器
  useEffect(() => {
    if (routingResult && apiTimeoutRef.current) {
      clearTimeout(apiTimeoutRef.current);
      apiTimeoutRef.current = null;
    }
  }, [routingResult]);

  /**
   * 确认设计，执行生成
   */
  const handleConfirmDesign = useCallback(async () => {
    if (!routingResult?.appSpecId) {
      setError('AppSpec ID 丢失，请重新开始');
      return;
    }

    setLoading(true);
    setError(null);
    setCurrentStep(WizardStep.EXECUTE);

    try {
      // Step 1: 确认设计
      console.log('[SmartWizard] 确认设计:', routingResult.appSpecId);
      const confirmResult = await confirmDesign(routingResult.appSpecId);

      if (!confirmResult.success || !confirmResult.canProceedToExecute) {
        throw new Error(confirmResult.message || '确认失败');
      }

      // Step 2: 执行代码生成
      console.log('[SmartWizard] 执行代码生成...');
      const codeResult = await executeCodeGeneration(routingResult.appSpecId);

      if (codeResult.success) {
        toast({
          title: '生成成功',
          description: '正在跳转到应用详情页...',
          duration: 2000,
        });
        router.push(`/wizard/${routingResult.appSpecId}`);
      } else {
        throw new Error(codeResult.error || '代码生成失败');
      }
    } catch (err) {
      console.error('[SmartWizard] 生成失败:', err);
      setError(err instanceof Error ? err.message : '操作失败，请重试');
      setCurrentStep(WizardStep.PROTOTYPE_CONFIRM);
      setLoading(false);
    }
  }, [routingResult, router, toast]);

  // 计算显示进度
  const currentStepIndex = STEP_ORDER.indexOf(currentStep);
  const progressPercent = Math.round(((currentStepIndex + 1) / STEP_ORDER.length) * 100);

  // ========== 渲染逻辑 ==========

  const renderStepContent = () => {
    switch (currentStep) {
      case WizardStep.ANALYZING:
        return (
          <div className="h-[600px] py-4 animate-in fade-in duration-500">
            <AnalysisProgressPanel
              messages={analysisMessages}
              isConnected={isAnalysisConnected}
              isCompleted={isAnalysisCompleted}
              error={analysisError}
              finalResult={routingResult}
            />
          </div>
        );

      case WizardStep.PROTOTYPE_CONFIRM:
        return routingResult ? (
          <PrototypeConfirmation
            routingResult={routingResult}
            userRequirement={requirement}
            onConfirm={handleConfirmDesign}
            onBack={onBack}
            loading={loading}
            error={error}
          />
        ) : null;

      case WizardStep.EXECUTE:
        return (
          <div className="flex flex-col items-center justify-center py-24 text-center">
            <div className="relative">
              <div className="absolute inset-0 bg-purple-500/20 blur-xl rounded-full" />
              <Rocket className="h-16 w-16 text-purple-600 animate-bounce relative z-10" />
            </div>
            <h2 className="text-2xl font-bold mt-8 mb-4 bg-clip-text text-transparent bg-gradient-to-r from-purple-600 to-blue-600">
              正在构建您的应用
            </h2>
            <p className="text-muted-foreground max-w-md mx-auto mb-8">
              AI 正在生成全栈代码、配置数据库并部署服务，这可能需要几十秒...
            </p>
            <Loader2 className="h-8 w-8 animate-spin text-purple-400" />
          </div>
        );

      default:
        return null;
    }
  };

  return (
    <div className="w-full max-w-7xl mx-auto animate-in fade-in slide-in-from-bottom-4 duration-500">
      
      {/* 顶部进度条 */}
      <div className="mb-8">
        <div className="flex items-center justify-between mb-2 px-1">
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-lg bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400">
              {STEP_META[currentStep].icon}
            </div>
            <span className="font-bold text-lg">{STEP_META[currentStep].title}</span>
          </div>
          <span className="text-sm font-mono text-muted-foreground">
            {progressPercent}%
          </span>
        </div>
        <Progress value={progressPercent} className="h-2" />
      </div>

      {/* 错误提示 */}
      {error && (
        <MotionWrapper motionKey="error">
          <Alert variant="destructive" className="mb-6">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription className="font-medium">{error}</AlertDescription>
          </Alert>
        </MotionWrapper>
      )}

      {/* 主内容 */}
      <Card className="min-h-[600px] border-0 shadow-2xl bg-white/80 dark:bg-gray-900/80 backdrop-blur-xl rounded-2xl overflow-hidden">
        <div className="p-6 md:p-8">
            {renderStepContent()}
        </div>
      </Card>
      
      {/* 底部返回 (仅在分析阶段或出错时显示) */}
      {(currentStep === WizardStep.ANALYZING || error) && (
        <div className="mt-6 text-center">
            <Button variant="ghost" onClick={onBack} className="text-muted-foreground hover:text-foreground">
                <ArrowLeft className="mr-2 h-4 w-4" />
                取消并返回首页
            </Button>
        </div>
      )}
    </div>
  );
}
