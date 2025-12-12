/**
 * WebSocket连接状态指示器组件
 *
 * 设计理念：
 * - 实时显示WebSocket连接状态（连接中、已连接、断开、重连中）
 * - 显示重连尝试次数和倒计时
 * - 提供手动重连按钮
 * - 苹果风格的磨砂玻璃效果和状态指示动画
 *
 * 连接状态：
 * - CONNECTING: 正在连接（蓝色脉冲）
 * - CONNECTED: 已连接（绿色稳定）
 * - DISCONNECTED: 断开连接（红色静态）
 * - RECONNECTING: 重连中（黄色脉冲）
 */
'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Wifi,
  WifiOff,
  RefreshCw,
  AlertCircle,
  CheckCircle2,
  Loader2,
} from 'lucide-react';

/**
 * WebSocket连接状态枚举
 */
export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected' | 'reconnecting';

/**
 * 组件Props
 */
interface WebSocketStatusProps {
  /** 是否已连接 */
  isConnected: boolean;
  /** 是否正在连接 */
  isConnecting: boolean;
  /** 连接错误信息 */
  error: string | null;
  /** 重连尝试次数 */
  connectionAttempts?: number;
  /** 最后一次消息时间戳 */
  lastMessageTime?: number;
  /** 重连回调 */
  onReconnect?: () => void;
  /** 是否显示详细信息 */
  showDetails?: boolean;
  /** 类名 */
  className?: string;
}

/**
 * 计算连接状态
 */
const getConnectionStatus = (
  isConnected: boolean,
  isConnecting: boolean,
  error: string | null
): ConnectionStatus => {
  if (isConnected) return 'connected';
  if (isConnecting) return 'connecting';
  if (error && !isConnecting) return 'disconnected';
  return 'reconnecting';
};

/**
 * 获取状态配置
 */
const getStatusConfig = (status: ConnectionStatus) => {
  switch (status) {
    case 'connected':
      return {
        label: '已连接',
        icon: <CheckCircle2 className="h-4 w-4" />,
        dotClass: 'bg-green-500',
        badgeVariant: 'default' as const,
        animate: false,
      };
    case 'connecting':
      return {
        label: '连接中',
        icon: <Loader2 className="h-4 w-4 animate-spin" />,
        dotClass: 'bg-blue-500 animate-pulse',
        badgeVariant: 'secondary' as const,
        animate: true,
      };
    case 'reconnecting':
      return {
        label: '重连中',
        icon: <RefreshCw className="h-4 w-4 animate-spin" />,
        dotClass: 'bg-yellow-500 animate-pulse',
        badgeVariant: 'secondary' as const,
        animate: true,
      };
    case 'disconnected':
      return {
        label: '连接断开',
        icon: <AlertCircle className="h-4 w-4" />,
        dotClass: 'bg-red-500',
        badgeVariant: 'destructive' as const,
        animate: false,
      };
  }
};

/**
 * 格式化最后消息时间
 */
const formatLastMessageTime = (timestamp?: number): string => {
  if (!timestamp) return '未知';
  const now = Date.now();
  const diff = now - timestamp;

  if (diff < 1000) return '刚刚';
  if (diff < 60000) return `${Math.floor(diff / 1000)}秒前`;
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`;
  return new Date(timestamp).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  });
};

/**
 * WebSocket连接状态指示器
 */
export const WebSocketStatus: React.FC<WebSocketStatusProps> = ({
  isConnected,
  isConnecting,
  error,
  connectionAttempts = 0,
  lastMessageTime,
  onReconnect,
  showDetails = true,
  className,
}) => {
  const status = getConnectionStatus(isConnected, isConnecting, error);
  const config = getStatusConfig(status);

  return (
    <div
      className={cn(
        'flex items-center justify-between rounded-lg border border-border/50 bg-card/30 p-3 backdrop-blur-sm',
        'transition-all duration-300',
        className
      )}
    >
      {/* 左侧：状态指示 */}
      <div className="flex items-center gap-3">
        {/* 状态圆点 */}
        <div
          className={cn(
            'h-3 w-3 rounded-full shadow-md transition-all duration-300',
            config.dotClass
          )}
        />

        {/* 状态信息 */}
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium">{config.label}</span>
            {connectionAttempts > 0 && status !== 'connected' && (
              <Badge variant="outline" className="text-xs">
                第{connectionAttempts}次尝试
              </Badge>
            )}
          </div>

          {/* 详细信息 */}
          {showDetails && (
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              {status === 'connected' && lastMessageTime && (
                <>
                  <Wifi className="h-3 w-3" />
                  <span>最后消息: {formatLastMessageTime(lastMessageTime)}</span>
                </>
              )}
              {status === 'disconnected' && error && (
                <>
                  <WifiOff className="h-3 w-3" />
                  <span className="text-red-600">{error}</span>
                </>
              )}
              {status === 'reconnecting' && (
                <>
                  <RefreshCw className="h-3 w-3 animate-spin" />
                  <span>正在重新连接...</span>
                </>
              )}
            </div>
          )}
        </div>
      </div>

      {/* 右侧：操作按钮 */}
      {(status === 'disconnected' || status === 'reconnecting') && onReconnect && (
        <Button
          variant="outline"
          size="sm"
          onClick={onReconnect}
          disabled={isConnecting}
          className="gap-2"
        >
          {isConnecting ? (
            <>
              <Loader2 className="h-3 w-3 animate-spin" />
              连接中
            </>
          ) : (
            <>
              <RefreshCw className="h-3 w-3" />
              重连
            </>
          )}
        </Button>
      )}
    </div>
  );
};
