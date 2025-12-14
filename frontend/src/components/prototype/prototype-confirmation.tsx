/**
 * PrototypeConfirmation - V2.0原型预览确认组件（深度融合版）
 *
 * 功能：
 * - 集成OpenLovable快速预览（5-10秒生成）
 * - 支持聊天式迭代修改
 * - 实时代码显示和预览切换
 * - 展示选择摘要（意图类型、选择的模板、设计风格）
 * - 全屏预览模式支持
 *
 * V2.0核心：用户必须确认设计后才能进入Execute阶段
 *
 * @author Ingenio Team
 * @version 2.0.1 (深度融合版)
 * @since 2025-11-16
 */
'use client';

import React, { useState, useRef, useEffect } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  Loader2,
  ArrowLeft,
  CheckCircle2,
  ExternalLink,
  Maximize2,
  Minimize2,
  RefreshCw,
  AlertCircle,
  Eye,
  FileText,
  Code,
  Send,
} from 'lucide-react';

import type { PlanRoutingResult } from '@/lib/api/plan-routing';
import type { Template } from '@/types/template';
import { getStyleDisplayInfo } from '@/types/design-style';
import { useOpenLovablePreview } from '@/hooks/use-openlovable-preview';
import { cn } from '@/lib/utils';
import { PrototypePreview } from './prototype-preview';
import { ConfirmationDialog } from './confirmation-dialog';

// ==================== 类型定义 ====================

/**
 * PrototypeConfirmation组件属性接口
 */
export interface PrototypeConfirmationProps {
  /** 路由结果（包含原型URL等信息） */
  routingResult: PlanRoutingResult;
  /** 用户需求（用于OpenLovable生成） */
  userRequirement?: string;
  /** 选择的模板（可选） */
  selectedTemplate?: Template | null;
  /** 确认设计回调 */
  onConfirm: () => void;
  /** 返回修改回调 */
  onBack: () => void;
  /** 是否正在加载 */
  loading?: boolean;
  /** 错误信息 */
  error?: string | null;
}

/**
 * 视图模式
 */
type ViewMode = 'preview' | 'code' | 'log';

// ==================== 主组件 ====================

/**
 * PrototypeConfirmation - 原型预览确认组件（深度融合版）
 */
