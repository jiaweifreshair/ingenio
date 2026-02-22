/**
 * 实时预览Iframe组件
 *
 * 设计理念：
 * - 实时展示代码生成后的应用预览
 * - 自动刷新以反映最新代码变更
 * - 支持多设备尺寸模拟
 * - 加载状态和错误处理
 *
 * 与sandbox-manager配合使用，实现代码生成到预览的完整闭环
 *
 * @author Ingenio Team
 * @since 2.0.0
 */
'use client';

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  RefreshCw,
  Smartphone,
  Tablet,
  Monitor,
  Maximize2,
  Minimize2,
  ExternalLink,
  Loader2,
  AlertCircle,
  CheckCircle2,
  XCircle,
  Eye,
  EyeOff,
} from 'lucide-react';
import type { SandboxStatus } from '@/lib/sandbox/sandbox-manager';

/**
 * 验证URL是否合法
 * 防止API返回的无效URL（如包含中文的测试消息）导致前端崩溃
 */
function isValidUrl(urlString: string | null | undefined): boolean {
  if (!urlString || typeof urlString !== 'string') {
    return false;
  }
  // 检查是否包含中文字符（明显的无效URL）
  if (/[\u4e00-\u9fa5]/.test(urlString)) {
    console.error(`[URL验证] ❌ URL包含中文字符: "${urlString}"`);
    return false;
  }
  try {
    const url = new URL(urlString);
    return url.protocol === 'http:' || url.protocol === 'https:';
  } catch {
    console.error(`[URL验证] ❌ URL格式无效: "${urlString}"`);
    return false;
  }
}

/**
 * 设备类型
 */
export type DeviceType = 'mobile' | 'tablet' | 'desktop' | 'fullscreen';

/**
 * 设备配置
 */
const deviceConfigs: Record<DeviceType, { width: number; height: number; label: string; icon: React.ReactNode }> = {
  mobile: { width: 375, height: 667, label: '手机', icon: <Smartphone className="h-4 w-4" /> },
  tablet: { width: 768, height: 1024, label: '平板', icon: <Tablet className="h-4 w-4" /> },
  desktop: { width: 1280, height: 800, label: '桌面', icon: <Monitor className="h-4 w-4" /> },
  fullscreen: { width: 0, height: 0, label: '全屏', icon: <Maximize2 className="h-4 w-4" /> },
};

/**
 * 组件Props
 */
interface LivePreviewIframeProps {
  /** 预览URL */
  previewUrl: string | null;
  /** 沙箱状态 */
  sandboxStatus?: SandboxStatus;
  /** 是否正在生成代码 */
  isGenerating?: boolean;
  /** 初始设备类型 */
  initialDevice?: DeviceType;
  /** 是否显示设备切换器 */
  showDeviceSwitcher?: boolean;
  /** 是否显示刷新按钮 */
  showRefreshButton?: boolean;
  /** 是否自动刷新 */
  autoRefresh?: boolean;
  /** 自动刷新间隔（毫秒） */
  autoRefreshInterval?: number;
  /** 刷新回调 */
  onRefresh?: () => Promise<boolean>;
  /** 容器类名 */
  className?: string;
  /** 标题 */
  title?: string;
  /** 加载中文案 */
  loadingText?: string;
  /** 错误文案 */
  errorText?: string;
  /** 运行时错误回调 */
  onRuntimeError?: (error: Error) => void;
  /** 预览加载失败回调 */
  onPreviewError?: (error: Error) => void;
}

/**
 * 小圆点占位组件（必须在statusConfigs之前定义）
 */
const CircleDot: React.FC<{ className?: string }> = ({ className }) => (
  <div className={cn('rounded-full bg-current', className)} />
);

/**
 * 沙箱状态配置
 */
const statusConfigs: Record<SandboxStatus, {
  icon: React.ReactNode;
  color: string;
  label: string;
}> = {
  idle: { icon: <CircleDot className="h-3 w-3" />, color: 'text-gray-400', label: '空闲' },
  creating: { icon: <Loader2 className="h-3 w-3 animate-spin" />, color: 'text-blue-500', label: '创建中' },
  ready: { icon: <CheckCircle2 className="h-3 w-3" />, color: 'text-green-500', label: '就绪' },
  syncing: { icon: <RefreshCw className="h-3 w-3 animate-spin" />, color: 'text-yellow-500', label: '同步中' },
  error: { icon: <XCircle className="h-3 w-3" />, color: 'text-red-500', label: '错误' },
  destroyed: { icon: <EyeOff className="h-3 w-3" />, color: 'text-gray-500', label: '已销毁' },
};

