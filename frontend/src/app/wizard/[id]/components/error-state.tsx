/**
 * ErrorState - 错误状态组件
 *
 * 功能：
 * - 显示生成失败的错误页面
 * - 显示错误信息
 * - 提供重试和返回按钮
 *
 * Props:
 * - title: 页面标题
 * - error: 错误信息
 * - onRetry: 重试回调
 * - onBack: 返回回调
 */
'use client';

import React from 'react';
import { AlertCircle } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';

/**
 * ErrorState Props接口
 */
export interface ErrorStateProps {
  /** 页面标题 */
  title?: string;
  /** 错误信息 */
  error: string;
  /** 重试回调 */
  onRetry: () => void;
  /** 返回回调 */
  onBack: () => void;
}

/**
 * ErrorState组件
 */
export const ErrorState: React.FC<ErrorStateProps> = ({
  title = 'AppSpec 生成向导',
  error,
  onRetry,
  onBack,
}) => {
  return (
    <div className="h-screen bg-background">
      {/* 顶部标题栏 - 始终显示 */}
      <div className="border-b bg-background px-6 py-4">
        <div className="flex items-center gap-4">
          <h1 className="text-xl font-semibold">{title}</h1>
          <Badge variant="destructive" className="text-sm">生成失败</Badge>
        </div>
      </div>

      {/* 错误信息内容区 */}
      <div className="flex items-center justify-center h-[calc(100vh-73px)] p-4">
        <Card className="max-w-md w-full">
          <CardContent className="pt-6">
            <div className="text-center">
              <AlertCircle className="w-12 h-12 text-destructive mx-auto mb-4" />
              <h3 className="text-lg font-semibold mb-2">生成失败</h3>
              <p className="text-muted-foreground mb-4">{error}</p>
              <div className="flex gap-3 justify-center">
                <Button onClick={onRetry}>重试</Button>
                <Button variant="outline" onClick={onBack}>
                  返回创建
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

// 设置displayName便于调试
ErrorState.displayName = 'ErrorState';
