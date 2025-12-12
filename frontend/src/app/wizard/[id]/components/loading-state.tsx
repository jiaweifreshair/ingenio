/**
 * LoadingState - 加载状态组件
 *
 * 功能：
 * - 显示加载中的页面状态
 * - 显示页面标题和加载动画
 * - 显示加载提示文本
 *
 * Props:
 * - title: 页面标题
 * - message: 加载提示消息
 */
'use client';

import React from 'react';
import { Loader2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';

/**
 * LoadingState Props接口
 */
export interface LoadingStateProps {
  /** 页面标题 */
  title?: string;
  /** 加载提示消息 */
  message?: string;
}

/**
 * LoadingState组件
 */
export const LoadingState: React.FC<LoadingStateProps> = ({
  title = 'AppSpec 生成向导',
  message = '正在加载向导...',
}) => {
  return (
    <div className="h-screen bg-background">
      {/* 顶部标题栏 - 始终显示 */}
      <div className="border-b bg-background px-6 py-4">
        <div className="flex items-center gap-4">
          <h1 className="text-xl font-semibold">{title}</h1>
          <Loader2 className="w-4 h-4 animate-spin text-primary" />
          <Badge variant="outline" className="text-sm">加载中</Badge>
        </div>
      </div>

      {/* 加载中内容区 */}
      <div className="flex items-center justify-center h-[calc(100vh-73px)]">
        <div className="text-center">
          <Loader2 className="w-8 h-8 animate-spin mx-auto mb-4 text-primary" />
          <p className="text-muted-foreground">{message}</p>
        </div>
      </div>
    </div>
  );
};

// 设置displayName便于调试
LoadingState.displayName = 'LoadingState';
