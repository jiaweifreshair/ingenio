/**
 * V2.0 创建向导页面
 * 基于意图识别+双重选择机制的全新创建流程
 *
 * 流程：需求输入 → 意图识别 → 模板选择(可选) → 风格选择 → 原型预览确认 → Execute执行
 *
 * V2.0核心升级：
 * - AI意图识别：自动识别克隆/设计/混合意图
 * - 双重选择机制：模板选择（可选）+ 风格选择（必选）
 * - 可交互原型：5-10秒生成，用户确认后再生成后端
 * - "选择题而非填空题"：降低60%认知负荷
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-15
 */
'use client';

import React, { useState, useCallback, useEffect, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Progress } from '@/components/ui/progress';
// Badge导入已移至PrototypeConfirmation组件
import { TopNav } from '@/components/layout/top-nav';
import {
  Loader2,
  ArrowLeft,
  Sparkles,
  Send,
  AlertCircle,
  FileText,
  Palette,
  Eye,
  Rocket,
  AlertTriangle,
} from 'lucide-react';

import { MotionWrapper } from '@/components/ui/motion-wrapper';

// V2.0组件导入
import { IntentResultPanel } from '@/components/intent/intent-result-panel';
import { TemplateSelectionPanel } from '@/components/intent/template-selection-panel';
import { StyleSelectionPanel } from '@/components/style/style-selection-panel';
import { PrototypeConfirmation } from '@/components/prototype/prototype-confirmation';
import { HistorySidebar } from '@/components/history/HistorySidebar';

// V2.0 API导入
import {
  routeRequirement,
  selectStyleAndGeneratePrototype,
  confirmDesign,
  executeCodeGeneration,
  type PlanRoutingResult,
  isDesignBranch,
} from '@/lib/api/plan-routing';
import { getAppSpec } from '@/lib/api/appspec';
import { SCENARIO_CONFIGS } from '@/lib/scenario-config';

// 类型导入
import type { IntentClassificationResult } from '@/types/intent';
import type { DesignStyle } from '@/types/design-style';
import type { Template } from '@/types/template';

// ==================== 步骤定义 ====================

/**
 * 向导步骤枚举
 */
enum WizardStep {
  /** 需求输入 */
  REQUIREMENT = 'requirement',
  /** 意图识别结果 */
  INTENT_RESULT = 'intent_result',
  /** 模板选择（可选） */
  TEMPLATE_SELECTION = 'template_selection',
  /** 风格选择 */
  STYLE_SELECTION = 'style_selection',
  /** 原型预览确认 */
  PROTOTYPE_CONFIRM = 'prototype_confirm',
  /** Execute执行 */
  EXECUTE = 'execute',
}

/**
 * 步骤元数据
 */
