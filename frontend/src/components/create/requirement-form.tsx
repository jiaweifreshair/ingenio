/**
 * RequirementForm组件 - 重构版本
 * 应用需求输入表单
 *
 * 重构说明：
 * - 原始文件：846行
 * - 重构后：~130行（减少85%）
 * - 提取3个Hook：useFormState, useTemplateInitializer, useGenerationFlow
 * - 提取7个子组件：FormInput, FormActions, AnalysisPanel, ModelSelectorCard,
 *                  TemplateLoadedBanner, TemplateGallery, SuccessOverlay
 *
 * 核心功能：
 * - 提供需求输入框（placeholder: "描述你想要的应用..."）
 * - "生成应用"按钮（初始disabled状态）
 * - 快速模板选项（data-template属性）
 * - 表单验证（至少10字符）
 * - 成功提交后导航到/wizard/:id
 *
 * UI增强：
 * - 页面加载时的淡入动画
 * - 提交成功后的庆祝动画
 * - 模板点击的反馈动画
 * - 按钮微交互（hover/active）
 *
 * E2E测试支持：
 * - textarea placeholder: "描述你想要的应用..."
 * - button: "生成应用"
 * - 模板卡片: data-template属性
 * - 空表单时按钮disabled
 * - 点击模板填充输入框
 * - 提交后导航到/wizard/:id
 *
 * @author Ingenio Team
 * @since V2.0
 */

'use client';

import React, { useState } from 'react';
import { PageTransition } from '@/components/ui/page-transition';
import { useToast } from '@/hooks/use-toast';
import { useAuth } from '@/hooks/use-auth';
import { TEMPLATE_CONFIGS } from '@/constants/template-configs';
import { getTemplateRequirement } from '@/constants/templates';
import { PrototypeConfirmation } from '@/components/prototype/prototype-confirmation';
import { selectStyleAndGeneratePrototype } from '@/lib/api/plan-routing';
import { LoginDialog } from '@/components/auth/login-dialog';
import type { AnalysisProgressMessage } from '@/hooks/use-analysis-sse';

// 导入自定义Hooks
import {
  useFormState,
  useTemplateInitializer,
  useGenerationFlow,
} from './hooks';

// 导入子组件
import {
  FormInput,
  FormActions,
  AnalysisPanel,
  ModelSelectorCard,
  TemplateLoadedBanner,
  TemplateGallery,
  SuccessOverlay,
} from './components';

/**
 * 从 Step5（交互设计师）结果中提取“推荐风格 code”
 *
 * 是什么：Step5 风格选型解析器。
 * 做什么：从后端返回的 selectedStyleId/styleVariants 中解析出 styleCode（例如 modern_minimal）。
 * 为什么：原型生成接口使用 styleCode，而 Step5 默认输出是 A-G + 变体列表。
 */
function resolveStep5RecommendedStyleCode(step5Result: unknown): string | null {
  if (!step5Result || typeof step5Result !== 'object') return null;
  const record = step5Result as Record<string, unknown>;

  const selectedStyleCode =
    typeof record.selectedStyleCode === 'string' ? record.selectedStyleCode.trim() : '';
  if (selectedStyleCode) return selectedStyleCode;

  const selectedStyleId =
    typeof record.selectedStyleId === 'string' ? record.selectedStyleId.trim() : '';

  const styleVariants = Array.isArray(record.styleVariants) ? record.styleVariants : [];
  const findVariantCode = (variant: unknown): string => {
    if (!variant || typeof variant !== 'object') return '';
    const v = variant as Record<string, unknown>;
    const id = typeof v.styleId === 'string' ? v.styleId.trim() : '';
    const code = typeof v.styleCode === 'string' ? v.styleCode.trim() : '';
    if (!code) return '';
    if (!selectedStyleId) return code;
    return id === selectedStyleId ? code : '';
  };

  // 1) 优先匹配 selectedStyleId 对应的 styleCode
  if (selectedStyleId) {
    for (const variant of styleVariants) {
      const code = findVariantCode(variant);
      if (code) return code;
    }
  }

  // 2) 回退：取第一个可用 styleCode
  for (const variant of styleVariants) {
    if (!variant || typeof variant !== 'object') continue;
    const v = variant as Record<string, unknown>;
    const code = typeof v.styleCode === 'string' ? v.styleCode.trim() : '';
    if (code) return code;
  }

  return null;
}

