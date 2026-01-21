'use client';

import React from 'react';
import { CreditCard } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useCredits } from '@/hooks/use-credits';

interface CreditBalanceProps {
  className?: string;
}

/**
 * 余额显示组件
 * 显示用户当前的生成次数余额
 */
export function CreditBalance({ className }: CreditBalanceProps): React.ReactElement {
  const { credits, loading } = useCredits();

  return (
    <div className={cn(
      'bg-gradient-to-r from-purple-600/10 to-blue-600/10 rounded-2xl p-6 border border-purple-200/50 dark:border-purple-800/50',
      className
    )}>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="p-3 bg-purple-600/20 rounded-xl">
            <CreditCard className="h-6 w-6 text-purple-600" />
          </div>
          <div>
            <p className="text-sm text-muted-foreground">剩余生成次数</p>
            {loading ? (
              <div className="h-8 w-20 bg-muted animate-pulse rounded" />
            ) : (
              <p className="text-3xl font-bold text-purple-600">
                {credits?.remaining ?? 0}
                <span className="text-base font-normal text-muted-foreground ml-1">次</span>
              </p>
            )}
          </div>
        </div>
        <div className="text-right text-sm text-muted-foreground">
          <p>已使用: {credits?.used ?? 0} 次</p>
          <p>总购买: {credits?.total ?? 0} 次</p>
        </div>
      </div>
    </div>
  );
}