const STEP_META: Record<WizardStep, { title: string; icon: React.ReactNode; description: string }> = {
  [WizardStep.REQUIREMENT]: {
    title: '描述需求',
    icon: <FileText className="h-5 w-5" />,
    description: '用自然语言描述您想要的应用',
  },
  [WizardStep.INTENT_RESULT]: {
    title: '意图识别',
    icon: <Sparkles className="h-5 w-5" />,
    description: 'AI分析您的需求并识别意图',
  },
  [WizardStep.TEMPLATE_SELECTION]: {
    title: '模板选择',
    icon: <FileText className="h-5 w-5" />,
    description: '选择行业模板快速启动（可跳过）',
  },
  [WizardStep.STYLE_SELECTION]: {
    title: '风格选择',
    icon: <Palette className="h-5 w-5" />,
    description: '从7种设计风格中选择',
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
 * 步骤顺序（用于进度计算）
 */
const STEP_ORDER: WizardStep[] = [
  WizardStep.REQUIREMENT,
  WizardStep.INTENT_RESULT,
  WizardStep.TEMPLATE_SELECTION,
  WizardStep.STYLE_SELECTION,
  WizardStep.PROTOTYPE_CONFIRM,
  WizardStep.EXECUTE,
];

// ==================== 主组件 ====================

/**
 * V2.0创建向导页面（内部组件）
 */
function CreateV2PageInner(): React.ReactElement {
  const router = useRouter();
  const searchParams = useSearchParams();

  // ========== 状态管理 ==========
  const [currentStep, setCurrentStep] = useState<WizardStep>(WizardStep.REQUIREMENT);
  const [requirement, setRequirement] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // V2.0核心状态
  const [routingResult, setRoutingResult] = useState<PlanRoutingResult | null>(null);
  const [selectedTemplate, setSelectedTemplate] = useState<Template | null>(null);
  const [selectedStyle, setSelectedStyle] = useState<DesignStyle | null>(null);
  
  // 场景化提示参数
  const [complexityHint, setComplexityHint] = useState<string | undefined>(undefined);
  const [techStackHint, setTechStackHint] = useState<string | undefined>(undefined);

  // ========== 初始化效果 ==========
  useEffect(() => {
    const scenarioId = searchParams.get('scenario');
    if (scenarioId && SCENARIO_CONFIGS[scenarioId]) {
      const scenario = SCENARIO_CONFIGS[scenarioId];
      // 预填充需求描述
      setRequirement(scenario.prompt);
      // 设置技术提示参数
      setComplexityHint(scenario.complexityHint);
      setTechStackHint(scenario.techStackHint);
    }
  }, [searchParams]);

  // ========== 进度计算 ==========
  const currentStepIndex = STEP_ORDER.indexOf(currentStep);
  const progressPercent = Math.round(((currentStepIndex + 1) / STEP_ORDER.length) * 100);

  // ========== 意图识别结果转换 ==========
  const intentResult: IntentClassificationResult | null = routingResult
    ? {
        intent: routingResult.intent,
        confidence: routingResult.confidence,
        reasoning: routingResult.nextAction,
        referenceUrls: [],
        extractedKeywords: routingResult.matchedTemplateResults?.flatMap(t => t.matchedKeywords) || [],
        suggestedNextAction: routingResult.nextAction,
        warnings: routingResult.requiresUserConfirmation ? ['请确认设计方案后再继续'] : [],
      }
    : null;

  // 计算下一步按钮文本
  const getNextActionText = () => {
    if (!routingResult) return '确认意图，继续';
    if (routingResult.matchedTemplateResults && routingResult.matchedTemplateResults.length > 0) {
      return '进入模板选择';
    }
    if (isDesignBranch(routingResult)) {
      return '进入风格选择';
    }
    return '生成原型预览';
  };

  // ========== 事件处理 ==========

  /**
   * 提交需求，开始V2.0路由流程
   */
  const handleSubmitRequirement = useCallback(async () => {
    if (!requirement.trim() || requirement.trim().length < 10) {
      setError('请输入至少10个字符的需求描述');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      console.log('[CreateV2] 开始路由需求:', requirement);
      const result = await routeRequirement({ 
        userRequirement: requirement,
        complexityHint,
        techStackHint
      });
      console.log('[CreateV2] 路由结果:', result);

      setRoutingResult(result);
      setCurrentStep(WizardStep.INTENT_RESULT);
    } catch (err) {
      console.error('[CreateV2] 路由失败:', err);
      setError(err instanceof Error ? err.message : '需求分析失败，请重试');
    } finally {
      setLoading(false);
    }
  }, [requirement, complexityHint, techStackHint]);

  /**
   * 确认意图，进入下一步
   */
  const handleConfirmIntent = useCallback(() => {
    if (!routingResult) return;

    // 根据路由分支决定下一步
    if (routingResult.matchedTemplateResults && routingResult.matchedTemplateResults.length > 0) {
      // 有匹配模板，进入模板选择
      setCurrentStep(WizardStep.TEMPLATE_SELECTION);
    } else if (isDesignBranch(routingResult)) {
      // 设计分支，直接进入风格选择
      setCurrentStep(WizardStep.STYLE_SELECTION);
    } else {
      // 克隆分支，直接进入原型确认
      setCurrentStep(WizardStep.PROTOTYPE_CONFIRM);
    }
  }, [routingResult]);

  /**
   * 修改意图，返回需求输入
   */
  const handleModifyIntent = useCallback(() => {
    setCurrentStep(WizardStep.REQUIREMENT);
  }, []);

  /**
   * 选择模板
   */
  const handleSelectTemplate = useCallback((template: Template) => {
    console.log('[CreateV2] 选择模板:', template.name);
    setSelectedTemplate(template);

    // 进入风格选择
    setCurrentStep(WizardStep.STYLE_SELECTION);
  }, []);

  /**
   * 跳过模板选择
   */
  const handleSkipTemplate = useCallback(() => {
    console.log('[CreateV2] 跳过模板选择');
    setSelectedTemplate(null);
    setCurrentStep(WizardStep.STYLE_SELECTION);
  }, []);

  /**
   * 选择风格并生成原型
   */
  const handleSelectStyle = useCallback(async (style: DesignStyle) => {
    if (!routingResult?.appSpecId) {
      setError('AppSpec ID 丢失，请重新开始');
      return;
    }

    setLoading(true);
    setError(null);
    setSelectedStyle(style);

    try {
      console.log('[CreateV2] 选择风格:', style);
      const result = await selectStyleAndGeneratePrototype(routingResult.appSpecId, style);
      console.log('[CreateV2] 原型生成结果:', result);

      setRoutingResult(result);
      setCurrentStep(WizardStep.PROTOTYPE_CONFIRM);
    } catch (err) {
      console.error('[CreateV2] 风格选择失败:', err);
      setError(err instanceof Error ? err.message : '原型生成失败，请重试');
    } finally {
      setLoading(false);
    }
  }, [routingResult?.appSpecId]);

  /**
   * 确认设计，进入Execute阶段
   * Phase 2.3.3更新：集成executeCodeGeneration完整流程
   */
  const handleConfirmDesign = useCallback(async () => {
    if (!routingResult?.appSpecId) {
      setError('AppSpec ID 丢失，请重新开始');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      // Step 1: 确认设计
      console.log('[CreateV2] Step 1: 确认设计:', routingResult.appSpecId);
      const confirmResult = await confirmDesign(routingResult.appSpecId);
      console.log('[CreateV2] 确认结果:', confirmResult);

      if (!confirmResult.success || !confirmResult.canProceedToExecute) {
        setError(confirmResult.message || '确认失败，请重试');
        return;
      }

      // Step 2: 执行代码生成 (Phase 2.2.4新增)
      console.log('[CreateV2] Step 2: 执行代码生成...');
      const codeResult = await executeCodeGeneration(routingResult.appSpecId);
      console.log('[CreateV2] 代码生成完成:', codeResult);

      if (codeResult.success) {
        // Step 3: 跳转到结果展示页面
        router.push(`/wizard/${routingResult.appSpecId}`);
      } else {
        setError(codeResult.error || '代码生成失败，请重试');
      }
    } catch (err) {
      console.error('[CreateV2] 流程失败:', err);
      setError(err instanceof Error ? err.message : '操作失败，请重试');
    } finally {
      setLoading(false);
    }
  }, [routingResult?.appSpecId, router]);

  /**
   * 历史记录 - 恢复版本
   */
  const handleRestoreHistory = useCallback(async (appSpecId: string) => {
    try {
      setLoading(true);
      // Fetch the AppSpec details
      const response = await getAppSpec(appSpecId);
      if (response.success && response.data) {
         // Restore state based on the fetched AppSpec
         // This is a simplified restore. In a real app, we'd map all fields back to state.
         const appSpec = response.data;
         setRequirement(appSpec.userRequirement);
         
         // Determine step based on appSpec status or metadata
         // For now, just go to Requirement step with restored text
         setCurrentStep(WizardStep.REQUIREMENT);
         
         // If we had full state persistence, we could jump to INTENT_RESULT or PROTOTYPE_CONFIRM
         // But for now, let's just restore the input so user can re-run or modify.
      }
    } catch (err) {
      console.error('Failed to restore history:', err);
      setError('恢复历史版本失败');
    } finally {
      setLoading(false);
    }
  }, []);

  /**
   * 历史记录 - 预览版本
   */
  const handlePreviewHistory = useCallback((appSpecId: string) => {
    router.push(`/preview/${appSpecId}`);
  }, [router]);

  /**
   * 返回上一步
   */
  const handleBack = useCallback(() => {
    const prevStepIndex = currentStepIndex - 1;
    if (prevStepIndex >= 0) {
      setCurrentStep(STEP_ORDER[prevStepIndex]);
      setError(null);
    }
  }, [currentStepIndex]);

  // ========== 渲染步骤内容 ==========

  const renderStepContent = () => {
    switch (currentStep) {
      // Step 1: 需求输入
      case WizardStep.REQUIREMENT:
        return (
          <div className="space-y-8 py-4">
            <div className="space-y-4 text-center max-w-2xl mx-auto">
              <div className="inline-flex items-center justify-center p-3 bg-gradient-to-br from-purple-100 to-blue-100 rounded-2xl mb-2 dark:from-purple-900/30 dark:to-blue-900/30">
                <Sparkles className="w-8 h-8 text-purple-600 dark:text-purple-400" />
              </div>
              <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight">
                <span className="bg-gradient-to-r from-purple-600 via-pink-600 to-blue-600 bg-clip-text text-transparent animate-gradient-x">
                  描述您想要的应用
                </span>
              </h1>
              <p className="text-lg text-muted-foreground leading-relaxed">
                AI将自动分析您的需求，识别意图并推荐最佳技术方案。<br className="hidden sm:block" />
                无论是<span className="text-purple-600 font-medium">克隆网站</span>还是<span className="text-blue-600 font-medium">从零设计</span>，都只需一句话。
              </p>
            </div>

            <Card className="p-1 bg-gradient-to-br from-purple-100 via-white to-blue-50 dark:from-gray-800 dark:via-gray-900 dark:to-gray-800 border-0 shadow-xl rounded-xl">
              <div className="bg-white dark:bg-gray-950 rounded-lg p-6 space-y-4">
                <Textarea
                  placeholder="例如：我想做一个类似Airbnb的民宿预订网站，需要房源搜索、在线预订、用户评价等功能..."
                  value={requirement}
                  onChange={(e) => setRequirement(e.target.value)}
                  className="min-h-[200px] text-lg p-4 resize-none border-2 border-gray-100 dark:border-gray-800 focus:border-purple-500/50 focus:ring-4 focus:ring-purple-100 dark:focus:ring-purple-900/20 transition-all rounded-lg bg-transparent"
                  disabled={loading}
                  data-testid="requirement-input"
                />

                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pt-2">
                  <div className="flex items-center gap-2 text-sm">
                    <div className={`w-2 h-2 rounded-full ${requirement.length >= 10 ? 'bg-green-500' : 'bg-gray-300'}`} />
                    <span className={requirement.length >= 10 ? 'text-green-600' : 'text-muted-foreground'}>
                      {requirement.length} / 10 字符
                    </span>
                  </div>
                  <Button
                    onClick={handleSubmitRequirement}
                    disabled={loading || requirement.trim().length < 10}
                    size="lg"
                    className="bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 shadow-lg hover:shadow-purple-500/25 transition-all rounded-full px-8"
                    data-testid="submit-requirement"
                  >
                    {loading ? (
                      <>
                        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
                        正在深度分析...
                      </>
                    ) : (
                      <>
                        <Send className="mr-2 h-5 w-5" />
                        开始分析
                      </>
                    )}
                  </Button>
                </div>
              </div>
            </Card>

            {/* 快速示例 */}
            <div className="space-y-4 text-center">
              <p className="text-sm font-medium text-muted-foreground uppercase tracking-wider">或尝试以下示例</p>
              <div className="flex flex-wrap justify-center gap-3">
                {[
                  { text: '参考淘宝做一个电商平台', icon: '🛍️' },
                  { text: '设计一个在线教育系统', icon: '🎓' },
                  { text: '仿照知乎做一个问答社区', icon: '🤔' },
                  { text: '开发一个企业报销工具', icon: '🏢' },
                ].map((example) => (
                  <button
                    key={example.text}
                    onClick={() => setRequirement(example.text)}
                    disabled={loading}
                    className="group flex items-center gap-2 px-4 py-2.5 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-full hover:border-purple-300 dark:hover:border-purple-700 hover:bg-purple-50 dark:hover:bg-purple-900/10 hover:shadow-md transition-all duration-200 text-sm text-gray-600 dark:text-gray-300"
                  >
                    <span className="group-hover:scale-110 transition-transform">{example.icon}</span>
                    <span>{example.text}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>
        );

      // Step 2: 意图识别结果
      case WizardStep.INTENT_RESULT:
        return intentResult ? (
          <IntentResultPanel
            result={intentResult}
            onConfirm={handleConfirmIntent}
            onModify={handleModifyIntent}
            loading={loading}
            nextActionText={getNextActionText()}
          />
        ) : (
          <div className="flex flex-col items-center justify-center py-16 px-4 animate-in fade-in zoom-in-95 duration-500">
            <div className="relative mb-6">
              <div className="absolute inset-0 bg-amber-100 dark:bg-amber-900/30 rounded-full blur-xl opacity-50 animate-pulse" />
              <div className="relative w-24 h-24 bg-white dark:bg-gray-800 rounded-full shadow-xl flex items-center justify-center border border-amber-100 dark:border-amber-800">
                <AlertTriangle className="w-10 h-10 text-amber-500" />
              </div>
            </div>
            
            <div className="text-center max-w-md space-y-3 mb-8">
              <h3 className="text-2xl font-bold text-gray-900 dark:text-gray-100">未能识别有效意图</h3>
              <p className="text-muted-foreground leading-relaxed">
                AI 暂时无法从您的描述中分析出明确的开发意图。建议您补充更多细节，例如功能模块、目标用户或参考产品。
              </p>
            </div>

            <div className="flex flex-col sm:flex-row gap-4 w-full sm:w-auto">
              <Button 
                variant="outline" 
                size="lg" 
                onClick={handleModifyIntent}
                className="min-w-[140px] border-2 hover:bg-gray-50 dark:hover:bg-gray-800"
              >
                <ArrowLeft className="mr-2 h-4 w-4" />
                返回修改
              </Button>
              <Button 
                size="lg" 
                onClick={handleSubmitRequirement}
                className="min-w-[140px] bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 text-white shadow-lg hover:shadow-purple-500/25"
              >
                <Sparkles className="mr-2 h-4 w-4" />
                重试分析
              </Button>
            </div>
          </div>
        );

      // Step 3: 模板选择
      case WizardStep.TEMPLATE_SELECTION:
        const templates: Template[] = routingResult?.matchedTemplateResults?.map(r => r.template) || [];
        return (
          <TemplateSelectionPanel
            templates={templates}
            onSelectTemplate={handleSelectTemplate}
            onSkip={handleSkipTemplate}
            loading={loading}
          />
        );

      // Step 4: 风格选择
      case WizardStep.STYLE_SELECTION:
        return (
          <StyleSelectionPanel
            onSelectStyle={handleSelectStyle}
            loading={loading}
            selectedStyle={selectedStyle}
            showConfirmButton={true}
            styleVariants={routingResult?.styleVariants}
          />
        );

      // Step 5: 原型预览确认（使用PrototypeConfirmation组件 - 深度融合版）
      case WizardStep.PROTOTYPE_CONFIRM:
        return routingResult ? (
          <PrototypeConfirmation
            routingResult={routingResult}
            userRequirement={requirement}
            selectedTemplate={selectedTemplate}
            onConfirm={handleConfirmDesign}
            onBack={handleBack}
            loading={loading}
            error={error}
          />
        ) : null;

      // Step 6: Execute执行（跳转到wizard页面）
      case WizardStep.EXECUTE:
        return (
          <div className="flex flex-col items-center justify-center py-12">
            <Loader2 className="h-12 w-12 animate-spin text-purple-600 mb-4" />
            <h2 className="text-xl font-bold mb-2">正在跳转到生成页面...</h2>
            <p className="text-muted-foreground">请稍候</p>
          </div>
        );

      default:
        return null;
    }
  };

  // ========== 主渲染 ==========

  return (
    <div className="flex min-h-screen flex-col bg-gradient-to-br from-indigo-50 via-white to-cyan-50 dark:from-gray-900 dark:via-gray-900 dark:to-slate-800">
      {/* 顶部导航 */}
      <TopNav />
      
      {/* 历史记录侧边栏 */}
      <HistorySidebar 
        currentAppSpecId={routingResult?.appSpecId} 
        onRestore={handleRestoreHistory}
        onPreview={handlePreviewHistory}
      />

      {/* 进度条 */}
      <div className="border-b bg-white/80 dark:bg-gray-900/80 backdrop-blur-md sticky top-16 z-40 shadow-sm transition-all duration-300">
        <div className="container px-4 py-4">
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-2">
              <div className={`p-1.5 rounded-lg ${currentStepIndex === STEP_ORDER.length - 1 ? 'bg-green-100 text-green-600' : 'bg-purple-100 text-purple-600'}`}>
                {STEP_META[currentStep].icon}
              </div>
              <div>
                <span className="font-bold text-lg tracking-tight">{STEP_META[currentStep].title}</span>
                <p className="text-xs text-muted-foreground hidden sm:block">{STEP_META[currentStep].description}</p>
              </div>
            </div>
            <span className="text-sm font-mono text-muted-foreground bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded">
              {progressPercent}%
            </span>
          </div>
          <Progress value={progressPercent} className="h-2 bg-gray-100 dark:bg-gray-800" gradient={true} />

          {/* 步骤指示器 */}
          <div className="flex justify-between mt-4 px-1">
            {STEP_ORDER.map((step, index) => {
              const isActive = step === currentStep;
              const isCompleted = index < currentStepIndex;
              
              return (
                <div
                  key={step}
                  className="flex flex-col items-center gap-1.5 group cursor-default"
                  title={STEP_META[step].title}
                >
                  <div
                    className={`w-2.5 h-2.5 rounded-full transition-all duration-300 ${
                      isActive
                        ? 'bg-purple-600 scale-125 ring-4 ring-purple-100 dark:ring-purple-900'
                        : isCompleted
                        ? 'bg-green-500'
                        : 'bg-gray-300 dark:bg-gray-600'
                    }`}
                  />
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* 主内容区 */}
      <main className="container flex-1 px-4 py-8">
        <div className="max-w-4xl mx-auto">
          {/* 错误提示 */}
          {error && (
            <MotionWrapper motionKey="error">
              <Alert variant="destructive" className="mb-6 border-destructive/20 shadow-lg animate-in slide-in-from-top-2">
                <AlertCircle className="h-4 w-4" />
                <AlertDescription className="font-medium">{error}</AlertDescription>
              </Alert>
            </MotionWrapper>
          )}

          {/* 步骤内容 */}
          <MotionWrapper motionKey={currentStep} className="min-h-[400px]">
            {renderStepContent()}
          </MotionWrapper>
        </div>
      </main>

      {/* 底部导航（非首步可返回）
          注意：返回按钮不应在loading时禁用，允许用户在超时时返回上一步
          用户体验优化：即使正在加载，也允许用户取消当前操作返回 */}
      {currentStep !== WizardStep.REQUIREMENT && currentStep !== WizardStep.EXECUTE && (
        <div className="border-t bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm">
          <div className="container px-4 py-4">
            <Button
              variant="ghost"
              onClick={handleBack}
              className="text-muted-foreground"
            >
              <ArrowLeft className="mr-2 h-4 w-4" />
              {loading ? '取消并返回' : '返回上一步'}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * V2.0创建向导页面（带Suspense包装）
 *
 * 修复Next.js 15的useSearchParams必须包裹在Suspense中的要求
 */
export default function CreateV2Page(): React.ReactElement {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 dark:from-gray-900 dark:to-gray-800">
          <div className="text-center">
            <Loader2 className="mx-auto h-8 w-8 animate-spin text-primary" />
            <p className="mt-4 text-sm text-muted-foreground">加载中...</p>
          </div>
        </div>
      }
    >
      <CreateV2PageInner />
    </Suspense>
  );
}