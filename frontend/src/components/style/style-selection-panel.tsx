'use client';

import React, { useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { StyleCard } from '@/components/style/style-card';
import { DesignStyle, getAllStyles, getStyleDisplayInfo } from '@/types/design-style';
import { Palette, ArrowRight, Lightbulb } from 'lucide-react';
import type { StyleVariant } from '@/lib/api/plan-routing';

/**
 * StyleSelectionPanel组件属性接口
 */
export interface StyleSelectionPanelProps {
  /** 选择风格回调 */
  onSelectStyle: (style: DesignStyle) => void;
  /** 是否正在加载 */
  loading?: boolean;
  /** 当前选中的风格（可选） */
  selectedStyle?: DesignStyle | null;
  /** 是否显示确认按钮（默认true） */
  showConfirmButton?: boolean;
  /** 风格变体（可选，从API获取的动态信息） */
  styleVariants?: StyleVariant[];
}

/**
 * StyleSelectionPanel - V2.0设计风格选择面板
 *
 * 功能：
 * - 展示SuperDesign支持的7种设计风格（A-G）
 * - 支持风格选择（点击卡片）
 * - 显示当前选中的风格
 * - 提供确认按钮继续下一步
 * - 响应式布局（移动端1列，平板2列，桌面3列）
 * - 完整的深色模式支持
 *
 * 设计参考：TemplateSelectionPanel.tsx（保持一致的样式和交互模式）
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-15
 */
export function StyleSelectionPanel({
  onSelectStyle,
  loading = false,
  selectedStyle = null,
  showConfirmButton = true,
  styleVariants = [],
}: StyleSelectionPanelProps): React.ReactElement {
  // 本地状态：临时选中的风格（用户点击但未确认）
  const [tempSelectedStyle, setTempSelectedStyle] = useState<DesignStyle | null>(selectedStyle);

  // 获取所有7种风格
  const allStyles = getAllStyles();

  // 当前选中的风格（优先使用临时选中）
  const currentSelected = tempSelectedStyle || selectedStyle;

  // 处理风格点击
  const handleStyleClick = (style: DesignStyle) => {
    setTempSelectedStyle(style);
    // 如果不显示确认按钮，直接触发回调
    if (!showConfirmButton) {
      onSelectStyle(style);
    }
  };

  // 处理确认选择
  const handleConfirm = () => {
    if (currentSelected) {
      onSelectStyle(currentSelected);
    }
  };

  // 获取选中风格的显示信息
  const selectedStyleInfo = currentSelected ? getStyleDisplayInfo(currentSelected) : null;

  return (
    <div className="flex flex-col h-full" data-testid="style-selection-panel">
      {/* 标题和选中状态 */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6 gap-4">
        <div className="flex items-center gap-3">
          <Palette className="h-6 w-6 text-purple-600 dark:text-purple-400" />
          <div>
            <h2 className="text-xl md:text-2xl font-bold text-gray-900 dark:text-gray-100">
              选择设计风格
            </h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              {currentSelected
                ? `已选择：${selectedStyleInfo?.displayName} (${selectedStyleInfo?.identifier})`
                : '请从7种设计风格中选择一种'}
            </p>
          </div>
        </div>

        {/* 确认按钮（仅在有选择时显示） */}
        {showConfirmButton && currentSelected && (
          <Button
            onClick={handleConfirm}
            disabled={loading}
            className="w-full sm:w-auto bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 text-white"
            data-testid="confirm-style-button"
          >
            <ArrowRight className="h-4 w-4 mr-2" />
            {loading ? '处理中...' : '确认风格，继续'}
          </Button>
        )}
      </div>

      {/* 风格选择提示卡片 */}
      <Card className="p-4 mb-6 border-2 bg-blue-50 dark:bg-blue-900/10 border-blue-200 dark:border-blue-800">
        <div className="flex items-start gap-3">
          <Lightbulb className="h-5 w-5 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <h4 className="text-sm font-semibold text-blue-900 dark:text-blue-100 mb-1">
              如何选择设计风格？
            </h4>
            <ul className="text-sm text-blue-800 dark:text-blue-200 leading-relaxed space-y-1">
              <li>• <strong>根据行业选择</strong>：每种风格适合不同类型的应用场景</li>
              <li>• <strong>参考案例</strong>：查看知名产品的设计风格作为参考</li>
              <li>• <strong>可后续调整</strong>：选择后仍可在后续步骤中微调细节</li>
            </ul>
          </div>
        </div>
      </Card>

      {/* 主要内容区域 - 7风格网格 */}
      <div className="flex-1 overflow-y-auto">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {allStyles.map((style) => {
            // 查找对应的变体数据
            const variant = styleVariants.find(v => v.styleCode === style);
            return (
              <StyleCard
                key={style}
                style={style}
                onClick={handleStyleClick}
                loading={loading}
                selected={currentSelected === style}
                variant={variant}
              />
            );
          })}
        </div>
      </div>

      {/* 底部提示信息 */}
      {currentSelected && (
        <div className="mt-6 pt-4 border-t-2 border-gray-200 dark:border-gray-700">
          <div className="flex items-center justify-center gap-2">
            <Badge className="bg-gradient-to-r from-purple-600 to-blue-600 text-white border-0">
              {selectedStyleInfo?.identifier}
            </Badge>
            <p className="text-sm text-gray-600 dark:text-gray-300">
              <strong>{selectedStyleInfo?.displayName}</strong>
              {' - '}
              {selectedStyleInfo?.suitableFor.slice(0, 3).join('、')}等场景
            </p>
          </div>
          {showConfirmButton && (
            <p className="text-xs text-center text-gray-500 dark:text-gray-400 mt-2">
              💡 提示：点击&ldquo;确认风格，继续&rdquo;按钮进入下一步
            </p>
          )}
        </div>
      )}
    </div>
  );
}
