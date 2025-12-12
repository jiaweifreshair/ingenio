'use client';

import React from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { TemplateCard } from '@/components/templates/template-card';
import { Template } from '@/types/template';
import { Sparkles, SkipForward, TrendingUp } from 'lucide-react';

/**
 * TemplateSelectionPanel组件属性接口
 */
export interface TemplateSelectionPanelProps {
  /** 匹配的模板列表 */
  templates: Template[];
  /** 选择模板回调 */
  onSelectTemplate: (template: Template) => void;
  /** 跳过模板选择回调 */
  onSkip: () => void;
  /** 是否正在加载 */
  loading?: boolean;
}

/**
 * TemplateSelectionPanel - V2.0模板选择面板
 *
 * 功能：
 * - 展示与用户意图匹配的行业模板
 * - 支持模板选择（点击卡片）
 * - 支持跳过模板选择（直接进入风格选择）
 * - 显示模板推荐度（基于匹配度排序）
 * - 响应式布局（移动端1列，平板2列，桌面3列）
 * - 完整的深色模式支持
 *
 * 设计参考：IntentResultPanel.tsx（保持一致的样式和交互模式）
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-15
 */
export function TemplateSelectionPanel({
  templates,
  onSelectTemplate,
  onSkip,
  loading = false,
}: TemplateSelectionPanelProps): React.ReactElement {
  // 判断是否有推荐模板
  const hasTemplates = templates && templates.length > 0;

  return (
    <div className="flex flex-col h-full" data-testid="template-selection-panel">
      {/* 标题和总体状态 */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6 gap-4">
        <div className="flex items-center gap-3">
          <Sparkles className="h-6 w-6 text-purple-600 dark:text-purple-400" />
          <div>
            <h2 className="text-xl md:text-2xl font-bold text-gray-900 dark:text-gray-100">
              行业模板推荐
            </h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              {hasTemplates
                ? `为您精选了 ${templates.length} 个匹配的行业模板`
                : '暂无匹配模板，您可以跳过此步骤'}
            </p>
          </div>
        </div>

        {/* 跳过按钮（移动端和桌面端都显示） */}
        <Button
          variant="outline"
          onClick={onSkip}
          disabled={loading}
          className="w-full sm:w-auto"
          data-testid="skip-template-button"
        >
          <SkipForward className="h-4 w-4 mr-2" />
          跳过，自己设计
        </Button>
      </div>

      {/* 模板选择提示卡片 */}
      <Card className="p-4 mb-6 border-2 bg-blue-50 dark:bg-blue-900/10 border-blue-200 dark:border-blue-800">
        <div className="flex items-start gap-3">
          <TrendingUp className="h-5 w-5 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <h4 className="text-sm font-semibold text-blue-900 dark:text-blue-100 mb-1">
              为什么选择模板？
            </h4>
            <ul className="text-sm text-blue-800 dark:text-blue-200 leading-relaxed space-y-1">
              <li>• 快速启动：基于成熟方案，节省70%开发时间</li>
              <li>• 最佳实践：集成行业标准功能和交互模式</li>
              <li>• 可定制化：选择模板后仍可自由修改风格和功能</li>
            </ul>
          </div>
        </div>
      </Card>

      {/* 主要内容区域 - 模板网格 */}
      <div className="flex-1 overflow-y-auto">
        {hasTemplates ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {templates.map((template, index) => (
              <div key={template.id} className="relative">
                {/* 推荐徽章（仅显示前3个） */}
                {index < 3 && (
                  <div className="absolute -top-2 -right-2 z-10">
                    <Badge className="bg-gradient-to-r from-orange-500 to-red-500 text-white border-0 shadow-lg">
                      {index === 0 ? '🏆 最匹配' : index === 1 ? '🥈 推荐' : '🥉 备选'}
                    </Badge>
                  </div>
                )}

                {/* 复用TemplateCard组件 */}
                <TemplateCard
                  template={template}
                  onClick={onSelectTemplate}
                />
              </div>
            ))}
          </div>
        ) : (
          // 无模板时的占位提示
          <div className="flex flex-col items-center justify-center h-full py-12">
            <div className="text-6xl mb-4">🎨</div>
            <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-2">
              暂无匹配模板
            </h3>
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-6 text-center max-w-md">
              没有找到与您需求完全匹配的行业模板，但您可以跳过此步骤，直接选择设计风格从零开始创建。
            </p>
            <Button onClick={onSkip} disabled={loading}>
              继续，选择风格
            </Button>
          </div>
        )}
      </div>

      {/* 底部操作说明（仅在有模板时显示） */}
      {hasTemplates && (
        <div className="mt-6 pt-4 border-t-2 border-gray-200 dark:border-gray-700">
          <p className="text-xs text-center text-gray-500 dark:text-gray-400">
            💡 提示：选择模板后仍可在下一步自定义设计风格，或点击&ldquo;跳过&rdquo;直接进入风格选择
          </p>
        </div>
      )}
    </div>
  );
}