/**
 * RequirementForm组件 - 重构版本
 * 使用Hook和组件提取，保持功能100%不变
 */
export function RequirementForm(): React.ReactElement {
  const { toast } = useToast();
  const { isAuthenticated } = useAuth();
  const [showLoginDialog, setShowLoginDialog] = useState(false);

  // ==================== 状态管理Hook ====================
  const {
    requirement,
    selectedModel,
    selectedStyle,
    loading,
    showSuccess,
    showAnalysis,
    currentPhase,
    loadedTemplate,
    routingResult,
    setRequirement,
    setSelectedStyle,
    g3Logs,
    setG3Logs,
    setSelectedModel,
    setLoading,
    setShowSuccess,
    setShowAnalysis,
    setCurrentPhase,
    setLoadedTemplate,
    setRoutingResult,
    clearTemplate,
  } = useFormState();

  // ==================== 生成流程Hook ====================
  const {
    messages,
    isConnected,
    isCompleted,
    analysisError,
    handleFormSubmit: triggerGeneration,
    handleStyleCancel,
    handleConfirmDesign,
  } = useGenerationFlow({
    requirement,
    selectedModel,
    loadedTemplate,
    setLoading,
    setShowAnalysis,
    setCurrentPhase,
    setShowSuccess,
    routingResult,
    setRoutingResult,
    setG3Logs,
  });

  /**
   * 处理表单提交（包含登录检查）
   */
  const handleFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!isAuthenticated) {
      setShowLoginDialog(true);
      return;
    }
    // 新一轮分析/生成：清理上一次的风格选择，避免“沿用旧结论”
    setSelectedStyle(null);
    triggerGeneration(e);
  };

  // ==================== 模板初始化Hook ====================
  useTemplateInitializer({
    onRequirementChange: setRequirement,
    onTemplateLoad: setLoadedTemplate,
    templates: TEMPLATE_CONFIGS,
  });

  // ==================== 自动流转逻辑 ====================
  // V2.1 修改：移除自动流转，改为用户确认技术方案后手动流转
  // useEffect(() => { ... }) 

  /**
   * 处理技术方案确认
   * 用户确认方案后，触发原型生成
   */
  const handlePlanConfirm = async () => {
    if (routingResult) {
      try {
        setLoading(true);
        console.log('[RequirementForm] 用户确认方案，开始生成原型');
        // 优先使用 Step5/用户确认的风格；若缺失则回退到 modern_minimal
        const styleCode = selectedStyle || 'modern_minimal';
        const prototypeResult = await selectStyleAndGeneratePrototype(routingResult.appSpecId, styleCode);
        
        setRoutingResult(prototypeResult);
        setCurrentPhase('prototype-preview');
        setLoading(false);
      } catch (err) {
        console.error('[RequirementForm] 原型生成失败:', err);
        toast({
          title: '原型生成失败',
          description: '请重试',
          variant: 'destructive',
        });
        setLoading(false);
      }
    }
  };

  /**
   * 处理技术方案修改
   */
  const handlePlanModify = (newReq: string) => {
    // 简单实现：更新需求并重新提交（TODO: 实现更平滑的增量修改）
    setRequirement(newReq + " (Based on previous plan feedback)");
    handleFormSubmit({ preventDefault: () => {} } as React.FormEvent);
  };

  /**
   * 自动回填 Step5 推荐风格（仅在用户尚未选择时）
   *
   * 为什么：
   * - 避免 create 流程“始终使用 modern_minimal（安全默认值）”，导致交互设计师的选型结论被忽略；
   * - 用户仍可在 Step6 蓝图视图顶部调整并确认自己的风格选择。
   */
  React.useEffect(() => {
    if (selectedStyle) return;
    const step5 = [...messages].reverse().find((m: AnalysisProgressMessage) => m.step === 5 && m.status === 'COMPLETED' && m.result != null);
    if (!step5?.result) return;

    const recommended = resolveStep5RecommendedStyleCode(step5.result);
    if (!recommended) return;
    setSelectedStyle(recommended);
  }, [messages, selectedStyle, setSelectedStyle]);

  // ==================== 事件处理 ====================

  /**
   * 处理模板点击
   * 填充输入框并聚焦
   */
  function handleTemplateClick(template: {
    id: string;
    title: string;
    description: string;
  }): void {
    const fullRequirement = getTemplateRequirement(template.id, template.title);

    setRequirement(fullRequirement);

    // 显示Toast通知
    toast({
      title: '模板已应用',
      description: `已使用"${template.title}"模板`,
    });

    // 聚焦到输入框
    const textarea = document.getElementById('requirement') as HTMLTextAreaElement | null;
    if (textarea) {
      textarea.focus();
      textarea.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }

  /**
   * 处理清除模板
   * 显示Toast通知
   */
  function handleClearTemplate(): void {
    clearTemplate();
    toast({
      title: '模板已清除',
      description: '已重置为空白表单',
    });
  }

  // ==================== 渲染 ====================

  return (
    <PageTransition type="slide-up" duration={0.4}>
      <div className="space-y-10">
        {/* 登录对话框 */}
        <LoginDialog
          open={showLoginDialog}
          onOpenChange={setShowLoginDialog}
          onSuccess={() => triggerGeneration({ preventDefault: () => {} } as React.FormEvent)}
          title="登录以开始生成"
          description="您需要登录才能使用AI生成功能。生成结果将保存到您的账户中。"
        />

        {/* 成功动画覆盖层 */}
        <SuccessOverlay show={showSuccess} />

        {/* 原型预览确认面板 (Unified Lite Flow) */}
        {currentPhase === 'prototype-preview' && routingResult ? (
          <PrototypeConfirmation
            routingResult={routingResult}
            userRequirement={requirement}
            onConfirm={handleConfirmDesign}
            onBack={handleStyleCancel}
            loading={loading}
            error={analysisError}
            g3Logs={g3Logs}
          />
        ) : showAnalysis ? (
          /* 分析面板：左右分屏布局 */
          <AnalysisPanel
            requirement={requirement}
            selectedModel={selectedModel}
            currentPhase={currentPhase}
            messages={messages}
            isConnected={isConnected}
            isCompleted={isCompleted}
            isLoading={loading}
            analysisError={analysisError}
            onConfirmPlan={handlePlanConfirm}
            onModifyPlan={handlePlanModify}
            onStyleSelected={(styleCode) => setSelectedStyle(styleCode)}
          />
        ) : (
          <>
            {/* AI模型选择 - 顶部独立显示 */}
            <ModelSelectorCard
              value={selectedModel}
              onValueChange={setSelectedModel}
              disabled={loading}
            />

            {/* 模板加载提示 */}
            {loadedTemplate && (
              <TemplateLoadedBanner
                template={loadedTemplate}
                onClear={handleClearTemplate}
              />
            )}

            {/* 需求输入表单 */}
            <form onSubmit={handleFormSubmit} className="space-y-8">
              {/* 输入框 */}
              <FormInput
                value={requirement}
                onChange={setRequirement}
                disabled={loading}
                minLength={10}
                onSubmit={handleFormSubmit}
              />

              {/* 双按钮布局：快速Web预览 + 完整生成 */}
              <FormActions
                isValid={requirement.trim().length >= 10}
                isLoading={loading}
                requirement={requirement}
                onFullGeneration={handleFormSubmit}
              />
            </form>

            {/* 模板快捷入口 - 横向滚动 */}
            <TemplateGallery
              templates={TEMPLATE_CONFIGS}
              onTemplateClick={handleTemplateClick}
            />
          </>
        )}
      </div>
    </PageTransition>
  );
}