export function PrototypeConfirmation({
  routingResult,
  userRequirement = '',
  selectedTemplate: _selectedTemplate, // Mark as unused
  onConfirm,
  onBack,
  loading = false,
  error: externalError = null,
}: PrototypeConfirmationProps): React.ReactElement {
  // OpenLovable Hook
  const {
    stage,
    previewUrl,
    streamedCode,
    generationLog,
    elapsedTime,
    totalTime,
    error: openLovableError,
    isReloading,
    startGeneration,
    sendIterationMessage,
    reloadPreview,
  } = useOpenLovablePreview();

  // 状态管理
  const [viewMode, setViewMode] = useState<ViewMode>('preview');
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [iterationInput, setIterationInput] = useState('');
  const [iframeKey, setIframeKey] = useState(0);
  const [showConfirmDialog, setShowConfirmDialog] = useState(false);

  // Refs
  const logContainerRef = useRef<HTMLDivElement>(null);
  const hasStartedRef = useRef(false);

  // 获取选中风格的显示信息
  const selectedStyleInfo = routingResult.selectedStyleId
    ? getStyleDisplayInfo(routingResult.selectedStyleId as import('@/types/design-style').DesignStyle)
    : null;

  // 组合错误信息
  const error = externalError || openLovableError;

  // 使用OpenLovable预览URL或后端提供的URL
  const effectivePreviewUrl = previewUrl || routingResult.prototypeUrl || null;

  // 是否可以确认（原型已生成且无错误）
  const canConfirm = (stage === 'complete' || routingResult.prototypeGenerated) && !error;

  // 是否正在生成
  const isGenerating = stage === 'sandbox' || stage === 'generating';

  // ========== 副作用 ==========

  /**
   * 自动启动OpenLovable生成（如果后端没有提供prototypeUrl）
   */
  useEffect(() => {
    if (!hasStartedRef.current && !routingResult.prototypeUrl && userRequirement && stage === 'idle') {
      hasStartedRef.current = true;
      const styleHint = selectedStyleInfo?.displayName || '';
      startGeneration(userRequirement, {
        styleHint,
        appSpecId: routingResult.appSpecId,
        styleId: routingResult.selectedStyleId
      });
    }
  }, [routingResult.prototypeUrl, userRequirement, stage, selectedStyleInfo, startGeneration]);

  /**
   * 自动滚动日志到底部
   */
  useEffect(() => {
    if (logContainerRef.current) {
      logContainerRef.current.scrollTop = logContainerRef.current.scrollHeight;
    }
  }, [generationLog]);

  /**
   * 生成完成后刷新iframe
   */
  useEffect(() => {
    if (stage === 'complete' && previewUrl) {
      // 等待Vite热更新
      const timer = setTimeout(() => {
        setIframeKey(prev => prev + 1);
      }, 5000); // 增加延迟，确保Vite完成热更新
      return () => clearTimeout(timer);
    }
  }, [stage, previewUrl]);

  // ========== 事件处理 ==========

  /**
   * 发送迭代修改请求
   */
  const handleSendIteration = async () => {
    if (!iterationInput.trim()) return;

    const message = iterationInput.trim();
    setIterationInput('');
    await sendIterationMessage(message);

    // 迭代完成后刷新预览
    if (previewUrl) {
      setTimeout(() => {
        setIframeKey(prev => prev + 1);
      }, 2000);
    }
  };

  /**
   * 刷新预览
   */
  const handleRefresh = async () => {
    await reloadPreview();
    // 刷新iframe
    setTimeout(() => {
      setIframeKey(prev => prev + 1);
    }, 3000);
  };

  /**
   * 切换全屏模式
   */
  const toggleFullscreen = () => {
    setIsFullscreen(prev => !prev);
  };

  // 获取选中风格的显示信息

  return (
    <div className={`flex flex-col ${isFullscreen ? 'fixed inset-0 z-50 bg-white dark:bg-gray-900 p-4' : 'h-full'}`} data-testid="prototype-confirmation-panel">
      <ConfirmationDialog
        isOpen={showConfirmDialog}
        onClose={() => setShowConfirmDialog(false)}
        onConfirm={onConfirm}
        loading={loading}
      />

      {/* 标题区域 */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-4 gap-3">
        <div className="flex items-center gap-3">
          <Eye className="h-6 w-6 text-purple-600 dark:text-purple-400" />
          <div>
            <h2 className="text-xl md:text-2xl font-bold text-gray-900 dark:text-gray-100">
              确认设计方案
            </h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              {isGenerating
                ? `AI正在生成原型... ${elapsedTime}s`
                : totalTime
                  ? `生成完成 (${totalTime}s)，可进行迭代修改`
                  : '预览原型效果，确认后将进入全栈代码生成'}
            </p>
          </div>
        </div>

        {/* 控制按钮 */}
        <div className="flex items-center gap-2">
          {/* 状态标签 */}
          <Badge
            className={cn(
              'px-3 py-1',
              stage === 'complete' && 'bg-green-100 text-green-700',
              isGenerating && 'bg-purple-100 text-purple-700',
              stage === 'error' && 'bg-red-100 text-red-700',
              stage === 'idle' && 'bg-gray-100 text-gray-700',
            )}
          >
            {isGenerating && <Loader2 className="h-3 w-3 mr-1 animate-spin" />}
            {stage === 'complete' && <CheckCircle2 className="h-3 w-3 mr-1" />}
            {stage === 'error' && <AlertCircle className="h-3 w-3 mr-1" />}
            {stage === 'sandbox' && `创建沙箱 ${elapsedTime}s`}
            {stage === 'generating' && `生成中 ${elapsedTime}s`}
            {stage === 'complete' && `已完成 ${totalTime}s`}
            {stage === 'error' && '失败'}
            {stage === 'idle' && '等待中'}
          </Badge>

          <Button
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={loading || isReloading || !effectivePreviewUrl}
            data-testid="refresh-preview-button"
          >
            <RefreshCw className={cn('h-4 w-4 mr-1', isReloading && 'animate-spin')} />
            刷新
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={toggleFullscreen}
            disabled={!effectivePreviewUrl}
          >
            {isFullscreen ? (
              <>
                <Minimize2 className="h-4 w-4 mr-1" />
                退出
              </>
            ) : (
              <>
                <Maximize2 className="h-4 w-4 mr-1" />
                全屏
              </>
            )}
          </Button>
          {effectivePreviewUrl && (
            <Button variant="outline" size="sm" asChild>
              <a href={effectivePreviewUrl} target="_blank" rel="noopener noreferrer">
                <ExternalLink className="h-4 w-4 mr-1" />
                新窗口
              </a>
            </Button>
          )}
        </div>
      </div>

      {/* 错误提示 */}
      {error && (
        <Alert variant="destructive" className="mb-4">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {/* 主要内容区域 - 响应式分屏布局 */}
      <div className="flex-1 flex flex-col lg:flex-row gap-4 min-h-0">
        
        {/* Mobile/Tablet Layout (Tabbed) - Hidden on LG */}
        <div className={`lg:hidden flex-1 min-h-[400px] ${isFullscreen ? 'hidden' : ''}`}>
           <Card className="h-full flex flex-col border-2">
            {/* 视图切换标签 */}
            <div className="flex items-center justify-between p-3 border-b">
              <Tabs value={viewMode} onValueChange={(v) => setViewMode(v as ViewMode)}>
                <TabsList className="h-8">
                  <TabsTrigger value="preview" className="text-xs px-3">
                    <Eye className="w-3 h-3 mr-1" />
                    预览
                  </TabsTrigger>
                  <TabsTrigger value="code" className="text-xs px-3">
                    <Code className="w-3 h-3 mr-1" />
                    代码
                  </TabsTrigger>
                  <TabsTrigger value="log" className="text-xs px-3">
                    <FileText className="w-3 h-3 mr-1" />
                    日志
                  </TabsTrigger>
                </TabsList>
              </Tabs>
            </div>
            {/* Mobile Content */}
            <div className="flex-1 p-0 overflow-hidden relative">
               {viewMode === 'preview' && (
                  <PrototypePreview
                    previewUrl={effectivePreviewUrl}
                    isGenerating={isGenerating}
                    elapsedTime={elapsedTime}
                    stage={stage}
                    iframeKey={iframeKey}
                    streamedCode={streamedCode}
                  />
               )}
               {viewMode === 'code' && (
                  <div className="h-full bg-[#1e1e1e] p-4 overflow-y-auto font-mono text-xs text-gray-300">
                     <pre className="whitespace-pre-wrap break-all">{streamedCode}</pre>
                  </div>
               )}
               {viewMode === 'log' && (
                  <ScrollArea className="h-full p-4">
                    {generationLog.map((log, i) => <div key={i} className="text-xs font-mono mb-1">{log}</div>)}
                  </ScrollArea>
               )}
            </div>
           </Card>
        </div>

        {/* Desktop Split Layout (Always Visible on LG) */}
        <div className="hidden lg:flex flex-1 gap-0 h-full shadow-xl rounded-xl overflow-hidden border-2 border-gray-200 dark:border-gray-800">
           
           {/* Left: Code Terminal (40%) */}
           <div className="w-[40%] flex flex-col bg-[#1e1e1e] border-r border-white/10">
              <div className="h-10 flex items-center px-4 bg-[#2d2d2d] border-b border-black/20 justify-between">
                 <div className="flex items-center gap-2 text-gray-400 text-xs font-mono">
                    <Code className="w-3 h-3" />
                    <span>GENERATOR_TERMINAL</span>
                 </div>
                 {isGenerating && <Loader2 className="w-3 h-3 text-green-500 animate-spin" />}
              </div>
              
              <div className="flex-1 overflow-y-auto p-4 font-mono text-xs text-green-400/90 selection:bg-green-900 selection:text-white" ref={logContainerRef}>
                 <pre className="whitespace-pre-wrap break-all">
                   {streamedCode || '// Initializing AI Generator...'}
                   {isGenerating && <span className="inline-block w-1.5 h-3 bg-green-500 ml-1 animate-pulse align-middle" />}
                 </pre>
              </div>

              {/* Chat Input Area */}
              <div className="p-3 bg-[#252526] border-t border-white/5">
                 <div className="relative">
                    <input 
                      className="w-full bg-[#3c3c3c] text-white text-xs rounded px-3 py-2 pr-8 focus:outline-none focus:ring-1 focus:ring-blue-500 placeholder:text-gray-500"
                      placeholder="Type to iterate (e.g., 'Make button blue')..."
                      value={iterationInput}
                      onChange={(e) => setIterationInput(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && !e.shiftKey) {
                          e.preventDefault();
                          handleSendIteration();
                        }
                      }}
                      disabled={loading || isGenerating}
                    />
                    <button 
                      className="absolute right-1.5 top-1.5 text-gray-400 hover:text-white disabled:opacity-50"
                      onClick={handleSendIteration}
                      disabled={!iterationInput.trim() || loading || isGenerating}
                    >
                      <Send className="w-3.5 h-3.5" />
                    </button>
                 </div>
              </div>
           </div>

           {/* Right: Preview (60%) */}
           <div className="flex-1 bg-white dark:bg-gray-900 relative">
              <PrototypePreview
                 previewUrl={effectivePreviewUrl}
                 isGenerating={isGenerating}
                 elapsedTime={elapsedTime}
                 stage={stage}
                 iframeKey={iframeKey}
                 // On desktop split view, we don't need the overlay code stream since it's on the left
                 streamedCode={undefined} 
              />
           </div>

        </div>

      </div>

      {/* 底部操作按钮 */}
      <div className="flex flex-col sm:flex-row gap-3 mt-4 pt-4 border-t-2 border-gray-200 dark:border-gray-700">
        <Button
          variant="outline"
          onClick={onBack}
          disabled={loading}
          className="w-full sm:flex-1"
          data-testid="back-to-style-button"
        >
          <ArrowLeft className="h-4 w-4 mr-2" />
          返回修改
        </Button>
        <Button
          onClick={() => setShowConfirmDialog(true)}
          disabled={loading || !canConfirm}
          className="w-full sm:flex-1 bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 text-white"
          data-testid="confirm-design-button"
        >
          {loading ? (
            <>
              <Loader2 className="h-4 w-4 mr-2 animate-spin" />
              确认中...
            </>
          ) : (
            <>
              <CheckCircle2 className="h-4 w-4 mr-2" />
              确认设计，开始生成
            </>
          )}
        </Button>
      </div>

      {/* 底部提示 */}
      {!canConfirm && !loading && (
        <div className="mt-3 p-3 rounded-lg bg-amber-50 dark:bg-amber-900/10 border border-amber-200 dark:border-amber-800">
          <p className="text-xs text-amber-700 dark:text-amber-300 text-center">
            {isGenerating
              ? `正在生成原型... ${elapsedTime}s`
              : stage === 'error'
                ? '生成失败，请重试'
                : '请等待原型生成完成后再确认设计'}
          </p>
        </div>
      )}
    </div>
  );
}

export default PrototypeConfirmation;
