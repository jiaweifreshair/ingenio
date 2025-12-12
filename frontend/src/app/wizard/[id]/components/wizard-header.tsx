/**
 * WizardHeader - 向导页面头部组件
 *
 * 功能：
 * - 显示页面标题
 * - 显示当前状态Badge
 * - 显示加载指示器
 * - 显示连接状态警告
 *
 * Props:
 * - title: 页面标题
 * - status: 当前状态（idle | generating | completed | failed）
 * - isLoading: 是否正在加载
 * - isConnected: 是否已连接
 * - isE2ETestMode: 是否为E2E测试模式
 */
'use client';

import React from 'react';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Loader2, AlertCircle } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * 状态类型定义
 */
export type WizardStatus = 'idle' | 'generating' | 'completed' | 'failed';

/**
 * WizardHeader Props接口
 */
export interface WizardHeaderProps {
  /** 页面标题 */
  title?: string;
  /** 当前状态 */
  status: WizardStatus;
  /** 是否正在加载 */
  isLoading?: boolean;
  /** 是否已连接 */
  isConnected?: boolean;
  /** 是否为E2E测试模式 */
  isE2ETestMode?: boolean;
  /** 自定义类名 */
  className?: string;
}

/**
 * 获取状态显示文本
 */
const getStatusText = (status: WizardStatus, isE2ETestMode: boolean): string => {
  if (isE2ETestMode) return 'E2E测试模式';

  switch (status) {
    case 'idle':
      return '待配置';
    case 'generating':
      return '生成中';
    case 'completed':
      return '生成完成';
    case 'failed':
      return '生成失败';
    default:
      return '未知状态';
  }
};

/**
 * 获取状态Badge变体
 */
const getStatusVariant = (status: WizardStatus, isE2ETestMode: boolean) => {
  if (isE2ETestMode) return 'outline';

  switch (status) {
    case 'completed':
      return 'default';
    case 'failed':
      return 'destructive';
    case 'generating':
      return 'outline';
    default:
      return 'outline';
  }
};

/**
 * 获取状态Badge自定义类名
 */
const getStatusClassName = (status: WizardStatus): string => {
  if (status === 'completed') {
    return 'bg-green-500';
  }
  return '';
};

/**
 * WizardHeader组件
 */
export const WizardHeader: React.FC<WizardHeaderProps> = ({
  title = 'AppSpec 生成向导',
  status,
  isLoading = false,
  isConnected = true,
  isE2ETestMode = false,
  className,
}) => {
  const statusText = getStatusText(status, isE2ETestMode);
  const statusVariant = getStatusVariant(status, isE2ETestMode);
  const statusClassName = getStatusClassName(status);

  return (
    <div className={cn('border-b bg-background px-6 py-4', className)}>
      <div className="flex items-center gap-4">
        <h1 className="text-xl font-semibold">{title}</h1>
        <Badge
          variant={statusVariant}
          className={cn('text-sm', statusClassName)}
        >
          {statusText}
        </Badge>
        {isLoading && <Loader2 className="w-4 h-4 animate-spin text-primary" />}
        {!isConnected && (
          <Alert className="max-w-md">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>
              连接断开，正在重试...
            </AlertDescription>
          </Alert>
        )}
      </div>
    </div>
  );
};

// 设置displayName便于调试
WizardHeader.displayName = 'WizardHeader';
