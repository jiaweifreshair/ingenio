'use client';

import React, { useState, useCallback, useEffect, useRef, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
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
import { useInteractiveAnalysisSse } from '@/hooks/use-interactive-analysis-sse';
import { useToast } from '@/hooks/use-toast';

// V2.0组件导入
import { AnalysisProgressPanel } from '@/components/analysis/AnalysisProgressPanel';
import { PrototypeConfirmation } from '@/components/prototype/prototype-confirmation';
import { InteractionPanel, type ChatHistoryItem } from '@/components/prototype/interaction-panel';

// G3 Engine Import
import { G3Console } from '@/components/g3/g3-console';
import type { G3Task } from '@/lib/g3/types';
import { getG3Artifacts, getG3JobStatus } from '@/lib/api/g3';

// V2.0 API导入
import {
  routeRequirement,
  confirmDesign,
  // executeCodeGeneration, // Replaced/Hidden by G3 Console flow for now
  updateAppSpecRequirement,
  type PlanRoutingResult,
} from '@/lib/api/plan-routing';

import {
  type IndustryType,
  type AppComplexityMode,
  type AICapabilityType,
  INDUSTRIES,
  AI_CAPABILITIES,
} from "@/types/smart-builder";

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
  /** 生成执行中 (G3 Engine) */
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
    title: '企业级服务端代码生成',
    icon: <Rocket className="h-5 w-5" />,
    description: '虚拟团队正在构建您的应用...',
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
  const [requirement, setRequirement] = useState(initialRequirement);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // V2核心状态
  const [routingResult, setRoutingResult] = useState<PlanRoutingResult | null>(null);
  const requirementRevisionRef = useRef(0);
  const [chatHistory, setChatHistory] = useState<ChatHistoryItem[]>([]);
  const [activeHistoryId, setActiveHistoryId] = useState<string | null>(null);
  const historySeededRef = useRef(false);

  /**
   * 合并用户“增量修改”到需求文本中
   *
   * 设计说明：
   * - 修改并不一定是“重写整段需求”，大多数场景是增量补充/约束追加
   * - 将增量修改按时间追加到末尾，保证上下文对后续阶段（原型/代码生成）可追溯、可透传
   */
  const mergeRequirementDelta = useCallback((base: string, delta: string) => {
    const trimmedDelta = delta.trim();
    if (!trimmedDelta) return base;
    const ts = new Date().toLocaleString();
    return `${base.trim()}\n\n---\n【需求变更 - ${ts}】\n${trimmedDelta}\n`;
  }, []);

  /**
   * 构建聊天历史条目ID
   *
   * 用途：
   * - 保证跨步骤的历史记录可唯一追踪
   */
  const buildWizardHistoryId = useCallback((prefix: 'requirement' | 'iteration') => (
    `history-${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  ), []);

  /**
   * 追加聊天历史记录
   *
   * 用途：
   * - 记录用户的需求或迭代修改
   * - 支持跨步骤透传与展示
   */
  const appendChatHistory = useCallback((content: string, kind: 'requirement' | 'iteration') => {
    const trimmed = content.trim();
    if (!trimmed) return null;
    const historyId = buildWizardHistoryId(kind);
    setChatHistory(prev => ([
      ...prev,
      {
        id: historyId,
        content: trimmed,
        timestamp: Date.now(),
        kind,
      },
    ]));
    return historyId;
  }, [buildWizardHistoryId]);

  /**
   * 截断长文本，避免日志区过度膨胀
   */
  const truncateText = useCallback((value: string, maxLength = 120) => {
    if (value.length <= maxLength) return value;
    return `${value.slice(0, maxLength)}...`;
  }, []);

  // API超时计时器
  const apiTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const API_TIMEOUT_MS = 300000;
  
  // 状态标记
  const hasStartedRef = useRef(false);
  const hasTriggeredTransitionRef = useRef(false);

  // ========== 交互式分析Hook ==========
  const {
    state,
    startSession,
    confirmStep,
    modifyStep,
  } = useInteractiveAnalysisSse();

  const analysisMessages = state.messages;
  const isProcessing = state.status === 'RUNNING' || state.status === 'STARTING';
  const analysisError = state.error;
  const analysisStep = state.currentStep;

  /**
   * 初始化聊天历史（仅首次）
   */
  useEffect(() => {
    if (historySeededRef.current) return;
    const trimmedRequirement = requirement.trim();
    if (!trimmedRequirement) return;
    setChatHistory(prev => {
      if (prev.length > 0) return prev;
      return [{
        id: buildWizardHistoryId('requirement'),
        content: trimmedRequirement,
        timestamp: Date.now(),
        kind: 'requirement',
      }];
    });
    historySeededRef.current = true;
  }, [requirement, buildWizardHistoryId]);

  /**
   * 从 initialContext 派生路由提示
   */
  const deriveRoutingHints = useCallback((): { complexityHint?: string; techStackHint?: string } => {
    let complexityHint: string | undefined;
    let techStackHint: string | undefined;

    if (initialContext?.mode) {
      if (initialContext.mode === 'ENTERPRISE') {
        complexityHint = 'COMPLEX';
        techStackHint = 'React + Spring Boot';
      } else if (initialContext.mode === 'WEB') {
        complexityHint = 'MEDIUM';
        techStackHint = 'React + Supabase';
      } else if (initialContext.mode === 'NATIVE') {
        complexityHint = 'COMPLEX';
        techStackHint = 'Kuikly + Spring Boot';
      }
    }
    return { complexityHint, techStackHint };
  }, [initialContext?.mode]);

  /**
   * 路由提示信息。
   *
   * 是什么：用于路由接口的复杂度与技术栈提示。
   * 做什么：在多个流程中复用同一套提示，避免重复计算。
   * 为什么：保证分析与执行判断使用一致的输入条件。
   */
  const routingHints = useMemo(() => deriveRoutingHints(), [deriveRoutingHints]);

  /**
   * 是否走Supabase前端直连模式。
   *
   * 是什么：判断当前方案是否无需服务端代码生成。
   * 做什么：根据技术栈提示决定是否跳过G3服务端流程。
   * 为什么：Supabase场景下前端直连即可，无需进入G3引擎。
   */
  const isSupabaseDirect = useMemo(() => {
    const techStack = routingHints.techStackHint;
    if (!techStack) return false;
    return techStack.toLowerCase().includes('supabase');
  }, [routingHints.techStackHint]);

  /**
   * 是否进入G3服务端生成流程。
   *
   * 是什么：执行阶段是否需要调用G3引擎的开关。
   * 做什么：根据Supabase直连模式决定是否显示G3控制台。
   * 为什么：避免在无需服务端时触发无意义的后端生成。
   */
  const shouldUseG3Engine = !isSupabaseDirect;

  // ========== 核心流程逻辑 ==========

  /**
   * 步骤修改包装函数
   */
  const handleStepModify = useCallback((step: number) => {
    // TODO: 实现步骤修改的用户输入收集
    // 目前暂时使用空字符串作为feedback
    modifyStep(step, '');
  }, [modifyStep]);

  /**
   * 启动分析流程
   */
  const startProcess = useCallback(async () => {
    if (hasStartedRef.current) {
      console.log('[SmartWizard] startProcess skipped: already started');
      return;
    }
    hasStartedRef.current = true;

    setLoading(true);
    setError(null);
    setCurrentStep(WizardStep.ANALYZING);

    // 1. 启动交互式分析会话
    await startSession(requirement);

    // 2. 并行调用V2路由API
    try {
      console.log('[SmartWizard] 开始路由需求:', requirement);

      const result = await routeRequirement({
        userRequirement: requirement,
        complexityHint: routingHints.complexityHint,
        techStackHint: routingHints.techStackHint
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
    }
  }, [requirement, startSession, toast, routingHints]);

  // 初始加载时自动启动
  useEffect(() => {
    if (requirement && !hasStartedRef.current) {
      startProcess();
    }
  }, [requirement, startProcess]);

  // 仅在组件卸载时重置（避免 requirement 变化触发 cleanup，导致 startProcess 被误二次触发）
  useEffect(() => {
    return () => {
      hasStartedRef.current = false;
    };
  }, []);


  // 计算当前分析进度
  const analysisProgress = analysisMessages.length > 0
    ? analysisMessages[analysisMessages.length - 1]?.progress || 0
    : 0;

  // 是否应该触发跳转
  const isAnalysisCompleted = state.status === 'COMPLETED';
  const shouldTransition = isAnalysisCompleted || analysisProgress >= 100;

  /**
   * 需求描述摘要。
   *
   * 是什么：用于透传参数展示的简短需求文本。
   * 做什么：控制展示长度，避免面板内容过长。
   * 为什么：在Supabase直连模式下保持信息清晰易读。
   */
  const requirementPreview = useMemo(() => {
    const trimmed = requirement.trim();
    if (!trimmed) return '未填写';
    if (trimmed.length <= 120) return trimmed;
    return `${trimmed.slice(0, 120)}...`;
  }, [requirement]);

  /**
   * 应用场景标签。
   *
   * 是什么：用户在首页选择的行业场景标签。
   * 做什么：映射为可读的中文描述用于展示。
   * 为什么：让Supabase直连方案的透传参数更清晰。
   */
  const selectedIndustryLabel = useMemo(() => {
    if (!initialContext?.industry) return '未选择';
    const industry = INDUSTRIES.find(item => item.id === initialContext.industry);
    return industry ? industry.label : initialContext.industry;
  }, [initialContext?.industry]);

  /**
   * AI能力标签列表。
   *
   * 是什么：用户选择的AI能力集合。
   * 做什么：映射为中文标签并拼接展示。
   * 为什么：让前端直连模式明确需要透传的能力开关。
   */
  const selectedCapabilitiesLabel = useMemo(() => {
    const capabilities = initialContext?.capabilities || [];
    if (capabilities.length === 0) return '未选择';
    const labels = capabilities.map((capability) => {
      const match = AI_CAPABILITIES.find(item => item.id === capability);
      return match ? match.label : capability;
    });
    return labels.join('、');
  }, [initialContext?.capabilities]);

  /**
   * 将分析消息转为日志文本，供 Chat 面板展示
   */
  const analysisLogs = useMemo(() => {
    return analysisMessages.map((msg) => {
      const stepLabel = msg.stepName || `步骤 ${msg.step}`;
      const statusLabel =
        msg.status === 'RUNNING'
          ? '进行中'
          : msg.status === 'COMPLETED'
            ? '已完成'
            : msg.status === 'FAILED'
              ? '失败'
              : '等待中';
      const detail = msg.detail || msg.description || '';
      let resultPreview = '';
      if (msg.result) {
        try {
          const raw = typeof msg.result === 'string' ? msg.result : JSON.stringify(msg.result);
          resultPreview = `结果: ${truncateText(raw, 100)}`;
        } catch {
          resultPreview = '结果: [无法序列化]';
        }
      }
      const parts = [stepLabel, statusLabel, detail ? truncateText(detail) : '', resultPreview].filter(Boolean);
      return `[${parts[0]}] ${parts.slice(1).join(' | ')}`;
    });
  }, [analysisMessages, truncateText]);

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
        
        // 更新 routingResult：设置默认风格，但不要覆盖 CLONE 分支已生成的原型状态
        setRoutingResult(prev => {
          if (!prev) return null;
          const hasPrototype = !!prev.prototypeUrl || !!prev.prototypeGenerated;
          return {
            ...prev,
            selectedStyleId: prev.selectedStyleId || 'modern_minimal',
            prototypeGenerated: hasPrototype,
          };
        });
        
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
        
        setRoutingResult(prev => {
          if (!prev) return null;
          const hasPrototype = !!prev.prototypeUrl || !!prev.prototypeGenerated;
          return {
            ...prev,
            selectedStyleId: prev.selectedStyleId || 'modern_minimal',
            prototypeGenerated: hasPrototype,
          };
        });
        
        setCurrentStep(WizardStep.PROTOTYPE_CONFIRM);
        setLoading(false);
      }, 30000); // 增加到30秒，给AI分析足够时间
      return () => clearTimeout(forceTimer);
    }
  }, [currentStep, routingResult, shouldTransition]);

  // API整体超时处理
  useEffect(() => {
    if (currentStep === WizardStep.ANALYZING && !routingResult && !error) {
      apiTimeoutRef.current = setTimeout(() => {
        if (!routingResult && currentStep === WizardStep.ANALYZING && !error) {
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
  }, [currentStep, routingResult, error, toast]);

  // 清除超时计时器
  useEffect(() => {
    if (routingResult && apiTimeoutRef.current) {
      clearTimeout(apiTimeoutRef.current);
      apiTimeoutRef.current = null;
    }
  }, [routingResult]);

  /**
   * 处理技术方案确认（从PlanDisplay点击"Confirm & Generate Prototype"按钮）
   * 跳转到原型预览步骤
   */
  const handlePlanConfirm = useCallback(() => {
    if (!routingResult?.appSpecId) {
      setError('AppSpec ID 丢失，请重新开始');
      return;
    }

    console.log('[SmartWizard] 用户确认技术方案，跳转到原型预览');
    hasTriggeredTransitionRef.current = true;

    // 更新 routingResult：设置默认风格，但不要错误覆盖 CLONE 分支的 prototypeGenerated 状态
    // - CLONE 分支可能已由后端直接生成 prototypeUrl，此时强制置 false 会导致“确认设计”按钮被禁用
    setRoutingResult(prev => {
      if (!prev) return null;
      const hasPrototype = !!prev.prototypeUrl || !!prev.prototypeGenerated;
      return {
        ...prev,
        selectedStyleId: prev.selectedStyleId || 'modern_minimal',
        prototypeGenerated: hasPrototype,
      };
    });

    setCurrentStep(WizardStep.PROTOTYPE_CONFIRM);
    setLoading(false);
  }, [routingResult]);

  /**
   * 处理技术方案修改
   * TODO: 实现增量修改逻辑
   */
  const handlePlanModify = useCallback((newReq: string) => {
    const historyId = appendChatHistory(newReq, 'iteration');
    if (historyId) {
      setActiveHistoryId(historyId);
    }

    const nextRequirement = mergeRequirementDelta(requirement, newReq);
    requirementRevisionRef.current += 1;
    console.log('[SmartWizard] 用户请求修改方案，revision=', requirementRevisionRef.current);

    // 防止"上一次完成态"触发自动跳转
    hasTriggeredTransitionRef.current = false;

    setRequirement(nextRequirement);
    setError(null);
    setLoading(true);
    setCurrentStep(WizardStep.ANALYZING);

    // 1) 立即用新需求重启分析
    startSession(nextRequirement);

    // 2) 更新路由（复用 appSpecId，确保上下文透传到后续阶段）
    (async () => {
      try {
        const result = await routeRequirement({
          userRequirement: nextRequirement,
          appSpecId: routingResult?.appSpecId,
          complexityHint: routingHints.complexityHint,
          techStackHint: routingHints.techStackHint,
        });
        setRoutingResult(result);
      } catch (e) {
        const msg = e instanceof Error ? e.message : '方案修改失败，请重试';
        toast({
          title: '方案修改失败',
          description: msg,
          variant: 'destructive',
        });
        setError(msg);
      } finally {
        setLoading(false);
        setActiveHistoryId(null);
      }
    })();
  }, [appendChatHistory, mergeRequirementDelta, requirement, routingResult?.appSpecId, routingHints, startSession, toast]);

  /**
   * 确认设计，执行生成
   * 修改：现在直接跳转到 EXECUTE 步骤，由 G3Console 接管
   */
  const handleConfirmDesign = useCallback(async () => {
    if (!routingResult?.appSpecId) {
      setError('AppSpec ID 丢失，请重新开始');
      return;
    }

    // 仅切换步骤，不直接调用后端 API
    // 在真实集成中，我们可以将 API 调用封装在 G3Orchestrator 内部
    setLoading(false);
    setError(null);
    setCurrentStep(WizardStep.EXECUTE);
    
    // 我们可以在这里做一个轻量级的 confirmDesign 调用来锁定状态，但把耗时的生成留给 G3 模拟
    // 为了流畅体验，这里直接通过
    try {
        await confirmDesign(routingResult.appSpecId);
    } catch (e) {
        console.warn("Design confirm warning:", e);
    }

  }, [routingResult]);

  /**
   * G3 引擎完成回调
   */
  const handleG3Complete = useCallback(async (task: G3Task) => {
    if (!routingResult?.appSpecId) return;

    // 兜底门禁：任何失败状态都不允许跳转预览
    if (task.status === 'FAILED') {
      toast({
        title: '生成失败',
        description: '请先在 G3 控制台查看失败原因并修复后再预览',
        variant: 'destructive',
        duration: 4000,
      });
      return;
    }

    // 编译失败门禁：即使任务“完成”，只要产物标记 hasErrors=true，就不跳转预览页
    // 说明：后端可能会以“完成 + 产物携带编译错误”的形式结束任务（便于后续修复），此处需要显式拦截。
    try {
      const jobId = task.id;

      const [statusResp, artifactsResp] = await Promise.all([
        getG3JobStatus(jobId),
        getG3Artifacts(jobId),
      ]);

      const backendStatus = statusResp.success ? statusResp.data?.status : null;
      if (backendStatus === 'FAILED') {
        toast({
          title: '生成失败',
          description: statusResp.data?.lastError || '请先修复失败原因后再预览',
          variant: 'destructive',
          duration: 4000,
        });
        return;
      }

      const hasCompileErrors =
        artifactsResp.success &&
        Array.isArray(artifactsResp.data) &&
        artifactsResp.data.some(a => a.hasErrors);

      if (hasCompileErrors) {
        toast({
          title: '编译失败',
          description: '产物存在编译错误，请先修复/重新生成后再进入应用预览',
          variant: 'destructive',
          duration: 5000,
        });
        return;
      }
    } catch (e) {
      // 若结果接口异常，为避免误跳转，这里也直接拦截（防止空白/异常预览页）
      toast({
        title: '结果校验失败',
        description: e instanceof Error ? e.message : '无法确认编译状态，请稍后重试',
        variant: 'destructive',
        duration: 4000,
      });
      return;
    }

    toast({
      title: '生成成功',
      description: '正在跳转到应用预览页...',
      duration: 2000,
    });
    router.push(`/preview/${routingResult.appSpecId}`);
  }, [routingResult, router, toast]);

  /**
   * Supabase直连模式完成后的跳转处理。
   *
   * 是什么：前端直连流程的收尾动作。
   * 做什么：提示用户并跳转到预览页。
   * 为什么：避免进入G3服务端流程，同时保持统一的交付体验。
   */
  const handleSupabaseComplete = useCallback(() => {
    if (!routingResult?.appSpecId) return;
    toast({
      title: '准备完成',
      description: '已进入前端直连交付流程，正在跳转预览页...',
      duration: 2000,
    });
    router.push(`/preview/${routingResult.appSpecId}`);
  }, [routingResult, router, toast]);

  // 计算显示进度
  const currentStepIndex = STEP_ORDER.indexOf(currentStep);
  const progressPercent = Math.round(((currentStepIndex + 1) / STEP_ORDER.length) * 100);

  /**
   * 当前步骤展示信息。
   *
   * 是什么：用于顶部进度条的标题与图标信息。
   * 做什么：在Supabase直连模式下替换执行阶段文案。
   * 为什么：避免显示“服务端代码生成”导致认知混淆。
   */
  const currentStepMeta =
    currentStep === WizardStep.EXECUTE && !shouldUseG3Engine
      ? {
          ...STEP_META[WizardStep.EXECUTE],
          title: '前端直连 Supabase',
          description: '使用 Supabase 作为后端能力，无需服务端生成',
        }
      : STEP_META[currentStep];

  // ========== 渲染逻辑 ==========

  const renderStepContent = () => {
    switch (currentStep) {
      case WizardStep.ANALYZING:
        return (
          <div className="h-[600px] py-4 animate-in fade-in duration-500">
            <div className="grid h-full gap-6 lg:grid-cols-[1.4fr_1fr]">
              <div className="min-h-0">
                <AnalysisProgressPanel
                  messages={analysisMessages}
                  isConnected={isProcessing}
                  isCompleted={analysisStep === 6}
                  error={analysisError}
                  finalResult={routingResult}
                  onConfirmPlan={handlePlanConfirm}
                  onModifyPlan={handlePlanModify}
                  onConfirmStep={confirmStep}
                  onModifyStep={handleStepModify}
                />
              </div>
              <div className="min-h-0">
                <InteractionPanel
                  historyItems={chatHistory}
                  logs={analysisLogs}
                  onSendMessage={handlePlanModify}
                  isGenerating={loading || isProcessing}
                  activeHistoryId={activeHistoryId}
                />
              </div>
            </div>
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
            chatHistory={chatHistory}
            onChatHistoryChange={setChatHistory}
            activeHistoryId={activeHistoryId}
            onActiveHistoryIdChange={setActiveHistoryId}
            onChatModify={async (delta) => {
              if (!routingResult?.appSpecId) return;
              const nextRequirement = mergeRequirementDelta(requirement, delta);
              setRequirement(nextRequirement);
              try {
                await updateAppSpecRequirement(routingResult.appSpecId, nextRequirement);
              } catch (e) {
                console.warn('[SmartWizard] 原型阶段需求同步失败:', e);
              }
            }}
          />
        ) : null;

      case WizardStep.EXECUTE:
        if (!shouldUseG3Engine) {
          return (
            <div className="h-[calc(100vh-240px)] min-h-[760px] -m-6 md:-m-8">
              <div className="h-full w-full flex items-center justify-center bg-gradient-to-br from-slate-950 to-slate-900 p-6 md:p-8">
                <Card className="w-full max-w-3xl p-6 md:p-8 space-y-6 bg-white/95 dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800">
                  <div className="flex items-center gap-3">
                    <Badge variant="secondary" className="text-xs">
                      React + Supabase
                    </Badge>
                    <span className="text-sm text-muted-foreground">前端直连模式</span>
                  </div>

                  <div className="space-y-2">
                    <h3 className="text-lg font-semibold text-foreground">无需服务端代码生成</h3>
                    <p className="text-sm text-muted-foreground">
                      根据首页方案拆解评估，本方案采用 Supabase 提供后端能力，前端直接调用即可，不需要进入G3服务端生成流程。
                    </p>
                  </div>

                  <div className="rounded-lg border bg-muted/40 p-4 text-sm space-y-2">
                    <div className="font-medium text-foreground">透传参数</div>
                    <div className="text-muted-foreground">需求描述：{requirementPreview}</div>
                    <div className="text-muted-foreground">应用场景：{selectedIndustryLabel}</div>
                    <div className="text-muted-foreground">AI能力：{selectedCapabilitiesLabel}</div>
                  </div>

                  <div className="flex flex-wrap gap-3">
                    <Button
                      onClick={handleSupabaseComplete}
                      className="bg-gradient-to-r from-blue-600 to-purple-600 text-white"
                    >
                      进入预览
                    </Button>
                    <Button variant="outline" onClick={onBack}>
                      返回首页
                    </Button>
                  </div>
                </Card>
              </div>
            </div>
          );
        }

        return (
          <div className="h-[calc(100vh-240px)] min-h-[760px] -m-6 md:-m-8">
            {/* G3 Console embedded with full height */}
            <G3Console 
                initialRequirement={requirement}
                appSpecId={routingResult?.appSpecId}
                autoStart={true}
                onComplete={handleG3Complete}
                className="h-full border-0 rounded-none bg-black"
            />
          </div>
        );

      default:
        return null;
    }
  };

  return (
    <div
      className="w-full max-w-7xl mx-auto animate-in fade-in slide-in-from-bottom-4 duration-500"
      data-testid="smart-wizard"
    >
      
      {/* 顶部进度条 */}
      <div className="mb-8">
        <div className="flex items-center justify-between mb-2 px-1">
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-lg bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400">
              {currentStepMeta.icon}
            </div>
            <span className="font-bold text-lg">{currentStepMeta.title}</span>
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
      <Card className="min-h-[600px] border-0 shadow-2xl bg-white/80 dark:bg-gray-900/80 backdrop-blur-xl rounded-2xl overflow-hidden relative">
        <div className={currentStep === WizardStep.EXECUTE ? "" : "p-6 md:p-8"}>
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
