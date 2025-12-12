/**
 * useTemplateInitializer Hook
 * 处理URL参数和sessionStorage的模板初始化逻辑
 *
 * 职责：
 * - 解析URL参数（templateId, template）
 * - 读取sessionStorage缓存
 * - 自动填充需求描述
 * - 显示Toast通知
 * - 自动聚焦输入框
 *
 * 优先级：URL参数(templateId) > URL参数(template) > sessionStorage
 *
 * @author Ingenio Team
 * @since V2.0
 */

import { useEffect } from 'react';
import { useToast } from '@/hooks/use-toast';
import { getTemplateRequirement } from '@/constants/templates';
import type { LoadedTemplate } from '@/types/requirement-form';

/**
 * useTemplateInitializer Hook返回值
 */
export interface UseTemplateInitializerProps {
  /** 需求描述变更回调 */
  onRequirementChange: (requirement: string) => void;
  /** 模板加载回调 */
  onTemplateLoad: (template: LoadedTemplate) => void;
  /** 已知的模板配置（用于显示名称） */
  templates?: Array<{ id: string; title: string; description: string }>;
}

/**
 * useTemplateInitializer Hook
 * 组件挂载时自动初始化模板
 *
 * @param props - Hook配置
 */
export function useTemplateInitializer({
  onRequirementChange,
  onTemplateLoad,
  templates = [],
}: UseTemplateInitializerProps): void {
  const { toast } = useToast();

  useEffect(() => {
    const searchParams = new URLSearchParams(window.location.search);

    // ==================== 优先级1: templateId参数（来自templates页面） ====================
    const templateIdFromPage = searchParams.get('templateId');
    const templateName = searchParams.get('templateName');

    if (templateIdFromPage) {
      const templateRequirement = getTemplateRequirement(templateIdFromPage, templateName || undefined);

      // 设置需求描述
      onRequirementChange(templateRequirement);

      // 设置模板加载状态
      onTemplateLoad({
        id: templateIdFromPage,
        name: templateName || templateIdFromPage,
      });

      // 显示Toast通知
      toast({
        title: '模板已加载',
        description: `已加载模板: ${templateName || templateIdFromPage}`,
      });

      // 聚焦到输入框
      focusRequirementInput();

      return; // 使用了URL参数，不再检查其他来源
    }

    // ==================== 优先级2: template参数（来自首页快捷入口） ====================
    const templateId = searchParams.get('template');

    if (templateId) {
      const templateRequirement = getTemplateRequirement(templateId);

      // 设置需求描述
      onRequirementChange(templateRequirement);

      // 查找模板配置
      const template = templates.find((t) => t.id === templateId);
      if (template) {
        onTemplateLoad({
          id: template.id,
          name: template.title,
        });

        // 显示Toast通知
        toast({
          title: '模板已应用',
          description: `已使用"${template.title}"模板`,
        });
      }

      // 聚焦到输入框
      focusRequirementInput();

      return; // 使用了URL参数，不再检查sessionStorage
    }

    // ==================== 优先级3: sessionStorage（来自首页输入框） ====================
    const savedRequirement = sessionStorage.getItem('requirement');
    if (savedRequirement && savedRequirement.trim()) {
      onRequirementChange(savedRequirement);

      // 读取后清除，避免重复使用
      sessionStorage.removeItem('requirement');
    }
  }, [onRequirementChange, onTemplateLoad, templates, toast]);
}

/**
 * 聚焦到需求输入框
 * 延迟100ms确保DOM已渲染
 */
function focusRequirementInput(): void {
  setTimeout(() => {
    const textarea = document.getElementById('requirement') as HTMLTextAreaElement | null;
    if (textarea) {
      textarea.focus();
      textarea.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }, 100);
}
