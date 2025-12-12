/**
 * ModelSelectorCard组件
 * AI模型选择卡片（包含说明文字）
 *
 * 功能：
 * - 显示AI模型选择器
 * - 显示选择说明文字
 * - 卡片式布局
 * - 响应式设计
 *
 * @author Ingenio Team
 * @since V2.0
 */

'use client';

import React from 'react';
import { ModelSelector } from '@/components/ai/model-selector';
import type { UniaixModel } from '@/lib/api/uniaix';

/**
 * ModelSelectorCard组件Props
 */
export interface ModelSelectorCardProps {
  /** 选中的AI模型 */
  value: UniaixModel;
  /** 模型变更回调 */
  onValueChange: (model: UniaixModel) => void;
  /** 是否禁用 */
  disabled?: boolean;
  /** 自定义类名 */
  className?: string;
}

/**
 * ModelSelectorCard组件
 * 带说明的AI模型选择卡片
 */
export function ModelSelectorCard({
  value,
  onValueChange,
  disabled = false,
  className,
}: ModelSelectorCardProps): React.ReactElement {
  return (
    <div
      className={`flex items-center justify-between rounded-2xl border border-border/50 bg-card/30 p-4 backdrop-blur-xl ${className || ''}`}
    >
      <div className="space-y-1">
        <p className="text-sm font-medium">AI模型</p>
        <p className="text-xs text-muted-foreground">
          选择最适合的AI模型来理解你的需求
        </p>
      </div>
      <ModelSelector
        value={value}
        onValueChange={onValueChange}
        disabled={disabled}
      />
    </div>
  );
}
