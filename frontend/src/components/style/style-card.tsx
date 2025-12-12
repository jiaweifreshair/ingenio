'use client';

import React from 'react';
import { Card, CardContent, CardFooter } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { DesignStyle, getStyleDisplayInfo } from '@/types/design-style';
import { Sparkles, CheckCircle2 } from 'lucide-react';
import type { StyleVariant } from '@/lib/api/plan-routing';

/**
 * StyleCard组件属性接口
 */
export interface StyleCardProps {
  /** 风格类型 */
  style: DesignStyle;
  /** 点击选择回调 */
  onClick: (style: DesignStyle) => void;
  /** 是否正在加载 */
  loading?: boolean;
  /** 是否已选中 */
  selected?: boolean;
  /** 风格变体数据（可选） */
  variant?: StyleVariant;
}

/**
 * StyleCard - 风格卡片组件
 *
 * 功能：
 * - 展示设计风格的基本信息（标识符A-G、名称、描述）
 * - 显示风格色彩方案预览（彩色圆点）
 * - 列出核心特征（前3个关键词）
 * - 提供选择按钮和Hover交互效果
 * - 完整的深色模式支持
 * - 选中状态高亮显示
 *
 * 设计参考：TemplateCard.tsx（保持一致的样式和交互模式）
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-15
 */
export function StyleCard({
  style,
  onClick,
  loading = false,
  selected = false,
  variant,
}: StyleCardProps): React.ReactElement {
  // 获取风格显示信息
  const styleInfo = getStyleDisplayInfo(style);

  return (
    <Card
      data-testid="style-card"
      className={cn(
        'group relative h-full cursor-pointer overflow-hidden',
        'transition-all duration-300 ease-out',
        'hover:shadow-2xl hover:-translate-y-2 hover:ring-2 hover:ring-purple-500/20',
        'border-border/50 bg-card/50 backdrop-blur-sm',
        // 选中状态样式
        selected && 'border-primary/50 ring-2 ring-primary/30 shadow-xl -translate-y-1'
      )}
      onClick={() => !loading && onClick(style)}
    >
      {/* 顶部：风格标识符徽章 + 色彩预览 */}
      <div className="relative h-32 w-full overflow-hidden bg-gradient-to-br p-6">
        {/* 如果有缩略图，显示缩略图 */}
        {variant?.thumbnailUrl ? (
          <>
            <img
              src={variant.thumbnailUrl}
              alt={styleInfo.displayName}
              className="absolute inset-0 w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" />
          </>
        ) : (
          /* 默认渐变背景 */
          <div
            className={cn(
              'absolute inset-0 bg-gradient-to-br opacity-20',
              styleInfo.colorClass
            )}
          />
        )}

        {/* 风格标识符徽章（左上角） */}
        <div className="absolute left-3 top-3">
          <Badge className="bg-white/90 dark:bg-gray-900/90 text-gray-900 dark:text-gray-100 font-bold text-lg px-3 py-1 shadow-sm">
            {styleInfo.identifier}
          </Badge>
        </div>

        {/* 选中状态图标（右上角） */}
        {selected && (
          <div className="absolute right-3 top-3">
            <CheckCircle2 className="h-6 w-6 text-primary fill-primary/20 drop-shadow-md" />
          </div>
        )}

        {/* 风格图标（居中，仅在无缩略图时显示） */}
        {!variant?.thumbnailUrl && (
          <div className="flex h-full items-center justify-center">
            <div className="text-5xl opacity-80 transition-transform duration-300 group-hover:scale-110">
              {styleInfo.icon}
            </div>
          </div>
        )}

        {/* 色彩方案预览（底部彩色圆点，仅在无缩略图时显示） */}
        {!variant?.thumbnailUrl && (
          <div className="absolute bottom-3 left-1/2 -translate-x-1/2 flex gap-2">
            <div
              className={cn(
                'h-3 w-3 rounded-full bg-gradient-to-br shadow-sm',
                styleInfo.colorClass
              )}
            />
            <div
              className={cn(
                'h-3 w-3 rounded-full bg-gradient-to-br opacity-70 shadow-sm',
                styleInfo.colorClass
              )}
            />
            <div
              className={cn(
                'h-3 w-3 rounded-full bg-gradient-to-br opacity-40 shadow-sm',
                styleInfo.colorClass
              )}
            />
          </div>
        )}
      </div>

      {/* 中间：内容区域 */}
      <CardContent className="p-5">
        {/* 风格名称 */}
        <div className="mb-3">
          <h3 className="text-lg font-bold text-gray-900 dark:text-gray-100 mb-1">
            {styleInfo.displayName}
          </h3>
          <p className="text-xs text-gray-500 dark:text-gray-400">
            {styleInfo.displayNameEn}
          </p>
        </div>

        {/* 风格描述 */}
        <p className="mb-4 text-sm text-gray-600 dark:text-gray-300 line-clamp-2 leading-relaxed">
          {styleInfo.description}
        </p>

        {/* 核心特征（显示前3个） */}
        <div className="mb-3">
          <div className="flex items-center gap-2 mb-2">
            <Sparkles className="h-3.5 w-3.5 text-primary" />
            <span className="text-xs font-semibold text-gray-700 dark:text-gray-300">
              核心特征
            </span>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {styleInfo.features.slice(0, 3).map((feature, index) => (
              <Badge
                key={index}
                variant="secondary"
                className="text-xs font-normal bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-gray-700"
              >
                {feature}
              </Badge>
            ))}
          </div>
        </div>

        {/* 适用场景（显示前2个） */}
        <div className="text-xs text-gray-500 dark:text-gray-400">
          <span className="font-medium">适用场景：</span>
          {styleInfo.suitableFor.slice(0, 2).join('、')}
          {styleInfo.suitableFor.length > 2 && '等'}
        </div>
      </CardContent>

      {/* 底部：操作按钮 */}
      <CardFooter className="p-5 pt-0">
        <Button
          className={cn(
            'w-full',
            selected
              ? 'bg-primary text-primary-foreground'
              : 'bg-gradient-to-r ' + styleInfo.colorClass + ' text-white hover:opacity-90'
          )}
          variant={selected ? 'default' : 'default'}
          size="sm"
          disabled={loading}
        >
          {loading ? '加载中...' : selected ? '✓ 已选择' : '选择此风格'}
        </Button>
      </CardFooter>
    </Card>
  );
}