/**
 * 实时预览Iframe组件
 */
export const LivePreviewIframe: React.FC<LivePreviewIframeProps> = ({
  previewUrl,
  sandboxStatus = 'idle',
  isGenerating = false,
  initialDevice = 'desktop',
  showDeviceSwitcher = true,
  showRefreshButton = true,
  autoRefresh = false,
  autoRefreshInterval = 5000,
  onRefresh,
  className,
  title = '应用预览',
  loadingText = '正在加载预览...',
  errorText = '预览加载失败',
  onRuntimeError,
  onPreviewError,
}) => {
  /**
   * 预览加载超时阈值
   *
   * 是什么：等待 iframe 加载完成的最大时长。
   * 做什么：超过阈值后触发 onPreviewError 并进入错误态。
   * 为什么：避免预览长期空白无反馈。
   */
  const PREVIEW_TIMEOUT_MS = 12_000;
  const [device, setDevice] = useState<DeviceType>(initialDevice);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [showPreview, setShowPreview] = useState(true);
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const refreshTimerRef = useRef<NodeJS.Timeout | null>(null);

  const [iframeKey, setIframeKey] = useState(0);

  // 验证previewUrl是否合法，防止无效URL导致错误
  const validPreviewUrl = isValidUrl(previewUrl) ? previewUrl : null;

  // 获取设备配置
  const deviceConfig = deviceConfigs[device];
  const statusConfig = statusConfigs[sandboxStatus];

  /**
   * 刷新预览
   * 采用 Open-Lovable-CN 的 Key-based Refresh 机制，强制销毁并重建组件
   */
  const handleRefresh = useCallback(async () => {
    if (isRefreshing) return;

    setIsRefreshing(true);
    setIsLoading(true);
    setHasError(false);

    try {
      // 调用外部刷新回调
      if (onRefresh) {
        await onRefresh();
      }

      // 增加 key 以强制 React 重新挂载 iframe 组件
      // 这比修改 src 更彻底，能清除所有 DOM 状态和缓存
      setIframeKey(prev => prev + 1);
      
    } catch {
      setHasError(true);
    } finally {
      // 短暂延迟后结束刷新状态，给用户视觉反馈
      setTimeout(() => setIsRefreshing(false), 500);
    }
  }, [isRefreshing, onRefresh]);

  /**
   * Iframe加载完成
   */
  const handleIframeLoad = useCallback(() => {
    setIsLoading(false);
    setHasError(false);
  }, []);

  /**
   * Iframe加载错误
   */
  const handleIframeError = useCallback(() => {
    setIsLoading(false);
    setHasError(true);
    if (onPreviewError) {
      onPreviewError(new Error('预览加载失败'));
    }
  }, [onPreviewError]);

  /**
   * 切换预览显示
   */
  const togglePreview = useCallback(() => {
    setShowPreview(prev => !prev);
  }, []);

  /**
   * 在新窗口打开
   */
  const openInNewWindow = useCallback(() => {
    if (validPreviewUrl) {
      window.open(validPreviewUrl, '_blank');
    }
  }, [validPreviewUrl]);

  // 自动刷新
  useEffect(() => {
    if (autoRefresh && validPreviewUrl && !isGenerating) {
      refreshTimerRef.current = setInterval(handleRefresh, autoRefreshInterval);
    }

    return () => {
      if (refreshTimerRef.current) {
        clearInterval(refreshTimerRef.current);
      }
    };
  }, [autoRefresh, validPreviewUrl, isGenerating, autoRefreshInterval, handleRefresh]);

  // URL变化时重新加载
  useEffect(() => {
    if (validPreviewUrl) {
      setIsLoading(true);
      setHasError(false);
    }
  }, [validPreviewUrl]);

  /**
   * 预览加载超时兜底
   *
   * 是什么：当 iframe 长时间未完成加载时触发错误回调。
   * 做什么：结束加载状态并进入错误态。
   * 为什么：防止预览一直空白却无提示。
   */
  useEffect(() => {
    if (!validPreviewUrl || isGenerating || !showPreview || hasError || !isLoading) {
      return;
    }

    const timer = window.setTimeout(() => {
      setIsLoading(false);
      setHasError(true);
      if (onPreviewError) {
        onPreviewError(new Error(errorText));
      }
    }, PREVIEW_TIMEOUT_MS);

    return () => window.clearTimeout(timer);
  }, [validPreviewUrl, isGenerating, showPreview, hasError, isLoading, onPreviewError, errorText, PREVIEW_TIMEOUT_MS]);

  // 沙箱状态异常时触发预览错误
  useEffect(() => {
    if (sandboxStatus === 'error') {
      setHasError(true);
      setIsLoading(false);
      if (onPreviewError) {
        onPreviewError(new Error('沙箱状态异常'));
      }
    }
  }, [sandboxStatus, onPreviewError]);

  // 监听来自沙箱的运行时错误消息
  useEffect(() => {
    /**
     * 运行时错误标准化
     *
     * 是什么：把沙箱错误 payload 转为 Error 实例。
     * 做什么：兼容字符串/对象/Error 等多种类型。
     * 为什么：确保上层统一处理白屏与运行时异常。
     */
    const normalizeSandboxError = (rawError: unknown): Error => {
      if (rawError instanceof Error) {
        return rawError;
      }
      if (rawError && typeof rawError === 'object' && 'message' in rawError) {
        const message = (rawError as { message?: unknown }).message;
        return new Error(typeof message === 'string' ? message : '预览运行时错误');
      }
      if (typeof rawError === 'string') {
        return new Error(rawError);
      }
      return new Error('预览运行时错误');
    };

    const handleMessage = (event: MessageEvent) => {
      // 简单校验消息格式
      if (event.data && event.data.type === 'SANDBOX_ERROR' && event.data.error) {
        console.warn('[LivePreview] 捕获到沙箱运行时错误:', event.data.error);
        if (onRuntimeError) {
          onRuntimeError(normalizeSandboxError(event.data.error));
        }
      }
    };

    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [onRuntimeError]);

  // 计算iframe尺寸
  const iframeStyle: React.CSSProperties = device === 'fullscreen'
    ? { width: '100%', height: '100%' }
    : { width: deviceConfig.width, height: deviceConfig.height };

  return (
    <div className={cn('flex flex-col h-full border rounded-lg overflow-hidden', className)}>
      {/* 顶部工具栏 */}
      <div className="px-4 py-2 border-b bg-muted/30 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Eye className="h-5 w-5 text-muted-foreground" />
          <span className="font-medium">{title}</span>

          {/* 沙箱状态 */}
          <Badge variant="outline" className={cn('text-xs gap-1', statusConfig.color)}>
            {statusConfig.icon}
            {statusConfig.label}
          </Badge>

          {/* 生成中指示器 */}
          {isGenerating && (
            <Badge variant="secondary" className="text-xs gap-1 animate-pulse">
              <Loader2 className="h-3 w-3 animate-spin" />
              代码生成中
            </Badge>
          )}
        </div>

        <div className="flex items-center gap-2">
          {/* 设备切换器 */}
          {showDeviceSwitcher && (
            <div className="flex items-center gap-1 bg-muted rounded-md p-1">
              {(Object.entries(deviceConfigs) as [DeviceType, typeof deviceConfig][]).map(([key, config]) => (
                <Button
                  key={key}
                  variant={device === key ? 'secondary' : 'ghost'}
                  size="sm"
                  className="h-7 w-7 p-0"
                  onClick={() => setDevice(key)}
                  title={config.label}
                >
                  {config.icon}
                </Button>
              ))}
            </div>
          )}

          {/* 操作按钮 */}
          <div className="flex items-center gap-1 border-l pl-2 ml-2">
            {/* 刷新 */}
            {showRefreshButton && (
              <Button
                variant="ghost"
                size="sm"
                className="h-7 w-7 p-0"
                onClick={handleRefresh}
                disabled={isRefreshing || !validPreviewUrl}
                title="刷新预览"
              >
                <RefreshCw className={cn('h-4 w-4', isRefreshing && 'animate-spin')} />
              </Button>
            )}

            {/* 显示/隐藏 */}
            <Button
              variant="ghost"
              size="sm"
              className="h-7 w-7 p-0"
              onClick={togglePreview}
              title={showPreview ? '隐藏预览' : '显示预览'}
            >
              {showPreview ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </Button>

            {/* 新窗口打开 */}
            <Button
              variant="ghost"
              size="sm"
              className="h-7 w-7 p-0"
              onClick={openInNewWindow}
              disabled={!validPreviewUrl}
              title="在新窗口打开"
            >
              <ExternalLink className="h-4 w-4" />
            </Button>

            {/* 全屏切换 */}
            <Button
              variant="ghost"
              size="sm"
              className="h-7 w-7 p-0"
              onClick={() => setDevice(device === 'fullscreen' ? 'desktop' : 'fullscreen')}
              title={device === 'fullscreen' ? '退出全屏' : '全屏'}
            >
              {device === 'fullscreen' ? (
                <Minimize2 className="h-4 w-4" />
              ) : (
                <Maximize2 className="h-4 w-4" />
              )}
            </Button>
          </div>
        </div>
      </div>

      {/* 预览内容区 */}
      <div className="flex-1 overflow-auto bg-gray-100 dark:bg-gray-900 flex items-center justify-center p-4">
        <AnimatePresence mode="wait">
          {!showPreview ? (
            // 预览隐藏状态
            <motion.div
              key="hidden"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="text-center text-muted-foreground"
            >
              <EyeOff className="h-12 w-12 mx-auto mb-4 opacity-50" />
              <p className="text-sm">预览已隐藏</p>
              <Button
                variant="outline"
                size="sm"
                className="mt-4"
                onClick={togglePreview}
              >
                显示预览
              </Button>
            </motion.div>
          ) : !validPreviewUrl ? (
            // 无URL状态（包括URL无效的情况）
            <motion.div
              key="no-url"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="text-center text-muted-foreground"
            >
              {isGenerating || sandboxStatus === 'creating' ? (
                <>
                  <Loader2 className="h-12 w-12 mx-auto mb-4 animate-spin text-primary" />
                  <p className="text-sm">{loadingText}</p>
                  <p className="text-xs mt-2 opacity-70">
                    正在准备预览环境...
                  </p>
                </>
              ) : (
                <>
                  <AlertCircle className="h-12 w-12 mx-auto mb-4 text-yellow-500" />
                  <p className="text-sm">等待预览URL</p>
                  <p className="text-xs mt-2 opacity-70">
                    开始代码生成后将自动显示预览
                  </p>
                </>
              )}
            </motion.div>
          ) : hasError ? (
            // 错误状态
            <motion.div
              key="error"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="text-center text-muted-foreground"
            >
              <XCircle className="h-12 w-12 mx-auto mb-4 text-red-500" />
              <p className="text-sm">{errorText}</p>
              <Button
                variant="outline"
                size="sm"
                className="mt-4"
                onClick={handleRefresh}
              >
                重试
              </Button>
            </motion.div>
          ) : (
            // 正常预览
            <motion.div
              key="preview"
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className={cn(
                'relative bg-white dark:bg-gray-800 rounded-lg shadow-lg overflow-hidden',
                device !== 'fullscreen' && 'border'
              )}
              style={device === 'fullscreen' ? { width: '100%', height: '100%' } : undefined}
            >
              {/* 设备框架 */}
              {device !== 'fullscreen' && (
                <div className="absolute -top-1 left-1/2 transform -translate-x-1/2 w-20 h-1 bg-gray-300 dark:bg-gray-600 rounded-b-lg" />
              )}

              {/* 加载遮罩 */}
              {isLoading && (
                <div className="absolute inset-0 bg-background/80 flex items-center justify-center z-10">
                  <div className="text-center">
                    <Loader2 className="h-8 w-8 animate-spin text-primary mx-auto" />
                    <p className="text-sm text-muted-foreground mt-2">加载中...</p>
                  </div>
                </div>
              )}

              {/* Iframe */}
              <iframe
                key={`${validPreviewUrl}-${iframeKey}`}
                ref={iframeRef}
                src={validPreviewUrl || ''}
                style={iframeStyle}
                className="border-0"
                title="应用预览"
                sandbox="allow-scripts allow-same-origin allow-forms allow-modals allow-popups"
                onLoad={handleIframeLoad}
                onError={handleIframeError}
              />
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* 底部状态栏 */}
      <div className="px-4 py-1.5 border-t bg-muted/30 text-xs text-muted-foreground flex items-center justify-between">
        <div className="flex items-center gap-4">
          {validPreviewUrl && (
            <span className="truncate max-w-xs">{validPreviewUrl}</span>
          )}
        </div>
        <div className="flex items-center gap-4">
          {device !== 'fullscreen' && (
            <span>{deviceConfig.width} × {deviceConfig.height}</span>
          )}
          <span>{deviceConfig.label}视图</span>
        </div>
      </div>
    </div>
  );
};

export default LivePreviewIframe;
