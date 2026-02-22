/**
 * 代码生成预览面板组件
 *
 * 整合功能：
 * - AI思考过程指示器 (ThinkingIndicator)
 * - 实时代码查看器 (RealtimeCodeViewer)
 * - 实时预览Iframe (LivePreviewIframe)
 * - SSE流式数据订阅 (useCodeGenerationStream)
 *
 * 布局设计：
 * - 顶部：思考过程指示器
 * - 左侧：实时代码查看器
 * - 右侧：实时应用预览
 *
 * 使用场景：
 * - 向导页面的代码生成阶段
 * - 用户确认设计后的后端代码生成
 *
 * @author Ingenio Team
 * @since 2.0.0
 */
'use client';

import React, { useState, useCallback, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Play,
  Pause,
  RotateCcw,
  Code2,
  Eye,
  Maximize2,
  Minimize2,
  PanelLeftClose,
  PanelLeft,
} from 'lucide-react';
import { ThinkingIndicator, type ThinkingPhase } from './thinking-indicator';
import { RealtimeCodeViewer } from './realtime-code-viewer';
import { LivePreviewIframe } from './live-preview-iframe';
import {
  useCodeGenerationStream,
  type GeneratedFile
} from '@/hooks/use-code-generation-stream';
import { SandboxManager, type SandboxStatus } from '@/lib/sandbox/sandbox-manager';

/**
 * 组件Props
 */
interface CodeGenerationPreviewPanelProps {
  /** AppSpec ID */
  appSpecId: string;
  /** 是否自动开始生成 */
  autoStart?: boolean;
  /** 初始沙箱URL（如果已有） */
  initialSandboxUrl?: string | null;
  /** 容器类名 */
  className?: string;
  /** 生成完成回调 */
  onComplete?: (files: GeneratedFile[]) => void;
  /** 生成错误回调 */
  onError?: (error: string) => void;
}

/**
 * 视图模式
 */
type ViewMode = 'split' | 'code' | 'preview';

/**
 * 代码生成预览面板组件
 */
