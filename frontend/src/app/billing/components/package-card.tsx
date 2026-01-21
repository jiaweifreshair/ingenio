'use client';

import React from 'react';
import { Check, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface PackageCardProps {
  code: string;
  name: string;
  credits: number;
  price: number;
  popular?: boolean;
  onSelect: () => void;
}

/**
 * 套餐卡片组件
 * 展示单个套餐的信息和购买按钮
 */
export function PackageCard({
  name,
  credits,
  price,
  popular = false,
  onSelect,
}: PackageCardProps): React.ReactElement {
  // 计算单价
  const unitPrice = (price / credits).toFixed(1);

  return (
    <div className={cn(
      'relative rounded-2xl border p-6 transition-all hover:shadow-lg',
      popular
        ? 'border-purple-500 bg-purple-50/50 dark:bg-purple-900/20 shadow-md'
        : 'border-border bg-card hover:border-purple-300'
    )}>
      {/* 推荐标签 */}
      {popular && (
        <div className="absolute -top-3 left-1/2 -translate-x-1/2">
          <span className="bg-gradient-to-r from-purple-600 to-blue-600 text-white text-xs font-medium px-3 py-1 rounded-full">
            最受欢迎
          </span>
        </div>
      )}

      {/* 套餐名称 */}
      <h3 className="text-xl font-semibold mb-2">{name}</h3>

      {/* 价格 */}
      <div className="mb-4">
        <span className="text-4xl font-bold">¥{price}</span>
      </div>

      {/* 次数 */}
      <div className="mb-6">
        <p className="text-2xl font-semibold text-purple-600">{credits} 次</p>
        <p className="text-sm text-muted-foreground">约 ¥{unitPrice}/次</p>
      </div>

      {/* 特性列表 */}
      <ul className="space-y-2 mb-6">
        <li className="flex items-center gap-2 text-sm">
          <Check className="h-4 w-4 text-green-500" />
          <span>AI 代码生成</span>
        </li>
        <li className="flex items-center gap-2 text-sm">
          <Check className="h-4 w-4 text-green-500" />
          <span>前端 + 后端代码</span>
        </li>
        <li className="flex items-center gap-2 text-sm">
          <Check className="h-4 w-4 text-green-500" />
          <span>永久有效</span>
        </li>
      </ul>

      {/* 购买按钮 */}
      <Button
        onClick={onSelect}
        className={cn(
          'w-full h-12 rounded-xl',
          popular
            ? 'bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700'
            : ''
        )}
        variant={popular ? 'default' : 'outline'}
      >
        <Sparkles className="h-4 w-4 mr-2" />
        立即购买
      </Button>
    </div>
  );
}
