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

import React from 'react';
import { PageTransition } from '@/components/ui/page-transition';
import { useToast } from '@/hooks/use-toast';
import { TEMPLATE_CONFIGS } from '@/constants/template-configs';
import { getTemplateRequirement } from '@/constants/templates';

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
 * RequirementForm组件 - 重构版本
 * 使用Hook和组件提取，保持功能100%不变
 */
export function RequirementForm(): React.ReactElement {
  const { toast } = useToast();

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
    setRequirement,
    setSelectedModel,
    setLoading,
    setShowSuccess,
    setShowAnalysis,
    setCurrentPhase,
    setLoadedTemplate,
    clearTemplate,
  } = useFormState();

  // ==================== 生成流程Hook ====================
  const {
    messages,
    isConnected,
    isCompleted,
    analysisError,
    handleFormSubmit,
    handleStyleSelected,
    handleStyleCancel,
  } = useGenerationFlow({
    requirement,
    selectedModel,
    setLoading,
    setShowAnalysis,
    setCurrentPhase,
    setShowSuccess,
  });

  // ==================== 模板初始化Hook ====================
  useTemplateInitializer({
    onRequirementChange: setRequirement,
    onTemplateLoad: setLoadedTemplate,
    templates: TEMPLATE_CONFIGS,
  });

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
        {/* 成功动画覆盖层 */}
        <SuccessOverlay show={showSuccess} />

        {/* 分析面板：左右分屏布局 */}
        {showAnalysis ? (
          <AnalysisPanel
            requirement={requirement}
            selectedModel={selectedModel}
            selectedStyle={selectedStyle}
            currentPhase={currentPhase}
            messages={messages}
            isConnected={isConnected}
            isCompleted={isCompleted}
            analysisError={analysisError}
            onStyleSelected={handleStyleSelected}
            onStyleCancel={handleStyleCancel}
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