export const CodeGenerationPreviewPanel: React.FC<CodeGenerationPreviewPanelProps> = ({
  appSpecId,
  autoStart = false,
  initialSandboxUrl,
  className,
  onComplete,
  onError,
}) => {
  // 视图状态
  const [viewMode, setViewMode] = useState<ViewMode>('split');
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [showCodePanel, setShowCodePanel] = useState(true);

  // 沙箱状态
  const [sandboxUrl, setSandboxUrl] = useState<string | null>(initialSandboxUrl || null);
  const [sandboxStatus, setSandboxStatus] = useState<SandboxStatus>('idle');

  // SSE代码生成流
  const {
    isConnected,
    isConnecting,
    error,
    isCompleted,
    thinkingMessage,
    thinkingDuration,
    currentFile,
    generatedFiles,
    totalFiles,
    completedFiles,
    progress,
    connect,
    disconnect,
    reset,
  } = useCodeGenerationStream({
    appSpecId,
    autoConnect: autoStart,
    onThinking: (message, duration) => {
      console.log('[思考]', message, duration);
    },
    onFileStart: (path, fileType) => {
      console.log('[文件开始]', path, fileType);
    },
    onFileContent: (path, content) => {
      console.log('[文件内容]', path, content.length);
      // 实时同步到沙箱
      const file = generatedFiles.find(f => f.path === path);
      if (file) {
        SandboxManager.addFile(file);
      }
    },
    onFileComplete: (path) => {
      console.log('[文件完成]', path);
    },
    onComplete: (files) => {
      console.log('[生成完成]', files.length);
      onComplete?.(files);
      // 刷新沙箱预览
      SandboxManager.refreshPreview();
    },
    onError: (errorMsg) => {
      console.error('[生成错误]', errorMsg);
      onError?.(errorMsg);
    },
  });

  // 计算思考阶段
  const thinkingPhase: ThinkingPhase = error
    ? 'error'
    : isCompleted
      ? 'completed'
      : isConnected && currentFile
        ? 'generating'
        : isConnected && thinkingMessage
          ? 'analyzing'
          : 'idle';

  /**
   * 预览错误处理
   *
   * 是什么：接收预览 iframe 失败回调。
   * 做什么：自动切换到代码视图并显示代码面板。
   * 为什么：避免预览白屏时用户无从排查。
   */
  const handlePreviewError = useCallback((previewError: Error) => {
    setViewMode('code');
    setShowCodePanel(true);
    onError?.(previewError.message);
  }, [onError]);

  /**
   * 初始化沙箱
   */
  useEffect(() => {
    // 设置沙箱事件监听
    SandboxManager.setListeners({
      onStatusChange: (status) => {
        setSandboxStatus(status);
      },
      onPreviewReady: (url) => {
        setSandboxUrl(url);
      },
      onError: (err) => {
        console.error('[沙箱错误]', err);
      },
    });

    // 如果有初始URL，直接设置为就绪状态
    if (initialSandboxUrl) {
      setSandboxUrl(initialSandboxUrl);
      setSandboxStatus('ready');
    }

    return () => {
      SandboxManager.reset();
    };
  }, [initialSandboxUrl]);

  /**
   * 开始生成
   */
  const handleStart = useCallback(async () => {
    // 如果没有沙箱URL，先创建沙箱
    if (!sandboxUrl) {
      try {
        setSandboxStatus('creating');
        const instance = await SandboxManager.createSandbox({
          appSpecId,
          template: 'react',
        });
        setSandboxUrl(instance.previewUrl);
      } catch (err) {
        console.error('创建沙箱失败:', err);
        // 继续生成，即使沙箱创建失败
      }
    }

    // 开始代码生成
    connect();
  }, [appSpecId, sandboxUrl, connect]);

  /**
   * 暂停生成
   */
  const handlePause = useCallback(() => {
    disconnect();
  }, [disconnect]);

  /**
   * 重置
   */
  const handleReset = useCallback(() => {
    reset();
    setSandboxUrl(initialSandboxUrl || null);
    setSandboxStatus(initialSandboxUrl ? 'ready' : 'idle');
  }, [reset, initialSandboxUrl]);

  /**
   * 切换全屏
   */
  const toggleFullscreen = useCallback(() => {
    setIsFullscreen(prev => !prev);
  }, []);

  /**
   * 切换代码面板
   */
  const toggleCodePanel = useCallback(() => {
    setShowCodePanel(prev => !prev);
  }, []);

  // 渲染主内容
  return (
    <div
      className={cn(
        'flex flex-col h-full bg-background',
        isFullscreen && 'fixed inset-0 z-50',
        className
      )}
    >
      {/* 顶部工具栏 */}
      <div className="px-4 py-3 border-b bg-muted/30 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <h2 className="font-semibold">实时代码生成</h2>

          {/* 状态徽章 */}
          <Badge
            variant={isConnected ? 'default' : 'outline'}
            className={cn(
              isConnected && 'bg-green-500',
              isConnecting && 'bg-yellow-500 animate-pulse'
            )}
          >
            {isConnecting ? '连接中...' : isConnected ? '已连接' : '未连接'}
          </Badge>

          {/* 进度 */}
          {(isConnected || isCompleted) && (
            <span className="text-sm text-muted-foreground">
              {completedFiles} / {totalFiles} 文件 ({Math.round(progress)}%)
            </span>
          )}
        </div>

        <div className="flex items-center gap-2">
          {/* 视图切换 */}
          <div className="flex items-center gap-1 bg-muted rounded-md p-1">
            <Button
              variant={viewMode === 'split' ? 'secondary' : 'ghost'}
              size="sm"
              className="h-7 px-2"
              onClick={() => setViewMode('split')}
              title="分屏视图"
            >
              <PanelLeft className="h-4 w-4 mr-1" />
              分屏
            </Button>
            <Button
              variant={viewMode === 'code' ? 'secondary' : 'ghost'}
              size="sm"
              className="h-7 px-2"
              onClick={() => setViewMode('code')}
              title="代码视图"
            >
              <Code2 className="h-4 w-4 mr-1" />
              代码
            </Button>
            <Button
              variant={viewMode === 'preview' ? 'secondary' : 'ghost'}
              size="sm"
              className="h-7 px-2"
              onClick={() => setViewMode('preview')}
              title="预览视图"
            >
              <Eye className="h-4 w-4 mr-1" />
              预览
            </Button>
          </div>

          {/* 操作按钮 */}
          <div className="flex items-center gap-1 border-l pl-2 ml-2">
            {/* 开始/暂停 */}
            {!isConnected && !isCompleted ? (
              <Button
                variant="default"
                size="sm"
                onClick={handleStart}
                disabled={isConnecting}
                className="gap-1"
              >
                <Play className="h-4 w-4" />
                开始生成
              </Button>
            ) : isConnected ? (
              <Button
                variant="outline"
                size="sm"
                onClick={handlePause}
                className="gap-1"
              >
                <Pause className="h-4 w-4" />
                暂停
              </Button>
            ) : null}

            {/* 重置 */}
            <Button
              variant="ghost"
              size="sm"
              onClick={handleReset}
              className="gap-1"
              title="重置"
            >
              <RotateCcw className="h-4 w-4" />
            </Button>

            {/* 全屏 */}
            <Button
              variant="ghost"
              size="sm"
              onClick={toggleFullscreen}
              title={isFullscreen ? '退出全屏' : '全屏'}
            >
              {isFullscreen ? (
                <Minimize2 className="h-4 w-4" />
              ) : (
                <Maximize2 className="h-4 w-4" />
              )}
            </Button>
          </div>
        </div>
      </div>

      {/* 思考指示器 */}
      <AnimatePresence>
        {(isConnected || thinkingMessage || error) && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="border-b"
          >
            <div className="p-4">
              <ThinkingIndicator
                message={thinkingMessage}
                duration={thinkingDuration}
                phase={thinkingPhase}
                progress={progress}
                currentFile={currentFile}
                completedFiles={completedFiles}
                totalFiles={totalFiles}
                error={error}
                showDetails={true}
              />
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* 主内容区 */}
      <div className="flex-1 flex overflow-hidden">
        {/* 代码视图 */}
        {(viewMode === 'split' || viewMode === 'code') && (
          <motion.div
            className={cn(
              'border-r overflow-hidden',
              viewMode === 'split' ? 'w-1/2' : 'flex-1'
            )}
            initial={false}
            animate={{
              width: viewMode === 'code' ? '100%' : showCodePanel ? '50%' : '0%',
            }}
            transition={{ duration: 0.2 }}
          >
            {showCodePanel && (
              <RealtimeCodeViewer
                files={generatedFiles}
                currentFile={currentFile}
                isGenerating={isConnected}
                showLineNumbers={true}
                darkMode={true}
                autoScroll={true}
              />
            )}
          </motion.div>
        )}

        {/* 代码面板切换按钮（分屏模式下） */}
        {viewMode === 'split' && (
          <Button
            variant="ghost"
            size="sm"
            className="absolute left-1/2 top-1/2 transform -translate-y-1/2 z-10 h-8 w-6 p-0 rounded-l-none"
            onClick={toggleCodePanel}
          >
            {showCodePanel ? (
              <PanelLeftClose className="h-4 w-4" />
            ) : (
              <PanelLeft className="h-4 w-4" />
            )}
          </Button>
        )}

        {/* 预览视图 */}
        {(viewMode === 'split' || viewMode === 'preview') && (
          <motion.div
            className={cn(
              'overflow-hidden',
              viewMode === 'split' ? 'w-1/2' : 'flex-1'
            )}
            initial={false}
            animate={{
              width: viewMode === 'preview' ? '100%' : '50%',
            }}
            transition={{ duration: 0.2 }}
          >
            <LivePreviewIframe
              previewUrl={sandboxUrl}
              sandboxStatus={sandboxStatus}
              isGenerating={isConnected}
              showDeviceSwitcher={true}
              showRefreshButton={true}
              autoRefresh={false}
              onRefresh={async () => {
                return SandboxManager.refreshPreview();
              }}
              onPreviewError={handlePreviewError}
            />
          </motion.div>
        )}
      </div>

      {/* 底部状态栏 */}
      <div className="px-4 py-2 border-t bg-muted/30 text-xs text-muted-foreground flex items-center justify-between">
        <div className="flex items-center gap-4">
          <span>AppSpec: {appSpecId}</span>
          {sandboxUrl && (
            <span className="truncate max-w-xs">沙箱: {sandboxUrl}</span>
          )}
        </div>
        <div className="flex items-center gap-4">
          {isCompleted && (
            <Badge variant="outline" className="text-green-600">
              生成完成
            </Badge>
          )}
        </div>
      </div>
    </div>
  );
};

export default CodeGenerationPreviewPanel;
